package com.example.dacs4.controllers;

import com.example.dacs4.DB.SQLiteConnection;
import com.example.dacs4.models.MeetingHistory;
import com.example.dacs4.network.MeetingRegistry;
import javafx.animation.PauseTransition;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.*;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.*;
import javafx.util.Duration;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.function.Consumer;

public class DashboardController {

    @FXML
    private Label avatarInitialLabel;
    @FXML
    private Label userNameLabel;
    @FXML
    private Label userEmailLabel;
    @FXML
    private VBox upcomingMeetingsContainer;

    // callback giống props TSX
    private Consumer<String> joinMeetingHandler;
    private Runnable logoutHandler;

    private final List<Meeting> upcomingMeetings = new ArrayList<>();
    private String lastCopiedMeetingId = null;

    // ----- Model Meeting (giống type Meeting TS) -----
    private static class Meeting {
        String id;
        String title;
        String time;
        int participants;
        Status status;

        enum Status {
            UPCOMING, LIVE, ENDED
        }

        Meeting(String id, String title, String time, int participants, Status status) {
            this.id = id;
            this.title = title;
            this.time = time;
            this.participants = participants;
            this.status = status;
        }
    }

    private String userId;
    private String userName;
    private String userEmail;
    private String userAvatar;

    public void setUser(String id, String name, String email, String avatarUrl) {
        this.userId = id;
        this.userName = name;
        this.userEmail = email;
        this.userAvatar = avatarUrl;

        // Cập nhật UI
        userNameLabel.setText(name);
        userEmailLabel.setText(email);
        if (name != null && !name.isEmpty()) {
            avatarInitialLabel.setText(name.substring(0, 1).toUpperCase());
        }

        System.out.println("Dashboard nhận user: " + name + " (ID: " + id + ")");

        // Load meetings từ database
        renderUpcomingMeetings();
    }

    @FXML
    private void initialize() {
        // Load meetings từ database sẽ được gọi sau khi setUser()
    }

    // ----- API giống props Dashboard -----

    public void setOnJoinMeeting(Consumer<String> handler) {
        this.joinMeetingHandler = handler;
    }

    public void setOnLogout(Runnable handler) {
        this.logoutHandler = handler;
    }

    public void setUser(String name, String email) {
        userNameLabel.setText(name);
        userEmailLabel.setText(email);
        if (name != null && !name.isEmpty()) {
            avatarInitialLabel.setText(name.substring(0, 1).toUpperCase());
        }
    }

    // Nếu bạn có class User riêng:
    // public void setUser(User user) { ... }

    // ----- Handlers cho FXML -----

    @FXML
    private void onCreateNewMeeting() {
        // Hiển thị dialog để nhập tên cuộc họp
        TextInputDialog dialog = new TextInputDialog("Cuộc họp mới");
        dialog.setTitle("Tạo cuộc họp");
        dialog.setHeaderText("Bắt đầu cuộc họp ngay");
        dialog.setContentText("Nhập tên cuộc họp:");

        Optional<String> result = dialog.showAndWait();
        result.ifPresent(meetingTitle -> {
            // Tạo ID ngẫu nhiên 5-6 ký tự (chữ + số)
            String newMeetingId = generateMeetingId();

            // Lưu vào database với role = "creator"
            try (SQLiteConnection db = new SQLiteConnection()) {
                db.createTables();
                db.saveMeetingHistory(userId, newMeetingId, meetingTitle, "creator");
                System.out.println("✅ Đã lưu meeting mới: " + newMeetingId + " - " + meetingTitle);

                // Note: Meeting will be registered in MeetingRegistry when P2P server starts
                // in MeetingRoomP2PHandler.startConnection() -> P2PManager.createMeeting()
            } catch (SQLException e) {
                e.printStackTrace();
                System.err.println("❌ Lỗi khi lưu meeting history: " + e.getMessage());
            }

            // Điều hướng đến MeetingRoom
            if (joinMeetingHandler != null) {
                joinMeetingHandler.accept(newMeetingId);
            }
        });
    }

    /**
     * Tạo Meeting ID ngẫu nhiên 5-6 ký tự (chữ + số)
     */
    private String generateMeetingId() {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        Random random = new Random();
        int length = 5 + random.nextInt(2); // 5 hoặc 6 ký tự

        StringBuilder meetingId = new StringBuilder();
        for (int i = 0; i < length; i++) {
            meetingId.append(chars.charAt(random.nextInt(chars.length())));
        }

        return meetingId.toString();
    }

    @FXML
    private void onLogoutClicked() {
        if (logoutHandler != null) {
            logoutHandler.run();
        }
    }

    // ----- Render list cuộc họp sắp tới -----

    private void renderUpcomingMeetings() {
        upcomingMeetingsContainer.getChildren().clear();

        // Load meetings từ database
        try (SQLiteConnection db = new SQLiteConnection()) {
            db.createTables();
            List<MeetingHistory> historyList = db.getMeetingHistory(userId);

            if (historyList.isEmpty()) {
                Label emptyLabel = new Label("Chưa có cuộc họp nào. Tạo cuộc họp mới để bắt đầu!");
                emptyLabel.setStyle("-fx-text-fill: #9ca3af; -fx-padding: 20;");
                upcomingMeetingsContainer.getChildren().add(emptyLabel);
                return;
            }

            // Convert MeetingHistory -> Meeting và render
            for (MeetingHistory history : historyList) {
                if (!history.isEnded()) { // Chỉ hiển thị meeting chưa kết thúc
                    Meeting meeting = new Meeting(
                            history.getMeetingId(),
                            history.getMeetingTitle(),
                            "Lần truy cập cuối: " + history.getLastAccessed(),
                            0, // participants count (có thể query từ meeting_participants)
                            Meeting.Status.UPCOMING);
                    upcomingMeetingsContainer.getChildren().add(createMeetingCard(meeting, history.getRole()));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
            Label errorLabel = new Label("Lỗi khi tải danh sách cuộc họp: " + e.getMessage());
            errorLabel.setStyle("-fx-text-fill: #ef4444; -fx-padding: 20;");
            upcomingMeetingsContainer.getChildren().add(errorLabel);
        }
    }

    private Pane createMeetingCard(Meeting meeting, String role) {
        VBox card = new VBox();
        card.getStyleClass().add("meeting-card");
        card.setSpacing(8);

        // Title với badge role
        HBox titleRow = new HBox(8);
        Label titleLabel = new Label(meeting.title);
        titleLabel.getStyleClass().add("meeting-title");

        Label roleLabel = new Label(role.equals("creator") ? "👑 Host" : "👤 Participant");
        roleLabel.setStyle("-fx-background-color: " + (role.equals("creator") ? "#4f46e5" : "#6b7280") +
                "; -fx-text-fill: white; -fx-padding: 2 8; -fx-background-radius: 4; -fx-font-size: 10;");
        titleRow.getChildren().addAll(titleLabel, roleLabel);

        Label timeLabel = new Label("⏰ " + meeting.time);
        Label participantsLabel = new Label("👥 " + meeting.participants + " người tham gia");

        HBox infoRow = new HBox(16, timeLabel, participantsLabel);
        infoRow.getStyleClass().add("meeting-info-row");

        // Copy ID button
        Button copyButton = new Button(meeting.id);
        copyButton.getStyleClass().add("copy-id-button");
        copyButton.setOnAction(e -> copyMeetingId(meeting.id, copyButton));

        // Join button
        // Join button trong DashboardController
        Button joinButton = new Button("Tham gia");
        joinButton.getStyleClass().add("primary-button");
        joinButton.setOnAction(e -> {
            if (joinMeetingHandler != null) {
                joinMeetingHandler.accept(meeting.id); // Điều hướng tới Meeting Room khi tham gia
            }
        });

        HBox bottomRow = new HBox(16);
        bottomRow.getChildren().addAll(copyButton, joinButton);
        bottomRow.getStyleClass().add("meeting-bottom-row");

        card.getChildren().addAll(titleRow, infoRow, bottomRow);
        return card;
    }

    // ----- Copy meeting ID + hiệu ứng "copied" 2s -----

    private void copyMeetingId(String id, Button button) {
        Clipboard clipboard = Clipboard.getSystemClipboard();
        ClipboardContent content = new ClipboardContent();
        content.putString(id);
        clipboard.setContent(content);

        lastCopiedMeetingId = id;
        String originalText = button.getText();
        button.setText("Đã copy ✔");

        PauseTransition pause = new PauseTransition(Duration.seconds(2));
        pause.setOnFinished(e -> {
            if (id.equals(lastCopiedMeetingId)) {
                button.setText(originalText);
                lastCopiedMeetingId = null;
            }
        });
        pause.play();
    }

    @FXML
    private void onOpenJoinMeetingDialog() {
        try {
            // Tìm joinMeetingDialog.fxml theo nhiều cách giống App
            java.net.URL url = DashboardController.class.getResource("/fxml/joinMeetingDialog.fxml");
            if (url == null) {
                url = DashboardController.class.getResource("fxml/joinMeetingDialog.fxml");
            }

            if (url == null) {
                ClassLoader cl = Thread.currentThread().getContextClassLoader();
                if (cl != null) {
                    url = cl.getResource("fxml/joinMeetingDialog.fxml");
                }
            }

            if (url == null) {
                System.err.println(
                        "[WARN] joinMeetingDialog.fxml không có trên classpath, thử đường dẫn file tuyệt đối.");

                // TH1: chạy từ root module frontend (working dir = frontend)
                java.io.File f1 = new java.io.File("src/main/resources/fxml/joinMeetingDialog.fxml");
                System.err.println("[DEBUG] JD TH1 ABS = " + f1.getAbsolutePath() + ", exists = " + f1.exists());
                if (f1.exists()) {
                    url = f1.toURI().toURL();
                }

                // TH2: chạy từ root project (working dir = CallAppDesktop_dacs4)
                if (url == null) {
                    java.io.File f2 = new java.io.File("frontend/src/main/resources/fxml/joinMeetingDialog.fxml");
                    System.err.println("[DEBUG] JD TH2 ABS = " + f2.getAbsolutePath() + ", exists = " + f2.exists());
                    if (f2.exists()) {
                        url = f2.toURI().toURL();
                    }
                }
            }

            if (url == null) {
                System.err.println(
                        "[ERROR] Vẫn không tìm được joinMeetingDialog.fxml. Không thể mở dialog tham gia cuộc họp.");
                return;
            }

            // Load dialog từ FXML và ủy quyền join cho callback
            FXMLLoader loader = new FXMLLoader(url);
            DialogPane dialogPane = loader.load();

            JoinMeetingDialogController controller = loader.getController();
            controller.setOnJoin(meetingId -> {
                // Lưu lịch sử tham gia cuộc họp cho user hiện tại với role = participant
                try (SQLiteConnection db = new SQLiteConnection()) {
                    db.createTables();
                    String title = "Cuộc họp " + meetingId;
                    db.saveMeetingHistory(userId, meetingId, title, "participant");
                } catch (Exception e) {
                    e.printStackTrace();
                    System.err.println("❌ Lỗi khi lưu meeting history cho participant: " + e.getMessage());
                }

                if (joinMeetingHandler != null) {
                    // Gửi mã cuộc họp về App qua callback
                    joinMeetingHandler.accept(meetingId);
                }
            });

            Dialog<Void> dialog = new Dialog<>();
            dialog.setDialogPane(dialogPane);
            dialog.setTitle("Tham gia cuộc họp");
            dialog.showAndWait();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}
