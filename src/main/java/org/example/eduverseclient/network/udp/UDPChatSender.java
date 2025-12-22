package org.example.eduverseclient.network.udp;

import lombok.extern.slf4j.Slf4j;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

@Slf4j
public class UDPChatSender {
    private DatagramSocket socket;
    private final String senderId;


    // SỬA CONSTRUCTOR NÀY
    public UDPChatSender(DatagramSocket socket, String senderId) {
        this.socket = socket;
        this.senderId = senderId;
    }
    private static final int HEADER_SIZE = 24;
    private static final int MAX_PACKET_SIZE = 65000;


    /**
     * Send text message
     */
    public void sendMessage(String message, String targetIP, int targetPort) {
        try {
            byte[] contentBytes = message.getBytes(StandardCharsets.UTF_8);
            
            if (contentBytes.length > MAX_PACKET_SIZE - HEADER_SIZE) {
                log.error("❌ Message too large: {} bytes", contentBytes.length);
                return;
            }

            // Build packet
            ByteBuffer buffer = ByteBuffer.allocate(HEADER_SIZE + contentBytes.length);
            
            // Header
            buffer.put(padString(senderId, 16).getBytes(StandardCharsets.UTF_8));
            buffer.putInt(0); // messageType: TEXT
            buffer.putInt(contentBytes.length);
            
            // Content
            buffer.put(contentBytes);

            byte[] packet = buffer.array();

            // Send
            DatagramPacket udpPacket = new DatagramPacket(
                packet, 
                packet.length,
                InetAddress.getByName(targetIP), 
                targetPort
            );

            socket.send(udpPacket);
            
            log.debug("📤 Sent chat message to {}:{} - {} bytes", targetIP, targetPort, packet.length);

        } catch (Exception e) {
            log.error("❌ Failed to send chat message", e);
        }
    }

    /**
     * Send file (chunked)
     */
    public void sendFile(String fileName, byte[] fileData, String targetIP, int targetPort) {
        try {
            int totalChunks = (int) Math.ceil((double) fileData.length / (MAX_PACKET_SIZE - HEADER_SIZE - 100));
            
            // 1. Send FILE_START
            sendFileStart(fileName, fileData.length, totalChunks, targetIP, targetPort);
            
            // 2. Send FILE_CHUNKs
            int offset = 0;
            for (int i = 0; i < totalChunks; i++) {
                int chunkSize = Math.min(MAX_PACKET_SIZE - HEADER_SIZE - 100, fileData.length - offset);
                byte[] chunk = new byte[chunkSize];
                System.arraycopy(fileData, offset, chunk, 0, chunkSize);
                
                sendFileChunk(i, totalChunks, chunk, targetIP, targetPort);
                
                offset += chunkSize;
                
                Thread.sleep(10); // Throttle để không quá tải
            }
            
            // 3. Send FILE_END
            sendFileEnd(fileName, targetIP, targetPort);
            
            log.info("✅ Sent file {} ({} bytes) in {} chunks", fileName, fileData.length, totalChunks);

        } catch (Exception e) {
            log.error("❌ Failed to send file", e);
        }
    }

    private void sendFileStart(String fileName, int fileSize, int totalChunks, String targetIP, int targetPort) throws Exception {
        String metadata = String.format("%s|%d|%d", fileName, fileSize, totalChunks);
        byte[] metadataBytes = metadata.getBytes(StandardCharsets.UTF_8);

        ByteBuffer buffer = ByteBuffer.allocate(HEADER_SIZE + metadataBytes.length);
        buffer.put(padString(senderId, 16).getBytes(StandardCharsets.UTF_8));
        buffer.putInt(1); // FILE_START
        buffer.putInt(metadataBytes.length);
        buffer.put(metadataBytes);

        DatagramPacket packet = new DatagramPacket(
            buffer.array(), 
            buffer.array().length,
            InetAddress.getByName(targetIP), 
            targetPort
        );
        socket.send(packet);
        
        log.info("📤 FILE_START: {}", fileName);
    }

    private void sendFileChunk(int chunkIndex, int totalChunks, byte[] chunk, String targetIP, int targetPort) throws Exception {
        ByteBuffer buffer = ByteBuffer.allocate(HEADER_SIZE + 8 + chunk.length);
        buffer.put(padString(senderId, 16).getBytes(StandardCharsets.UTF_8));
        buffer.putInt(2); // FILE_CHUNK
        buffer.putInt(8 + chunk.length);
        buffer.putInt(chunkIndex);
        buffer.putInt(totalChunks);
        buffer.put(chunk);

        DatagramPacket packet = new DatagramPacket(
            buffer.array(), 
            buffer.array().length,
            InetAddress.getByName(targetIP), 
            targetPort
        );
        socket.send(packet);
        
        log.debug("📤 FILE_CHUNK {}/{}", chunkIndex + 1, totalChunks);
    }

    private void sendFileEnd(String fileName, String targetIP, int targetPort) throws Exception {
        byte[] fileNameBytes = fileName.getBytes(StandardCharsets.UTF_8);

        ByteBuffer buffer = ByteBuffer.allocate(HEADER_SIZE + fileNameBytes.length);
        buffer.put(padString(senderId, 16).getBytes(StandardCharsets.UTF_8));
        buffer.putInt(3); // FILE_END
        buffer.putInt(fileNameBytes.length);
        buffer.put(fileNameBytes);

        DatagramPacket packet = new DatagramPacket(
            buffer.array(), 
            buffer.array().length,
            InetAddress.getByName(targetIP), 
            targetPort
        );
        socket.send(packet);
        
        log.info("📤 FILE_END: {}", fileName);
    }

    private String padString(String str, int length) {
        if (str.length() >= length) {
            return str.substring(0, length);
        }
        return String.format("%-" + length + "s", str);
    }

    public void close() {
        if (socket != null && !socket.isClosed()) {
            socket.close();
            log.info("🛑 UDP Chat Sender closed");
        }
    }
}