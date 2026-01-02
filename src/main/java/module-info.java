module com.example.dacs4 {
    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.graphics;
    requires javafx.base;
    requires javafx.media;
    requires javafx.web;
    requires javafx.swing;

    // Database
    requires java.sql;

    // Audio/Sound
    requires java.desktop;

    // Optional libs
    requires org.controlsfx.controls;
    requires com.dlsc.formsfx;
    requires net.synedra.validatorfx;
    requires org.kordamp.ikonli.javafx;
    requires org.kordamp.bootstrapfx.core;
    // Webcam capture library (automatic module from webcam-capture.jar)
    requires webcam.capture;

    // Export main package để JavaFX có thể truy cập App class
    exports com.example.dacs4;

    // Cho phép FXML truy cập controller
    opens com.example.dacs4 to javafx.fxml;
    opens com.example.dacs4.controllers to javafx.fxml;
}
