package com.example.dacs4.network;

import com.example.dacs4.models.MessageType;
import com.example.dacs4.models.P2PMessage;
import com.example.dacs4.models.PeerInfo;

import java.io.IOException;
import java.util.Map;

/**
 * Manages peer list serialization, distribution, and mesh network formation
 */
public class PeerListManager {

    private final PeerStateManager peerStateManager;
    private final P2PConnectionManager connectionManager;

    public PeerListManager(PeerStateManager peerStateManager, P2PConnectionManager connectionManager) {
        this.peerStateManager = peerStateManager;
        this.connectionManager = connectionManager;
    }

    /**
     * Send peer list to a specific peer connection
     */
    public void sendPeerList(PeerConnection toConnection, String currentUserId, String currentUserName,
            String myIp, int serverPort) {
        P2PMessage response = new P2PMessage(MessageType.PEER_LIST_RESPONSE, currentUserId, toConnection.getPeerId());

        // Serialize peer list
        String serializedPeers = serializePeerList(currentUserId, currentUserName, myIp, serverPort,
                peerStateManager.getPeers());

        response.addPayload("peers", serializedPeers);
        toConnection.sendMessage(response);

        System.out.println("📤 Sent peer list to: " + toConnection.getPeerId());
    }

    /**
     * Handle incoming peer list response and establish mesh connections
     */
    public void handlePeerListResponse(P2PMessage message, String currentUserId) {
        System.out.println("📋 Received peer list from host");

        // Extract peers from message and try to connect
        String payload = message.getPayloadString("peers");
        if (payload == null || payload.isBlank()) {
            System.out.println("📋 Received empty peer list");
            return;
        }

        String[] entries = payload.split(";");
        int connectAttempts = 0;

        for (String entry : entries) {
            String[] parts = entry.split("\\|", -1);
            if (parts.length < 4) {
                continue;
            }

            String peerId = parts[0];
            String peerName = parts[1];
            String ip = parts[2];
            int port;

            try {
                port = Integer.parseInt(parts[3]);
            } catch (Exception e) {
                continue;
            }

            // Validate peer entry
            if (peerId == null || peerId.isBlank()) {
                continue;
            }
            if (peerId.equals(currentUserId)) {
                continue; // Skip self
            }
            if (connectionManager.getConnection(peerId) != null) {
                continue; // Already connected
            }
            if (ip == null || ip.isBlank() || port <= 0) {
                continue;
            }

            // Try to connect to this peer
            try {
                System.out.println("🔗 Connecting to peer from list: " + peerName + " (" + ip + ":" + port + ")");
                connectionManager.connectToPeer(ip, port);
                connectAttempts++;

                // Update or create peer info
                PeerInfo pi = peerStateManager.getPeerInfo(peerId);
                if (pi == null) {
                    pi = new PeerInfo();
                    pi.setUserId(peerId);
                    pi.setUserName(peerName);
                }
                pi.setIpAddress(ip);
                pi.setPort(port);
                peerStateManager.updatePeerInfo(peerId, pi);

            } catch (IOException e) {
                System.err
                        .println("❌ Failed to connect to peer from list: " + ip + ":" + port + " -> " + e.getMessage());
            }
        }

        System.out.println("📋 Peer list processed, connection attempts: " + connectAttempts);
    }

    /**
     * Serialize peer list to string format
     * Format: userId|userName|ip|port;userId|userName|ip|port
     */
    public String serializePeerList(String currentUserId, String currentUserName, String myIp, int serverPort,
            Map<String, PeerInfo> peers) {
        StringBuilder sb = new StringBuilder();

        // Include myself (host or participant) so clients can learn host port
        sb.append(currentUserId).append('|')
                .append(currentUserName != null ? currentUserName : "").append('|')
                .append(myIp != null ? myIp : "").append('|')
                .append(serverPort);

        // Add all other peers
        for (PeerInfo peer : peers.values()) {
            if (peer == null || peer.getUserId() == null) {
                continue;
            }

            sb.append(';')
                    .append(peer.getUserId()).append('|')
                    .append(peer.getUserName() != null ? peer.getUserName() : "").append('|')
                    .append(peer.getIpAddress() != null ? peer.getIpAddress() : "").append('|')
                    .append(peer.getPort());
        }

        return sb.toString();
    }

    /**
     * Parse peer list from string format
     * Returns array of peer entries, each entry is [userId, userName, ip, port]
     */
    public String[][] parsePeerList(String payload) {
        if (payload == null || payload.isBlank()) {
            return new String[0][];
        }

        String[] entries = payload.split(";");
        String[][] result = new String[entries.length][];

        for (int i = 0; i < entries.length; i++) {
            result[i] = entries[i].split("\\|", -1);
        }

        return result;
    }
}
