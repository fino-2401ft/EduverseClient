package org.example.eduverseclient.controller;


import common.model.Peer;
import common.model.User;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
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
    }
    
    @FXML
    private void showCourses() {
        showInfo("Chức năng Khóa học đang được phát triển");
    }
    
    @FXML
    private void showMeetings() {
        showInfo("Chức năng Meeting đang được phát triển");
    }
    
    @FXML
    private void showChat() {
        showInfo("Chức năng Chat đang được phát triển");
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