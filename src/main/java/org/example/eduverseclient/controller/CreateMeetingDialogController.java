package org.example.eduverseclient.controller;


import common.model.Meeting;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.Stage;
import lombok.extern.slf4j.Slf4j;
import org.example.eduverseclient.service.MeetingService;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.function.Consumer;

@Slf4j
public class CreateMeetingDialogController {

    @FXML private ComboBox courseComboBox;
    @FXML private TextField titleField;
    @FXML private DatePicker datePicker;
    @FXML private TextField timeField;
    @FXML private TextArea descriptionArea;
    
    private MeetingService meetingService;
    private Consumer<Meeting> onMeetingCreated;
    
    @FXML
    public void initialize() {
        meetingService = MeetingService.getInstance();
        
        // Set default date = today
        datePicker.setValue(LocalDate.now());
    }
    
    public void setOnMeetingCreated(Consumer<Meeting> callback) {
        this.onMeetingCreated = callback;
    }
    
    @FXML
    private void handleCreate() {
        // Validation
        String title = titleField.getText().trim();
        if (title.isEmpty()) {
            showError("Vui lòng nhập tiêu đề meeting!");
            return;
        }
        
        LocalDate date = datePicker.getValue();
        if (date == null) {
            showError("Vui lòng chọn ngày!");
            return;
        }
        
        String timeStr = timeField.getText().trim();
        if (timeStr.isEmpty()) {
            showError("Vui lòng nhập giờ! (VD: 14:30)");
            return;
        }
        
        // Parse time
        try {
            String[] parts = timeStr.split(":");
            int hour = Integer.parseInt(parts[0]);
            int minute = Integer.parseInt(parts[1]);
            
            LocalDateTime dateTime = date.atTime(hour, minute);
            long scheduledTime = dateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
            
            // Create meeting
            new Thread(() -> {
                Meeting meeting = meetingService.createMeeting("course_demo", title, scheduledTime);
                
                Platform.runLater(() -> {
                    if (meeting != null) {
                        log.info("✅ Meeting created");
                        
                        if (onMeetingCreated != null) {
                            onMeetingCreated.accept(meeting);
                        }
                        
                        closeDialog();
                    } else {
                        showError("Không thể tạo meeting!");
                    }
                });
            }).start();
            
        } catch (Exception e) {
            showError("Định dạng giờ không đúng! (VD: 14:30)");
        }
    }
    
    @FXML
    private void handleCancel() {
        closeDialog();
    }
    
    private void closeDialog() {
        Stage stage = (Stage) titleField.getScene().getWindow();
        stage.close();
    }
    
    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Lỗi");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}