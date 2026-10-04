package com.example.focusforge;

// Plain data-holder class - carries one session's info from the DB to the UI
public class ForestSession {
    private final String status; // e.g. "Success (Coding)" or "Failed (Math)"
    private final int duration; // minutes this session lasted
    private final int xp; // XP earned (0 if failed)

    public ForestSession(String status, int duration, int xp) { // runs once when object is created
        this.status = status; // "this.status" = field, "status" = parameter
        this.duration = duration;
        this.xp = xp;
    }

    // Getters - fields are private, so other classes must go through these
    public String getStatus() { return status; }
    public int getDuration() { return duration; }
    public int getXp() { return xp; }
}