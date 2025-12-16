module org.example.eduverseclient {
    requires javafx.controls;
    requires javafx.fxml;

    requires org.controlsfx.controls;
    requires org.kordamp.bootstrapfx.core;
    requires java.rmi;
    requires org.slf4j;
    requires static lombok;

    opens org.example.eduverseclient to javafx.fxml;
    exports org.example.eduverseclient;


 //exports là để chỉ định các gói (packages) mà module này muốn chia sẻ với các module khác.
    exports common.rmi;
    exports common.model;
}