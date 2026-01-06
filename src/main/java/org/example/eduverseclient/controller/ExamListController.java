package org.example.eduverseclient.controller;

import common.enums.ExamStatus;
import common.model.Course;
import common.model.Peer;
import common.model.exam.Exam;
import common.model.exam.ExamParticipant;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Modality;
import javafx.stage.Stage;
import lombok.extern.slf4j.Slf4j;
import org.example.eduverseclient.RMIClient;
import org.example.eduverseclient.service.CourseService;
import org.example.eduverseclient.service.ExamService;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Slf4j
public class ExamListController {
    
    @FXML private VBox examListBox;
    @FXML private ComboBox<Course> courseComboBox;
    
    private ExamService examService;
    private CourseService courseService;
    private Course selectedCourse;
    
    @FXML
    public void initialize() {
        examService = ExamService.getInstance();
        courseService = CourseService.getInstance();
        
        // Load courses vào combobox
        loadCourses();
        
        // Load exams khi chọn course
        courseComboBox.setOnAction(e -> {
            selectedCourse = courseComboBox.getSelectionModel().getSelectedItem();
            if (selectedCourse != null) {
                loadExams(selectedCourse.getCourseId());
            }
        });
        
        // Load exams mặc định (nếu có course đầu tiên)
        if (courseComboBox.getItems().size() > 0) {
            courseComboBox.getSelectionModel().select(0);
            selectedCourse = courseComboBox.getItems().get(0);
            loadExams(selectedCourse.getCourseId());
        }
    }
    
    private void loadCourses() {
        new Thread(() -> {
            List<Course> coursesList;
            var currentUser = RMIClient.getInstance().getCurrentUser();
            
            if (currentUser != null) {
                // Lấy courses dựa trên role
                if (currentUser.getRole() == common.enums.UserRole.TEACHER) {
                    coursesList = courseService.getCoursesByTeacher(currentUser.getUserId());
                } else if (currentUser.getRole() == common.enums.UserRole.STUDENT) {
                    coursesList = courseService.getCoursesByStudent(currentUser.getUserId());
                } else {
                    // Admin hoặc role khác: lấy tất cả
                    coursesList = courseService.getAllCourses();
                }
            } else {
                coursesList = courseService.getAllCourses();
            }
            
            // Tạo biến final để dùng trong lambda
            final List<Course> finalCourses = coursesList != null ? coursesList : new ArrayList<>();
            
            Platform.runLater(() -> {
                courseComboBox.getItems().clear();
                courseComboBox.getItems().addAll(finalCourses);
                
                // Set CellFactory để hiển thị tên course
                courseComboBox.setCellFactory(param -> new ListCell<Course>() {
                    @Override
                    protected void updateItem(Course item, boolean empty) {
                        super.updateItem(item, empty);
                        if (empty || item == null) {
                            setText(null);
                        } else {
                            setText(item.getTitle());
                        }
                    }
                });
                
                // Set ButtonCell để hiển thị tên course khi chọn
                courseComboBox.setButtonCell(new ListCell<Course>() {
                    @Override
                    protected void updateItem(Course item, boolean empty) {
                        super.updateItem(item, empty);
                        if (empty || item == null) {
                            setText(null);
                        } else {
                            setText(item.getTitle());
                        }
                    }
                });
            });
        }).start();
    }
    
    private void loadExams(String courseId) {
        new Thread(() -> {
            List<Exam> exams = examService.getExamsByCourse(courseId);
            
            Platform.runLater(() -> {
                examListBox.getChildren().clear();
                
                if (exams.isEmpty()) {
                    Label emptyLabel = new Label("Chưa có bài thi nào trong khóa học này");
                    emptyLabel.setStyle("-fx-font-size: 14; -fx-text-fill: #999;");
                    examListBox.getChildren().add(emptyLabel);
                } else {
                    exams.forEach(this::addExamCard);
                }
            });
        }).start();
    }
    
    private void addExamCard(Exam exam) {
        // Card container
        VBox card = new VBox(10);
        card.setStyle("-fx-background-color: white; -fx-border-color: #E0E0E0; " +
                     "-fx-border-radius: 10; -fx-background-radius: 10; -fx-padding: 15;");
        
        // Title
        Label titleLabel = new Label(exam.getTitle());
        titleLabel.setStyle("-fx-font-size: 18; -fx-font-weight: bold;");
        
        // Description
        if (exam.getDescription() != null && !exam.getDescription().isEmpty()) {
            Label descLabel = new Label(exam.getDescription());
            descLabel.setWrapText(true);
            descLabel.setStyle("-fx-font-size: 12; -fx-text-fill: #666;");
            card.getChildren().add(descLabel);
        }
        
        // Info
        HBox infoBox = new HBox(20);
        
        Label proctorLabel = new Label("👨‍🏫 " + exam.getProctorName());
        proctorLabel.setStyle("-fx-font-size: 13; -fx-text-fill: #666;");
        
        Label timeLabel = new Label("🕐 " + formatTime(exam.getScheduledTime()));
        timeLabel.setStyle("-fx-font-size: 13; -fx-text-fill: #666;");
        
        Label durationLabel = new Label("⏱ " + exam.getDurationMinutes() + " phút");
        durationLabel.setStyle("-fx-font-size: 13; -fx-text-fill: #666;");
        
        Label statusLabel = new Label(getStatusText(exam.getStatus()));
        statusLabel.setStyle("-fx-font-size: 13; -fx-font-weight: bold; " +
                            "-fx-text-fill: " + getStatusColor(exam.getStatus()));
        
        infoBox.getChildren().addAll(proctorLabel, timeLabel, durationLabel, statusLabel);
        
        // Buttons
        HBox buttonBox = new HBox(10);
        buttonBox.setAlignment(Pos.CENTER_RIGHT);
        
        String currentUserId = RMIClient.getInstance().getCurrentUser().getUserId();
        boolean isProctor = exam.getProctorId().equals(currentUserId);
        
        if (isProctor) {
            // Proctor: Start/End exam
            if (exam.getStatus().equals(ExamStatus.PENDING.toString()) || 
                exam.getStatus().equals(ExamStatus.SCHEDULED.toString())) {
                Button startButton = new Button("▶️ Bắt đầu");
                startButton.setStyle("-fx-background-color: #43A047; -fx-text-fill: white;");
                startButton.setOnAction(e -> handleStartExam(exam));
                buttonBox.getChildren().add(startButton);
            } else if (exam.getStatus().equals(ExamStatus.IN_PROGRESS.toString())) {
                Button endButton = new Button("🛑 Kết thúc");
                endButton.setStyle("-fx-background-color: #E53935; -fx-text-fill: white;");
                endButton.setOnAction(e -> handleEndExam(exam));
                buttonBox.getChildren().add(endButton);
                
                Button joinButton = new Button("📝 Vào phòng thi");
                joinButton.setStyle("-fx-background-color: #1976D2; -fx-text-fill: white;");
                joinButton.setOnAction(e -> handleJoinExam(exam));
                buttonBox.getChildren().add(joinButton);
            }
        } else {
            // Student: Join exam
            if (exam.getStatus().equals(ExamStatus.IN_PROGRESS.toString())) {
                Button joinButton = new Button("📝 Tham gia thi");
                joinButton.setStyle("-fx-background-color: #1976D2; -fx-text-fill: white;");
                joinButton.setOnAction(e -> handleJoinExam(exam));
                buttonBox.getChildren().add(joinButton);
            }
        }
        
        Button detailButton = new Button("ℹ️ Chi tiết");
        detailButton.setStyle("-fx-background-color: #757575; -fx-text-fill: white;");
        detailButton.setOnAction(e -> showExamDetail(exam));
        buttonBox.getChildren().add(detailButton);
        
        card.getChildren().addAll(titleLabel, infoBox, buttonBox);
        examListBox.getChildren().add(card);
    }
    
    @FXML
    private void handleCreateExam() {
        if (selectedCourse == null) {
            showError("Vui lòng chọn khóa học trước!");
            return;
        }
        
        try {
            FXMLLoader loader = new FXMLLoader(
                getClass().getResource("/view/create-exam-dialog.fxml")
            );
            
            Stage dialog = new Stage();
            dialog.initModality(Modality.APPLICATION_MODAL);
            dialog.setTitle("Tạo Bài Thi Mới");
            dialog.setScene(new Scene(loader.load(), 600, 500));
            
            CreateExamDialogController controller = loader.getController();
            controller.setCourse(selectedCourse);
            controller.setOnExamCreated(this::onExamCreated);
            
            dialog.showAndWait();
            
        } catch (Exception e) {
            log.error("Failed to open create exam dialog", e);
            showError("Không thể mở dialog tạo bài thi!");
        }
    }
    
    private void onExamCreated(Exam exam) {
        loadExams(selectedCourse.getCourseId()); // Reload list
        showInfo("Bài thi đã được tạo thành công!");
    }
    
    private void handleStartExam(Exam exam) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Xác nhận");
        confirm.setHeaderText("Bắt đầu bài thi: " + exam.getTitle());
        confirm.setContentText("Bạn có chắc muốn bắt đầu bài thi này?");
        
        confirm.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                new Thread(() -> {
                    boolean success = examService.startExam(exam.getExamId());
                    
                    Platform.runLater(() -> {
                        if (success) {
                            showInfo("Bài thi đã bắt đầu!");
                            loadExams(exam.getCourseId());
                            // Auto join
                            handleJoinExam(exam);
                        } else {
                            showError("Không thể bắt đầu bài thi!");
                        }
                    });
                }).start();
            }
        });
    }
    
    private void handleEndExam(Exam exam) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Xác nhận");
        confirm.setHeaderText("Kết thúc bài thi: " + exam.getTitle());
        confirm.setContentText("Kết thúc sẽ đuổi tất cả thí sinh khỏi phòng thi.");
        
        confirm.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                new Thread(() -> {
                    boolean success = examService.endExam(exam.getExamId());
                    
                    Platform.runLater(() -> {
                        if (success) {
                            showInfo("Bài thi đã kết thúc!");
                            loadExams(exam.getCourseId());
                        } else {
                            showError("Không thể kết thúc bài thi!");
                        }
                    });
                }).start();
            }
        });
    }
    
    private void handleJoinExam(Exam exam) {
        new Thread(() -> {
            ExamParticipant participant = examService.joinExam(exam.getExamId());
            
            Platform.runLater(() -> {
                if (participant != null) {
                    openExamRoom(exam, participant);
                } else {
                    showError("Không thể tham gia bài thi!");
                }
            });
        }).start();
    }
    
    private void openExamRoom(Exam exam, ExamParticipant participant) {
        try {
            FXMLLoader loader = new FXMLLoader(
                getClass().getResource("/view/exam-room.fxml")
            );
            
            Stage stage = new Stage();
            stage.setTitle("Bài thi: " + exam.getTitle());
            stage.setScene(new Scene(loader.load(), 1200, 800));
            
            ExamRoomController controller = loader.getController();
            controller.initExam(exam, participant);
            
            stage.show();
            
        } catch (Exception e) {
            log.error("Failed to open exam room", e);
            showError("Không thể mở phòng thi!");
        }
    }
    
    private void showExamDetail(Exam exam) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Chi tiết Bài thi");
        alert.setHeaderText(exam.getTitle());
        
        String content = String.format(
            "Giám thị: %s\n" +
            "Thời gian: %s\n" +
            "Thời lượng: %d phút\n" +
            "Trạng thái: %s\n" +
            "Số thí sinh: %d/%d\n" +
            "Exam ID: %s",
            exam.getProctorName(),
            formatTime(exam.getScheduledTime()),
            exam.getDurationMinutes(),
            getStatusText(exam.getStatus()),
            exam.getParticipants().size(),
            exam.getMaxParticipants(),
            exam.getExamId()
        );
        
        alert.setContentText(content);
        alert.showAndWait();
    }
    
    @FXML
    private void handleRefresh() {
        if (selectedCourse != null) {
            loadExams(selectedCourse.getCourseId());
        }
    }
    
    private String formatTime(long timestamp) {
        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy HH:mm");
        return sdf.format(new Date(timestamp));
    }
    
    private String getStatusText(String status) {
        if (status == null) return "● Không xác định";
        
        switch (status) {
            case "PENDING":
            case "SCHEDULED": return "● Đã lên lịch";
            case "IN_PROGRESS": return "● Đang diễn ra";
            case "COMPLETED": return "● Đã kết thúc";
            case "CANCELLED": return "● Đã hủy";
            default: return "● " + status;
        }
    }
    
    private String getStatusColor(String status) {
        if (status == null) return "#000000";
        
        switch (status) {
            case "PENDING":
            case "SCHEDULED": return "#FFA726";
            case "IN_PROGRESS": return "#43A047";
            case "COMPLETED": return "#757575";
            case "CANCELLED": return "#E53935";
            default: return "#000000";
        }
    }
    
    private void showInfo(String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Thông báo");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
    
    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Lỗi");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}

