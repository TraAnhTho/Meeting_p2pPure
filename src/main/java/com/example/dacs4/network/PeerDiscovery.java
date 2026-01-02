package com.example.dacs4.network;

import java.net.*;

public class PeerDiscovery {
    private String multicastAddress;
    private int port;

    // Constructor nhận tham số địa chỉ multicast và port
    public PeerDiscovery(String multicastAddress, int port) {
        this.multicastAddress = multicastAddress;
        this.port = port;
    }

    // Khám phá các peer trong mạng
    public void discoverPeers() {
        try {
            InetAddress group = InetAddress.getByName(multicastAddress);  // Địa chỉ multicast truyền vào constructor
            MulticastSocket socket = new MulticastSocket(port);
            socket.joinGroup(group);

            // Gửi thông điệp tìm kiếm peer
            String message = "Looking for peers";
            DatagramPacket packet = new DatagramPacket(message.getBytes(), message.length(), group, port);
            socket.send(packet);

            // Nhận phản hồi từ peer
            byte[] buffer = new byte[256];
            DatagramPacket receivePacket = new DatagramPacket(buffer, buffer.length);
            socket.receive(receivePacket);
            System.out.println("Received from: " + new String(receivePacket.getData()));

            socket.leaveGroup(group);
            socket.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
