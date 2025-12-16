package org.example.eduverseclient.controller;


import common.enums.MeetingStatus;
import common.model.Meeting;
import common.model.MeetingEnrollment;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Modality;
import javafx.stage.Stage;
import lombok.extern.slf4j.Slf4j;
import org.example.eduverseclient.service.MeetingService;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

@Slf4j
public class MeetingListController {
    
    @FXML private VBox meetingListBox;
    
    private MeetingService meetingService;
    
    @FXML
    public void initialize() {
        meetingService = MeetingService.getInstance();
        loadMeetings();
    }
    
    private void loadMeetings() {
        // Load meetings trong background thread
        new Thread(() -> {
            // TODO: Tạm thời dùng courseId cố định, sau này sẽ chọn course
            List<Meeting> meetings = meetingService.getMeetingsByCourse("course_demo");
            
            Platform.runLater(() -> {
                meetingListBox.getChildren().clear();
                
                if (meetings.isEmpty()) {
                    Label emptyLabel = new Label("Chưa có meeting nào");
                    emptyLabel.setStyle("-fx-font-size: 14; -fx-text-fill: #999;");
                    meetingListBox.getChildren().add(emptyLabel);
                } else {
                    meetings.forEach(this::addMeetingCard);
                }
            });
        }).start();
    }
    
    private void addMeetingCard(Meeting meeting) {
        // Card container
        VBox card = new VBox(10);
        card.setStyle("-fx-background-color: white; -fx-border-color: #E0E0E0; " +
                     "-fx-border-radius: 10; -fx-background-radius: 10; -fx-padding: 15;");
        
        // Title
        Label titleLabel = new Label(meeting.getTitle());
        titleLabel.setStyle("-fx-font-size: 18; -fx-font-weight: bold;");
        
        // Info
        HBox infoBox = new HBox(20);
        
        Label hostLabel = new Label("👨‍🏫 " + meeting.getHostName());
        hostLabel.setStyle("-fx-font-size: 13; -fx-text-fill: #666;");
        
        Label timeLabel = new Label("🕐 " + formatTime(meeting.getScheduledTime()));
        timeLabel.setStyle("-fx-font-size: 13; -fx-text-fill: #666;");
        
        Label statusLabel = new Label(getStatusText(meeting.getStatus()));
        statusLabel.setStyle("-fx-font-size: 13; -fx-font-weight: bold; " +
                            "-fx-text-fill: " + getStatusColor(meeting.getStatus()));
        
        infoBox.getChildren().addAll(hostLabel, timeLabel, statusLabel);
        
        // Buttons
        HBox buttonBox = new HBox(10);
        buttonBox.setAlignment(Pos.CENTER_RIGHT);
        
        if (meeting.getStatus() == MeetingStatus.IN_PROGRESS) {
            Button joinButton = new Button("Tham gia");
            joinButton.setStyle("-fx-background-color: #43A047; -fx-text-fill: white;");
            joinButton.setOnAction(e -> handleJoinMeeting(meeting));
            buttonBox.getChildren().add(joinButton);
            
        } else if (meeting.getStatus() == MeetingStatus.SCHEDULED) {
            // TODO: Chỉ HOST mới có nút Start
            Button startButton = new Button("Bắt đầu");
            startButton.setStyle("-fx-background-color: #1976D2; -fx-text-fill: white;");
            startButton.setOnAction(e -> handleStartMeeting(meeting));
            buttonBox.getChildren().add(startButton);
        }
        
        Button detailButton = new Button("Chi tiết");
        detailButton.setStyle("-fx-background-color: #757575; -fx-text-fill: white;");
        detailButton.setOnAction(e -> showMeetingDetail(meeting));
        buttonBox.getChildren().add(detailButton);
        
        card.getChildren().addAll(titleLabel, infoBox, buttonBox);
        meetingListBox.getChildren().add(card);
    }
    
    @FXML
    private void handleCreateMeeting() {
        try {
            FXMLLoader loader = new FXMLLoader(
                getClass().getResource("/view/create-meeting-dialog.fxml")
            );
            
            Stage dialog = new Stage();
            dialog.initModality(Modality.APPLICATION_MODAL);
            dialog.setTitle("Tạo Meeting Mới");
            dialog.setScene(new Scene(loader.load(), 500, 400));
            
            CreateMeetingDialogController controller = loader.getController();
            controller.setOnMeetingCreated(this::onMeetingCreated);
            
            dialog.showAndWait();
            
        } catch (Exception e) {
            log.error("Failed to open create meeting dialog", e);
        }
    }
    
    private void onMeetingCreated(Meeting meeting) {
        loadMeetings(); // Reload list
        showInfo("Meeting đã được tạo thành công!");
    }
    
    private void handleStartMeeting(Meeting meeting) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Xác nhận");
        confirm.setHeaderText("Bắt đầu meeting: " + meeting.getTitle());
        confirm.setContentText("Bạn có chắc muốn bắt đầu meeting này?");
        
        confirm.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                new Thread(() -> {
                    boolean success = meetingService.startMeeting(meeting.getMeetingId());
                    
                    Platform.runLater(() -> {
                        if (success) {
                            showInfo("Meeting đã bắt đầu!");
                            loadMeetings();
                            // Auto join
                            handleJoinMeeting(meeting);
                        } else {
                            showError("Không thể bắt đầu meeting!");
                        }
                    });
                }).start();
            }
        });
    }
    
    private void handleJoinMeeting(Meeting meeting) {
        new Thread(() -> {
            MeetingEnrollment enrollment = meetingService.joinMeeting(meeting.getMeetingId());
            
            Platform.runLater(() -> {
                if (enrollment != null) {
                    openMeetingRoom(meeting, enrollment);
                } else {
                    showError("Không thể tham gia meeting!");
                }
            });
        }).start();
    }
    
    private void openMeetingRoom(Meeting meeting, MeetingEnrollment enrollment) {
        try {
            FXMLLoader loader = new FXMLLoader(
                getClass().getResource("/view/meeting-room.fxml")
            );
            
            Stage stage = new Stage();
            stage.setTitle("Meeting: " + meeting.getTitle());
            stage.setScene(new Scene(loader.load(), 1200, 700));
            
            MeetingRoomController controller = loader.getController();
            controller.initMeeting(meeting, enrollment);
            
            stage.show();
            
        } catch (Exception e) {
            log.error("Failed to open meeting room", e);
            showError("Không thể mở phòng meeting!");
        }
    }
    
    private void showMeetingDetail(Meeting meeting) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Chi tiết Meeting");
        alert.setHeaderText(meeting.getTitle());
        
        String content = String.format(
            "Host: %s\n" +
            "Thời gian: %s\n" +
            "Trạng thái: %s\n" +
            "Số người tham gia: %d/%d\n" +
            "Meeting ID: %s",
            meeting.getHostName(),
            formatTime(meeting.getScheduledTime()),
            getStatusText(meeting.getStatus()),
            meeting.getParticipants().size(),
            meeting.getMaxParticipants(),
            meeting.getMeetingId()
        );
        
        alert.setContentText(content);
        alert.showAndWait();
    }
    
    @FXML
    private void handleRefresh() {
        loadMeetings();
    }
    
    private String formatTime(long timestamp) {
        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy HH:mm");
        return sdf.format(new Date(timestamp));
    }
    
    private String getStatusText(MeetingStatus status) {
        switch (status) {
            case SCHEDULED: return "● Đã lên lịch";
            case IN_PROGRESS: return "● Đang diễn ra";
            case ENDED: return "● Đã kết thúc";
            case CANCELLED: return "● Đã hủy";
            default: return "● Không xác định";
        }
    }
    
    private String getStatusColor(MeetingStatus status) {
        switch (status) {
            case SCHEDULED: return "#FFA726";
            case IN_PROGRESS: return "#43A047";
            case ENDED: return "#757575";
            case CANCELLED: return "#E53935";
            default: return "#000000";
        }
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