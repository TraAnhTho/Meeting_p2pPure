package com.example.dacs4.network;

import java.io.IOException;
import java.net.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * UDP-based video engine skeleton for P2P video calls.
 *
 * Step 2: provide basic send/receive of raw byte[] over UDP so that
 * two peers can exchange dummy data (e.g. small test frames).
 *
 * Actual image encoding/decoding and UI integration will be added later.
 */
public class VideoEngine {

    private final int localPort;
    private DatagramSocket socket;
    private InetAddress remoteAddress;
    private int remotePort;
    private final AtomicBoolean running = new AtomicBoolean(false);
    /** Listener for raw frame bytes received from remote peer. */
    public interface FrameListener {
        void onFrameReceived(byte[] data, InetAddress from, int fromPort);
    }

    private volatile FrameListener frameListener;

    private static DatagramSocket bindUdpSocket(int port) throws SocketException {
        DatagramSocket s = new DatagramSocket(null);
        s.setReuseAddress(true);
        s.bind(new InetSocketAddress(port));
        return s;
    }

    public VideoEngine(int localPort) {
        this.localPort = localPort;
    }

    public int getLocalPort() {
        return localPort;
    }

    public void setFrameListener(FrameListener listener) {
        this.frameListener = listener;
    }

    /**
     * Start only the receive loop on the local port (no remote endpoint required).
     * This lets a peer receive video frames even when they haven't enabled their own camera.
     */
    public synchronized void startReceiver() throws SocketException {
        if (running.get()) {
            return;
        }
        if (socket == null || socket.isClosed()) {
            socket = bindUdpSocket(localPort);
        }
        running.set(true);
        Thread t = new Thread(this::receiveLoop, "video-engine-" + localPort);
        t.setDaemon(true);
        t.start();
        System.out.println("[VideoEngine] receiver started on port " + localPort);
    }

    /**
     * Configure remote endpoint and start a background receive loop.
     *
     * For now this only logs the size of received packets as dummy data.
     */
    public synchronized void start(String remoteIp, int remoteVideoPort) throws SocketException {
        if (running.get()) {
            // already started, just update remote address if needed
            try {
                this.remoteAddress = InetAddress.getByName(remoteIp);
            } catch (UnknownHostException e) {
                throw new SocketException("Invalid remote IP: " + e.getMessage());
            }
            this.remotePort = remoteVideoPort;
            return;
        }

        this.remotePort = remoteVideoPort;
        try {
            this.remoteAddress = InetAddress.getByName(remoteIp);
        } catch (UnknownHostException e) {
            throw new SocketException("Invalid remote IP: " + e.getMessage());
        }

        // bind local UDP socket
        if (socket == null || socket.isClosed()) {
            socket = bindUdpSocket(localPort);
        }

        running.set(true);
        Thread t = new Thread(this::receiveLoop, "video-engine-" + localPort);
        t.setDaemon(true);
        t.start();

        System.out.println("[VideoEngine] started on port " + localPort + " -> remote " + remoteIp + ":" + remoteVideoPort);
    }

    /**
     * Send raw bytes to the remote peer over UDP.
     *
     * This is intentionally generic so you can test with any dummy payload
     * (e.g. a short string). Later this will carry encoded video frames.
     */
    public synchronized void sendFrame(byte[] data) {
        if (!running.get() || socket == null || socket.isClosed()) {
            System.err.println("[VideoEngine] sendFrame called while not running");
            return;
        }
        if (remoteAddress == null || remotePort <= 0) {
            System.err.println("[VideoEngine] remote endpoint not configured");
            return;
        }
        try {
            DatagramPacket packet = new DatagramPacket(data, data.length, remoteAddress, remotePort);
            socket.send(packet);
        } catch (IOException e) {
            System.err.println("[VideoEngine] Failed to send frame: " + e.getMessage());
        }
    }

    private void receiveLoop() {
        byte[] buf = new byte[65507]; // max UDP payload
        while (running.get() && socket != null && !socket.isClosed()) {
            try {
                DatagramPacket packet = new DatagramPacket(buf, buf.length);
                socket.receive(packet);

                int len = packet.getLength();
                InetAddress fromAddr = packet.getAddress();
                int fromPort = packet.getPort();

                // Copy only the actual payload
                byte[] data = new byte[len];
                System.arraycopy(packet.getData(), packet.getOffset(), data, 0, len);

                // Notify listener if present, otherwise just log
                FrameListener listener = frameListener;
                if (listener != null) {
                    listener.onFrameReceived(data, fromAddr, fromPort);
                } else {
                    System.out.println("[VideoEngine] received " + len + " bytes from " + fromAddr.getHostAddress() + ":" + fromPort);
                }
            } catch (SocketException e) {
                // socket closed or stopped
                if (running.get()) {
                    System.err.println("[VideoEngine] SocketException in receiveLoop: " + e.getMessage());
                }
                break;
            } catch (IOException e) {
                System.err.println("[VideoEngine] IOException in receiveLoop: " + e.getMessage());
            }
        }
    }

    /**
     * Stop any ongoing video streaming and close the socket.
     */
    public synchronized void stop() {
        if (!running.getAndSet(false)) {
            return;
        }

        if (socket != null && !socket.isClosed()) {
            socket.close();
        }
        System.out.println("[VideoEngine] stopped on port " + localPort);
    }

    public void shutdown() {
        stop();
    }
}
