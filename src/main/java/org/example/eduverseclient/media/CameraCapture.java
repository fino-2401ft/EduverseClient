package org.example.eduverseclient.media;

import com.github.sarxos.webcam.Webcam;
import javafx.application.Platform;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.image.Image;
import lombok.extern.slf4j.Slf4j;

import javax.imageio.ImageIO;
import java.awt.Dimension;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

@Slf4j
public class CameraCapture {
    private Webcam webcam;
    private ScheduledExecutorService executor;
    private Consumer<byte[]> frameCallback;
    private Consumer<Image> previewCallback;
    private boolean isRunning = false;
    
    private static final int WIDTH = 640;
    private static final int HEIGHT = 480;
    private static final int FPS = 15;
    
    public CameraCapture() {
        webcam = Webcam.getDefault();
        if (webcam != null) {
            webcam.setViewSize(new Dimension(WIDTH, HEIGHT));
            log.info("✅ Webcam found: {}", webcam.getName());
        } else {
            log.warn("⚠️ No webcam found");
        }
    }
    
    public void start(Consumer<byte[]> frameCallback, Consumer<Image> previewCallback) {
        if (webcam == null) {
            log.error("❌ No webcam available");
            return;
        }
        
        this.frameCallback = frameCallback;
        this.previewCallback = previewCallback;
        
        if (!webcam.isOpen()) {
            webcam.open();
        }
        
        isRunning = true;
        executor = Executors.newScheduledThreadPool(1);
        
        int intervalMs = 1000 / FPS;
        
        executor.scheduleAtFixedRate(() -> {
            try {
                if (isRunning && webcam.isOpen()) {
                    BufferedImage image = webcam.getImage();
                    
                    if (image != null) {
                        byte[] frameBytes = encodeFrame(image);
                        
                        if (frameCallback != null) {
                            frameCallback.accept(frameBytes);
                        }
                        
                        if (previewCallback != null) {
                            Image fxImage = SwingFXUtils.toFXImage(image, null);
                            Platform.runLater(() -> previewCallback.accept(fxImage));
                        }
                    }
                }
            } catch (Exception e) {
                log.error("Capture error", e);
            }
        }, 0, intervalMs, TimeUnit.MILLISECONDS);
        
        log.info("✅ Camera capture started - {}x{} @ {}fps", WIDTH, HEIGHT, FPS);
    }
    
    public void stop() {
        isRunning = false;
        
        if (executor != null) {
            executor.shutdown();
        }
        
        if (webcam != null && webcam.isOpen()) {
            webcam.close();
        }
        
        log.info("🛑 Camera capture stopped");
    }
    
    private byte[] encodeFrame(BufferedImage image) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(image, "jpg", baos);
            return baos.toByteArray();
        } catch (Exception e) {
            log.error("Encode error", e);
            return new byte[0];
        }
    }
    
    public boolean isAvailable() {
        return webcam != null;
    }
}