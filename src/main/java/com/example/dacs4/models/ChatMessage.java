package com.example.dacs4.models;

import java.time.LocalDateTime;

public class ChatMessage {



    public enum Type {
        TEXT,
        FILE
    }

    private String id;
    private String senderId;
    private String senderName;
    private String text;
    private LocalDateTime timestamp;
    private Type type;
    private String fileName;

    public ChatMessage(String id, String senderId, String senderName,
            String text, LocalDateTime timestamp,
            Type type, String fileName) {
        this.id = id;
        this.senderId = senderId;
        this.senderName = senderName;
        this.text = text;
        this.timestamp = timestamp;
        this.type = type;
        this.fileName = fileName;
    }

    // Simplified constructor for text messages
    public ChatMessage(String senderId, String senderName, String text, LocalDateTime timestamp) {
        this(null, senderId, senderName, text, timestamp, Type.TEXT, null);
    }

    // GETTERS ↓
    public String getId() {
        return id;
    }

    public String getSenderId() {
        return senderId;
    }

    public String getSenderName() {
        return senderName;
    }

    public String getText() {
        return text;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public Type getType() {
        return type;
    }

    public String getFileName() {
        return fileName;
    }
}
