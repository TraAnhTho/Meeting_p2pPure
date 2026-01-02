package com.example.dacs4.controllers;

import com.example.dacs4.App;
import com.example.dacs4.DB.SQLiteConnection;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;

public class LoginController {

    @FXML
    private Label titleLabel;
    @FXML
    private Label descriptionLabel;
    @FXML
    private VBox nameFieldContainer;
    @FXML
    private TextField nameField;
    @FXML
    private TextField emailField;
    @FXML
    private PasswordField passwordField;
    @FXML
    private Button submitButton;
    @FXML
    private Hyperlink toggleModeLink;

    private boolean isSignUp = false; // Trạng thái đăng ký hay đăng nhập

    // Callback để App.java bắt sự kiện login
    public interface OnLoginListener {
        void onLogin(String id, String name, String email, String avatar);
    }

    private OnLoginListener loginCallback;

    public void setOnLogin(OnLoginListener cb) {
        this.loginCallback = cb;
    }

    @FXML
    private void initialize() {
        updateModeUI();
    }

    @FXML
    private void onToggleMode() {
        isSignUp = !isSignUp; // Chuyển đổi giữa đăng ký và đăng nhập
        updateModeUI();
    }

    @FXML
    private void onSubmit() {
        String email = emailField.getText().trim();
        String password = passwordField.getText();

        // Kiểm tra email và password
        if (email.isEmpty() || password.isEmpty()) {
            showAlert("Vui lòng nhập đầy đủ email và mật khẩu.");
            return;
        }

        try (SQLiteConnection db = new SQLiteConnection()) {
            db.createTables(); // Đảm bảo tables đã được tạo

            if (isSignUp) {
                // ===== ĐĂNG KÝ =====
                String name = nameField.getText().trim();
                if (name.isEmpty()) {
                    showAlert("Vui lòng nhập họ tên.");
                    return;
                }

                // Kiểm tra email đã tồn tại chưa
                ResultSet existingUser = db.getUserByEmail(email);
                if (existingUser.next()) {
                    showAlert("Email đã được đăng ký. Vui lòng đăng nhập.");
                    return;
                }

                // Đăng ký user mới (password sẽ được hash tự động)
                int userId = db.registerUser(name, email, password);
                String avatarUrl = "https://api.dicebear.com/7.x/avataaars/svg?seed=" + email;

                System.out.println("=== ĐĂNG KÝ THÀNH CÔNG ===");
                System.out.println("User ID: " + userId);
                System.out.println("Name: " + name);
                System.out.println("Email: " + email);

                // Chuyển sang Dashboard
                navigateToDashboard(String.valueOf(userId), name, email, avatarUrl);

            } else {
                // ===== ĐĂNG NHẬP =====
                int userId = db.authenticateUser(email, password);

                if (userId == -1) {
                    showAlert("Email hoặc mật khẩu không đúng.");
                    return;
                }

                // Lấy thông tin user
                ResultSet userInfo = db.getUserById(userId);
                if (userInfo.next()) {
                    String name = userInfo.getString("username");
                    String avatarUrl = "https://api.dicebear.com/7.x/avataaars/svg?seed=" + email;

                    System.out.println("=== ĐĂNG NHẬP THÀNH CÔNG ===");
                    System.out.println("User ID: " + userId);
                    System.out.println("Name: " + name);
                    System.out.println("Email: " + email);

                    // Chuyển sang Dashboard
                    navigateToDashboard(String.valueOf(userId), name, email, avatarUrl);
                } else {
                    showAlert("Không tìm thấy thông tin người dùng.");
                }
            }

        } catch (SQLException e) {
            e.printStackTrace();
            showAlert("Lỗi kết nối database: " + e.getMessage());
        }
    }

    /** Helper method để navigate sang Dashboard */
    private void navigateToDashboard(String userId, String name, String email, String avatarUrl) {
        // Nếu có callback -> dùng callback
        if (loginCallback != null) {
            loginCallback.onLogin(userId, name, email, avatarUrl);
            return;
        }

        // Nếu không có callback -> load Dashboard trực tiếp
        try {
            Stage stage = (Stage) submitButton.getScene().getWindow();
            goToDashboard(stage, userId, name, email, avatarUrl);
        } catch (IOException e) {
            e.printStackTrace();
            showAlert("Không thể mở Dashboard. Vui lòng thử lại.");
        }
    }

    /** Chuyển sang màn Dashboard và truyền thông tin user */
    private void goToDashboard(Stage stage, String userId, String name, String email, String avatarUrl)
            throws IOException {
        // Lưu thông tin user vào App static fields
        App.currentUserId = userId;
        App.currentUserName = name;
        App.currentUserEmail = email;
        App.currentUserAvatar = avatarUrl;

        // Navigate to Dashboard
        App.goToDashboard();
    }

    /** Cập nhật giao diện khi chuyển giữa đăng nhập và đăng ký */
    private void updateModeUI() {
        if (isSignUp) {
            // Nếu là chế độ đăng ký
            titleLabel.setText("Tạo tài khoản");
            descriptionLabel.setText("Điền thông tin để tạo tài khoản mới");
            submitButton.setText("Tạo tài khoản");
            toggleModeLink.setText("Đã có tài khoản? Đăng nhập");
            nameFieldContainer.setVisible(true);
            nameFieldContainer.setManaged(true);
        } else {
            // Nếu là chế độ đăng nhập
            titleLabel.setText("Đăng nhập");
            descriptionLabel.setText("Đăng nhập để tham gia cuộc họp");
            submitButton.setText("Đăng nhập");
            toggleModeLink.setText("Chưa có tài khoản? Đăng ký");
            nameFieldContainer.setVisible(false);
            nameFieldContainer.setManaged(false);
        }
    }

    /** Hiển thị thông báo lỗi */
    private void showAlert(String msg) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setHeaderText(null);
        alert.setContentText(msg);
        alert.showAndWait();
    }
}
