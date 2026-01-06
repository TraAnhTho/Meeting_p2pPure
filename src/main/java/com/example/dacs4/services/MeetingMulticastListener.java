package com.example.dacs4.services;

import com.example.dacs4.DB.SQLiteConnection;
import javafx.application.Platform;

import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Listener for multicast meeting updates
 */
public class MeetingMulticastListener {

    private Thread listenThread;
    private volatile boolean listening = false;

    // Callbacks
    private Consumer<String> onMeetingClosed;
    private BiConsumer<String, Integer> onParticipantCountUpdate;

    public void setOnMeetingClosed(Consumer<String> callback) {
        this.onMeetingClosed = callback;
    }

    public void setOnParticipantCountUpdate(BiConsumer<String, Integer> callback) {
        this.onParticipantCountUpdate = callback;
    }

    /**
     * Start listening for multicast updates
     */
    public void start() {
        if (listenThread != null && listenThread.isAlive()) {
            System.out.println("⚠️ Multicast listener already running");
            return;
        }

        listening = true;
        listenThread = new Thread(this::listenLoop, "dashboard-mcast-listener");
        listenThread.setDaemon(true);
        listenThread.start();
        System.out.println("📡 Multicast listener started");
    }

    /**
     * Stop listening for multicast updates
     */
    public void stop() {
        listening = false;
        if (listenThread != null) {
            listenThread.interrupt();
        }
        System.out.println("🛑 Multicast listener stopped");
    }

    /**
     * Main listen loop
     */
    private void listenLoop() {
        final String addr = "230.0.0.0";
        final int port = 9999;

        try {
            InetAddress group = InetAddress.getByName(addr);
            NetworkInterface ni = pickLanInterface();
            if (ni == null) {
                System.err.println("[Dashboard multicast] Cannot find LAN network interface");
                return;
            }

            try (MulticastSocket sock = new MulticastSocket(null)) {
                sock.setReuseAddress(true);
                sock.bind(new InetSocketAddress(port));
                sock.joinGroup(new InetSocketAddress(group, port), ni);
                sock.setSoTimeout(1000);

                byte[] buf = new byte[512];
                DatagramPacket packet = new DatagramPacket(buf, buf.length);

                while (listening) {
                    try {
                        sock.receive(packet);
                        String msg = new String(packet.getData(), 0, packet.getLength(), StandardCharsets.UTF_8)
                                .trim();

                        String[] parts = msg.split("\\|", -1);

                        // Handle MEETING_CLOSED
                        if (parts.length >= 2 && "MEETING_CLOSED".equals(parts[0])) {
                            String meetingId = parts[1];
                            handleMeetingClosed(meetingId);
                        }

                        // Handle MEETING_HOST (participant count update)
                        if (parts.length >= 5 && "MEETING_HOST".equals(parts[0])) {
                            String meetingId = parts[1];
                            try {
                                int participants = Integer.parseInt(parts[4]);
                                handleParticipantCountUpdate(meetingId, participants);
                            } catch (Exception ignored) {
                            }
                        }
                    } catch (SocketTimeoutException ignored) {
                        // Normal timeout, continue
                    } catch (Exception e) {
                        if (listening) {
                            System.err.println("[Dashboard multicast] " + e.getMessage());
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("[Dashboard multicast] Failed: " + e.getMessage());
        }
    }

    /**
     * Handle meeting closed event
     */
    private void handleMeetingClosed(String meetingId) {
        if (meetingId == null || meetingId.isBlank()) {
            return;
        }

        System.out.println("📡 Received MEETING_CLOSED for: " + meetingId);

        // Mark meeting as ended in database
        try (SQLiteConnection db = new SQLiteConnection()) {
            db.createTables();
            db.endMeeting(meetingId);
        } catch (Exception e) {
            System.err.println("[Dashboard] Failed to mark meeting ended: " + e.getMessage());
        }

        // Notify callback
        if (onMeetingClosed != null) {
            Platform.runLater(() -> onMeetingClosed.accept(meetingId));
        }
    }

    /**
     * Handle participant count update
     */
    private void handleParticipantCountUpdate(String meetingId, int count) {
        if (onParticipantCountUpdate != null) {
            Platform.runLater(() -> onParticipantCountUpdate.accept(meetingId, count));
        }
    }

    /**
     * Pick a LAN network interface for multicast
     */
    private NetworkInterface pickLanInterface() throws SocketException {
        Enumeration<NetworkInterface> nis = NetworkInterface.getNetworkInterfaces();
        while (nis.hasMoreElements()) {
            NetworkInterface n = nis.nextElement();
            if (!n.isUp() || n.isLoopback() || n.isVirtual())
                continue;

            Enumeration<InetAddress> addrs = n.getInetAddresses();
            while (addrs.hasMoreElements()) {
                InetAddress a = addrs.nextElement();
                if (a instanceof Inet4Address && !a.isLoopbackAddress()) {
                    return n;
                }
            }
        }
        return null;
    }
}
