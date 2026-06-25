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

    /**
     * Set to {@code true} once {@link #mergeLoadedEvents} has completed its first run.
     * Exposed as volatile for visibility across threads; only written under {@code synchronized(this)}.
     */
    private volatile boolean merged = false;

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

    /** Returns {@code true} after {@link #mergeLoadedEvents} has run at least once. */
    public boolean isMerged() {
        return merged;
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

    /**
     * Merges database-loaded events with any events already accumulated in the live window.
     *
     * <p>A2 FIX: Instead of clearing the deque first and then rebuilding (which races with
     * concurrent {@link #addEvent} calls), we capture the current live events, combine them
     * with the loaded set, sort, and rebuild — all under {@code synchronized(this)} so no
     * in-flight {@code addEvent} call can be lost.</p>
     */
    public synchronized void mergeLoadedEvents(List<MineEvent> loaded) {
        if (loaded == null || loaded.isEmpty()) {
            merged = true;
            return;
        }

        // Capture whatever live events are already in the window (may include events that
        // arrived between the putIfAbsent in loadOrCreateAsync and now).
        List<MineEvent> live = new ArrayList<>(eventWindow);

        // Build the combined sorted list.
        List<MineEvent> combined = new ArrayList<>(loaded.size() + live.size());
        combined.addAll(loaded);
        combined.addAll(live);
        combined.sort(Comparator.comparingLong(MineEvent::timestamp));

        // Rebuild accumulators atomically.
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

        merged = true;
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
