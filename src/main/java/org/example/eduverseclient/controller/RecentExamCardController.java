package org.example.eduverseclient.controller;

import common.model.exam.ExamResult;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.text.SimpleDateFormat;
import java.util.Date;

@Slf4j
@Getter
public class RecentExamCardController {

    @FXML private Label examStatusIcon;
    @FXML private Label examTitleLabel;
    @FXML private Label courseNameLabel;
    @FXML private Label scoreLabel;
    @FXML private Label dateLabel;
    @FXML private Label statusLabel;
    @FXML private Button viewButton;
    @FXML private HBox root;

    private ExamResult examResult;
    private Runnable onViewCallback;

    @FXML
    public void initialize() {
        root.setOnMouseClicked(e -> {
            if (examResult != null && onViewCallback != null) {
                onViewCallback.run();
            }
        });
    }

    /**
     * Set exam result data and update UI
     */
    public void setExamResult(ExamResult examResult) {
        this.examResult = examResult;
        updateUI();
    }

    /**
     * Set callback when view button is clicked
     */
    public void setOnView(Runnable callback) {
        this.onViewCallback = callback;
    }

    /**
     * Update UI elements
     */
    private void updateUI() {
        if (examResult == null) return;

        // Set exam title (you may need to get exam title from examId)
        examTitleLabel.setText("Exam: " + examResult.getExamId());

        // Set course name (if available)
        courseNameLabel.setText("Course: " + (examResult.getExamId() != null ? examResult.getExamId() : "Unknown"));

        // Set score
        if (examResult.getStatus().equals("GRADED")) {
            scoreLabel.setText(String.format("%.1f / %.1f", examResult.getTotalScore(), examResult.getMaxScore()));
        } else {
            scoreLabel.setText("Not graded");
        }

        // Set date
        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy HH:mm");
        dateLabel.setText(sdf.format(new Date(examResult.getSubmitTime() > 0 ? examResult.getSubmitTime() : examResult.getCreatedAt())));

        // Set status
        String status = examResult.getStatus();
        statusLabel.setText(status);
        
        // Set status icon
        if (examResult.isPassed()) {
            examStatusIcon.setText("✅");
            statusLabel.getStyleClass().add("exam-status-passed");
        } else if (status.equals("GRADED")) {
            examStatusIcon.setText("❌");
            statusLabel.getStyleClass().add("exam-status-failed");
        } else {
            examStatusIcon.setText("📝");
            statusLabel.getStyleClass().add("exam-status-pending");
        }
    }

    /**
     * Handle view button click
     */
    @FXML
    private void handleView() {
        if (examResult != null && onViewCallback != null) {
            onViewCallback.run();
        }
    }
    
    /**
     * Get root node for adding to parent container
     */
    public HBox getRoot() {
        return root;
    }
}

