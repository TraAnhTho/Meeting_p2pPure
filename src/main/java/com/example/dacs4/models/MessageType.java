package com.example.dacs4.models;

public enum MessageType {
    // Signaling & Connection
    JOIN_MEETING,
    LEAVE_MEETING,
    REQUEST_PEER_LIST,
    PEER_LIST_RESPONSE,

    // Events
    USER_JOINED,
    USER_LEFT,
    MEETING_ENDED,

    // Communication
    CHAT_MESSAGE,
    FILE_SHARE,
    FILE_REQUEST,

    // Media Control
    AUDIO_TOGGLE,
    VIDEO_TOGGLE,
    SCREEN_SHARE_START,
    SCREEN_SHARE_STOP,

    // Heartbeat
    PING,
    PONG,

    // File Sharing
    FILE_SHARE_REQUEST,  // Gửi metadata file
    FILE_SHARE_ACCEPT,   // Chấp nhận nhận file
    FILE_SHARE_REJECT,   // Từ chối nhận file
    FILE_CHUNK,          // Chuyển dữ liệu chunk
    CHUNK_ACK,           // Xác nhận nhận chunk
    FILE_COMPLETE        // Hoàn thành transfer
}
