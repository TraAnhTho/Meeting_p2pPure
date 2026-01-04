package com.example.dacs4.network;

import com.example.dacs4.models.MessageType;
import com.example.dacs4.models.P2PMessage;
import com.example.dacs4.models.PeerInfo;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public class P2PManager {
    private String currentUserId;
    private String currentUserName;
    private String currentMeetingId;
    private int serverPort;

    private boolean isHost = false;

    private final LanMeetingDiscovery lanDiscovery = new LanMeetingDiscovery();
    private final LanMeetingMulticast mcast = new LanMeetingMulticast();

    private PeerServer server;
    private Map<String, PeerConnection> connections = new ConcurrentHashMap<>();
    private Map<String, PeerInfo> peers = new ConcurrentHashMap<>();

    // Event listeners
    private List<Consumer<PeerInfo>> peerJoinedListeners = new ArrayList<>();
    private List<Consumer<String>> peerLeftListeners = new ArrayList<>();
    private List<Consumer<P2PMessage>> messageListeners = new ArrayList<>();

    public P2PManager() {
    }

    /**
     * Start peer server on specified port
     */
    public void startServer(int port) throws IOException {
        if (server != null && server.isRunning()) {
            System.out.println("⚠️ Server already running on port " + serverPort);
            return;
        }

        this.serverPort = port;
        server = new PeerServer(port, this);
        new Thread(server, "PeerServer-" + port).start();
        System.out.println("🚀 P2PManager started on port " + port);
    }

    /**
     * Create a new meeting (as host)
     */
    public void createMeeting(String meetingId, String userId, String userName, int port) throws IOException {
        this.currentMeetingId = meetingId;
        this.currentUserId = userId;
        this.currentUserName = userName;
        this.isHost = true;

        // Start server if not already running, with retry logic for port conflicts
        if (server == null || !server.isRunning()) {
            int attempts = 0;
            int maxAttempts = 10;
            int tryPort = port;

            while (attempts < maxAttempts) {
                try {
                    startServer(tryPort);
                    break; // Success
                } catch (java.net.BindException e) {
                    attempts++;
                    tryPort = port + attempts; // Try next port
                    System.out.println("⚠️ Port " + (tryPort - 1) + " in use, trying " + tryPort);

                    if (attempts >= maxAttempts) {
                        throw new IOException("Could not find available port after " + maxAttempts + " attempts", e);
                    }
                }
            }
        }

        // Register meeting in registry
        String myIp = getLocalIp();
        MeetingRegistry.registerMeeting(meetingId, myIp, serverPort);
        System.out.println("📝 Meeting registered in database: " + meetingId + " at " + myIp + ":" + serverPort);

        // Multicast announce so other machines can find host without request/reply
        try {
            mcast.startAnnounce(meetingId, myIp, serverPort);
        } catch (Exception e) {
            System.err.println("❌ Failed to start multicast announce: " + e.getMessage());
            System.err.println("⚠️ Warning: LAN multicast discovery may not work on this network");
        }

        // Also start LAN discovery responder so other machines can find host
        try {
            lanDiscovery.startHostResponder(meetingId, serverPort);
            System.out.println("✅ LAN discovery responder started for meeting: " + meetingId);
        } catch (Exception e) {
            System.err.println("❌ Failed to start LAN discovery responder: " + e.getMessage());
            e.printStackTrace();
            System.err.println(
                    "⚠️ Warning: Participants on other machines may not be able to discover this meeting via LAN");
        }

        System.out.println("🎯 HOST READY! Meeting: " + meetingId + " | IP: " + myIp + " | Port: " + serverPort);
        System.out.println("📢 Participants can now join using meeting code: " + meetingId);
    }

    /**
     * Join an existing meeting (as participant)
     */
    public void joinMeeting(String meetingId, String userId, String userName, int myPort) throws IOException {
        this.currentMeetingId = meetingId;
        this.currentUserId = userId;
        this.currentUserName = userName;
        this.isHost = false;

        // Start server for incoming connections, with retry logic for port conflicts
        if (server == null || !server.isRunning()) {
            int attempts = 0;
            int maxAttempts = 10;
            int tryPort = myPort;

            while (attempts < maxAttempts) {
                try {
                    startServer(tryPort);
                    break; // Success
                } catch (java.net.BindException e) {
                    attempts++;
                    tryPort = myPort + attempts; // Try next port
                    System.out.println("⚠️ Port " + (tryPort - 1) + " in use, trying " + tryPort);

                    if (attempts >= maxAttempts) {
                        throw new IOException("Could not find available port after " + maxAttempts + " attempts", e);
                    }
                }
            }
        }

        // Step 1: Try multicast discovery FIRST (host announces periodically)
        // Step 2: Fallback to broadcast request/reply discovery
        // Step 3: Fallback to local database (only works on same machine)
        System.out.println("🔍 Step 1: Trying multicast discovery for meeting: " + meetingId);

        MeetingRegistry.HostInfo host = null;
        try {
            LanMeetingMulticast.HostInfo hi = mcast.discoverHost(meetingId, 5000);
            if (hi != null) {
                host = new MeetingRegistry.HostInfo(hi.ip, hi.port);
                System.out.println("📡 Found meeting via multicast: " + host.ip + ":" + host.port);
                try {
                    MeetingRegistry.registerMeeting(meetingId, host.ip, host.port);
                } catch (Exception ignored) {
                }
            }
        } catch (Exception e) {
            System.err.println("❌ Multicast discovery error: " + e.getMessage());
        }

        if (host == null) {
            System.out.println("🔍 Step 2: Multicast failed, trying LAN broadcast discovery for meeting: " + meetingId);
            host = discoverHostViaLan(meetingId);
        }

        boolean hostFromRegistry = false;

        if (host == null) {
            System.out.println("🔍 Step 3: Discovery failed, checking local database...");
            host = MeetingRegistry.getMeetingHost(meetingId);
            hostFromRegistry = host != null;

            if (host == null) {
                throw new IOException("Meeting not found: " + meetingId);
            } else {
                System.out.println("📝 Found meeting in local database: " + host.ip + ":" + host.port);
            }
        } else {
            System.out.println("📡 Found meeting via discovery: " + host.ip + ":" + host.port);
            // Save to local database for future reference
            try {
                MeetingRegistry.registerMeeting(meetingId, host.ip, host.port);
            } catch (Exception ignored) {
            }
        }

        try {
            System.out.println("🔗 Attempting to connect to host: " + host.ip + ":" + host.port);
            connectToPeer(host.ip, host.port);
            System.out.println("✅ Successfully connected to meeting host!");
        } catch (IOException firstConnectError) {
            System.err.println("❌ Connection failed: " + firstConnectError.getMessage());
            String msg = firstConnectError.getMessage() != null ? firstConnectError.getMessage() : "";
            boolean refused = (firstConnectError instanceof java.net.ConnectException)
                    || msg.contains("Connection refused")
                    || msg.contains("refused");

            if (hostFromRegistry && refused) {
                System.out.println("🔄 Retrying with LAN discovery...");
                MeetingRegistry.HostInfo discovered = discoverHostViaLan(meetingId);
                if (discovered != null) {
                    host = discovered;
                    try {
                        MeetingRegistry.registerMeeting(meetingId, host.ip, host.port);
                    } catch (Exception ignored) {
                    }
                    System.out.println(
                            "📡 Found host via LAN discovery: " + host.ip + ":" + host.port);
                    connectToPeer(host.ip, host.port);
                    System.out.println("✅ Successfully connected via LAN discovery!");
                } else {
                    System.err.println("❌ LAN discovery also failed");
                    throw firstConnectError;
                }
            } else {
                throw firstConnectError;
            }
        }

        System.out.println("✅ Joined meeting: " + meetingId);
    }

    private MeetingRegistry.HostInfo discoverHostViaLan(String meetingId) {
        MeetingRegistry.HostInfo discovered = null;
        int attempts = 3;
        for (int i = 1; i <= attempts; i++) {
            try {
                discovered = lanDiscovery.discoverHost(meetingId);
            } catch (IOException ignored) {
            }
            if (discovered != null)
                break;
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        return discovered;
    }

    public int getListeningPort() {
        return serverPort;
    }

    /**
     * Connect to a specific peer
     */
    public void connectToPeer(String ip, int port) throws IOException {
        Socket socket = new Socket(ip, port);
        PeerConnection conn = new PeerConnection(socket, this);
        new Thread(conn, "PeerConnection-" + ip + ":" + port).start();
    }

    /**
     * Broadcast message to all connected peers
     */
    public void broadcast(P2PMessage message) {
        message.setFrom(currentUserId);
        message.setMeetingId(currentMeetingId);
        message.setTimestamp(System.currentTimeMillis());

        System.out.println("📤 Broadcasting message: " + message.getType() + " to " + connections.size() + " peers");

        for (PeerConnection conn : connections.values()) {
            if (conn.isConnected()) {
                conn.sendMessage(message);
            }
        }
    }

    /**
     * Send message to specific peer
     */
    public void sendToPeer(String peerId, P2PMessage message) {
        PeerConnection conn = connections.get(peerId);
        if (conn != null && conn.isConnected()) {
            message.setFrom(currentUserId);
            message.setMeetingId(currentMeetingId);
            conn.sendMessage(message);
        } else {
            System.err.println("❌ Peer not connected: " + peerId);
        }
    }

    /**
     * Called when a new peer connects
     */
    public void onPeerConnected(String peerId, String peerName, PeerConnection connection) {
        connections.put(peerId, connection);

        PeerInfo peerInfo = new PeerInfo();
        peerInfo.setUserId(peerId);
        peerInfo.setUserName(peerName);
        peerInfo.setIpAddress(connection != null ? connection.getRemoteIpAddress() : null);
        peerInfo.setPort(connection != null ? connection.getPeerListenPort() : 0);
        peers.put(peerId, peerInfo);

        System.out.println("✅ Peer connected: " + peerName + " (" + peerId + ")");

        // Notify listeners
        for (Consumer<PeerInfo> listener : peerJoinedListeners) {
            listener.accept(peerInfo);
        }

        // Broadcast USER_JOINED to OTHER peers (not the new peer itself)
        P2PMessage joinMessage = new P2PMessage(MessageType.USER_JOINED, currentUserId, "all");
        joinMessage.addPayload("userId", peerId);
        joinMessage.addPayload("userName", peerName);
        joinMessage.setMeetingId(currentMeetingId);
        joinMessage.setTimestamp(System.currentTimeMillis());

        // Send to all peers EXCEPT the one that just joined
        for (Map.Entry<String, PeerConnection> entry : connections.entrySet()) {
            if (!entry.getKey().equals(peerId) && entry.getValue().isConnected()) {
                entry.getValue().sendMessage(joinMessage);
            }
        }

        System.out.println("📤 Sent USER_JOINED to " + (connections.size() - 1) + " existing peers");

        // Host pushes updated peer list to all peers so mesh can self-heal quickly
        if (isHost) {
            for (PeerConnection conn : connections.values()) {
                if (conn != null && conn.isConnected()) {
                    sendPeerList(conn);
                }
            }
        }
    }

    /**
     * Called when a peer disconnects
     */
    public void onPeerDisconnected(String peerId) {
        connections.remove(peerId);
        peers.remove(peerId);

        System.out.println("❌ Peer disconnected: " + peerId);

        // Notify listeners
        for (Consumer<String> listener : peerLeftListeners) {
            listener.accept(peerId);
        }

        // Broadcast USER_LEFT to other peers
        P2PMessage leftMessage = new P2PMessage(MessageType.USER_LEFT, currentUserId, "all");
        leftMessage.addPayload("userId", peerId);
        broadcast(leftMessage);
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
                if (isHost) {
                    sendPeerList(fromConnection);
                }
                break;

            case PEER_LIST_RESPONSE:
                handlePeerListResponse(message);
                break;

            default:
                // Notify listeners
                for (Consumer<P2PMessage> listener : messageListeners) {
                    listener.accept(message);
                }
                break;
        }
    }

    private void sendPeerList(PeerConnection toConnection) {
        P2PMessage response = new P2PMessage(MessageType.PEER_LIST_RESPONSE, currentUserId, toConnection.getPeerId());

        // Serialize peer list as a simple string payload since P2PMessage JSON is flat
        // Format: userId|userName|ip|port;userId|userName|ip|port
        StringBuilder sb = new StringBuilder();

        // include myself (host or participant) so clients can learn host port
        String myIp = getLocalIp();
        sb.append(currentUserId).append('|')
                .append(currentUserName != null ? currentUserName : "").append('|')
                .append(myIp != null ? myIp : "").append('|')
                .append(serverPort);

        for (PeerInfo peer : peers.values()) {
            if (peer == null || peer.getUserId() == null)
                continue;
            sb.append(';')
                    .append(peer.getUserId()).append('|')
                    .append(peer.getUserName() != null ? peer.getUserName() : "").append('|')
                    .append(peer.getIpAddress() != null ? peer.getIpAddress() : "").append('|')
                    .append(peer.getPort());
        }

        response.addPayload("peers", sb.toString());
        toConnection.sendMessage(response);
    }

    private void handlePeerListResponse(P2PMessage message) {
        String payload = message.getPayloadString("peers");
        if (payload == null || payload.isBlank()) {
            System.out.println("📋 Received empty peer list");
            return;
        }

        // Connect to peers we don't have a connection to yet (mesh)
        String[] entries = payload.split(";");
        int connectAttempts = 0;
        for (String entry : entries) {
            String[] parts = entry.split("\\|", -1);
            if (parts.length < 4)
                continue;

            String peerId = parts[0];
            String peerName = parts[1];
            String ip = parts[2];
            int port;
            try {
                port = Integer.parseInt(parts[3]);
            } catch (Exception e) {
                continue;
            }

            if (peerId == null || peerId.isBlank())
                continue;
            if (peerId.equals(currentUserId))
                continue;
            if (connections.containsKey(peerId))
                continue;
            if (ip == null || ip.isBlank() || port <= 0)
                continue;

            try {
                connectToPeer(ip, port);
                connectAttempts++;

                PeerInfo pi = peers.get(peerId);
                if (pi == null) {
                    pi = new PeerInfo();
                    pi.setUserId(peerId);
                    pi.setUserName(peerName);
                    peers.put(peerId, pi);
                }
                pi.setIpAddress(ip);
                pi.setPort(port);
            } catch (IOException e) {
                System.err
                        .println("❌ Failed to connect to peer from list: " + ip + ":" + port + " -> " + e.getMessage());
            }
        }

        System.out.println("📋 Received peer list, connect attempts: " + connectAttempts);
    }

    /**
     * Stop P2P manager and close all connections
     */
    public void stop() {
        System.out.println("🛑 Stopping P2PManager...");

        try {
            mcast.stopAnnounce();
        } catch (Exception ignored) {
        }

        try {
            lanDiscovery.stopHostResponder();
        } catch (Exception ignored) {
        }

        // Unregister meeting from registry if this was the host
        if (isHost && currentMeetingId != null) {
            MeetingRegistry.unregisterMeeting(currentMeetingId);
        }

        // Close all peer connections
        for (PeerConnection conn : connections.values()) {
            conn.close();
        }
        connections.clear();
        peers.clear();

        // Stop server
        if (server != null) {
            server.stop();
        }

        System.out.println("✅ P2PManager stopped");
    }

    // Event listener registration
    public void addPeerJoinedListener(Consumer<PeerInfo> listener) {
        peerJoinedListeners.add(listener);
    }

    public void addPeerLeftListener(Consumer<String> listener) {
        peerLeftListeners.add(listener);
    }

    public void addMessageListener(Consumer<P2PMessage> listener) {
        messageListeners.add(listener);
    }

    // Getters
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

    public Map<String, PeerInfo> getPeers() {
        return new HashMap<>(peers);
    }

    public void setPeerVideoOn(String peerId, boolean videoOn) {
        PeerInfo peer = peers.get(peerId);
        if (peer != null) {
            peer.setVideoOn(videoOn);
        }
    }

    public void setPeerAudioOn(String peerId, boolean audioOn) {
        PeerInfo peer = peers.get(peerId);
        if (peer != null) {
            peer.setAudioOn(audioOn);
        }
    }

    public int getConnectionCount() {
        return connections.size();
    }

    private String getLocalIp() {
        try {
            return InetAddress.getLocalHost().getHostAddress();
        } catch (Exception e) {
            return "127.0.0.1";
        }
    }
}
