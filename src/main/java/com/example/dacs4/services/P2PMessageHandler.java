package com.example.dacs4.services;

import com.example.dacs4.controllers.ChatController;
import com.example.dacs4.controllers.FileSharingController;
import com.example.dacs4.models.ChatMessage;
import com.example.dacs4.models.MessageType;
import com.example.dacs4.models.P2PMessage;
import com.example.dacs4.network.FileTransferManager;
import com.example.dacs4.network.P2PManager;
import javafx.application.Platform;

import java.time.LocalDateTime;
import java.util.function.BiConsumer;

/**
 * Handler for all P2P message types
 */
public class P2PMessageHandler {

    private final P2PManager p2pManager;
    private final String currentUserId;

    // Controllers
    private ChatController chatController;
    private FileSharingController fileSharingController;
    private FileTransferManager fileTransferManager;

    // Callbacks
    private Runnable onParticipantChanged;
    private Runnable onLeaveMeeting;
    private BiConsumer<String, Boolean> onVideoToggle;
    private BiConsumer<String, Boolean> onAudioToggle;
    private BiConsumer<String, Integer> onScreenShareStart;
    private Runnable onScreenShareStop;

    public P2PMessageHandler(P2PManager p2pManager, String userId) {
        this.p2pManager = p2pManager;
        this.currentUserId = userId;
    }

    // ===== Setters =====

    public void setChatController(ChatController controller) {
        this.chatController = controller;
    }

    public void setFileSharingController(FileSharingController controller) {
        this.fileSharingController = controller;
    }

    public void setFileTransferManager(FileTransferManager manager) {
        this.fileTransferManager = manager;
    }

    public void setOnParticipantChanged(Runnable callback) {
        this.onParticipantChanged = callback;
    }

    public void setOnLeaveMeeting(Runnable callback) {
        this.onLeaveMeeting = callback;
    }

    public void setOnVideoToggle(BiConsumer<String, Boolean> callback) {
        this.onVideoToggle = callback;
    }

    public void setOnAudioToggle(BiConsumer<String, Boolean> callback) {
        this.onAudioToggle = callback;
    }

    public void setOnScreenShareStart(BiConsumer<String, Integer> callback) {
        this.onScreenShareStart = callback;
    }

    public void setOnScreenShareStop(Runnable callback) {
        this.onScreenShareStop = callback;
    }

    // ===== Message Handling =====

    public void handleMessage(P2PMessage message) {
        System.out.println("📥 Handling P2P message: " + message.getType());

        switch (message.getType()) {
            case CHAT_MESSAGE:
                handleChatMessage(message);
                break;
            case MEETING_ENDED:
                handleMeetingEnded();
                break;
            case USER_JOINED:
                handleUserJoined(message);
                break;
            case USER_LEFT:
                handleUserLeft(message);
                break;
            case FILE_SHARE_REQUEST:
                if (fileTransferManager != null)
                    fileTransferManager.handleFileShareRequest(message);
                break;
            case FILE_SHARE_ACCEPT:
                if (fileTransferManager != null)
                    fileTransferManager.handleFileShareAccept(message);
                break;
            case FILE_CHUNK:
                if (fileTransferManager != null)
                    fileTransferManager.handleFileChunk(message);
                break;
            case CHUNK_ACK:
                break;
            case FILE_COMPLETE:
                handleFileComplete(message);
                break;
            case VIDEO_TOGGLE:
                handleVideoToggle(message);
                break;
            case AUDIO_TOGGLE:
                handleAudioToggle(message);
                break;
            case SCREEN_SHARE_START:
                handleScreenShareStart(message);
                break;
            case SCREEN_SHARE_STOP:
                handleScreenShareStop(message);
                break;
            default:
                System.out.println("⚠️ Unhandled message type: " + message.getType());
        }
    }

    // ===== Message Handlers =====

    private void handleChatMessage(P2PMessage message) {
        if (chatController != null) {
            String senderName = message.getPayloadString("senderName");
            String content = message.getPayloadString("content");

            ChatMessage chatMessage = new ChatMessage(
                    message.getFrom(),
                    senderName,
                    content,
                    LocalDateTime.now());

            Platform.runLater(() -> chatController.addMessage(chatMessage));
        }
    }

    private void handleUserJoined(P2PMessage message) {
        String userId = message.getPayloadString("userId");
        String userName = message.getPayloadString("userName");
        System.out.println("👋 User joined: " + userName + " (" + userId + ")");

        // Request updated peer list
        if (p2pManager != null && currentUserId != null) {
            P2PMessage req = new P2PMessage(MessageType.REQUEST_PEER_LIST, currentUserId, "all");
            p2pManager.broadcast(req);
        }

        if (onParticipantChanged != null)
            Platform.runLater(onParticipantChanged);
    }

    private void handleUserLeft(P2PMessage message) {
        String userId = message.getPayloadString("userId");
        System.out.println("👋 User left: " + userId);

        if (onParticipantChanged != null)
            Platform.runLater(onParticipantChanged);
    }

    private void handleMeetingEnded() {
        Platform.runLater(() -> {
            javafx.scene.control.Alert alert = new javafx.scene.control.Alert(
                    javafx.scene.control.Alert.AlertType.INFORMATION);
            alert.setTitle("Cuộc họp đã kết thúc");
            alert.setContentText("Host đã kết thúc cuộc họp.");
            alert.showAndWait();

            if (onLeaveMeeting != null)
                onLeaveMeeting.run();
        });
    }

    private void handleVideoToggle(P2PMessage message) {
        String from = message.getFrom();
        boolean on = Boolean.parseBoolean(message.getPayloadString("isOn"));

        if (onVideoToggle != null) {
            onVideoToggle.accept(from, on);
        }

        if (onParticipantChanged != null) {
            Platform.runLater(onParticipantChanged);
        }
    }

    private void handleAudioToggle(P2PMessage message) {
        String from = message.getFrom();
        boolean on = Boolean.parseBoolean(message.getPayloadString("isOn"));

        if (onAudioToggle != null) {
            onAudioToggle.accept(from, on);
        }

        if (onParticipantChanged != null) {
            Platform.runLater(onParticipantChanged);
        }
    }

    private void handleScreenShareStart(P2PMessage message) {
        String sharerId = message.getFrom();
        System.out.println("🖥 DEBUG: handleScreenShareStart() called");
        System.out.println("👤 DEBUG: Screen share started by: " + sharerId);

        int screenPort = 0;
        try {
            screenPort = Integer.parseInt(message.getPayloadString("screenPort"));
            System.out.println("📡 DEBUG: Screen port: " + screenPort);
        } catch (Exception e) {
            System.err.println("⚠️ WARNING: Could not parse screenPort: " + e.getMessage());
        }

        if (onScreenShareStart != null) {
            System.out.println("✅ DEBUG: Calling onScreenShareStart callback");
            onScreenShareStart.accept(sharerId, screenPort);
        } else {
            System.err.println("❌ ERROR: onScreenShareStart callback is NULL!");
        }

        if (onParticipantChanged != null) {
            Platform.runLater(onParticipantChanged);
        }
    }

    private void handleScreenShareStop(P2PMessage message) {
        String sharerId = message.getFrom();
        System.out.println("🛑 Screen share stopped by: " + sharerId);

        if (onScreenShareStop != null) {
            onScreenShareStop.run();
        }

        if (onParticipantChanged != null) {
            Platform.runLater(onParticipantChanged);
        }
    }

    private void handleFileComplete(P2PMessage message) {
        System.out.println("🔍 DEBUG: handleFileComplete() called");

        if (fileSharingController == null) {
            System.err.println("❌ ERROR: fileSharingController is NULL!");
            return;
        }
        System.out.println("✅ DEBUG: fileSharingController is available");

        String fileName = message.getPayloadString("fileName");
        if (fileName == null) {
            System.err.println("❌ ERROR: fileName is null in message payload");
            return;
        }
        System.out.println("📄 DEBUG: fileName = " + fileName);

        // Get sender info from message (the person who sent the file)
        String senderId = message.getFrom();
        String senderName = message.getPayloadString("senderName");
        System.out.println("👤 DEBUG: senderId = " + senderId + ", senderName = " + senderName);

        // If senderName not in payload, try to get from P2PManager
        if (senderName == null && p2pManager != null) {
            var peers = p2pManager.getPeers();
            if (peers.containsKey(senderId)) {
                senderName = peers.get(senderId).getUserName();
                System.out.println("✅ DEBUG: Got senderName from P2PManager: " + senderName);
            }
        }

        if (senderName == null) {
            senderName = senderId; // Fallback to ID if name not available
            System.out.println("⚠️ DEBUG: Using senderId as fallback for senderName");
        }

        ChatMessage fileMsg = new ChatMessage(
                null,
                senderId,
                senderName,
                fileName,
                LocalDateTime.now(),
                ChatMessage.Type.FILE,
                fileName);

        System.out.println("📦 DEBUG: Created ChatMessage for file");
        System.out.println("🚀 DEBUG: Calling fileSharingController.addFileMessage() on JavaFX thread...");

        Platform.runLater(() -> {
            System.out.println("🎯 DEBUG: Now on JavaFX thread, calling addFileMessage()");
            fileSharingController.addFileMessage(fileMsg);
        });
    }

    // ===== Broadcast Methods =====

    public void broadcastVideoToggle(boolean isOn) {
        if (p2pManager == null || currentUserId == null)
            return;
        P2PMessage msg = new P2PMessage(MessageType.VIDEO_TOGGLE, currentUserId, "all");
        msg.addPayload("isOn", String.valueOf(isOn));
        p2pManager.broadcast(msg);
    }

    public void broadcastAudioToggle(boolean isOn) {
        if (p2pManager == null || currentUserId == null)
            return;
        P2PMessage msg = new P2PMessage(MessageType.AUDIO_TOGGLE, currentUserId, "all");
        msg.addPayload("isOn", String.valueOf(isOn));
        p2pManager.broadcast(msg);
    }

    public void broadcastScreenShareStart(int screenPort) {
        if (p2pManager == null || currentUserId == null)
            return;
        P2PMessage msg = new P2PMessage(MessageType.SCREEN_SHARE_START, currentUserId, "all");
        msg.addPayload("screenPort", String.valueOf(screenPort));
        p2pManager.broadcast(msg);
    }

    public void broadcastScreenShareStop() {
        if (p2pManager == null || currentUserId == null)
            return;
        P2PMessage msg = new P2PMessage(MessageType.SCREEN_SHARE_STOP, currentUserId, "all");
        p2pManager.broadcast(msg);
    }
}
