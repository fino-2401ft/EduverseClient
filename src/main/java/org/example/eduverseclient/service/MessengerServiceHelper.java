package org.example.eduverseclient.service;

import common.model.Conversation;
import common.model.Message;
import common.model.User;
import lombok.extern.slf4j.Slf4j;
import org.example.eduverseclient.RMIClient;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Client-side helper service for messenger operations
 * Wraps RMI ChatService calls and provides convenient methods
 */
@Slf4j
public class MessengerServiceHelper {
    private static MessengerServiceHelper instance;
    private final RMIClient rmiClient;

    private MessengerServiceHelper() {
        this.rmiClient = RMIClient.getInstance();
    }

    public static synchronized MessengerServiceHelper getInstance() {
        if (instance == null) {
            instance = new MessengerServiceHelper();
        }
        return instance;
    }

    /**
     * Lấy danh sách conversations của user
     */
    public CompletableFuture<List<Conversation>> getUserConversations() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                User currentUser = rmiClient.getCurrentUser();
                if (currentUser == null) {
                    return new ArrayList<>();
                }

                return rmiClient.getChatService().getUserConversations(currentUser.getUserId());

            } catch (Exception e) {
                log.error("Get user conversations failed", e);
                return new ArrayList<>();
            }
        });
    }

    /**
     * Lấy messages của conversation
     */
    public CompletableFuture<List<Message>> getMessages(String conversationId, int limit) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return rmiClient.getChatService().getMessages(conversationId, limit);

            } catch (Exception e) {
                log.error("Get messages failed", e);
                return new ArrayList<>();
            }
        });
    }

    /**
     * Lấy messages sau timestamp
     */
    public CompletableFuture<List<Message>> getMessagesAfter(String conversationId, long timestamp) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return rmiClient.getChatService().getMessagesAfter(conversationId, timestamp);

            } catch (Exception e) {
                log.error("Get messages after failed", e);
                return new ArrayList<>();
            }
        });
    }

    /**
     * Tạo private conversation giữa 2 users
     */
    public CompletableFuture<Conversation> createPrivateConversation(String otherUserId) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                User currentUser = rmiClient.getCurrentUser();
                if (currentUser == null) {
                    throw new IllegalStateException("User not logged in");
                }

                return rmiClient.getChatService().createPrivateConversation(
                    currentUser.getUserId(),
                    otherUserId
                );

            } catch (Exception e) {
                log.error("Create private conversation failed", e);
                return null;
            }
        });
    }

    /**
     * Lấy course conversation
     */
    public CompletableFuture<Conversation> getCourseConversation(String courseId) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return rmiClient.getChatService().getCourseConversation(courseId);

            } catch (Exception e) {
                log.error("Get course conversation failed", e);
                return null;
            }
        });
    }

    /**
     * Lưu message vào Firebase (text message)
     * Note: For file/image/video messages, use P2PMessengerService.saveToFirebase
     */
    public CompletableFuture<Message> saveMessageToFirebase(Message message) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // For now, only text messages are supported via ChatService
                if (message.getType() == null || message.getContent() == null) {
                    log.warn("Only text messages are supported for Firebase save via ChatService");
                    return null;
                }

                User currentUser = rmiClient.getCurrentUser();
                if (currentUser == null) {
                    return null;
                }
                return rmiClient.getChatService().sendMessage(
                    message.getConversationId(),
                    currentUser.getUserId(),
                    message.getContent()
                );

            } catch (Exception e) {
                log.error("Save message to Firebase failed", e);
                return null;
            }
        });
    }

    /**
     * Lấy conversation theo ID
     */
    public CompletableFuture<Conversation> getConversation(String conversationId) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Get all user conversations and find the one matching conversationId
                User currentUser = rmiClient.getCurrentUser();
                if (currentUser == null) {
                    return null;
                }
                
                List<Conversation> conversations = rmiClient.getChatService().getUserConversations(currentUser.getUserId());
                return conversations.stream()
                    .filter(conv -> conv.getConversationId().equals(conversationId))
                    .findFirst()
                    .orElse(null);
                    
            } catch (Exception e) {
                log.error("Get conversation failed", e);
                return null;
            }
        });
    }

    /**
     * Lưu file message vào Firebase (FILE/IMAGE/VIDEO với URL)
     */
    public CompletableFuture<Message> saveFileMessageToFirebase(Message message) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                User currentUser = rmiClient.getCurrentUser();
                if (currentUser == null) {
                    return null;
                }
                
                return rmiClient.getChatService().sendFileMessage(
                    message.getConversationId(),
                    currentUser.getUserId(),
                    message.getType(),
                    message.getContent() != null ? message.getContent() : ""
                );
                
            } catch (Exception e) {
                log.error("Save file message to Firebase failed", e);
                return null;
            }
        });
    }

    /**
     * Đánh dấu conversation đã đọc
     */
    public CompletableFuture<Boolean> markAsRead(String conversationId) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                User currentUser = rmiClient.getCurrentUser();
                if (currentUser == null) {
                    return false;
                }

                return rmiClient.getChatService().markAsRead(conversationId, currentUser.getUserId());

            } catch (Exception e) {
                log.error("Mark as read failed", e);
                return false;
            }
        });
    }
}

