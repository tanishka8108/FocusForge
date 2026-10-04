package com.example.focusforge;

public class HistoryModel {
    private final int id;
    private final String date;
    private final int duration;
    private final int xp;
    private final String treeType;

    public HistoryModel(int id, String date, int duration, int xp, String treeType) {
        this.id = id;
        this.date = date;
        this.duration = duration;
        this.xp = xp;
        this.treeType = treeType;
    }

    public int getId() { return id; }
    public String getDate() { return date; }
    public int getDuration() { return duration; }
    public int getXp() { return xp; }

    public String getTreeType() {
        return treeType;
    }
}
