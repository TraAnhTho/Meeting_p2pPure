package com.example.dacs4.services;

import com.example.dacs4.DB.SQLiteConnection;
import com.example.dacs4.models.MeetingHistory;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Manager for rendering and updating meeting list UI
 */
public class MeetingListManager {

    private final VBox meetingsContainer;
    private final Map<String, Label> participantsLabelsByMeetingId = new ConcurrentHashMap<>();
    private String lastCopiedMeetingId = null;

    // Callbacks
    private Consumer<String> onJoinMeeting;
    private Consumer<String> onEndMeeting;

    // Meeting model
    private static class Meeting {
        String id;
        String title;
        String time;
        int participants;

        Meeting(String id, String title, String time, int participants) {
            this.id = id;
            this.title = title;
            this.time = time;
            this.participants = participants;
        }
    }

    public MeetingListManager(VBox meetingsContainer) {
        this.meetingsContainer = meetingsContainer;
    }

    public void setOnJoinMeeting(Consumer<String> callback) {
        this.onJoinMeeting = callback;
    }

    public void setOnEndMeeting(Consumer<String> callback) {
        this.onEndMeeting = callback;
    }

    /**
     * Render meeting list from database
     */
    public void renderMeetings(String userId) {
        System.out.println("🔄 renderMeetings() called - userId: " + userId);
        meetingsContainer.getChildren().clear();
        participantsLabelsByMeetingId.clear();

        try (SQLiteConnection db = new SQLiteConnection()) {
            db.createTables();
            List<MeetingHistory> historyList = db.getMeetingHistory(userId);
            System.out.println("📊 Loaded " + historyList.size() + " meetings from database");

            if (historyList.isEmpty()) {
                Label emptyLabel = new Label("Chưa có cuộc họp nào. Tạo cuộc họp mới để bắt đầu!");
                emptyLabel.setStyle("-fx-text-fill: #9ca3af; -fx-padding: 20;");
                meetingsContainer.getChildren().add(emptyLabel);
                System.out.println("📭 No meetings to display");
                return;
            }

            // Render meetings
            int displayedCount = 0;
            for (MeetingHistory history : historyList) {
                if (!history.isEnded()) {
                    Meeting meeting = new Meeting(
                            history.getMeetingId(),
                            history.getMeetingTitle(),
                            "Lần truy cập cuối: " + history.getLastAccessed(),
                            0);
                    meetingsContainer.getChildren().add(createMeetingCard(meeting, history.getRole()));
                    displayedCount++;
                    System.out.println("  ✅ Displaying: " + history.getMeetingId() + " - " + history.getMeetingTitle()
                            + " (Role: " + history.getRole() + ")");
                } else {
                    System.out.println("  ⏭️ Skipping ended meeting: " + history.getMeetingId());
                }
            }
            System.out.println("📋 Displayed " + displayedCount + " active meetings");
        } catch (SQLException e) {
            e.printStackTrace();
            System.err.println("❌ Error loading meetings: " + e.getMessage());
            Label errorLabel = new Label("Lỗi khi tải danh sách cuộc họp: " + e.getMessage());
            errorLabel.setStyle("-fx-text-fill: #ef4444; -fx-padding: 20;");
            meetingsContainer.getChildren().add(errorLabel);
        }
    }

    /**
     * Update participant count for a meeting
     */
    public void updateParticipantCount(String meetingId, int count) {
        Label label = participantsLabelsByMeetingId.get(meetingId);
        if (label != null) {
            Platform.runLater(() -> label.setText("👥 " + count + " người tham gia"));
        }
    }

    /**
     * Create a meeting card UI component
     */
    private Pane createMeetingCard(Meeting meeting, String role) {
        VBox card = new VBox();
        card.getStyleClass().add("meeting-card");
        card.setSpacing(8);

        // Title with role badge
        HBox titleRow = new HBox(8);
        Label titleLabel = new Label(meeting.title);
        titleLabel.getStyleClass().add("meeting-title");

        Label roleLabel = new Label(role.equals("creator") ? "👑 Host" : "👤 Participant");
        roleLabel.setStyle("-fx-background-color: " + (role.equals("creator") ? "#4f46e5" : "#6b7280") +
                "; -fx-text-fill: white; -fx-padding: 2 8; -fx-background-radius: 4; -fx-font-size: 10;");
        titleRow.getChildren().addAll(titleLabel, roleLabel);

        Label timeLabel = new Label("⏰ " + meeting.time);
        Label participantsLabel = new Label("👥 " + meeting.participants + " người tham gia");

        participantsLabelsByMeetingId.put(meeting.id, participantsLabel);

        HBox infoRow = new HBox(16, timeLabel, participantsLabel);
        infoRow.getStyleClass().add("meeting-info-row");

        // Copy ID button
        Button copyButton = new Button(meeting.id);
        copyButton.getStyleClass().add("copy-id-button");
        copyButton.setOnAction(e -> copyMeetingId(meeting.id, copyButton));

        // Join button
        Button joinButton = new Button("Tham gia");
        joinButton.getStyleClass().add("primary-button");
        joinButton.setOnAction(e -> {
            if (onJoinMeeting != null) {
                onJoinMeeting.accept(meeting.id);
            }
        });

        // End button (for creator only)
        Button endButton = null;
        if ("creator".equals(role)) {
            endButton = new Button("Kết thúc");
            endButton.getStyleClass().add("danger-button");
            Button finalEndButton = endButton;
            endButton.setOnAction(e -> {
                Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
                confirm.setTitle("Kết thúc cuộc họp");
                confirm.setHeaderText("Bạn có chắc muốn kết thúc cuộc họp " + meeting.id + "?");
                confirm.setContentText("Tất cả người tham gia sẽ thấy cuộc họp đã kết thúc.");
                Optional<ButtonType> r = confirm.showAndWait();
                if (r.isPresent() && r.get() == ButtonType.OK) {
                    if (onEndMeeting != null) {
                        onEndMeeting.accept(meeting.id);
                    }
                    finalEndButton.setDisable(true);
                }
            });
        }

        HBox bottomRow = new HBox(16);
        if (endButton != null) {
            bottomRow.getChildren().addAll(copyButton, joinButton, endButton);
        } else {
            bottomRow.getChildren().addAll(copyButton, joinButton);
        }
        bottomRow.getStyleClass().add("meeting-bottom-row");

        card.getChildren().addAll(titleRow, infoRow, bottomRow);
        return card;
    }

    /**
     * Copy meeting ID to clipboard
     */
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
}
