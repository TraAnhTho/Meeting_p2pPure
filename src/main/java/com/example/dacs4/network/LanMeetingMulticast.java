package com.example.dacs4.network;

import java.io.IOException;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.IntSupplier;

public class LanMeetingMulticast {

    // Keep same as your friend
    private static final String MCAST_ADDR = "230.0.0.0";
    private static final int MCAST_PORT = 9999;

    private final AtomicBoolean announcing = new AtomicBoolean(false);
    private Thread announceThread;
    private MulticastSocket announceSocket;
    private InetAddress group;
    private NetworkInterface ni;

    public static class HostInfo {
        public final String ip;
        public final int port;
        public final Integer participants;

        public HostInfo(String ip, int port) {
            this.ip = ip;
            this.port = port;
            this.participants = null;
        }

        public HostInfo(String ip, int port, Integer participants) {
            this.ip = ip;
            this.port = port;
            this.participants = participants;
        }
    }

    /** HOST: start announcing MEETING_HOST|CODE|IP|PORT every 500ms–1s */
    public void startAnnounce(String meetingCode, String hostIp, int tcpPort) throws IOException {
        startAnnounce(meetingCode, hostIp, tcpPort, null);
    }

    public void startAnnounce(String meetingCode, String hostIp, int tcpPort, IntSupplier participantsSupplier)
            throws IOException {
        stopAnnounce();

        group = InetAddress.getByName(MCAST_ADDR);
        ni = pickLanInterface();
        if (ni == null)
            throw new IOException("Cannot find LAN network interface for multicast");

        // Use MulticastSocket so we can set TTL + interface
        announceSocket = new MulticastSocket(); // ephemeral local port
        announceSocket.setTimeToLive(1); // LAN only
        announceSocket.setNetworkInterface(ni);

        announcing.set(true);
        announceThread = new Thread(() -> {
            while (announcing.get()) {
                try {
                    String msg;
                    if (participantsSupplier != null) {
                        int pCount;
                        try {
                            pCount = participantsSupplier.getAsInt();
                        } catch (Exception e) {
                            pCount = -1;
                        }
                        msg = "MEETING_HOST|" + meetingCode + "|" + hostIp + "|" + tcpPort + "|" + pCount;
                    } else {
                        msg = "MEETING_HOST|" + meetingCode + "|" + hostIp + "|" + tcpPort;
                    }
                    byte[] buf = msg.getBytes(StandardCharsets.UTF_8);
                    DatagramPacket packet = new DatagramPacket(buf, buf.length, group, MCAST_PORT);
                    announceSocket.send(packet);
                    Thread.sleep(700); // 500ms–1s
                } catch (Exception e) {
                    if (announcing.get()) {
                        System.err.println("[Multicast announce] " + e.getMessage());
                    }
                }
            }
        }, "meeting-mcast-announce");
        announceThread.setDaemon(true);
        announceThread.start();

        System.out.println("📡 Multicast announce started on " + MCAST_ADDR + ":" + MCAST_PORT +
                " | code=" + meetingCode + " | host=" + hostIp + ":" + tcpPort +
                " | iface=" + ni.getDisplayName());
    }

    public void stopAnnounce() {
        announcing.set(false);
        try {
            if (announceSocket != null)
                announceSocket.close();
        } catch (Exception ignored) {
        }
        announceSocket = null;
        announceThread = null;
    }

    /** CLIENT: listen multicast until we see matching meetingCode (timeoutMs) */
    public HostInfo discoverHost(String meetingCode, int timeoutMs) throws IOException {
        InetAddress group = InetAddress.getByName(MCAST_ADDR);
        NetworkInterface ni = pickLanInterface();
        if (ni == null)
            throw new IOException("Cannot find LAN network interface for multicast");

        long end = System.currentTimeMillis() + timeoutMs;

        try (MulticastSocket sock = new MulticastSocket(MCAST_PORT)) {
            sock.setReuseAddress(true);
            sock.joinGroup(new InetSocketAddress(group, MCAST_PORT), ni);

            // Use short timeout loops so we can check end-time
            sock.setSoTimeout(500);

            byte[] buf = new byte[512];
            DatagramPacket packet = new DatagramPacket(buf, buf.length);

            System.out.println("👂 Listening multicast " + MCAST_ADDR + ":" + MCAST_PORT +
                    " for code=" + meetingCode + " (timeout " + timeoutMs + "ms) on " + ni.getDisplayName());

            while (System.currentTimeMillis() < end) {
                try {
                    sock.receive(packet);
                    String msg = new String(packet.getData(), 0, packet.getLength(), StandardCharsets.UTF_8).trim();

                    // Expected: MEETING_HOST|CODE|IP|PORT
                    String[] p = msg.split("\\|", -1);
                    if (p.length >= 4 && "MEETING_HOST".equals(p[0]) && meetingCode.equals(p[1])) {
                        String ip = p[2];
                        int port = Integer.parseInt(p[3]);
                        System.out.println("✅ Found host via multicast: " + ip + ":" + port);
                        if (p.length >= 5) {
                            try {
                                Integer participants = Integer.parseInt(p[4]);
                                return new HostInfo(ip, port, participants);
                            } catch (Exception ignored) {
                                return new HostInfo(ip, port);
                            }
                        }
                        return new HostInfo(ip, port);
                    }
                } catch (SocketTimeoutException ignored) {
                    // loop again until end
                }
            }
        }

        return null;
    }

    public void announceClosed(String meetingCode) throws IOException {
        InetAddress group = InetAddress.getByName(MCAST_ADDR);
        NetworkInterface ni = pickLanInterface();
        if (ni == null)
            throw new IOException("Cannot find LAN network interface for multicast");

        try (MulticastSocket sock = new MulticastSocket()) {
            sock.setTimeToLive(1);
            sock.setNetworkInterface(ni);
            String msg = "MEETING_CLOSED|" + meetingCode;
            byte[] buf = msg.getBytes(StandardCharsets.UTF_8);
            DatagramPacket packet = new DatagramPacket(buf, buf.length, group, MCAST_PORT);
            sock.send(packet);
        }
    }

    /** Pick a usable LAN interface (similar logic to your friend's code) */
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
