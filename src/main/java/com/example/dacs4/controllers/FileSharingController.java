package com.example.dacs4.controllers;

import com.example.dacs4.models.ChatMessage;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.format.DateTimeFormatter;
import java.util.function.Consumer;

public class FileSharingController {

    @FXML
    private BorderPane root;

    @FXML
    private Button uploadButton;

    @FXML
    private ListView<ChatMessage> filesListView;

    private ObservableList<ChatMessage> fileMessages;
    private Consumer<File> onFileUpload;

    private final DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm");

    @FXML
    private void initialize() {
        // Placeholder khi chưa có file
        Label placeholder = new Label("Chưa có tệp tin nào được chia sẻ");
        placeholder.setStyle("-fx-text-fill: #9ca3af; -fx-font-size: 12;");
        filesListView.setPlaceholder(placeholder);

        // Custom cell hiển thị giống UI React
        filesListView.setCellFactory(list -> new FileMessageCell());
    }

    // ========== API cho MeetingRoomController ==========

    public Parent getRoot() {
        return root;
    }

    public void setOnFileUpload(Consumer<File> onFileUpload) {
        this.onFileUpload = onFileUpload;
    }

    public void setMessages(ObservableList<ChatMessage> fileMessages) {
        System.out.println("🔍 DEBUG: FileSharingController.setMessages() called");
        System.out.println("📊 DEBUG: Observable list size: " + (fileMessages != null ? fileMessages.size() : "null"));
        this.fileMessages = fileMessages;
        filesListView.setItems(fileMessages);
        System.out.println("✅ DEBUG: ListView items set successfully");
    }

    public void addFileMessage(ChatMessage msg) {
        System.out.println("🔍 DEBUG: FileSharingController.addFileMessage() called");
        System.out.println("📄 DEBUG: Message - fileName=" + (msg != null ? msg.getFileName() : "null") + ", sender="
                + (msg != null ? msg.getSenderName() : "null"));

        if (fileMessages == null) {
            System.err.println("❌ ERROR: fileMessages is NULL! Cannot add message.");
            System.err.println("⚠️ This means setMessages() was not called or controller not initialized!");
            return;
        }

        System.out.println("📊 DEBUG: Current fileMessages size before add: " + fileMessages.size());
        fileMessages.add(msg);
        System.out.println("✅ DEBUG: File message added successfully. New size: " + fileMessages.size());
    }

    // ========== Xử lý chọn file ==========

    @FXML
    private void handleUploadClick() {
        if (root == null || root.getScene() == null || root.getScene().getWindow() == null) {
            System.err.println("❌ Cannot open file chooser: UI not fully initialized");
            return;
        }

        FileChooser chooser = new FileChooser();
        chooser.setTitle("Chọn tệp để tải lên");
        File file = chooser.showOpenDialog(root.getScene().getWindow());
        if (file != null && onFileUpload != null) {
            onFileUpload.accept(file);
        }
    }

    // ========== Cell hiển thị từng file ==========

    private class FileMessageCell extends ListCell<ChatMessage> {
        private final VBox rootBox = new VBox(4);
        private final HBox contentBox = new HBox(8);
        private final Label iconLabel = new Label();
        private final ImageView thumbnailView = new ImageView();
        private final VBox infoBox = new VBox(2);
        private final Label fileNameLabel = new Label();
        private final Label metaLabel = new Label();
        private final Button downloadButton = new Button();

        FileMessageCell() {
            super();

            iconLabel.setStyle("-fx-font-size: 20;");

            thumbnailView.setFitWidth(64);
            thumbnailView.setFitHeight(64);
            thumbnailView.setPreserveRatio(true);
            thumbnailView.setSmooth(true);
            thumbnailView.setVisible(false);
            thumbnailView.setManaged(false);

            fileNameLabel.setStyle("-fx-text-fill: white; -fx-font-size: 13;");
            fileNameLabel.setMaxWidth(Double.MAX_VALUE);

            metaLabel.setStyle("-fx-text-fill: #9ca3af; -fx-font-size: 10;");

            infoBox.getChildren().addAll(fileNameLabel, metaLabel);
            HBox.setHgrow(infoBox, Priority.ALWAYS);

            downloadButton.setText("⬇");
            downloadButton.setStyle("-fx-background-color: transparent; -fx-text-fill: white;");

            contentBox.getChildren().addAll(iconLabel, thumbnailView, infoBox, downloadButton);
            rootBox.getChildren().add(contentBox);
            rootBox.setStyle("-fx-background-color: rgba(55,65,81,0.5); -fx-padding: 8; -fx-background-radius: 8;");
        }

        @Override
        protected void updateItem(ChatMessage msg, boolean empty) {
            System.out.println("🔍 DEBUG: FileMessageCell.updateItem() called - empty=" + empty + ", msg="
                    + (msg != null ? msg.getFileName() : "null"));
            super.updateItem(msg, empty);

            if (empty || msg == null) {
                System.out.println("⚠️ DEBUG: Cell is empty or msg is null, setting graphic to null");
                setGraphic(null);
                return;
            }

            System.out.println("✅ DEBUG: Rendering cell for file: " + msg.getFileName());

            String fileName = msg.getFileName() != null ? msg.getFileName() : msg.getText();
            fileNameLabel.setText(fileName);

            boolean isImage = isImageFile(fileName);
            Path localPath = resolveDownloadedPath(fileName);
            boolean hasLocalFile = localPath != null && Files.exists(localPath);

            String icon = getFileIcon(fileName);
            iconLabel.setText(icon);

            if (isImage && hasLocalFile) {
                try {
                    Image img = new Image(localPath.toUri().toString(), 64, 64, true, true);
                    thumbnailView.setImage(img);
                    thumbnailView.setVisible(true);
                    thumbnailView.setManaged(true);
                } catch (Exception ignored) {
                    thumbnailView.setImage(null);
                    thumbnailView.setVisible(false);
                    thumbnailView.setManaged(false);
                }
            } else {
                thumbnailView.setImage(null);
                thumbnailView.setVisible(false);
                thumbnailView.setManaged(false);
            }

            if (isImage && hasLocalFile) {
                thumbnailView.setOnMouseClicked(ev -> {
                    if (ev.getButton() != MouseButton.PRIMARY)
                        return;
                    showImagePreview(localPath, fileName);
                });
            } else {
                thumbnailView.setOnMouseClicked(null);
            }

            downloadButton.setDisable(!hasLocalFile);
            downloadButton.setOnAction(e -> downloadLocalFile(fileName));

            String time = msg.getTimestamp() != null
                    ? msg.getTimestamp().format(timeFormatter)
                    : "";
            metaLabel.setText(msg.getSenderName() + " • " + time);

            setGraphic(rootBox);
        }
    }

    private void showImagePreview(Path localPath, String fileName) {
        try {
            Image img = new Image(localPath.toUri().toString());
            ImageView view = new ImageView(img);
            view.setPreserveRatio(true);
            view.setSmooth(true);
            view.setFitWidth(800);

            Dialog<Void> dialog = new Dialog<>();
            dialog.setTitle(fileName);
            dialog.getDialogPane().setContent(view);
            dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
            dialog.show();
        } catch (Exception ex) {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Xem ảnh");
            alert.setHeaderText(null);
            alert.setContentText("Không thể mở ảnh: " + ex.getMessage());
            alert.show();
        }
    }

    private Path resolveDownloadedPath(String fileName) {
        if (fileName == null || fileName.isBlank())
            return null;
        return Paths.get("downloads", fileName);
    }

    private boolean isImageFile(String fileName) {
        if (fileName == null)
            return false;
        String lower = fileName.toLowerCase();
        return lower.endsWith(".jpg") || lower.endsWith(".jpeg")
                || lower.endsWith(".png") || lower.endsWith(".gif");
    }

    private void downloadLocalFile(String fileName) {
        try {
            Path src = resolveDownloadedPath(fileName);
            if (src == null || !Files.exists(src)) {
                Alert alert = new Alert(Alert.AlertType.INFORMATION);
                alert.setTitle("Tải xuống");
                alert.setHeaderText(null);
                alert.setContentText("Không tìm thấy tệp trong thư mục downloads. Tệp có thể chưa được nhận xong.");
                alert.show();
                return;
            }

            if (root == null || root.getScene() == null || root.getScene().getWindow() == null) {
                System.err.println("❌ Cannot open save dialog: UI not fully initialized");
                return;
            }

            FileChooser chooser = new FileChooser();
            chooser.setTitle("Lưu tệp về máy");
            chooser.setInitialFileName(fileName);
            File dest = chooser.showSaveDialog(root.getScene().getWindow());
            if (dest == null)
                return;

            Files.copy(src, dest.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception ex) {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Tải xuống");
            alert.setHeaderText(null);
            alert.setContentText("Không thể tải xuống tệp: " + ex.getMessage());
            alert.show();
        }
    }

    // ========== Helpers giống React ==========

    private String getFileIcon(String fileName) {
        String ext = "";
        int idx = fileName.lastIndexOf('.');
        if (idx != -1 && idx < fileName.length() - 1) {
            ext = fileName.substring(idx + 1).toLowerCase();
        }

        if (ext.matches("jpg|jpeg|png|gif|svg"))
            return "🖼️";
        if (ext.equals("pdf"))
            return "📄";
        if (ext.matches("doc|docx"))
            return "📝";
        if (ext.matches("xls|xlsx"))
            return "📊";
        if (ext.matches("zip|rar"))
            return "🗜️";
        return "📎";
    }
}
