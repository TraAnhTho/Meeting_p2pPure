package com.example.dacs4.network;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages P2P connections - server socket and peer connections
 */
public class P2PConnectionManager {

    private ServerSocket serverSocket;
    private int serverPort;
    private boolean isRunning = false;
    private Thread acceptThread;

    private final Map<String, PeerConnection> connections = new ConcurrentHashMap<>();
    private final P2PManager p2pManager;

    public P2PConnectionManager(P2PManager p2pManager) {
        this.p2pManager = p2pManager;
    }

    /**
     * Start server socket and begin accepting connections
     */
    public void startServer(int port) throws IOException {
        if (isRunning) {
            System.out.println("⚠️ Connection manager already running on port " + serverPort);
            return;
        }

        this.serverPort = port;
        this.serverSocket = new ServerSocket(port);
        this.isRunning = true;

        System.out.println("🎧 DEBUG: ServerSocket created and bound to port " + port);

        // Start accept thread
        acceptThread = new Thread(this::acceptConnections, "ConnectionAcceptor-" + port);
        acceptThread.setDaemon(true);
        acceptThread.start();

        System.out.println("🚀 P2P Connection Manager started on port " + port);
        System.out.println("✅ DEBUG: Accept thread started, ready to accept connections");
    }

    /**
     * Accept incoming connections
     */
    private void acceptConnections() {
        System.out.println("🎧 DEBUG: acceptConnections() thread started, listening on port " + serverPort);
        while (isRunning && serverSocket != null && !serverSocket.isClosed()) {
            try {
                System.out.println("⏳ DEBUG: Waiting for incoming connection on port " + serverPort + "...");
                Socket clientSocket = serverSocket.accept();
                System.out.println("📞 DEBUG: Incoming connection accepted from: " + clientSocket.getInetAddress() + ":"
                        + clientSocket.getPort());

                // Create peer connection
                PeerConnection peerConn = new PeerConnection(clientSocket, p2pManager);
                Thread connThread = new Thread(peerConn, "PeerConnection-" + clientSocket.getInetAddress());
                connThread.setDaemon(true);
                connThread.start();
                System.out.println("🚀 DEBUG: Started PeerConnection thread for " + clientSocket.getInetAddress());

            } catch (IOException e) {
                if (isRunning) {
                    System.err.println("❌ Error accepting connection: " + e.getMessage());
                }
            }
        }
    }

    /**
     * Connect to a peer
     */
    public PeerConnection connectToPeer(String host, int port) throws IOException {
        Socket socket = new Socket(host, port);
        PeerConnection peerConn = new PeerConnection(socket, p2pManager);

        Thread connThread = new Thread(peerConn, "PeerConnection-" + host + ":" + port);
        connThread.setDaemon(true);
        connThread.start();

        return peerConn;
    }

    /**
     * Add a peer connection
     */
    public void addConnection(String peerId, PeerConnection connection) {
        connections.put(peerId, connection);
        System.out.println("🔗 Connection added for peer: " + peerId);
    }

    /**
     * Remove a peer connection
     */
    public void removeConnection(String peerId) {
        PeerConnection removed = connections.remove(peerId);
        if (removed != null) {
            removed.close();
            System.out.println("🔌 Connection removed for peer: " + peerId);
        }
    }

    /**
     * Get a specific connection
     */
    public PeerConnection getConnection(String peerId) {
        return connections.get(peerId);
    }

    /**
     * Get all connections
     */
    public Map<String, PeerConnection> getConnections() {
        return new ConcurrentHashMap<>(connections);
    }

    /**
     * Get connection count
     */
    public int getConnectionCount() {
        return connections.size();
    }

    /**
     * Check if server is running
     */
    public boolean isRunning() {
        return isRunning;
    }

    /**
     * Get server port
     */
    public int getServerPort() {
        return serverPort;
    }

    /**
     * Disconnect all peers and stop server
     */
    public void shutdown() {
        isRunning = false;

        // Close all connections
        for (PeerConnection conn : connections.values()) {
            if (conn != null) {
                conn.close();
            }
        }
        connections.clear();

        // Close server socket
        if (serverSocket != null && !serverSocket.isClosed()) {
            try {
                serverSocket.close();
            } catch (IOException e) {
                System.err.println("❌ Error closing server socket: " + e.getMessage());
            }
        }

        System.out.println("🛑 P2P Connection Manager shut down");
    }
}
