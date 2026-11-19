package com.digitalvoting.api;

import com.digitalvoting.config.AppConfig;
import com.digitalvoting.util.ServiceException;
import org.bytedeco.javacpp.BytePointer;
import org.bytedeco.javacpp.DoublePointer;
import org.bytedeco.javacpp.IntPointer;
import org.bytedeco.javacv.Java2DFrameConverter;
import org.bytedeco.javacv.OpenCVFrameConverter;
import org.bytedeco.opencv.opencv_core.*;
import org.bytedeco.opencv.opencv_face.LBPHFaceRecognizer;
import org.bytedeco.opencv.opencv_objdetect.CascadeClassifier;

import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.IntBuffer;
import java.nio.file.*;
import java.util.List;

import static org.bytedeco.opencv.global.opencv_core.CV_32SC1;
import static org.bytedeco.opencv.global.opencv_imgcodecs.*;
import static org.bytedeco.opencv.global.opencv_imgproc.*;

/**
 * Face detection, enrollment and verification with OpenCV (via JavaCV).
 * Detection: Haar cascade. Matching: LBPH recognizer trained on the voter's stored samples.
 * Requires src/main/resources/cascades/haarcascade_frontalface_default.xml (see README).
 */
public class FaceService {
    private static final String CASCADE = "/cascades/haarcascade_frontalface_default.xml";
    private CascadeClassifier detector;

    private synchronized CascadeClassifier detector() throws ServiceException {
        if (detector != null) return detector;
        try (InputStream in = FaceService.class.getResourceAsStream(CASCADE)) {
            if (in == null) throw new ServiceException("Missing " + CASCADE + ". Download haarcascade_frontalface_default.xml from the OpenCV repository into src/main/resources/cascades/ (see README).");
            Path tmp = Files.createTempFile("haar", ".xml");
            tmp.toFile().deleteOnExit();
            Files.copy(in, tmp, StandardCopyOption.REPLACE_EXISTING);
            CascadeClassifier cc = new CascadeClassifier(tmp.toString());
            if (cc.empty()) throw new ServiceException("Could not load face cascade file.");
            detector = cc;
            return cc;
        } catch (IOException e) {
            throw new ServiceException("Could not load face detector: " + e.getMessage(), e);
        }
    }

    /** @return PNG bytes of the 100x100 grayscale face crop, or null if not exactly one face is visible. */
    public byte[] extractFace(BufferedImage image) throws ServiceException {
        Java2DFrameConverter j2d = new Java2DFrameConverter();
        OpenCVFrameConverter.ToMat toMat = new OpenCVFrameConverter.ToMat();
        Mat color = toMat.convert(j2d.convert(image));
        if (color == null || color.empty()) return null;

        Mat gray = new Mat();
        cvtColor(color, gray, COLOR_BGR2GRAY);
        equalizeHist(gray, gray);

        RectVector faces = new RectVector();
        detector().detectMultiScale(gray, faces, 1.1, 5, 0, new Size(80, 80), new Size());
        if (faces.size() != 1) return null;

        Mat crop = new Mat(gray, faces.get(0));
        Mat small = new Mat();
        resize(crop, small, new Size(100, 100));
        BytePointer buf = new BytePointer();
        imencode(".png", small, buf);
        byte[] out = new byte[(int) buf.limit()];
        buf.get(out);
        return out;
    }

    /** @return true if the probe face (from extractFace) matches the stored samples. */
    public boolean verify(byte[] probePng, List<byte[]> enrolledPng) throws ServiceException {
        if (enrolledPng.isEmpty()) throw new ServiceException("No face enrolled for this voter.");
        MatVector images = new MatVector(enrolledPng.size());
        Mat labels = new Mat(enrolledPng.size(), 1, CV_32SC1);
        IntBuffer lb = labels.createBuffer();
        for (int i = 0; i < enrolledPng.size(); i++) {
            images.put(i, decode(enrolledPng.get(i)));
            lb.put(i, 1);
        }
        LBPHFaceRecognizer recognizer = LBPHFaceRecognizer.create();
        recognizer.train(images, labels);

        IntPointer label = new IntPointer(1);
        DoublePointer confidence = new DoublePointer(1);
        recognizer.predict(decode(probePng), label, confidence);
        return confidence.get() <= AppConfig.getDouble("face.threshold", 60);   // lower = closer match
    }

    private static Mat decode(byte[] png) {
        return imdecode(new Mat(new BytePointer(png)), IMREAD_GRAYSCALE);
    }
}
