package com.example.dacs4.network;

import javax.sound.sampled.*;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class VoiceEngine {
    private final int localPort;
    private DatagramSocket socket;
    private TargetDataLine mic;
    private SourceDataLine speaker;
    private volatile boolean running = false;

    private final Map<String, InetSocketAddress> remoteTargets = new ConcurrentHashMap<>();

    private static DatagramSocket bindUdpSocket(int port) throws SocketException {
        DatagramSocket s = new DatagramSocket(null);
        s.setReuseAddress(true);
        s.bind(new java.net.InetSocketAddress(port));
        return s;
    }

    public VoiceEngine(int localPort) throws LineUnavailableException, SocketException {
        this.localPort = localPort;
        socket = bindUdpSocket(localPort);
        AudioFormat fmt = new AudioFormat(16000.0f, 16, 1, true, false);

        DataLine.Info micInfo = new DataLine.Info(TargetDataLine.class, fmt);
        DataLine.Info spkInfo = new DataLine.Info(SourceDataLine.class, fmt);
        mic = (TargetDataLine) AudioSystem.getLine(micInfo);
        mic.open(fmt);

        speaker = (SourceDataLine) AudioSystem.getLine(spkInfo);
        speaker.open(fmt);
    }

    public void start(String remoteIp, int remotePort) {
        clearRemoteTargets();
        addRemoteTarget("default", remoteIp, remotePort);
        start();
    }

    public void addRemoteTarget(String peerId, String remoteIp, int remotePort) {
        if (peerId == null || remoteIp == null || remoteIp.isBlank() || remotePort <= 0) return;
        try {
            InetAddress addr = InetAddress.getByName(remoteIp);
            remoteTargets.put(peerId, new InetSocketAddress(addr, remotePort));
        } catch (Exception ignored) {
        }
    }

    public void removeRemoteTarget(String peerId) {
        if (peerId == null) return;
        remoteTargets.remove(peerId);
    }

    public void clearRemoteTargets() {
        remoteTargets.clear();
    }

    public void start() {
        if (running) {
            return;
        }

        // Tạo lại socket nếu đã bị đóng hoặc null
        if (socket == null || socket.isClosed()) {
            try {
                socket = bindUdpSocket(localPort);
            } catch (SocketException e) {
                e.printStackTrace();
                return;
            }
        }

        // Tạo lại audio lines nếu đã bị đóng hoặc null
        try {
            AudioFormat fmt = new AudioFormat(16000.0f, 16, 1, true, false);
            if (mic == null || !mic.isOpen()) {
                DataLine.Info micInfo = new DataLine.Info(TargetDataLine.class, fmt);
                mic = (TargetDataLine) AudioSystem.getLine(micInfo);
                mic.open(fmt);
            }
            if (speaker == null || !speaker.isOpen()) {
                DataLine.Info spkInfo = new DataLine.Info(SourceDataLine.class, fmt);
                speaker = (SourceDataLine) AudioSystem.getLine(spkInfo);
                speaker.open(fmt);
            }
        } catch (LineUnavailableException e) {
            e.printStackTrace();
            return;
        }

        running = true;
        mic.start();
        speaker.start();

        // sender: send to all known targets (mesh)
        new Thread(() -> {
            byte[] buf = new byte[2048];
            try {
                while (running) {
                    int r = mic.read(buf, 0, buf.length);
                    if (r <= 0) continue;

                    for (InetSocketAddress target : remoteTargets.values()) {
                        if (target == null) continue;
                        DatagramPacket p = new DatagramPacket(buf, r, target.getAddress(), target.getPort());
                        socket.send(p);
                    }
                }
            } catch (Exception e) {
                if (running) e.printStackTrace();
            }
        }, "voice-send").start();

        // receiver
        new Thread(() -> {
            byte[] buf = new byte[2048];
            DatagramPacket p = new DatagramPacket(buf, buf.length);
            try {
                while (running) {
                    socket.receive(p);
                    speaker.write(p.getData(), 0, p.getLength());
                }
            } catch (Exception e) {
                if (running) e.printStackTrace();
            }
        }, "voice-recv").start();
    }

    public void stop() {
        running = false;
        try { mic.stop(); mic.close(); } catch (Exception ignored) {}
        try { speaker.stop(); speaker.close(); } catch (Exception ignored) {}
        try {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (Exception ignored) {
        }
        socket = null;
    }

    /**
     * Đóng socket hoàn toàn. Chỉ gọi khi PeerHandle shutdown.
     */
    public void shutdown() {
        running = false;
        try { mic.stop(); mic.close(); } catch (Exception ignored) {}
        try { speaker.stop(); speaker.close(); } catch (Exception ignored) {}
        try { 
            if (socket != null && !socket.isClosed()) {
                socket.close(); 
            }
        } catch (Exception ignored) {}
        socket = null;
    }

    public int getLocalPort() { return localPort; }
}