package org.example.eduverseclient.controller;


import common.model.User;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.Stage;
import lombok.extern.slf4j.Slf4j;
import org.example.eduverseclient.RMIClient;

@Slf4j
public class LoginController {
    
    @FXML private TextField emailField;
    @FXML private PasswordField passwordField;
    @FXML private Button loginButton;
    @FXML private Label statusLabel;
    
    private RMIClient rmiClient;
    
    @FXML
    public void initialize() {
        rmiClient = RMIClient.getInstance();
        
        // Kết nối RMI Server
        if (!rmiClient.connect()) {
            showError("Không thể kết nối đến Server!");
            loginButton.setDisable(true);
        }
        
        // Enter để login
        passwordField.setOnAction(e -> handleLogin());
    }
    
    @FXML
    private void handleLogin() {
        String email = emailField.getText().trim();
        String password = passwordField.getText();
        
        // Validation
        if (email.isEmpty() || password.isEmpty()) {
            showError("Vui lòng nhập đầy đủ thông tin!");
            return;
        }
        
        // Disable button
        loginButton.setDisable(true);
        statusLabel.setText("Đang đăng nhập...");
        statusLabel.setStyle("-fx-text-fill: blue;");
        
        // Login trong background thread
        new Thread(() -> {
            User user = rmiClient.login(email, password);
            
            Platform.runLater(() -> {
                loginButton.setDisable(false);
                
                if (user != null) {
                    log.info("✅ Login success: {}", user.getFullName());
                    openDashboard();
                } else {
                    showError("Email hoặc mật khẩu không đúng!");
                }
            });
        }).start();
    }
    
    @FXML
    private void handleRegister() {
        // TODO: Mở màn hình đăng ký
        showInfo("Chức năng đăng ký đang được phát triển");
    }
    
    private void openDashboard() {
        try {
            FXMLLoader loader = new FXMLLoader(
                getClass().getResource("/org/example/eduverseclient/dashboard.fxml")
            );
            Scene scene = new Scene(loader.load(), 1200, 700);
            
            Stage stage = (Stage) loginButton.getScene().getWindow();
            stage.setScene(scene);
            stage.setTitle("Eduverse - Dashboard");
            stage.centerOnScreen();
            
        } catch (Exception e) {
            log.error(" Failed to open dashboard", e);
            showError("Không thể mở Dashboard!");
        }
    }
    
    private void showError(String message) {
        statusLabel.setText(message);
        statusLabel.setStyle("-fx-text-fill: red;");
    }
    
    private void showInfo(String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Thông báo");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}