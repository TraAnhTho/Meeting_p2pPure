package com.example.dacs4.controllers;

import com.example.dacs4.models.ChatMessage; // Đảm bảo package này đúng
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.time.format.DateTimeFormatter;
import java.util.function.Consumer;

public class ChatController {

    // ===== FXML UI Elements =====
    @FXML private ScrollPane scrollPane;
    @FXML private VBox messagesBox;
    @FXML private TextField messageInput;
    @FXML private Button sendButton;

    // ===== State =====
    private String currentUserId;  // ID của user hiện tại
    private Consumer<String> onSendMessage; // Callback gửi tin nhắn từ ChatController sang MeetingRoomController

    // Formatter để hiển thị thời gian
    private final DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm");

    // ===== Initialize =====
    @FXML
    private void initialize() {
        // Tự động kéo xuống cuối khi thêm tin nhắn
        messagesBox.heightProperty().addListener((obs, oldV, newV) -> scrollPane.setVvalue(1.0));

        // Đảm bảo nút gửi chỉ hoạt động khi có nội dung trong ô nhập
        sendButton.setDisable(true);
        messageInput.textProperty().addListener((observable, oldValue, newValue) ->
                sendButton.setDisable(newValue.trim().isEmpty())
        );
    }

    // ===== API cho controller khác dùng =====

    // Set ID của người dùng hiện tại (để phân biệt tin nhắn)
    public void setCurrentUserId(String currentUserId) {
        this.currentUserId = currentUserId;
    }

    // Set callback để gửi tin nhắn
    public void setOnSendMessage(Consumer<String> onSendMessage) {
        this.onSendMessage = onSendMessage;
    }

    /** Thêm 1 message mới vào UI (gọi từ MeetingRoomController khi có tin nhắn mới) */
    public void addMessage(ChatMessage msg) {
        boolean isCurrentUser = currentUserId != null && currentUserId.equals(msg.getSenderId());

        HBox root = new HBox();
        root.setFillHeight(true);

        // Đẩy bubble về bên phải nếu là user hiện tại
        if (isCurrentUser) {
            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);
            root.getChildren().add(spacer);
        }

        VBox bubbleBox = new VBox(2);
        bubbleBox.setMaxWidth(400);

        // Tên người gửi (chỉ hiển thị nếu không phải current user)
        if (!isCurrentUser) {
            Label nameLabel = new Label(msg.getSenderName());
            nameLabel.setStyle("-fx-text-fill: #9ca3af; -fx-font-size: 11;");
            bubbleBox.getChildren().add(nameLabel);
        }

        // Nội dung tin nhắn
        Label textLabel = new Label(msg.getText());
        textLabel.setWrapText(true);
        textLabel.setStyle(
                "-fx-padding: 6 10 6 10;" +
                        "-fx-background-radius: 8;" +
                        (isCurrentUser
                                ? "-fx-background-color: #4f46e5; -fx-text-fill: white;"  // Màu nền cho tin nhắn của user
                                : "-fx-background-color: #1f2937; -fx-text-fill: white;") // Màu nền cho tin nhắn của người khác
        );
        bubbleBox.getChildren().add(textLabel);

        // Thời gian
        Label timeLabel = new Label(msg.getTimestamp().format(timeFormatter));
        timeLabel.setStyle("-fx-text-fill: #6b7280; -fx-font-size: 10;");
        bubbleBox.getChildren().add(timeLabel);

        root.getChildren().add(bubbleBox);
        messagesBox.getChildren().add(root);
    }

    // ===== Xử lý nút gửi =====

    @FXML
    private void handleSendMessage() {
        String text = messageInput.getText().trim();
        if (text.isEmpty()) {
            return;  // Nếu không có nội dung, không gửi
        }

        // Gọi callback để gửi tin nhắn từ ChatController sang MeetingRoomController
        if (onSendMessage != null) {
            onSendMessage.accept(text);  // Gửi tin nhắn từ ChatController sang MeetingRoomController
        }

        // Xóa nội dung nhập sau khi gửi
        messageInput.clear();
    }
}
