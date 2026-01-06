package com.example.dacs4.network;

import com.example.dacs4.models.MessageType;
import com.example.dacs4.models.P2PMessage;
import com.example.dacs4.models.PeerInfo;

import java.io.IOException;
import java.net.InetAddress;
import java.util.*;
import java.util.function.Consumer;

/**
 * P2PManager - Coordinator for peer-to-peer networking
 * Delegates responsibilities to specialized service classes
 */
public class P2PManager {

    // Delegate to specialized managers
    private final PeerStateManager peerStateManager = new PeerStateManager();
    private final P2PConnectionManager connectionManager = new P2PConnectionManager(this);
    private final MeetingDiscoveryService discoveryService = new MeetingDiscoveryService();
    private final PeerListManager peerListManager = new PeerListManager(peerStateManager, connectionManager);
    private final MeetingLifecycleManager lifecycleManager;

    // Event listeners
    private List<Consumer<P2PMessage>> messageListeners = new ArrayList<>();

    public P2PManager() {
        // Initialize lifecycle manager with dependencies
        this.lifecycleManager = new MeetingLifecycleManager(discoveryService, connectionManager, this);
    }

    // ===== Meeting Lifecycle Methods =====

    /**
     * Create a new meeting (as host)
     */
    public void createMeeting(String meetingId, String userId, String userName, int port) throws IOException {
        lifecycleManager.createMeeting(meetingId, userId, userName, port);
    }

    /**
     * Join an existing meeting (as participant)
     */
    public void joinMeeting(String meetingId, String userId, String userName, int myPort) throws IOException {
        lifecycleManager.joinMeeting(meetingId, userId, userName, myPort);
    }

    /**
     * Stop P2P manager and close all connections
     * This is called when leaving a meeting (NOT the same as ending a meeting)
     */
    public void stop() {
        System.out.println("🛑 Stopping P2PManager...");

        // Leave meeting (stops announcements, unregisters, etc.)
        lifecycleManager.leaveMeeting();

        // Shutdown connection manager (closes all connections)
        connectionManager.shutdown();

        // Clear peer state
        peerStateManager.clearPeers();

        System.out.println("✅ P2PManager stopped");
    }

    /**
     * End the meeting (host only)
     * This broadcasts MEETING_ENDED to all participants and marks the meeting as
     * closed
     */
    public void endMeeting() {
        lifecycleManager.endMeeting();

        // Also stop everything
        connectionManager.shutdown();
        peerStateManager.clearPeers();
    }

    // ===== Connection Methods =====

    /**
     * Connect to a specific peer
     */
    public void connectToPeer(String ip, int port) throws IOException {
        connectionManager.connectToPeer(ip, port);
    }

    public int getListeningPort() {
        return lifecycleManager.getServerPort();
    }

    // ===== Messaging Methods =====

    /**
     * Broadcast message to all connected peers
     */
    public void broadcast(P2PMessage message) {
        message.setFrom(getCurrentUserId());
        message.setMeetingId(getCurrentMeetingId());
        message.setTimestamp(System.currentTimeMillis());

        var allConnections = connectionManager.getConnections();
        System.out.println("📤 Broadcasting message: " + message.getType() + " to " + allConnections.size() + " peers");

        for (PeerConnection conn : allConnections.values()) {
            if (conn.isConnected()) {
                conn.sendMessage(message);
            }
        }
    }

    /**
     * Send message to specific peer
     */
    public void sendToPeer(String peerId, P2PMessage message) {
        PeerConnection conn = connectionManager.getConnection(peerId);
        if (conn != null && conn.isConnected()) {
            message.setFrom(getCurrentUserId());
            message.setMeetingId(getCurrentMeetingId());
            message.setTimestamp(System.currentTimeMillis());
            conn.sendMessage(message);
        } else {
            System.err.println("❌ Cannot send to peer " + peerId + ": not connected");
        }
    }

    /**
     * Handle incoming message
     */
    public void handleMessage(P2PMessage message, PeerConnection fromConnection) {
        System.out.println("📥 Received message: " + message.getType() + " from " + message.getFrom());

        // Handle special message types
        switch (message.getType()) {
            case REQUEST_PEER_LIST:
                // Only host should answer to avoid clients receiving multiple peer lists
                if (lifecycleManager.isHost()) {
                    String myIp = getLocalIp();
                    peerListManager.sendPeerList(fromConnection, getCurrentUserId(),
                            getCurrentUserName(), myIp, getServerPort());
                }
                break;

            case PEER_LIST_RESPONSE:
                peerListManager.handlePeerListResponse(message, getCurrentUserId());

                // Also forward to listeners so UI can refresh participant list/count
                for (Consumer<P2PMessage> listener : messageListeners) {
                    listener.accept(message);
                }
                break;

            default:
                // Notify listeners
                for (Consumer<P2PMessage> listener : messageListeners) {
                    listener.accept(message);
                }
                break;
        }
    }

    // ===== Peer Connection Callbacks =====

    /**
     * Called when a new peer connects
     */
    public void onPeerConnected(String peerId, String peerName, PeerConnection connection) {
        System.out.println("🔔 DEBUG: onPeerConnected() called - peerId=" + peerId + ", peerName=" + peerName);

        // Add connection to connection manager
        connectionManager.addConnection(peerId, connection);
        System.out.println("🔗 DEBUG: Added connection to connectionManager for peer: " + peerId);

        // Create and add peer info to state manager
        PeerInfo peerInfo = new PeerInfo();
        peerInfo.setUserId(peerId);
        peerInfo.setUserName(peerName);
        peerInfo.setIpAddress(connection != null ? connection.getRemoteIpAddress() : null);
        peerInfo.setPort(connection != null ? connection.getPeerListenPort() : 0);
        peerStateManager.addPeer(peerId, peerInfo);
        System.out.println("👤 DEBUG: Added peer to peerStateManager: " + peerName + " (" + peerId + ")");
        System.out.println("📊 DEBUG: Total peers in PeerStateManager: " + peerStateManager.getPeerCount());

        System.out.println("✅ Peer connected: " + peerName + " (" + peerId + ")");

        // Broadcast USER_JOINED to OTHER peers (not the new peer itself)
        P2PMessage joinMessage = new P2PMessage(MessageType.USER_JOINED, getCurrentUserId(), "all");
        joinMessage.addPayload("userId", peerId);
        joinMessage.addPayload("userName", peerName);
        joinMessage.setMeetingId(getCurrentMeetingId());
        joinMessage.setTimestamp(System.currentTimeMillis());

        // Send to all peers EXCEPT the one that just joined
        var allConnections = connectionManager.getConnections();
        for (Map.Entry<String, PeerConnection> entry : allConnections.entrySet()) {
            if (!entry.getKey().equals(peerId) && entry.getValue().isConnected()) {
                entry.getValue().sendMessage(joinMessage);
            }
        }

        System.out.println("📤 Sent USER_JOINED to " + (allConnections.size() - 1) + " existing peers");

        // Host pushes updated peer list to all peers so mesh can self-heal quickly
        if (lifecycleManager.isHost()) {
            String myIp = getLocalIp();
            for (PeerConnection conn : allConnections.values()) {
                if (conn != null && conn.isConnected()) {
                    peerListManager.sendPeerList(conn, getCurrentUserId(),
                            getCurrentUserName(), myIp, getServerPort());
                }
            }
        }
    }

    /**
     * Called when a peer disconnects
     */
    public void onPeerDisconnected(String peerId) {
        connectionManager.removeConnection(peerId);
        peerStateManager.removePeer(peerId);

        System.out.println("❌ Peer disconnected: " + peerId);

        // Broadcast USER_LEFT to other peers
        P2PMessage leftMessage = new P2PMessage(MessageType.USER_LEFT, getCurrentUserId(), "all");
        leftMessage.addPayload("userId", peerId);
        broadcast(leftMessage);
    }

    // ===== Event Listener Management =====

    public void addPeerJoinedListener(Consumer<PeerInfo> listener) {
        peerStateManager.addPeerJoinedListener(listener);
    }

    public void addPeerLeftListener(Consumer<String> listener) {
        peerStateManager.addPeerLeftListener(listener);
    }

    public void addMessageListener(Consumer<P2PMessage> listener) {
        messageListeners.add(listener);
    }

    // ===== Getters =====

    public String getCurrentUserId() {
        return lifecycleManager.getCurrentUserId();
    }

    public String getCurrentUserName() {
        return lifecycleManager.getCurrentUserName();
    }

    public String getCurrentMeetingId() {
        return lifecycleManager.getCurrentMeetingId();
    }

    public int getServerPort() {
        return lifecycleManager.getServerPort();
    }

    public Map<String, PeerInfo> getPeers() {
        return peerStateManager.getPeers();
    }

    public void setPeerVideoOn(String peerId, boolean videoOn) {
        PeerInfo peer = peerStateManager.getPeerInfo(peerId);
        if (peer != null) {
            peer.setVideoOn(videoOn);
            peerStateManager.updatePeerInfo(peerId, peer);
        }
    }

    public void setPeerAudioOn(String peerId, boolean audioOn) {
        PeerInfo peer = peerStateManager.getPeerInfo(peerId);
        if (peer != null) {
            peer.setAudioOn(audioOn);
            peerStateManager.updatePeerInfo(peerId, peer);
        }
    }

    public int getConnectionCount() {
        return connectionManager.getConnectionCount();
    }

    private String getLocalIp() {
        try {
            return InetAddress.getLocalHost().getHostAddress();
        } catch (Exception e) {
            return "127.0.0.1";
        }
    }
}
