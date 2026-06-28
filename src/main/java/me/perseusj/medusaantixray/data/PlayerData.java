package me.perseusj.medusaantixray.data;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.UUID;

public class PlayerData {

    public enum MiningStyle {
        CAVE, BRANCH, STRIP, UNKNOWN
    }

    private static final int STYLE_CLASSIFY_INTERVAL = 50;

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

    // C1 — Teleport-cooldown grace period
    private volatile long lastTeleportTimestamp = 0;

    // C2 — Mining-style classification
    private volatile MiningStyle miningStyle = MiningStyle.UNKNOWN;
    private int blocksSinceLastClassification = 0;

    // C4 — Trust tiers
    private double trustMultiplier = 1.0;

    // C5 — Mine-gap multiplier
    private volatile long lastOreTimestamp = 0;

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

    // =========================================================================
    // C1 — Teleport cooldown
    // =========================================================================

    public long getLastTeleportTimestamp() {
        return lastTeleportTimestamp;
    }

    public void setLastTeleportTimestamp(long ts) {
        this.lastTeleportTimestamp = ts;
    }

    public boolean isInTeleportCooldown(long now, long cooldownMs) {
        if (cooldownMs <= 0 || lastTeleportTimestamp == 0) return false;
        return (now - lastTeleportTimestamp) < cooldownMs;
    }

    // =========================================================================
    // C2 — Mining-style classification
    // =========================================================================

    public MiningStyle getMiningStyle() {
        return miningStyle;
    }

    public void setMiningStyle(MiningStyle style) {
        this.miningStyle = style;
    }

    /**
     * Periodically classifies this player's mining style based on the current
     * event window. Called every STYLE_CLASSIFY_INTERVAL blocks.
     *
     * <p>Heuristics:
     * <ul>
     *   <li><b>CAVE</b> — high ore ratio + high Y variance (player traversing naturally exposed ores)</li>
     *   <li><b>STRIP</b> — low Y variance (player staying at one level)</li>
     *   <li><b>BRANCH</b> — moderate ore ratio + moderate Y variance</li>
     *   <li><b>UNKNOWN</b> — insufficient data</li>
     * </ul>
     */
    public synchronized void classifyMiningStyle() {
        blocksSinceLastClassification++;
        if (blocksSinceLastClassification < STYLE_CLASSIFY_INTERVAL) return;
        blocksSinceLastClassification = 0;

        int total = eventWindow.size();
        if (total < 20) {
            miningStyle = MiningStyle.UNKNOWN;
            return;
        }

        int valuableCount = 0;
        double ySum = 0;
        double ySumSq = 0;
        for (MineEvent e : eventWindow) {
            if (e.isValuable()) valuableCount++;
            ySum += e.y();
            ySumSq += (double) e.y() * e.y();
        }

        double oreRatio = (double) valuableCount / total;
        double yMean = ySum / total;
        double yVariance = (ySumSq / total) - (yMean * yMean);
        double yStdDev = Math.sqrt(Math.max(0, yVariance));

        // Cave miners: high ore ratio + high Y variance (exploring caves with exposed ores)
        if (oreRatio > 0.08 && yStdDev > 15.0) {
            miningStyle = MiningStyle.CAVE;
        // Strip miners: very low Y variance (mining at one elevation)
        } else if (yStdDev < 3.0 && oreRatio > 0.02) {
            miningStyle = MiningStyle.STRIP;
        // Branch miners: moderate variance and ratio
        } else if (yStdDev >= 3.0 && yStdDev <= 15.0) {
            miningStyle = MiningStyle.BRANCH;
        } else {
            miningStyle = MiningStyle.UNKNOWN;
        }
    }

    // =========================================================================
    // C4 — Trust multiplier
    // =========================================================================

    public double getTrustMultiplier() {
        return trustMultiplier;
    }

    public void setTrustMultiplier(double trustMultiplier) {
        this.trustMultiplier = trustMultiplier;
    }

    // =========================================================================
    // C5 — Mine-gap tracking
    // =========================================================================

    public long getLastOreTimestamp() {
        return lastOreTimestamp;
    }

    public void setLastOreTimestamp(long ts) {
        this.lastOreTimestamp = ts;
    }
}
