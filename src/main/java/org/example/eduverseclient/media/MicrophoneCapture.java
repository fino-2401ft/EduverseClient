package org.example.eduverseclient.media;

import lombok.extern.slf4j.Slf4j;

import javax.sound.sampled.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

@Slf4j
public class MicrophoneCapture {
    private TargetDataLine microphone;
    private ExecutorService executor;
    private Consumer<byte[]> audioCallback;
    private boolean isRunning = false;
    
    // Audio format configuration
    private static final float SAMPLE_RATE = 16000.0f;  // 16kHz
    private static final int SAMPLE_SIZE = 16;          // 16-bit
    private static final int CHANNELS = 1;              // Mono
    private static final boolean SIGNED = true;
    private static final boolean BIG_ENDIAN = false;
    
    private static final int BUFFER_SIZE = 3200;        // 200ms @ 16kHz
    
    private AudioFormat audioFormat;
    
    public MicrophoneCapture() {
        audioFormat = new AudioFormat(
            SAMPLE_RATE,
            SAMPLE_SIZE,
            CHANNELS,
            SIGNED,
            BIG_ENDIAN
        );
        
        try {
            DataLine.Info info = new DataLine.Info(TargetDataLine.class, audioFormat);
            
            if (!AudioSystem.isLineSupported(info)) {
                log.error("❌ Audio line not supported");
                return;
            }
            
            microphone = (TargetDataLine) AudioSystem.getLine(info);
            log.info("✅ Microphone initialized");
            
        } catch (Exception e) {
            log.error("❌ Failed to initialize microphone", e);
        }
    }
    
    /**
     * Start capturing audio
     */
    public void start(Consumer<byte[]> audioCallback) {
        if (microphone == null) {
            log.error("❌ Microphone not available");
            return;
        }
        
        this.audioCallback = audioCallback;
        
        try {
            microphone.open(audioFormat, BUFFER_SIZE);
            microphone.start();
            
            isRunning = true;
            executor = Executors.newSingleThreadExecutor();
            executor.submit(this::captureLoop);
            
            log.info("✅ Microphone capture started - {}Hz, {}bit, {} channel", 
                     SAMPLE_RATE, SAMPLE_SIZE, CHANNELS);
            
        } catch (Exception e) {
            log.error("❌ Failed to start microphone", e);
        }
    }
    
    /**
     * Capture loop
     */
    private void captureLoop() {
        byte[] buffer = new byte[BUFFER_SIZE];
        
        while (isRunning) {
            try {
                int bytesRead = microphone.read(buffer, 0, buffer.length);
                
                if (bytesRead > 0) {
                    // Copy buffer to avoid race condition
                    byte[] audioData = new byte[bytesRead];
                    System.arraycopy(buffer, 0, audioData, 0, bytesRead);
                    
                    // Callback
                    if (audioCallback != null) {
                        audioCallback.accept(audioData);
                    }
                }
                
            } catch (Exception e) {
                if (isRunning) {
                    log.error("❌ Capture error", e);
                }
            }
        }
    }
    
    /**
     * Stop capturing
     */
    public void stop() {
        isRunning = false;
        
        if (executor != null) {
            executor.shutdownNow();
        }
        
        if (microphone != null) {
            microphone.stop();
            microphone.close();
        }
        
        log.info("🛑 Microphone capture stopped");
    }
    
    public boolean isAvailable() {
        return microphone != null;
    }
}