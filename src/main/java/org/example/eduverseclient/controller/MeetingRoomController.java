package org.example.eduverseclient.controller;

import common.enums.MeetingRole;
import common.model.Meeting;
import common.model.MeetingEnrollment;
import common.model.Peer;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.example.eduverseclient.RMIClient;
import org.example.eduverseclient.component.VideoPanel; // Đảm bảo bạn có class này
import org.example.eduverseclient.network.p2p.MediaStreamManager;
import org.example.eduverseclient.service.MeetingService;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Slf4j
public class MeetingRoomController {

    @FXML private Label meetingTitleLabel;
    @FXML private Label participantCountLabel;
    @FXML private Label durationLabel;
    @FXML private GridPane videoGrid;
    @FXML private VBox participantListBox;

    @FXML private Button muteButton;
    @FXML private Button cameraButton;
    @FXML private Button handButton;
    @FXML private Button endButton;

    // Chat UI
    @FXML private VBox chatMessagesBox;
    @FXML private TextField chatInputField;
    @FXML private ScrollPane chatScrollPane;

    @Getter private Meeting meeting;
    @Getter private MeetingEnrollment myEnrollment;

    private MeetingService meetingService;
    private MediaStreamManager mediaStreamManager;
    private Map<String, VideoPanel> videoPanels = new HashMap<>();

    // State
    private boolean isMuted = false;
    private boolean isCameraOn = true;
    private boolean isHandRaised = false;

    private ScheduledExecutorService updateExecutor;
    private long joinTime;

    @FXML
    public void initialize() {
        meetingService = MeetingService.getInstance();
    }

    /**
     * Khởi tạo phòng họp và các kết nối
     */
    public void initMeeting(Meeting meeting, MeetingEnrollment enrollment) {
        this.meeting = meeting;
        this.myEnrollment = enrollment;
        this.joinTime = System.currentTimeMillis();

        // 1. Cập nhật UI cơ bản
        meetingTitleLabel.setText(meeting.getTitle());
        setupButtonsByRole();

        // 2. Load danh sách người tham gia
        loadParticipants();
        setupVideoGrid();

        // 3. Bắt đầu cập nhật tự động (Participants, Duration)
        startAutoUpdate();

        // 4. Khởi động Media (Video/Audio/Chat)
        initMediaStreaming();

        log.info("✅ Meeting room initialized - Role: {}", myEnrollment.getRole());
    }

    // Trong MeetingRoomController.java

    private void initMediaStreaming() {
        try {
            Peer hostPeer = meetingService.getHostPeer(meeting.getMeetingId());
            if (hostPeer == null) return;

            mediaStreamManager = new MediaStreamManager(myEnrollment);

            mediaStreamManager.start(
                    hostPeer,
                    // Callback Video
                    (userId, image) -> Platform.runLater(() -> updateVideoPanel(userId, image)),

                    // Callback Chat
                    (senderId, message) -> Platform.runLater(() -> {
                        // ✅ FIX LỖI CHAT BỊ LẶP:
                        // Chỉ hiển thị tin nhắn nếu người gửi KHÔNG PHẢI là mình
                        if (!senderId.equals(myEnrollment.getUserId())) {
                            displayChatMessage(senderId, message);
                        }
                    })
            );

            // Đồng bộ trạng thái ban đầu
            mediaStreamManager.setMicrophoneMute(isMuted);
            mediaStreamManager.setCameraActive(isCameraOn); // Gọi hàm này ngay khi vào

        } catch (Exception e) {
            log.error("❌ Failed to init media streaming", e);
        }
    }


    @FXML
    private void handleToggleCamera() {
        isCameraOn = !isCameraOn;

        // Đổi màu nút
        cameraButton.setText(isCameraOn ? "📹 Camera" : "📷❌ Off");
        cameraButton.setStyle(isCameraOn
                ? "-fx-background-color: #424242; -fx-text-fill: white; -fx-pref-width: 100; -fx-pref-height: 40; -fx-background-radius: 20;"
                : "-fx-background-color: #E53935; -fx-text-fill: white; -fx-pref-width: 100; -fx-pref-height: 40; -fx-background-radius: 20;");

        // ✅ GỌI XUỐNG MANAGER ĐỂ TẮT CAM
        if (mediaStreamManager != null) {
            mediaStreamManager.setCameraActive(isCameraOn);
        }

        updateStatus();
    }


    // ================= CHAT METHODS =================

    @FXML
    private void handleSendMessage() {
        String message = chatInputField.getText().trim();
        if (message.isEmpty()) return;

        // ✨ FIX CRASH: Kiểm tra null trước khi gửi
        if (mediaStreamManager == null) {
            showError("Chưa kết nối được với phòng họp. Vui lòng đợi...");
            return;
        }

        // Gửi qua mạng P2P
        mediaStreamManager.sendChatMessage(message);

        // Hiển thị tin nhắn của chính mình
        displayChatMessage(myEnrollment.getUserId(), message);

        chatInputField.clear();
    }

    @FXML
    private void handleAttachFile() {
        if (mediaStreamManager == null) {
            showError("Chưa kết nối mạng.");
            return;
        }

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Chọn file để gửi");
        File file = fileChooser.showOpenDialog(chatInputField.getScene().getWindow());

        if (file != null) {
            try {
                if (file.length() > 10 * 1024 * 1024) {
                    showError("File quá lớn! (Max 10MB)");
                    return;
                }

                // TODO: Implement sendFile in MediaStreamManager if needed
                // byte[] fileData = Files.readAllBytes(file.toPath());
                // mediaStreamManager.sendFile(file.getName(), fileData);

                displayFileMessage(myEnrollment.getUserId(), file.getName(), (int)file.length());

            } catch (Exception e) {
                log.error("File error", e);
            }
        }
    }

    private void displayChatMessage(String senderId, String message) {
        boolean isMe = senderId.equals(myEnrollment.getUserId());
        String senderName = isMe ? "Bạn" : getSenderName(senderId);

        HBox messageBox = new HBox(10);
        messageBox.setAlignment(isMe ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);

        VBox bubble = new VBox(5);
        bubble.setStyle(isMe
                ? "-fx-background-color: #4CAF50; -fx-background-radius: 10; -fx-padding: 8;"
                : "-fx-background-color: #424242; -fx-background-radius: 10; -fx-padding: 8;");

        Label nameLabel = new Label(senderName);
        nameLabel.setStyle("-fx-text-fill: white; -fx-font-size: 11; -fx-font-weight: bold;");

        Label messageLabel = new Label(message);
        messageLabel.setWrapText(true);
        messageLabel.setMaxWidth(250);
        messageLabel.setStyle("-fx-text-fill: white; -fx-font-size: 13;");

        bubble.getChildren().addAll(nameLabel, messageLabel);
        messageBox.getChildren().add(bubble);

        chatMessagesBox.getChildren().add(messageBox);
        Platform.runLater(() -> chatScrollPane.setVvalue(1.0));
    }

    private void displayFileMessage(String senderId, String fileName, int fileSize) {
        boolean isMe = senderId.equals(myEnrollment.getUserId());

        HBox messageBox = new HBox(10);
        messageBox.setAlignment(isMe ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);

        VBox bubble = new VBox(5);
        bubble.setStyle("-fx-background-color: #2196F3; -fx-background-radius: 10; -fx-padding: 8;");

        Label fileLabel = new Label("📎 " + fileName + " (" + formatFileSize(fileSize) + ")");
        fileLabel.setStyle("-fx-text-fill: white; -fx-font-size: 13;");

        bubble.getChildren().add(fileLabel);
        messageBox.getChildren().add(bubble);
        chatMessagesBox.getChildren().add(messageBox);
    }

    // ================= MEDIA CONTROLS (MUTE/CAMERA) =================

    @FXML
    private void handleToggleMute() {
        isMuted = !isMuted; // Đảo trạng thái

        // ✨ 1. Cập nhật UI
        muteButton.setText(isMuted ? "🔇 Muted" : "🎤 Mic");
        muteButton.setStyle(isMuted
                ? "-fx-background-color: #E53935; -fx-text-fill: white; -fx-pref-width: 80; -fx-pref-height: 40; -fx-background-radius: 20;"
                : "-fx-background-color: #424242; -fx-text-fill: white; -fx-pref-width: 80; -fx-pref-height: 40; -fx-background-radius: 20;");

        // ✨ 2. GỌI XUỐNG MEDIA STREAM MANAGER ĐỂ TẮT MIC THỰC SỰ
        if (mediaStreamManager != null) {
            mediaStreamManager.setMicrophoneMute(isMuted);
        }

        // ✨ 3. Cập nhật trạng thái lên Server (để người khác thấy icon mic tắt)
        updateStatus();
    }


    @FXML
    private void handleRaiseHand() {
        isHandRaised = !isHandRaised;
        handButton.setText(isHandRaised ? "✋ Đã giơ tay" : "✋ Giơ tay");
        handButton.setStyle(isHandRaised
                ? "-fx-background-color: #FFA726; -fx-text-fill: white; -fx-pref-width: 120; -fx-pref-height: 40; -fx-background-radius: 20;"
                : "-fx-background-color: #424242; -fx-text-fill: white; -fx-pref-width: 100; -fx-pref-height: 40; -fx-background-radius: 20;");

        updateStatus();
    }

    private void updateStatus() {
        // Gửi trạng thái mới lên server để đồng bộ icon
        new Thread(() -> {
            meetingService.updateStatus(meeting.getMeetingId(), isMuted, isCameraOn, isHandRaised);
        }).start();
    }

    // ================= VIDEO RENDERING =================

    // Trong hàm updateVideoPanel (đổi logic cũ thành logic dùng VideoPanel mới)
    private void updateVideoPanel(String userId, Image image) {
        Platform.runLater(() -> {
            VideoPanel panel = videoPanels.get(userId);
            if (panel == null) {
                // Lấy tên user để tạo panel mới
                String name = getSenderName(userId);
                panel = new VideoPanel(userId, name);

                videoPanels.put(userId, panel);

                // Add vào Grid
                int index = videoPanels.size() - 1;
                videoGrid.add(panel, index % 2, index / 2);
            }

            // Cập nhật hình ảnh
            panel.updateFrame(image);
        });
    }

    private void setupVideoGrid() {
        videoGrid.getChildren().clear();
        videoPanels.clear();
        // Không tạo placeholder tĩnh nữa, để video tự động thêm vào khi có dữ liệu
    }

    // ================= HELPER METHODS & LIFECYCLE =================

    private void setupButtonsByRole() {
        if (myEnrollment.getRole() == MeetingRole.HOST) {
            endButton.setText("🛑 Kết thúc");
            endButton.setStyle("-fx-background-color: #D32F2F; -fx-text-fill: white; -fx-background-radius: 20;");
        } else {
            endButton.setText("📞 Rời phòng");
            endButton.setStyle("-fx-background-color: #E53935; -fx-text-fill: white; -fx-background-radius: 20;");
        }
    }

    @FXML
    private void handleLeave() {
        if (myEnrollment.getRole() == MeetingRole.HOST) handleEndMeeting();
        else handleLeaveMeeting();
    }

    private void handleEndMeeting() {
        showConfirmation("Kết thúc meeting?", "Bạn là HOST. Kết thúc sẽ đuổi tất cả mọi người.", this::endMeeting);
    }

    private void handleLeaveMeeting() {
        showConfirmation("Rời khỏi meeting?", "Bạn có chắc muốn rời đi?", this::leaveMeeting);
    }

    private void showConfirmation(String title, String content, Runnable action) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Xác nhận");
        confirm.setHeaderText(title);
        confirm.setContentText(content);
        confirm.showAndWait().filter(r -> r == ButtonType.OK).ifPresent(r -> action.run());
    }

    private void endMeeting() {
        cleanup();
        new Thread(() -> {
            boolean success = meetingService.endMeeting(meeting.getMeetingId());
            Platform.runLater(() -> {
                if (success) { showInfo("Meeting đã kết thúc!"); closeWindow(); }
                else showError("Không thể kết thúc meeting!");
            });
        }).start();
    }

    private void leaveMeeting() {
        cleanup();
        new Thread(() -> {
            meetingService.leaveMeeting(meeting.getMeetingId());
            Platform.runLater(this::closeWindow);
        }).start();
    }

    private void cleanup() {
        log.info("🧹 Cleaning up meeting room resources...");
        
        // 1. Dừng auto-update executor
        if (updateExecutor != null && !updateExecutor.isShutdown()) {
            updateExecutor.shutdown();
            try {
                if (!updateExecutor.awaitTermination(2, TimeUnit.SECONDS)) {
                    log.warn("Force shutdown update executor");
                    updateExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                updateExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        
        // 2. Dừng media stream manager (sẽ cleanup tất cả threads và sockets)
        if (mediaStreamManager != null) {
            try {
                mediaStreamManager.stop();
            } catch (Exception e) {
                log.error("Error stopping media stream manager", e);
            }
        }
        
        // 3. Clear video panels
        if (videoPanels != null) {
            videoPanels.clear();
        }
        
        log.info("✅ Cleanup completed");
    }

    private void closeWindow() {
        Stage stage = (Stage) endButton.getScene().getWindow();
        stage.close();
    }

    // ... (Giữ nguyên các hàm loadParticipants, autoUpdate, formatTime, formatFileSize, getSenderName cũ của bạn) ...

    // Hàm phụ trợ để code ngắn gọn hơn
    private void loadParticipants() {
        new Thread(() -> {
            List<MeetingEnrollment> participants = meetingService.getParticipants(meeting.getMeetingId());
            Platform.runLater(() -> {
                participantCountLabel.setText(participants.size() + " người");
                displayParticipants(participants);

                scheduleAutoEnd(participants);
            });
        }).start();
    }

    private void scheduleAutoEnd(List<MeetingEnrollment> participants) {
        // Chỉ HOST mới có quyền auto end và chỉ khi phòng trống (chỉ còn 1 mình host hoặc không ai)
        if (myEnrollment.getRole() != MeetingRole.HOST) return;

        if (participants.size() <= 1) {
            log.warn("⚠️ Meeting empty - scheduling auto end check");
            // Kiểm tra lại sau 60s
            Executors.newSingleThreadScheduledExecutor().schedule(() -> {
                // Lấy lại danh sách mới nhất để kiểm tra
                List<MeetingEnrollment> currentParticipants = meetingService.getParticipants(meeting.getMeetingId());
                if (currentParticipants.size() <= 1) {
                    Platform.runLater(() -> {
                        showInfo("Meeting tự động kết thúc do không có người tham gia.");
                        endMeeting();
                    });
                }
            }, 60, TimeUnit.SECONDS);
        }
    }

    // Thay thế hàm displayParticipants hiện tại bằng hàm này
    private void displayParticipants(List<MeetingEnrollment> participants) {
        participantListBox.getChildren().clear();

        participants.forEach(p -> {
            HBox participantItem = new HBox(10);
            participantItem.setAlignment(Pos.CENTER_LEFT);
            participantItem.setStyle("-fx-background-color: #424242; -fx-background-radius: 5; -fx-padding: 10;");

            // Icon based on role
            String icon = (p.getRole() == MeetingRole.HOST) ? "👨‍🏫" : "👤";
            Label nameLabel = new Label(icon + " " + p.getUserName());

            // Highlight bản thân
            if (p.getUserId().equals(myEnrollment.getUserId())) {
                nameLabel.setText(nameLabel.getText() + " (Bạn)");
                nameLabel.setStyle("-fx-text-fill: #4CAF50; -fx-font-size: 13; -fx-font-weight: bold;");
            } else {
                nameLabel.setStyle("-fx-text-fill: white; -fx-font-size: 13;");
            }
            HBox.setHgrow(nameLabel, Priority.ALWAYS);

            // Status indicators (Mic/Cam/Hand)
            HBox statusBox = new HBox(5);

            // Mic status
            Label micLabel = new Label(p.isMuted() ? "🔇" : "🎤");
            micLabel.setStyle("-fx-text-fill: white;");
            statusBox.getChildren().add(micLabel);

            // Camera status
            if (!p.isCameraOn()) {
                Label camLabel = new Label("📷❌");
                camLabel.setStyle("-fx-text-fill: #E53935;");
                statusBox.getChildren().add(camLabel);
            }

            // Hand status
            if (p.isHandRaised()) {
                Label handLabel = new Label("✋");
                handLabel.setStyle("-fx-text-fill: #FFA726;");
                statusBox.getChildren().add(handLabel);
            }

            participantItem.getChildren().addAll(nameLabel, statusBox);
            participantListBox.getChildren().add(participantItem);
        });



        for (MeetingEnrollment p : participants) {
            VideoPanel panel = videoPanels.get(p.getUserId());
            if (panel != null) {
                // Nếu server báo user này đang tắt cam -> Chuyển về Avatar ngay
                panel.setCameraStatus(p.isCameraOn());
            }
        }
    }

    private void startAutoUpdate() {
        updateExecutor = Executors.newScheduledThreadPool(1);
        updateExecutor.scheduleAtFixedRate(this::loadParticipants, 5, 5, TimeUnit.SECONDS);
        updateExecutor.scheduleAtFixedRate(this::updateDuration, 1, 1, TimeUnit.SECONDS);
    }

    private void updateDuration() {
        long duration = (System.currentTimeMillis() - joinTime) / 1000;
        String time = String.format("%02d:%02d:%02d", duration / 3600, (duration % 3600) / 60, duration % 60);
        Platform.runLater(() -> durationLabel.setText(time));
    }

    private String getSenderName(String userId) {
        // Logic lấy tên user (có thể cache lại để tối ưu)
        return userId.substring(0, Math.min(6, userId.length()));
    }

    private String formatFileSize(long bytes) {
        return bytes < 1024 ? bytes + " B" : (bytes / 1024) + " KB";
    }

    private void showInfo(String msg) {
        Alert a = new Alert(Alert.AlertType.INFORMATION); a.setContentText(msg); a.show();
    }
    private void showError(String msg) {
        Alert a = new Alert(Alert.AlertType.ERROR); a.setContentText(msg); a.show();
    }
}