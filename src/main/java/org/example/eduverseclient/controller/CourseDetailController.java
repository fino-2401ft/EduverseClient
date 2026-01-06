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

        setupPlaceholders();

        // Set default thumbnail size
        if (thumbnailImageView != null) {
            thumbnailImageView.setFitWidth(340);
            thumbnailImageView.setFitHeight(190);
        }
    }


    public void loadCourse(String courseId) {
        log.info("🔍 Loading course: {}", courseId);

        Platform.runLater(() -> {
            try {
                CompletableFuture.supplyAsync(() -> {
                    return courseService.getCourseById(courseId);
                }).thenAccept(loadedCourse -> {
                    Platform.runLater(() -> {
                        if (loadedCourse != null) {
                            log.info("✅ Course loaded: {}", loadedCourse.getTitle());
                            this.course = loadedCourse;
                            this.isEnrolled = currentUserId != null && course.hasStudent(currentUserId);
                            loadCourseDetails();
                            loadLessons();
                        } else {
                            log.error("❌ Course not found: {}", courseId);
                            showError("Khóa học không tồn tại!");
                        }
                    });
                }).exceptionally(throwable -> {
                    log.error("❌ Failed to load course", throwable);
                    Platform.runLater(() -> showError("Không thể tải thông tin khóa học!"));
                    return null;
                });
            } catch (Exception e) {
                log.error("❌ Exception loading course", e);
                showError("Không thể tải thông tin khóa học!");
            }
        });
    }

    private void loadCourseDetails() {
        if (course == null) return;

        courseTitleLabel.setText(course.getTitle());
        courseSubtitleLabel.setText(course.getDescription());

        ratingLabel.setText("4.7");
        ratingCountLabel.setText("(930 ratings)");

        int studentCount = course.getStudentCount();
        studentCountLabel.setText(studentCount + " students");

        if (course.getTeacherName() != null) {
            instructorLink.setText(course.getTeacherName());
        } else {
            instructorLink.setText("Unknown Teacher");
        }

        SimpleDateFormat sdf = new SimpleDateFormat("MM/yyyy");
        lastUpdatedLabel.setText("Last updated " + sdf.format(new Date(course.getUpdatedAt())));
        languageLabel.setText(DEFAULT_LANGUAGE);

        // FIX: Load thumbnail with better error handling
        loadThumbnail();

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

        setupBreadcrumb();
        setupLearningObjectives();
        setupRequirements();
        setupCourseIncludes();
    }


    // FIX: Separate thumbnail loading method
    private void loadThumbnail() {
        if (course.getThumbnailUrl() != null && !course.getThumbnailUrl().isEmpty()) {
            log.info("📷 Loading thumbnail from: {}", course.getThumbnailUrl());
            try {
                Image image = new Image(course.getThumbnailUrl(), true);

                // Check if image loaded successfully
                image.errorProperty().addListener((obs, oldError, newError) -> {
                    if (newError) {
                        log.warn("❌ Failed to load thumbnail: {}", course.getThumbnailUrl());
                        Platform.runLater(this::setPlaceholderThumbnail);
                    }
                });

                image.progressProperty().addListener((obs, oldProgress, newProgress) -> {
                    if (newProgress.doubleValue() >= 1.0) {
                        log.info("✅ Thumbnail loaded successfully");
                        Platform.runLater(() -> thumbnailImageView.setImage(image));
                    }
                });

                // Set image immediately (will show when loaded)
                thumbnailImageView.setImage(image);

            } catch (Exception e) {
                log.error("❌ Exception loading thumbnail", e);
                setPlaceholderThumbnail();
            }
        } else {
            log.warn("⚠️ No thumbnail URL provided");
            setPlaceholderThumbnail();
        }
    }

    // FIX: Load lessons with detailed logging
    private void loadLessons() {
        if (course == null) {
            log.error("❌ Cannot load lessons: course is null");
            return;
        }

        log.info("📚 Loading lessons for course: {}", course.getCourseId());

        CompletableFuture.supplyAsync(() -> {
            List<Lesson> loadedLessons = courseService.getLessonsByCourse(course.getCourseId());
            log.info("📚 Found {} lessons", loadedLessons != null ? loadedLessons.size() : 0);
            return loadedLessons;
        }).thenAccept(loadedLessons -> {
            Platform.runLater(() -> {
                this.lessons = loadedLessons != null ? loadedLessons : new ArrayList<>();

                if (this.lessons.isEmpty()) {
                    log.warn("⚠️ No lessons found for course: {}", course.getCourseId());
                } else {
                    log.info("✅ Successfully loaded {} lessons", this.lessons.size());
                    for (Lesson lesson : this.lessons) {
                        log.debug("  - Lesson: {} ({}s)", lesson.getTitle(), lesson.getVideoDuration());
                    }
                }

                displayLessons();
                updateContentSummary();
            });
        }).exceptionally(throwable -> {
            log.error("❌ Failed to load lessons", throwable);
            Platform.runLater(() -> {
                this.lessons = new ArrayList<>();
                displayLessons();
                updateContentSummary();
            });
            return null;
        });
    }

    private void displayLessons() {
        lessonsContainer.getChildren().clear();

        if (lessons == null || lessons.isEmpty()) {
            log.warn("⚠️ Displaying empty lessons container");
            Label noLessonsLabel = new Label("No lessons available yet.");
            noLessonsLabel.setStyle("-fx-text-fill: #6a6f73; -fx-font-size: 14px; -fx-padding: 20px;");
            lessonsContainer.getChildren().add(noLessonsLabel);
            return;
        }

        log.info("🎨 Displaying {} lessons", lessons.size());
        String sectionId = "section_0";
        boolean isExpanded = expandedSections.getOrDefault(sectionId, true);

        VBox sectionContainer = createLessonSection("Course Content", lessons, sectionId, isExpanded);
        lessonsContainer.getChildren().add(sectionContainer);

        log.info("✅ Lessons displayed successfully");
    }

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
        expandIcon.getStyleClass().add("lesson-section-icon");

        header.getChildren().addAll(titleLabel, countLabel, spacer, expandIcon);

        // Lesson list
        VBox lessonList = new VBox();
        lessonList.getStyleClass().add("lesson-list");

        if (expanded) {
            for (Lesson lesson : sectionLessons) {
                Node lessonItem = createLessonItem(lesson);
                lessonList.getChildren().add(lessonItem);
            }
            lessonList.setVisible(true);
            lessonList.setManaged(true);
        } else {
            lessonList.setVisible(false);
            lessonList.setManaged(false);
        }

        header.setOnMouseClicked(e -> {
            boolean newExpanded = !expandedSections.getOrDefault(sectionId, true);
            expandedSections.put(sectionId, newExpanded);
            displayLessons();
        });

        section.getChildren().addAll(header, lessonList);
        return section;
    }

    private Node createLessonItem(Lesson lesson) {
        HBox item = new HBox(10);
        item.getStyleClass().add("lesson-item");
        item.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        item.setPadding(new javafx.geometry.Insets(12, 15, 12, 15));

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
            item.setOnMouseClicked(e -> openLesson(lesson));
            item.setStyle(item.getStyle() + "; -fx-cursor: hand;");
        }

        return item;
    }

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

    private void updateContentSummary() {
        if (lessons == null || lessons.isEmpty()) {
            contentSummaryLabel.setText("0 sections • 0 lectures • 0h 0m total length");
            return;
        }

        int totalDuration = lessons.stream().mapToInt(Lesson::getVideoDuration).sum();
        String durationStr = formatTotalDuration(totalDuration);
        contentSummaryLabel.setText("1 section • " + lessons.size() + " lectures • " + durationStr + " total length");
    }

    private String formatTotalDuration(int totalSeconds) {
        int hours = totalSeconds / 3600;
        int minutes = (totalSeconds % 3600) / 60;

        if (hours > 0) {
            return hours + "h " + minutes + "m";
        } else {
            return minutes + "m";
        }
    }

    private void setupBreadcrumb() {
        breadcrumbContainer.getChildren().clear();

        Label itLabel = new Label("IT & Software");
        itLabel.setOnMouseClicked(e -> {});

        Label separator1 = new Label(">");
        separator1.getStyleClass().add("separator");

        Label pythonLabel = new Label("Python");
        pythonLabel.setOnMouseClicked(e -> {});

        breadcrumbContainer.getChildren().addAll(itLabel, separator1, pythonLabel);
    }

    private void setupLearningObjectives() {
        learningObjectivesContainer.getChildren().clear();

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

    private void setupPlaceholders() {
        courseTitleLabel.setText("Loading...");
        setPlaceholderThumbnail();
    }

    private void setPlaceholderThumbnail() {
        thumbnailImageView.setImage(null);
        // Set a light gray background with text
        StackPane placeholder = new StackPane();
        placeholder.setStyle("-fx-background-color: #e8e8e8; -fx-background-radius: 4px;");
        placeholder.setPrefSize(340, 190);

        Label placeholderText = new Label("Course Preview");
        placeholderText.setStyle("-fx-text-fill: #999; -fx-font-size: 14px;");
        placeholder.getChildren().add(placeholderText);
    }

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

    @FXML
    private void handleContinue() {
        if (lessons == null || lessons.isEmpty()) {
            showInfo("Khóa học chưa có bài học nào!");
            return;
        }

        Lesson firstLesson = lessons.get(0);
        openLesson(firstLesson);
    }



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

    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Error");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private void showInfo(String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Information");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private void openLesson(Lesson lesson) {
        if (!isEnrolled && !lesson.isFree()) {
            showError("Vui lòng tham gia khóa học để xem bài học này!");
            return;
        }

        log.info("📖 Opening lesson: {}", lesson.getTitle());

        try {
            // Navigate to lesson learning page
            javafx.scene.layout.StackPane contentPane =
                    (javafx.scene.layout.StackPane) mainContainer.getScene().lookup("#contentPane");

            if (contentPane != null) {
                FXMLLoader loader = new FXMLLoader(
                        getClass().getResource("/view/lesson-learning.fxml")
                );
                Node lessonView = loader.load();
                LessonLearningController controller = loader.getController();
                controller.loadLesson(course.getCourseId(), lesson.getLessonId());

                contentPane.getChildren().clear();
                contentPane.getChildren().add(lessonView);

                log.info("✅ Navigated to lesson learning");
            }
        } catch (Exception e) {
            log.error("❌ Failed to open lesson", e);
            showError("Không thể mở bài học!");
        }
    }
}