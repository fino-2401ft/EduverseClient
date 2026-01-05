package org.example.eduverseclient.controller;

import common.model.Course;
import common.model.User;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import lombok.extern.slf4j.Slf4j;
import org.example.eduverseclient.RMIClient;
import org.example.eduverseclient.controller.CourseDetailController;
import org.example.eduverseclient.service.CourseService;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Slf4j
public class HomeDashboardController {
    
    @FXML private VBox mainContainer;
    @FXML private FlowPane coursesContainer;
    @FXML private HBox teachersContainer;
    @FXML private FlowPane testimonialsContainer;
    @FXML private HBox certificationsContainer;
    @FXML private HBox companiesContainer;
    
    private CourseService courseService;
    private ObservableList<Course> allCourses;
    private ObservableList<User> allTeachers;
    
    @FXML
    public void initialize() {
        try {
            // Initialize service
            courseService = CourseService.getInstance();
            
            // Initialize data
            allCourses = FXCollections.observableArrayList();
            allTeachers = FXCollections.observableArrayList();
            
            // Load data
            loadCourses();
            loadTeachers();
            setupTestimonials();
            setupCertifications();
            setupCompanies();
            
            log.info("✅ Home Dashboard initialized");
            
        } catch (Exception e) {
            log.error("❌ Failed to initialize Home Dashboard", e);
        }
    }
    
    private void loadCourses() {
        CompletableFuture.supplyAsync(() -> {
            try {
                return courseService.getAllCourses();
            } catch (Exception e) {
                log.error("Failed to load courses", e);
                return new ArrayList<Course>();
            }
        }).thenAccept(courses -> {
            Platform.runLater(() -> {
                if (courses != null && !courses.isEmpty()) {
                    allCourses.addAll(courses.stream()
                        .limit(6) // Show top 6 courses
                        .collect(Collectors.toList()));
                    
                    displayCourses();
                }
            });
        });
    }
    
    private void loadTeachers() {
        // TODO: Load teachers from service
        // For now, use mock data
        displayTeachers();
    }
    
    private void displayCourses() {
        coursesContainer.getChildren().clear();
        
        for (Course course : allCourses) {
            try {
                FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/view/course-card.fxml")
                );
                Node card = loader.load();
                CourseCardController controller = loader.getController();
                
                String userId = null;
                if (RMIClient.getInstance().getCurrentUser() != null) {
                    userId = RMIClient.getInstance().getCurrentUser().getUserId();
                }
                controller.setCourse(course, userId);
                
                // Set callback to navigate to course detail
                String courseId = course.getCourseId();
                if (courseId != null) {
                    controller.setOnCourseClick(() -> navigateToCourseDetail(courseId));
                }
                
                coursesContainer.getChildren().add(controller.getRoot());
                
            } catch (Exception e) {
                log.error("Failed to load course card", e);
            }
        }
    }
    
    /**
     * Navigate to course detail page
     */
    private void navigateToCourseDetail(String courseId) {
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
                javafx.stage.Stage stage = new javafx.stage.Stage();
                stage.setTitle("Course Detail");
                stage.setScene(new javafx.scene.Scene((javafx.scene.Parent) courseDetailView, 1200, 800));
                stage.show();
            }
            
        } catch (Exception e) {
            log.error("❌ Failed to navigate to course detail", e);
        }
    }
    
    private void displayTeachers() {
        teachersContainer.getChildren().clear();
        
        // Load teachers from Firebase
        CompletableFuture.supplyAsync(() -> {
            try {
                org.example.eduverseclient.service.UserService userService = 
                    org.example.eduverseclient.service.UserService.getInstance();
                return userService.getTeachers();
            } catch (Exception e) {
                log.error("Failed to load teachers", e);
                return new ArrayList<User>();
            }
        }).thenAccept(teachers -> {
            Platform.runLater(() -> {
                for (User teacher : teachers) {
                    VBox card = createTeacherCard(teacher);
                    teachersContainer.getChildren().add(card);
                }
                
                if (teachers.isEmpty()) {
                    Label emptyLabel = new Label("Chưa có giáo viên nào");
                    emptyLabel.setStyle("-fx-font-size: 14px; -fx-text-fill: #6c757d;");
                    teachersContainer.getChildren().add(emptyLabel);
                }
            });
        });
    }
    
    private VBox createTeacherCard(User teacher) {
        VBox card = new VBox(10);
        card.getStyleClass().add("teacher-card");
        card.setAlignment(javafx.geometry.Pos.CENTER);
        
        // Avatar
        ImageView avatar = new ImageView();
        avatar.setFitWidth(80);
        avatar.setFitHeight(80);
        avatar.getStyleClass().add("teacher-avatar");
        avatar.setPreserveRatio(true);
        
        if (teacher.getAvatarUrl() != null && !teacher.getAvatarUrl().isEmpty()) {
            try {
                Image img = new Image(teacher.getAvatarUrl(), true);
                avatar.setImage(img);
            } catch (Exception e) {
                log.warn("Failed to load teacher avatar: {}", teacher.getAvatarUrl());
            }
        }
        
        // Name
        Label nameLabel = new Label(teacher.getFullName());
        nameLabel.getStyleClass().add("teacher-name");
        
        // Title
        Label titleLabel = new Label("Giảng viên");
        titleLabel.getStyleClass().add("teacher-title");
        
        card.getChildren().addAll(avatar, nameLabel, titleLabel);
        
        return card;
    }
    
    private List<User> createMockTeachers() {
        List<User> teachers = new ArrayList<>();
        // This will be replaced with real data from service
        return teachers;
    }
    
    private void setupTestimonials() {
        testimonialsContainer.getChildren().clear();
        
        // Mock testimonials
        List<Testimonial> testimonials = createMockTestimonials();
        
        for (Testimonial testimonial : testimonials) {
            VBox card = createTestimonialCard(testimonial);
            testimonialsContainer.getChildren().add(card);
        }
    }
    
    private VBox createTestimonialCard(Testimonial testimonial) {
        VBox card = new VBox(15);
        card.getStyleClass().add("testimonial-card");
        
        // Quote
        Label quoteLabel = new Label(testimonial.quote);
        quoteLabel.getStyleClass().add("testimonial-quote");
        quoteLabel.setWrapText(true);
        
        // Source
        VBox sourceBox = new VBox(5);
        sourceBox.getStyleClass().add("testimonial-source");
        
        Label authorLabel = new Label(testimonial.author);
        authorLabel.getStyleClass().add("testimonial-author");
        
        Label roleLabel = new Label(testimonial.role);
        roleLabel.getStyleClass().add("testimonial-role");
        
        sourceBox.getChildren().addAll(authorLabel, roleLabel);
        
        // Link
        Hyperlink link = new Hyperlink(testimonial.linkText);
        link.getStyleClass().add("testimonial-link");
        
        card.getChildren().addAll(quoteLabel, sourceBox, link);
        
        return card;
    }
    
    private List<Testimonial> createMockTestimonials() {
        List<Testimonial> testimonials = new ArrayList<>();
        
        testimonials.add(new Testimonial(
            "Eduverse là nền tảng học tập trực tuyến tuyệt vời, giúp tôi nâng cao kỹ năng và phát triển sự nghiệp.",
            "Nguyễn Văn A",
            "Software Engineer",
            "Xem khóa học Lập trình →"
        ));
        
        testimonials.add(new Testimonial(
            "Chất lượng giảng dạy rất tốt, giáo viên nhiệt tình và nội dung bài học phong phú.",
            "Trần Thị B",
            "Product Manager",
            "Xem khóa học Quản lý dự án →"
        ));
        
        testimonials.add(new Testimonial(
            "Tôi đã học được rất nhiều từ các khóa học trên Eduverse, đặc biệt là các khóa học về công nghệ.",
            "Lê Văn C",
            "Data Analyst",
            "Xem khóa học Phân tích dữ liệu →"
        ));
        
        testimonials.add(new Testimonial(
            "Eduverse giúp tôi có thể học mọi lúc mọi nơi, rất tiện lợi cho người đi làm như tôi.",
            "Phạm Thị D",
            "Marketing Specialist",
            "Xem khóa học Marketing →"
        ));
        
        return testimonials;
    }
    
    private void setupCertifications() {
        certificationsContainer.getChildren().clear();
        
        List<Certification> certifications = createMockCertifications();
        
        for (Certification cert : certifications) {
            VBox card = createCertificationCard(cert);
            certificationsContainer.getChildren().add(card);
        }
    }
    
    private VBox createCertificationCard(Certification cert) {
        VBox card = new VBox(10);
        card.getStyleClass().add("cert-card");
        
        // Name
        Label nameLabel = new Label(cert.name);
        nameLabel.getStyleClass().add("cert-name");
        
        // Tags
        Label tagsLabel = new Label(cert.tags);
        tagsLabel.getStyleClass().add("cert-tags");
        tagsLabel.setWrapText(true);
        
        // Badges
        HBox badgesBox = new HBox(8);
        badgesBox.getStyleClass().add("cert-badges");
        
        for (String badge : cert.badges) {
            Label badgeLabel = new Label(badge);
            badgeLabel.getStyleClass().addAll("cert-badge", "cert-badge-label");
            badgesBox.getChildren().add(badgeLabel);
        }
        
        card.getChildren().addAll(nameLabel, tagsLabel, badgesBox);
        
        return card;
    }
    
    private List<Certification> createMockCertifications() {
        List<Certification> certifications = new ArrayList<>();
        
        certifications.add(new Certification(
            "CompTIA",
            "Đám mây, Mạng, An ninh mạng",
            List.of("Security+", "A+", "Network+", "Linux+")
        ));
        
        certifications.add(new Certification(
            "AWS",
            "Đám mây, AI, Lập trình, Mạng",
            List.of("Solutions Architect", "Developer", "SysOps", "Cloud Practitioner")
        ));
        
        certifications.add(new Certification(
            "PMI",
            "Quản lý dự án và chương trình",
            List.of("PMP", "CAPM", "PgMP", "PMI-ACP")
        ));
        
        return certifications;
    }
    
    private void setupCompanies() {
        companiesContainer.getChildren().clear();
        
        // Mock company logos (text labels for now)
        String[] companies = {"Volkswagen", "Samsung", "Cisco", "Vimeo", "P&G", "HPE", "Citi", "Ericsson"};
        
        for (String company : companies) {
            Label logo = new Label(company);
            logo.getStyleClass().add("company-logo");
            companiesContainer.getChildren().add(logo);
        }
    }
    
    @FXML
    private void viewAllCourses() {
        // Navigate to Course Dashboard
        try {
            Node current = mainContainer;
            javafx.scene.layout.StackPane contentPane = null;
            
            while (current != null && contentPane == null) {
                if (current instanceof javafx.scene.layout.StackPane) {
                    javafx.scene.Parent parent = current.getParent();
                    if (parent != null && parent.getChildrenUnmodifiable().contains(current)) {
                        contentPane = (javafx.scene.layout.StackPane) current;
                        break;
                    }
                }
                current = current.getParent();
            }
            
            if (contentPane == null && mainContainer.getScene() != null) {
                contentPane = (javafx.scene.layout.StackPane) mainContainer.getScene().lookup("#contentPane");
            }
            
            if (contentPane != null) {
                FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/view/course-dashboard.fxml")
                );
                Node courseView = loader.load();
                contentPane.getChildren().clear();
                contentPane.getChildren().add(courseView);
            }
        } catch (Exception e) {
            log.error("Failed to navigate to courses", e);
        }
    }
    
    @FXML
    private void viewAllTeachers() {
        // TODO: Navigate to teachers page
        log.info("View all teachers");
    }
    
    @FXML
    private void viewAllTestimonials() {
        // TODO: Navigate to testimonials page
        log.info("View all testimonials");
    }
    
    @FXML
    private void viewCertifications() {
        // TODO: Navigate to certifications page
        log.info("View certifications");
    }
    
    // Inner classes for data models
    private static class Testimonial {
        String quote;
        String author;
        String role;
        String linkText;
        
        Testimonial(String quote, String author, String role, String linkText) {
            this.quote = quote;
            this.author = author;
            this.role = role;
            this.linkText = linkText;
        }
    }
    
    private static class Certification {
        String name;
        String tags;
        List<String> badges;
        
        Certification(String name, String tags, List<String> badges) {
            this.name = name;
            this.tags = tags;
            this.badges = badges;
        }
    }
}

