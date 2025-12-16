package org.example.eduverseclient.media;

import lombok.extern.slf4j.Slf4j;

import javax.sound.sampled.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;

@Slf4j
public class AudioPlayer {
    private SourceDataLine speaker;
    private ExecutorService executor;
    private BlockingQueue<byte[]> audioQueue;
    private boolean isRunning = false;
    
    private static final float SAMPLE_RATE = 16000.0f;
    private static final int SAMPLE_SIZE = 16;
    private static final int CHANNELS = 1;
    private static final boolean SIGNED = true;
    private static final boolean BIG_ENDIAN = false;
    
    private AudioFormat audioFormat;
    
    public AudioPlayer() {
        audioFormat = new AudioFormat(
            SAMPLE_RATE,
            SAMPLE_SIZE,
            CHANNELS,
            SIGNED,
            BIG_ENDIAN
        );
        
        try {
            DataLine.Info info = new DataLine.Info(SourceDataLine.class, audioFormat);
            
            if (!AudioSystem.isLineSupported(info)) {
                log.error("❌ Speaker line not supported");
                return;
            }
            
            speaker = (SourceDataLine) AudioSystem.getLine(info);
            audioQueue = new LinkedBlockingQueue<>(50); // Buffer 50 packets
            
            log.info("✅ Audio player initialized");
            
        } catch (Exception e) {
            log.error("❌ Failed to initialize speaker", e);
        }
    }
    
    /**
     * Start playing audio
     */
    public void start() {
        if (speaker == null) {
            log.error("❌ Speaker not available");
            return;
        }
        
        try {
            speaker.open(audioFormat);
            speaker.start();
            
            isRunning = true;
            executor = Executors.newSingleThreadExecutor();
            executor.submit(this::playbackLoop);
            
            log.info("✅ Audio player started");
            
        } catch (Exception e) {
            log.error("❌ Failed to start speaker", e);
        }
    }
    
    /**
     * Play audio data
     */
    public void play(byte[] audioData) {
        if (!isRunning) return;
        
        try {
            // Add to queue (drop if full)
            if (!audioQueue.offer(audioData)) {
                log.debug("⚠️ Audio queue full, dropping packet");
            }
        } catch (Exception e) {
            log.error("❌ Queue error", e);
        }
    }
    
    /**
     * Playback loop
     */
    private void playbackLoop() {
        while (isRunning) {
            try {
                byte[] audioData = audioQueue.take();
                
                if (audioData.length > 0) {
                    speaker.write(audioData, 0, audioData.length);
                }
                
            } catch (InterruptedException e) {
                if (isRunning) {
                    log.error("❌ Playback interrupted", e);
                }
            } catch (Exception e) {
                log.error("❌ Playback error", e);
            }
        }
    }
    
    /**
     * Stop playing
     */
    public void stop() {
        isRunning = false;
        
        if (executor != null) {
            executor.shutdownNow();
        }
        
        if (speaker != null) {
            speaker.drain();
            speaker.stop();
            speaker.close();
        }
        
        if (audioQueue != null) {
            audioQueue.clear();
        }
        
        log.info("🛑 Audio player stopped");
    }
}