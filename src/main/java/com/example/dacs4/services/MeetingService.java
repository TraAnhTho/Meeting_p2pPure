package com.example.dacs4.services;

import com.example.dacs4.DB.SQLiteConnection;
import javafx.scene.control.Alert;
import javafx.scene.control.TextInputDialog;

import java.sql.SQLException;
import java.util.Optional;
import java.util.Random;

/**
 * Service class for meeting-related business logic
 */
public class MeetingService {

    /**
     * Create a new meeting with a generated ID
     * 
     * @param userId       User ID of the creator
     * @param meetingTitle Title of the meeting
     * @return Generated meeting ID, or null if creation failed
     */
    public String createMeeting(String userId, String meetingTitle) {
        if (userId == null || userId.isEmpty()) {
            System.err.println("❌ userId is null or empty, cannot create meeting");
            showErrorAlert("Lỗi", "Không thể tạo cuộc họp", "Vui lòng đăng nhập lại.");
            return null;
        }

        // Generate meeting ID
        String meetingId = generateMeetingId();
        System.out.println("🎲 Generated meeting ID: " + meetingId);

        // Save to database
        try (SQLiteConnection db = new SQLiteConnection()) {
            db.createTables();
            db.saveMeetingHistory(userId, meetingId, meetingTitle, "creator");
            System.out.println("✅ Đã lưu meeting mới: " + meetingId + " - " + meetingTitle);
            return meetingId;
        } catch (SQLException e) {
            e.printStackTrace();
            System.err.println("❌ Lỗi khi lưu meeting history: " + e.getMessage());
            showErrorAlert("Lỗi cơ sở dữ liệu",
                    "Không thể lưu cuộc họp",
                    "Chi tiết: " + e.getMessage());
            return null;
        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("❌ Lỗi không mong đợi: " + e.getMessage());
            showErrorAlert("Lỗi",
                    "Đã xảy ra lỗi không mong đợi",
                    "Chi tiết: " + e.getMessage());
            return null;
        }
    }

    /**
     * Show dialog to create a new meeting
     * 
     * @param userId User ID of the creator
     * @return Meeting ID if created, null if cancelled or failed
     */
    public String showCreateMeetingDialog(String userId) {
        System.out.println("🔵 showCreateMeetingDialog() called - userId: " + userId);

        if (userId == null || userId.isEmpty()) {
            System.err.println("❌ userId is null or empty");
            showErrorAlert("Lỗi", "Không thể tạo cuộc họp", "Vui lòng đăng nhập lại.");
            return null;
        }

        // Show dialog
        TextInputDialog dialog = new TextInputDialog("Cuộc họp mới");
        dialog.setTitle("Tạo cuộc họp");
        dialog.setHeaderText("Bắt đầu cuộc họp ngay");
        dialog.setContentText("Nhập tên cuộc họp:");

        Optional<String> result = dialog.showAndWait();
        if (result.isPresent()) {
            String meetingTitle = result.get();
            System.out.println("📝 User entered meeting title: " + meetingTitle);
            return createMeeting(userId, meetingTitle);
        }

        return null;
    }

    /**
     * Save meeting history when joining as participant
     * 
     * @param userId    User ID
     * @param meetingId Meeting ID
     */
    public void joinMeeting(String userId, String meetingId) {
        try (SQLiteConnection db = new SQLiteConnection()) {
            db.createTables();
            String title = "Cuộc họp " + meetingId;
            db.saveMeetingHistory(userId, meetingId, title, "participant");
            System.out.println("✅ Saved meeting history for participant: " + meetingId);
        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("❌ Lỗi khi lưu meeting history cho participant: " + e.getMessage());
        }
    }

    /**
     * End a meeting and mark it as ended in database
     * 
     * @param meetingId Meeting ID to end
     */
    public void endMeeting(String meetingId) {
        if (meetingId == null || meetingId.isBlank()) {
            return;
        }

        try (SQLiteConnection db = new SQLiteConnection()) {
            db.createTables();
            db.endMeeting(meetingId);
            System.out.println("✅ Meeting ended: " + meetingId);
        } catch (Exception e) {
            System.err.println("❌ Lỗi khi kết thúc meeting: " + e.getMessage());
        }
    }

    /**
     * Generate a random meeting ID (5-6 characters)
     * 
     * @return Generated meeting ID
     */
    private String generateMeetingId() {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        Random random = new Random();
        int length = 5 + random.nextInt(2); // 5 or 6 characters

        StringBuilder meetingId = new StringBuilder();
        for (int i = 0; i < length; i++) {
            meetingId.append(chars.charAt(random.nextInt(chars.length())));
        }

        return meetingId.toString();
    }

    /**
     * Show error alert to user
     */
    private void showErrorAlert(String title, String header, String content) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(header);
        alert.setContentText(content);
        alert.showAndWait();
    }
}
