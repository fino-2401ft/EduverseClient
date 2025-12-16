package org.example.eduverseclient.network.udp;

import javafx.application.Platform;
import lombok.extern.slf4j.Slf4j;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.nio.ByteBuffer;
import java.util.function.BiConsumer;

@Slf4j
public class UDPAudioReceiver {
    private DatagramSocket socket;
    private Thread receiveThread;
    private boolean isRunning = false;
    
    private BiConsumer<String, byte[]> audioCallback;
    
    private static final int MAX_PACKET_SIZE = 8192;
    
    public UDPAudioReceiver(int port) {
        try {
            socket = new DatagramSocket(port);
            log.info("✅ UDPAudioReceiver listening on port {}", port);
        } catch (Exception e) {
            log.error("❌ Failed to create UDP socket", e);
        }
    }
    
    /**
     * Start receiving
     */
    public void start(BiConsumer<String, byte[]> audioCallback) {
        if (socket == null) return;
        
        this.audioCallback = audioCallback;
        isRunning = true;
        
        receiveThread = new Thread(this::receiveLoop);
        receiveThread.setDaemon(true);
        receiveThread.start();
        
        log.info("✅ UDPAudioReceiver started");
    }
    
    private void receiveLoop() {
        byte[] buffer = new byte[MAX_PACKET_SIZE];
        
        while (isRunning) {
            try {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);
                
                processPacket(packet.getData(), packet.getLength());
                
            } catch (Exception e) {
                if (isRunning) {
                    log.error("❌ Receive error", e);
                }
            }
        }
    }
    
    private void processPacket(byte[] data, int length) {
        try {
            ByteBuffer buffer = ByteBuffer.wrap(data, 0, length);
            
            // Parse header
            int userIdLength = buffer.getInt();
            byte[] userIdBytes = new byte[userIdLength];
            buffer.get(userIdBytes);
            String userId = new String(userIdBytes);
            
            long timestamp = buffer.getLong();
            
            // Extract audio data
            int audioDataLength = length - 12 - userIdLength;
            byte[] audioData = new byte[audioDataLength];
            buffer.get(audioData);
            
            // Callback
            if (audioCallback != null) {
                audioCallback.accept(userId, audioData);
            }
            
            log.debug("📥 Received audio from {}: {} bytes", userId, audioDataLength);
            
        } catch (Exception e) {
            log.error("❌ Process packet error", e);
        }
    }
    
    public void stop() {
        isRunning = false;
        
        if (socket != null && !socket.isClosed()) {
            socket.close();
        }
        
        log.info("🛑 UDPAudioReceiver stopped");
    }
}