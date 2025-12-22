package org.example.eduverseclient.network.udp;


import common.constant.NetworkConfig;
import lombok.extern.slf4j.Slf4j;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.ByteBuffer;

@Slf4j
public class UDPVideoSender {
    private DatagramSocket socket; // Không tạo mới, chỉ tham chiếu
    private String userId;

    // SỬA CONSTRUCTOR NÀY
    public UDPVideoSender(DatagramSocket socket, String userId) {
        this.socket = socket;
        this.userId = userId;
    }
    
    public void sendFrame(byte[] frameData, String hostIP, int hostPort) {
        if (socket == null || frameData == null || frameData.length == 0) {
            return;
        }
        
        try {
            int maxPacketSize = NetworkConfig.VIDEO_PACKET_SIZE;
            int totalPackets = (int) Math.ceil((double) frameData.length / maxPacketSize);
            
            long timestamp = System.currentTimeMillis();
            
            for (int i = 0; i < totalPackets; i++) {
                int offset = i * maxPacketSize;
                int length = Math.min(maxPacketSize, frameData.length - offset);
                
                byte[] userIdBytes = userId.getBytes();
                ByteBuffer buffer = ByteBuffer.allocate(20 + userIdBytes.length + length);
                
                buffer.putInt(userIdBytes.length);
                buffer.put(userIdBytes);
                buffer.putLong(timestamp);
                buffer.putInt(i);
                buffer.putInt(totalPackets);
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
            
            log.debug("📤 Sent frame: {} packets", totalPackets);
            
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