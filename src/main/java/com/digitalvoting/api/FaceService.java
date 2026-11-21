
package com.digitalvoting.api;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.IntBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.bytedeco.javacpp.BytePointer;
import org.bytedeco.javacpp.DoublePointer;
import org.bytedeco.javacpp.IntPointer;
import org.bytedeco.javacv.Java2DFrameConverter;
import org.bytedeco.javacv.OpenCVFrameConverter;
import static org.bytedeco.opencv.global.opencv_core.CV_32SC1;
import static org.bytedeco.opencv.global.opencv_imgcodecs.IMREAD_GRAYSCALE;
import static org.bytedeco.opencv.global.opencv_imgcodecs.imdecode;
import static org.bytedeco.opencv.global.opencv_imgcodecs.imencode;
import static org.bytedeco.opencv.global.opencv_imgproc.COLOR_BGR2GRAY;
import static org.bytedeco.opencv.global.opencv_imgproc.cvtColor;
import static org.bytedeco.opencv.global.opencv_imgproc.equalizeHist;
import static org.bytedeco.opencv.global.opencv_imgproc.resize;
import org.bytedeco.opencv.opencv_core.Mat;
import org.bytedeco.opencv.opencv_core.MatVector;
import org.bytedeco.opencv.opencv_core.RectVector;
import org.bytedeco.opencv.opencv_core.Size;
import org.bytedeco.opencv.opencv_face.LBPHFaceRecognizer;
import org.bytedeco.opencv.opencv_objdetect.CascadeClassifier;

import com.digitalvoting.config.AppConfig;
import com.digitalvoting.model.FaceSample;
import com.digitalvoting.util.ServiceException;

public class FaceService {

    private static final String CASCADE =
            "/cascades/haarcascade_frontalface_default.xml";

    private static final int FACE_SIZE = 100;

    private CascadeClassifier detector;

    private synchronized CascadeClassifier detector()
            throws ServiceException {

        if (detector != null) {
            return detector;
        }

        try (InputStream in =
                     FaceService.class.getResourceAsStream(CASCADE)) {

            if (in == null) {
                throw new ServiceException(
                        "Missing " + CASCADE +
                        ". Place haarcascade_frontalface_default.xml " +
                        "inside src/main/resources/cascades/."
                );
            }

            Path tmp = Files.createTempFile("haar", ".xml");
            tmp.toFile().deleteOnExit();

            Files.copy(
                    in,
                    tmp,
                    StandardCopyOption.REPLACE_EXISTING
            );

            CascadeClassifier cc =
                    new CascadeClassifier(tmp.toString());

            if (cc.empty()) {
                throw new ServiceException(
                        "Could not load face cascade file."
                );
            }

            detector = cc;
            return detector;

        } catch (IOException e) {
            throw new ServiceException(
                    "Could not load face detector: " + e.getMessage(),
                    e
            );
        }
    }

    /**
     * Extract exactly one face and return a normalized
     * 100x100 grayscale PNG.
     */
    public byte[] extractFace(BufferedImage image)
            throws ServiceException {

        if (image == null) {
            return null;
        }

        Java2DFrameConverter j2d =
                new Java2DFrameConverter();

        OpenCVFrameConverter.ToMat toMat =
                new OpenCVFrameConverter.ToMat();

        Mat color = toMat.convert(j2d.convert(image));

        if (color == null || color.empty()) {
            return null;
        }

        Mat gray = new Mat();

        cvtColor(color, gray, COLOR_BGR2GRAY);

        RectVector faces = new RectVector();

        detector().detectMultiScale(
                gray,
                faces,
                1.1,
                5,
                0,
                new Size(80, 80),
                new Size()
        );

        if (faces.size() != 1) {
            return null;
        }

        Mat crop = new Mat(gray, faces.get(0));

        if (crop.empty()) {
            return null;
        }

        Mat normalized = new Mat();

        equalizeHist(crop, normalized);

        Mat small = new Mat();

        resize(
                normalized,
                small,
                new Size(FACE_SIZE, FACE_SIZE)
        );

        BytePointer buf = new BytePointer();

        boolean encoded = imencode(".png", small, buf);

        if (!encoded || buf.limit() <= 0) {
            return null;
        }

        byte[] out = new byte[(int) buf.limit()];

        buf.get(out);

        return out;
    }

    /**
     * Multi-voter face verification.
     *
     * Every sample is labelled with its actual voter ID.
     * The predicted voter ID must match the authenticated
     * voter's ID, and the LBPH distance must pass threshold.
     */
    public boolean verify(
            int expectedVoterId,
            byte[] probePng,
            List<FaceSample> enrolledSamples
    ) throws ServiceException {

        if (probePng == null || probePng.length == 0) {
            throw new ServiceException(
                    "Captured face image is missing."
            );
        }

        if (enrolledSamples == null || enrolledSamples.isEmpty()) {
            throw new ServiceException(
                    "No approved face templates are available."
            );
        }

        Set<Integer> voterIds = new HashSet<>();

        int ownSamples = 0;

        for (FaceSample sample : enrolledSamples) {

            if (sample == null ||
                    sample.image() == null ||
                    sample.image().length == 0) {

                throw new ServiceException(
                        "An enrolled face image is missing."
                );
            }

            voterIds.add(sample.voterId());

            if (sample.voterId() == expectedVoterId) {
                ownSamples++;
            }
        }

        if (ownSamples == 0) {
            throw new ServiceException(
                    "No face samples found for this voter."
            );
        }

        if (voterIds.size() < 2) {
            throw new ServiceException(
                    "Face verification requires at least two " +
                    "approved, face-enrolled voters."
            );
        }

        MatVector images =
                new MatVector(enrolledSamples.size());

        Mat labels =
                new Mat(enrolledSamples.size(), 1, CV_32SC1);

        IntBuffer labelBuffer = labels.createBuffer();

        // Load images and assign actual voter IDs.
        for (int i = 0; i < enrolledSamples.size(); i++) {

            FaceSample enrolled = enrolledSamples.get(i);

            Mat sample = decode(enrolled.image());

            if (sample == null || sample.empty()) {
                throw new ServiceException(
                        "An enrolled face image is invalid."
                );
            }

            if (sample.cols() != FACE_SIZE ||
                    sample.rows() != FACE_SIZE) {

                throw new ServiceException(
                        "An enrolled face image has incorrect dimensions."
                );
            }

            images.put(i, sample);

            labelBuffer.put(i, enrolled.voterId());
        }

        // Decode login probe.
        Mat probe = decode(probePng);

        if (probe == null || probe.empty()) {
            throw new ServiceException(
                    "Captured face image is invalid."
            );
        }

        if (probe.cols() != FACE_SIZE ||
                probe.rows() != FACE_SIZE) {

            throw new ServiceException(
                    "Captured face image has incorrect dimensions."
            );
        }

        // Train recognizer with all approved voters.
        LBPHFaceRecognizer recognizer =
                LBPHFaceRecognizer.create();

        recognizer.train(images, labels);

        IntPointer predictedLabel = new IntPointer(1);

        DoublePointer confidence = new DoublePointer(1);

        recognizer.predict(
                probe,
                predictedLabel,
                confidence
        );

        int predictedVoterId = predictedLabel.get();

        double score = confidence.get();

        double threshold =
                AppConfig.getDouble("face.threshold", 65.0);

        System.out.println("Expected voter ID: " + expectedVoterId);
        System.out.println("Predicted voter ID: " + predictedVoterId);
        System.out.println("Approved voter identities: " + voterIds.size());
        System.out.println("Enrolled samples: " + enrolledSamples.size());
        System.out.println("Face confidence: " + score);
        System.out.println("Face threshold: " + threshold);

        if (!Double.isFinite(score) || score < 0) {
            return false;
        }

        return predictedVoterId == expectedVoterId
                && score <= threshold;
    }

    /**
     * Decode PNG bytes as grayscale.
     */
    private static Mat decode(byte[] png) {

        if (png == null || png.length == 0) {
            return new Mat();
        }

        Mat encoded = new Mat(new BytePointer(png));

        return imdecode(
                encoded,
                IMREAD_GRAYSCALE
        );
    }
}
