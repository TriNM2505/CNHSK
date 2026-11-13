package com.cnhsk.learning.api;

/** Shared game identifiers and Vietnamese names for game and leaderboard catalogs. */
public enum GameCode {
    CHAR_RAIN("Mưa Chữ"),
    PINYIN_MATCH("Ghép Pinyin"),
    RADICAL_MATCH("Ghép Bộ Thủ"),
    CHAR_CATCH("Bắt Chữ"),
    BRUSH_STROKE("Thần Bút"),
    FLYING_DART("Phi Đao");

    private final String displayName;

    GameCode(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }
}
