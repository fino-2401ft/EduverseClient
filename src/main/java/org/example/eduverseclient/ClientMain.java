package org.example.eduverseclient;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ClientMain extends Application {
    
    @Override
    public void start(Stage primaryStage) {
        try {
            log.info("========================================");
            log.info("   EDUVERSE CLIENT - STARTING UP");
            log.info("========================================");
            
            // Load Login UI
            FXMLLoader loader = new FXMLLoader(
                getClass().getResource("/org/example/eduverseclient/login.fxml")
            );
            Scene scene = new Scene(loader.load(), 500, 600);
            
            primaryStage.setTitle("Eduverse - Đăng nhập");
            primaryStage.setScene(scene);
            primaryStage.setResizable(false);
            primaryStage.show();
            
            log.info(" Client started successfully");
            
        } catch (Exception e) {
            log.error(" Failed to start client", e);
            System.exit(1);
        }
    }
    
    @Override
    public void stop() {
        log.info(" Shutting down client...");
        // Cleanup resources
        RMIClient.getInstance().shutdown();
        log.info(" Client shutdown complete");
    }
    
    public static void main(String[] args) {
        launch(args);
    }
}