package com.example.dacs4.network;

import java.io.IOException;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;

public class LanMeetingDiscovery {
    private static final int DISCOVERY_PORT = 5051;
    private static final int SOCKET_TIMEOUT_MS = 1500;

    private static final String DISCOVER_PREFIX = "DISCOVER_MEETING|";
    private static final String RESPONSE_PREFIX = "MEETING_HOST|";

    private DatagramSocket serverSocket;
    private Thread serverThread;

    public void startHostResponder(String meetingId, int hostTcpPort) throws SocketException {
        stopHostResponder();

        serverSocket = new DatagramSocket(null);
        serverSocket.setReuseAddress(true);
        serverSocket.setBroadcast(true);
        serverSocket.bind(new InetSocketAddress("0.0.0.0", DISCOVERY_PORT));

        System.out.println("📡 LAN discovery responder started on UDP " + DISCOVERY_PORT
                + " (meeting " + meetingId + ", tcp " + hostTcpPort + ")");

        serverThread = new Thread(() -> {
            byte[] buf = new byte[1024];
            while (serverSocket != null && !serverSocket.isClosed()) {
                try {
                    DatagramPacket packet = new DatagramPacket(buf, buf.length);
                    serverSocket.receive(packet);

                    String msg = new String(packet.getData(), packet.getOffset(), packet.getLength(), StandardCharsets.UTF_8);
                    if (!msg.startsWith(DISCOVER_PREFIX)) {
                        continue;
                    }

                    String reqMeetingId = msg.substring(DISCOVER_PREFIX.length()).trim();
                    if (!meetingId.equalsIgnoreCase(reqMeetingId)) {
                        continue;
                    }

                    String hostIp = InetAddress.getLocalHost().getHostAddress();
                    String response = RESPONSE_PREFIX + meetingId + "|" + hostIp + "|" + hostTcpPort;
                    byte[] respBytes = response.getBytes(StandardCharsets.UTF_8);

                    DatagramPacket resp = new DatagramPacket(respBytes, respBytes.length, packet.getAddress(), packet.getPort());
                    serverSocket.send(resp);

                    System.out.println("📡 Discovery reply to " + packet.getAddress().getHostAddress() + ":" + packet.getPort()
                            + " -> " + response);
                } catch (IOException ignored) {
                    // ignore; socket closed or transient network error
                } catch (Exception ignored) {
                    // ignore
                }
            }
        }, "LanDiscoveryResponder-" + DISCOVERY_PORT);
        serverThread.setDaemon(true);
        serverThread.start();
    }

    public MeetingRegistry.HostInfo discoverHost(String meetingId) throws IOException {
        String request = DISCOVER_PREFIX + meetingId;
        byte[] reqBytes = request.getBytes(StandardCharsets.UTF_8);

        try (DatagramSocket sock = new DatagramSocket()) {
            sock.setBroadcast(true);
            sock.setSoTimeout(SOCKET_TIMEOUT_MS);

            System.out.println("📡 LAN discovery broadcast on UDP " + DISCOVERY_PORT + ": " + request);

            // 1) Global broadcast
            try {
                InetAddress broadcast = InetAddress.getByName("255.255.255.255");
                DatagramPacket pkt = new DatagramPacket(reqBytes, reqBytes.length, broadcast, DISCOVERY_PORT);
                sock.send(pkt);
            } catch (Exception ignored) {
            }

            // 2) Interface-specific broadcast (more reliable on some Windows setups)
            try {
                Enumeration<NetworkInterface> ifaces = NetworkInterface.getNetworkInterfaces();
                while (ifaces != null && ifaces.hasMoreElements()) {
                    NetworkInterface nif = ifaces.nextElement();
                    if (!nif.isUp() || nif.isLoopback()) continue;
                    for (InterfaceAddress ia : nif.getInterfaceAddresses()) {
                        InetAddress bcast = ia.getBroadcast();
                        if (bcast == null) continue;
                        try {
                            DatagramPacket pkt = new DatagramPacket(reqBytes, reqBytes.length, bcast, DISCOVERY_PORT);
                            sock.send(pkt);
                        } catch (Exception ignored) {
                        }
                    }
                }
            } catch (Exception ignored) {
            }

            byte[] buf = new byte[1024];
            DatagramPacket resp = new DatagramPacket(buf, buf.length);
            sock.receive(resp);

            String msg = new String(resp.getData(), resp.getOffset(), resp.getLength(), StandardCharsets.UTF_8);
            if (!msg.startsWith(RESPONSE_PREFIX)) {
                return null;
            }

            String payload = msg.substring(RESPONSE_PREFIX.length());
            String[] parts = payload.split("\\|", -1);
            if (parts.length < 3) {
                return null;
            }

            String respMeetingId = parts[0];
            String ip = parts[1];
            int port;
            try {
                port = Integer.parseInt(parts[2]);
            } catch (Exception e) {
                return null;
            }

            if (!meetingId.equalsIgnoreCase(respMeetingId)) {
                return null;
            }

            System.out.println("📡 LAN discovery found host: " + ip + ":" + port);
            return new MeetingRegistry.HostInfo(ip, port);
        } catch (SocketTimeoutException e) {
            return null;
        }
    }

    public void stopHostResponder() {
        if (serverSocket != null) {
            serverSocket.close();
            serverSocket = null;
        }
        if (serverThread != null) {
            try {
                serverThread.join(200);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            serverThread = null;
        }
    }
}
