package org.example.eduverseclient.controller;


import common.model.Conversation;
import common.model.Message;
import common.model.User;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.text.Font;
import lombok.extern.slf4j.Slf4j;
import org.example.eduverseclient.RMIClient;
import org.example.eduverseclient.service.ChatService;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Slf4j
public class ChatViewController {
    
    @FXML private VBox conversationListBox;
    @FXML private VBox chatWindow;
    @FXML private VBox emptyState;
    @FXML private Label chatTitleLabel;
    @FXML private Label onlineStatusLabel;
    @FXML private ScrollPane messagesScrollPane;
    @FXML private VBox messagesBox;
    @FXML private TextField messageField;
    
    private ChatService chatService;
    private RMIClient rmiClient;
    
    private Conversation currentConversation;
    private long lastMessageTimestamp = 0;
    
    private ScheduledExecutorService updateExecutor;
    
    @FXML
    public void initialize() {
        chatService = ChatService.getInstance();
        rmiClient = RMIClient.getInstance();
        
        // Load conversations
        loadConversations();
        
        // Enter để gửi
        messageField.setOnAction(e -> handleSend());
       // to bottom
        messagesBox.heightProperty().addListener((obs, old, newVal) -> {
            messagesScrollPane.setVvalue(1.0);
        });
    }
    private void loadConversations() {
        new Thread(() -> {
            List<Conversation> conversations = chatService.getUserConversations();

            Platform.runLater(() -> {
                conversationListBox.getChildren().clear();

                if (conversations.isEmpty()) {
                    Label emptyLabel = new Label("Chưa có cuộc trò chuyện nào");
                    emptyLabel.setStyle("-fx-font-size: 13; -fx-text-fill: #999;");
                    conversationListBox.getChildren().add(emptyLabel);
                } else {
                    conversations.forEach(this::addConversationItem);
                }
            });
        }).start();
    }

    private void addConversationItem(Conversation conversation) {
        VBox item = new VBox(5);
        item.setStyle("-fx-background-color: white; -fx-padding: 10; " +
                "-fx-background-radius: 5; -fx-cursor: hand;");

        // Title
        Label titleLabel = new Label(conversation.getConversationName());
        titleLabel.setStyle("-fx-font-size: 14; -fx-font-weight: bold;");

        // Last message
        Label lastMsgLabel = new Label(conversation.getLastMessageText() != null
                ? conversation.getLastMessageText()
                : "Chưa có tin nhắn");
        lastMsgLabel.setStyle("-fx-font-size: 12; -fx-text-fill: #666;");
        lastMsgLabel.setMaxWidth(250);

        // Time
        Label timeLabel = new Label(formatTime(conversation.getLastMessageTime()));
        timeLabel.setStyle("-fx-font-size: 11; -fx-text-fill: #999;");

        item.getChildren().addAll(titleLabel, lastMsgLabel, timeLabel);

        // Click to open
        item.setOnMouseClicked(e -> openConversation(conversation));

        // Hover effect
        item.setOnMouseEntered(e -> item.setStyle(
                "-fx-background-color: #E3F2FD; -fx-padding: 10; -fx-background-radius: 5; -fx-cursor: hand;"
        ));
        item.setOnMouseExited(e -> item.setStyle(
                "-fx-background-color: white; -fx-padding: 10; -fx-background-radius: 5; -fx-cursor: hand;"
        ));

        conversationListBox.getChildren().add(item);
    }

    private void openConversation(Conversation conversation) {
        this.currentConversation = conversation;
        this.lastMessageTimestamp = 0;

        // Show chat window
        emptyState.setVisible(false);
        chatWindow.setVisible(true);

        // Update header
        chatTitleLabel.setText(conversation.getConversationName());
        onlineStatusLabel.setText(""); // TODO: Check online status

        // Load messages
        loadMessages();

        // Start auto-update
        startAutoUpdate();
    }

    private void loadMessages() {
        new Thread(() -> {
            List<Message> messages = chatService.getMessages(
                    currentConversation.getConversationId(),
                    50
            );

            Platform.runLater(() -> {
                messagesBox.getChildren().clear();
                messages.forEach(this::addMessageBubble);

                // Update last timestamp
                if (!messages.isEmpty()) {
                    lastMessageTimestamp = messages.get(messages.size() - 1).getTimestamp();
                }
            });
        }).start();
    }

    private void loadNewMessages() {
        if (currentConversation == null) return;

        new Thread(() -> {
            List<Message> newMessages = chatService.getMessagesAfter(
                    currentConversation.getConversationId(),
                    lastMessageTimestamp
            );

            if (!newMessages.isEmpty()) {
                Platform.runLater(() -> {
                    newMessages.forEach(this::addMessageBubble);
                    lastMessageTimestamp = newMessages.get(newMessages.size() - 1).getTimestamp();
                });
            }
        }).start();
    }

    private void addMessageBubble(Message message) {
        User currentUser = rmiClient.getCurrentUser();
        boolean isMyMessage = message.getSenderId().equals(currentUser.getUserId());

        HBox container = new HBox();
        container.setAlignment(isMyMessage ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
        container.setPadding(new Insets(5, 0, 5, 0));

        VBox bubble = new VBox(5);
        bubble.setMaxWidth(400);
        bubble.setPadding(new Insets(10));
        bubble.setStyle(isMyMessage
                ? "-fx-background-color: #1976D2; -fx-background-radius: 15 15 0 15;"
                : "-fx-background-color: #E0E0E0; -fx-background-radius: 15 15 15 0;");

        // Sender name (if not my message)
        if (!isMyMessage) {
            Label senderLabel = new Label(message.getSenderName());
            senderLabel.setStyle("-fx-font-size: 11; -fx-font-weight: bold; -fx-text-fill: #555;");
            bubble.getChildren().add(senderLabel);
        }

        // Message text
        Label textLabel = new Label(message.getTextContent());
        textLabel.setWrapText(true);
        textLabel.setStyle(isMyMessage
                ? "-fx-text-fill: white; -fx-font-size: 13;"
                : "-fx-text-fill: black; -fx-font-size: 13;");

        // Timestamp
        Label timeLabel = new Label(formatTime(message.getTimestamp()));
        timeLabel.setStyle(isMyMessage
                ? "-fx-text-fill: #BBDEFB; -fx-font-size: 10;"
                : "-fx-text-fill: #999; -fx-font-size: 10;");

        bubble.getChildren().addAll(textLabel, timeLabel);
        container.getChildren().add(bubble);

        messagesBox.getChildren().add(container);
    }

    @FXML
    private void handleSend() {
        if (currentConversation == null) return;

        String text = messageField.getText().trim();
        if (text.isEmpty()) return;

        // Clear field
        messageField.clear();

        // Send message
        new Thread(() -> {
            Message message = chatService.sendMessage(
                    currentConversation.getConversationId(),
                    text
            );

            if (message != null) {
                Platform.runLater(() -> {
                    addMessageBubble(message);
                    lastMessageTimestamp = message.getTimestamp();
                });
            }
        }).start();
    }

    @FXML
    private void handleAttachment() {
        showInfo("Chức năng đính kèm file đang được phát triển");
    }

    @FXML
    private void handleNewChat() {
        showInfo("Chức năng tạo chat mới đang được phát triển");
    }

    private void startAutoUpdate() {
        // Stop previous executor
        if (updateExecutor != null && !updateExecutor.isShutdown()) {
            updateExecutor.shutdown();
        }

        updateExecutor = Executors.newScheduledThreadPool(1);

        // Poll for new messages every 3 seconds
        updateExecutor.scheduleAtFixedRate(() -> {
            try {
                loadNewMessages();
            } catch (Exception e) {
                log.error("Auto update failed", e);
            }
        }, 3, 3, TimeUnit.SECONDS);
    }

    public void cleanup() {
        if (updateExecutor != null && !updateExecutor.isShutdown()) {
            updateExecutor.shutdown();
        }
    }

    private String formatTime(long timestamp) {
        if (timestamp == 0) return "";

        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm");
        return sdf.format(new Date(timestamp));
    }

    private void showInfo(String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Thông báo");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
        
        // Auto-scroll messages