package com.example.dacs4.network;

import com.github.sarxos.webcam.Webcam;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.image.Image;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.function.Consumer;

public class WebcamVideoSource {

    private final Webcam webcam;
    private volatile boolean running;
    private Thread captureThread;

    // callback cho UI preview (JavaFX Image)
    private Consumer<Image> localFrameCallback;

    // callback cho network (JPEG bytes)
    private Consumer<byte[]> encodedFrameCallback;

    public WebcamVideoSource() {
        webcam = Webcam.getDefault();
    }

    public boolean isAvailable() {
        return webcam != null;
    }

    public void setLocalFrameCallback(Consumer<Image> cb) {
        this.localFrameCallback = cb;
    }

    public void setEncodedFrameCallback(Consumer<byte[]> cb) {
        this.encodedFrameCallback = cb;
    }

    public void start() {
        if (!isAvailable() || running) return;

        webcam.open();
        running = true;

        captureThread = new Thread(() -> {
            while (running && webcam.isOpen()) {
                try {
                    BufferedImage awt = webcam.getImage();
                    if (awt != null) {

                        // ✅ 1. Convert sang JavaFX Image cho UI
                        if (localFrameCallback != null) {
                            Image fx = SwingFXUtils.toFXImage(awt, null);
                            localFrameCallback.accept(fx);
                        }

                        // ✅ 2. Encode JPEG cho network
                        if (encodedFrameCallback != null) {
                            ByteArrayOutputStream baos = new ByteArrayOutputStream();
                            ImageIO.write(awt, "jpg", baos);
                            encodedFrameCallback.accept(baos.toByteArray());
                        }
                    }

                    Thread.sleep(100); // ~10 FPS
                } catch (Exception e) {
                    System.err.println("Webcam error: " + e.getMessage());
                }
            }
        }, "webcam-capture");

        captureThread.setDaemon(true);
        captureThread.start();
        System.out.println("📷 Webcam capture started");
    }

    public void stop() {
        running = false;
        if (webcam != null && webcam.isOpen()) webcam.close();
        System.out.println("📷 Webcam capture stopped");
    }
}
