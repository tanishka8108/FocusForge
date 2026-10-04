package com.example.focusforge;

import java.util.HashMap;
import java.util.Map;

public class StudyRoom {
    private String roomId;
    private String hostName;
    private int roomBossHp;
    private int roomBossMaxHp;
    private boolean isSessionActive;
    private Map<String, Integer> membersScore; // Username -> XP contributed

    public StudyRoom() {
        // Required empty constructor for Firestore
    }

    public StudyRoom(String roomId, String hostName, int bossHp) {
        this.roomId = roomId;
        this.hostName = hostName;
        this.roomBossHp = bossHp;
        this.roomBossMaxHp = bossHp;
        this.isSessionActive = false;
        this.membersScore = new HashMap<>();
        this.membersScore.put(hostName, 0);
    }

    public String getRoomId() { return roomId; }
    public String getHostName() { return hostName; }
    public int getRoomBossHp() { return roomBossHp; }
    public void setRoomBossHp(int roomBossHp) { this.roomBossHp = roomBossHp; }
    public int getRoomBossMaxHp() { return roomBossMaxHp; }
    public boolean isSessionActive() { return isSessionActive; }
    public void setSessionActive(boolean sessionActive) { isSessionActive = sessionActive; }
    public Map<String, Integer> getMembersScore() { return membersScore; }
    public void setMembersScore(Map<String, Integer> membersScore) { this.membersScore = membersScore; }
}