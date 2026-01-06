package com.example.dacs4.network;

import com.example.dacs4.models.P2PMessage;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.net.Socket;

public class PeerConnection implements Runnable {
    private Socket socket;
    private DataInputStream input;
    private DataOutputStream output;
    private P2PManager manager;
    private String peerId;
    private String peerName;
    private int peerListenPort;
    private boolean running = true;

    public PeerConnection(Socket socket, P2PManager manager) throws IOException {
        this.socket = socket;
        this.manager = manager;
        this.input = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
        this.output = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
    }

    @Override
    public void run() {
        try {
            // Handshake: exchange peer info
            performHandshake();

            // Notify manager
            manager.onPeerConnected(peerId, peerName, this);

            // Listen for messages
            while (running && !socket.isClosed()) {
                try {
                    String messageJson = readMessageString();
                    P2PMessage message = P2PMessage.fromJson(messageJson);

                    // Handle message
                    manager.handleMessage(message, this);

                } catch (EOFException e) {
                    // Connection closed
                    break;
                } catch (IOException e) {
                    if (running) {
                        System.err.println("❌ Error reading message from peer " + peerId + ": " + e.getMessage());
                    }
                    break;
                }
            }

        } catch (IOException e) {
            System.err.println("❌ Handshake failed: " + e.getMessage());
        } finally {
            cleanup();
        }
    }

    private void performHandshake() throws IOException {
        System.out.println("🤝 DEBUG: Starting handshake...");

        // Send my info
        System.out.println("📤 DEBUG: Sending my info - userId=" + manager.getCurrentUserId() + ", userName="
                + manager.getCurrentUserName() + ", port=" + manager.getServerPort());
        output.writeUTF(manager.getCurrentUserId());
        output.writeUTF(manager.getCurrentUserName());
        output.writeInt(manager.getServerPort());
        output.flush();
        System.out.println("✅ DEBUG: Sent my info successfully");

        // Receive peer info
        System.out.println("📥 DEBUG: Waiting to receive peer info...");
        this.peerId = input.readUTF();
        this.peerName = input.readUTF();
        this.peerListenPort = input.readInt();

        System.out.println("🤝 Handshake complete with peer: " + peerName + " (" + peerId + ") port=" + peerListenPort);
        System.out.println("✅ DEBUG: Handshake successful! Will now call manager.onPeerConnected()");
    }

    public void sendMessage(P2PMessage message) {
        try {
            synchronized (output) {
                writeMessageString(message.toJson());
            }
        } catch (IOException e) {
            String err = e.getMessage() != null ? e.getMessage() : "";
            System.err.println("❌ Error sending message to peer " + peerId + ": " + err);
            close();
        }
    }

    /**
     * Read a length-prefixed UTF-8 message.
     *
     * We avoid DataInputStream.readUTF()/writeUTF() because it has a ~64KB limit
     * that breaks file transfer chunks and other larger messages.
     */
    private String readMessageString() throws IOException {
        int len = input.readInt();
        if (len < 0) {
            throw new EOFException("Negative message length");
        }
        byte[] buf = new byte[len];
        input.readFully(buf);
        return new String(buf, StandardCharsets.UTF_8);
    }

    private void writeMessageString(String message) throws IOException {
        byte[] bytes = message.getBytes(StandardCharsets.UTF_8);
        output.writeInt(bytes.length);
        output.write(bytes);
        output.flush();
    }

    public void close() {
        running = false;
        try {
            if (input != null)
                input.close();
            if (output != null)
                output.close();
            if (socket != null && !socket.isClosed())
                socket.close();
        } catch (IOException e) {
            System.err.println("❌ Error closing connection: " + e.getMessage());
        }
    }

    private void cleanup() {
        System.out.println("👋 Peer disconnected: " + peerName + " (" + peerId + ")");
        manager.onPeerDisconnected(peerId);
        close();
    }

    // Getters
    public String getPeerId() {
        return peerId;
    }

    public String getPeerName() {
        return peerName;
    }

    public int getPeerListenPort() {
        return peerListenPort;
    }

    public boolean isConnected() {
        return running && socket != null && !socket.isClosed();
    }

    public String getRemoteIpAddress() {
        if (socket == null || socket.getInetAddress() == null)
            return null;
        return socket.getInetAddress().getHostAddress();
    }

    public int getRemotePort() {
        if (socket == null)
            return -1;
        return socket.getPort();
    }
}
