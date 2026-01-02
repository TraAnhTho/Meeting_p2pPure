package com.example.dacs4.models;

public class Participant {
    private String id;
    private String name;
    private String avatar;
    private boolean audioOn;
    private boolean videoOn;
    private boolean screenSharing;
    private boolean speaking;

    public Participant(String id, String name, String avatar,
                       boolean audioOn, boolean videoOn,
                       boolean screenSharing, boolean speaking) {
        this.id = id;
        this.name = name;
        this.avatar = avatar;
        this.audioOn = audioOn;
        this.videoOn = videoOn;
        this.screenSharing = screenSharing;
        this.speaking = speaking;
    }

    public Participant(String id, String name, boolean audioOn, boolean videoOn, boolean screenSharing) {
        this(id, name, null, audioOn, videoOn, screenSharing, false);
    }


    // getter + setter
    public String getId() { return id; }
    public String getName() { return name; }
    public String getAvatar() { return avatar; }
    public boolean isAudioOn() { return audioOn; }
    public boolean isVideoOn() { return videoOn; }
    public boolean isScreenSharing() { return screenSharing; }
    public boolean isSpeaking() { return speaking; }

    public void setAudioOn(boolean audioOn) { this.audioOn = audioOn; }
    public void setVideoOn(boolean videoOn) { this.videoOn = videoOn; }
    public void setScreenSharing(boolean screenSharing) { this.screenSharing = screenSharing; }
    public void setSpeaking(boolean speaking) { this.speaking = speaking; }
}
