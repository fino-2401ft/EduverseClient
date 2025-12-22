package org.example.eduverseclient.network.udp;

import lombok.extern.slf4j.Slf4j;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;

@Slf4j
public class UDPChatReceiver {
    private DatagramSocket socket;
    private ExecutorService executorService;
    private boolean isRunning = false;

    private static final int HEADER_SIZE = 24;
    private static final int MAX_PACKET_SIZE = 65000;

    // Callbacks
    private BiConsumer<String, String> textMessageCallback; // (senderId, message)
    private FileTransferCallback fileCallback;

    // File buffer: senderId -> FileTransferState
    private Map<String, FileTransferState> fileTransfers = new ConcurrentHashMap<>();

    public interface FileTransferCallback {
        void onFileStart(String senderId, String fileName, int fileSize, int totalChunks);
        void onFileChunk(String senderId, int chunkIndex, int totalChunks);
        void onFileComplete(String senderId, String fileName, byte[] fileData);
    }

    private static class FileTransferState {
        String fileName;
        int fileSize;
        int totalChunks;
        Map<Integer, byte[]> chunks = new ConcurrentHashMap<>();
    }

    // SỬA CONSTRUCTOR NÀY
    public UDPChatReceiver(DatagramSocket socket) {
        this.socket = socket;
        this.executorService = Executors.newFixedThreadPool(2);
    }


    public void start(BiConsumer<String, String> textMessageCallback, FileTransferCallback fileCallback) {
        this.textMessageCallback = textMessageCallback;
        this.fileCallback = fileCallback;
        this.isRunning = true;

        executorService.submit(() -> {
            byte[] buffer = new byte[MAX_PACKET_SIZE];

            while (isRunning) {
                try {
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                    socket.receive(packet);

                    // Parse packet
                    ByteBuffer byteBuffer = ByteBuffer.wrap(packet.getData(), 0, packet.getLength());

                    // Header
                    byte[] senderIdBytes = new byte[16];
                    byteBuffer.get(senderIdBytes);
                    String senderId = new String(senderIdBytes, StandardCharsets.UTF_8).trim();

                    int messageType = byteBuffer.getInt();
                    int contentLength = byteBuffer.getInt();

                    // Content
                    byte[] content = new byte[contentLength];
                    byteBuffer.get(content);

                    // Handle by type
                    switch (messageType) {
                        case 0: // TEXT
                            handleTextMessage(senderId, content);
                            break;
                        case 1: // FILE_START
                            handleFileStart(senderId, content);
                            break;
                        case 2: // FILE_CHUNK
                            handleFileChunk(senderId, content);
                            break;
                        case 3: // FILE_END
                            handleFileEnd(senderId, content);
                            break;
                        default:
                            log.warn("❓ Unknown message type: {}", messageType);
                    }

                } catch (Exception e) {
                    if (isRunning) {
                        log.error("❌ Error receiving chat packet", e);
                    }
                }
            }
        });

        log.info("✅ UDP Chat Receiver started");
    }

    private void handleTextMessage(String senderId, byte[] content) {
        String message = new String(content, StandardCharsets.UTF_8);
        log.info("📥 Received chat: {} - {}", senderId, message);

        if (textMessageCallback != null) {
            textMessageCallback.accept(senderId, message);
        }
    }

    private void handleFileStart(String senderId, byte[] content) {
        String metadata = new String(content, StandardCharsets.UTF_8);
        String[] parts = metadata.split("\\|");

        if (parts.length != 3) {
            log.error("❌ Invalid FILE_START metadata");
            return;
        }

        String fileName = parts[0];
        int fileSize = Integer.parseInt(parts[1]);
        int totalChunks = Integer.parseInt(parts[2]);

        FileTransferState state = new FileTransferState();
        state.fileName = fileName;
        state.fileSize = fileSize;
        state.totalChunks = totalChunks;

        fileTransfers.put(senderId, state);

        log.info("📥 FILE_START: {} ({} bytes, {} chunks)", fileName, fileSize, totalChunks);

        if (fileCallback != null) {
            fileCallback.onFileStart(senderId, fileName, fileSize, totalChunks);
        }
    }

    private void handleFileChunk(String senderId, byte[] content) {
        ByteBuffer buffer = ByteBuffer.wrap(content);
        int chunkIndex = buffer.getInt();
        int totalChunks = buffer.getInt();

        byte[] chunkData = new byte[content.length - 8];
        buffer.get(chunkData);

        FileTransferState state = fileTransfers.get(senderId);
        if (state == null) {
            log.error("❌ FILE_CHUNK without FILE_START");
            return;
        }

        state.chunks.put(chunkIndex, chunkData);

        log.debug("📥 FILE_CHUNK {}/{}", chunkIndex + 1, totalChunks);

        if (fileCallback != null) {
            fileCallback.onFileChunk(senderId, chunkIndex, totalChunks);
        }
    }

    private void handleFileEnd(String senderId, byte[] content) {
        String fileName = new String(content, StandardCharsets.UTF_8);

        FileTransferState state = fileTransfers.get(senderId);
        if (state == null) {
            log.error("❌ FILE_END without FILE_START");
            return;
        }

        // Reassemble file
        if (state.chunks.size() != state.totalChunks) {
            log.error("❌ Missing chunks: got {}/{}", state.chunks.size(), state.totalChunks);
            return;
        }

        byte[] fileData = new byte[state.fileSize];
        int offset = 0;

        for (int i = 0; i < state.totalChunks; i++) {
            byte[] chunk = state.chunks.get(i);
            if (chunk == null) {
                log.error("❌ Missing chunk {}", i);
                return;
            }

            System.arraycopy(chunk, 0, fileData, offset, chunk.length);
            offset += chunk.length;
        }

        log.info("✅ FILE_END: {} - Reassembled {} bytes", fileName, fileData.length);

        if (fileCallback != null) {
            fileCallback.onFileComplete(senderId, fileName, fileData);
        }

        fileTransfers.remove(senderId);
    }

    public void stop() {
        isRunning = false;

        // Shutdown executor service
        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdown();
            try {
                if (!executorService.awaitTermination(2, TimeUnit.SECONDS)) {
                    executorService.shutdownNow();
                }
            } catch (InterruptedException e) {
                executorService.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }

        // Đóng socket sau khi threads đã dừng
        if (socket != null && !socket.isClosed()) {
            try {
                socket.close();
            } catch (Exception e) {
                log.warn("Error closing socket", e);
            }
        }

        // Clear file transfers
        fileTransfers.clear();

        log.info("🛑 UDP Chat Receiver stopped");
    }
}