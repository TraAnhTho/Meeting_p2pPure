package com.example.dacs4.controllers;

import com.example.dacs4.network.MeetingRegistry;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.DialogPane;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.stage.Stage;

public class JoinMeetingDialogController {
    @FXML private TextField meetingIdField;
    @FXML private TextField hostIpField;
    @FXML private TextField hostPortField;
    @FXML private Button joinButton;
    @FXML private DialogPane dialogPane;

    private Runnable onCancelCallback;
    private java.util.function.Consumer<String> onJoinCallback;

    @FXML
    private void initialize() {
        meetingIdField.textProperty().addListener((obs, oldVal, newVal) -> {
            joinButton.setDisable(newVal.trim().isEmpty());
        });
        joinButton.setDisable(true);
    }

    @FXML
    private void onJoin() {
        String id = meetingIdField.getText().trim();
        String hostIp = hostIpField != null ? hostIpField.getText().trim() : "";
        String hostPortText = hostPortField != null ? hostPortField.getText().trim() : "";

        if (!hostIp.isEmpty() || !hostPortText.isEmpty()) {
            try {
                int hostPort = Integer.parseInt(hostPortText);
                if (!hostIp.isEmpty() && hostPort > 0) {
                    MeetingRegistry.registerMeeting(id, hostIp, hostPort);
                }
            } catch (Exception ignored) {
            }
        }
        if (!id.isEmpty() && onJoinCallback != null) {
            onJoinCallback.accept(id);
            closeDialog();
        }
    }

    @FXML
    private void onCancel() {
        if (onCancelCallback != null) onCancelCallback.run();
        closeDialog();
    }

    @FXML
    private void onKeyPressed(javafx.scene.input.KeyEvent event) {
        if (event.getCode() == KeyCode.ENTER) {
            onJoin();
        }
    }


    public void setOnJoin(java.util.function.Consumer<String> callback) {
        this.onJoinCallback = callback;
    }

    public void setOnCancel(Runnable callback) {
        this.onCancelCallback = callback;
    }

    private void closeDialog() {
        Stage stage = (Stage) dialogPane.getScene().getWindow();
        stage.close();
    }
}
