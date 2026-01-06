package org.example.eduverseclient.controller;

import common.model.Peer;
import common.model.exam.Exam;
import common.model.exam.ExamParticipant;
import common.model.exam.Question;
import common.model.exam.StudentAnswer;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.example.eduverseclient.RMIClient;
import org.example.eduverseclient.component.VideoPanel;
import org.example.eduverseclient.network.media.ExamStreamManager;
import org.example.eduverseclient.service.ExamService;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Slf4j
public class ExamRoomController {

    @FXML private Label examTitleLabel;
    @FXML private Label timerLabel;
    @FXML private Label cameraStatusLabel;
    @FXML private GridPane videoGrid;           // Grid view (giống meeting)
    @FXML private ScrollPane questionsScrollPane;
    @FXML private VBox questionsContainer;
    @FXML private VBox alertContainer;  // Alert container cho student
    @FXML private Button submitButton;
    @FXML private Button leaveButton;
    @FXML private Button addQuestionButton;     // Chỉ proctor mới có

    @Getter private Exam exam;
    @Getter private ExamParticipant myParticipant;
    private boolean isProctor;

    private ExamService examService;
    private ExamStreamManager examStreamManager;
    
    // Video panels (giống meeting)
    private Map<String, VideoPanel> videoPanels = new ConcurrentHashMap<>();

    // Exam state
    private List<Question> questions;
    private Map<String, StudentAnswer> studentAnswers;  // questionId -> StudentAnswer
    private long examStartTime;
    private long examDurationMs;
    private ScheduledExecutorService updateExecutor;
    private ScheduledExecutorService timerExecutor;

    private Map<String, String> participantNames = new ConcurrentHashMap<>();

    @FXML
    public void initialize() {
        examService = ExamService.getInstance();
        studentAnswers = new ConcurrentHashMap<>();
    }

    /**
     * Khởi tạo exam room
     */
    public void initExam(Exam exam, ExamParticipant participant) {
        this.exam = exam;
        this.myParticipant = participant;
        this.isProctor = exam.getProctorId().equals(participant.getUserId());
        this.examStartTime = System.currentTimeMillis();
        this.examDurationMs = exam.getDurationMinutes() * 60 * 1000L;

        // 1. Cập nhật UI cơ bản
        examTitleLabel.setText(exam.getTitle());
        cameraStatusLabel.setText("📹 Camera: BẮT BUỘC BẬT");
        cameraStatusLabel.setStyle("-fx-text-fill: #4CAF50; -fx-font-size: 13;");

        // 2. Setup UI theo role
        setupVideoGrid();
        
        if (isProctor) {
            submitButton.setVisible(false);
            addQuestionButton.setVisible(true);
            addQuestionButton.setText("➕ Thêm câu hỏi");
            leaveButton.setText("🛑 Kết thúc bài thi");
            
            // Proctor: Ẩn alert container
            if (alertContainer != null) {
                alertContainer.setManaged(false);
                alertContainer.setVisible(false);
            }
            
            // Proctor: Load questions để xem/quản lý
            loadQuestions();
        } else {
            submitButton.setVisible(true);
            submitButton.setText("📤 Nộp bài");
            addQuestionButton.setVisible(false);
            leaveButton.setText("📞 Rời phòng thi");
            
            // Student: Hiển thị alert container
            if (alertContainer != null) {
                alertContainer.setManaged(true);
                alertContainer.setVisible(true);
            }
            
            // Student: Load questions để làm bài
            loadQuestions();
        }

        // 4. Bắt đầu timer
        startTimer();

        // 5. Khởi động media streaming
        initMediaStreaming();

        // 6. Auto-update participants (nếu proctor)
        if (isProctor) {
            startAutoUpdate();
        }

        log.info("✅ Exam room initialized - Role: {}", isProctor ? "PROCTOR" : "STUDENT");
    }

    private void initMediaStreaming() {
        try {
            Peer proctorPeer = examService.getProctorPeer(exam.getExamId());
            
            // Nếu student và proctor chưa join, retry sau 2 giây
            if (proctorPeer == null && !isProctor) {
                log.warn("⚠️ Proctor peer not found, will retry...");
                // Retry sau 2 giây
                new Thread(() -> {
                    try {
                        Thread.sleep(2000);
                        Peer retryPeer = examService.getProctorPeer(exam.getExamId());
                        if (retryPeer != null) {
                            Platform.runLater(() -> {
                                log.info("✅ Proctor peer found after retry");
                                startStreamingWithPeer(retryPeer);
                            });
                        } else {
                            log.warn("⚠️ Proctor peer still not found after retry");
                            // Vẫn start streaming, sẽ tự động update khi nhận video
                            Platform.runLater(() -> startStreamingWithPeer(null));
                        }
                    } catch (Exception e) {
                        log.error("Retry failed", e);
                        Platform.runLater(() -> startStreamingWithPeer(null));
                    }
                }).start();
                return;
            }

            startStreamingWithPeer(proctorPeer);

        } catch (Exception e) {
            log.error("❌ Failed to init media streaming", e);
        }
    }
    
    private void startStreamingWithPeer(Peer proctorPeer) {
        try {
            examStreamManager = new ExamStreamManager(myParticipant, isProctor);

            examStreamManager.start(
                    proctorPeer,
                    // Callback Video (không có chat)
                    (userId, image) -> Platform.runLater(() -> updateVideoPanel(userId, image))
            );

            // Setup anti-cheat callback (chỉ cho students)
            if (!isProctor) {
                setupAntiCheat();
            }

            // Camera bắt buộc ON
            examStreamManager.setCameraActive(true);
            
            log.info("✅ Exam streaming started - Role: {}, ProctorPeer: {}", 
                    isProctor ? "PROCTOR" : "STUDENT", 
                    proctorPeer != null ? proctorPeer.getUserId() : "null");

        } catch (Exception e) {
            log.error("❌ Failed to start streaming", e);
        }
    }

    private void updateVideoPanel(String userId, Image image) {
        Platform.runLater(() -> {
            VideoPanel panel = videoPanels.get(userId);
            if (panel == null) {
                // Lấy tên từ participantNames hoặc exam
                String name;
                if (userId.equals(exam.getProctorId())) {
                    name = exam.getProctorName();
                } else {
                    name = participantNames.getOrDefault(userId, 
                            userId.equals(RMIClient.getInstance().getMyPeer().getUserId()) 
                                    ? "Bạn" 
                                    : "Student " + userId.substring(0, 8));
                }

                panel = new VideoPanel(userId, name);
                videoPanels.put(userId, panel);

                // Thêm vào grid
                int index = videoPanels.size() - 1;
                videoGrid.add(panel, index % 2, index / 2);
            }

            // Cập nhật hình ảnh
            panel.updateFrame(image);
        });
    }
    
    private void setupVideoGrid() {
        videoGrid.getChildren().clear();
        videoPanels.clear();
        // Grid sẽ tự động thêm video panels khi có dữ liệu
    }

    private void loadQuestions() {
        new Thread(() -> {
            try {
                questions = examService.getQuestions(exam.getExamId());
                Platform.runLater(() -> {
                    questionsContainer.getChildren().clear();
                    if (questions == null || questions.isEmpty()) {
                        questionsContainer.getChildren().add(new Label("Chưa có câu hỏi nào."));
                        return;
                    }

                    // Hiển thị từng câu hỏi
                    for (int i = 0; i < questions.size(); i++) {
                        Question question = questions.get(i);
                        VBox questionBox = createQuestionUI(question, i + 1);
                        questionsContainer.getChildren().add(questionBox);
                    }
                });
            } catch (Exception e) {
                log.error("❌ Load questions failed", e);
                Platform.runLater(() -> {
                    questionsContainer.getChildren().add(new Label("❌ Không thể tải câu hỏi: " + e.getMessage()));
                });
            }
        }).start();
    }

    private VBox createQuestionUI(Question question, int questionNumber) {
        VBox questionBox = new VBox(10);
        questionBox.setStyle("-fx-background-color: #2C2C2C; -fx-background-radius: 10; -fx-padding: 15;");
        questionBox.setSpacing(10);

        // Question header
        HBox headerBox = new HBox(10);
        Label questionNumberLabel = new Label("Câu " + questionNumber + " (" + question.getPoints() + " điểm):");
        questionNumberLabel.setStyle("-fx-text-fill: #4CAF50; -fx-font-size: 14; -fx-font-weight: bold;");
        headerBox.getChildren().add(questionNumberLabel);
        
        if (isProctor) {
            // Proctor: Hiển thị đáp án đúng
            if (question.getCorrectAnswerId() != null) {
                String correctAnswer = question.getAnswers().stream()
                        .filter(a -> a.getAnswerId().equals(question.getCorrectAnswerId()))
                        .findFirst()
                        .map(a -> a.getAnswerLabel() + ". " + a.getAnswerText())
                        .orElse("N/A");
                Label correctLabel = new Label("✓ Đáp án đúng: " + correctAnswer);
                correctLabel.setStyle("-fx-text-fill: #4CAF50; -fx-font-size: 12;");
                headerBox.getChildren().add(correctLabel);
            }
        }

        // Question text
        Label questionTextLabel = new Label(question.getQuestionText());
        questionTextLabel.setWrapText(true);
        questionTextLabel.setStyle("-fx-text-fill: white; -fx-font-size: 13;");

        // Answer options
        VBox answersBox = new VBox(8);
        answersBox.setSpacing(8);

        if (isProctor) {
            // Proctor: Chỉ xem (không cho chọn)
            for (var answer : question.getAnswers()) {
                Label answerLabel = new Label(answer.getAnswerLabel() + ". " + answer.getAnswerText());
                answerLabel.setStyle("-fx-text-fill: white; -fx-font-size: 12;");
                if (answer.getAnswerId().equals(question.getCorrectAnswerId())) {
                    answerLabel.setStyle("-fx-text-fill: #4CAF50; -fx-font-size: 12; -fx-font-weight: bold;");
                }
                answersBox.getChildren().add(answerLabel);
            }
        } else {
            // Student: RadioButtons để chọn đáp án
            ToggleGroup answerGroup = new ToggleGroup();
            for (int i = 0; i < question.getAnswers().size(); i++) {
                var answer = question.getAnswers().get(i);
                RadioButton radioButton = new RadioButton(answer.getAnswerLabel() + ". " + answer.getAnswerText());
                radioButton.setToggleGroup(answerGroup);
                radioButton.setUserData(answer.getAnswerId());
                radioButton.setStyle("-fx-text-fill: white; -fx-font-size: 12;");
                
                // Load saved answer
                StudentAnswer savedAnswer = studentAnswers.get(question.getQuestionId());
                if (savedAnswer != null && answer.getAnswerId().equals(savedAnswer.getSelectedAnswerId())) {
                    radioButton.setSelected(true);
                }

                // Save answer when selected
                radioButton.selectedProperty().addListener((obs, oldVal, newVal) -> {
                    if (newVal) {
                        saveAnswer(question, answer.getAnswerId());
                    }
                });

                answersBox.getChildren().add(radioButton);
            }
        }

        questionBox.getChildren().addAll(headerBox, questionTextLabel, answersBox);
        return questionBox;
    }
    
    @FXML
    private void handleAddQuestion() {
        if (!isProctor) return;
        
        try {
            javafx.fxml.FXMLLoader loader = new javafx.fxml.FXMLLoader(
                getClass().getResource("/view/add-question-dialog.fxml")
            );
            
            javafx.stage.Stage dialog = new javafx.stage.Stage();
            dialog.initModality(javafx.stage.Modality.APPLICATION_MODAL);
            dialog.setTitle("Thêm Câu Hỏi");
            dialog.setScene(new javafx.scene.Scene(loader.load(), 600, 500));
            
            AddQuestionDialogController controller = loader.getController();
            controller.setExamId(exam.getExamId());
            controller.setOnQuestionAdded(this::onQuestionAdded);
            
            dialog.showAndWait();
            
        } catch (Exception e) {
            log.error("Failed to open add question dialog", e);
            showError("Không thể mở dialog thêm câu hỏi!");
        }
    }
    
    private void onQuestionAdded(Question question) {
        loadQuestions(); // Reload questions
    }

    private void saveAnswer(Question question, String selectedAnswerId) {
        try {
            StudentAnswer answer = StudentAnswer.builder()
                    .questionId(question.getQuestionId())
                    .selectedAnswerId(selectedAnswerId)
                    .maxPoints(question.getPoints())
                    .answeredAt(System.currentTimeMillis())
                    .build();

            studentAnswers.put(question.getQuestionId(), answer);

            // Gửi lên server (async)
            new Thread(() -> {
                examService.submitAnswer(exam.getExamId(), answer);
            }).start();

            log.debug("💾 Saved answer for question: {}", question.getQuestionId());
        } catch (Exception e) {
            log.error("❌ Save answer failed", e);
        }
    }

    private void startTimer() {
        timerExecutor = Executors.newSingleThreadScheduledExecutor();
        timerExecutor.scheduleAtFixedRate(() -> {
            long elapsed = System.currentTimeMillis() - examStartTime;
            long remaining = examDurationMs - elapsed;

            if (remaining <= 0) {
                Platform.runLater(() -> {
                    timerLabel.setText("⏱ Hết giờ!");
                    timerLabel.setStyle("-fx-text-fill: #E53935; -fx-font-size: 16; -fx-font-weight: bold;");
                    if (!isProctor) {
                        handleSubmitExam();
                    }
                });
                timerExecutor.shutdown();
                return;
            }

            long hours = remaining / (1000 * 60 * 60);
            long minutes = (remaining % (1000 * 60 * 60)) / (1000 * 60);
            long seconds = (remaining % (1000 * 60)) / 1000;

            String timeStr = String.format("%02d:%02d:%02d", hours, minutes, seconds);
            Platform.runLater(() -> {
                timerLabel.setText("⏱ " + timeStr);
                if (remaining < 5 * 60 * 1000) { // Cảnh báo khi còn < 5 phút
                    timerLabel.setStyle("-fx-text-fill: #FF9800; -fx-font-size: 16; -fx-font-weight: bold;");
                } else {
                    timerLabel.setStyle("-fx-text-fill: #4CAF50; -fx-font-size: 16; -fx-font-weight: bold;");
                }
            });
        }, 0, 1, TimeUnit.SECONDS);
    }

    private void startAutoUpdate() {
        if (!isProctor) return;

        updateExecutor = Executors.newScheduledThreadPool(1);
        updateExecutor.scheduleAtFixedRate(() -> {
            try {
                List<ExamParticipant> participants = examService.getExamParticipants(exam.getExamId());
                Platform.runLater(() -> {
                    // Cập nhật tên participants
                    participants.forEach(p -> {
                        if (!p.getUserId().equals(exam.getProctorId())) {
                            participantNames.put(p.getUserId(), p.getUserName());
                        }
                    });
                    log.debug("📋 Participants: {}", participants.size());
                });
            } catch (Exception e) {
                log.warn("⚠️ Failed to update participants", e);
            }
        }, 5, 5, TimeUnit.SECONDS);
    }

    @FXML
    private void handleSubmitExam() {
        if (isProctor) return;

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Xác nhận nộp bài");
        confirm.setHeaderText("Bạn có chắc muốn nộp bài?");
        confirm.setContentText("Sau khi nộp bài, bạn không thể chỉnh sửa câu trả lời.");
        confirm.showAndWait().filter(r -> r == ButtonType.OK).ifPresent(r -> {
            submitExam();
        });
    }

    private void submitExam() {
        new Thread(() -> {
            try {
                // Nộp tất cả answers chưa nộp
                for (StudentAnswer answer : studentAnswers.values()) {
                    examService.submitAnswer(exam.getExamId(), answer);
                }

                // Submit exam và chấm điểm
                var result = examService.submitExam(exam.getExamId());

                Platform.runLater(() -> {
                    if (result != null) {
                        showExamResult(result);
                    } else {
                        showError("Không thể nộp bài. Vui lòng thử lại.");
                    }
                });
            } catch (Exception e) {
                log.error("❌ Submit exam failed", e);
                Platform.runLater(() -> {
                    showError("Lỗi khi nộp bài: " + e.getMessage());
                });
            }
        }).start();
    }

    private void showExamResult(common.model.exam.ExamResult result) {
        Alert resultAlert = new Alert(Alert.AlertType.INFORMATION);
        resultAlert.setTitle("Kết quả thi");
        resultAlert.setHeaderText("Điểm số của bạn");

        String content = String.format(
                "Tổng điểm: %.1f / %.1f điểm\n" +
                "Tỷ lệ: %.1f%%\n" +
                "Đúng: %d câu\n" +
                "Sai: %d câu\n" +
                "Tổng số câu: %d\n\n" +
                "Kết quả: %s",
                result.getTotalScore(),
                result.getMaxScore(),
                result.getPercentage(),
                result.getCorrectAnswers(),
                result.getWrongAnswers(),
                result.getTotalQuestions(),
                result.isPassed() ? "✅ ĐẠT" : "❌ KHÔNG ĐẠT"
        );

        resultAlert.setContentText(content);
        resultAlert.showAndWait();

        // Đóng exam room
        closeWindow();
    }

    @FXML
    private void handleLeave() {
        if (isProctor) {
            handleEndExam();
        } else {
            handleLeaveExam();
        }
    }

    private void handleEndExam() {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Xác nhận");
        confirm.setHeaderText("Kết thúc bài thi?");
        confirm.setContentText("Kết thúc sẽ đuổi tất cả thí sinh khỏi phòng thi.");
        confirm.showAndWait().filter(r -> r == ButtonType.OK).ifPresent(r -> {
            new Thread(() -> {
                examService.endExam(exam.getExamId());
                Platform.runLater(this::closeWindow);
            }).start();
        });
    }

    private void handleLeaveExam() {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Xác nhận");
        confirm.setHeaderText("Rời khỏi phòng thi?");
        confirm.setContentText("Bạn có chắc muốn rời đi?");
        confirm.showAndWait().filter(r -> r == ButtonType.OK).ifPresent(r -> {
            cleanup();
            new Thread(() -> {
                examService.leaveExam(exam.getExamId());
                Platform.runLater(this::closeWindow);
            }).start();
        });
    }

    private void setupAntiCheat() {
        if (examStreamManager != null && !isProctor) {
            examStreamManager.setViolationCallback(result -> {
                Platform.runLater(() -> {
                    if (result != null && !"OK".equals(result.decision)) {
                        showViolationAlert(result);
                    }
                });
            });
            log.info("✅ Anti-cheat monitoring enabled for student");
        }
    }

    private void showViolationAlert(org.example.eduverseclient.service.AntiCheatService.AnalysisResult result) {
        if (alertContainer == null) return;
        
        String style = "-fx-background-color: #E53935; -fx-text-fill: white; -fx-padding: 10; -fx-background-radius: 5; -fx-font-size: 12;";
        if ("WARNING".equals(result.decision)) {
            style = "-fx-background-color: #FFC107; -fx-text-fill: black; -fx-padding: 10; -fx-background-radius: 5; -fx-font-size: 12;";
        }
        
        String flagsText = result.flags != null ? String.join(", ", result.flags) : "Unknown";
        Label alert = new Label(String.format("⚠️ %s (Score: %.1f%%) - %s", 
                result.decision, result.suspicionScore * 100, flagsText));
        alert.setStyle(style);
        alert.setWrapText(true);
        alert.setMaxWidth(Double.MAX_VALUE);
        
        alertContainer.getChildren().add(alert);
        
        // Auto-remove sau 5 giây
        new Thread(() -> {
            try {
                Thread.sleep(5000);
                Platform.runLater(() -> alertContainer.getChildren().remove(alert));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }).start();
    }

    private void cleanup() {
        log.info("🧹 Cleaning up exam room resources...");

        if (timerExecutor != null) {
            timerExecutor.shutdownNow();
        }

        if (updateExecutor != null) {
            updateExecutor.shutdownNow();
        }

        if (examStreamManager != null) {
            examStreamManager.stop();
        }

        log.info("✅ Cleanup completed");
    }

    private void closeWindow() {
        cleanup();
        Stage stage = (Stage) leaveButton.getScene().getWindow();
        stage.close();
    }

    private void showError(String msg) {
        Alert a = new Alert(Alert.AlertType.ERROR);
        a.setContentText(msg);
        a.show();
    }
}

