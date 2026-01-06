package com.example.dacs4.network;

import com.example.dacs4.models.PeerInfo;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Manages peer state and lifecycle events
 */
public class PeerStateManager {

    private final Map<String, PeerInfo> peers = new ConcurrentHashMap<>();

    // Event listeners
    private final List<Consumer<PeerInfo>> peerJoinedListeners = new ArrayList<>();
    private final List<Consumer<String>> peerLeftListeners = new ArrayList<>();

    /**
     * Add a new peer
     */
    public void addPeer(String peerId, PeerInfo peerInfo) {
        peers.put(peerId, peerInfo);
        System.out.println("👤 Peer added: " + peerInfo.getUserName() + " (" + peerId + ")");

        // Notify listeners
        for (Consumer<PeerInfo> listener : peerJoinedListeners) {
            listener.accept(peerInfo);
        }
    }

    /**
     * Remove a peer
     */
    public void removePeer(String peerId) {
        PeerInfo removed = peers.remove(peerId);
        if (removed != null) {
            System.out.println("👋 Peer removed: " + removed.getUserName() + " (" + peerId + ")");

            // Notify listeners
            for (Consumer<String> listener : peerLeftListeners) {
                listener.accept(peerId);
            }
        }
    }

    /**
     * Get all peers
     */
    public Map<String, PeerInfo> getPeers() {
        return new HashMap<>(peers);
    }

    /**
     * Get specific peer info
     */
    public PeerInfo getPeerInfo(String peerId) {
        return peers.get(peerId);
    }

    /**
     * Update peer info
     */
    public void updatePeerInfo(String peerId, PeerInfo updatedInfo) {
        peers.put(peerId, updatedInfo);
    }

    /**
     * Check if peer exists
     */
    public boolean hasPeer(String peerId) {
        return peers.containsKey(peerId);
    }

    /**
     * Get peer count
     */
    public int getPeerCount() {
        return peers.size();
    }

    /**
     * Clear all peers
     */
    public void clearPeers() {
        peers.clear();
    }

    // ===== Listener Management =====

    public void addPeerJoinedListener(Consumer<PeerInfo> listener) {
        peerJoinedListeners.add(listener);
    }

    public void addPeerLeftListener(Consumer<String> listener) {
        peerLeftListeners.add(listener);
    }

    public void removePeerJoinedListener(Consumer<PeerInfo> listener) {
        peerJoinedListeners.remove(listener);
    }

    public void removePeerLeftListener(Consumer<String> listener) {
        peerLeftListeners.remove(listener);
    }
}
