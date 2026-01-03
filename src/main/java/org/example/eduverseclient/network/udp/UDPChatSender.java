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
    private final String myUserId; // Đổi tên biến để rõ nghĩa hơn

    public UDPChatSender(DatagramSocket socket, String myUserId) {
        this.socket = socket;
        this.myUserId = myUserId;
    }
        //44 (36 ID + 4 Type + 4 Length)
    private static final int HEADER_SIZE = 44;
    private static final int MAX_PACKET_SIZE = 65000;

    // ==================================================================
    // 1. PUBLIC METHODS (Giao diện cho bên ngoài gọi)
    // ==================================================================

    /**
     * Gửi tin nhắn của CHÍNH MÌNH (Client -> Host)
     */
    public void sendMessage(String message, String targetIP, int targetPort) {
        // Dùng ID của chính mình
        sendPacketInternal(this.myUserId, message, targetIP, targetPort);
    }

    /**
     * Chuyển tiếp tin nhắn của NGƯỜI KHÁC (Host -> Client khác)
     * Hàm này giờ đây đã TUÂN THỦ ĐÚNG giao thức Binary
     */
    public void forwardMessage(String originalSenderId, String message, String targetIP, int targetPort) {
        // Dùng ID của người gửi gốc
        sendPacketInternal(originalSenderId, message, targetIP, targetPort);
    }

    // ==================================================================
    // 2. INTERNAL CORE LOGIC (Logic đóng gói Binary chuẩn)
    // ==================================================================

    private void sendPacketInternal(String idToEmbed, String message, String targetIP, int targetPort) {
        try {
            byte[] contentBytes = message.getBytes(StandardCharsets.UTF_8);

            if (contentBytes.length > MAX_PACKET_SIZE - HEADER_SIZE) {
                log.error("❌ Message too large: {} bytes", contentBytes.length);
                return;
            }

            // Build packet (Binary Protocol)
            ByteBuffer buffer = ByteBuffer.allocate(HEADER_SIZE + contentBytes.length);

            // --- HEADER ---
            // ID (36 bytes)
            buffer.put(padString(idToEmbed, 36).getBytes(StandardCharsets.UTF_8));
            // Type (4 bytes) - 0: TEXT
            buffer.putInt(0);
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

    // ... (Giữ nguyên logic gửi File như cũ vì nó không liên quan đến Chat Text) ...
    public void sendFile(String fileName, byte[] fileData, String targetIP, int targetPort) {
        // ... (Code sendFile cũ của bạn giữ nguyên ở đây) ...
        try {
            int totalChunks = (int) Math.ceil((double) fileData.length / (MAX_PACKET_SIZE - HEADER_SIZE - 100));
            sendFileStart(fileName, fileData.length, totalChunks, targetIP, targetPort);

            int offset = 0;
            for (int i = 0; i < totalChunks; i++) {
                int chunkSize = Math.min(MAX_PACKET_SIZE - HEADER_SIZE - 100, fileData.length - offset);
                byte[] chunk = new byte[chunkSize];
                System.arraycopy(fileData, offset, chunk, 0, chunkSize);
                sendFileChunk(i, totalChunks, chunk, targetIP, targetPort);
                offset += chunkSize;
                Thread.sleep(5); // Throttle nhẹ 5ms
            }
            sendFileEnd(fileName, targetIP, targetPort);
            log.info("✅ Sent file {} ({} bytes)", fileName, fileData.length);
        } catch (Exception e) {
            log.error("❌ Failed to send file", e);
        }
    }

    // Các hàm private sendFileStart, sendFileChunk, sendFileEnd giữ nguyên...
    // Chỉ cần đảm bảo chúng dùng biến this.myUserId (hoặc idToEmbed nếu muốn forward file sau này)

    // Copy lại các hàm helper sendFileStart/Chunk/End vào đây...
    // Lưu ý: Trong sendFileStart/Chunk/End cũ, bạn đang dùng `senderId`.
    // Hãy đổi thành dùng `this.myUserId` vì file luôn do chính chủ gửi.

    private void sendFileStart(String fileName, int fileSize, int totalChunks, String targetIP, int targetPort) throws Exception {
        String metadata = String.format("%s|%d|%d", fileName, fileSize, totalChunks);
        byte[] metadataBytes = metadata.getBytes(StandardCharsets.UTF_8);
        ByteBuffer buffer = ByteBuffer.allocate(HEADER_SIZE + metadataBytes.length);

        // Dùng myUserId
        buffer.put(padString(this.myUserId, 16).getBytes(StandardCharsets.UTF_8));
        buffer.putInt(1); // FILE_START
        buffer.putInt(metadataBytes.length);
        buffer.put(metadataBytes);

        DatagramPacket packet = new DatagramPacket(buffer.array(), buffer.array().length, InetAddress.getByName(targetIP), targetPort);
        socket.send(packet);
    }

    // (Làm tương tự cho sendFileChunk và sendFileEnd với this.myUserId)
    private void sendFileChunk(int chunkIndex, int totalChunks, byte[] chunk, String targetIP, int targetPort) throws Exception {
        ByteBuffer buffer = ByteBuffer.allocate(HEADER_SIZE + 8 + chunk.length);
        buffer.put(padString(this.myUserId, 16).getBytes(StandardCharsets.UTF_8));
        buffer.putInt(2); // FILE_CHUNK
        buffer.putInt(8 + chunk.length);
        buffer.putInt(chunkIndex);
        buffer.putInt(totalChunks);
        buffer.put(chunk);
        DatagramPacket packet = new DatagramPacket(buffer.array(), buffer.array().length, InetAddress.getByName(targetIP), targetPort);
        socket.send(packet);
    }

    private void sendFileEnd(String fileName, String targetIP, int targetPort) throws Exception {
        byte[] fileNameBytes = fileName.getBytes(StandardCharsets.UTF_8);
        ByteBuffer buffer = ByteBuffer.allocate(HEADER_SIZE + fileNameBytes.length);
        buffer.put(padString(this.myUserId, 16).getBytes(StandardCharsets.UTF_8));
        buffer.putInt(3); // FILE_END
        buffer.putInt(fileNameBytes.length);
        buffer.put(fileNameBytes);
        DatagramPacket packet = new DatagramPacket(buffer.array(), buffer.array().length, InetAddress.getByName(targetIP), targetPort);
        socket.send(packet);
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