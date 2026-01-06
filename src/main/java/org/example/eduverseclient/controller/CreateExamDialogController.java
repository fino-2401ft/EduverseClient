package org.example.eduverseclient.controller;

import common.model.Course;
import common.model.exam.Answer;
import common.model.exam.Exam;
import common.model.exam.Question;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import lombok.extern.slf4j.Slf4j;
import org.example.eduverseclient.service.ExamService;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

@Slf4j
public class CreateExamDialogController {

    @FXML private TextField titleField;
    @FXML private TextArea descriptionArea;
    @FXML private DatePicker datePicker;
    @FXML private TextField timeField;
    @FXML private Spinner<Integer> durationSpinner;
    @FXML private VBox questionsContainer;
    @FXML private ScrollPane questionsScrollPane;
    
    private ExamService examService;
    private Course course;
    private Consumer<Exam> onExamCreated;
    private List<Question> questions = new ArrayList<>();
    
    @FXML
    public void initialize() {
        examService = ExamService.getInstance();
        
        // Set default date = today
        datePicker.setValue(LocalDate.now());
        
        // Duration spinner: 15-180 minutes, step 15
        SpinnerValueFactory.IntegerSpinnerValueFactory factory = 
            new SpinnerValueFactory.IntegerSpinnerValueFactory(15, 180, 60, 15);
        durationSpinner.setValueFactory(factory);
        
        // Questions container setup
        if (questionsContainer != null) {
            questionsContainer.setSpacing(10);
            refreshQuestionsList(); // Initialize empty list
        }
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
                
                if (exam != null) {
                    log.info("✅ Exam created: {}", exam.getExamId());
                    
                    // Add all questions to exam
                    if (!questions.isEmpty()) {
                        for (Question question : questions) {
                            question.setExamId(exam.getExamId());
                            boolean success = examService.addQuestion(exam.getExamId(), question);
                            if (!success) {
                                log.warn("Failed to add question: {}", question.getQuestionText());
                            }
                        }
                        log.info("✅ Added {} questions to exam", questions.size());
                    }
                    
                    Platform.runLater(() -> {
                        if (onExamCreated != null) {
                            onExamCreated.accept(exam);
                        }
                        closeDialog();
                    });
                } else {
                    Platform.runLater(() -> showError("Không thể tạo bài thi!"));
                }
            }).start();
            
        } catch (Exception e) {
            showError("Định dạng giờ không đúng! (VD: 14:30)");
        }
    }
    
    @FXML
    private void handleAddQuestion() {
        try {
            FXMLLoader loader = new FXMLLoader(
                getClass().getResource("/view/add-question-dialog.fxml")
            );
            
            Stage dialog = new Stage();
            dialog.initModality(Modality.APPLICATION_MODAL);
            dialog.setTitle("Thêm Câu Hỏi");
            dialog.setScene(new Scene(loader.load(), 600, 500));
            
            AddQuestionDialogController controller = loader.getController();
            controller.setExamId(null); // Chưa có examId vì chưa tạo exam
            controller.setCreatingMode(true); // Đang ở chế độ tạo exam
            controller.setOnQuestionAdded(this::onQuestionAdded);
            
            dialog.showAndWait();
            
        } catch (Exception e) {
            log.error("Failed to open add question dialog", e);
            showError("Không thể mở dialog thêm câu hỏi!");
        }
    }
    
    private void onQuestionAdded(Question question) {
        questions.add(question);
        refreshQuestionsList();
    }
    
    private void refreshQuestionsList() {
        if (questionsContainer == null) return;
        
        questionsContainer.getChildren().clear();
        
        if (questions.isEmpty()) {
            Label emptyLabel = new Label("Chưa có câu hỏi nào. Nhấn 'Thêm câu hỏi' để thêm.");
            emptyLabel.setStyle("-fx-text-fill: #666; -fx-font-size: 12;");
            questionsContainer.getChildren().add(emptyLabel);
            return;
        }
        
        for (int i = 0; i < questions.size(); i++) {
            Question q = questions.get(i);
            VBox questionCard = createQuestionCard(q, i + 1);
            questionsContainer.getChildren().add(questionCard);
        }
    }
    
    private VBox createQuestionCard(Question question, int index) {
        VBox card = new VBox(8);
        card.setStyle("-fx-background-color: #F5F5F5; -fx-background-radius: 5; -fx-padding: 10;");
        
        HBox header = new HBox(10);
        Label questionLabel = new Label("Câu " + index + ": " + question.getQuestionText());
        questionLabel.setWrapText(true);
        questionLabel.setStyle("-fx-font-weight: bold;");
        
        Label pointsLabel = new Label("(" + question.getPoints() + " điểm)");
        pointsLabel.setStyle("-fx-text-fill: #1976D2;");
        
        Button removeButton = new Button("✕");
        removeButton.setStyle("-fx-background-color: #E53935; -fx-text-fill: white; -fx-pref-width: 30;");
        removeButton.setOnAction(e -> {
            questions.remove(question);
            refreshQuestionsList();
        });
        
        header.getChildren().addAll(questionLabel, pointsLabel);
        HBox.setHgrow(questionLabel, javafx.scene.layout.Priority.ALWAYS);
        
        HBox footer = new HBox(10);
        footer.setAlignment(javafx.geometry.Pos.CENTER_RIGHT);
        footer.getChildren().add(removeButton);
        
        card.getChildren().addAll(header, footer);
        return card;
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

