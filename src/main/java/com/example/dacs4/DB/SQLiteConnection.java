package com.example.dacs4.DB;

import com.example.dacs4.models.ChatMessage;
import com.example.dacs4.models.MeetingHistory;
import com.example.dacs4.utils.PasswordHasher;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class SQLiteConnection implements AutoCloseable {
    private static final String URL = "jdbc:sqlite:meeting_app.db";
    private Connection connection;

    public SQLiteConnection() throws SQLException {
        this.connection = DriverManager.getConnection(URL);
        System.out.println("Kết nối đến cơ sở dữ liệu thành công!");
    }

    // ============================================================
    // CREATE TABLES - Updated Schema
    // ============================================================

    public void createTables() {
        // Users table - thêm email
        String userTableQuery = "CREATE TABLE IF NOT EXISTS users (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "username TEXT NOT NULL, " +
                "email TEXT UNIQUE NOT NULL, " +
                "password TEXT NOT NULL);";

        // Messages table - thêm meeting_id và sender_name
        String messageTableQuery = "CREATE TABLE IF NOT EXISTS messages (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "meeting_id TEXT, " +
                "sender_id TEXT, " +
                "sender_name TEXT, " +
                "content TEXT, " +
                "timestamp DATETIME DEFAULT CURRENT_TIMESTAMP);";

        // Files table - thêm meeting_id
        String fileTableQuery = "CREATE TABLE IF NOT EXISTS files (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "meeting_id TEXT, " +
                "sender_id TEXT, " +
                "sender_name TEXT, " +
                "file_path TEXT, " +
                "file_name TEXT, " +
                "timestamp DATETIME DEFAULT CURRENT_TIMESTAMP);";

        // Meeting history table - NEW
        String meetingHistoryQuery = "CREATE TABLE IF NOT EXISTS meeting_history (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "user_id TEXT NOT NULL, " +
                "meeting_id TEXT NOT NULL, " +
                "meeting_title TEXT, " +
                "role TEXT, " + // 'creator' or 'participant'
                "joined_at DATETIME DEFAULT CURRENT_TIMESTAMP, " +
                "last_accessed DATETIME DEFAULT CURRENT_TIMESTAMP, " +
                "is_ended BOOLEAN DEFAULT 0);";

        // Meeting participants table - NEW
        String meetingParticipantsQuery = "CREATE TABLE IF NOT EXISTS meeting_participants (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "meeting_id TEXT NOT NULL, " +
                "user_id TEXT NOT NULL, " +
                "user_name TEXT, " +
                "role TEXT, " +
                "is_audio_on BOOLEAN DEFAULT 1, " +
                "is_video_on BOOLEAN DEFAULT 1, " +
                "is_screen_sharing BOOLEAN DEFAULT 0, " +
                "joined_at DATETIME DEFAULT CURRENT_TIMESTAMP);";

        // Active meetings table - For P2P discovery
        String activeMeetingsQuery = "CREATE TABLE IF NOT EXISTS active_meetings (" +
                "meeting_id TEXT PRIMARY KEY, " +
                "host_ip TEXT NOT NULL, " +
                "host_port INTEGER NOT NULL, " +
                "created_at DATETIME DEFAULT CURRENT_TIMESTAMP);";

        try (Statement stmt = connection.createStatement()) {
            stmt.execute(userTableQuery);
            stmt.execute(messageTableQuery);
            stmt.execute(fileTableQuery);
            stmt.execute(meetingHistoryQuery);
            stmt.execute(meetingParticipantsQuery);
            stmt.execute(activeMeetingsQuery);
            System.out.println("✅ Tất cả bảng đã được tạo thành công!");
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // ============================================================
    // USER AUTHENTICATION
    // ============================================================

    /**
     * Đăng ký user mới với password hashing
     */
    public int registerUser(String username, String email, String password) throws SQLException {
        // Hash password trước khi lưu
        String hashedPassword = PasswordHasher.hash(password);

        String insertQuery = "INSERT INTO users (username, email, password) VALUES (?, ?, ?)";
        try (PreparedStatement pstmt = connection.prepareStatement(insertQuery, Statement.RETURN_GENERATED_KEYS)) {
            pstmt.setString(1, username);
            pstmt.setString(2, email);
            pstmt.setString(3, hashedPassword);
            pstmt.executeUpdate();

            // Lấy ID vừa tạo
            ResultSet rs = pstmt.getGeneratedKeys();
            if (rs.next()) {
                return rs.getInt(1);
            }
            throw new SQLException("Failed to get user ID");
        }
    }

    /**
     * Xác thực user khi đăng nhập
     * 
     * @return User ID nếu thành công, -1 nếu thất bại
     */
    public int authenticateUser(String email, String password) throws SQLException {
        String query = "SELECT id, password FROM users WHERE email = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(query)) {
            pstmt.setString(1, email);
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                String storedHash = rs.getString("password");
                if (PasswordHasher.verify(password, storedHash)) {
                    return rs.getInt("id");
                }
            }
            return -1; // Authentication failed
        }
    }

    /**
     * Lấy thông tin user theo ID
     */
    public ResultSet getUserById(int userId) throws SQLException {
        String query = "SELECT * FROM users WHERE id = ?";
        PreparedStatement pstmt = connection.prepareStatement(query);
        pstmt.setInt(1, userId);
        return pstmt.executeQuery();
    }

    /**
     * Lấy thông tin user theo email
     */
    public ResultSet getUserByEmail(String email) throws SQLException {
        String query = "SELECT * FROM users WHERE email = ?";
        PreparedStatement pstmt = connection.prepareStatement(query);
        pstmt.setString(1, email);
        return pstmt.executeQuery();
    }

    // ============================================================
    // MEETING HISTORY
    // ============================================================

    /**
     * Lưu meeting vào history
     */
    public void saveMeetingHistory(String userId, String meetingId, String title, String role) throws SQLException {
        // Kiểm tra xem đã tồn tại chưa
        String checkQuery = "SELECT id FROM meeting_history WHERE user_id = ? AND meeting_id = ?";
        try (PreparedStatement checkStmt = connection.prepareStatement(checkQuery)) {
            checkStmt.setString(1, userId);
            checkStmt.setString(2, meetingId);
            ResultSet rs = checkStmt.executeQuery();

            if (rs.next()) {
                // Đã tồn tại -> update last_accessed
                updateLastAccessed(userId, meetingId);
                return;
            }
        }

        // Chưa tồn tại -> insert mới
        String insertQuery = "INSERT INTO meeting_history (user_id, meeting_id, meeting_title, role) VALUES (?, ?, ?, ?)";
        try (PreparedStatement pstmt = connection.prepareStatement(insertQuery)) {
            pstmt.setString(1, userId);
            pstmt.setString(2, meetingId);
            pstmt.setString(3, title);
            pstmt.setString(4, role);
            pstmt.executeUpdate();
        }
    }

    /**
     * Lấy danh sách meeting history của user
     */
    public List<MeetingHistory> getMeetingHistory(String userId) throws SQLException {
        List<MeetingHistory> meetings = new ArrayList<>();
        String query = "SELECT * FROM meeting_history WHERE user_id = ? ORDER BY last_accessed DESC";

        try (PreparedStatement pstmt = connection.prepareStatement(query)) {
            pstmt.setString(1, userId);
            ResultSet rs = pstmt.executeQuery();

            while (rs.next()) {
                MeetingHistory meeting = new MeetingHistory();
                meeting.setId(rs.getInt("id"));
                meeting.setUserId(rs.getString("user_id"));
                meeting.setMeetingId(rs.getString("meeting_id"));
                meeting.setMeetingTitle(rs.getString("meeting_title"));
                meeting.setRole(rs.getString("role"));
                meeting.setJoinedAt(rs.getString("joined_at"));
                meeting.setLastAccessed(rs.getString("last_accessed"));
                meeting.setEnded(rs.getBoolean("is_ended"));
                meetings.add(meeting);
            }
        }
        return meetings;
    }

    /**
     * Cập nhật thời gian truy cập cuối
     */
    public void updateLastAccessed(String userId, String meetingId) throws SQLException {
        String query = "UPDATE meeting_history SET last_accessed = CURRENT_TIMESTAMP WHERE user_id = ? AND meeting_id = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(query)) {
            pstmt.setString(1, userId);
            pstmt.setString(2, meetingId);
            pstmt.executeUpdate();
        }
    }

    /**
     * Đánh dấu meeting đã kết thúc
     */
    public void endMeeting(String meetingId) throws SQLException {
        String query = "UPDATE meeting_history SET is_ended = 1 WHERE meeting_id = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(query)) {
            pstmt.setString(1, meetingId);
            pstmt.executeUpdate();
        }
    }

    // ============================================================
    // MEETING PARTICIPANTS
    // ============================================================

    /**
     * Lưu thông tin participant vào meeting
     */
    public void saveMeetingParticipant(String meetingId, String userId, String userName, String role)
            throws SQLException {
        String insertQuery = "INSERT INTO meeting_participants (meeting_id, user_id, user_name, role) VALUES (?, ?, ?, ?)";
        try (PreparedStatement pstmt = connection.prepareStatement(insertQuery)) {
            pstmt.setString(1, meetingId);
            pstmt.setString(2, userId);
            pstmt.setString(3, userName);
            pstmt.setString(4, role);
            pstmt.executeUpdate();
        }
    }

    /**
     * Lấy danh sách participants trong meeting
     */
    public ResultSet getMeetingParticipants(String meetingId) throws SQLException {
        String query = "SELECT * FROM meeting_participants WHERE meeting_id = ?";
        PreparedStatement pstmt = connection.prepareStatement(query);
        pstmt.setString(1, meetingId);
        return pstmt.executeQuery();
    }

    // ============================================================
    // MESSAGES - Updated với meeting_id
    // ============================================================

    /**
     * Gửi tin nhắn trong meeting
     */
    public void sendMessage(String meetingId, String senderId, String senderName, String content) throws SQLException {
        String insertQuery = "INSERT INTO messages (meeting_id, sender_id, sender_name, content) VALUES (?, ?, ?, ?)";
        try (PreparedStatement pstmt = connection.prepareStatement(insertQuery)) {
            pstmt.setString(1, meetingId);
            pstmt.setString(2, senderId);
            pstmt.setString(3, senderName);
            pstmt.setString(4, content);
            pstmt.executeUpdate();
        }
    }

    /**
     * Lấy tất cả messages trong một meeting
     */
    public List<ChatMessage> getMessagesByMeeting(String meetingId) throws SQLException {
        List<ChatMessage> messages = new ArrayList<>();
        String query = "SELECT * FROM messages WHERE meeting_id = ? ORDER BY timestamp ASC";

        try (PreparedStatement pstmt = connection.prepareStatement(query)) {
            pstmt.setString(1, meetingId);
            ResultSet rs = pstmt.executeQuery();

            while (rs.next()) {
                ChatMessage msg = new ChatMessage(
                        rs.getString("sender_id"),
                        rs.getString("sender_name"),
                        rs.getString("content"),
                        LocalDateTime.parse(rs.getString("timestamp").replace(" ", "T")));
                messages.add(msg);
            }
        }
        return messages;
    }

    // ============================================================
    // FILES - Updated với meeting_id
    // ============================================================

    /**
     * Lưu file được share trong meeting
     */
    public void sendFile(String meetingId, String senderId, String senderName, String filePath, String fileName)
            throws SQLException {
        String insertQuery = "INSERT INTO files (meeting_id, sender_id, sender_name, file_path, file_name) VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement pstmt = connection.prepareStatement(insertQuery)) {
            pstmt.setString(1, meetingId);
            pstmt.setString(2, senderId);
            pstmt.setString(3, senderName);
            pstmt.setString(4, filePath);
            pstmt.setString(5, fileName);
            pstmt.executeUpdate();
        }
    }

    /**
     * Lấy tất cả files trong một meeting
     */
    public ResultSet getFilesByMeeting(String meetingId) throws SQLException {
        String query = "SELECT * FROM files WHERE meeting_id = ? ORDER BY timestamp DESC";
        PreparedStatement pstmt = connection.prepareStatement(query);
        pstmt.setString(1, meetingId);
        return pstmt.executeQuery();
    }

    // ============================================================
    // ACTIVE MEETINGS - For P2P Discovery
    // ============================================================

    /**
     * Register an active meeting with host information
     */
    public void registerActiveMeeting(String meetingId, String hostIp, int hostPort) throws SQLException {
        String insertQuery = "INSERT OR REPLACE INTO active_meetings (meeting_id, host_ip, host_port) VALUES (?, ?, ?)";
        try (PreparedStatement pstmt = connection.prepareStatement(insertQuery)) {
            pstmt.setString(1, meetingId);
            pstmt.setString(2, hostIp);
            pstmt.setInt(3, hostPort);
            pstmt.executeUpdate();
        }
    }

    /**
     * Get host information for a meeting
     */
    public ResultSet getActiveMeeting(String meetingId) throws SQLException {
        String query = "SELECT * FROM active_meetings WHERE meeting_id = ?";
        PreparedStatement pstmt = connection.prepareStatement(query);
        pstmt.setString(1, meetingId);
        return pstmt.executeQuery();
    }

    /**
     * Unregister an active meeting
     */
    public void unregisterActiveMeeting(String meetingId) throws SQLException {
        String deleteQuery = "DELETE FROM active_meetings WHERE meeting_id = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(deleteQuery)) {
            pstmt.setString(1, meetingId);
            pstmt.executeUpdate();
        }
    }

    // ============================================================
    // UTILITY
    // ============================================================

    /**
     * Đóng kết nối
     */
    @Override
    public void close() throws SQLException {
        if (connection != null) {
            connection.close();
        }
    }

    /**
     * Lấy connection (cho các trường hợp đặc biệt)
     */
    public Connection getConnection() {
        return connection;
    }
}
