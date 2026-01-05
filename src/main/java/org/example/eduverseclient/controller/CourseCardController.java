package org.example.eduverseclient.controller;

import common.model.Course;
import javafx.event.Event;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.VBox;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Getter
public class CourseCardController {

    @FXML private ImageView thumbnailImage;
    @FXML private Label titleLabel;
    @FXML private Label teacherLabel;
    @FXML private Label studentCountLabel;
    @FXML private Button actionButton;
    @FXML private VBox root;

    private Course course;
    private String currentUserId;
    private Runnable onCourseClickCallback;

    @FXML
    public void initialize() {
        // Root is automatically injected by FXML
        // Make card clickable
        root.setOnMouseClicked(e -> {
            if (course != null && onCourseClickCallback != null) {
                onCourseClickCallback.run();
            }
        });
    }
    
    /**
     * Set callback when course card is clicked
     */
    public void setOnCourseClick(Runnable callback) {
        this.onCourseClickCallback = callback;
    }

    /**
     * Set course data and update UI
     */
    public void setCourse(Course course, String currentUserId) {
        this.course = course;
        this.currentUserId = currentUserId;
        updateUI();
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

        // Set student count
        int studentCount = course.getStudentCount();
        studentCountLabel.setText(studentCount + " students enrolled");

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

        // Set action button based on enrollment status
        if (currentUserId != null && course.hasStudent(currentUserId)) {
            actionButton.setText("Continue Learning");
            actionButton.getStyleClass().add("button-continue");
            actionButton.getStyleClass().remove("button-enroll");
        } else {
            actionButton.setText("Enroll Now");
            actionButton.getStyleClass().add("button-enroll");
            actionButton.getStyleClass().remove("button-continue");
        }
    }

    /**
     * Set placeholder image when thumbnail is not available
     */
    private void setPlaceholderImage() {
        // Create a simple placeholder using a data URL or use a default image
        // For now, we'll use a CSS background color as fallback
        thumbnailImage.setImage(null);
        thumbnailImage.getStyleClass().add("placeholder-thumbnail");
    }

    /**
     * Handle action button click
     */
    @FXML
    private void handleAction(Event event) {
        if (course == null) return;
        
        // Stop event propagation to prevent card click
        event.consume();

        log.info("Clicked action button for course [{}] - {}", course.getCourseId(), course.getTitle());

        // Always navigate to course detail when clicking button
        // User can enroll from course detail page if not enrolled yet
        if (onCourseClickCallback != null) {
            onCourseClickCallback.run();
        } else {
            log.warn("⚠️ onCourseClickCallback is null for course: {}", course.getCourseId());
        }
    }
    
    /**
     * Enroll in course
     */
    private void enrollInCourse() {
        if (course == null || currentUserId == null) return;
        
        // Show confirmation dialog
        Alert confirmDialog = new Alert(Alert.AlertType.CONFIRMATION);
        confirmDialog.setTitle("Enroll in Course");
        confirmDialog.setHeaderText("Enroll in " + course.getTitle() + "?");
        confirmDialog.setContentText("Do you want to enroll in this course?");
        
        confirmDialog.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                // Call enrollment service
                org.example.eduverseclient.service.CourseService courseService = 
                    org.example.eduverseclient.service.CourseService.getInstance();
                
                boolean success = courseService.joinCourse(course.getCourseId(), currentUserId);
                
                if (success) {
                    // Update UI
                    actionButton.setText("Continue Learning");
                    actionButton.getStyleClass().remove("button-enroll");
                    actionButton.getStyleClass().add("button-continue");
                    course.addStudent(currentUserId);
                    
                    log.info("✅ Successfully enrolled in course: {}", course.getCourseId());
                } else {
                    Alert errorDialog = new Alert(Alert.AlertType.ERROR);
                    errorDialog.setTitle("Enrollment Failed");
                    errorDialog.setHeaderText("Could not enroll in course");
                    errorDialog.setContentText("Please try again later.");
                    errorDialog.showAndWait();
                }
            }
        });
    }

    /**
     * Get root node for adding to parent container
     */
    public VBox getRoot() {
        return root;
    }
}

