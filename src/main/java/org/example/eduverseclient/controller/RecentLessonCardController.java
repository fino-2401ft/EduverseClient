package org.example.eduverseclient.controller;

import common.model.Course;
import common.model.Lesson;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Getter
public class RecentLessonCardController {

    @FXML private ImageView courseThumbnail;
    @FXML private Label courseTitleLabel;
    @FXML private Label lessonTitleLabel;
    @FXML private ProgressBar lessonProgressBar;
    @FXML private Label durationLabel;
    @FXML private Button resumeButton;
    @FXML private HBox root;

    private Course course;
    private Lesson lesson;
    private double lessonProgress; // 0.0-1.0
    private Runnable onResumeCallback;

    @FXML
    public void initialize() {
        root.setOnMouseClicked(e -> {
            if (lesson != null && onResumeCallback != null) {
                onResumeCallback.run();
            }
        });
    }

    /**
     * Set lesson data and update UI
     */
    public void setLesson(Course course, Lesson lesson, double lessonProgress) {
        this.course = course;
        this.lesson = lesson;
        this.lessonProgress = lessonProgress;
        updateUI();
    }

    /**
     * Set callback when resume button is clicked
     */
    public void setOnResume(Runnable callback) {
        this.onResumeCallback = callback;
    }

    /**
     * Update UI elements
     */
    private void updateUI() {
        if (course == null || lesson == null) return;

        // Set course title
        courseTitleLabel.setText(course.getTitle());

        // Set lesson title
        lessonTitleLabel.setText(lesson.getTitle());

        // Set progress
        lessonProgressBar.setProgress(lessonProgress);

        // Set duration
        int minutes = lesson.getVideoDuration() / 60;
        int seconds = lesson.getVideoDuration() % 60;
        durationLabel.setText(String.format("%d:%02d", minutes, seconds));

        // Set course thumbnail
        if (course.getThumbnailUrl() != null && !course.getThumbnailUrl().isEmpty()) {
            try {
                Image image = new Image(course.getThumbnailUrl(), true);
                courseThumbnail.setImage(image);
            } catch (Exception e) {
                log.warn("Failed to load course thumbnail: {}", course.getThumbnailUrl());
                courseThumbnail.setImage(null);
                courseThumbnail.getStyleClass().add("placeholder-thumbnail");
            }
        } else {
            courseThumbnail.setImage(null);
            courseThumbnail.getStyleClass().add("placeholder-thumbnail");
        }
    }

    /**
     * Handle resume button click
     */
    @FXML
    private void handleResume() {
        if (lesson != null && onResumeCallback != null) {
            onResumeCallback.run();
        }
    }
    
    /**
     * Get root node for adding to parent container
     */
    public HBox getRoot() {
        return root;
    }
}

