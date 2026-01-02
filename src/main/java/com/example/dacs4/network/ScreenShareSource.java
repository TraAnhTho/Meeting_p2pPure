package com.example.dacs4.network;

import javafx.embed.swing.SwingFXUtils;
import javafx.scene.image.Image;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.function.Consumer;

/**
 * Screen share source: captures screen using Robot, resizes, JPEG-encodes, and sends via callback.
 */
public class ScreenShareSource {

    private Thread captureThread;
    private volatile boolean running;

    private final int targetW;
    private final int targetH;
    private final int fps;
    private final float jpegQuality;

    private Consumer<Image> localPreviewCallback; // preview local (optional)
    private Consumer<byte[]> frameCallback; // send encoded JPEG bytes

    public ScreenShareSource(int targetW, int targetH, int fps, float jpegQuality) {
        this.targetW = targetW;
        this.targetH = targetH;
        this.fps = Math.max(1, fps);
        this.jpegQuality = jpegQuality;
    }

    public void setLocalPreviewCallback(Consumer<Image> cb) {
        this.localPreviewCallback = cb;
    }

    public void setFrameCallback(Consumer<byte[]> cb) {
        this.frameCallback = cb;
    }

    public void start() {
        if (running) return;

        running = true;

        captureThread = new Thread(() -> {
            try {
                GraphicsDevice gd = GraphicsEnvironment
                        .getLocalGraphicsEnvironment()
                        .getDefaultScreenDevice();

                Rectangle bounds = gd.getDefaultConfiguration().getBounds();
                Robot robot = new Robot(gd);

                long delayMs = 1000L / fps;

                while (running) {
                    long t0 = System.currentTimeMillis();

                    BufferedImage raw = robot.createScreenCapture(bounds);
                    BufferedImage resized = resize(raw, targetW, targetH);

                    // local preview (optional)
                    if (localPreviewCallback != null) {
                        Image fxImg = SwingFXUtils.toFXImage(resized, null);
                        localPreviewCallback.accept(fxImg);
                    }

                    try {
                        byte[] jpeg = JpegUtils.encodeJpeg(resized, jpegQuality);
                        if (frameCallback != null) {
                            frameCallback.accept(jpeg);
                        }
                    } catch (IOException e) {
                        System.err.println("❌ ScreenShare encode/send error: " + e.getMessage());
                    }

                    long spent = System.currentTimeMillis() - t0;
                    long sleep = delayMs - spent;
                    if (sleep > 0) Thread.sleep(sleep);
                }
            } catch (Exception e) {
                System.err.println("❌ ScreenShare capture failed: " + e.getMessage());
                e.printStackTrace();
            }
        }, "screen-share-capture");

        captureThread.setDaemon(true);
        captureThread.start();

        System.out.println("🖥 ScreenShare capture started");
    }

    public void stop() {
        running = false;
        if (captureThread != null) {
            captureThread.interrupt();
            try {
                captureThread.join(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            captureThread = null;
        }
        System.out.println("🛑 ScreenShare capture stopped");
    }

    public boolean isRunning() {
        return running;
    }

    private static BufferedImage resize(BufferedImage src, int w, int h) {
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_3BYTE_BGR);
        Graphics2D g2 = out.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2.drawImage(src, 0, 0, w, h, null);
        g2.dispose();
        return out;
    }
}

