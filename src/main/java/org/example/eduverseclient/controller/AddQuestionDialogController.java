package org.example.eduverseclient.controller;

import common.model.exam.Answer;
import common.model.exam.Question;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.Stage;
import lombok.extern.slf4j.Slf4j;
import org.example.eduverseclient.service.ExamService;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

@Slf4j
public class AddQuestionDialogController {

    @FXML private TextArea questionTextArea;
    @FXML private TextField answerAField;
    @FXML private TextField answerBField;
    @FXML private TextField answerCField;
    @FXML private TextField answerDField;
    @FXML private ComboBox<String> correctAnswerCombo;
    @FXML private Spinner<Double> pointsSpinner;
    @FXML private Spinner<Integer> orderSpinner;
    
    private ExamService examService;
    private String examId;
    private Consumer<Question> onQuestionAdded;
    private boolean isCreatingMode = false; // true if creating exam, false if adding to existing exam
    
    @FXML
    public void initialize() {
        examService = ExamService.getInstance();
        
        // Correct answer combo
        correctAnswerCombo.getItems().addAll("A", "B", "C", "D");
        correctAnswerCombo.setValue("A");
        
        // Points spinner: 0.5 - 10, step 0.5
        SpinnerValueFactory.DoubleSpinnerValueFactory pointsFactory = 
            new SpinnerValueFactory.DoubleSpinnerValueFactory(0.5, 10.0, 1.0, 0.5);
        pointsSpinner.setValueFactory(pointsFactory);
        
        // Order spinner: 1 - 100
        SpinnerValueFactory.IntegerSpinnerValueFactory orderFactory = 
            new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 100, 1);
        orderSpinner.setValueFactory(orderFactory);
    }
    
    public void setExamId(String examId) {
        this.examId = examId;
    }
    
    public void setOnQuestionAdded(Consumer<Question> callback) {
        this.onQuestionAdded = callback;
    }
    
    public void setCreatingMode(boolean creatingMode) {
        this.isCreatingMode = creatingMode;
    }
    
    @FXML
    private void handleAdd() {
        // Validation
        String questionText = questionTextArea.getText().trim();
        if (questionText.isEmpty()) {
            showError("Vui lòng nhập nội dung câu hỏi!");
            return;
        }
        
        String answerA = answerAField.getText().trim();
        String answerB = answerBField.getText().trim();
        String answerC = answerCField.getText().trim();
        String answerD = answerDField.getText().trim();
        
        if (answerA.isEmpty() || answerB.isEmpty() || answerC.isEmpty() || answerD.isEmpty()) {
            showError("Vui lòng nhập đầy đủ 4 đáp án!");
            return;
        }
        
        String correctAnswerLabel = correctAnswerCombo.getValue();
        double points = pointsSpinner.getValue();
        int order = orderSpinner.getValue();
        
        // Tạo answers
        List<Answer> answers = new ArrayList<>();
        String[] answerTexts = {answerA, answerB, answerC, answerD};
        String[] labels = {"A", "B", "C", "D"};
        String correctAnswerId = null;
        
        for (int i = 0; i < 4; i++) {
            String answerId = UUID.randomUUID().toString();
            boolean isCorrect = labels[i].equals(correctAnswerLabel);
            
            if (isCorrect) {
                correctAnswerId = answerId;
            }
            
            Answer answer = Answer.builder()
                    .answerId(answerId)
                    .answerText(answerTexts[i])
                    .answerLabel(labels[i])
                    .isCorrect(isCorrect)
                    .orderIndex(i)
                    .build();
            answers.add(answer);
        }
        
        // Tạo question
        Question question = Question.builder()
                .questionId(UUID.randomUUID().toString())
                .examId(examId) // May be null if creating exam
                .questionText(questionText)
                .questionType("MULTIPLE_CHOICE")
                .answers(answers)
                .correctAnswerId(correctAnswerId)
                .points(points)
                .orderIndex(order)
                .build();
        
        // Nếu đang tạo exam (examId == null), chỉ cần callback
        if (examId == null || isCreatingMode) {
            Platform.runLater(() -> {
                if (onQuestionAdded != null) {
                    onQuestionAdded.accept(question);
                }
                closeDialog();
            });
        } else {
            // Thêm vào exam đã tồn tại
            new Thread(() -> {
                boolean success = examService.addQuestion(examId, question);
                
                Platform.runLater(() -> {
                    if (success) {
                        log.info("✅ Question added: {}", question.getQuestionId());
                        if (onQuestionAdded != null) {
                            onQuestionAdded.accept(question);
                        }
                        closeDialog();
                    } else {
                        showError("Không thể thêm câu hỏi!");
                    }
                });
            }).start();
        }
    }
    
    @FXML
    private void handleCancel() {
        closeDialog();
    }
    
    private void closeDialog() {
        Stage stage = (Stage) questionTextArea.getScene().getWindow();
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

