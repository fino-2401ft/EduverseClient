package org.example.eduverseclient.controller;

import common.enums.MeetingRole;
import common.model.Meeting;
import common.model.MeetingEnrollment;
import common.model.Peer;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.example.eduverseclient.RMIClient;
import org.example.eduverseclient.component.VideoPanel;
import org.example.eduverseclient.network.p2p.MediaStreamManager;
import org.example.eduverseclient.service.MeetingService;
import javafx.scene.image.Image;
import java.awt.*;
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

    @Getter
    private Meeting meeting;

    @Getter
    private MeetingEnrollment myEnrollment;

    private MeetingService meetingService;
    private RMIClient rmiClient;

    private boolean isMuted = false;
    private boolean isCameraOn = true;
    private boolean isHandRaised = false;

    private ScheduledExecutorService updateExecutor;
    private long joinTime;

    private MediaStreamManager mediaStreamManager;
    private Map<String, VideoPanel> videoPanels = new HashMap<>();


    @FXML
    public void initialize() {
        meetingService = MeetingService.getInstance();
        rmiClient = RMIClient.getInstance();
    }

    public void initMeeting(Meeting meeting, MeetingEnrollment enrollment) {
        this.meeting = meeting;
        this.myEnrollment = enrollment;
        this.joinTime = System.currentTimeMillis();

        // Update UI
        meetingTitleLabel.setText(meeting.getTitle());

        // 🔧 FIX: Thay đổi nút End button cho HOST
        if (myEnrollment.getRole() == MeetingRole.HOST) {
            endButton.setText("🛑 Kết thúc meeting");
            endButton.setStyle("-fx-background-color: #D32F2F; -fx-text-fill: white; " +
                    "-fx-pref-width: 150; -fx-pref-height: 40; -fx-background-radius: 20;");
            log.info("✅ User is HOST - End Meeting button enabled");
        } else {
            endButton.setText("📞 Rời phòng");
            endButton.setStyle("-fx-background-color: #E53935; -fx-text-fill: white; " +
                    "-fx-pref-width: 120; -fx-pref-height: 40; -fx-background-radius: 20;");
            log.info("✅ User is PARTICIPANT - Leave button enabled");
        }

        // Load participants
        loadParticipants();

        // Setup video grid
        setupVideoGrid();

        // Start auto-update
        startAutoUpdate();

        initMediaStreaming();

        log.info("✅ Meeting room initialized - Role: {}", myEnrollment.getRole());
    }

    // Thêm methods:
    private void initMediaStreaming() {
        try {
            Peer hostPeer = meetingService.getHostPeer(meeting.getMeetingId());

            if (hostPeer == null) {
                log.error("❌ Host peer not found");
                return;
            }

            mediaStreamManager = new MediaStreamManager(myEnrollment);
            mediaStreamManager.start(hostPeer, (userId, image) -> {
                Platform.runLater(() -> updateVideoPanel(userId, image));
            });

            log.info("✅ Media streaming initialized");

        } catch (Exception e) {
            log.error("❌ Failed to init media streaming", e);
        }
    }

    private void updateVideoPanel(String userId, Image image) {
        VideoPanel panel = videoPanels.get(userId);

        if (panel == null) {
            panel = new VideoPanel(userId, "User " + userId.substring(0, Math.min(8, userId.length())));
            videoPanels.put(userId, panel);

            int index = videoPanels.size() - 1;
            videoGrid.add(panel, index % 2, index / 2);
        }

        panel.updateFrame(image);
    }




    private void loadParticipants() {
        new Thread(() -> {
            List<MeetingEnrollment> participants = meetingService.getParticipants(meeting.getMeetingId());

            Platform.runLater(() -> {
                participantCountLabel.setText(participants.size() + " người tham gia");
                displayParticipants(participants);

                // 🔧 AUTO END nếu không còn ai (sau 1 phút)
                if (participants.isEmpty()) {
                    scheduleAutoEnd();
                }
            });
        }).start();
    }

    private void scheduleAutoEnd() {
        // Chỉ HOST mới auto end
        if (myEnrollment.getRole() != MeetingRole.HOST) return;

        log.warn("⚠️ No participants in meeting - scheduling auto end in 60s");

        Executors.newSingleThreadScheduledExecutor().schedule(() -> {
            // Kiểm tra lại sau 60s
            List<MeetingEnrollment> participants = meetingService.getParticipants(meeting.getMeetingId());

            if (participants.size() <= 1) { // Chỉ có HOST
                log.warn("🛑 Auto ending meeting - no participants for 60s");
                Platform.runLater(() -> {
                    showInfo("Meeting tự động kết thúc do không có người tham gia trong 1 phút");
                    endMeeting();
                });
            }
        }, 60, TimeUnit.SECONDS);
    }

    private void displayParticipants(List<MeetingEnrollment> participants) {
        participantListBox.getChildren().clear();

        participants.forEach(p -> {
            HBox participantItem = new HBox(10);
            participantItem.setAlignment(Pos.CENTER_LEFT);
            participantItem.setStyle("-fx-background-color: #424242; -fx-background-radius: 5; -fx-padding: 10;");

            // Icon based on role
            String icon = (p.getRole() == MeetingRole.HOST) ? "👨‍🏫" : "👨‍🎓";

            Label nameLabel = new Label(icon + " " + p.getUserName());
            nameLabel.setStyle("-fx-text-fill: white; -fx-font-size: 13;");

            // Highlight current user
            if (p.getUserId().equals(myEnrollment.getUserId())) {
                nameLabel.setText(nameLabel.getText() + " (Bạn)");
                nameLabel.setStyle("-fx-text-fill: #4CAF50; -fx-font-size: 13; -fx-font-weight: bold;");
            }

            HBox.setHgrow(nameLabel, Priority.ALWAYS);

            // Status indicators
            HBox statusBox = new HBox(5);

            if (p.isMuted()) {
                Label mutedLabel = new Label("🔇");
                statusBox.getChildren().add(mutedLabel);
            } else {
                Label micLabel = new Label("🎤");
                statusBox.getChildren().add(micLabel);
            }

            if (!p.isCameraOn()) {
                Label cameraOffLabel = new Label("📷❌");
                statusBox.getChildren().add(cameraOffLabel);
            }

            if (p.isHandRaised()) {
                Label handLabel = new Label("✋");
                handLabel.setStyle("-fx-text-fill: #FFA726;");
                statusBox.getChildren().add(handLabel);
            }

            participantItem.getChildren().addAll(nameLabel, statusBox);
            participantListBox.getChildren().add(participantItem);
        });
    }

    private void setupVideoGrid() {
        videoGrid.getChildren().clear();

        // Placeholder video panels
        for (int i = 0; i < 4; i++) {
            VBox videoPanel = createVideoPlaceholder("Participant " + (i + 1));
            videoGrid.add(videoPanel, i % 2, i / 2);
        }
    }

    private VBox createVideoPlaceholder(String name) {
        VBox panel = new VBox();
        panel.setAlignment(Pos.CENTER);
        panel.setStyle("-fx-background-color: #424242; -fx-background-radius: 10;");
        panel.setPrefSize(400, 300);

        Label nameLabel = new Label(name);
        nameLabel.setStyle("-fx-text-fill: white; -fx-font-size: 16;");

        Label iconLabel = new Label("👤");
        iconLabel.setStyle("-fx-font-size: 48;");

        panel.getChildren().addAll(iconLabel, nameLabel);

        return panel;
    }

    @FXML
    private void handleToggleMute() {
        isMuted = !isMuted;

        muteButton.setText(isMuted ? "🔇 Muted" : "🎤 Mic");
        muteButton.setStyle(isMuted
                ? "-fx-background-color: #E53935; -fx-text-fill: white; -fx-pref-width: 80; -fx-pref-height: 40; -fx-background-radius: 20;"
                : "-fx-background-color: #424242; -fx-text-fill: white; -fx-pref-width: 80; -fx-pref-height: 40; -fx-background-radius: 20;");

        updateStatus();
    }

    @FXML
    private void handleToggleCamera() {
        isCameraOn = !isCameraOn;

        cameraButton.setText(isCameraOn ? "📹 Camera" : "📷❌ Off");
        cameraButton.setStyle(isCameraOn
                ? "-fx-background-color: #424242; -fx-text-fill: white; -fx-pref-width: 100; -fx-pref-height: 40; -fx-background-radius: 20;"
                : "-fx-background-color: #E53935; -fx-text-fill: white; -fx-pref-width: 100; -fx-pref-height: 40; -fx-background-radius: 20;");

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
        new Thread(() -> {
            meetingService.updateStatus(meeting.getMeetingId(), isMuted, isCameraOn, isHandRaised);
        }).start();
    }

    @FXML
    private void handleLeave() {
        // 🔧 FIX: Phân biệt HOST và PARTICIPANT
        if (myEnrollment.getRole() == MeetingRole.HOST) {
            handleEndMeeting();
        } else {
            handleLeaveMeeting();
        }
    }

    private void handleEndMeeting() {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Xác nhận");
        confirm.setHeaderText("Kết thúc meeting?");
        confirm.setContentText("Bạn là HOST. Kết thúc meeting sẽ đuổi tất cả người tham gia ra.");

        confirm.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                endMeeting();
            }
        });
    }

    private void handleLeaveMeeting() {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Xác nhận");
        confirm.setHeaderText("Rời khỏi meeting?");
        confirm.setContentText("Bạn có chắc muốn rời khỏi meeting này?");

        confirm.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                leaveMeeting();
            }
        });
    }

    private void endMeeting() {
        // Stop auto-update
        if (updateExecutor != null) {
            updateExecutor.shutdown();
        }

        // End meeting (HOST only)
        new Thread(() -> {
            boolean success = meetingService.endMeeting(meeting.getMeetingId());

            Platform.runLater(() -> {
                if (success) {
                    showInfo("Meeting đã kết thúc!");
                    closeWindow();
                } else {
                    showError("Không thể kết thúc meeting!");
                }
            });
        }).start();
    }

    private void leaveMeeting() {
        // Stop auto-update
        if (updateExecutor != null) {
            updateExecutor.shutdown();
        }

        // Leave meeting
        new Thread(() -> {
            meetingService.leaveMeeting(meeting.getMeetingId());

            Platform.runLater(this::closeWindow);
        }).start();

        // Đóng media stream
        if (mediaStreamManager != null) {
            mediaStreamManager.stop();
        }
    }

    private void closeWindow() {
        Stage stage = (Stage) endButton.getScene().getWindow();
        stage.close();
    }

    private void startAutoUpdate() {
        updateExecutor = Executors.newScheduledThreadPool(1);

        // Update participants every 5 seconds
        updateExecutor.scheduleAtFixedRate(() -> {
            try {
                loadParticipants();
            } catch (Exception e) {
                log.error("Auto update failed", e);
            }
        }, 5, 5, TimeUnit.SECONDS);

        // Update duration every second
        updateExecutor.scheduleAtFixedRate(() -> {
            try {
                updateDuration();
            } catch (Exception e) {
                log.error("Duration update failed", e);
            }
        }, 1, 1, TimeUnit.SECONDS);
    }

    private void updateDuration() {
        long duration = (System.currentTimeMillis() - joinTime) / 1000;
        long hours = duration / 3600;
        long minutes = (duration % 3600) / 60;
        long seconds = duration % 60;

        String durationText = String.format("⏱ %02d:%02d:%02d", hours, minutes, seconds);

        Platform.runLater(() -> durationLabel.setText(durationText));
    }

    private void showInfo(String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Thông báo");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Lỗi");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
