package com.example.dacs4.network;

import java.io.*;
import java.net.*;

public class PeerHandler {
    private Socket socket;   // Kết nối giữa các peer
    private DataInputStream inputStream;
    private DataOutputStream outputStream;

    // Constructor nhận socket từ PeerConnection
    public PeerHandler(Socket socket) throws IOException {
        this.socket = socket;
        this.inputStream = new DataInputStream(socket.getInputStream());
        this.outputStream = new DataOutputStream(socket.getOutputStream());
    }

    // Gửi dữ liệu đến peer
    public void sendData(String data) {
        try {
            outputStream.writeUTF(data);
            outputStream.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Nhận dữ liệu từ peer
    public String receiveData() {
        try {
            return inputStream.readUTF();
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    // Đóng kết nối với peer
    public void closeConnection() {
        try {
            inputStream.close();
            outputStream.close();
            socket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Phương thức giúp gửi/nhận dữ liệu video, âm thanh hoặc tin nhắn
    public void handlePeerCommunication() {
        // Logic xử lý giao tiếp giữa các peer, có thể là video, tin nhắn, v.v.
    }
}
