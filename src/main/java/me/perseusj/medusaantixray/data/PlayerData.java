package me.perseusj.medusaantixray.data;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.UUID;

public class PlayerData {
    private final UUID playerUuid;
    private final String playerName;
    private final Deque<MineEvent> eventWindow;
    private long lastAlertTimestamp;
    private double currentScore;
    private int currentBlocks;

    public PlayerData(UUID playerUuid, String playerName) {
        this.playerUuid = playerUuid;
        this.playerName = playerName;
        this.eventWindow = new ArrayDeque<>();
        this.lastAlertTimestamp = 0;
        this.currentScore = 0;
        this.currentBlocks = 0;
    }

    public UUID getPlayerUuid() {
        return playerUuid;
    }

    public String getPlayerName() {
        return playerName;
    }

    public synchronized long getLastAlertTimestamp() {
        return lastAlertTimestamp;
    }

    public synchronized void setLastAlertTimestamp(long lastAlertTimestamp) {
        this.lastAlertTimestamp = lastAlertTimestamp;
    }

    public synchronized void addEvent(MineEvent event) {
        eventWindow.addLast(event);
        currentBlocks++;
        if (event.isValuable()) {
            currentScore += event.weight();
        }
    }

    public synchronized void purgeExpired(long cutoff) {
        while (!eventWindow.isEmpty() && eventWindow.peekFirst().timestamp() < cutoff) {
            MineEvent removed = eventWindow.pollFirst();
            currentBlocks--;
            if (removed.isValuable()) {
                currentScore -= removed.weight();
            }
        }
    }

    public synchronized void mergeLoadedEvents(List<MineEvent> loaded) {
        if (loaded == null || loaded.isEmpty()) {
            return;
        }
        List<MineEvent> combined = new ArrayList<>(eventWindow.size() + loaded.size());
        combined.addAll(loaded);
        combined.addAll(eventWindow);
        combined.sort(Comparator.comparingLong(MineEvent::timestamp));
        eventWindow.clear();
        currentScore = 0;
        currentBlocks = 0;
        for (MineEvent event : combined) {
            eventWindow.addLast(event);
            currentBlocks++;
            if (event.isValuable()) {
                currentScore += event.weight();
            }
        }
    }

    public synchronized int getTotalBlocks() {
        return currentBlocks;
    }

    public synchronized double calculateScore() {
        return currentScore;
    }

    public synchronized double calculateRatio() {
        if (currentBlocks == 0) {
            return 0;
        }
        return currentScore / currentBlocks;
    }

    public synchronized List<MineEvent> snapshotEvents() {
        return new ArrayList<>(eventWindow);
    }

    public synchronized boolean shouldAlert(long now, long cooldownMillis) {
        if (lastAlertTimestamp != 0 && (now - lastAlertTimestamp) < cooldownMillis) {
            return false;
        }
        lastAlertTimestamp = now;
        return true;
    }
}
