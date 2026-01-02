package com.example.dacs4.models;

public class PeerInfo {
    private String userId;
    private String userName;
    private String ipAddress;
    private int port;
    private boolean isAudioOn;
    private boolean isVideoOn;
    private boolean isScreenSharing;

    public PeerInfo() {
    }

    public PeerInfo(String userId, String userName, String ipAddress, int port) {
        this.userId = userId;
        this.userName = userName;
        this.ipAddress = ipAddress;
        this.port = port;
        this.isAudioOn = true;
        this.isVideoOn = true;
        this.isScreenSharing = false;
    }

    // Getters and Setters
    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public boolean isAudioOn() {
        return isAudioOn;
    }

    public void setAudioOn(boolean audioOn) {
        isAudioOn = audioOn;
    }

    public boolean isVideoOn() {
        return isVideoOn;
    }

    public void setVideoOn(boolean videoOn) {
        isVideoOn = videoOn;
    }

    public boolean isScreenSharing() {
        return isScreenSharing;
    }

    public void setScreenSharing(boolean screenSharing) {
        isScreenSharing = screenSharing;
    }

    @Override
    public String toString() {
        return "PeerInfo{" +
                "userId='" + userId + '\'' +
                ", userName='" + userName + '\'' +
                ", ipAddress='" + ipAddress + '\'' +
                ", port=" + port +
                ", isAudioOn=" + isAudioOn +
                ", isVideoOn=" + isVideoOn +
                ", isScreenSharing=" + isScreenSharing +
                '}';
    }
}
