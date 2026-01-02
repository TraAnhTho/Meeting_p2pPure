package com.example.dacs4.models;

public class MeetingHistory {
    private int id;
    private String userId;
    private String meetingId;
    private String meetingTitle;
    private String role; // "creator" or "participant"
    private String joinedAt;
    private String lastAccessed;
    private boolean isEnded;

    // Constructors
    public MeetingHistory() {
    }

    public MeetingHistory(String userId, String meetingId, String meetingTitle, String role) {
        this.userId = userId;
        this.meetingId = meetingId;
        this.meetingTitle = meetingTitle;
        this.role = role;
    }

    // Getters and Setters
    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getMeetingId() {
        return meetingId;
    }

    public void setMeetingId(String meetingId) {
        this.meetingId = meetingId;
    }

    public String getMeetingTitle() {
        return meetingTitle;
    }

    public void setMeetingTitle(String meetingTitle) {
        this.meetingTitle = meetingTitle;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getJoinedAt() {
        return joinedAt;
    }

    public void setJoinedAt(String joinedAt) {
        this.joinedAt = joinedAt;
    }

    public String getLastAccessed() {
        return lastAccessed;
    }

    public void setLastAccessed(String lastAccessed) {
        this.lastAccessed = lastAccessed;
    }

    public boolean isEnded() {
        return isEnded;
    }

    public void setEnded(boolean ended) {
        isEnded = ended;
    }

    @Override
    public String toString() {
        return "MeetingHistory{" +
                "id=" + id +
                ", userId='" + userId + '\'' +
                ", meetingId='" + meetingId + '\'' +
                ", meetingTitle='" + meetingTitle + '\'' +
                ", role='" + role + '\'' +
                ", joinedAt='" + joinedAt + '\'' +
                ", lastAccessed='" + lastAccessed + '\'' +
                ", isEnded=" + isEnded +
                '}';
    }
}
