package com.example.dacs4.controllers;

import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

/**
 * Helper controller để quản lý hiển thị/ẩn side panel (Chat / Participants / File Sharing)
 * cho MeetingRoomController. Không phải FXML controller độc lập.
 */
public class SidePanelController {

    private final VBox sidePanel;
    private final StackPane chatPanelRoot;
    private final StackPane participantsPanelRoot;
    private final StackPane fileSharingPanelRoot;

    private boolean showChat = false;
    private boolean showParticipants = false;
    private boolean showFileSharing = false;

    public SidePanelController(VBox sidePanel,
                               StackPane chatPanelRoot,
                               StackPane participantsPanelRoot,
                               StackPane fileSharingPanelRoot) {
        this.sidePanel = sidePanel;
        this.chatPanelRoot = chatPanelRoot;
        this.participantsPanelRoot = participantsPanelRoot;
        this.fileSharingPanelRoot = fileSharingPanelRoot;

        updateSidePanel();
    }

    public void toggleChat() {
        showChat = !showChat;
        showParticipants = false;
        showFileSharing = false;
        updateSidePanel();
    }

    public void toggleParticipants() {
        showParticipants = !showParticipants;
        showChat = false;
        showFileSharing = false;
        updateSidePanel();
    }

    public void toggleFileSharing() {
        showFileSharing = !showFileSharing;
        showChat = false;
        showParticipants = false;
        updateSidePanel();
    }

    private void updateSidePanel() {
        boolean any = showChat || showParticipants || showFileSharing;
        sidePanel.setVisible(any);
        sidePanel.setManaged(any);

        chatPanelRoot.setVisible(showChat);
        chatPanelRoot.setManaged(showChat);

        participantsPanelRoot.setVisible(showParticipants);
        participantsPanelRoot.setManaged(showParticipants);

        fileSharingPanelRoot.setVisible(showFileSharing);
        fileSharingPanelRoot.setManaged(showFileSharing);
    }
}
