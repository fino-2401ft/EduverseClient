package org.example.eduverseclient.controller;

import common.enums.ConversationType;
import common.enums.MessageType;
import common.model.Conversation;
import common.model.Course;
import common.model.Message;
import common.model.User;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.stage.*;
import lombok.extern.slf4j.Slf4j;
import org.example.eduverseclient.RMIClient;
import org.example.eduverseclient.service.MessengerServiceHelper;
import org.example.eduverseclient.service.P2PMessengerService;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
public class MessengerController {
    
    @FXML private Label appTitleLabel;
    @FXML private Button privateTabButton;
    @FXML private Button courseTabButton;
    @FXML private TextField userSearchField;
    @FXML private ScrollPane conversationListScrollPane;
    @FXML private VBox conversationListBox;
    
    @FXML private HBox conversationHeader;
    @FXML private ImageView conversationAvatarImage;
    @FXML private Label conversationNameLabel;
    @FXML private Label conversationStatusLabel;
    @FXML private Button optionsButton;
    
    @FXML private ScrollPane messagesScrollPane;
    @FXML private VBox messagesBox;
    
    @FXML private Button emojiButton;
    @FXML private Button attachmentButton;
    @FXML private TextField messageField;
    @FXML private Button sendButton;
    
    private RMIClient rmiClient;
    private P2PMessengerService messengerService;
    private User currentUser;
    
    private ConversationType currentTab = ConversationType.PRIVATE;
    private Conversation selectedConversation;
    private Map<String, Conversation> conversationsMap = new HashMap<>();
    private Map<String, Course> coursesMap = new HashMap<>();
    private List<User> allUsers = new ArrayList<>();
    private boolean isSearching = false;
    
    @FXML
    public void initialize() {
        rmiClient = RMIClient.getInstance();
        messengerService = P2PMessengerService.getInstance();
        currentUser = rmiClient.getCurrentUser();
        
        // Initialize messenger service
        messengerService.initialize();
        messengerService.setMessageCallback(this::onNewMessage);
        
        // Setup UI
        setupTabs();
        setupInputArea();
        setupSearchField();
        
        // Load conversations
        loadConversations();
        
        // Load all users for search (async)
        loadAllUsers();
    }
    
    private void setupTabs() {
        // Set active tab style
        privateTabButton.setOnAction(e -> switchToPrivateChat());
        courseTabButton.setOnAction(e -> switchToCourseChat());
        
        // Default to private chat
        switchToPrivateChat();
    }
    
    @FXML
    private void switchToPrivateChat() {
        currentTab = ConversationType.PRIVATE;
        privateTabButton.setStyle("-fx-border-color: #0084FF; -fx-border-width: 0 0 2 0; -fx-background-color: transparent;");
        courseTabButton.setStyle("-fx-border-width: 0; -fx-background-color: transparent;");
        loadConversations();
    }
    
    @FXML
    private void switchToCourseChat() {
        currentTab = ConversationType.COURSE;
        courseTabButton.setStyle("-fx-border-color: #0084FF; -fx-border-width: 0 0 2 0; -fx-background-color: transparent;");
        privateTabButton.setStyle("-fx-border-width: 0; -fx-background-color: transparent;");
        loadConversations();
    }
    
    private void loadConversations() {
        // Don't load conversations if user is searching
        if (isSearching) {
            return;
        }
        
        conversationListBox.getChildren().clear();
        
        // Show loading state
        Label loadingLabel = new Label("Đang tải conversations...");
        loadingLabel.setStyle("-fx-text-fill: #999; -fx-font-size: 14; -fx-padding: 20;");
        conversationListBox.getChildren().add(loadingLabel);
        
        // Load async
        MessengerServiceHelper helper = MessengerServiceHelper.getInstance();
        final ConversationType finalCurrentTab = currentTab; // Make effectively final
        helper.getUserConversations()
            .thenAccept(conversations -> {
                Platform.runLater(() -> {
                    conversationListBox.getChildren().clear();
                    
                    List<Conversation> conversationsList = conversations != null ? conversations : new ArrayList<>();
                    
                    // Filter by current tab
                    String targetType = finalCurrentTab == ConversationType.PRIVATE ? "private" : "courseChat";
                    List<Conversation> filtered = conversationsList.stream()
                        .filter(conv -> conv.getType() != null && conv.getType().equals(targetType))
                        .sorted(Comparator.comparingLong(Conversation::getLastUpdate).reversed())
                        .collect(Collectors.toList());
                    
                    for (Conversation conv : filtered) {
                        addConversationCard(conv);
                    }
                    
                    if (filtered.isEmpty()) {
                        showEmptyState();
                    }
                });
            })
            .exceptionally(e -> {
                log.error("Failed to load conversations", e);
                Platform.runLater(() -> {
                    conversationListBox.getChildren().clear();
                    Label errorLabel = new Label("Lỗi khi tải conversations");
                    errorLabel.setStyle("-fx-text-fill: #D32F2F; -fx-font-size: 14; -fx-padding: 20;");
                    conversationListBox.getChildren().add(errorLabel);
                });
                return null;
            });
    }
    
    private void addConversationCard(Conversation conv) {
        HBox card = new HBox(10);
        card.setStyle("-fx-padding: 10; -fx-background-color: white; -fx-background-radius: 5;");
        card.setPrefWidth(280);
        card.setOnMouseClicked(e -> openConversation(conv));
        card.setOnMouseEntered(e -> card.setStyle("-fx-padding: 10; -fx-background-color: #F0F0F0; -fx-background-radius: 5;"));
        card.setOnMouseExited(e -> {
            if (selectedConversation == null || !selectedConversation.getConversationId().equals(conv.getConversationId())) {
                card.setStyle("-fx-padding: 10; -fx-background-color: white; -fx-background-radius: 5;");
            }
        });
        
        // Avatar container
        StackPane avatarContainer = new StackPane();
        avatarContainer.setPrefSize(50, 50);
        
        // Default avatar (circle with initial)
        Circle defaultAvatar = new Circle(25, Color.LIGHTGRAY);
        Label initialLabel = new Label("?");
        initialLabel.setStyle("-fx-font-size: 20; -fx-font-weight: bold; -fx-text-fill: #666;");
        avatarContainer.getChildren().addAll(defaultAvatar, initialLabel);
        
        // Load avatar from user (for private conversations)
        if ("private".equals(conv.getType()) && conv.getParticipants() != null) {
            String otherUserId = conv.getParticipants().stream()
                .filter(id -> !id.equals(currentUser.getUserId()))
                .findFirst()
                .orElse(null);
            
            if (otherUserId != null) {
                new Thread(() -> {
                    try {
                        User otherUser = rmiClient.getAuthService().getUserById(otherUserId);
                        Platform.runLater(() -> {
                            if (otherUser != null) {
                                if (otherUser.getFullName() != null) {
                                    initialLabel.setText(otherUser.getFullName().substring(0, 1).toUpperCase());
                                }
                                
                                if (otherUser.getAvatarUrl() != null && !otherUser.getAvatarUrl().isEmpty()) {
                                    try {
                                        ImageView avatarView = new ImageView();
                                        avatarView.setFitWidth(50);
                                        avatarView.setFitHeight(50);
                                        avatarView.setPreserveRatio(true);
                                        avatarView.setSmooth(true);
                                        Image avatarImage = new Image(otherUser.getAvatarUrl(), true);
                                        avatarView.setImage(avatarImage);
                                        
                                        // Clip to circle
                                        Circle clip = new Circle(25, 25, 25);
                                        avatarView.setClip(clip);
                                        
                                        avatarContainer.getChildren().clear();
                                        avatarContainer.getChildren().add(avatarView);
                                    } catch (Exception e) {
                                        log.warn("Failed to load avatar image: {}", e.getMessage());
                                    }
                                }
                            }
                        });
                    } catch (Exception e) {
                        log.warn("Failed to load avatar for user {}: {}", otherUserId, e.getMessage());
                    }
                }).start();
            }
        } else if ("courseChat".equals(conv.getType())) {
            // Course icon
            defaultAvatar.setFill(Color.web("#4CAF50"));
            initialLabel.setText("📚");
        }
        
        // Info
        VBox info = new VBox(3);
        info.setPrefWidth(200);
        
        // Get conversation name from participants
        String displayName = getConversationDisplayName(conv);
        Label nameLabel = new Label(displayName);
        nameLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 14;");
        nameLabel.setTextOverrun(OverrunStyle.ELLIPSIS);
        
        Label subtitleLabel = new Label();
        if ("courseChat".equals(conv.getType())) {
            subtitleLabel.setText("Course Chat");
        } else {
            subtitleLabel.setText("Online");
        }
        subtitleLabel.setStyle("-fx-text-fill: #666; -fx-font-size: 11;");
        
        // Preview from last message
        Label previewLabel = new Label("");
        previewLabel.setStyle("-fx-text-fill: #666; -fx-font-size: 12;");
        previewLabel.setMaxWidth(200);
        previewLabel.setTextOverrun(OverrunStyle.ELLIPSIS);
        
        // Load last message preview
        loadLastMessagePreview(conv, previewLabel);
        
        Label timeLabel = new Label(formatTime(conv.getLastUpdate()));
        timeLabel.setStyle("-fx-text-fill: #999; -fx-font-size: 11;");
        
        info.getChildren().addAll(nameLabel, subtitleLabel, previewLabel, timeLabel);
        
        card.getChildren().addAll(avatarContainer, info);
        conversationListBox.getChildren().add(card);
    }
    
    private void showEmptyState() {
        Label emptyLabel = new Label("No conversations yet");
        emptyLabel.setStyle("-fx-text-fill: #999; -fx-font-size: 14; -fx-padding: 20;");
        conversationListBox.getChildren().add(emptyLabel);
    }
    
    private void openConversation(Conversation conv) {
        selectedConversation = conv;
        
        // Update header - Get display name from participants
        updateConversationHeader(conv);
        
        // Load messages
        loadMessages(conv);
        
        // Highlight selected card
        updateCardSelection(conv);
    }
    
    /**
     * Cập nhật header với thông tin conversation (tên user, status, avatar)
     */
    private void updateConversationHeader(Conversation conv) {
        if (conv == null) {
            conversationNameLabel.setText("Select a conversation");
            conversationStatusLabel.setText("");
            conversationAvatarImage.setImage(null);
            return;
        }
        
        // Get display name
        String displayName = getConversationDisplayName(conv);
        conversationNameLabel.setText(displayName);
        
        // Get status and avatar for private conversations
        if ("private".equals(conv.getType()) && conv.getParticipants() != null) {
            String otherUserId = conv.getParticipants().stream()
                .filter(id -> !id.equals(currentUser.getUserId()))
                .findFirst()
                .orElse(null);
            
            if (otherUserId != null) {
                // Check if user is online and load avatar
                new Thread(() -> {
                    try {
                        boolean isOnline = rmiClient.getPeerService().isUserOnline(otherUserId);
                        User otherUser = rmiClient.getAuthService().getUserById(otherUserId);
                        
                        Platform.runLater(() -> {
                            conversationStatusLabel.setText(isOnline ? "🟢 Online" : "⚫ Offline");
                            if (otherUser != null) {
                                conversationNameLabel.setText(otherUser.getFullName() != null ? otherUser.getFullName() : otherUser.getEmail());
                                
                                // Load avatar
                                if (otherUser.getAvatarUrl() != null && !otherUser.getAvatarUrl().isEmpty()) {
                                    try {
                                        Image avatarImage = new Image(otherUser.getAvatarUrl(), true);
                                        conversationAvatarImage.setImage(avatarImage);
                                        
                                        // Clip to circle
                                        Circle clip = new Circle(20, 20, 20);
                                        conversationAvatarImage.setClip(clip);
                                    } catch (Exception e) {
                                        log.warn("Failed to load avatar image: {}", e.getMessage());
                                        conversationAvatarImage.setImage(null);
                                    }
                                } else {
                                    conversationAvatarImage.setImage(null);
                                }
                            }
                        });
                    } catch (Exception e) {
                        log.warn("Failed to get user status: {}", e.getMessage());
                        Platform.runLater(() -> {
                            conversationStatusLabel.setText("⚫ Offline");
                            conversationAvatarImage.setImage(null);
                        });
                    }
                }).start();
            } else {
                conversationStatusLabel.setText("");
                conversationAvatarImage.setImage(null);
            }
        } else {
            conversationStatusLabel.setText("Group Chat");
            conversationAvatarImage.setImage(null);
        }
    }
    
    private void updateCardSelection(Conversation conv) {
        for (javafx.scene.Node node : conversationListBox.getChildren()) {
            if (node instanceof HBox) {
                HBox card = (HBox) node;
                if (selectedConversation != null && selectedConversation.getConversationId().equals(conv.getConversationId())) {
                    card.setStyle("-fx-padding: 10; -fx-background-color: #E3F2FD; -fx-background-radius: 5;");
                } else {
                    card.setStyle("-fx-padding: 10; -fx-background-color: white; -fx-background-radius: 5;");
                }
            }
        }
    }
    
    private void loadMessages(Conversation conv) {
        messagesBox.getChildren().clear();
        
        // Show loading state
        Label loadingLabel = new Label("Đang tải messages...");
        loadingLabel.setStyle("-fx-text-fill: #999; -fx-font-size: 14; -fx-padding: 20;");
        messagesBox.getChildren().add(loadingLabel);
        
        // Load async
        MessengerServiceHelper helper = MessengerServiceHelper.getInstance();
        helper.getMessages(conv.getConversationId(), 50)
            .thenAccept(messages -> {
                Platform.runLater(() -> {
                    messagesBox.getChildren().clear();
                    
                    if (messages != null) {
                        for (Message msg : messages) {
                            addMessageBubble(msg);
                        }
                        
                        // Scroll to bottom
                        messagesScrollPane.setVvalue(1.0);
                    }
                });
            })
            .exceptionally(e -> {
                log.error("Failed to load messages", e);
                Platform.runLater(() -> {
                    messagesBox.getChildren().clear();
                    Label errorLabel = new Label("Lỗi khi tải messages");
                    errorLabel.setStyle("-fx-text-fill: #D32F2F; -fx-font-size: 14; -fx-padding: 20;");
                    messagesBox.getChildren().add(errorLabel);
                });
                return null;
            });
    }
    
    private void addMessageBubble(Message message) {
        boolean isSent = message.getSenderId().equals(currentUser.getUserId());
        
        VBox bubbleContainer = new VBox(5);
        bubbleContainer.setAlignment(isSent ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
        bubbleContainer.setPadding(new Insets(5, 10, 5, 10));
        
        HBox messageRow = new HBox(10);
        messageRow.setAlignment(isSent ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
        
        // Avatar (only for received messages)
        if (!isSent) {
            StackPane avatarContainer = new StackPane();
            avatarContainer.setPrefSize(40, 40);
            
            // Default avatar (circle with initial)
            Circle defaultAvatar = new Circle(20, Color.LIGHTGRAY);
            Label initialLabel = new Label("?");
            initialLabel.setStyle("-fx-font-size: 18; -fx-font-weight: bold; -fx-text-fill: #666;");
            avatarContainer.getChildren().addAll(defaultAvatar, initialLabel);
            
            // Load avatar from user
            String senderId = message.getSenderId();
            new Thread(() -> {
                try {
                    User sender = rmiClient.getAuthService().getUserById(senderId);
                    Platform.runLater(() -> {
                        if (sender != null && sender.getFullName() != null) {
                            initialLabel.setText(sender.getFullName().substring(0, 1).toUpperCase());
                        }
                        
                        if (sender != null && sender.getAvatarUrl() != null && !sender.getAvatarUrl().isEmpty()) {
                            try {
                                ImageView avatarView = new ImageView();
                                avatarView.setFitWidth(40);
                                avatarView.setFitHeight(40);
                                avatarView.setPreserveRatio(true);
                                avatarView.setSmooth(true);
                                Image avatarImage = new Image(sender.getAvatarUrl(), true);
                                avatarView.setImage(avatarImage);
                                
                                // Clip to circle
                                Circle clip = new Circle(20, 20, 20);
                                avatarView.setClip(clip);
                                
                                avatarContainer.getChildren().clear();
                                avatarContainer.getChildren().add(avatarView);
                            } catch (Exception e) {
                                log.warn("Failed to load avatar image: {}", e.getMessage());
                            }
                        }
                    });
                } catch (Exception e) {
                    log.warn("Failed to load avatar for user {}: {}", senderId, e.getMessage());
                }
            }).start();
            
            messageRow.getChildren().add(avatarContainer);
        }
        
        VBox messageContent = new VBox(5);
        messageContent.setMaxWidth(400);
        messageContent.setPadding(new Insets(10, 15, 10, 15));
        
        // Style based on sent/received
        if (isSent) {
            messageContent.setStyle("-fx-background-color: #0084FF; -fx-background-radius: 18;");
        } else {
            messageContent.setStyle("-fx-background-color: #E4E6EB; -fx-background-radius: 18;");
        }
        
        // Message content based on type
        if (message.getType() == MessageType.TEXT) {
            Label textLabel = new Label(message.getContent() != null ? message.getContent() : "");
            textLabel.setWrapText(true);
            textLabel.setStyle(isSent ? "-fx-text-fill: white; -fx-font-size: 14;" : "-fx-text-fill: black; -fx-font-size: 14;");
            messageContent.getChildren().add(textLabel);
        } else if (message.getType() == MessageType.IMAGE && message.getContent() != null && message.getContent().startsWith("http")) {
            // Display image from URL (Cloudinary)
            try {
                ImageView imageView = new ImageView();
                Image image = new Image(message.getContent(), true);
                imageView.setImage(image);
                imageView.setFitWidth(300);
                imageView.setFitHeight(300);
                imageView.setPreserveRatio(true);
                imageView.setSmooth(true);
                imageView.setCache(true);
                messageContent.getChildren().add(imageView);
            } catch (Exception e) {
                log.error("Failed to load image: {}", e.getMessage());
                Label errorLabel = new Label("[Image failed to load]");
                errorLabel.setStyle(isSent ? "-fx-text-fill: white; -fx-font-size: 14;" : "-fx-text-fill: black; -fx-font-size: 14;");
                messageContent.getChildren().add(errorLabel);
            }
        } else if (message.getType() == MessageType.VIDEO && message.getContent() != null && message.getContent().startsWith("http")) {
            // Display video from URL (Cloudinary) - show thumbnail with play button
            try {
                StackPane videoContainer = new StackPane();
                videoContainer.setPrefWidth(300);
                videoContainer.setPrefHeight(200);
                
                // Video thumbnail (Cloudinary auto-generates thumbnail for videos)
                ImageView thumbnailView = new ImageView();
                // Try to get thumbnail URL (Cloudinary format: add .jpg extension or use transformation)
                String thumbnailUrl = message.getContent();
                if (thumbnailUrl.contains("upload/")) {
                    // Insert transformation before file extension
                    thumbnailUrl = thumbnailUrl.replace("/upload/", "/upload/w_300,h_200,c_fill/");
                }
                Image thumbnail = new Image(thumbnailUrl, true);
                thumbnailView.setImage(thumbnail);
                thumbnailView.setFitWidth(300);
                thumbnailView.setFitHeight(200);
                thumbnailView.setPreserveRatio(true);
                thumbnailView.setSmooth(true);
                
                // Play button overlay
                Circle playButton = new Circle(30, Color.WHITE);
                playButton.setOpacity(0.8);
                Label playIcon = new Label("▶");
                playIcon.setStyle("-fx-font-size: 24; -fx-text-fill: #0084FF;");
                StackPane playOverlay = new StackPane();
                playOverlay.getChildren().addAll(playButton, playIcon);
                
                videoContainer.getChildren().addAll(thumbnailView, playOverlay);
                
                // Click to open video in desktop
                videoContainer.setOnMouseClicked(e -> openFileFromUrl(message.getContent()));
                videoContainer.setStyle("-fx-cursor: hand;");
                
                messageContent.getChildren().add(videoContainer);
            } catch (Exception e) {
                log.error("Failed to load video thumbnail: {}", e.getMessage());
                Label errorLabel = new Label("[Video failed to load]");
                errorLabel.setStyle(isSent ? "-fx-text-fill: white; -fx-font-size: 14;" : "-fx-text-fill: black; -fx-font-size: 14;");
                messageContent.getChildren().add(errorLabel);
            }
        } else if (message.getType() == MessageType.FILE && message.getContent() != null && message.getContent().startsWith("http")) {
            // Display file - clickable to open in desktop
            HBox fileContainer = new HBox(10);
            fileContainer.setAlignment(Pos.CENTER_LEFT);
            fileContainer.setStyle("-fx-cursor: hand;");
            fileContainer.setOnMouseClicked(e -> openFileFromUrl(message.getContent()));
            
            Label fileLabel = new Label("📎 " + (message.getContent().contains("/") 
                ? message.getContent().substring(message.getContent().lastIndexOf("/") + 1) 
                : "File"));
            fileLabel.setWrapText(true);
            fileLabel.setStyle(isSent ? "-fx-text-fill: white; -fx-font-size: 14;" : "-fx-text-fill: black; -fx-font-size: 14;");
            
            fileContainer.getChildren().add(fileLabel);
            messageContent.getChildren().add(fileContainer);
        } else {
            // For FILE, VIDEO, or IMAGE without URL
            Label textLabel = new Label(message.getContent() != null ? message.getContent() : "[Media]");
            textLabel.setWrapText(true);
            textLabel.setStyle(isSent ? "-fx-text-fill: white; -fx-font-size: 14;" : "-fx-text-fill: black; -fx-font-size: 14;");
            messageContent.getChildren().add(textLabel);
        }
        
        // Timestamp
        Label timeLabel = new Label(formatMessageTime(message.getTimestamp()));
        timeLabel.setStyle("-fx-font-size: 10; -fx-text-fill: #999;");
        messageContent.getChildren().add(timeLabel);
        
        messageRow.getChildren().add(messageContent);
        bubbleContainer.getChildren().add(messageRow);
        messagesBox.getChildren().add(bubbleContainer);
    }
    
    /**
     * Download và mở file từ URL bằng desktop application
     */
    private void openFileFromUrl(String fileUrl) {
        new Thread(() -> {
            try {
                // Download file to temp directory
                java.net.URL url = new java.net.URL(fileUrl);
                String fileName = fileUrl.substring(fileUrl.lastIndexOf("/") + 1);
                File tempFile = new File(System.getProperty("java.io.tmpdir"), fileName);
                
                try (java.io.InputStream in = url.openStream();
                     java.io.FileOutputStream out = new java.io.FileOutputStream(tempFile)) {
                    byte[] buffer = new byte[4096];
                    int bytesRead;
                    while ((bytesRead = in.read(buffer)) != -1) {
                        out.write(buffer, 0, bytesRead);
                    }
                }
                
                // Open file with desktop application
                if (java.awt.Desktop.isDesktopSupported()) {
                    java.awt.Desktop.getDesktop().open(tempFile);
                } else {
                    Platform.runLater(() -> {
                        Alert alert = new Alert(Alert.AlertType.WARNING);
                        alert.setTitle("Cannot Open File");
                        alert.setHeaderText(null);
                        alert.setContentText("Desktop is not supported on this system");
                        alert.showAndWait();
                    });
                }
            } catch (Exception e) {
                log.error("Failed to open file from URL: {}", e.getMessage());
                Platform.runLater(() -> {
                    Alert alert = new Alert(Alert.AlertType.ERROR);
                    alert.setTitle("Error");
                    alert.setHeaderText(null);
                    alert.setContentText("Failed to open file: " + e.getMessage());
                    alert.showAndWait();
                });
            }
        }).start();
    }
    
    private void setupInputArea() {
        sendButton.setOnAction(e -> sendMessage());
        messageField.setOnAction(e -> sendMessage());
        
        attachmentButton.setOnAction(e -> selectFile());
        emojiButton.setOnAction(e -> showEmojiPicker());
    }
    
    /**
     * Hiển thị emoji picker và insert emoji vào message field
     */
    private void showEmojiPicker() {
        // Simple emoji picker with common emojis
        String[] commonEmojis = {
            "😀", "😃", "😄", "😁", "😆", "😅", "😂", "🤣",
            "😊", "😇", "🙂", "🙃", "😉", "😌", "😍", "🥰",
            "😘", "😗", "😙", "😚", "😋", "😛", "😝", "😜",
            "🤪", "🤨", "🧐", "🤓", "😎", "🤩", "🥳", "😏",
            "😒", "😞", "😔", "😟", "😕", "🙁", "☹️", "😣",
            "😖", "😫", "😩", "🥺", "😢", "😭", "😤", "😠",
            "👍", "👎", "👌", "✌️", "🤞", "🤟", "🤘", "👏",
            "🙌", "👐", "🤲", "🤝", "🙏", "✍️", "💪", "🦾",
            "❤️", "🧡", "💛", "💚", "💙", "💜", "🖤", "🤍",
            "💯", "🔥", "⭐", "🌟", "✨", "💫", "💥", "💢"
        };
        
        // Create popup with emoji grid
        Stage emojiStage = new Stage();
        emojiStage.initStyle(StageStyle.UTILITY);
        emojiStage.initModality(Modality.NONE);
        emojiStage.setTitle("Emoji");
        
        GridPane emojiGrid = new GridPane();
        emojiGrid.setPadding(new Insets(10));
        emojiGrid.setHgap(5);
        emojiGrid.setVgap(5);
        emojiGrid.setStyle("-fx-background-color: white;");
        
        int cols = 8;
        for (int i = 0; i < commonEmojis.length; i++) {
            Button emojiBtn = new Button(commonEmojis[i]);
            emojiBtn.setStyle("-fx-background-color: transparent; -fx-font-size: 24; -fx-padding: 5;");
            emojiBtn.setOnAction(e -> {
                String emoji = emojiBtn.getText();
                messageField.appendText(emoji);
                messageField.requestFocus();
                emojiStage.close();
            });
            
            int row = i / cols;
            int col = i % cols;
            emojiGrid.add(emojiBtn, col, row);
        }
        
        ScrollPane scrollPane = new ScrollPane(emojiGrid);
        scrollPane.setFitToWidth(true);
        scrollPane.setPrefHeight(200);
        
        Scene scene = new Scene(scrollPane, 350, 250);
        emojiStage.setScene(scene);
        
        // Position near emoji button
        if (emojiButton.getScene() != null && emojiButton.getScene().getWindow() != null) {
            Window owner = emojiButton.getScene().getWindow();
            emojiStage.initOwner(owner);
            emojiStage.setX(owner.getX() + emojiButton.getLayoutX() + 50);
            emojiStage.setY(owner.getY() + emojiButton.getLayoutY() + 400);
        }
        
        emojiStage.show();
        
        // Close when clicking outside
        emojiStage.focusedProperty().addListener((obs, wasFocused, isNowFocused) -> {
            if (!isNowFocused) {
                emojiStage.close();
            }
        });
    }
    
    /**
     * Load last message preview for conversation card
     */
    private void loadLastMessagePreview(Conversation conv, Label previewLabel) {
        if (conv.getConversationId() == null) {
            previewLabel.setText("");
            return;
        }
        
        // Load last message (limit=1 to get the most recent)
        MessengerServiceHelper helper = MessengerServiceHelper.getInstance();
        helper.getMessages(conv.getConversationId(), 1)
            .thenAccept(messages -> {
                Platform.runLater(() -> {
                    if (messages != null && !messages.isEmpty()) {
                        Message lastMessage = messages.get(messages.size() - 1); // Get the last one
                        String preview = formatMessagePreview(lastMessage);
                        previewLabel.setText(preview);
                    } else {
                        previewLabel.setText("");
                    }
                });
            })
            .exceptionally(e -> {
                log.warn("Failed to load last message preview: {}", e.getMessage());
                return null;
            });
    }
    
    /**
     * Format message preview text (like Meta Messenger)
     */
    private String formatMessagePreview(Message message) {
        if (message == null) {
            return "";
        }
        
        String prefix = "";
        String content = "";
        
        switch (message.getType()) {
            case TEXT:
                content = message.getContent() != null ? message.getContent() : "";
                break;
            case IMAGE:
                prefix = "📷 ";
                content = "Photo";
                break;
            case VIDEO:
                prefix = "🎥 ";
                content = "Video";
                break;
            case FILE:
                prefix = "📎 ";
                // Extract filename from URL or use default
                if (message.getContent() != null && message.getContent().contains("/")) {
                    String fileName = message.getContent().substring(message.getContent().lastIndexOf("/") + 1);
                    // Remove query params if any
                    if (fileName.contains("?")) {
                        fileName = fileName.substring(0, fileName.indexOf("?"));
                    }
                    content = fileName.length() > 30 ? fileName.substring(0, 30) + "..." : fileName;
                } else {
                    content = "File";
                }
                break;
            default:
                content = "Message";
        }
        
        String preview = prefix + content;
        // Truncate if too long
        if (preview.length() > 40) {
            preview = preview.substring(0, 37) + "...";
        }
        
        return preview;
    }
    
    private void setupSearchField() {
        userSearchField.textProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue != null && !newValue.trim().isEmpty()) {
                isSearching = true;
                searchUsers(newValue.trim());
            } else {
                isSearching = false;
                loadConversations();
            }
        });
    }
    
    @FXML
    private void onSearchUser() {
        String query = userSearchField.getText();
        if (query == null || query.trim().isEmpty()) {
            isSearching = false;
            loadConversations();
        } else {
            isSearching = true;
            searchUsers(query.trim());
        }
    }
    
    private void loadAllUsers() {
        new Thread(() -> {
            try {
                List<User> loadedUsers = rmiClient.getAuthService().searchUsers("");
    
                loadedUsers = loadedUsers.stream()
                        .filter(u -> !u.getUserId().equals(currentUser.getUserId()))
                        .collect(Collectors.toList());
    
                allUsers = loadedUsers;
    
                log.info("Loaded {} users for search", allUsers.size());
            } catch (Exception e) {
                log.error("Failed to load users", e);
                allUsers = new ArrayList<>();
            }
        }).start();
    }
    
    
    private void searchUsers(String query) {
        conversationListBox.getChildren().clear();
    
        Label loadingLabel = new Label("Đang tìm kiếm...");
        loadingLabel.setStyle("-fx-text-fill: #999; -fx-font-size: 14; -fx-padding: 20;");
        conversationListBox.getChildren().add(loadingLabel);
    
        new Thread(() -> {
            try {
                List<User> result = rmiClient.getAuthService().searchUsers(query);
    
                List<User> filteredUsers = result.stream()
                        .filter(u -> !u.getUserId().equals(currentUser.getUserId()))
                        .collect(Collectors.toList());
    
                Platform.runLater(() -> {
                    conversationListBox.getChildren().clear();
    
                    if (filteredUsers.isEmpty()) {
                        Label noResultsLabel = new Label("Không tìm thấy user nào");
                        noResultsLabel.setStyle("-fx-text-fill: #999; -fx-font-size: 14; -fx-padding: 20;");
                        conversationListBox.getChildren().add(noResultsLabel);
                        return;
                    }
    
                    for (User u : filteredUsers) {
                        addUserCard(u);
                    }
                });
    
            } catch (Exception e) {
                log.error("Failed to search users", e);
                Platform.runLater(() -> {
                    conversationListBox.getChildren().clear();
                    Label errorLabel = new Label("Lỗi khi tìm kiếm users");
                    errorLabel.setStyle("-fx-text-fill: #D32F2F; -fx-font-size: 14; -fx-padding: 20;");
                    conversationListBox.getChildren().add(errorLabel);
                });
            }
        }).start();
    }
    
    
    private void addUserCard(User user) {
        HBox card = new HBox(10);
        card.setStyle("-fx-padding: 10; -fx-background-color: white; -fx-background-radius: 5;");
        card.setPrefWidth(280);
        card.setOnMouseClicked(e -> createConversationWithUser(user));
        card.setOnMouseEntered(e -> card.setStyle("-fx-padding: 10; -fx-background-color: #F0F0F0; -fx-background-radius: 5;"));
        card.setOnMouseExited(e -> card.setStyle("-fx-padding: 10; -fx-background-color: white; -fx-background-radius: 5;"));
        
        // Avatar
        Circle avatar = new Circle(25, Color.LIGHTGRAY);
        // TODO: Load avatar from user.getAvatarUrl() if available
        
        // Info
        VBox info = new VBox(3);
        info.setPrefWidth(200);
        
        Label nameLabel = new Label(user.getFullName() != null ? user.getFullName() : user.getEmail());
        nameLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 14;");
        nameLabel.setTextOverrun(OverrunStyle.ELLIPSIS);
        
        Label emailLabel = new Label(user.getEmail());
        emailLabel.setStyle("-fx-text-fill: #666; -fx-font-size: 11;");
        emailLabel.setTextOverrun(OverrunStyle.ELLIPSIS);
        
        Label statusLabel = new Label(user.isOnline() ? "🟢 Online" : "⚫ Offline");
        statusLabel.setStyle("-fx-text-fill: #666; -fx-font-size: 11;");
        
        info.getChildren().addAll(nameLabel, emailLabel, statusLabel);
        
        card.getChildren().addAll(avatar, info);
        conversationListBox.getChildren().add(card);
    }
    
    private void createConversationWithUser(User otherUser) {
        new Thread(() -> {
            try {
                log.info("Creating conversation with user: {}", otherUser.getUserId());
                
                // Kiểm tra conversation đã tồn tại trong list chưa
                MessengerServiceHelper helper = MessengerServiceHelper.getInstance();
                List<Conversation> existingConversations = helper.getUserConversations().get();
                
                Conversation existingConv = existingConversations.stream()
                    .filter(conv -> "private".equals(conv.getType()) 
                        && conv.getParticipants() != null 
                        && conv.getParticipants().contains(otherUser.getUserId())
                        && conv.getParticipants().contains(currentUser.getUserId()))
                    .findFirst()
                    .orElse(null);
                
                Conversation conversation;
                if (existingConv != null) {
                    log.info("Conversation already exists: {}", existingConv.getConversationId());
                    conversation = existingConv;
                } else {
                    // Tạo mới (server sẽ check lại và trả về existing nếu có)
                    conversation = helper.createPrivateConversation(otherUser.getUserId()).get();
                }
                
                if (conversation != null) {
                    Platform.runLater(() -> {
                        // Clear search
                        userSearchField.clear();
                        isSearching = false;
                        
                        // Open the conversation
                        openConversation(conversation);
                        
                        // Refresh conversation list
                        loadConversations();
                    });
                } else {
                    Platform.runLater(() -> {
                        Alert alert = new Alert(Alert.AlertType.ERROR);
                        alert.setTitle("Lỗi");
                        alert.setHeaderText(null);
                        alert.setContentText("Không thể tạo cuộc trò chuyện!");
                        alert.showAndWait();
                    });
                }
            } catch (Exception e) {
                log.error("Failed to create conversation with user", e);
                Platform.runLater(() -> {
                    Alert alert = new Alert(Alert.AlertType.ERROR);
                    alert.setTitle("Lỗi");
                    alert.setHeaderText(null);
                    alert.setContentText("Đã xảy ra lỗi: " + e.getMessage());
                    alert.showAndWait();
                });
            }
        }).start();
    }
    
    @FXML
    private void sendMessage() {
        String text = messageField.getText().trim();
        if (text.isEmpty() || selectedConversation == null) {
            return;
        }
        
        messengerService.sendMessage(selectedConversation.getConversationId(), text);
        messageField.clear();
    }
    
    @FXML
    private void selectFile() {
        if (selectedConversation == null) {
            return;
        }
        
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Select File");
        File file = fileChooser.showOpenDialog(messageField.getScene().getWindow());
        
        if (file != null) {
            String fileName = file.getName().toLowerCase();
            MessageType type = fileName.endsWith(".jpg") || fileName.endsWith(".jpeg") || fileName.endsWith(".png") 
                ? MessageType.IMAGE 
                : fileName.endsWith(".mp4") || fileName.endsWith(".avi") 
                    ? MessageType.VIDEO 
                    : MessageType.FILE;
            
            messengerService.sendFile(selectedConversation.getConversationId(), file, type);
        }
    }
    
    private void onNewMessage(Message message, Conversation conv) {
        Platform.runLater(() -> {
            if (conv == null) {
                log.warn("Received message with null conversation, messageId: {}", message.getMessageId());
                return;
            }
            
            if (selectedConversation != null && selectedConversation.getConversationId().equals(conv.getConversationId())) {
                addMessageBubble(message);
                messagesScrollPane.setVvalue(1.0);
            }
            // Update conversation list preview
            loadConversations();
        });
    }
    
    private String formatTime(long timestamp) {
        if (timestamp == 0) return "";
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm");
        return sdf.format(new Date(timestamp));
    }
    
    private String formatMessageTime(long timestamp) {
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm");
        return sdf.format(new Date(timestamp));
    }
    
    /**
     * Get display name for conversation from participants
     */
    private String getConversationDisplayName(Conversation conv) {
        if (conv.getParticipants() == null || conv.getParticipants().isEmpty()) {
            return "Unknown";
        }
        
        if ("private".equals(conv.getType()) && conv.getParticipants().size() == 2) {
            // Get other user's name
            String otherUserId = conv.getParticipants().stream()
                .filter(id -> !id.equals(currentUser.getUserId()))
                .findFirst()
                .orElse(null);
            
            if (otherUserId != null && allUsers != null) {
                return allUsers.stream()
                    .filter(u -> u.getUserId().equals(otherUserId))
                    .findFirst()
                    .map(User::getFullName)
                    .orElse(otherUserId);
            }
            
            return "Private Chat";
        } else if ("courseChat".equals(conv.getType())) {
            return "Course Chat";
        }
        
        return "Group Chat";
    }
}

