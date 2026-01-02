package com.example.dacs4.controllers;

import com.example.dacs4.models.ChatMessage;
import com.example.dacs4.models.Participant;
import com.example.dacs4.models.P2PMessage;
import com.example.dacs4.models.MessageType;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class MeetingRoomController {

    @FXML
    private Label meetingTitleLabel;
    @FXML
    private Label participantsCountLabel;
    @FXML
    private Button chatButton;
    @FXML
    private Button participantsButton;
    @FXML
    private Button fileSharingButton;
    @FXML
    private VBox sidePanel;
    @FXML
    private StackPane chatPanelRoot;
    @FXML
    private StackPane participantsPanelRoot;
    @FXML
    private StackPane fileSharingPanelRoot;
    @FXML
    private VideoGridController videoGridController;
    @FXML
    private MeetingControlsController meetingControlsController;
    private ChatController chatController;
    @FXML
    private FileSharingController fileSharingController;

    @FXML
    private ParticipantsListController participantsListController;

    private String meetingId;
    private String meetingName;
    private String currentUserId;
    private String currentUserName;
    private String currentUserAvatar;
    private String userRole;
    private boolean mockInitialized = false;
    private MeetingRoomP2PHandler p2pHandler;
    private ParticipantsManager participantsManager;
    private final ObservableList<Participant> participantsObservable = FXCollections.observableArrayList();
    private final ObservableList<ChatMessage> fileMessagesObservable = FXCollections.observableArrayList();
    private final List<ChatMessage> messages = new ArrayList<>();
    private Runnable leaveMeetingCallback;
    private SidePanelController sidePanelController;

    private boolean controlsListenersAttached = false;
    private boolean suppressControlEvents = false;

    @FXML
    private void initialize() {
        sidePanelController = new SidePanelController(sidePanel, chatPanelRoot, participantsPanelRoot,
                fileSharingPanelRoot);

        // Load chat UI programmatically and keep a reference to its controller
        try {
            FXMLLoader chatLoader = new FXMLLoader(getClass().getResource("/fxml/chat.fxml"));
            Parent chatUi = chatLoader.load();
            this.chatController = chatLoader.getController();
            if (chatPanelRoot != null) {
                chatPanelRoot.getChildren().clear();
                chatPanelRoot.getChildren().add(chatUi);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        participantsManager = new ParticipantsManager(
                list -> {
                    participantsObservable.setAll(list);
                    if (videoGridController != null) {
                        videoGridController.setCurrentUserId(currentUserId);
                        videoGridController.setParticipants(new ArrayList<>(list));
                    }
                    if (participantsListController != null) {
                        participantsListController.refresh();
                    }
                },
                count -> participantsCountLabel.setText(count + " người tham gia"));

        p2pHandler = new MeetingRoomP2PHandler();
        p2pHandler.initialize();
        if (videoGridController != null) {
            p2pHandler.setVideoGridController(videoGridController);
        }
        if (meetingControlsController != null) {
            p2pHandler.setMeetingControlsController(meetingControlsController);
        }
        p2pHandler.setOnParticipantChanged(this::updateParticipantsUI);
        p2pHandler.setOnLeaveMeeting(this::handleLeaveMeeting);
        if (videoGridController != null) {
            p2pHandler.setOnRemoteVideoFrame((userId, image) -> {
                videoGridController.updateVideoFrame(userId, image);
            });
        }

        if (fileSharingController != null) {
            p2pHandler.setFileSharingController(fileSharingController);

            fileSharingController.setMessages(fileMessagesObservable);

            fileSharingController.setOnFileUpload(file -> {
                if (p2pHandler != null && currentUserId != null && currentUserName != null) {
                    p2pHandler.sendFile(file, currentUserId, currentUserName);

                    try {
                        Path dst = Paths.get("downloads", file.getName());
                        Files.createDirectories(dst.getParent());
                        Files.copy(file.toPath(), dst, StandardCopyOption.REPLACE_EXISTING);
                    } catch (Exception ignored) {
                    }

                    // Show locally immediately
                    ChatMessage localFileMsg = new ChatMessage(
                            null,
                            currentUserId,
                            currentUserName,
                            file.getName(),
                            LocalDateTime.now(),
                            ChatMessage.Type.FILE,
                            file.getName());
                    fileMessagesObservable.add(localFileMsg);
                }
            });
        }

        if (participantsListController != null) {
            participantsListController.setParticipants(participantsObservable, currentUserId);
        }

        if (meetingControlsController != null) {
            if (!controlsListenersAttached) {
                controlsListenersAttached = true;

                meetingControlsController.audioOnProperty().addListener((o, oldV, newV) -> {
                    if (suppressControlEvents)
                        return;
                    updateCurrentUserAudio(newV);
                    if (p2pHandler != null) {
                        p2pHandler.broadcastAudioToggle(newV);
                        if (newV)
                            p2pHandler.startAudioStreaming();
                        else
                            p2pHandler.stopAudioStreaming();
                    }
                });

                meetingControlsController.videoOnProperty().addListener((o, oldV, newV) -> {
                    if (suppressControlEvents)
                        return;
                    updateCurrentUserVideo(newV);
                    if (p2pHandler != null) {
                        p2pHandler.broadcastVideoToggle(newV);
                        if (newV)
                            p2pHandler.startVideoStreaming();
                        else
                            p2pHandler.stopVideoStreaming();
                    }
                });

                meetingControlsController.screenSharingProperty().addListener((o, oldV, newV) -> {
                    if (suppressControlEvents)
                        return;
                    updateCurrentUserScreenSharing(newV);
                });

                meetingControlsController.setLeaveMeetingCallback(this::handleLeaveMeeting);
            }

            // default state when entering room: mic ON, cam OFF
            suppressControlEvents = true;
            meetingControlsController.audioOnProperty().set(true);
            meetingControlsController.videoOnProperty().set(false);
            suppressControlEvents = false;
        }
    }

    public void setMeetingId(String meetingId) {
        this.meetingId = meetingId;
        this.meetingName = "Cuộc họp " + meetingId;
        updateMeetingTitle();
        initMockDataIfReady();
        startP2PIfReady();
    }

    public void setMeetingName(String name) {
        this.meetingName = name;
        updateMeetingTitle();
    }

    private void updateMeetingTitle() {
        if (meetingName != null && meetingId != null) {
            meetingTitleLabel.setText(meetingName + " (" + meetingId + ")");
        } else if (meetingId != null) {
            meetingTitleLabel.setText("Mã cuộc họp: " + meetingId);
        }
    }

    public void setUser(String id, String name, String avatar) {
        this.currentUserId = id;
        this.currentUserName = name;
        this.currentUserAvatar = avatar;

        if (participantsManager != null) {
            participantsManager.setCurrentUserId(id);
        }

        if (participantsListController != null) {
            participantsListController.setParticipants(participantsObservable, currentUserId);
            participantsListController.refresh();
        }

        // Setup chat controller after user is set
        if (chatController != null) {
            chatController.setCurrentUserId(currentUserId);
            chatController.setOnSendMessage(this::sendChatMessage);
            if (p2pHandler != null) {
                p2pHandler.setChatController(chatController);
            }
        }

        initMockDataIfReady();
        startP2PIfReady();
    }

    public void setUserRole(String role) {
        this.userRole = role;
        startP2PIfReady();
    }

    public void setOnLeaveMeeting(Runnable callback) {
        this.leaveMeetingCallback = callback;
    }

    private void initMockDataIfReady() {
        if (mockInitialized || currentUserId == null || meetingId == null)
            return;

        List<Participant> mockParticipants = new ArrayList<>();
        // mockParticipants.add(new Participant(currentUserId, currentUserName,
        // currentUserAvatar, true, true, false, false));
        // mockParticipants.add(new Participant("2", "Trần Thị B",
        // "https://api.dicebear.com/7.x/avataaars/svg?seed=user2",
        // true, true, false, false));
        // mockParticipants.add(new Participant("3", "Lê Văn C",
        // "https://api.dicebear.com/7.x/avataaars/svg?seed=user3",
        // false, true, false, false));
        // mockParticipants.add(new Participant("4", "Phạm Thị D",
        // "https://api.dicebear.com/7.x/avataaars/svg?seed=user4",
        // true, false, false, false));

        if (participantsManager != null) {
            participantsManager.setParticipants(mockParticipants);
        }

        messages.clear();
        messages.add(new ChatMessage("1", "2", "Trần Thị B", "Chào mọi người!", LocalDateTime.now().minusMinutes(5),
                ChatMessage.Type.TEXT, null));
        messages.add(new ChatMessage("2", currentUserId, currentUserName, "Xin chào!",
                LocalDateTime.now().minusMinutes(4), ChatMessage.Type.TEXT, null));

        mockInitialized = true;
    }

    @FXML
    private void onToggleChat() {
        if (sidePanelController != null) {
            sidePanelController.toggleChat();
        }
    }

    @FXML
    private void onToggleParticipants() {
        if (sidePanelController != null) {
            sidePanelController.toggleParticipants();
        }
    }

    @FXML
    private void onToggleFileSharing() {
        if (sidePanelController != null) {
            sidePanelController.toggleFileSharing();
        }
    }

    @FXML
    private void onLeaveButtonClicked() {
        handleLeaveMeeting();
    }

    private void handleLeaveMeeting() {
        if (p2pHandler != null)
            p2pHandler.stop();
        if (leaveMeetingCallback != null)
            leaveMeetingCallback.run();
    }

    private void updateCurrentUserAudio(boolean isOn) {
        if (participantsManager != null) {
            participantsManager.updateCurrentUserAudio(isOn);
        }
    }

    private void updateCurrentUserVideo(boolean isOn) {
        if (participantsManager != null) {
            participantsManager.updateCurrentUserVideo(isOn);
        }
    }

    private void updateCurrentUserScreenSharing(boolean isOn) {
        if (participantsManager != null) {
            participantsManager.updateCurrentUserScreenSharing(isOn);
        }
        if (p2pHandler != null) {
            if (isOn) {
                p2pHandler.startScreenShare();
            } else {
                p2pHandler.stopScreenShare();
            }
        }
    }

    private void sendChatMessage(String content) {
        if (p2pHandler != null && currentUserId != null && currentUserName != null) {
            P2PMessage chatMsg = new P2PMessage(MessageType.CHAT_MESSAGE, currentUserId, "all");
            chatMsg.addPayload("senderName", currentUserName);
            chatMsg.addPayload("content", content);
            p2pHandler.broadcastMessage(chatMsg);

            // Add to local chat
            if (chatController != null) {
                ChatMessage localMsg = new ChatMessage(currentUserId, currentUserName, content, LocalDateTime.now());
                chatController.addMessage(localMsg);
            }
        }
    }

    private void startP2PIfReady() {
        if (p2pHandler != null && meetingId != null && currentUserId != null && userRole != null) {
            p2pHandler.startConnection(meetingId, currentUserId, currentUserName, userRole);
            updateParticipantsUI();
        }
    }

    private void updateParticipantsUI() {
        if (p2pHandler == null)
            return;

        List<Participant> p2pParticipants = p2pHandler.getParticipants();
        if (participantsManager != null) {
            participantsManager.setParticipants(p2pParticipants);
        }
    }
}
