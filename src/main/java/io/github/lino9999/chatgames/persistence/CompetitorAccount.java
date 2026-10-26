package io.github.lino9999.chatgames.persistence;

import java.util.UUID;

public class CompetitorAccount {
    private final UUID playerUuid;
    private volatile String playerName;
    private volatile int score;
    private volatile boolean chatVisible;
    private volatile boolean audioEnabled;
    private volatile double bestReactionSeconds;

    public CompetitorAccount(UUID playerUuid, String playerName, int score, boolean chatVisible, boolean audioEnabled, double bestReactionSeconds) {
        this.playerUuid = playerUuid;
        this.playerName = playerName != null ? playerName : "Unknown";
        this.score = Math.max(0, score);
        this.chatVisible = chatVisible;
        this.audioEnabled = audioEnabled;
        this.bestReactionSeconds = Math.max(0.0, bestReactionSeconds);
    }

    public UUID getPlayerUuid() {
        return playerUuid;
    }

    public String getPlayerName() {
        return playerName;
    }

    public void setPlayerName(String playerName) {
        if (playerName != null && !playerName.isBlank()) {
            this.playerName = playerName;
        }
    }

    public int getScore() {
        return score;
    }

    public synchronized void setScore(int score) {
        this.score = Math.max(0, score);
    }

    public synchronized void modifyScore(int delta) {
        this.score = Math.max(0, this.score + delta);
    }

    public boolean isChatVisible() {
        return chatVisible;
    }

    public void setChatVisible(boolean chatVisible) {
        this.chatVisible = chatVisible;
    }

    public boolean isAudioEnabled() {
        return audioEnabled;
    }

    public void setAudioEnabled(boolean audioEnabled) {
        this.audioEnabled = audioEnabled;
    }

    public double getBestReactionSeconds() {
        return bestReactionSeconds;
    }

    public synchronized void recordReactionTime(double elapsedSeconds) {
        if (elapsedSeconds > 0.0 && (this.bestReactionSeconds == 0.0 || elapsedSeconds < this.bestReactionSeconds)) {
            this.bestReactionSeconds = elapsedSeconds;
        }
    }
}
