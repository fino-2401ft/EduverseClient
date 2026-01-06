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

@Slf4j
public class UDPChatReceiver {
    private DatagramSocket socket;
    private ExecutorService executorService;
    private boolean isRunning = false;

    // Header: senderId (36) + conversationId (36) + messageType (4) + contentLength (4) = 80 bytes
    private static final int HEADER_SIZE = 80;
    private static final int MAX_PACKET_SIZE = 65000;

    // Callbacks
    private TextMessageCallback textMessageCallback;
    private FileTransferCallback fileCallback;

    // File buffer: senderId + "|" + conversationId -> FileTransferState
    private Map<String, FileTransferState> fileTransfers = new ConcurrentHashMap<>();

    /**
     * Callback interface for text messages
     */
    public interface TextMessageCallback {
        void accept(String senderId, String conversationId, String message);
    }

    /**
     * Callback interface for file transfers
     */
    public interface FileTransferCallback {
        void onFileStart(String senderId, String conversationId, String fileName, int fileSize, int totalChunks);
        void onFileChunk(String senderId, String conversationId, int chunkIndex, int totalChunks);
        void onFileComplete(String senderId, String conversationId, String fileName, byte[] fileData);
    }

    private static class FileTransferState {
        String fileName;
        int fileSize;
        int totalChunks;
        Map<Integer, byte[]> chunks = new ConcurrentHashMap<>();
    }

    public UDPChatReceiver(DatagramSocket socket) {
        this.socket = socket;
        this.executorService = Executors.newFixedThreadPool(2);
    }

    public void start(TextMessageCallback textMessageCallback, FileTransferCallback fileCallback) {
        this.textMessageCallback = textMessageCallback;
        this.fileCallback = fileCallback;
        this.isRunning = true;

        executorService.submit(() -> {
            byte[] buffer = new byte[MAX_PACKET_SIZE];

            while (isRunning) {
                try {
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                    socket.receive(packet);
                    if (packet.getLength() < HEADER_SIZE) {
                        log.warn("⚠️ Received malformed packet (too short): {} bytes", packet.getLength());
                        continue; // Bỏ qua gói tin lỗi
                    }
                    // Parse packet
                    ByteBuffer byteBuffer = ByteBuffer.wrap(packet.getData(), 0, packet.getLength());

                    // Header
                    byte[] senderIdBytes = new byte[36];
                    byteBuffer.get(senderIdBytes);
                    String senderId = new String(senderIdBytes, StandardCharsets.UTF_8).trim();

                    byte[] conversationIdBytes = new byte[36];
                    byteBuffer.get(conversationIdBytes);
                    String conversationId = new String(conversationIdBytes, StandardCharsets.UTF_8).trim();

                    int messageType = byteBuffer.getInt();
                    int contentLength = byteBuffer.getInt();

                    // Content
                    byte[] content = new byte[contentLength];
                    byteBuffer.get(content);

                    // Handle by type
                    switch (messageType) {
                        case 0: // TEXT
                            handleTextMessage(senderId, conversationId, content);
                            break;
                        case 1: // FILE_START
                            handleFileStart(senderId, conversationId, content);
                            break;
                        case 2: // FILE_CHUNK
                            handleFileChunk(senderId, conversationId, content);
                            break;
                        case 3: // FILE_END
                            handleFileEnd(senderId, conversationId, content);
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

    private void handleTextMessage(String senderId, String conversationId, byte[] content) {
        String message = new String(content, StandardCharsets.UTF_8);
        log.info("📥 Received chat: senderId={}, conversationId={}, msg={}", senderId, conversationId, message);

        if (textMessageCallback != null) {
            textMessageCallback.accept(senderId, conversationId, message);
        }
    }

    private void handleFileStart(String senderId, String conversationId, byte[] content) {
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

        String transferKey = senderId + "|" + conversationId;
        fileTransfers.put(transferKey, state);

        log.info("📥 FILE_START: {} ({} bytes, {} chunks) from {} in conversation {}", fileName, fileSize, totalChunks, senderId, conversationId);

        if (fileCallback != null) {
            fileCallback.onFileStart(senderId, conversationId, fileName, fileSize, totalChunks);
        }
    }

    private void handleFileChunk(String senderId, String conversationId, byte[] content) {
        ByteBuffer buffer = ByteBuffer.wrap(content);
        int chunkIndex = buffer.getInt();
        int totalChunks = buffer.getInt();

        byte[] chunkData = new byte[content.length - 8];
        buffer.get(chunkData);

        String transferKey = senderId + "|" + conversationId;
        FileTransferState state = fileTransfers.get(transferKey);
        if (state == null) {
            log.error("❌ FILE_CHUNK without FILE_START for key: {}", transferKey);
            return;
        }

        state.chunks.put(chunkIndex, chunkData);

        log.debug("📥 FILE_CHUNK {}/{} from {} in conversation {}", chunkIndex + 1, totalChunks, senderId, conversationId);

        if (fileCallback != null) {
            fileCallback.onFileChunk(senderId, conversationId, chunkIndex, totalChunks);
        }
    }

    private void handleFileEnd(String senderId, String conversationId, byte[] content) {
        String fileName = new String(content, StandardCharsets.UTF_8);

        String transferKey = senderId + "|" + conversationId;
        FileTransferState state = fileTransfers.get(transferKey);
        if (state == null) {
            log.error("❌ FILE_END without FILE_START for key: {}", transferKey);
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

        log.info("✅ FILE_END: {} - Reassembled {} bytes from {} in conversation {}", fileName, fileData.length, senderId, conversationId);

        if (fileCallback != null) {
            fileCallback.onFileComplete(senderId, conversationId, fileName, fileData);
        }

        fileTransfers.remove(transferKey);
    }

    public void stop() {
        isRunning = false;

        // Shutdown executor service properly
        if (executorService != null) {
            executorService.shutdown();
            try {
                // Wait for tasks to complete
                if (!executorService.awaitTermination(500, TimeUnit.MILLISECONDS)) {
                    executorService.shutdownNow();
                    // Wait again for forced shutdown
                    if (!executorService.awaitTermination(200, TimeUnit.MILLISECONDS)) {
                        log.warn("ExecutorService did not terminate");
                    }
                }
            } catch (InterruptedException e) {
                executorService.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }

        // Don't close socket here - MediaStreamManager will handle it
        // Closing socket here can cause issues if MediaStreamManager still needs it

        fileTransfers.clear();

        log.info("🛑 UDP Chat Receiver stopped");
    }
}
