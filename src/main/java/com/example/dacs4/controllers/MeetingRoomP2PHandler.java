package com.example.dacs4.controllers;

import com.example.dacs4.models.*;
import com.example.dacs4.network.VideoEngine;
import com.example.dacs4.network.VoiceEngine;
import com.example.dacs4.network.FileTransferManager;
import com.example.dacs4.network.P2PManager;
import com.example.dacs4.network.WebcamVideoSource;
import com.example.dacs4.network.ScreenShareSource;
import javafx.scene.image.Image;
import javafx.application.Platform;
import javafx.embed.swing.SwingFXUtils;

import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.awt.image.BufferedImage;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** P2P Handler - Quản lý P2P logic cho MeetingRoom */
public class MeetingRoomP2PHandler {

    private static final int BASE_PORT = 5000;
    private static final int VOICE_BASE_PORT = 6000;
    private static final int VIDEO_BASE_PORT = 7000;
    private static final int SCREEN_BASE_PORT = 8000;

    private P2PManager p2pManager;
    private VoiceEngine voiceEngine;
    private VideoEngine videoEngine; // camera
    private VideoEngine screenEngine; // screen share
    private WebcamVideoSource webcamSource;
    private ScreenShareSource screenSource;
    private FileTransferManager fileTransferManager;
    private VideoGridController videoGridController;
    private boolean isAudioStreaming = false;
    private boolean isVideoStreaming = false;
    private boolean isScreenSharing = false;
    private volatile String currentScreenSharerId = null; // who is currently sharing
    private ScreenFrameBroadcaster screenBroadcaster;

    private boolean connected = false;
    private boolean autoAudioStarted = false;

    private String meetingId;
    private String currentUserId;
    private String currentUserName;
    private String userRole;

    private Runnable onParticipantChanged;
    private Runnable onLeaveMeeting;
    private java.util.function.BiConsumer<String, Image> onRemoteVideoFrame;
    private ChatController chatController;
    private FileSharingController fileSharingController;
    private MeetingControlsController meetingControlsController;
    private boolean suppressControlEvents = false;

    public void setVideoGridController(VideoGridController controller) {
        this.videoGridController = controller;
    }

    public void initialize() {
        try {
            p2pManager = new P2PManager();
            fileTransferManager = new FileTransferManager(p2pManager);
            fileTransferManager.setOnFileReceivedUI(fileName -> {
                if (fileSharingController != null) {
                    Platform.runLater(() -> {
                        ChatMessage fileMsg = new ChatMessage(
                                null,
                                null,
                                "", // senderName không quan trọng phía nhận trong danh sách file
                                fileName,
                                LocalDateTime.now(),
                                ChatMessage.Type.FILE,
                                fileName);
                        fileSharingController.addFileMessage(fileMsg);
                    });
                }
            });
            setupListeners();
            System.out.println("✅ P2P Handler initialized");
        } catch (Exception e) {
            System.err.println("❌ Error initializing P2P: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void setupListeners() {
        p2pManager.addPeerJoinedListener(peerInfo -> {
            Platform.runLater(() -> {
                System.out.println("✅ Peer joined: " + peerInfo.getUserName());
                if (isAudioStreaming) {
                    syncAudioTargets();
                }
                if (onParticipantChanged != null)
                    onParticipantChanged.run();
            });
        });

        p2pManager.addPeerLeftListener(peerId -> {
            Platform.runLater(() -> {
                System.out.println("❌ Peer left: " + peerId);
                if (isAudioStreaming) {
                    syncAudioTargets();
                }
                if (onParticipantChanged != null)
                    onParticipantChanged.run();
            });
        });

        p2pManager.addMessageListener(message -> {
            Platform.runLater(() -> handleP2PMessage(message));
        });
    }

    public void startConnection(String meetingId, String userId, String userName, String role) {
        this.meetingId = meetingId;
        this.currentUserId = userId;
        this.currentUserName = userName;
        this.userRole = role;

        connected = false;

        if (p2pManager == null) {
            System.err.println("❌ P2P Manager not initialized");
            return;
        }

        try {
            // For host: use meetingId to determine port so participants can find it
            // For participant: use userId to determine port (doesn't matter as much)
            int myPort = "creator".equals(role)
                    ? BASE_PORT + Math.abs(meetingId.hashCode() % 1000)
                    : BASE_PORT + Math.abs(userId.hashCode() % 1000);

            if ("creator".equals(role)) {
                p2pManager.createMeeting(meetingId, userId, userName, myPort);
                int actualPort = p2pManager.getListeningPort();
                System.out.println("🎯 Created meeting on port " + actualPort);
                connected = true;
            } else {
                // Retry logic for joining meeting
                int maxRetries = 3;
                int retryDelay = 2000; // 2 seconds
                boolean joined = false;

                for (int attempt = 1; attempt <= maxRetries; attempt++) {
                    try {
                        p2pManager.joinMeeting(meetingId, userId, userName, myPort);
                        System.out.println("🔗 Joined meeting on port " + myPort);
                        joined = true;

                        // Ask host for full peer list so we can sync participants and connect mesh
                        P2PMessage req = new P2PMessage(MessageType.REQUEST_PEER_LIST, currentUserId, "all");
                        broadcastMessage(req);
                        break;
                    } catch (IOException e) {
                        String msg = e.getMessage() != null ? e.getMessage() : "";

                        boolean retryable = msg.contains("Meeting not found")
                                || e instanceof java.net.ConnectException
                                || msg.contains("Connection refused");

                        if (retryable) {
                            System.out.println("⏳ Attempt " + attempt + "/" + maxRetries
                                    + ": Host meeting not ready yet (" + msg + "), retrying in "
                                    + (retryDelay / 1000) + "s...");
                            if (attempt < maxRetries) {
                                try {
                                    Thread.sleep(retryDelay);
                                } catch (InterruptedException ie) {
                                    Thread.currentThread().interrupt();
                                }
                            }
                        } else {
                            throw e;
                        }
                    }
                }

                if (!joined) {
                    showMeetingNotFoundAlert(meetingId);
                    throw new IOException("❌ Không thể tham gia cuộc họp " + meetingId
                            + ". Host có thể chưa tạo cuộc họp hoặc đã kết thúc.");
                }

                connected = true;
            }

            // Always start the UDP receiver so we can display remote video even if local
            // camera is off
            int myVideoPort = VIDEO_BASE_PORT + Math.abs(userId.hashCode() % 1000);
            if (videoEngine == null) {
                videoEngine = new VideoEngine(myVideoPort);
                System.out.println("📹 VideoEngine initialized on port " + myVideoPort);
            }
            try {
                videoEngine.startReceiver();

                // Always listen for incoming frames (even if local camera is off)
                videoEngine.setFrameListener((data, from, fromPort) -> {
                    String remoteId = resolvePeerIdByVideoPort(fromPort);
                    if (remoteId == null) {
                        return;
                    }
                    try (ByteArrayInputStream bais = new ByteArrayInputStream(data)) {
                        BufferedImage bimg = ImageIO.read(bais);
                        if (bimg != null) {
                            Image fxImg = SwingFXUtils.toFXImage(bimg, null);
                            if (p2pManager != null) {
                                p2pManager.setPeerVideoOn(remoteId, true);
                            }
                            if (onRemoteVideoFrame != null) {
                                Platform.runLater(() -> onRemoteVideoFrame.accept(remoteId, fxImg));
                            }
                            if (onParticipantChanged != null) {
                                Platform.runLater(onParticipantChanged);
                            }
                        }
                    } catch (IOException e) {
                        System.err.println("❌ Error decoding video frame: " + e.getMessage());
                    }
                });
            } catch (Exception e) {
                System.err.println("❌ Error starting video receiver: " + e.getMessage());
            }

            // Start screen share receiver (always listening)
            int myScreenPort = SCREEN_BASE_PORT + Math.abs(userId.hashCode() % 1000);
            if (screenEngine == null) {
                screenEngine = new VideoEngine(myScreenPort);
                System.out.println("🖥 ScreenEngine initialized on port " + myScreenPort);
            }
            try {
                screenEngine.startReceiver();
                screenEngine.setFrameListener((data, from, fromPort) -> {
                    String remoteId = resolvePeerIdByScreenPort(fromPort);
                    if (remoteId == null) {
                        // Try to resolve by checking all peers by IP
                        System.out.println("⚠️ Could not resolve peer by screen port: " + fromPort);
                        for (PeerInfo peer : p2pManager.getPeers().values()) {
                            if (peer != null && peer.getIpAddress() != null) {
                                try {
                                    if (java.net.InetAddress.getByName(peer.getIpAddress()).equals(from)) {
                                        remoteId = peer.getUserId();
                                        break;
                                    }
                                } catch (Exception ignored) {
                                }
                            }
                        }
                        if (remoteId == null) {
                            return;
                        }
                    }
                    // Decode JPEG bytes to JavaFX Image
                    Image fxImg = decodeJpegToFxImage(data);
                    if (fxImg != null && videoGridController != null) {
                        // Create final variable for lambda
                        final String finalRemoteId = remoteId;
                        final Image finalFxImg = fxImg;
                        Platform.runLater(() -> {
                            videoGridController.showScreen(finalRemoteId, finalFxImg);
                            currentScreenSharerId = finalRemoteId;
                        });
                    } else {
                        System.err.println("❌ Failed to decode screen frame from " + remoteId);
                    }
                });
                System.out.println("✅ Screen receiver started on port " + myScreenPort);
            } catch (Exception e) {
                System.err.println("❌ Error starting screen receiver: " + e.getMessage());
            }

            // Auto-start mic after P2P is ready (default mic ON in UI)
            if (connected && !autoAudioStarted) {
                autoAudioStarted = true;
                startAudioStreaming();
                broadcastAudioToggle(true);
            }
        } catch (Exception e) {
            System.err.println("❌ Error starting P2P connection: " + e.getMessage());
            e.printStackTrace();
            connected = false;
        }
    }

    private String resolvePeerIdByVideoPort(int fromPort) {
        if (p2pManager == null)
            return null;
        for (PeerInfo peer : p2pManager.getPeers().values()) {
            if (peer == null || peer.getUserId() == null)
                continue;
            int expected = VIDEO_BASE_PORT + Math.abs(peer.getUserId().hashCode() % 1000);
            if (expected == fromPort) {
                return peer.getUserId();
            }
        }
        return null;
    }

    private String resolvePeerIdByScreenPort(int fromPort) {
        if (p2pManager == null)
            return null;
        for (PeerInfo peer : p2pManager.getPeers().values()) {
            if (peer == null || peer.getUserId() == null)
                continue;
            int expected = SCREEN_BASE_PORT + Math.abs(peer.getUserId().hashCode() % 1000);
            if (expected == fromPort) {
                return peer.getUserId();
            }
        }
        return null;
    }

    private static Image decodeJpegToFxImage(byte[] jpegBytes) {
        try (ByteArrayInputStream bais = new ByteArrayInputStream(jpegBytes)) {
            BufferedImage bimg = ImageIO.read(bais);
            if (bimg != null) {
                return SwingFXUtils.toFXImage(bimg, null);
            }
        } catch (IOException e) {
            System.err.println("❌ Error decoding JPEG: " + e.getMessage());
        }
        return null;
    }

    private void showMeetingNotFoundAlert(String meetingId) {
        javafx.application.Platform.runLater(() -> {
            javafx.scene.control.Alert alert = new javafx.scene.control.Alert(
                    javafx.scene.control.Alert.AlertType.ERROR);
            alert.setTitle("Không tìm thấy cuộc họp");
            alert.setHeaderText("Không thể tham gia cuộc họp " + meetingId);
            alert.setContentText(
                    "Host có thể chưa tạo cuộc họp hoặc đã kết thúc.\nVui lòng kiểm tra lại mã cuộc họp hoặc liên hệ với host.");
            alert.showAndWait();
            if (onLeaveMeeting != null)
                onLeaveMeeting.run();
        });
    }

    public void stop() {
        System.out.println("🛑 Stopping P2P Handler...");
        stopAudioStreaming();
        stopVideoStreaming();
        stopScreenShare();
        if (videoEngine != null) {
            videoEngine.stop();
        }
        if (screenEngine != null) {
            screenEngine.stop();
        }
        if (p2pManager != null) {
            p2pManager.stop();
            p2pManager = null;
        }
        voiceEngine = null;
        videoEngine = null;
        screenEngine = null;
        meetingId = null;
        currentUserId = null;
        currentUserName = null;
        userRole = null;
        isAudioStreaming = false;
        isVideoStreaming = false;
        isScreenSharing = false;
        currentScreenSharerId = null;
        connected = false;
        autoAudioStarted = false;
    }

    public boolean isConnected() {
        return connected;
    }

    private void handleP2PMessage(P2PMessage message) {
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

    private void handleVideoToggle(P2PMessage message) {
        String from = message.getFrom();
        boolean on = Boolean.parseBoolean(message.getPayloadString("isOn"));
        if (p2pManager != null && from != null) {
            p2pManager.setPeerVideoOn(from, on);
        }
        if (onParticipantChanged != null) {
            onParticipantChanged.run();
        }
    }

    private void handleAudioToggle(P2PMessage message) {
        String from = message.getFrom();
        boolean on = Boolean.parseBoolean(message.getPayloadString("isOn"));
        if (p2pManager != null && from != null) {
            p2pManager.setPeerAudioOn(from, on);
        }
        if (onParticipantChanged != null) {
            onParticipantChanged.run();
        }
    }

    public void broadcastVideoToggle(boolean isOn) {
        if (p2pManager == null || currentUserId == null)
            return;
        P2PMessage msg = new P2PMessage(MessageType.VIDEO_TOGGLE, currentUserId, "all");
        msg.addPayload("isOn", String.valueOf(isOn));
        broadcastMessage(msg);
    }

    public void broadcastAudioToggle(boolean isOn) {
        if (p2pManager == null || currentUserId == null)
            return;
        P2PMessage msg = new P2PMessage(MessageType.AUDIO_TOGGLE, currentUserId, "all");
        msg.addPayload("isOn", String.valueOf(isOn));
        broadcastMessage(msg);
    }

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

        // Request updated peer list so clients can connect mesh to missing peers
        if (p2pManager != null && currentUserId != null) {
            P2PMessage req = new P2PMessage(MessageType.REQUEST_PEER_LIST, currentUserId, "all");
            broadcastMessage(req);
        }
        if (onParticipantChanged != null)
            onParticipantChanged.run();
    }

    private void handleUserLeft(P2PMessage message) {
        String userId = message.getPayloadString("userId");
        System.out.println("👋 User left: " + userId);
        if (onParticipantChanged != null)
            onParticipantChanged.run();
    }

    private void handleMeetingEnded() {
        javafx.scene.control.Alert alert = new javafx.scene.control.Alert(
                javafx.scene.control.Alert.AlertType.INFORMATION);
        alert.setTitle("Cuộc họp đã kết thúc");
        alert.setContentText("Host đã kết thúc cuộc họp.");
        alert.showAndWait();
        if (onLeaveMeeting != null)
            onLeaveMeeting.run();
    }

    public void startAudioStreaming() {
        if (isAudioStreaming || p2pManager == null || currentUserId == null)
            return;

        try {
            if (voiceEngine == null) {
                int myVoicePort = VOICE_BASE_PORT + Math.abs(currentUserId.hashCode() % 1000);
                voiceEngine = new VoiceEngine(myVoicePort);
                System.out.println("🎤 VoiceEngine initialized on port " + myVoicePort);
            }

            syncAudioTargets();
            voiceEngine.start();
            isAudioStreaming = true;
            System.out.println("🎤 Audio streaming started (mesh)");
        } catch (Exception e) {
            System.err.println("❌ Error starting audio: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void syncAudioTargets() {
        if (voiceEngine == null || p2pManager == null)
            return;

        voiceEngine.clearRemoteTargets();
        for (PeerInfo peer : p2pManager.getPeers().values()) {
            if (peer == null || peer.getUserId() == null)
                continue;
            String ip = peer.getIpAddress();
            if (ip == null || ip.isBlank())
                continue;

            int peerVoicePort = VOICE_BASE_PORT + Math.abs(peer.getUserId().hashCode() % 1000);
            voiceEngine.addRemoteTarget(peer.getUserId(), ip, peerVoicePort);
        }
    }

    public void stopAudioStreaming() {
        if (voiceEngine != null && isAudioStreaming) {
            voiceEngine.stop();
            isAudioStreaming = false;
            System.out.println("🛑 Audio streaming stopped");
        }
    }

    public void startVideoStreaming() {
        if (isVideoStreaming || p2pManager == null || currentUserId == null)
            return;

        try {
            if (videoEngine == null) {
                int myVideoPort = VIDEO_BASE_PORT + Math.abs(currentUserId.hashCode() % 1000);
                videoEngine = new VideoEngine(myVideoPort);
                System.out.println("📹 VideoEngine initialized on port " + myVideoPort);
            }

            var peers = p2pManager.getPeers();
            if (!peers.isEmpty()) {
                PeerInfo firstPeer = peers.values().iterator().next();
                int peerVideoPort = VIDEO_BASE_PORT + Math.abs(firstPeer.getUserId().hashCode() % 1000);
                String peerIp = firstPeer.getIpAddress() != null ? firstPeer.getIpAddress() : "127.0.0.1";
                videoEngine.start(peerIp, peerVideoPort);

                if (webcamSource == null) {
                    webcamSource = new WebcamVideoSource();
                }

                if (webcamSource.isAvailable()) {
                    webcamSource.setLocalFrameCallback(fxImage -> {
                        if (fxImage == null)
                            return;
                        if (onRemoteVideoFrame != null && currentUserId != null) {
                            Platform.runLater(() -> onRemoteVideoFrame.accept(currentUserId, fxImage));
                        }
                        if (videoGridController != null && currentUserId != null) {
                            Platform.runLater(() -> videoGridController.updateVideoFrame(currentUserId, fxImage));
                        }
                    });

                    webcamSource.setEncodedFrameCallback(bytes -> {
                        if (bytes != null && videoEngine != null) {
                            videoEngine.sendFrame(bytes);
                        }
                    });

                    webcamSource.start();
                    System.out.println("📷 Webcam capture started");
                } else {
                    System.out.println("⚠️ No webcam available, video will not send frames");
                }

                isVideoStreaming = true;
                System.out.println("📹 Video streaming started (real webcam frames)");
            }
        } catch (Exception e) {
            System.err.println("❌ Error starting video: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void stopVideoStreaming() {
        if (isVideoStreaming) {
            if (webcamSource != null) {
                webcamSource.stop();
                System.out.println("📷 Webcam capture stopped");
            }
            isVideoStreaming = false;
            System.out.println("🛑 Video streaming stopped");
        }
    }

    public void setOnRemoteVideoFrame(java.util.function.BiConsumer<String, Image> callback) {
        this.onRemoteVideoFrame = callback;
    }

    public void sendFile(File file, String senderId, String senderName) {
        if (fileTransferManager == null || file == null || senderId == null || senderName == null)
            return;

        try {
            fileTransferManager.sendFile(file, senderId, senderName);
        } catch (IOException e) {
            System.err.println("❌ Error sending file: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public List<Participant> getParticipants() {
        List<Participant> participantList = new ArrayList<>();
        if (currentUserId != null && currentUserName != null) {
            participantList.add(new Participant(currentUserId, currentUserName, true, true, false));
        }
        if (p2pManager != null) {
            for (PeerInfo peer : p2pManager.getPeers().values()) {
                participantList.add(new Participant(peer.getUserId(), peer.getUserName(), peer.isAudioOn(),
                        peer.isVideoOn(), false));
            }
        }
        return participantList;
    }

    public int getParticipantCount() {
        return p2pManager != null ? 1 + p2pManager.getPeers().size() : 1;
    }

    public void broadcastMeetingEnded() {
        if (p2pManager != null) {
            P2PMessage endMessage = new P2PMessage(MessageType.MEETING_ENDED, currentUserId, "all");
            endMessage.setMeetingId(meetingId);
            p2pManager.broadcast(endMessage);
            System.out.println("📤 Broadcasted MEETING_ENDED");
        }
    }

    public void broadcastMessage(P2PMessage message) {
        if (p2pManager != null) {
            message.setMeetingId(meetingId);
            p2pManager.broadcast(message);
        }
    }

    public void setChatController(ChatController chatController) {
        this.chatController = chatController;
    }

    public void setFileSharingController(FileSharingController fileSharingController) {
        this.fileSharingController = fileSharingController;
    }

    public void setMeetingControlsController(MeetingControlsController controller) {
        this.meetingControlsController = controller;
    }

    public void setOnParticipantChanged(Runnable callback) {
        this.onParticipantChanged = callback;
    }

    public void setOnLeaveMeeting(Runnable callback) {
        this.onLeaveMeeting = callback;
    }

    private void handleFileComplete(P2PMessage message) {
        if (fileSharingController == null)
            return;

        String fileName = message.getPayloadString("fileName");
        if (fileName == null)
            return;

        ChatMessage fileMsg = new ChatMessage(
                null,
                p2pManager != null ? p2pManager.getCurrentUserId() : null,
                p2pManager != null ? p2pManager.getCurrentUserName() : null,
                fileName,
                LocalDateTime.now(),
                ChatMessage.Type.FILE,
                fileName);

        fileSharingController.addFileMessage(fileMsg);
    }

    // ===== SCREEN SHARING =====

    public void startScreenShare() {
        if (isScreenSharing || p2pManager == null || currentUserId == null) {
            return;
        }

        // Kiểm tra nếu đã có người khác đang share
        if (currentScreenSharerId != null && !currentScreenSharerId.equals(currentUserId)) {
            Platform.runLater(() -> {
                javafx.scene.control.Alert alert = new javafx.scene.control.Alert(
                        javafx.scene.control.Alert.AlertType.WARNING);
                alert.setTitle("Không thể chia sẻ màn hình");
                alert.setHeaderText("Đã có người đang chia sẻ màn hình");
                alert.setContentText("Vui lòng đợi người khác dừng chia sẻ trước khi bạn bắt đầu.");
                alert.showAndWait();
            });
            // Reset toggle button
            if (meetingControlsController != null) {
                Platform.runLater(() -> {
                    suppressControlEvents = true;
                    meetingControlsController.screenSharingProperty().set(false);
                    suppressControlEvents = false;
                });
            }
            return;
        }

        try {
            // Ensure screen engine is initialized
            if (screenEngine == null) {
                int myScreenPort = SCREEN_BASE_PORT + Math.abs(currentUserId.hashCode() % 1000);
                screenEngine = new VideoEngine(myScreenPort);
                System.out.println("🖥 ScreenEngine initialized on port " + myScreenPort);
            }

            // Create broadcaster for screen frames
            // Note: broadcaster doesn't need to bind to specific port, receiver does
            if (screenBroadcaster == null) {
                screenBroadcaster = new ScreenFrameBroadcaster(p2pManager, 0); // 0 = let system assign port
                if (!screenBroadcaster.isReady()) {
                    System.err.println("❌ Failed to create screen broadcaster, cannot start screen share");
                    screenBroadcaster = null;
                    return;
                }
            }

            // Start screen capture
            if (screenSource == null) {
                screenSource = new ScreenShareSource(800, 450, 10, 0.6f);
                // Optional: local preview
                screenSource.setLocalPreviewCallback(img -> {
                    if (videoGridController != null) {
                        Platform.runLater(() -> videoGridController.showScreen(currentUserId, img));
                    }
                });
                // Set frame callback to broadcast to all peers
                screenSource.setFrameCallback(jpegBytes -> {
                    if (screenBroadcaster != null) {
                        screenBroadcaster.sendFrame(jpegBytes);
                    }
                });
            }

            // Broadcast screen share start
            broadcastScreenShareStart();

            // Start capture
            screenSource.start();

            isScreenSharing = true;
            currentScreenSharerId = currentUserId;

            System.out.println("🖥 Screen sharing started");
        } catch (Exception e) {
            System.err.println("❌ startScreenShare failed: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void stopScreenShare() {
        if (!isScreenSharing) {
            return;
        }

        try {
            if (screenSource != null) {
                screenSource.stop();
            }
            if (screenBroadcaster != null) {
                screenBroadcaster.close();
                screenBroadcaster = null;
            }
            broadcastScreenShareStop();

            isScreenSharing = false;
            if (currentScreenSharerId != null && currentScreenSharerId.equals(currentUserId)) {
                currentScreenSharerId = null;
                if (videoGridController != null) {
                    Platform.runLater(() -> videoGridController.hideScreen());
                }
            }

            System.out.println("🛑 Screen sharing stopped");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void broadcastScreenShareStart() {
        if (p2pManager == null || currentUserId == null)
            return;
        P2PMessage msg = new P2PMessage(MessageType.SCREEN_SHARE_START, currentUserId, "all");
        msg.addPayload("screenPort", String.valueOf(SCREEN_BASE_PORT + Math.abs(currentUserId.hashCode() % 1000)));
        broadcastMessage(msg);
    }

    private void broadcastScreenShareStop() {
        if (p2pManager == null || currentUserId == null)
            return;
        P2PMessage msg = new P2PMessage(MessageType.SCREEN_SHARE_STOP, currentUserId, "all");
        broadcastMessage(msg);
    }

    private void handleScreenShareStart(P2PMessage message) {
        String sharerId = message.getFrom();
        System.out.println("🖥 Screen share started by: " + sharerId);

        // Nếu đã có người share và không phải mình, cập nhật state
        if (currentScreenSharerId != null && !currentScreenSharerId.equals(sharerId)) {
            System.out.println("⚠️ Someone else is already sharing, ignoring");
            return;
        }

        currentScreenSharerId = sharerId;

        // Nếu mình đang share nhưng có người khác cũng share, dừng mình lại
        if (isScreenSharing && !sharerId.equals(currentUserId)) {
            Platform.runLater(() -> {
                javafx.scene.control.Alert alert = new javafx.scene.control.Alert(
                        javafx.scene.control.Alert.AlertType.INFORMATION);
                alert.setTitle("Chia sẻ màn hình đã dừng");
                alert.setHeaderText("Người khác đã bắt đầu chia sẻ màn hình");
                alert.setContentText("Chia sẻ màn hình của bạn đã được dừng.");
                alert.showAndWait();
            });
            stopScreenShare();
            if (meetingControlsController != null) {
                Platform.runLater(() -> {
                    suppressControlEvents = true;
                    meetingControlsController.screenSharingProperty().set(false);
                    suppressControlEvents = false;
                });
            }
        }

        if (onParticipantChanged != null) {
            onParticipantChanged.run();
        }
    }

    private void handleScreenShareStop(P2PMessage message) {
        String sharerId = message.getFrom();
        System.out.println("🛑 Screen share stopped by: " + sharerId);
        if (sharerId != null && sharerId.equals(currentScreenSharerId)) {
            currentScreenSharerId = null;
            if (videoGridController != null) {
                Platform.runLater(() -> videoGridController.hideScreen());
            }
        }
        if (onParticipantChanged != null) {
            onParticipantChanged.run();
        }
    }

    public boolean isScreenSharing() {
        return isScreenSharing;
    }

    public String getCurrentScreenSharerId() {
        return currentScreenSharerId;
    }

    /**
     * Helper class to broadcast screen frames to all peers (mesh) using UDP
     * directly
     */
    private class ScreenFrameBroadcaster {
        private final P2PManager p2pManager;
        private java.net.DatagramSocket socket;

        public ScreenFrameBroadcaster(P2PManager p2pManager, int unusedPort) {
            this.p2pManager = p2pManager;
            try {
                // Don't bind to specific port for sender - let system assign available port
                // This avoids conflict with receiver which binds to localPort
                this.socket = new java.net.DatagramSocket();
                System.out.println("✅ Screen broadcaster socket created on port " + socket.getLocalPort());
            } catch (java.net.SocketException e) {
                System.err.println("❌ Failed to create screen broadcast socket: " + e.getMessage());
                e.printStackTrace();
                this.socket = null; // Ensure socket is null on failure
            }
        }

        public boolean isReady() {
            return socket != null && !socket.isClosed();
        }

        public void sendFrame(byte[] data) {
            if (p2pManager == null || socket == null || socket.isClosed()) {
                System.err.println("⚠️ Screen broadcaster not ready");
                return;
            }

            var peers = p2pManager.getPeers();
            if (peers.isEmpty()) {
                System.out.println("⚠️ No peers to send screen frame to");
                return;
            }

            // Broadcast to all peers
            int sentCount = 0;
            for (PeerInfo peer : peers.values()) {
                if (peer == null || peer.getUserId() == null)
                    continue;
                String ip = peer.getIpAddress();
                if (ip == null || ip.isBlank())
                    continue;

                int peerScreenPort = SCREEN_BASE_PORT + Math.abs(peer.getUserId().hashCode() % 1000);
                try {
                    java.net.InetAddress peerAddr = java.net.InetAddress.getByName(ip);
                    java.net.DatagramPacket packet = new java.net.DatagramPacket(
                            data, data.length, peerAddr, peerScreenPort);
                    socket.send(packet);
                    sentCount++;
                } catch (Exception e) {
                    System.err.println("❌ Failed to send screen frame to " + peer.getUserId() + " (" + ip + ":"
                            + peerScreenPort + "): " + e.getMessage());
                }
            }
            if (sentCount > 0) {
                System.out.println("📤 Sent screen frame to " + sentCount + " peer(s)");
            }
        }

        public void close() {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        }
    }
}
