package com.example.dacs4.services;

import com.example.dacs4.DB.SQLiteConnection;

import java.sql.SQLException;

public class PeerService {
    private SQLiteConnection db;

    // Constructor nhận đối số động, tránh hardcoding thông tin database
    public PeerService(SQLiteConnection db) {
        this.db = db;
    }

    // Đăng ký người dùng mới
    public void registerPeer(String username, String email, String password) throws SQLException {
        // Gọi registerUser với đầy đủ 3 tham số: username, email, password
        db.registerUser(username, email, password);
    }

    // Đăng nhập người dùng - trả về userId
    public int loginPeer(String email, String password) throws SQLException {
        // authenticateUser trả về int (userId), không phải Participant
        return db.authenticateUser(email, password);
    }
}
