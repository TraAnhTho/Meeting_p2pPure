package com.example.dacs4.network;

import com.example.dacs4.models.MessageType;
import com.example.dacs4.models.P2PMessage;

import java.io.IOException;
import java.net.InetAddress;

/**
 * Manages meeting lifecycle: creation, joining, leaving, and ending
 */
public class MeetingLifecycleManager {

    private String currentUserId;
    private String currentUserName;
    private String currentMeetingId;
    private int serverPort;
    private boolean isHost = false;

    private final MeetingDiscoveryService discoveryService;
    private final P2PConnectionManager connectionManager;
    private final P2PManager p2pManager; // For callbacks

    public MeetingLifecycleManager(MeetingDiscoveryService discoveryService,
            P2PConnectionManager connectionManager,
            P2PManager p2pManager) {
        this.discoveryService = discoveryService;
        this.connectionManager = connectionManager;
        this.p2pManager = p2pManager;
    }

    /**
     * Create a new meeting as host
     */
    public void createMeeting(String meetingId, String userId, String userName, int port) throws IOException {
        this.currentMeetingId = meetingId;
        this.currentUserId = userId;
        this.currentUserName = userName;
        this.isHost = true;

        // Start server with port retry logic
        this.serverPort = startServerWithRetry(port, 10);

        // Register meeting in database
        String myIp = getLocalIp();
        discoveryService.registerMeeting(meetingId, myIp, serverPort);

        // Start host announcements (multicast + LAN responder)
        // Note: connectionCount doesn't include self, so this will be 0 until someone
        // joins
        discoveryService.startHostAnnouncement(meetingId, myIp, serverPort,
                () -> 1 + connectionManager.getConnectionCount());

        System.out.println("🎯 HOST READY! Meeting: " + meetingId + " | IP: " + myIp + " | Port: " + serverPort);
        System.out.println("📢 Participants can now join using meeting code: " + meetingId);
    }

    /**
     * Join an existing meeting as participant
     */
    public void joinMeeting(String meetingId, String userId, String userName, int myPort) throws IOException {
        this.currentMeetingId = meetingId;
        this.currentUserId = userId;
        this.currentUserName = userName;
        this.isHost = false;

        // Start server for incoming connections with port retry logic
        this.serverPort = startServerWithRetry(myPort, 10);

        // Discover host using multi-strategy discovery
        MeetingRegistry.HostInfo host = discoveryService.discoverHost(meetingId);

        if (host == null) {
            throw new IOException("Meeting not found: " + meetingId);
        }

        // Try to connect to host
        boolean connected = false;
        try {
            System.out.println("🔗 Attempting to connect to host: " + host.ip + ":" + host.port);
            connectionManager.connectToPeer(host.ip, host.port);
            connected = true;
            System.out.println("✅ Successfully connected to meeting host!");
        } catch (IOException firstConnectError) {
            System.err.println("❌ Connection failed: " + firstConnectError.getMessage());

            // Check if it's a connection refused error
            String msg = firstConnectError.getMessage() != null ? firstConnectError.getMessage() : "";
            boolean refused = (firstConnectError instanceof java.net.ConnectException)
                    || msg.contains("Connection refused")
                    || msg.contains("refused");

            // If connection refused, try LAN discovery as a last resort
            if (refused) {
                System.out.println("🔄 Retrying with fresh LAN discovery...");
                MeetingRegistry.HostInfo discovered = discoveryService.discoverViaLan(meetingId, 3);

                if (discovered != null) {
                    host = discovered;
                    discoveryService.registerMeeting(meetingId, host.ip, host.port);
                    System.out.println("📡 Found host via LAN discovery: " + host.ip + ":" + host.port);
                    connectionManager.connectToPeer(host.ip, host.port);
                    connected = true;
                    System.out.println("✅ Successfully connected via LAN discovery!");
                } else {
                    System.err.println("❌ LAN discovery also failed");
                    throw firstConnectError;
                }
            } else {
                throw firstConnectError;
            }
        }

        if (connected) {
            System.out.println("✅ Joined meeting: " + meetingId);
        }
    }

    /**
     * Leave the current meeting (does NOT end the meeting)
     */
    public void leaveMeeting() {
        System.out.println("🚪 Leaving meeting: " + currentMeetingId);

        // Stop announcements if host
        if (isHost) {
            discoveryService.stopHostAnnouncement();

            // Unregister from database
            if (currentMeetingId != null) {
                discoveryService.unregisterMeeting(currentMeetingId);
            }
        }

        // Reset state
        currentMeetingId = null;
        currentUserId = null;
        currentUserName = null;
        isHost = false;
    }

    /**
     * End the meeting (host only) - broadcasts MEETING_ENDED and closes everything
     */
    public void endMeeting() {
        if (!isHost) {
            System.err.println("❌ Only host can end the meeting");
            return;
        }

        if (currentMeetingId == null) {
            System.err.println("❌ No active meeting to end");
            return;
        }

        System.out.println("🛑 Ending meeting: " + currentMeetingId);

        // Broadcast MEETING_ENDED to all participants
        try {
            P2PMessage endMessage = new P2PMessage(MessageType.MEETING_ENDED, currentUserId, "all");
            endMessage.setMeetingId(currentMeetingId);
            p2pManager.broadcast(endMessage);
            System.out.println("📤 Broadcasted MEETING_ENDED to all participants");
        } catch (Exception e) {
            System.err.println("❌ Failed to broadcast MEETING_ENDED: " + e.getMessage());
        }

        // Announce meeting closed via multicast
        discoveryService.announceMeetingClosed(currentMeetingId);

        // Leave the meeting (cleanup)
        leaveMeeting();
    }

    /**
     * Start server with automatic port retry on conflict
     */
    private int startServerWithRetry(int preferredPort, int maxAttempts) throws IOException {
        if (connectionManager.isRunning()) {
            System.out.println("⚠️ Server already running on port " + connectionManager.getServerPort());
            return connectionManager.getServerPort();
        }

        int attempts = 0;
        int tryPort = preferredPort;

        while (attempts < maxAttempts) {
            try {
                connectionManager.startServer(tryPort);
                return tryPort; // Success
            } catch (java.net.BindException e) {
                attempts++;
                tryPort = preferredPort + attempts; // Try next port
                System.out.println("⚠️ Port " + (tryPort - 1) + " in use, trying " + tryPort);

                if (attempts >= maxAttempts) {
                    throw new IOException("Could not find available port after " + maxAttempts + " attempts", e);
                }
            }
        }

        throw new IOException("Failed to start server");
    }

    /**
     * Get local IP address
     */
    private String getLocalIp() {
        try {
            return InetAddress.getLocalHost().getHostAddress();
        } catch (Exception e) {
            return "127.0.0.1";
        }
    }

    // ===== Getters =====

    public String getCurrentUserId() {
        return currentUserId;
    }

    public String getCurrentUserName() {
        return currentUserName;
    }

    public String getCurrentMeetingId() {
        return currentMeetingId;
    }

    public int getServerPort() {
        return serverPort;
    }

    public boolean isHost() {
        return isHost;
    }
}
