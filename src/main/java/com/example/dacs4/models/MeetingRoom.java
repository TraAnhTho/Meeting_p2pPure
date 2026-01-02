package com.example.dacs4.models;

import java.util.List;

public class MeetingRoom {
    private String roomId;  // ID phòng, nhận từ tham số
    private List<Participant> participants;  // Danh sách người tham gia

    // Constructor nhận tham số động, tránh hardcode
    public MeetingRoom(String roomId, List<Participant> participants) {
        this.roomId = roomId;
        this.participants = participants;
    }

    // Getter & Setter
    public String getRoomId() {
        return roomId;
    }

    public void setRoomId(String roomId) {
        this.roomId = roomId;
    }

    public List<Participant> getParticipants() {
        return participants;
    }

    public void setParticipants(List<Participant> participants) {
        this.participants = participants;
    }
}
