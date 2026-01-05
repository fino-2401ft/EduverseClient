package org.example.eduverseclient.controller;

import common.model.Course;
import common.model.Lesson;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import lombok.extern.slf4j.Slf4j;
import org.example.eduverseclient.RMIClient;
import org.example.eduverseclient.service.CourseService;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.CompletableFuture;

@Slf4j
public class CourseDetailController {

    @FXML private BorderPane mainContainer;
    @FXML private HBox breadcrumbContainer;
    @FXML private Label courseTitleLabel;
    @FXML private Label courseSubtitleLabel;
    @FXML private Label ratingLabel;
    @FXML private Label ratingCountLabel;
    @FXML private Label studentCountLabel;
    @FXML private Hyperlink instructorLink;
    @FXML private Label lastUpdatedLabel;
    @FXML private Label languageLabel;
    @FXML private Hyperlink expandAllLink;
    @FXML private Label contentSummaryLabel;
    @FXML private VBox lessonsContainer;
    @FXML private VBox learningObjectivesContainer;
    @FXML private VBox requirementsContainer;
    @FXML private VBox courseIncludesContainer;
    @FXML private ImageView thumbnailImageView;
    @FXML private Label priceLabel;
    @FXML private Button enrollButton;
    @FXML private Button continueButton;

    private CourseService courseService;
    private Course course;
    private List<Lesson> lessons;
    private String currentUserId;
    private boolean isEnrolled;
    private Map<String, Boolean> expandedSections = new HashMap<>();

    private static final String DEFAULT_LANGUAGE = "Vietnamese";

    @FXML
    public void initialize() {
        courseService = CourseService.getInstance();
        currentUserId = RMIClient.getInstance().getCurrentUser() != null
            ? RMIClient.getInstance().getCurrentUser().getUserId()
            : null;

        // Initialize with placeholder data
        setupPlaceholders();
    }

    /**
     * Load course detail page with courseId
     */
    public void loadCourse(String courseId) {
        Platform.runLater(() -> {
            try {
                // Load course data
                CompletableFuture.supplyAsync(() -> {
                    return courseService.getCourseById(courseId);
                }).thenAccept(loadedCourse -> {
                    Platform.runLater(() -> {
                        if (loadedCourse != null) {
                            this.course = loadedCourse;
                            this.isEnrolled = currentUserId != null && course.hasStudent(currentUserId);
                            loadCourseDetails();
                            loadLessons();
                        } else {
                            log.error("Course not found: {}", courseId);
                            showError("Khóa học không tồn tại!");
                        }
                    });
                }).exceptionally(throwable -> {
                    log.error("Failed to load course", throwable);
                    Platform.runLater(() -> showError("Không thể tải thông tin khóa học!"));
                    return null;
                });
            } catch (Exception e) {
                log.error("Failed to load course", e);
                showError("Không thể tải thông tin khóa học!");
            }
        });
    }

    /**
     * Load course details and display
     */
    private void loadCourseDetails() {
        if (course == null) return;

        // Title
        courseTitleLabel.setText(course.getTitle());

        // Subtitle (use description as subtitle)
        courseSubtitleLabel.setText(course.getDescription());

        // Rating (mock data for now)
        ratingLabel.setText("4.7");
        ratingCountLabel.setText("(930 ratings)");

        // Student count
        int studentCount = course.getStudentCount();
        studentCountLabel.setText(studentCount + " students");

        // Instructor
        if (course.getTeacherName() != null) {
            instructorLink.setText(course.getTeacherName());
        } else {
            instructorLink.setText("Unknown Teacher");
        }

        // Last updated
        SimpleDateFormat sdf = new SimpleDateFormat("MM/yyyy");
        lastUpdatedLabel.setText("Last updated " + sdf.format(new Date(course.getUpdatedAt())));

        // Language
        languageLabel.setText(DEFAULT_LANGUAGE);

        // Thumbnail
        if (course.getThumbnailUrl() != null && !course.getThumbnailUrl().isEmpty()) {
            try {
                Image image = new Image(course.getThumbnailUrl(), true);
                thumbnailImageView.setImage(image);
            } catch (Exception e) {
                log.warn("Failed to load thumbnail: {}", course.getThumbnailUrl());
                setPlaceholderThumbnail();
            }
        } else {
            setPlaceholderThumbnail();
        }

        // Enrollment buttons
        if (isEnrolled) {
            enrollButton.setVisible(false);
            enrollButton.setManaged(false);
            continueButton.setVisible(true);
            continueButton.setManaged(true);
        } else {
            enrollButton.setVisible(true);
            enrollButton.setManaged(true);
            continueButton.setVisible(false);
            continueButton.setManaged(false);
        }

        // Breadcrumb
        setupBreadcrumb();

        // Learning objectives (extract from description or use defaults)
        setupLearningObjectives();

        // Requirements (default for now)
        setupRequirements();

        // Course includes
        setupCourseIncludes();
    }

    /**
     * Load lessons for the course
     */
    private void loadLessons() {
        if (course == null) return;

        CompletableFuture.supplyAsync(() -> {
            return courseService.getLessonsByCourse(course.getCourseId());
        }).thenAccept(loadedLessons -> {
            Platform.runLater(() -> {
                this.lessons = loadedLessons != null ? loadedLessons : new ArrayList<>();
                displayLessons();
                updateContentSummary();
            });
        }).exceptionally(throwable -> {
            log.error("Failed to load lessons", throwable);
            Platform.runLater(() -> {
                this.lessons = new ArrayList<>();
                displayLessons();
            });
            return null;
        });
    }

    /**
     * Display lessons grouped by sections (for now, show all as one section)
     */
    private void displayLessons() {
        lessonsContainer.getChildren().clear();

        if (lessons.isEmpty()) {
            Label noLessonsLabel = new Label("No lessons available yet.");
            noLessonsLabel.setStyle("-fx-text-fill: #6a6f73; -fx-font-size: 14px;");
            lessonsContainer.getChildren().add(noLessonsLabel);
            return;
        }

        // Group lessons (for now, all in one section)
        String sectionId = "section_0";
        boolean isExpanded = expandedSections.getOrDefault(sectionId, true);

        VBox sectionContainer = createLessonSection("Course Content", lessons, sectionId, isExpanded);
        lessonsContainer.getChildren().add(sectionContainer);
    }

    /**
     * Create a collapsible lesson section
     */
    private VBox createLessonSection(String sectionTitle, List<Lesson> sectionLessons, String sectionId, boolean expanded) {
        VBox section = new VBox();
        section.getStyleClass().add("lesson-section");

        // Header
        HBox header = new HBox(10);
        header.getStyleClass().add("lesson-section-header");
        header.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

        Label titleLabel = new Label(sectionTitle);
        titleLabel.getStyleClass().add("lesson-section-title");

        Label countLabel = new Label(sectionLessons.size() + " lectures");
        countLabel.getStyleClass().add("lesson-section-count");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label expandIcon = new Label(expanded ? "▼" : "▶");
        expandIcon.getStyleClass().add("lesson-section-count");

        header.getChildren().addAll(titleLabel, countLabel, spacer, expandIcon);

        // Lesson list
        VBox lessonList = new VBox();
        lessonList.getStyleClass().add("lesson-list");

        if (expanded) {
            for (Lesson lesson : sectionLessons) {
                Node lessonItem = createLessonItem(lesson);
                lessonList.getChildren().add(lessonItem);
            }
        }

        // Toggle expand/collapse
        header.setOnMouseClicked(e -> {
            boolean newExpanded = !expandedSections.getOrDefault(sectionId, true);
            expandedSections.put(sectionId, newExpanded);
            displayLessons(); // Refresh
        });

        section.getChildren().addAll(header, lessonList);

        return section;
    }

    /**
     * Create a lesson item
     */
    private Node createLessonItem(Lesson lesson) {
        HBox item = new HBox(10);
        item.getStyleClass().add("lesson-item");
        item.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

        // Check if lesson is accessible (enrolled or free)
        boolean isAccessible = isEnrolled || lesson.isFree();
        if (!isAccessible) {
            item.getStyleClass().add("locked");
        }

        Label titleLabel = new Label(lesson.getTitle());
        titleLabel.getStyleClass().add("lesson-title");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox rightBox = new HBox(5);
        rightBox.setAlignment(javafx.geometry.Pos.CENTER_RIGHT);

        if (lesson.isFree()) {
            Label previewBadge = new Label("Preview");
            previewBadge.getStyleClass().add("lesson-preview-badge");
            rightBox.getChildren().add(previewBadge);
        }

        String duration = formatDuration(lesson.getVideoDuration());
        Label durationLabel = new Label(duration);
        durationLabel.getStyleClass().add("lesson-duration");
        rightBox.getChildren().add(durationLabel);

        item.getChildren().addAll(titleLabel, spacer, rightBox);

        if (isAccessible) {
            item.setOnMouseClicked(e -> {
                openLesson(lesson);
            });
        }

        return item;
    }

    /**
     * Format duration in seconds to MM:SS or HH:MM:SS
     */
    private String formatDuration(int seconds) {
        int hours = seconds / 3600;
        int minutes = (seconds % 3600) / 60;
        int secs = seconds % 60;

        if (hours > 0) {
            return String.format("%d:%02d:%02d", hours, minutes, secs);
        } else {
            return String.format("%d:%02d", minutes, secs);
        }
    }

    /**
     * Update content summary
     */
    private void updateContentSummary() {
        if (lessons == null || lessons.isEmpty()) {
            contentSummaryLabel.setText("0 sections • 0 lectures • 0h 0m total length");
            return;
        }

        int totalDuration = lessons.stream().mapToInt(Lesson::getVideoDuration).sum();
        String durationStr = formatTotalDuration(totalDuration);
        contentSummaryLabel.setText("1 section • " + lessons.size() + " lectures • " + durationStr + " total length");
    }

    /**
     * Format total duration
     */
    private String formatTotalDuration(int totalSeconds) {
        int hours = totalSeconds / 3600;
        int minutes = (totalSeconds % 3600) / 60;

        if (hours > 0) {
            return hours + "h " + minutes + "m";
        } else {
            return minutes + "m";
        }
    }

    /**
     * Setup breadcrumb
     */
    private void setupBreadcrumb() {
        breadcrumbContainer.getChildren().clear();

        Label itLabel = new Label("IT & Software");
        itLabel.setOnMouseClicked(e -> {}); // TODO: Navigate

        Label separator1 = new Label(">");
        separator1.getStyleClass().add("separator");

        Label pythonLabel = new Label("Python");
        pythonLabel.setOnMouseClicked(e -> {}); // TODO: Navigate

        breadcrumbContainer.getChildren().addAll(itLabel, separator1, pythonLabel);
    }

    /**
     * Setup learning objectives
     */
    private void setupLearningObjectives() {
        learningObjectivesContainer.getChildren().clear();

        // Default learning objectives (can be extracted from course description or stored in DB)
        List<String> objectives = Arrays.asList(
            "Master Python programming from basics to advanced",
            "Build real-world applications and projects",
            "Understand object-oriented programming concepts",
            "Work with databases and data manipulation",
            "Create web applications using Python frameworks"
        );

        for (String objective : objectives) {
            HBox item = new HBox(8);
            item.getStyleClass().add("learning-objective-item");

            Label checkmark = new Label("✓");
            checkmark.setStyle("-fx-text-fill: #1c1d1f; -fx-font-weight: bold;");

            Label text = new Label(objective);

            item.getChildren().addAll(checkmark, text);
            learningObjectivesContainer.getChildren().add(item);
        }
    }

    /**
     * Setup requirements
     */
    private void setupRequirements() {
        requirementsContainer.getChildren().clear();

        List<String> requirements = Arrays.asList(
            "No programming experience needed",
            "Computer with internet connection",
            "Willingness to learn and practice"
        );

        for (String req : requirements) {
            HBox item = new HBox(8);
            item.getStyleClass().add("requirement-item");

            Label bullet = new Label("•");
            bullet.setStyle("-fx-text-fill: #1c1d1f;");

            Label text = new Label(req);

            item.getChildren().addAll(bullet, text);
            requirementsContainer.getChildren().add(item);
        }
    }

    /**
     * Setup course includes
     */
    private void setupCourseIncludes() {
        courseIncludesContainer.getChildren().clear();

        int totalDuration = lessons != null ? lessons.stream().mapToInt(Lesson::getVideoDuration).sum() : 0;
        String videoHours = formatTotalDuration(totalDuration);

        List<String> includes = Arrays.asList(
            videoHours + " on-demand video",
            "Multiple downloadable resources",
            "Access on mobile and TV",
            "Certificate of completion"
        );

        for (String include : includes) {
            HBox item = new HBox(8);
            item.getStyleClass().add("course-include-item");

            Label icon = new Label("✓");
            icon.setStyle("-fx-text-fill: #1c1d1f;");

            Label text = new Label(include);

            item.getChildren().addAll(icon, text);
            courseIncludesContainer.getChildren().add(item);
        }
    }

    /**
     * Setup placeholders
     */
    private void setupPlaceholders() {
        courseTitleLabel.setText("Loading...");
        setPlaceholderThumbnail();
    }

    /**
     * Set placeholder thumbnail
     */
    private void setPlaceholderThumbnail() {
        thumbnailImageView.setImage(null);
        thumbnailImageView.setStyle("-fx-background-color: #d1d7dc;");
    }

    /**
     * Handle enroll button click
     */
    @FXML
    private void handleEnroll() {
        if (course == null || currentUserId == null) {
            showError("Vui lòng đăng nhập để tham gia khóa học!");
            return;
        }

        Alert confirmDialog = new Alert(Alert.AlertType.CONFIRMATION);
        confirmDialog.setTitle("Enroll in Course");
        confirmDialog.setHeaderText("Enroll in " + course.getTitle() + "?");
        confirmDialog.setContentText("Do you want to enroll in this course?");

        confirmDialog.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                CompletableFuture.supplyAsync(() -> {
                    return courseService.joinCourse(course.getCourseId(), currentUserId);
                }).thenAccept(success -> {
                    Platform.runLater(() -> {
                        if (success) {
                            isEnrolled = true;
                            enrollButton.setVisible(false);
                            enrollButton.setManaged(false);
                            continueButton.setVisible(true);
                            continueButton.setManaged(true);

                            // Refresh lesson display to show all lessons
                            displayLessons();

                            showInfo("Đã tham gia khóa học thành công!");
                        } else {
                            showError("Không thể tham gia khóa học. Vui lòng thử lại!");
                        }
                    });
                });
            }
        });
    }

    /**
     * Handle continue button click
     */
    @FXML
    private void handleContinue() {
        if (lessons == null || lessons.isEmpty()) {
            showInfo("Khóa học chưa có bài học nào!");
            return;
        }

        // Open first lesson or last viewed lesson
        Lesson firstLesson = lessons.get(0);
        openLesson(firstLesson);
    }

    /**
     * Open lesson learning page
     */
    private void openLesson(Lesson lesson) {
        if (!isEnrolled && !lesson.isFree()) {
            showError("Vui lòng tham gia khóa học để xem bài học này!");
            return;
        }

        log.info("Opening lesson: {}", lesson.getTitle());
        // TODO: Navigate to lesson learning page
        showInfo("Tính năng học bài sẽ được triển khai sớm!");
    }

    /**
     * Handle expand all link
     */
    @FXML
    private void handleExpandAll() {
        boolean allExpanded = expandedSections.values().stream().allMatch(b -> b);
        boolean newState = !allExpanded;

        for (String key : expandedSections.keySet()) {
            expandedSections.put(key, newState);
        }

        expandAllLink.setText(newState ? "Collapse all sections" : "Expand all sections");
        displayLessons();
    }

    /**
     * Show error message
     */
    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Error");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    /**
     * Show info message
     */
    private void showInfo(String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Information");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}

