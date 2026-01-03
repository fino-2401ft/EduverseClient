package org.example.eduverseclient.component;

import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.text.Font;

public class VideoPanel extends StackPane {

    private ImageView videoView;
    private VBox avatarView;
    private Label nameLabel;
    private String userId;

    public VideoPanel(String userId, String userName) {
        this.userId = userId;
        this.setStyle("-fx-background-color: #2b2b2b; -fx-background-radius: 10; -fx-border-radius: 10; -fx-border-color: #3e3e3e;");
        this.setPrefSize(240, 180); // Kích thước mặc định

        // 1. Layer Avatar (Nằm dưới)
        avatarView = createAvatarView(userName);
        
        // 2. Layer Video (Nằm trên)
        videoView = new ImageView();
        videoView.setFitWidth(240);
        videoView.setFitHeight(180);
        videoView.setPreserveRatio(true);
        videoView.setVisible(false); // Mặc định ẩn video đi

        // 3. Layer Tên (Nằm trên cùng góc dưới)
        nameLabel = new Label(userName);
        nameLabel.setStyle("-fx-text-fill: white; -fx-background-color: rgba(0,0,0,0.5); -fx-padding: 2 5; -fx-background-radius: 5;");
        StackPane.setAlignment(nameLabel, Pos.BOTTOM_LEFT);
        
        this.getChildren().addAll(avatarView, videoView, nameLabel);
    }

    private VBox createAvatarView(String name) {
        VBox box = new VBox(5);
        box.setAlignment(Pos.CENTER);

        // Tạo hình tròn đại diện Avatar
        Circle circle = new Circle(30);
        circle.setFill(Color.web("#4CAF50")); // Màu nền avatar
        
        // Lấy chữ cái đầu của tên
        String initial = (name != null && !name.isEmpty()) ? name.substring(0, 1).toUpperCase() : "?";
        Label initialLabel = new Label(initial);
        initialLabel.setFont(new Font("Arial", 24));
        initialLabel.setTextFill(Color.WHITE);

        StackPane avatarIcon = new StackPane(circle, initialLabel);
        
        Label statusLabel = new Label("Camera Off");
        statusLabel.setTextFill(Color.GRAY);
        statusLabel.setFont(new Font("Arial", 10));

        box.getChildren().addAll(avatarIcon, statusLabel);
        return box;
    }

    // Hàm cập nhật frame video
    public void updateFrame(Image image) {
        if (image != null) {
            Platform.runLater(() -> {
                videoView.setImage(image);
                videoView.setVisible(true); // Có hình -> Hiện Video
                avatarView.setVisible(false); // Ẩn Avatar
            });
        }
    }

    // Hàm set trạng thái Camera (Gọi từ Controller dựa vào thông tin từ Server)
    public void setCameraStatus(boolean isCameraOn) {
        Platform.runLater(() -> {
            if (!isCameraOn) {
                videoView.setVisible(false); // Ẩn Video
                videoView.setImage(null);    // Xóa hình cũ
                avatarView.setVisible(true); // Hiện Avatar
            }
            // Nếu cameraOn = true thì chưa làm gì cả, đợi có frame gửi tới mới hiện
        });
    }
}