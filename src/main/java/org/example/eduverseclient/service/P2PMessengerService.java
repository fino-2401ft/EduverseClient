package org.example.eduverseclient.service;

import common.enums.MessageType;
import common.model.Conversation;
import common.model.Message;
import common.model.Peer;
import common.model.User;
import lombok.extern.slf4j.Slf4j;
import org.example.eduverseclient.RMIClient;
import org.example.eduverseclient.network.udp.UDPChatReceiver;
import org.example.eduverseclient.network.udp.UDPChatSender;
import org.example.eduverseclient.utils.MediaProcessor;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

@Slf4j
public class P2PMessengerService {
    private static P2PMessengerService instance;
    
    private final RMIClient rmiClient;
    private final CloudinaryService cloudinaryService;
    
    // UDP Components
    private UDPChatSender chatSender;
    private UDPChatReceiver chatReceiver;
    private java.net.DatagramSocket chatSocket;
    
    // State
    private User currentUser;
    private Peer myPeer;
    private boolean isInitialized = false;
    
    // Conversation cache
    private final Map<String, List<Peer>> conversationPeersCache = new ConcurrentHashMap<>();
    private final Map<String, Long> lastPeerUpdateTime = new ConcurrentHashMap<>();
    private static final long PEER_CACHE_TTL = 5000; // 5 seconds
    
    // Callbacks
    private BiConsumer<Message, Conversation> messageCallback;
    
    private P2PMessengerService() {
        this.rmiClient = RMIClient.getInstance();
        this.cloudinaryService = CloudinaryService.getInstance();
        this.currentUser = rmiClient.getCurrentUser();
        this.myPeer = rmiClient.getMyPeer();
    }
    
    public static synchronized P2PMessengerService getInstance() {
        if (instance == null) {
            instance = new P2PMessengerService();
        }
        return instance;
    }
    
    /**
     * Khởi tạo messenger service
     */
    public void initialize() {
        if (isInitialized) {
            log.warn("Messenger service already initialized");
            return;
        }
        
        try {
            currentUser = rmiClient.getCurrentUser();
            myPeer = rmiClient.getMyPeer();
            
            if (currentUser == null || myPeer == null) {
                log.error("Cannot initialize messenger: User not logged in");
                return;
            }
            
            // Tạo UDP socket cho messaging (dùng chatPort từ global peer)
            chatSocket = new java.net.DatagramSocket(myPeer.getChatPort());
            
            // Khởi tạo UDP sender/receiver
            chatSender = new UDPChatSender(chatSocket, currentUser.getUserId());
            chatReceiver = new UDPChatReceiver(chatSocket);
            
            // Start receiver với callbacks
            chatReceiver.start(
                this::handleTextMessage,
                new UDPChatReceiver.FileTransferCallback() {
                    @Override
                    public void onFileStart(String senderId, String conversationId, String fileName, int fileSize, int totalChunks) {
                        // File transfer started
                    }
                    
                    @Override
                    public void onFileChunk(String senderId, String conversationId, int chunkIndex, int totalChunks) {
                        // Chunk received
                    }
                    
                    @Override
                    public void onFileComplete(String senderId, String conversationId, String fileName, byte[] fileData) {
                        handleFileTransfer(senderId, conversationId, fileName, fileData);
                    }
                }
            );
            
            isInitialized = true;
            log.info("✅ P2P Messenger Service initialized on port {}", myPeer.getChatPort());
            
        } catch (Exception e) {
            log.error("❌ Failed to initialize messenger service", e);
        }
    }
    
    /**
     * Get chat socket for reuse by MediaStreamManager
     */
    public java.net.DatagramSocket getChatSocket() {
        return chatSocket;
    }
    
    /**
     * Check if messenger service is initialized
     */
    public boolean isInitialized() {
        return isInitialized && chatSocket != null && !chatSocket.isClosed();
    }
    
    /**
     * Set callback cho new messages
     */
    public void setMessageCallback(BiConsumer<Message, Conversation> callback) {
        this.messageCallback = callback;
    }
    
    /**
     * Gửi text message
     */
    public void sendMessage(String conversationId, String textContent) {
        if (!isInitialized) {
            log.error("Messenger not initialized");
            return;
        }
        
        try {
            // 1. Tạo Message object
            Message message = Message.builder()
                .messageId(UUID.randomUUID().toString())
                .conversationId(conversationId)
                .senderId(currentUser.getUserId())
                .type(MessageType.TEXT)
                .content(textContent)
                .timestamp(System.currentTimeMillis())
                .build();
            
            // 2. Trigger callback để hiển thị ngay trên UI
            if (messageCallback != null) {
                Conversation conv = getConversation(conversationId);
                messageCallback.accept(message, conv);
            }
            
            // 3. Gửi UDP P2P
            sendViaUDP(message, conversationId);
            
            // 4. Lưu Firebase (sender lưu, receiver không lưu)
            saveToFirebase(message);
            
        } catch (Exception e) {
            log.error("Send message failed", e);
        }
    }

    public void pauseReceiver() {
        if (chatReceiver != null) {
            chatReceiver.stop(); // Tạm dừng thread receive của Messenger
        }
    }

    public void resumeReceiver() {
        // Trường hợp 1: Socket đã bị đóng hoặc chưa từng khởi tạo -> Init lại từ đầu
        if (chatSocket == null || chatSocket.isClosed()) {
            log.warn("⚠️ Chat socket is closed/null. Re-initializing full service...");
            isInitialized = false;
            initialize();
            return;
        }

        // Trường hợp 2: Socket vẫn sống (vừa rời họp xong)
        log.info("▶️ Resuming P2P Messenger Receiver on existing socket...");

        // Đảm bảo receiver cũ đã dừng hẳn
        if (chatReceiver != null) {
            chatReceiver.stop();
        }

        // Khởi tạo Receiver mới gắn vào Socket cũ
        chatReceiver = new UDPChatReceiver(chatSocket);

        // Gắn lại các Callback (Logic y hệt như trong hàm initialize)
        chatReceiver.start(
                this::handleTextMessage,
                new UDPChatReceiver.FileTransferCallback() {
                    @Override
                    public void onFileStart(String senderId, String conversationId, String fileName, int fileSize, int totalChunks) {
                        // File transfer started logic
                    }

                    @Override
                    public void onFileChunk(String senderId, String conversationId, int chunkIndex, int totalChunks) {
                        // Chunk received logic
                    }

                    @Override
                    public void onFileComplete(String senderId, String conversationId, String fileName, byte[] fileData) {
                        handleFileTransfer(senderId, conversationId, fileName, fileData);
                    }
                }
        );

        // Đồng bộ lại tin nhắn từ Firebase để bù đắp cho khoảng thời gian bị Pause
        // fetchMissedMessages(); // (Optional: Nên làm thêm hàm này sau)
    }

    /**
     * Gửi file/image/video
     */
    public void sendFile(String conversationId, File file, MessageType type) {
        if (!isInitialized) {
            log.error("Messenger not initialized");
            return;
        }
        
        CompletableFuture.runAsync(() -> {
            try {
                // 1. Read file
                byte[] fileData = Files.readAllBytes(file.toPath());
                String fileName = file.getName();
                String fileType = MediaProcessor.getFileType(fileName);
                
                // 2. Process file (compress nếu là image)
                byte[] processedData = fileData;
                if (type == MessageType.IMAGE) {
                    processedData = MediaProcessor.compressImage(fileData);
                }
                
                // 3. Tạo Message object với content là fileName (tạm thời)
                Message message = Message.builder()
                    .messageId(UUID.randomUUID().toString())
                    .conversationId(conversationId)
                    .senderId(currentUser.getUserId())
                    .type(type)
                    .content(fileName) // Store fileName in content for now
                    .timestamp(System.currentTimeMillis())
                    .build();
                
                // Store fileName and upload result for later use
                final String finalFileName = fileName;
                final Message finalMessage = message;
                
                // 4. Trigger callback để hiển thị ngay trên UI (với fileName)
                if (messageCallback != null) {
                    Conversation conv = getConversation(conversationId);
                    messageCallback.accept(finalMessage, conv);
                }
                
                // 5. Gửi UDP P2P (real-time)
                sendFileViaUDP(finalMessage, processedData, conversationId, finalFileName);
                
                // 6. Upload Cloudinary và lưu Firebase (sender lưu, receiver không lưu)
                cloudinaryService.uploadFile(processedData, finalFileName, fileType)
                    .thenAccept(uploadResult -> {
                        // Update message content với URL (file/image/video URL)
                        finalMessage.setContent(uploadResult.getFileUrl());
                        // Lưu Firebase với URLs
                        saveToFirebase(finalMessage);
                    })
                    .exceptionally(e -> {
                        log.error("Cloudinary upload failed", e);
                        // Vẫn lưu message nhưng không có URLs
                        saveToFirebase(finalMessage);
                        return null;
                    });
                
            } catch (Exception e) {
                log.error("Send file failed", e);
            }
        });
    }
    
    /**
     * Gửi message qua UDP P2P
     */
    private void sendViaUDP(Message message, String conversationId) {
        try {
            List<Peer> peers = getConversationPeers(conversationId);
            log.info("Sending message via UDP to {} peers in conversation {}", peers.size(), conversationId);
            
            if (peers.isEmpty()) {
                log.warn("No peers found for conversation {}, message will only be saved to Firebase", conversationId);
                return;
            }
            
            for (Peer peer : peers) {
                if (!peer.getUserId().equals(currentUser.getUserId())) {
                    log.info("Sending UDP message to peer {} at {}:{}", peer.getUserId(), peer.getIpAddress(), peer.getChatPort());
                    chatSender.sendMessage(
                        conversationId,
                        message.getContent() != null ? message.getContent() : "",
                        peer.getIpAddress(),
                        peer.getChatPort()
                    );
                }
            }
        } catch (Exception e) {
            log.error("Failed to send via UDP", e);
        }
    }
    
    /**
     * Gửi file qua UDP P2P
     */
    private void sendFileViaUDP(Message message, byte[] fileData, String conversationId, String fileName) {
        try {
            List<Peer> peers = getConversationPeers(conversationId);
            
            for (Peer peer : peers) {
                if (!peer.getUserId().equals(currentUser.getUserId())) {
                    chatSender.sendFile(
                        conversationId,
                        fileName,
                        fileData,
                        peer.getIpAddress(),
                        peer.getChatPort()
                    );
                }
            }
        } catch (Exception e) {
            log.error("Failed to send file via UDP", e);
        }
    }
    
    /**
     * Lấy danh sách peers trong conversation
     */
    private List<Peer> getConversationPeers(String conversationId) {
        // Check cache
        Long lastUpdate = lastPeerUpdateTime.get(conversationId);
        if (lastUpdate != null && (System.currentTimeMillis() - lastUpdate) < PEER_CACHE_TTL) {
            List<Peer> cached = conversationPeersCache.get(conversationId);
            if (cached != null && !cached.isEmpty()) {
                return cached;
            }
        }
        
        try {
            // Query conversation từ server để lấy participants (sync)
            MessengerServiceHelper helper = MessengerServiceHelper.getInstance();
            Conversation conv = helper.getConversation(conversationId).get();
            
            if (conv != null && conv.getParticipants() != null) {
                List<Peer> peers = new ArrayList<>();
                
                // Lấy peer info cho mỗi participant
                for (String userId : conv.getParticipants()) {
                    try {
                        Peer peer = rmiClient.getPeerService().getGlobalPeer(userId);
                        if (peer != null && peer.isAlive(30000)) { // 30s timeout
                            peers.add(peer);
                        }
                    } catch (Exception e) {
                        log.warn("Failed to get peer for user {}: {}", userId, e.getMessage());
                    }
                }
                
                // Update cache
                conversationPeersCache.put(conversationId, peers);
                lastPeerUpdateTime.put(conversationId, System.currentTimeMillis());
                
                log.info("Found {} peers for conversation {}", peers.size(), conversationId);
                return peers;
            }
            
        } catch (Exception e) {
            log.error("Failed to get conversation peers", e);
        }
        
        return new ArrayList<>();
    }
    
    /**
     * Lưu message vào Firebase (async)
     */
    private void saveToFirebase(Message message) {
        CompletableFuture.runAsync(() -> {
            try {
                // Save text messages via ChatService
                if (message.getType() == MessageType.TEXT && message.getContent() != null) {
                    MessengerServiceHelper helper = MessengerServiceHelper.getInstance();
                    helper.saveMessageToFirebase(message).get();
                    log.info("✅ Message saved to Firebase: {}", message.getMessageId());
                } else {
                    // For file/image/video messages, save via RMI ChatService
                    // Use MessageDAO.sendMessage với type và content (URL)
                    MessengerServiceHelper helper = MessengerServiceHelper.getInstance();
                    helper.saveFileMessageToFirebase(message).get();
                    log.info("✅ File message saved to Firebase: {} with URL: {}", message.getMessageId(), message.getContent());
                }
            } catch (Exception e) {
                log.error("Failed to save message to Firebase", e);
            }
        });
    }
    
    /**
     * Handle text message từ UDP
     */
    private void handleTextMessage(String senderId, String conversationId, String messageText) {
        try {
            // Tạo Message object với conversationId từ packet
            Message message = Message.builder()
                .messageId(UUID.randomUUID().toString())
                .conversationId(conversationId)
                .senderId(senderId)
                .type(MessageType.TEXT)
                .content(messageText)
                .timestamp(System.currentTimeMillis())
                .build();
            
            // Trigger callback
            if (messageCallback != null) {
                Conversation conv = getConversation(conversationId);
                messageCallback.accept(message, conv);
            }
            
            // Note: Không lưu Firebase ở receiver - sender đã lưu rồi
            
        } catch (Exception e) {
            log.error("Handle text message failed", e);
        }
    }
    
    /**
     * Handle file transfer từ UDP
     */
    private void handleFileTransfer(String senderId, String conversationId, String fileName, byte[] fileData) {
        try {
            String fileType = MediaProcessor.getFileType(fileName);
            MessageType type = MediaProcessor.isImage(fileName) ? MessageType.IMAGE :
                              MediaProcessor.isVideo(fileName) ? MessageType.VIDEO :
                              MessageType.FILE;
            
            Message message = Message.builder()
                .messageId(UUID.randomUUID().toString())
                .conversationId(conversationId)
                .senderId(senderId)
                .type(type)
                .content(fileName) // Store fileName temporarily
                .timestamp(System.currentTimeMillis())
                .build();
            
            // Trigger callback
            if (messageCallback != null) {
                Conversation conv = getConversation(conversationId);
                messageCallback.accept(message, conv);
            }
            
            // Note: Không upload Cloudinary và không lưu Firebase ở receiver - sender đã làm rồi
            // Receiver chỉ hiển thị file đã nhận qua UDP, URL sẽ được sync từ Firebase khi load messages
            
        } catch (Exception e) {
            log.error("Handle file transfer failed", e);
        }
    }
    
    /**
     * Helper: Lấy conversationId từ userId (1-1 chat)
     */
    private String getConversationIdForUser(String userId) {
        try {
            // Query conversations từ server và tìm conversation giữa currentUser và userId
            MessengerServiceHelper helper = MessengerServiceHelper.getInstance();
            List<Conversation> conversations = helper.getUserConversations().get();
            
            if (conversations != null) {
                Conversation found = conversations.stream()
                    .filter(conv -> "private".equals(conv.getType())
                        && conv.getParticipants() != null
                        && conv.getParticipants().size() == 2
                        && conv.getParticipants().contains(currentUser.getUserId())
                        && conv.getParticipants().contains(userId))
                    .findFirst()
                    .orElse(null);
                
                if (found != null) {
                    return found.getConversationId();
                }
            }
        } catch (Exception e) {
            log.warn("Failed to find conversation for user {}: {}", userId, e.getMessage());
        }
        
        // Fallback: Return null if not found (should not happen in normal flow)
        return null;
    }
    
    /**
     * Helper: Lấy conversation object
     */
    private Conversation getConversation(String conversationId) {
        try {
            MessengerServiceHelper helper = MessengerServiceHelper.getInstance();
            return helper.getConversation(conversationId).get();
        } catch (Exception e) {
            log.warn("Failed to get conversation, using default: {}", e.getMessage());
            return Conversation.builder()
                .conversationId(conversationId)
                .type("private")
                .build();
        }
    }
    
    /**
     * Shutdown
     */
    public void shutdown() {
        if (chatReceiver != null) {
            chatReceiver.stop();
        }
        if (chatSocket != null && !chatSocket.isClosed()) {
            chatSocket.close();
        }
        isInitialized = false;
        log.info("Messenger service shutdown");
    }
}

