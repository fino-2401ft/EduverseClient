package org.example.eduverseclient.controller;

import common.model.Course;
import common.model.CourseEnrollment;
import common.model.Lesson;
import common.model.exam.ExamResult;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import lombok.extern.slf4j.Slf4j;
import org.example.eduverseclient.RMIClient;
import org.example.eduverseclient.service.CourseService;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Slf4j
public class CourseDashboardController {

    @FXML private VBox mainContainer;
    @FXML private TextField searchField;
    @FXML private HBox categoriesContainer;
    @FXML private HBox myCoursesContainer;
    @FXML private FlowPane popularCoursesContainer;
    @FXML private VBox recentLessonsContainer;
    @FXML private VBox recentExamsContainer;
    @FXML private Label heroTitle;
    @FXML private Label heroSubtitle;
    @FXML private Label myCoursesCount;
    @FXML private Label popularCoursesCount;
    @FXML private Label recentLessonsCount;
    @FXML private Label recentExamsCount;
    @FXML private VBox heroSection;

    private ObservableList<Course> allCourses;
    private FilteredList<Course> filteredCourses;
    private String currentUserId;
    private CourseService courseService;
    private List<String> categories = Arrays.asList(
        "All", "Development", "Business", "IT & Software", 
        "Design", "Marketing", "Photography", "Music"
    );

    @FXML
    public void initialize() {
        try {
            // Initialize service
            courseService = CourseService.getInstance();
            
            // Get current user
            if (RMIClient.getInstance().getCurrentUser() != null) {
                currentUserId = RMIClient.getInstance().getCurrentUser().getUserId();
                String userName = RMIClient.getInstance().getCurrentUser().getFullName();
                heroTitle.setText("Welcome back, " + userName);
            }

            // Initialize data
            allCourses = FXCollections.observableArrayList();
            filteredCourses = new FilteredList<>(allCourses, course -> true);
            
            // Load courses from Firebase
            loadCoursesFromFirebase();
            
            // Load My Courses with progress
            loadMyCourses();
            
            // Load Recent Lessons
            loadRecentLessons();
            
            // Load Recent Exam Results
            loadRecentExamResults();
            
            // Setup search
            setupSearch();
            
            // Setup categories
            setupCategories();
            
            log.info("✅ Course Dashboard initialized");
            
        } catch (Exception e) {
            log.error("❌ Failed to initialize Course Dashboard", e);
            // Fallback to mock data if Firebase fails
            loadMockCourses();
            displayCourses();
        }
    }

    /**
     * Load courses from Firebase via RMI
     */
    private void loadCoursesFromFirebase() {
        Platform.runLater(() -> {
            try {
                // Show loading indicator
                heroSubtitle.setText("Loading courses...");
                
                CompletableFuture.supplyAsync(() -> {
                    try {
                        return courseService.getAllCourses();
                    } catch (Exception e) {
                        log.error("Failed to load courses from Firebase", e);
                        return new ArrayList<Course>();
                    }
                }).thenAccept(courses -> {
                    Platform.runLater(() -> {
                        if (courses != null && !courses.isEmpty()) {
                            allCourses.addAll(courses);
                            log.info("📚 Loaded {} courses from Firebase", courses.size());
                            heroSubtitle.setText("Discover new courses and expand your skills");
                        } else {
                            log.warn("⚠️ No courses found in Firebase, using mock data");
                            loadMockCourses();
                        }
                        displayCourses();
                    });
                });
                
            } catch (Exception e) {
                log.error("❌ Failed to load courses from Firebase", e);
                loadMockCourses();
                displayCourses();
            }
        });
    }
    
    /**
     * Generate mock courses for testing (fallback)
     */
    private void loadMockCourses() {
        List<Course> mockCourses = new ArrayList<>();
        
        // Mock courses with different scenarios
        for (int i = 1; i <= 15; i++) {
            Course course = Course.builder()
                .courseId("course_" + i)
                .title("Complete Java Programming Masterclass " + i)
                .description("Learn Java from scratch to advanced level")
                .teacherId("teacher_" + (i % 3 + 1))
                .teacherName("Dr. " + (char)('A' + (i % 3)) + " Smith")
                .thumbnailUrl(i % 2 == 0 ? "https://via.placeholder.com/300x180?text=Course+" + i : null)
                .active(true)
                .build();
            
            // Some courses have current user enrolled
            if (i <= 5 && currentUserId != null) {
                course.addStudent(currentUserId);
            }
            
            // Add some random students
            for (int j = 0; j < (i * 10); j++) {
                course.addStudent("student_" + j);
            }
            
            mockCourses.add(course);
        }
        
        allCourses.addAll(mockCourses);
        log.info("📚 Loaded {} mock courses", mockCourses.size());
    }

    /**
     * Setup search functionality
     */
    private void setupSearch() {
        searchField.textProperty().addListener((observable, oldValue, newValue) -> {
            filteredCourses.setPredicate(course -> {
                if (newValue == null || newValue.isEmpty()) {
                    return true;
                }
                String lowerCaseFilter = newValue.toLowerCase();
                return course.getTitle().toLowerCase().contains(lowerCaseFilter) ||
                       (course.getTeacherName() != null && 
                        course.getTeacherName().toLowerCase().contains(lowerCaseFilter));
            });
            displayCourses();
        });
    }

    /**
     * Setup category buttons
     */
    private void setupCategories() {
        categoriesContainer.getChildren().clear();
        
        for (String category : categories) {
            Button categoryBtn = new Button(category);
            categoryBtn.getStyleClass().add("category-chip");
            
            if ("All".equals(category)) {
                categoryBtn.getStyleClass().add("category-chip-active");
            }
            
            categoryBtn.setOnAction(e -> handleCategoryClick(category, categoryBtn));
            categoriesContainer.getChildren().add(categoryBtn);
        }
    }

    /**
     * Handle category button click
     */
    private void handleCategoryClick(String category, Button clickedButton) {
        // Remove active class from all buttons
        categoriesContainer.getChildren().forEach(node -> {
            if (node instanceof Button) {
                ((Button) node).getStyleClass().remove("category-chip-active");
            }
        });
        
        // Add active class to clicked button
        clickedButton.getStyleClass().add("category-chip-active");
        
        // Filter courses by category (for now, just refresh)
        // TODO: Implement actual category filtering
        displayCourses();
    }

    /**
     * Display courses in sections
     */
    private void displayCourses() {
        Platform.runLater(() -> {
            // Separate my courses and popular courses
            List<Course> myCourses = filteredCourses.stream()
                .filter(course -> currentUserId != null && course.hasStudent(currentUserId))
                .collect(Collectors.toList());
            
            List<Course> popularCourses = filteredCourses.stream()
                .filter(course -> currentUserId == null || !course.hasStudent(currentUserId))
                .sorted((c1, c2) -> Integer.compare(c2.getStudentCount(), c1.getStudentCount()))
                .limit(12)
                .collect(Collectors.toList());
            
            // Update counts
            myCoursesCount.setText("(" + myCourses.size() + ")");
            popularCoursesCount.setText("(" + popularCourses.size() + ")");
            
            // Display my courses
            displayMyCourses(myCourses);
            
            // Display popular courses
            displayPopularCourses(popularCourses);
        });
    }

    /**
     * Load My Courses with progress from service
     */
    private void loadMyCourses() {
        if (currentUserId == null) {
            myCoursesCount.setText("(0)");
            return;
        }
        
        CompletableFuture.supplyAsync(() -> {
            try {
                return courseService.getCoursesByStudent(currentUserId);
            } catch (Exception e) {
                log.error("Failed to load my courses", e);
                return new ArrayList<Course>();
            }
        }).thenAccept(courses -> {
            Platform.runLater(() -> {
                displayMyCoursesWithProgress(courses);
            });
        });
    }
    
    /**
     * Display my courses with progress in horizontal scroll
     */
    private void displayMyCoursesWithProgress(List<Course> courses) {
        myCoursesContainer.getChildren().clear();
        myCoursesCount.setText("(" + courses.size() + ")");
        
        // Load enrollments to get real progress data
        CompletableFuture.supplyAsync(() -> {
            try {
                return courseService.getEnrollmentsByStudent(currentUserId);
            } catch (Exception e) {
                log.error("Failed to load enrollments", e);
                return new ArrayList<CourseEnrollment>();
            }
        }).thenAccept(enrollments -> {
            Platform.runLater(() -> {
                for (Course course : courses) {
                    try {
                        FXMLLoader loader = new FXMLLoader(
                            getClass().getResource("/view/my-course-card.fxml")
                        );
                        loader.load();
                        MyCourseCardController controller = loader.getController();
                        
                        // Find enrollment for this course
                        CourseEnrollment enrollment = enrollments.stream()
                            .filter(e -> e.getCourseId() != null && e.getCourseId().equals(course.getCourseId()))
                            .findFirst()
                            .orElse(null);
                        
                        // Get total lessons for this course
                        List<Lesson> lessons = courseService.getLessonsByCourse(course.getCourseId());
                        int totalLessons = lessons != null ? lessons.size() : 0;
                        
                        int progress = 0;
                        int completedLessons = 0;
                        
                        if (enrollment != null) {
                            progress = enrollment.getProgress();
                            completedLessons = enrollment.getCompletedLessonIds() != null 
                                ? enrollment.getCompletedLessonIds().size() 
                                : 0;
                            // Use totalLessons from enrollment if available, otherwise from lessons list
                            if (enrollment.getTotalLessons() > 0) {
                                totalLessons = enrollment.getTotalLessons();
                            }
                        }
                        
                        controller.setCourse(course, progress, completedLessons, totalLessons);
                        
                        String courseId = course.getCourseId();
                        controller.setOnContinue(() -> navigateToCourseDetail(courseId));
                        
                        myCoursesContainer.getChildren().add(controller.getRoot());
                    } catch (Exception e) {
                        log.error("Failed to load my course card", e);
                    }
                }
            });
        });
    }
    
    /**
     * Display my courses in horizontal scroll (legacy method for compatibility)
     */
    private void displayMyCourses(List<Course> courses) {
        displayMyCoursesWithProgress(courses);
    }

    /**
     * Display popular courses in flow pane
     */
    private void displayPopularCourses(List<Course> courses) {
        popularCoursesContainer.getChildren().clear();
        
        for (Course course : courses) {
            try {
                CourseCardController card = loadCourseCard(course);
                popularCoursesContainer.getChildren().add(card.getRoot());
            } catch (IOException e) {
                log.error("Failed to load course card for: {}", course.getTitle(), e);
            }
        }
    }

    /**
     * Load a course card component
     */
    private CourseCardController loadCourseCard(Course course) throws IOException {
        FXMLLoader loader = new FXMLLoader(
            getClass().getResource("/view/course-card.fxml")
        );
        loader.load();
        CourseCardController controller = loader.getController();
        controller.setCourse(course, currentUserId);
        
        // Capture courseId to avoid null reference
        String courseId = course != null ? course.getCourseId() : null;
        if (courseId != null) {
            controller.setOnCourseClick(() -> navigateToCourseDetail(courseId));
        }
        
        return controller;
    }
    
    /**
     * Navigate to course detail page
     */
    public void navigateToCourseDetail(String courseId) {
        if (courseId == null || courseId.isEmpty()) {
            log.error("❌ Cannot navigate: courseId is null or empty");
            return;
        }
        
        try {
            // Find the contentPane from scene
            javafx.scene.layout.StackPane contentPane = null;
            if (mainContainer.getScene() != null) {
                contentPane = (javafx.scene.layout.StackPane) mainContainer.getScene().lookup("#contentPane");
            }
            
            // Load course detail
            FXMLLoader loader = new FXMLLoader(
                getClass().getResource("/view/course-detail.fxml")
            );
            Node courseDetailView = loader.load();
            CourseDetailController controller = loader.getController();
            controller.loadCourse(courseId);
            
            if (contentPane != null) {
                contentPane.getChildren().clear();
                contentPane.getChildren().add(courseDetailView);
                log.info("✅ Navigated to course detail: {}", courseId);
            } else {
                // Fallback: Show in new window
                Stage stage = new Stage();
                stage.setTitle("Course Detail");
                stage.setScene(new javafx.scene.Scene((Parent) courseDetailView, 1200, 800));
                stage.show();
                log.info("✅ Opened course detail in new window: {}", courseId);
            }
            
        } catch (Exception e) {
            log.error("❌ Failed to navigate to course detail", e);
        }
    }

    /**
     * Load Recent Lessons
     */
    private void loadRecentLessons() {
        if (currentUserId == null) {
            recentLessonsCount.setText("(0)");
            return;
        }
        
        // Mock data for now - will be replaced with real data from enrollments
        CompletableFuture.supplyAsync(() -> {
            try {
                // Get my courses
                List<Course> myCourses = courseService.getCoursesByStudent(currentUserId);
                List<Lesson> recentLessons = new ArrayList<>();
                
                // Get lessons from first 3 courses
                for (int i = 0; i < Math.min(3, myCourses.size()); i++) {
                    Course course = myCourses.get(i);
                    List<Lesson> lessons = courseService.getLessonsByCourse(course.getCourseId());
                    if (lessons != null && !lessons.isEmpty()) {
                        // Get first 2 lessons from each course
                        for (int j = 0; j < Math.min(2, lessons.size()); j++) {
                            recentLessons.add(lessons.get(j));
                        }
                    }
                }
                
                return recentLessons;
            } catch (Exception e) {
                log.error("Failed to load recent lessons", e);
                return new ArrayList<Lesson>();
            }
        }).thenAccept(lessons -> {
            Platform.runLater(() -> {
                displayRecentLessons(lessons);
            });
        });
    }
    
    /**
     * Display Recent Lessons
     */
    private void displayRecentLessons(List<Lesson> lessons) {
        recentLessonsContainer.getChildren().clear();
        recentLessonsCount.setText("(" + lessons.size() + ")");
        
        for (Lesson lesson : lessons) {
            try {
                // Get course for this lesson - try from allCourses first, then from service
                Course course = allCourses.stream()
                    .filter(c -> c.getCourseId() != null && c.getCourseId().equals(lesson.getCourseId()))
                    .findFirst()
                    .orElse(null);
                
                // If not found in allCourses, load from service
                if (course == null && lesson.getCourseId() != null) {
                    try {
                        course = courseService.getCourseById(lesson.getCourseId());
                    } catch (Exception e) {
                        log.warn("Failed to load course for lesson: {}", lesson.getCourseId());
                    }
                }
                
                if (course == null) continue;
                
                FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/view/recent-lesson-card.fxml")
                );
                loader.load();
                RecentLessonCardController controller = loader.getController();
                
                // Get enrollment to calculate real progress for this lesson
                CourseEnrollment enrollment = null;
                try {
                    enrollment = courseService.getEnrollmentByCourseAndStudent(lesson.getCourseId(), currentUserId);
                } catch (Exception e) {
                    log.warn("Failed to load enrollment for lesson progress", e);
                }
                
                // Calculate progress: 0.0 if not started, 0.5 if in progress, 1.0 if completed
                double progress = 0.0;
                if (enrollment != null && enrollment.getCompletedLessonIds() != null) {
                    if (enrollment.getCompletedLessonIds().contains(lesson.getLessonId())) {
                        progress = 1.0; // Completed
                    } else {
                        // Check if this is the current lesson (first incomplete lesson)
                        List<Lesson> allLessons = courseService.getLessonsByCourse(lesson.getCourseId());
                        if (allLessons != null) {
                            boolean isCurrentLesson = false;
                            for (Lesson l : allLessons) {
                                if (enrollment.getCompletedLessonIds().contains(l.getLessonId())) {
                                    continue;
                                }
                                if (l.getLessonId().equals(lesson.getLessonId())) {
                                    isCurrentLesson = true;
                                    break;
                                }
                                break; // First incomplete lesson
                            }
                            if (isCurrentLesson) {
                                progress = 0.3; // In progress (partially watched)
                            }
                        }
                    }
                }
                
                controller.setLesson(course, lesson, progress);
                controller.setOnResume(() -> {
                    log.info("Resume lesson: {}", lesson.getTitle());
                    // TODO: Navigate to lesson learning page
                });
                
                recentLessonsContainer.getChildren().add(controller.getRoot());
            } catch (Exception e) {
                log.error("Failed to load recent lesson card", e);
            }
        }
    }
    
    /**
     * Load Recent Exam Results
     */
    private void loadRecentExamResults() {
        if (currentUserId == null) {
            recentExamsCount.setText("(0)");
            return;
        }
        
        // Load real exam results from Firebase
        CompletableFuture.supplyAsync(() -> {
            try {
                List<ExamResult> examResults = courseService.getExamResultsByStudent(currentUserId);
                // Limit to 5 most recent
                if (examResults.size() > 5) {
                    examResults = examResults.subList(0, 5);
                }
                return examResults;
            } catch (Exception e) {
                log.error("Failed to load recent exam results", e);
                return new ArrayList<ExamResult>();
            }
        }).thenAccept(examResults -> {
            Platform.runLater(() -> {
                displayRecentExamResults(examResults);
            });
        });
    }
    
    /**
     * Display Recent Exam Results
     */
    private void displayRecentExamResults(List<ExamResult> examResults) {
        recentExamsContainer.getChildren().clear();
        recentExamsCount.setText("(" + examResults.size() + ")");
        
        for (ExamResult examResult : examResults) {
            try {
                FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/view/recent-exam-card.fxml")
                );
                loader.load();
                RecentExamCardController controller = loader.getController();
                
                controller.setExamResult(examResult);
                controller.setOnView(() -> {
                    log.info("View exam result: {}", examResult.getResultId());
                    // TODO: Show exam result details dialog
                    Alert alert = new Alert(Alert.AlertType.INFORMATION);
                    alert.setTitle("Exam Result");
                    alert.setHeaderText("Exam: " + examResult.getExamId());
                    alert.setContentText(
                        "Score: " + examResult.getTotalScore() + " / " + examResult.getMaxScore() + "\n" +
                        "Percentage: " + String.format("%.1f", examResult.getPercentage()) + "%\n" +
                        "Status: " + examResult.getStatus() + "\n" +
                        "Passed: " + (examResult.isPassed() ? "Yes" : "No")
                    );
                    alert.showAndWait();
                });
                
                recentExamsContainer.getChildren().add(controller.getRoot());
            } catch (Exception e) {
                log.error("Failed to load recent exam card", e);
            }
        }
    }

    /**
     * Handle filter button click
     */
    @FXML
    private void handleFilter() {
        // TODO: Implement filter dialog
        log.info("Filter button clicked");
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Filter");
        alert.setHeaderText("Filter Options");
        alert.setContentText("Filter functionality will be implemented soon.");
        alert.showAndWait();
    }
}

