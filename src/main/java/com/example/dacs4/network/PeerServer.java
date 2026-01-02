package com.example.dacs4.network;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

public class PeerServer implements Runnable {
    private ServerSocket serverSocket;
    private int port;
    private P2PManager manager;
    private boolean running = true;

    public PeerServer(int port, P2PManager manager) throws IOException {
        this.port = port;
        this.manager = manager;
        this.serverSocket = new ServerSocket(port);
        System.out.println("✅ PeerServer started on port " + port);
    }

    @Override
    public void run() {
        System.out.println("🔊 PeerServer listening for connections...");

        while (running) {
            try {
                Socket clientSocket = serverSocket.accept();
                System.out.println("📥 New peer connection from: " + clientSocket.getInetAddress());

                // Handle new peer in separate thread
                handleNewPeer(clientSocket);

            } catch (IOException e) {
                if (running) {
                    System.err.println("❌ Error accepting connection: " + e.getMessage());
                }
            }
        }
    }

    private void handleNewPeer(Socket socket) {
        try {
            PeerConnection conn = new PeerConnection(socket, manager);
            new Thread(conn).start();
        } catch (IOException e) {
            System.err.println("❌ Error creating peer connection: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void stop() {
        running = false;
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
                System.out.println("🛑 PeerServer stopped");
            }
        } catch (IOException e) {
            System.err.println("❌ Error stopping server: " + e.getMessage());
        }
    }

    public int getPort() {
        return port;
    }

    public boolean isRunning() {
        return running;
    }
}
