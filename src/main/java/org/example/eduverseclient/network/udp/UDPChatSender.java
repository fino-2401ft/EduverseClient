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
    private final String myUserId;

    public UDPChatSender(DatagramSocket socket, String myUserId) {
        this.socket = socket;
        this.myUserId = myUserId;
    }
    
    // Header: senderId (36) + conversationId (36) + messageType (4) + contentLength (4) = 80 bytes
    private static final int HEADER_SIZE = 80;
    private static final int MAX_PACKET_SIZE = 65000;

    // ==================================================================
    // 1. PUBLIC METHODS (Giao diện cho bên ngoài gọi)
    // ==================================================================

    /**
     * Gửi tin nhắn text với conversationId
     */
    public void sendMessage(String conversationId, String message, String targetIP, int targetPort) {
        sendPacketInternal(this.myUserId, conversationId, 0, message, targetIP, targetPort);
    }

    /**
     * Chuyển tiếp tin nhắn của NGƯỜI KHÁC (Host -> Client khác)
     * @deprecated Not used in P2P messenger, kept for backward compatibility
     */
    @Deprecated
    public void forwardMessage(String originalSenderId, String conversationId, String message, String targetIP, int targetPort) {
        // This method doesn't make sense with conversationId, but keeping for compatibility

        sendPacketInternal(originalSenderId, conversationId, 0, message, targetIP, targetPort);
        log.warn("forwardMessage called without conversationId - this method is deprecated");
    }

    // ==================================================================
    // 2. FILE SENDING
    // ==================================================================

    /**
     * Gửi file với conversationId
     */
    public void sendFile(String conversationId, String fileName, byte[] fileData, String targetIP, int targetPort) {
        try {
            int totalChunks = (int) Math.ceil((double) fileData.length / (MAX_PACKET_SIZE - HEADER_SIZE - 100));
            sendFileStart(conversationId, fileName, fileData.length, totalChunks, targetIP, targetPort);

            int offset = 0;
            for (int i = 0; i < totalChunks; i++) {
                int chunkSize = Math.min(MAX_PACKET_SIZE - HEADER_SIZE - 100, fileData.length - offset);
                byte[] chunk = new byte[chunkSize];
                System.arraycopy(fileData, offset, chunk, 0, chunkSize);
                sendFileChunk(conversationId, i, totalChunks, chunk, targetIP, targetPort);
                offset += chunkSize;
                Thread.sleep(5); // Throttle nhẹ 5ms
            }
            sendFileEnd(conversationId, fileName, targetIP, targetPort);
            log.info("✅ Sent file {} ({} bytes)", fileName, fileData.length);
        } catch (Exception e) {
            log.error("❌ Failed to send file", e);
        }
    }

    private void sendFileStart(String conversationId, String fileName, int fileSize, int totalChunks, String targetIP, int targetPort) throws Exception {
        String metadata = String.format("%s|%d|%d", fileName, fileSize, totalChunks);
        byte[] metadataBytes = metadata.getBytes(StandardCharsets.UTF_8);
        ByteBuffer buffer = ByteBuffer.allocate(HEADER_SIZE + metadataBytes.length);

        // Header: senderId + conversationId + type + length
        buffer.put(padString(this.myUserId, 36).getBytes(StandardCharsets.UTF_8));
        buffer.put(padString(conversationId, 36).getBytes(StandardCharsets.UTF_8));
        buffer.putInt(1); // FILE_START
        buffer.putInt(metadataBytes.length);
        buffer.put(metadataBytes);

        DatagramPacket packet = new DatagramPacket(buffer.array(), buffer.array().length, InetAddress.getByName(targetIP), targetPort);
        socket.send(packet);
    }

    private void sendFileChunk(String conversationId, int chunkIndex, int totalChunks, byte[] chunk, String targetIP, int targetPort) throws Exception {
        ByteBuffer buffer = ByteBuffer.allocate(HEADER_SIZE + 8 + chunk.length);
        
        // Header: senderId + conversationId + type + length
        buffer.put(padString(this.myUserId, 36).getBytes(StandardCharsets.UTF_8));
        buffer.put(padString(conversationId, 36).getBytes(StandardCharsets.UTF_8));
        buffer.putInt(2); // FILE_CHUNK
        buffer.putInt(8 + chunk.length);
        buffer.putInt(chunkIndex);
        buffer.putInt(totalChunks);
        buffer.put(chunk);
        
        DatagramPacket packet = new DatagramPacket(buffer.array(), buffer.array().length, InetAddress.getByName(targetIP), targetPort);
        socket.send(packet);
    }

    private void sendFileEnd(String conversationId, String fileName, String targetIP, int targetPort) throws Exception {
        byte[] fileNameBytes = fileName.getBytes(StandardCharsets.UTF_8);
        ByteBuffer buffer = ByteBuffer.allocate(HEADER_SIZE + fileNameBytes.length);
        
        // Header: senderId + conversationId + type + length
        buffer.put(padString(this.myUserId, 36).getBytes(StandardCharsets.UTF_8));
        buffer.put(padString(conversationId, 36).getBytes(StandardCharsets.UTF_8));
        buffer.putInt(3); // FILE_END
        buffer.putInt(fileNameBytes.length);
        buffer.put(fileNameBytes);
        
        DatagramPacket packet = new DatagramPacket(buffer.array(), buffer.array().length, InetAddress.getByName(targetIP), targetPort);
        socket.send(packet);
    }

    // ==================================================================
    // 3. INTERNAL CORE LOGIC
    // ==================================================================

    private void sendPacketInternal(String senderId, String conversationId, int messageType, String message, String targetIP, int targetPort) {
        try {
            byte[] contentBytes = message.getBytes(StandardCharsets.UTF_8);

            if (contentBytes.length > MAX_PACKET_SIZE - HEADER_SIZE) {
                log.error("❌ Message too large: {} bytes", contentBytes.length);
                return;
            }

            // Build packet (Binary Protocol)
            ByteBuffer buffer = ByteBuffer.allocate(HEADER_SIZE + contentBytes.length);

            // --- HEADER ---
            // senderId (36 bytes)
            buffer.put(padString(senderId, 36).getBytes(StandardCharsets.UTF_8));
            // conversationId (36 bytes)
            buffer.put(padString(conversationId, 36).getBytes(StandardCharsets.UTF_8));
            // Type (4 bytes) - 0: TEXT
            buffer.putInt(messageType);
            // Length (4 bytes)
            buffer.putInt(contentBytes.length);

            // --- CONTENT ---
            buffer.put(contentBytes);

            byte[] packetData = buffer.array();

            // Send
            DatagramPacket udpPacket = new DatagramPacket(
                    packetData,
                    packetData.length,
                    InetAddress.getByName(targetIP),
                    targetPort
            );

            socket.send(udpPacket);
            // log.debug("📤 Sent chat to {}:{}", targetIP, targetPort);

        } catch (Exception e) {
            log.error("❌ Failed to send chat message", e);
        }
    }

    private String padString(String str, int length) {
        if (str == null) str = "";
        if (str.length() >= length) return str.substring(0, length);
        return String.format("%-" + length + "s", str);
    }

    public void close() {
        // Không đóng socket ở đây nếu socket được dùng chung (Shared Socket)
        // Chỉ log thôi
        log.info("🛑 UDP Chat Sender stopping logic");
    }
}
