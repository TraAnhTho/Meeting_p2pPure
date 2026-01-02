package com.example.dacs4.network;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class ConnectionManager {
    private static final int MAX_RECONNECT_ATTEMPTS = 5;
    private static final int INITIAL_RETRY_DELAY_MS = 1000;
    private static final int MAX_RETRY_DELAY_MS = 30000;

    private P2PManager p2pManager;
    private ScheduledExecutorService scheduler;
    private String meetingId;
    private String userId;
    private String userName;
    private int myPort;
    private boolean isHost;

    private int reconnectAttempts = 0;
    private boolean isConnected = false;

    public ConnectionManager(P2PManager p2pManager) {
        this.p2pManager = p2pManager;
        this.scheduler = Executors.newScheduledThreadPool(1);
    }

    public void startHeartbeat() {
        scheduler.scheduleAtFixedRate(() -> {
            if (!checkConnection()) {
                handleDisconnection();
            }
        }, 5, 5, TimeUnit.SECONDS);
    }

    private boolean checkConnection() {
        if (p2pManager.getConnectionCount() == 0 && !isHost) {
            return false; // Participant không có connection nào
        }
        return true;
    }

    private void handleDisconnection() {
        if (isConnected) {
            isConnected = false;
            System.out.println("⚠️ Connection lost, attempting to reconnect...");
            attemptReconnect();
        }
    }

    private void attemptReconnect() {
        if (reconnectAttempts >= MAX_RECONNECT_ATTEMPTS) {
            System.err.println("❌ Max reconnect attempts reached");
            notifyConnectionFailed();
            return;
        }

        // Exponential backoff
        int delay = Math.min(
                INITIAL_RETRY_DELAY_MS * (int) Math.pow(2, reconnectAttempts),
                MAX_RETRY_DELAY_MS
        );

        reconnectAttempts++;
        System.out.println("🔄 Reconnect attempt " + reconnectAttempts + "/" +
                MAX_RECONNECT_ATTEMPTS + " in " + delay + "ms");

        scheduler.schedule(() -> {
            try {
                if (isHost) {
                    // Host: restart server
                    p2pManager.createMeeting(meetingId, userId, userName, myPort);
                } else {
                    // Participant: rejoin meeting
                    p2pManager.joinMeeting(meetingId, userId, userName, myPort);
                }

                isConnected = true;
                reconnectAttempts = 0;
                System.out.println("✅ Reconnected successfully");

            } catch (Exception e) {
                System.err.println("❌ Reconnect failed: " + e.getMessage());
                attemptReconnect(); // Retry
            }
        }, delay, TimeUnit.MILLISECONDS);
    }

    private void notifyConnectionFailed() {
        // TODO: Show alert to user
        System.err.println("❌ Unable to reconnect to meeting");
    }

    public void stop() {
        scheduler.shutdown();
    }

    public void setConnectionInfo(String meetingId, String userId, String userName,
                                  int myPort, boolean isHost) {
        this.meetingId = meetingId;
        this.userId = userId;
        this.userName = userName;
        this.myPort = myPort;
        this.isHost = isHost;
        this.isConnected = true;
    }
}

