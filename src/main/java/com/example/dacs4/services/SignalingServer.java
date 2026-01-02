package com.example.dacs4.services;

import java.io.*;
import java.net.*;
import java.util.*;

public class SignalingServer {
    private static final int PORT = 5000;
    private static Map<String, PrintWriter> peers = new HashMap<>();

    public static void main(String[] args) {
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("Signaling server started on port " + PORT);

            // Chờ các peer kết nối
            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("New connection: " + clientSocket.getInetAddress());

                // Tạo một thread mới để xử lý kết nối
                new Thread(new PeerHandler(clientSocket)).start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Xử lý kết nối của mỗi peer
    private static class PeerHandler implements Runnable {
        private Socket socket;
        private PrintWriter out;
        private BufferedReader in;

        public PeerHandler(Socket socket) throws IOException {
            this.socket = socket;
            this.out = new PrintWriter(socket.getOutputStream(), true);
            this.in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        }

        @Override
        public void run() {
            try {
                String peerId = in.readLine(); // Đọc peerId từ client
                peers.put(peerId, out); // Lưu peerId và PrintWriter để gửi tin nhắn sau

                System.out.println("Peer connected: " + peerId);

                String message;
                while ((message = in.readLine()) != null) {
                    System.out.println("Received message from " + peerId + ": " + message);

                    // Gửi tin nhắn đến tất cả các peer
                    for (PrintWriter writer : peers.values()) {
                        writer.println(peerId + ": " + message);
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                try {
                    peers.remove(socket);
                    socket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }
}
