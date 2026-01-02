package com.example.dacs4.controllers;

import com.example.dacs4.models.Participant;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Helper quản lý danh sách Participant cho MeetingRoomController.
 * Chịu trách nhiệm cập nhật trạng thái mic/cam/share và thông báo UI khi list thay đổi.
 */
public class ParticipantsManager {

    private final List<Participant> participants = new ArrayList<>();

    private final Consumer<List<Participant>> participantsUiUpdater;
    private final Consumer<Integer> countUiUpdater;

    private String currentUserId;

    public ParticipantsManager(Consumer<List<Participant>> participantsUiUpdater,
                               Consumer<Integer> countUiUpdater) {
        this.participantsUiUpdater = participantsUiUpdater;
        this.countUiUpdater = countUiUpdater;
    }

    public void setCurrentUserId(String currentUserId) {
        this.currentUserId = currentUserId;
    }

    /**
     * Thay toàn bộ danh sách participants (đã từ P2P hoặc mock data).
     */
    public void setParticipants(List<Participant> newList) {
        participants.clear();
        if (newList != null) {
            for (Participant p : newList) {
                if (p != null && p.getId() != null && p.getName() != null) {
                    participants.add(p);
                }
            }
        }
        refresh();
    }

    public List<Participant> getParticipants() {
        return new ArrayList<>(participants);
    }

    public void updateCurrentUserAudio(boolean isOn) {
        if (currentUserId == null) return;
        participants.forEach(p -> {
            if (currentUserId.equals(p.getId())) {
                p.setAudioOn(isOn);
            }
        });
        refresh();
    }

    public void updateCurrentUserVideo(boolean isOn) {
        if (currentUserId == null) return;
        participants.forEach(p -> {
            if (currentUserId.equals(p.getId())) {
                p.setVideoOn(isOn);
            }
        });
        refresh();
    }

    public void updateCurrentUserScreenSharing(boolean isOn) {
        if (currentUserId == null) return;

        if (isOn) {
            // Chỉ cho phép 1 người share màn hình tại một thời điểm
            participants.forEach(p -> p.setScreenSharing(false));
        }

        participants.forEach(p -> {
            if (currentUserId.equals(p.getId())) {
                p.setScreenSharing(isOn);
            }
        });
        refresh();
    }

    private void refresh() {
        if (participantsUiUpdater != null) {
            participantsUiUpdater.accept(new ArrayList<>(participants));
        }
        if (countUiUpdater != null) {
            countUiUpdater.accept(participants.size());
        }
    }
}
