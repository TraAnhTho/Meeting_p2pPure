package com.example.dacs4.controllers;

import com.example.dacs4.network.LanMeetingMulticast;
import com.example.dacs4.services.MeetingListManager;
import com.example.dacs4.services.MeetingMulticastListener;
import com.example.dacs4.services.MeetingService;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.io.IOException;
import java.util.function.Consumer;

/**
 * Refactored Dashboard Controller using service classes
 */
public class DashboardController {

    @FXML
    private Label avatarInitialLabel;
    @FXML
    private Label userNameLabel;
    @FXML
    private Label userEmailLabel;
    @FXML
    private VBox upcomingMeetingsContainer;

    // Services
    private MeetingService meetingService;
    private MeetingListManager listManager;
    private MeetingMulticastListener multicastListener;

    // Callbacks
    private Consumer<String> joinMeetingHandler;
    private Runnable logoutHandler;

    // User info
    private String userId;
    private String userName;
    private String userEmail;
    private String userAvatar;

    @FXML
    private void initialize() {
        System.out.println("🟢 DashboardController.initialize() called");

        // Initialize services
        meetingService = new MeetingService();
        listManager = new MeetingListManager(upcomingMeetingsContainer);
        multicastListener = new MeetingMulticastListener();

        // Setup callbacks
        listManager.setOnJoinMeeting(this::handleJoinMeeting);
        listManager.setOnEndMeeting(this::handleEndMeeting);

        multicastListener.setOnMeetingClosed(meetingId -> {
            System.out.println("📡 Meeting closed: " + meetingId);
            refreshMeetingList();
        });

        multicastListener.setOnParticipantCountUpdate((meetingId, count) -> {
            listManager.updateParticipantCount(meetingId, count);
        });
    }

    // ===== Public API =====

    public void setUser(String id, String name, String email, String avatarUrl) {
        this.userId = id;
        this.userName = name;
        this.userEmail = email;
        this.userAvatar = avatarUrl;

        // Update UI
        userNameLabel.setText(name);
        userEmailLabel.setText(email);
        if (name != null && !name.isEmpty()) {
            avatarInitialLabel.setText(name.substring(0, 1).toUpperCase());
        }

        System.out.println("Dashboard nhận user: " + name + " (ID: " + id + ")");

        // Load meetings and start multicast listener
        refreshMeetingList();
        multicastListener.start();
    }

    public void setOnJoinMeeting(Consumer<String> handler) {
        this.joinMeetingHandler = handler;
    }

    public void setOnLogout(Runnable handler) {
        this.logoutHandler = handler;
    }

    // ===== FXML Event Handlers =====

    @FXML
    private void onCreateNewMeeting() {
        String meetingId = meetingService.showCreateMeetingDialog(userId);
        if (meetingId != null) {
            // Refresh dashboard
            System.out.println("🔄 Refreshing dashboard after new meeting creation");
            Platform.runLater(this::refreshMeetingList);

            // Navigate to meeting room
            System.out.println("🚀 Navigating to meeting room: " + meetingId);
            if (joinMeetingHandler != null) {
                joinMeetingHandler.accept(meetingId);
            }
        }
    }

    @FXML
    private void onOpenJoinMeetingDialog() {
        System.out.println("🟡 onOpenJoinMeetingDialog() called");
        try {
            // Load join meeting dialog
            java.net.URL url = DashboardController.class.getResource("/fxml/joinMeetingDialog.fxml");
            if (url == null) {
                url = DashboardController.class.getResource("fxml/joinMeetingDialog.fxml");
            }

            if (url == null) {
                ClassLoader cl = Thread.currentThread().getContextClassLoader();
                if (cl != null) {
                    url = cl.getResource("fxml/joinMeetingDialog.fxml");
                }
            }

            if (url == null) {
                System.err.println("[WARN] joinMeetingDialog.fxml not found on classpath");
                // Try absolute paths
                java.io.File f1 = new java.io.File("src/main/resources/fxml/joinMeetingDialog.fxml");
                if (f1.exists()) {
                    url = f1.toURI().toURL();
                }
            }

            if (url == null) {
                System.err.println("[ERROR] Cannot find joinMeetingDialog.fxml");
                return;
            }

            FXMLLoader loader = new FXMLLoader(url);
            DialogPane dialogPane = loader.load();

            JoinMeetingDialogController controller = loader.getController();
            controller.setOnJoin(meetingId -> {
                // Save meeting history
                meetingService.joinMeeting(userId, meetingId);

                // Navigate to meeting room
                if (joinMeetingHandler != null) {
                    joinMeetingHandler.accept(meetingId);
                }
            });

            Dialog<Void> dialog = new Dialog<>();
            dialog.setDialogPane(dialogPane);
            dialog.setTitle("Tham gia cuộc họp");
            dialog.showAndWait();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @FXML
    private void onLogoutClicked() {
        System.out.println("🔴 onLogoutClicked() called");

        // Stop multicast listener
        if (multicastListener != null) {
            multicastListener.stop();
        }

        if (logoutHandler != null) {
            logoutHandler.run();
        }
    }

    // ===== Private Methods =====

    private void refreshMeetingList() {
        if (listManager != null && userId != null) {
            listManager.renderMeetings(userId);
        }
    }

    private void handleJoinMeeting(String meetingId) {
        if (joinMeetingHandler != null) {
            joinMeetingHandler.accept(meetingId);
        }
    }

    private void handleEndMeeting(String meetingId) {
        // End meeting in database
        meetingService.endMeeting(meetingId);

        // Broadcast to LAN
        try {
            LanMeetingMulticast mc = new LanMeetingMulticast();
            mc.announceClosed(meetingId);
            System.out.println("📡 Broadcasted meeting closed: " + meetingId);
        } catch (Exception e) {
            System.err.println("❌ Error broadcasting meeting closed: " + e.getMessage());
        }

        // Refresh list
        refreshMeetingList();
    }
}
