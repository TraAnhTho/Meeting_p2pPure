package com.example.dacs4.services;

import com.example.dacs4.models.P2PMessage;
import com.example.dacs4.models.PeerInfo;
import com.example.dacs4.network.P2PManager;
import com.example.dacs4.network.ScreenShareSource;
import com.example.dacs4.network.VideoEngine;
import com.example.dacs4.network.VoiceEngine;
import com.example.dacs4.network.WebcamVideoSource;
import javafx.application.Platform;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.image.Image;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.function.BiConsumer;

/**
 * Manager for all media streams (audio, video, screen sharing)
 */
public class MediaStreamManager {

    private static final int VOICE_BASE_PORT = 6000;
    private static final int VIDEO_BASE_PORT = 7000;
    private static final int SCREEN_BASE_PORT = 8000;

    private final P2PManager p2pManager;
    private final String currentUserId;

    private VoiceEngine voiceEngine;
    private VideoEngine videoEngine; // camera
    private VideoEngine screenEngine; // screen share
    private WebcamVideoSource webcamSource;
    private ScreenShareSource screenSource;
    private ScreenFrameBroadcaster screenBroadcaster;

    private boolean isAudioStreaming = false;
    private boolean isVideoStreaming = false;
    private boolean isScreenSharing = false;
    private volatile String currentScreenSharerId = null;

    // Callbacks
    private BiConsumer<String, Image> onRemoteVideoFrame;
    private Runnable onParticipantChanged;
    private Object videoGridController; // VideoGridController
    private Object meetingControlsController; // MeetingControlsController
    private boolean suppressControlEvents = false;

    public MediaStreamManager(P2PManager p2pManager, String userId) {
        this.p2pManager = p2pManager;
        this.currentUserId = userId;
    }

    // ===== Setters =====

    public void setOnRemoteVideoFrame(BiConsumer<String, Image> callback) {
        this.onRemoteVideoFrame = callback;
    }

    public void setOnParticipantChanged(Runnable callback) {
        this.onParticipantChanged = callback;
    }

    public void setVideoGridController(Object controller) {
        this.videoGridController = controller;
    }

    public void setMeetingControlsController(Object controller) {
        this.meetingControlsController = controller;
    }

    // ===== Audio Streaming =====

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

    public void stopAudioStreaming() {
        if (voiceEngine != null && isAudioStreaming) {
            voiceEngine.stop();
            isAudioStreaming = false;
            System.out.println("🛑 Audio streaming stopped");
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

    // ===== Video Streaming =====

    public void startVideoStreaming() {
        if (isVideoStreaming || p2pManager == null || currentUserId == null)
            return;

        try {
            if (videoEngine == null) {
                int myVideoPort = VIDEO_BASE_PORT + Math.abs(currentUserId.hashCode() % 1000);
                videoEngine = new VideoEngine(myVideoPort);
                System.out.println("📹 VideoEngine initialized on port " + myVideoPort);
            }

            // Update video targets (connect to peers if they exist)
            updateVideoTargets();

            // Initialize webcam source
            if (webcamSource == null) {
                webcamSource = new WebcamVideoSource();
            }

            if (webcamSource.isAvailable()) {
                // Set callback for local preview (always show own video)
                webcamSource.setLocalFrameCallback(fxImage -> {
                    if (fxImage == null)
                        return;
                    if (onRemoteVideoFrame != null && currentUserId != null) {
                        Platform.runLater(() -> onRemoteVideoFrame.accept(currentUserId, fxImage));
                    }
                    if (videoGridController != null && currentUserId != null) {
                        // Call videoGridController.updateVideoFrame(currentUserId, fxImage)
                        try {
                            videoGridController.getClass()
                                    .getMethod("updateVideoFrame", String.class, Image.class)
                                    .invoke(videoGridController, currentUserId, fxImage);
                        } catch (Exception ignored) {
                        }
                    }
                });

                // Set callback for encoded frames (send to peers if they exist)
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
        } catch (Exception e) {
            System.err.println("❌ Error starting video: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Update video engine targets when peers change
     * Should be called when peers join/leave or when video streaming starts
     */
    private void updateVideoTargets() {
        if (videoEngine == null || p2pManager == null)
            return;

        var peers = p2pManager.getPeers();
        if (!peers.isEmpty()) {
            PeerInfo firstPeer = peers.values().iterator().next();
            int peerVideoPort = VIDEO_BASE_PORT + Math.abs(firstPeer.getUserId().hashCode() % 1000);
            String peerIp = firstPeer.getIpAddress() != null ? firstPeer.getIpAddress() : "127.0.0.1";

            try {
                videoEngine.start(peerIp, peerVideoPort);
                System.out.println("📹 VideoEngine connected to peer: " + peerIp + ":" + peerVideoPort);
            } catch (Exception e) {
                System.err.println("❌ Error connecting video engine to peer: " + e.getMessage());
            }
        } else {
            // No peers yet, but we can still stream for local preview
            System.out.println("📹 VideoEngine ready (no peers yet, will show local preview only)");
        }
    }

    /**
     * Called when peers join/leave to update video streaming targets
     */
    public void onPeersChanged() {
        if (isVideoStreaming) {
            updateVideoTargets();
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

    // ===== Screen Sharing =====

    public void startScreenShare() {
        if (isScreenSharing || p2pManager == null || currentUserId == null) {
            return;
        }

        // Check if someone else is sharing
        if (currentScreenSharerId != null && !currentScreenSharerId.equals(currentUserId)) {
            Platform.runLater(() -> {
                javafx.scene.control.Alert alert = new javafx.scene.control.Alert(
                        javafx.scene.control.Alert.AlertType.WARNING);
                alert.setTitle("Không thể chia sẻ màn hình");
                alert.setHeaderText("Đã có người đang chia sẻ màn hình");
                alert.setContentText("Vui lòng đợi người khác dừng chia sẻ trước khi bạn bắt đầu.");
                alert.showAndWait();
            });
            return;
        }

        try {
            if (screenEngine == null) {
                int myScreenPort = SCREEN_BASE_PORT + Math.abs(currentUserId.hashCode() % 1000);
                screenEngine = new VideoEngine(myScreenPort);
                System.out.println("🖥 ScreenEngine initialized on port " + myScreenPort);
            }

            if (screenBroadcaster == null) {
                screenBroadcaster = new ScreenFrameBroadcaster(p2pManager, 0);
                if (!screenBroadcaster.isReady()) {
                    System.err.println("❌ Failed to create screen broadcaster");
                    screenBroadcaster = null;
                    return;
                }
            }

            if (screenSource == null) {
                screenSource = new ScreenShareSource(800, 450, 10, 0.6f);
                screenSource.setLocalPreviewCallback(img -> {
                    if (videoGridController != null) {
                        try {
                            videoGridController.getClass()
                                    .getMethod("showScreen", String.class, Image.class)
                                    .invoke(videoGridController, currentUserId, img);
                        } catch (Exception ignored) {
                        }
                    }
                });
                screenSource.setFrameCallback(jpegBytes -> {
                    if (screenBroadcaster != null) {
                        screenBroadcaster.sendFrame(jpegBytes);
                    }
                });
            }

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

            isScreenSharing = false;
            if (currentScreenSharerId != null && currentScreenSharerId.equals(currentUserId)) {
                currentScreenSharerId = null;
                if (videoGridController != null) {
                    try {
                        videoGridController.getClass().getMethod("hideScreen").invoke(videoGridController);
                    } catch (Exception ignored) {
                    }
                }
            }

            System.out.println("🛑 Screen sharing stopped");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // ===== Cleanup =====

    public void stop() {
        stopAudioStreaming();
        stopVideoStreaming();
        stopScreenShare();
        if (videoEngine != null) {
            videoEngine.stop();
        }
        if (screenEngine != null) {
            screenEngine.stop();
        }
        voiceEngine = null;
        videoEngine = null;
        screenEngine = null;
    }

    // ===== Getters =====

    public boolean isAudioStreaming() {
        return isAudioStreaming;
    }

    public boolean isVideoStreaming() {
        return isVideoStreaming;
    }

    public boolean isScreenSharing() {
        return isScreenSharing;
    }

    public String getCurrentScreenSharerId() {
        return currentScreenSharerId;
    }

    public void setCurrentScreenSharerId(String sharerId) {
        this.currentScreenSharerId = sharerId;
    }

    // ===== Helper Classes =====

    /**
     * Helper class to broadcast screen frames to all peers
     */
    private class ScreenFrameBroadcaster {
        private final P2PManager p2pManager;
        private java.net.DatagramSocket socket;

        public ScreenFrameBroadcaster(P2PManager p2pManager, int unusedPort) {
            this.p2pManager = p2pManager;
            try {
                this.socket = new java.net.DatagramSocket();
                System.out.println("✅ Screen broadcaster socket created on port " + socket.getLocalPort());
            } catch (java.net.SocketException e) {
                System.err.println("❌ Failed to create screen broadcast socket: " + e.getMessage());
                this.socket = null;
            }
        }

        public boolean isReady() {
            return socket != null && !socket.isClosed();
        }

        public void sendFrame(byte[] data) {
            if (p2pManager == null || socket == null || socket.isClosed()) {
                return;
            }

            var peers = p2pManager.getPeers();
            if (peers.isEmpty()) {
                return;
            }

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
                    System.err.println("❌ Failed to send screen frame to " + peer.getUserId());
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

    // ===== Utility Methods =====

    public static Image decodeJpegToFxImage(byte[] jpegBytes) {
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
}
