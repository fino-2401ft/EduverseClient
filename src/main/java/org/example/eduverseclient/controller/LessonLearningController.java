package org.example.eduverseclient.controller;

import common.model.Course;
import common.model.CourseEnrollment;
import common.model.Lesson;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import javafx.scene.web.WebView;
import lombok.extern.slf4j.Slf4j;
import org.example.eduverseclient.RMIClient;
import org.example.eduverseclient.service.CourseService;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Slf4j
public class LessonLearningController {

    @FXML private Button backButton;
    @FXML private Label courseTitleLabel;
    @FXML private Label progressLabel;
    @FXML private ProgressBar progressBar;

    @FXML private WebView videoWebView;
    @FXML private TabPane contentTabPane;

    @FXML private Label lessonTitleLabel;
    @FXML private Label lessonDescriptionLabel;
    @FXML private Label lessonContentLabel;
    @FXML private VBox resourcesContainer;
    @FXML private TextArea notesTextArea;

    @FXML private Label lessonCountLabel;
    @FXML private VBox lessonListContainer;

    @FXML private Button previousButton;
    @FXML private Button completeButton;
    @FXML private Button nextButton;

    private CourseService courseService;
    private Course course;
    private List<Lesson> lessons;
    private Lesson currentLesson;
    private int currentLessonIndex = 0;
    private String currentUserId;
    private CourseEnrollment enrollment;
    private List<String> completedLessonIds;

    @FXML
    public void initialize() {
        courseService = CourseService.getInstance();
        currentUserId = RMIClient.getInstance().getCurrentUser() != null
                ? RMIClient.getInstance().getCurrentUser().getUserId()
                : null;

        completedLessonIds = new ArrayList<>();

        log.info("✅ Lesson Learning Controller initialized");
    }

    /**
     * Load lesson for learning
     */
    public void loadLesson(String courseId, String lessonId) {
        log.info("📚 Loading lesson: {} for course: {}", lessonId, courseId);

        CompletableFuture.supplyAsync(() -> {
            return courseService.getCourseById(courseId);
        }).thenAccept(loadedCourse -> {
            Platform.runLater(() -> {
                if (loadedCourse != null) {
                    this.course = loadedCourse;
                    courseTitleLabel.setText(course.getTitle());
                    loadCourseData(lessonId);
                } else {
                    showError("Không thể tải khóa học!");
                }
            });
        });
    }

    /**
     * Load course data including lessons and enrollment
     */
    private void loadCourseData(String lessonId) {
        // Load lessons
        CompletableFuture.supplyAsync(() -> {
            return courseService.getLessonsByCourse(course.getCourseId());
        }).thenAccept(loadedLessons -> {
            Platform.runLater(() -> {
                this.lessons = loadedLessons != null ? loadedLessons : new ArrayList<>();

                if (lessons.isEmpty()) {
                    showError("Khóa học chưa có bài học nào!");
                    return;
                }

                // Find lesson index
                for (int i = 0; i < lessons.size(); i++) {
                    if (lessons.get(i).getLessonId().equals(lessonId)) {
                        currentLessonIndex = i;
                        break;
                    }
                }

                loadEnrollment();
                displayLessonList();
                loadCurrentLesson();
            });
        });
    }

    /**
     * Load enrollment data
     */
    private void loadEnrollment() {
        if (currentUserId == null) return;

        CompletableFuture.supplyAsync(() -> {
            return courseService.getEnrollmentByCourseAndStudent(
                    course.getCourseId(),
                    currentUserId
            );
        }).thenAccept(loadedEnrollment -> {
            Platform.runLater(() -> {
                if (loadedEnrollment != null) {
                    this.enrollment = loadedEnrollment;
                    this.completedLessonIds = enrollment.getCompletedLessonIds() != null
                            ? new ArrayList<>(enrollment.getCompletedLessonIds())
                            : new ArrayList<>();
                    updateProgress();
                }
            });
        });
    }

    /**
     * Load and display current lesson
     */
    private void loadCurrentLesson() {
        if (currentLessonIndex < 0 || currentLessonIndex >= lessons.size()) {
            return;
        }

        currentLesson = lessons.get(currentLessonIndex);

        log.info("📖 Loading lesson: {}", currentLesson.getTitle());

        // Update lesson info
        lessonTitleLabel.setText(currentLesson.getTitle());
        lessonDescriptionLabel.setText(currentLesson.getDescription() != null
                ? currentLesson.getDescription()
                : "No description available");
        lessonContentLabel.setText(currentLesson.getContent() != null
                ? currentLesson.getContent()
                : "No content available");

        // Load video
        loadVideo();

        // Update navigation buttons
        updateNavigationButtons();

        // Update complete button
        updateCompleteButton();

        // Highlight current lesson in sidebar
        highlightCurrentLesson();

        // Load resources
        loadResources();
    }

    /**
     * Load video into WebView
     */
    private void loadVideo() {
        if (currentLesson.getVideoUrl() == null || currentLesson.getVideoUrl().isEmpty()) {
            String noVideoHtml = "<html><body style='margin:0;padding:0;background:#000;display:flex;align-items:center;justify-content:center;height:100vh;'>" +
                    "<p style='color:#fff;font-family:Arial;font-size:18px;'>No video available</p>" +
                    "</body></html>";
            videoWebView.getEngine().loadContent(noVideoHtml);
            return;
        }

        String videoUrl = currentLesson.getVideoUrl();
        String videoId = extractYouTubeVideoId(videoUrl);

        if (videoId != null) {
            String embedHtml = createYouTubeEmbedHtml(videoId);
            videoWebView.getEngine().loadContent(embedHtml);
        } else {
            // For other video sources
            String videoHtml = "<html><body style='margin:0;padding:0;'>" +
                    "<video width='100%' height='100%' controls>" +
                    "<source src='" + videoUrl + "' type='video/mp4'>" +
                    "Your browser does not support the video tag." +
                    "</video></body></html>";
            videoWebView.getEngine().loadContent(videoHtml);
        }
    }

    /**
     * Extract YouTube video ID from URL
     */
    private String extractYouTubeVideoId(String url) {
        if (url.contains("youtube.com/watch?v=")) {
            int start = url.indexOf("v=") + 2;
            int end = url.indexOf("&", start);
            return end > start ? url.substring(start, end) : url.substring(start);
        } else if (url.contains("youtu.be/")) {
            int start = url.lastIndexOf("/") + 1;
            return url.substring(start);
        }
        return null;
    }

    /**
     * Create YouTube embed HTML
     */
    private String createYouTubeEmbedHtml(String videoId) {
        return "<html><head><style>body{margin:0;padding:0;overflow:hidden;}</style></head>" +
                "<body><iframe width='100%' height='100%' " +
                "src='https://www.youtube.com/embed/" + videoId + "?rel=0&modestbranding=1' " +
                "frameborder='0' allow='accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture' " +
                "allowfullscreen></iframe></body></html>";
    }

    /**
     * Display lesson list in sidebar
     */
    private void displayLessonList() {
        lessonListContainer.getChildren().clear();
        lessonCountLabel.setText(lessons.size() + " lessons");

        for (int i = 0; i < lessons.size(); i++) {
            Lesson lesson = lessons.get(i);
            int index = i;

            VBox lessonItem = createSidebarLessonItem(lesson, index);
            lessonListContainer.getChildren().add(lessonItem);
        }
    }

    /**
     * Create sidebar lesson item
     */
    private VBox createSidebarLessonItem(Lesson lesson, int index) {
        VBox item = new VBox(8);
        item.getStyleClass().add("sidebar-lesson-item");
        item.setPadding(new Insets(15, 20, 15, 20));

        boolean isCompleted = completedLessonIds.contains(lesson.getLessonId());
        boolean isCurrent = index == currentLessonIndex;

        if (isCurrent) {
            item.getStyleClass().add("current");
        } else if (isCompleted) {
            item.getStyleClass().add("completed");
        }

        // Lesson number
        Label numberLabel = new Label("Lesson " + (index + 1));
        numberLabel.getStyleClass().add("sidebar-lesson-number");

        // Lesson title
        Label titleLabel = new Label(lesson.getTitle());
        titleLabel.getStyleClass().add("sidebar-lesson-title");
        titleLabel.setWrapText(true);

        // Bottom row (duration + status)
        HBox bottomRow = new HBox(10);
        bottomRow.setAlignment(Pos.CENTER_LEFT);

        Label durationLabel = new Label(formatDuration(lesson.getVideoDuration()));
        durationLabel.getStyleClass().add("sidebar-lesson-duration");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        bottomRow.getChildren().addAll(durationLabel, spacer);

        if (isCompleted) {
            Label statusLabel = new Label("✓ Completed");
            statusLabel.getStyleClass().add("sidebar-lesson-status");
            bottomRow.getChildren().add(statusLabel);
        }

        item.getChildren().addAll(numberLabel, titleLabel, bottomRow);

        // Click to navigate
        item.setOnMouseClicked(e -> {
            currentLessonIndex = index;
            loadCurrentLesson();
        });

        return item;
    }

    /**
     * Format duration
     */
    private String formatDuration(int seconds) {
        int minutes = seconds / 60;
        int secs = seconds % 60;
        return String.format("%d:%02d", minutes, secs);
    }

    /**
     * Update navigation buttons
     */
    private void updateNavigationButtons() {
        previousButton.setDisable(currentLessonIndex == 0);
        nextButton.setDisable(currentLessonIndex >= lessons.size() - 1);
    }

    /**
     * Update complete button
     */
    private void updateCompleteButton() {
        boolean isCompleted = completedLessonIds.contains(currentLesson.getLessonId());

        if (isCompleted) {
            completeButton.setText("✓ Completed");
            completeButton.getStyleClass().add("completed");
        } else {
            completeButton.setText("Mark as Complete");
            completeButton.getStyleClass().remove("completed");
        }
    }

    /**
     * Update progress
     */
    private void updateProgress() {
        int completed = completedLessonIds.size();
        int total = lessons.size();
        double progress = total > 0 ? (double) completed / total : 0;

        progressLabel.setText(completed + "/" + total + " completed");
        progressBar.setProgress(progress);
    }

    /**
     * Highlight current lesson in sidebar
     */
    private void highlightCurrentLesson() {
        displayLessonList(); // Refresh to update highlights
    }

    /**
     * Load resources
     */
    private void loadResources() {
        resourcesContainer.getChildren().clear();

        if (currentLesson.getFileIds() == null || currentLesson.getFileIds().isEmpty()) {
            Label noResources = new Label("No resources available for this lesson");
            noResources.setStyle("-fx-text-fill: #9da3a7; -fx-font-size: 14px;");
            resourcesContainer.getChildren().add(noResources);
            return;
        }

        // TODO: Load actual files from database
        for (String fileId : currentLesson.getFileIds()) {
            HBox resourceItem = new HBox(15);
            resourceItem.getStyleClass().add("resource-item");
            resourceItem.setAlignment(Pos.CENTER_LEFT);

            Label nameLabel = new Label("📄 " + fileId + ".pdf");
            nameLabel.getStyleClass().add("resource-name");

            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);

            Label sizeLabel = new Label("2.5 MB");
            sizeLabel.getStyleClass().add("resource-size");

            Button downloadBtn = new Button("Download");
            downloadBtn.setStyle("-fx-background-color: #a435f0; -fx-text-fill: white; -fx-cursor: hand;");

            resourceItem.getChildren().addAll(nameLabel, spacer, sizeLabel, downloadBtn);
            resourcesContainer.getChildren().add(resourceItem);
        }
    }

    /**
     * Handle previous lesson
     */
    @FXML
    private void handlePreviousLesson() {
        if (currentLessonIndex > 0) {
            currentLessonIndex--;
            loadCurrentLesson();
        }
    }

    /**
     * Handle next lesson
     */
    @FXML
    private void handleNextLesson() {
        if (currentLessonIndex < lessons.size() - 1) {
            currentLessonIndex++;
            loadCurrentLesson();
        }
    }

    /**
     * Handle mark complete
     */
    @FXML
    private void handleMarkComplete() {
        if (currentLesson == null || enrollment == null) return;

        boolean isCompleted = completedLessonIds.contains(currentLesson.getLessonId());

        if (isCompleted) {
            // Already completed, do nothing or toggle
            return;
        }

        // Mark as complete
        completedLessonIds.add(currentLesson.getLessonId());

        // TODO: Update enrollment in database
        CompletableFuture.runAsync(() -> {
            // Call service to update enrollment
            log.info("✅ Marked lesson {} as complete", currentLesson.getLessonId());
        }).thenRun(() -> {
            Platform.runLater(() -> {
                updateCompleteButton();
                updateProgress();
                displayLessonList();
                showInfo("Lesson marked as complete! 🎉");

                // Auto advance to next lesson
                if (currentLessonIndex < lessons.size() - 1) {
                    currentLessonIndex++;
                    loadCurrentLesson();
                }
            });
        });
    }

    /**
     * Handle save notes
     */
    @FXML
    private void handleSaveNotes() {
        String notes = notesTextArea.getText();

        if (notes == null || notes.trim().isEmpty()) {
            showWarning("Please write some notes first!");
            return;
        }

        // TODO: Save notes to database
        log.info("💾 Saving notes for lesson: {}", currentLesson.getLessonId());

        showInfo("Notes saved successfully!");
    }

    /**
     * Handle back to course
     */
    @FXML
    private void handleBackToCourse() {
        try {
            // Navigate back to course detail
            javafx.scene.layout.StackPane contentPane =
                    (javafx.scene.layout.StackPane) backButton.getScene().lookup("#contentPane");

            if (contentPane != null) {
                FXMLLoader loader = new FXMLLoader(
                        getClass().getResource("/view/course-detail.fxml")
                );
                Node courseDetailView = loader.load();
                CourseDetailController controller = loader.getController();
                controller.loadCourse(course.getCourseId());

                contentPane.getChildren().clear();
                contentPane.getChildren().add(courseDetailView);

                log.info("✅ Navigated back to course detail");
            }
        } catch (Exception e) {
            log.error("❌ Failed to navigate back", e);
            showError("Failed to navigate back to course!");
        }
    }

    /**
     * Show error dialog
     */
    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Error");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    /**
     * Show info dialog
     */
    private void showInfo(String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Success");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    /**
     * Show warning dialog
     */
    private void showWarning(String message) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle("Warning");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}