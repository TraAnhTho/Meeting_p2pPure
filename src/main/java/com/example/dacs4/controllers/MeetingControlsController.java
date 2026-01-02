package com.example.dacs4.controllers;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;

public class MeetingControlsController {

    @FXML private Button audioButton;
    @FXML private Button videoButton;
    @FXML private Button screenShareButton;
    @FXML private Button settingsButton;
    @FXML private Button moreButton;
    @FXML private Button leaveButton;

    @FXML private Label audioIcon;
    @FXML private Label videoIcon;
    @FXML private Label screenShareIcon;

    @FXML private Label audioLabel;
    @FXML private Label videoLabel;
    @FXML private Label screenShareLabel;

    // Các thuộc tính BooleanProperty để theo dõi trạng thái của mic, camera và chia sẻ màn hình
    private final BooleanProperty audioOn = new SimpleBooleanProperty(true);
    private final BooleanProperty videoOn = new SimpleBooleanProperty(true);
    private final BooleanProperty screenSharing = new SimpleBooleanProperty(false);

    private Runnable leaveMeetingCallback;

    @FXML
    private void initialize() {
        // Lắng nghe sự thay đổi trạng thái của mic, camera và chia sẻ màn hình
        audioOn.addListener((observable, oldValue, newValue) -> updateUI(audioOn, audioIcon, audioLabel, "🎤", "🔇", "Tắt mic", "Bật mic"));
        videoOn.addListener((observable, oldValue, newValue) -> updateUI(videoOn, videoIcon, videoLabel, "📹", "🚫📹", "Tắt camera", "Bật camera"));
        screenSharing.addListener((observable, oldValue, newValue) -> updateUI(screenSharing, screenShareIcon, screenShareLabel, "🛑🖥", "🖥", "Dừng chia sẻ", "Chia sẻ"));

        // Cập nhật UI lần đầu tiên
        updateUI(audioOn, audioIcon, audioLabel, "🎤", "🔇", "Tắt mic", "Bật mic");
        updateUI(videoOn, videoIcon, videoLabel, "📹", "🚫📹", "Tắt camera", "Bật camera");
        updateUI(screenSharing, screenShareIcon, screenShareLabel, "🛑🖥", "🖥", "Dừng chia sẻ", "Chia sẻ");
    }

    // Cập nhật UI chung cho các hành động (mic, camera, screen share)
    private void updateUI(BooleanProperty property, Label iconLabel, Label textLabel, String activeIcon, String inactiveIcon, String activeText, String inactiveText) {
        if (property.get()) {
            iconLabel.setText(activeIcon);
            textLabel.setText(activeText);
        } else {
            iconLabel.setText(inactiveIcon);
            textLabel.setText(inactiveText);
        }
    }

    // Các sự kiện thay đổi trạng thái của mic, camera và chia sẻ màn hình
    @FXML
    private void onToggleAudio() {
        audioOn.set(!audioOn.get());  // Toggle trạng thái mic
    }

    @FXML
    private void onToggleVideo() {
        videoOn.set(!videoOn.get());  // Toggle trạng thái camera
    }

    @FXML
    private void onToggleScreenShare() {
        screenSharing.set(!screenSharing.get());  // Toggle trạng thái chia sẻ màn hình
    }

    // Sự kiện mở cài đặt
    @FXML
    private void onOpenSettings() {
        System.out.println("Mở cài đặt");
    }

    // Sự kiện các tùy chọn khác
    @FXML
    private void onMoreOptions() {
        System.out.println("Mở thêm tùy chọn");
    }

    // Sự kiện rời khỏi cuộc họp
    @FXML
    private void onLeaveMeeting() {
        if (leaveMeetingCallback != null) {
            leaveMeetingCallback.run();  // Thực thi callback khi rời cuộc họp
        }
    }

    // ===== Public API =====
    public BooleanProperty audioOnProperty() {
        return audioOn;
    }

    public BooleanProperty videoOnProperty() {
        return videoOn;
    }

    public BooleanProperty screenSharingProperty() {
        return screenSharing;
    }

    // Cài đặt callback cho rời cuộc họp
    public void setLeaveMeetingCallback(Runnable callback) {
        this.leaveMeetingCallback = callback;
    }
}
