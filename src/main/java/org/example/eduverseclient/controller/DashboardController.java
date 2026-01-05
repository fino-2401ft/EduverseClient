package org.example.eduverseclient.controller;


import common.model.Peer;
import common.model.User;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import lombok.extern.slf4j.Slf4j;
import org.example.eduverseclient.RMIClient;

@Slf4j
public class DashboardController {
    
    @FXML private Label userNameLabel;
    @FXML private Label userRoleLabel;
    @FXML private Label ipLabel;
    @FXML private Label videoPortLabel;
    @FXML private Label audioPortLabel;
    @FXML private Label chatPortLabel;
    @FXML private StackPane contentPane;
    @FXML private VBox peerInfoBox;

    private RMIClient rmiClient;
    private User currentUser;
    private Peer myPeer;
    
    @FXML
    public void initialize() {
        rmiClient = RMIClient.getInstance();
        currentUser = rmiClient.getCurrentUser();
        myPeer = rmiClient.getMyPeer();
        
        // Hiển thị thông tin user
        if (currentUser != null) {
            userNameLabel.setText(currentUser.getFullName());
            userRoleLabel.setText("(" + currentUser.getRole().name() + ")");
        }
        
        // Hiển thị peer info
        if (myPeer != null) {
            ipLabel.setText("IP: " + myPeer.getIpAddress());
            videoPortLabel.setText("Video: " + myPeer.getVideoPort());
            audioPortLabel.setText("Audio: " + myPeer.getAudioPort());
            chatPortLabel.setText("Chat: " + myPeer.getChatPort());
        }
        
        // Load Home Dashboard mặc định
        showHome();
    }
    
    @FXML
    private void showHome() {
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/view/home-dashboard.fxml")
            );
            
            Node homeView = loader.load();
            contentPane.getChildren().clear();
            contentPane.getChildren().add(homeView);
            
            log.info("✅ Home Dashboard loaded");
            
        } catch (Exception e) {
            log.error("❌ Failed to load home dashboard", e);
            showInfo("Không thể tải Home Dashboard!");
        }
    }
    
    @FXML
    private void showCourses() {
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/view/course-dashboard.fxml")
            );
            
            Node courseView = loader.load();
            contentPane.getChildren().clear();
            contentPane.getChildren().add(courseView);
            
            log.info("✅ Course Dashboard loaded");
            
        } catch (Exception e) {
            log.error("❌ Failed to load course dashboard", e);
            showInfo("Không thể tải Course Dashboard!");
        }
    }

    @FXML
    private void showMeetings() {
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/view/meeting-list.fxml")
            );

            Node meetingView = loader.load();
            contentPane.getChildren().clear();
            contentPane.getChildren().add(meetingView);

        } catch (Exception e) {
            log.error("Failed to load meeting view", e);
            showInfo("Không thể tải danh sách meeting!");
        }
    }
    
    @FXML
    private void showChat() {
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/view/chat-view.fxml")
            );    Node chatView = loader.load();
            contentPane.getChildren().clear();
            contentPane.getChildren().add(chatView);} catch (Exception e) {
            log.error("Failed to load chat view", e);
            showInfo("Không thể tải chat!");
        }
    }
    
    @FXML
    private void showExams() {
        showInfo("Chức năng Bài thi đang được phát triển");
    }
    
    @FXML
    private void handleLogout() {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Xác nhận");
        confirm.setHeaderText("Bạn có chắc muốn đăng xuất?");
        confirm.setContentText("Tất cả kết nối sẽ bị ngắt.");
        
        confirm.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                // Logout
                new Thread(() -> {
                    rmiClient.logout();
                    Platform.runLater(this::backToLogin);
                }).start();
            }
        });
    }


    
    private void backToLogin() {
        try {
            FXMLLoader loader = new FXMLLoader(
                getClass().getResource("/view/login.fxml")
            );
            Scene scene = new Scene(loader.load(), 500, 600);
            
            Stage stage = (Stage) userNameLabel.getScene().getWindow();
            stage.setScene(scene);
            stage.setTitle("Eduverse - Đăng nhập");
            stage.centerOnScreen();
            
        } catch (Exception e) {
            log.error(" Failed to open login", e);
        }
    }
    
    private void showInfo(String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Thông báo");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}