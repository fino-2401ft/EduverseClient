package org.example.eduverseclient.controller;

import common.model.Course;
import javafx.event.Event;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.VBox;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Getter
public class MyCourseCardController {

    @FXML private ImageView thumbnailImage;
    @FXML private Label titleLabel;
    @FXML private Label teacherLabel;
    @FXML private Label progressLabel;
    @FXML private Label progressPercentLabel;
    @FXML private ProgressBar progressBar;
    @FXML private Label lessonsInfoLabel;
    @FXML private Button continueButton;
    @FXML private VBox root;

    private Course course;
    private int progress; // 0-100
    private int completedLessons;
    private int totalLessons;
    private Runnable onContinueCallback;

    @FXML
    public void initialize() {
        // Root is automatically injected by FXML
        root.setOnMouseClicked(e -> {
            if (course != null && onContinueCallback != null) {
                onContinueCallback.run();
            }
        });
    }

    /**
     * Set course data with progress and update UI
     */
    public void setCourse(Course course, int progress, int completedLessons, int totalLessons) {
        this.course = course;
        this.progress = progress;
        this.completedLessons = completedLessons;
        this.totalLessons = totalLessons;
        updateUI();
    }

    /**
     * Set callback when continue button is clicked
     */
    public void setOnContinue(Runnable callback) {
        this.onContinueCallback = callback;
    }

    /**
     * Update UI elements with course data
     */
    private void updateUI() {
        if (course == null) return;

        // Set title
        titleLabel.setText(course.getTitle());

        // Set teacher name
        if (course.getTeacherName() != null && !course.getTeacherName().isEmpty()) {
            teacherLabel.setText("By " + course.getTeacherName());
        } else {
            teacherLabel.setText("By Unknown Teacher");
        }

        // Set progress
        progressBar.setProgress(progress / 100.0);
        progressPercentLabel.setText(progress + "%");
        progressLabel.setText("Progress");
        
        // Set lessons info
        lessonsInfoLabel.setText(completedLessons + " of " + totalLessons + " lessons completed");

        // Set thumbnail
        if (course.getThumbnailUrl() != null && !course.getThumbnailUrl().isEmpty()) {
            try {
                Image image = new Image(course.getThumbnailUrl(), true);
                thumbnailImage.setImage(image);
            } catch (Exception e) {
                log.warn("Failed to load thumbnail: {}", course.getThumbnailUrl());
                setPlaceholderImage();
            }
        } else {
            setPlaceholderImage();
        }
    }

    /**
     * Set placeholder image when thumbnail is not available
     */
    private void setPlaceholderImage() {
        thumbnailImage.setImage(null);
        thumbnailImage.getStyleClass().add("placeholder-thumbnail");
    }

    /**
     * Handle continue button click
     */
    @FXML
    private void handleContinue(Event event) {
        if (course == null) return;
        
        // Stop event propagation to prevent card click
        event.consume();

        log.info("Continue learning course [{}] - {}", course.getCourseId(), course.getTitle());

        if (onContinueCallback != null) {
            onContinueCallback.run();
        }
    }
}

