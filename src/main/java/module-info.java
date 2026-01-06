module org.example.eduverseclient {
    requires javafx.controls;
    requires javafx.fxml;

    requires org.controlsfx.controls;
    requires org.kordamp.bootstrapfx.core;
    requires java.rmi;
    requires org.slf4j;
    requires static lombok;
    requires java.desktop;  // Cho các class AWT (BufferedImage, Dimension)
    requires javafx.swing;
    requires webcam.capture;
    requires cloudinary.core;
    requires javafx.web;  // Cho SwingFXUtils
    requires java.net.http;
    requires com.google.gson;  // Cho HttpClient (không cần Gson/Jackson vì dùng manual JSON parsing)

    opens org.example.eduverseclient to javafx.fxml;
    exports org.example.eduverseclient;

    //exports là để chỉ định các gói (packages) mà module này muốn chia sẻ với các module khác.
    exports common.rmi;
    exports common.model;

    // Mở quyền cho javafx.fxml truy cập vào package chứa Controller
    // để nó có thể khởi tạo LoginController và gán các biến @FXML
    opens org.example.eduverseclient.controller to javafx.fxml;
}

