package org.example.eduverseclient.network.udp;

import lombok.extern.slf4j.Slf4j;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.ByteBuffer;

@Slf4j
public class UDPAudioSender {
    private DatagramSocket socket;
    private String userId;
    
    public UDPAudioSender(String userId, int localPort) {
        this.userId = userId;
        try {
            socket = new DatagramSocket(localPort);
            log.info("✅ UDPAudioSender started on port {}", localPort);
        } catch (Exception e) {
            log.error("❌ Failed to create UDP socket", e);
        }
    }
    
    /**
     * Gửi audio packet đến HOST
     */
    public void sendAudio(byte[] audioData, String hostIP, int hostPort) {
        if (socket == null || audioData == null || audioData.length == 0) {
            return;
        }
        
        try {
            long timestamp = System.currentTimeMillis();
            
            // Create packet with header:
            // [userId_length(4)][userId][timestamp(8)][audio_data]
            byte[] userIdBytes = userId.getBytes();
            ByteBuffer buffer = ByteBuffer.allocate(12 + userIdBytes.length + audioData.length);
            
            buffer.putInt(userIdBytes.length);
            buffer.put(userIdBytes);
            buffer.putLong(timestamp);
            buffer.put(audioData);
            
            byte[] packet = buffer.array();
            
            DatagramPacket udpPacket = new DatagramPacket(
                packet,
                packet.length,
                InetAddress.getByName(hostIP),
                hostPort
            );
            
            socket.send(udpPacket);
            
            log.debug("📤 Sent audio: {} bytes to {}:{}", audioData.length, hostIP, hostPort);
            
        } catch (Exception e) {
            log.error("❌ Send audio error", e);
        }
    }
    
    public void close() {
        if (socket != null && !socket.isClosed()) {
            socket.close();
            log.info("🛑 UDPAudioSender closed");
        }
    }
}