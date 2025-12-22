package org.example.eduverseclient.network.udp;

import common.constant.NetworkConfig;
import javafx.application.Platform;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.image.Image;
import lombok.extern.slf4j.Slf4j;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

@Slf4j
public class UDPVideoReceiver {
    private DatagramSocket socket;
    private Thread receiveThread;
    private boolean isRunning = false;
    
    private BiConsumer<String, Image> frameCallback;
    
    private final Map<String, Map<Integer, byte[]>> frameBuffers = new ConcurrentHashMap<>();
    private final Map<String, FrameMetadata> frameMetadata = new ConcurrentHashMap<>();

    private volatile boolean running = true;

    // SỬA CONSTRUCTOR NÀY
    public UDPVideoReceiver(DatagramSocket socket) {
        this.socket = socket;
    }
    
    public void start(BiConsumer<String, Image> frameCallback) {
        if (socket == null) return;
        
        this.frameCallback = frameCallback;
        isRunning = true;
        
        receiveThread = new Thread(this::receiveLoop);
        receiveThread.setDaemon(true);
        receiveThread.start();
        
        log.info("✅ UDPVideoReceiver started");
    }
    
    private void receiveLoop() {
        byte[] buffer = new byte[NetworkConfig.MAX_PACKET_SIZE];
        
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
            
            int userIdLength = buffer.getInt();
            byte[] userIdBytes = new byte[userIdLength];
            buffer.get(userIdBytes);
            String userId = new String(userIdBytes);
            
            long timestamp = buffer.getLong();
            int seqNum = buffer.getInt();
            int totalPackets = buffer.getInt();
            
            int frameDataLength = length - 20 - userIdLength;
            byte[] frameData = new byte[frameDataLength];
            buffer.get(frameData);
            
            String frameKey = userId + "_" + timestamp;
            
            frameBuffers.computeIfAbsent(frameKey, k -> new HashMap<>())
                       .put(seqNum, frameData);
            
            frameMetadata.putIfAbsent(frameKey, new FrameMetadata(userId, timestamp, totalPackets));
            
            Map<Integer, byte[]> packets = frameBuffers.get(frameKey);
            if (packets.size() == totalPackets) {
                reassembleFrame(frameKey, packets, totalPackets);
                frameBuffers.remove(frameKey);
                frameMetadata.remove(frameKey);
            }
            
        } catch (Exception e) {
            log.error("❌ Process packet error", e);
        }
    }
    
    private void reassembleFrame(String frameKey, Map<Integer, byte[]> packets, int totalPackets) {
        try {
            int totalSize = packets.values().stream()
                .mapToInt(p -> p.length)
                .sum();
            
            ByteBuffer buffer = ByteBuffer.allocate(totalSize);
            for (int i = 0; i < totalPackets; i++) {
                buffer.put(packets.get(i));
            }
            
            byte[] frameData = buffer.array();
            
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(frameData));
            
            if (image != null) {
                Image fxImage = SwingFXUtils.toFXImage(image, null);
                
                FrameMetadata metadata = frameMetadata.get(frameKey);
                
                if (frameCallback != null) {
                    Platform.runLater(() -> frameCallback.accept(metadata.userId, fxImage));
                }
                
                log.debug("📥 Received frame from {}", metadata.userId);
            }
            
        } catch (Exception e) {
            log.error("❌ Reassemble error", e);
        }
    }
    
    public void stop() {
        isRunning = false;
        running = false;
        
        // Đợi thread kết thúc
        if (receiveThread != null && receiveThread.isAlive()) {
            try {
                receiveThread.join(1000); // Đợi tối đa 1 giây
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("Interrupted while waiting for receive thread");
            }
        }
        
        // Đóng socket sau khi thread đã dừng
        if (socket != null && !socket.isClosed()) {
            try {
                socket.close();
            } catch (Exception e) {
                log.warn("Error closing socket", e);
            }
        }
        
        // Clear buffers
        frameBuffers.clear();
        frameMetadata.clear();
        
        log.info("🛑 UDPVideoReceiver stopped");
    }
    
    private static class FrameMetadata {
        String userId;
        long timestamp;
        int totalPackets;
        
        FrameMetadata(String userId, long timestamp, int totalPackets) {
            this.userId = userId;
            this.timestamp = timestamp;
            this.totalPackets = totalPackets;
        }
    }
}