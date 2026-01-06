package org.example.eduverseclient.controller;

import common.model.Course;
import common.model.exam.Exam;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.Stage;
import lombok.extern.slf4j.Slf4j;
import org.example.eduverseclient.service.ExamService;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.function.Consumer;

@Slf4j
public class CreateExamDialogController {

    @FXML private TextField titleField;
    @FXML private TextArea descriptionArea;
    @FXML private DatePicker datePicker;
    @FXML private TextField timeField;
    @FXML private Spinner<Integer> durationSpinner;
    
    private ExamService examService;
    private Course course;
    private Consumer<Exam> onExamCreated;
    
    @FXML
    public void initialize() {
        examService = ExamService.getInstance();
        
        // Set default date = today
        datePicker.setValue(LocalDate.now());
        
        // Duration spinner: 15-180 minutes, step 15
        SpinnerValueFactory.IntegerSpinnerValueFactory factory = 
            new SpinnerValueFactory.IntegerSpinnerValueFactory(15, 180, 60, 15);
        durationSpinner.setValueFactory(factory);
    }
    
    public void setCourse(Course course) {
        this.course = course;
    }
    
    public void setOnExamCreated(Consumer<Exam> callback) {
        this.onExamCreated = callback;
    }
    
    @FXML
    private void handleCreate() {
        // Validation
        String title = titleField.getText().trim();
        if (title.isEmpty()) {
            showError("Vui lòng nhập tiêu đề bài thi!");
            return;
        }
        
        String description = descriptionArea.getText().trim();
        
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
        
        int durationMinutes = durationSpinner.getValue();
        
        // Parse time
        try {
            String[] parts = timeStr.split(":");
            int hour = Integer.parseInt(parts[0]);
            int minute = Integer.parseInt(parts[1]);
            
            LocalDateTime dateTime = date.atTime(hour, minute);
            long scheduledTime = dateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
            
            // Create exam
            new Thread(() -> {
                Exam exam = examService.createExam(
                    course.getCourseId(),
                    title,
                    description,
                    durationMinutes,
                    scheduledTime
                );
                
                Platform.runLater(() -> {
                    if (exam != null) {
                        log.info("✅ Exam created: {}", exam.getExamId());
                        
                        if (onExamCreated != null) {
                            onExamCreated.accept(exam);
                        }
                        
                        closeDialog();
                    } else {
                        showError("Không thể tạo bài thi!");
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

