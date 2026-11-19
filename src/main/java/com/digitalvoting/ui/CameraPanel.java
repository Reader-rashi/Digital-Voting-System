package com.digitalvoting.ui;

import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.FrameGrabber;
import org.bytedeco.javacv.Java2DFrameConverter;
import org.bytedeco.javacv.OpenCVFrameGrabber;
import org.bytedeco.javacv.VideoInputFrameGrabber;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.function.Supplier;

/**
 * Live webcam preview. Starts when shown, releases the camera when removed from the window.
 * Tries several camera back-ends (OpenCV device 0/1, then DirectShow via videoInput on Windows)
 * and waits for the camera to warm up before giving up.
 */
public class CameraPanel extends JPanel {
    private volatile BufferedImage latest;
    private volatile boolean running;
    private volatile String error;
    private volatile String status = "Starting camera...";

    public CameraPanel() { setPreferredSize(new Dimension(480, 360)); setBackground(Color.BLACK); }

    @Override public void addNotify() { super.addNotify(); start(); }
    @Override public void removeNotify() { running = false; super.removeNotify(); }

    /** Opens the first back-end that really delivers a frame, or returns null (error text is set). */
    private FrameGrabber open() {
        List<Supplier<FrameGrabber>> candidates = List.of(
                () -> new OpenCVFrameGrabber(0),
                () -> new OpenCVFrameGrabber(1),
                () -> new VideoInputFrameGrabber(0),
                () -> new VideoInputFrameGrabber(1));
        String[] names = {"OpenCV camera 0", "OpenCV camera 1", "DirectShow camera 0", "DirectShow camera 1"};
        StringBuilder errors = new StringBuilder();
        for (int c = 0; c < candidates.size() && running; c++) {
            FrameGrabber g = null;
            boolean ok = false;
            try {
                status = "Trying " + names[c] + "...";
                repaint();
                g = candidates.get(c).get();
                g.start();
                for (int i = 0; i < 40 && running; i++) {              // warm-up: first frames are often empty
                    Frame f = null;
                    try { f = g.grab(); } catch (Exception ignored) { }
                    if (f != null && f.image != null) { ok = true; return g; }
                    Thread.sleep(100);
                }
                errors.append(names[c]).append(": no frames. ");
            } catch (Throwable t) {                                     // includes missing native libs
                errors.append(names[c]).append(": ").append(t.getMessage()).append(". ");
            } finally {
                if (!ok && g != null) { try { g.stop(); } catch (Throwable ignored) { } try { g.release(); } catch (Throwable ignored) { } }
            }
        }
        error = "Camera unavailable. Close other apps using the camera and allow camera access for desktop apps in Windows Settings. Details: " + errors;
        return null;
    }

    private void start() {
        if (running) return;
        running = true;
        Thread t = new Thread(() -> {
            FrameGrabber grabber = null;
            Java2DFrameConverter conv = new Java2DFrameConverter();
            try {
                grabber = open();
                if (grabber == null) { repaint(); return; }
                status = null;
                int failures = 0;
                while (running) {
                    Frame f;
                    try { f = grabber.grab(); } catch (Exception e) { f = null; }
                    if (f == null || f.image == null) {
                        if (++failures > 100) { error = "Camera stopped delivering frames."; repaint(); return; }
                        Thread.sleep(50);
                        continue;
                    }
                    failures = 0;
                    BufferedImage img = conv.getBufferedImage(f);
                    if (img == null) continue;
                    BufferedImage copy = new BufferedImage(img.getWidth(), img.getHeight(), BufferedImage.TYPE_3BYTE_BGR);
                    Graphics2D g = copy.createGraphics(); g.drawImage(img, 0, 0, null); g.dispose();
                    latest = copy;
                    repaint();
                }
            } catch (Throwable e) {
                error = "Camera error: " + e;
                repaint();
            } finally {
                if (grabber != null) { try { grabber.stop(); } catch (Throwable ignored) { } try { grabber.release(); } catch (Throwable ignored) { } }
            }
        }, "camera-capture");
        t.setDaemon(true);
        t.start();
    }

    /** Latest frame (null if the camera has not produced one yet). */
    public BufferedImage snapshot() { return latest; }

    @Override protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        BufferedImage img = latest;
        g.setColor(Color.WHITE);
        if (error != null) { drawWrapped(g, error); return; }
        if (img == null) { g.drawString(status == null ? "Waiting for camera..." : status, 16, 24); return; }
        double s = Math.min((double) getWidth() / img.getWidth(), (double) getHeight() / img.getHeight());
        int w = (int) (img.getWidth() * s), h = (int) (img.getHeight() * s);
        int x = (getWidth() - w) / 2, y = (getHeight() - h) / 2;
        g.drawImage(img, x + w, y, -w, h, null);      // mirrored, like a selfie preview
    }

    private void drawWrapped(Graphics g, String text) {
        FontMetrics fm = g.getFontMetrics();
        int y = 24, max = Math.max(200, getWidth() - 32);
        StringBuilder line = new StringBuilder();
        for (String word : text.split(" ")) {
            if (fm.stringWidth(line + word) > max) { g.drawString(line.toString(), 16, y); y += fm.getHeight() + 2; line.setLength(0); }
            line.append(word).append(' ');
        }
        g.drawString(line.toString(), 16, y);
    }
}