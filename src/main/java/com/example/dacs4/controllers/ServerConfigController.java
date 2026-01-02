package com.example.dacs4.controllers;

import com.example.dacs4.App;
import com.example.dacs4.network.PeerDiscovery;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;

import java.util.function.BiConsumer;

public class ServerConfigController {

    @FXML
    private TextField ipField;
    @FXML
    private TextField portField;
    @FXML
    private Label errorLabel;

    private BiConsumer<String, Integer> onConnectCallback;

    public void setOnConnect(BiConsumer<String, Integer> callback) {
        this.onConnectCallback = callback;
    }

    @FXML
    private void onConnectClicked() {
        String ip = ipField.getText().trim();
        String portStr = portField.getText().trim();

        if (ip.isEmpty()) {
            errorLabel.setText("Vui lòng nhập IP server");
            return;
        }

        int port;
        try {
            port = Integer.parseInt(portStr);
        } catch (Exception e) {
            errorLabel.setText("Port không hợp lệ");
            return;
        }

        try {
            // PeerDiscovery cần multicastAddress và port
            PeerDiscovery client = new PeerDiscovery(ip, port);

            // Gọi discoverPeers() thay vì connect()
            client.discoverPeers();

            App.setSocketClient(client);

            System.out.println("Connected to server!");
            System.out.println("=== GO TO LOGIN ===");
            App.goToLogin();

            if (onConnectCallback != null)
                onConnectCallback.accept(ip, port);

        } catch (Exception e) {
            errorLabel.setText("Không thể kết nối server!");
            e.printStackTrace();
        }
    }
}
