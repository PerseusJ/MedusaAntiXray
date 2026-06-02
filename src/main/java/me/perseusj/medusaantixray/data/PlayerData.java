package me.perseusj.medusaantixray.data;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.UUID;

public class PlayerData {
    private final UUID playerUuid;
    private final String playerName;
    private final Deque<MineEvent> eventWindow;
    private long lastAlertTimestamp;

    public PlayerData(UUID playerUuid, String playerName) {
        this.playerUuid = playerUuid;
        this.playerName = playerName;
        this.eventWindow = new ArrayDeque<>();
        this.lastAlertTimestamp = 0;
    }

    public UUID getPlayerUuid() {
        return playerUuid;
    }

    public String getPlayerName() {
        return playerName;
    }

    public long getLastAlertTimestamp() {
        return lastAlertTimestamp;
    }

    public void setLastAlertTimestamp(long lastAlertTimestamp) {
        this.lastAlertTimestamp = lastAlertTimestamp;
    }

    public void addEvent(MineEvent event) {
        this.eventWindow.addLast(event);
    }

    public void purgeExpired(long cutoff) {
        while (!eventWindow.isEmpty() && eventWindow.peekFirst().timestamp() < cutoff) {
            eventWindow.pollFirst();
        }
    }

    public int getTotalBlocks() {
        return eventWindow.size();
    }

    public double calculateScore() {
        double score = 0;
        for (MineEvent event : eventWindow) {
            if (event.isValuable()) {
                score += event.weight();
            }
        }
        return score;
    }

    public double calculateRatio() {
        int total = getTotalBlocks();
        if (total == 0) {
            return 0;
        }
        return calculateScore() / total;
    }
}
