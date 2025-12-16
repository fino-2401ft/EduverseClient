package org.example.eduverseclient.component;

import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import lombok.Getter;

public class VideoPanel extends VBox {
    
    @Getter
    private String userId;
    
    private ImageView imageView;
    private Label nameLabel;
    private StackPane videoContainer;
    
    public VideoPanel(String userId, String userName) {
        this.userId = userId;
        
        setAlignment(Pos.CENTER);
        setStyle("-fx-background-color: #424242; -fx-background-radius: 10;");
        setPrefSize(320, 240);
        setSpacing(10);
        
        videoContainer = new StackPane();
        videoContainer.setStyle("-fx-background-color: #2C2C2C;");
        videoContainer.setPrefSize(300, 200);
        
        imageView = new ImageView();
        imageView.setFitWidth(300);
        imageView.setFitHeight(200);
        imageView.setPreserveRatio(false);
        
        Label placeholderLabel = new Label("👤");
        placeholderLabel.setStyle("-fx-font-size: 48; -fx-text-fill: #666;");
        
        videoContainer.getChildren().addAll(placeholderLabel, imageView);
        
        nameLabel = new Label(userName);
        nameLabel.setStyle("-fx-text-fill: white; -fx-font-size: 13;");
        
        getChildren().addAll(videoContainer, nameLabel);
    }
    
    public void updateFrame(Image image) {
        if (image != null) {
            imageView.setImage(image);
            imageView.setVisible(true);
        }
    }
    
    public void clearVideo() {
        imageView.setImage(null);
        imageView.setVisible(false);
    }
}