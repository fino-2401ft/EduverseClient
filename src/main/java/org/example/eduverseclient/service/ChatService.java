//package org.example.eduverseclient.service;
//
//
//import common.model.Conversation;
//import common.model.Message;
//import lombok.extern.slf4j.Slf4j;
//import org.example.eduverseclient.RMIClient;
//
//import java.util.ArrayList;
//import java.util.List;
//
//@Slf4j
//public class ChatService {
//    private static ChatService instance;
//    private final RMIClient rmiClient;
//
//    private ChatService() {
//        this.rmiClient = RMIClient.getInstance();
//    }
//
//    public static synchronized ChatService getInstance() {
//        if (instance == null) {
//            instance = new ChatService();
//        }
//        return instance;
//    }
//
//    /**
//     * Gửi tin nhắn
//     */
//    public Message sendMessage(String conversationId, String textContent) {
//        try {
//            Message message = rmiClient.getChatService().sendMessage(
//                conversationId,
//                rmiClient.getCurrentUser().getUserId(),
//                textContent
//            );
//
//            if (message != null) {
//                log.info("Message sent: {}", message.getMessageId());
//            }
//
//            return message;
//
//        } catch (Exception e) {
//            log.error("Send message failed", e);
//            return null;
//        }
//    }
//
//    /**
//     * Lấy lịch sử chat
//     */
//    public List<Message> getMessages(String conversationId, int limit) {
//        try {
//            return rmiClient.getChatService().getMessages(conversationId, limit);
//        } catch (Exception e) {
//            log.error("Get messages failed", e);
//            return new ArrayList<>();
//        }
//    }
//
//    /**
//     * Lấy tin nhắn mới sau timestamp
//     */
//    public List<Message> getMessagesAfter(String conversationId, long timestamp) {
//        try {
//            return rmiClient.getChatService().getMessagesAfter(conversationId, timestamp);
//        } catch (Exception e) {
//            log.error("Get new messages failed", e);
//            return new ArrayList<>();
//        }
//    }
//
//    /**
//     * Tạo conversation riêng 1:1
//     */
//    public Conversation createPrivateConversation(String userId1, String userId2) {
//        try {
//            return rmiClient.getChatService().createPrivateConversation(userId1, userId2);
//        } catch (Exception e) {
//            log.error("Create private conversation failed", e);
//            return null;
//        }
//    }
//
//    /**
//     * Lấy conversation của khóa học
//     */
//    public Conversation getCourseConversation(String courseId) {
//        try {
//            return rmiClient.getChatService().getCourseConversation(courseId);
//        } catch (Exception e) {
//            log.error("Get course conversation failed", e);
//            return null;
//        }
//    }
//
//    /**
//     * Lấy danh sách conversations của user
//     */
//    public List<Conversation> getUserConversations() {
//        try {
//            String userId = rmiClient.getCurrentUser().getUserId();
//            return rmiClient.getChatService().getUserConversations(userId);
//        } catch (Exception e) {
//            log.error("Get user conversations failed", e);
//            return new ArrayList<>();
//        }
//    }
//
//    /**
//     * Đánh dấu đã đọc
//     */
//    public boolean markAsRead(String conversationId) {
//        try {
//            String userId = rmiClient.getCurrentUser().getUserId();
//            return rmiClient.getChatService().markAsRead(conversationId, userId);
//        } catch (Exception e) {
//            log.error("Mark as read failed", e);
//            return false;
//        }
//    }
//}