package org.example.eduverseclient.media;


import common.constant.NetworkConfig;
import lombok.extern.slf4j.Slf4j;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.ByteBuffer;

@Slf4j
public class UDPVideoSender {
    private DatagramSocket socket;
    private String userId;
    
    public UDPVideoSender(String userId, int localPort) {
        this.userId = userId;
        try {
            socket = new DatagramSocket(localPort);
            log.info("✅ UDPVideoSender started on port {}", localPort);
        } catch (Exception e) {
            log.error("❌ Failed to create UDP socket", e);
        }
    }
    
    /**
     * Gửi video frame đến HOST
     */
    public void sendFrame(byte[] frameData, String hostIP, int hostPort) {
        if (socket == null || frameData == null || frameData.length == 0) {
            return;
        }
        
        try {
            // Split frame into packets if too large
            int maxPacketSize = NetworkConfig.VIDEO_PACKET_SIZE;
            int totalPackets = (int) Math.ceil((double) frameData.length / maxPacketSize);
            
            long timestamp = System.currentTimeMillis();
            
            for (int i = 0; i < totalPackets; i++) {
                int offset = i * maxPacketSize;
                int length = Math.min(maxPacketSize, frameData.length - offset);
                
                // Create packet with header:
                // [userId_length(4)][userId][timestamp(8)][seq(4)][total(4)][frame_data]
                byte[] userIdBytes = userId.getBytes();
                ByteBuffer buffer = ByteBuffer.allocate(20 + userIdBytes.length + length);
                
                buffer.putInt(userIdBytes.length);
                buffer.put(userIdBytes);
                buffer.putLong(timestamp);
                buffer.putInt(i);              // sequence number
                buffer.putInt(totalPackets);   // total packets
                buffer.put(frameData, offset, length);
                
                byte[] packet = buffer.array();
                
                DatagramPacket udpPacket = new DatagramPacket(
                    packet,
                    packet.length,
                    InetAddress.getByName(hostIP),
                    hostPort
                );
                
                socket.send(udpPacket);
            }
            
            log.debug("📤 Sent frame: {} packets to {}:{}", totalPackets, hostIP, hostPort);
            
        } catch (Exception e) {
            log.error("❌ Send frame error", e);
        }
    }
    
    public void close() {
        if (socket != null && !socket.isClosed()) {
            socket.close();
            log.info("🛑 UDPVideoSender closed");
        }
    }
}