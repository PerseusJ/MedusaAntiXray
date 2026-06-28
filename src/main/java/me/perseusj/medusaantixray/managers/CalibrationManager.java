package me.perseusj.medusaantixray.managers;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

public class CalibrationManager {

    private final ConfigManager config;
    private final Logger logger;

    private volatile long startTime;
    private final ConcurrentHashMap<UUID, PlayerStats> playerStats = new ConcurrentHashMap<>();

    private static class PlayerStats {
        long totalBlocks;
        double totalScore;
    }

    public CalibrationManager(ConfigManager config, Logger logger) {
        this.config = config;
        this.logger = logger;
    }

    public void start() {
        this.startTime = System.currentTimeMillis();
        this.playerStats.clear();
        logger.info("[Medusa] Learning mode started — collecting data for "
                + config.getLearningModeDurationMinutes() + " minutes. No alerts will be dispatched.");
    }

    public boolean isActive() {
        return config.isLearningModeEnabled() && startTime > 0;
    }

    public void recordEvent(UUID playerUuid, boolean isValuable, double weight) {
        if (!config.isLearningModeEnabled()) return;
        PlayerStats stats = playerStats.computeIfAbsent(playerUuid, k -> new PlayerStats());
        synchronized (stats) {
            stats.totalBlocks++;
            if (isValuable) {
                stats.totalScore += weight;
            }
        }
    }

    public void tick() {
        if (!config.isLearningModeEnabled() || startTime == 0) return;

        long elapsed = System.currentTimeMillis() - startTime;
        long durationMs = config.getLearningModeDurationMinutes() * 60_000L;
        if (elapsed < durationMs) return;

        List<Double> ratios = new ArrayList<>();
        long globalTotalBlocks = 0;
        double globalTotalScore = 0;

        for (PlayerStats stats : playerStats.values()) {
            synchronized (stats) {
                globalTotalBlocks += stats.totalBlocks;
                globalTotalScore += stats.totalScore;
                if (stats.totalBlocks > 0) {
                    ratios.add(stats.totalScore / stats.totalBlocks);
                }
            }
        }

        double meanRatio = globalTotalBlocks > 0 ? globalTotalScore / globalTotalBlocks : 0;

        Collections.sort(ratios);
        int percentile = config.getLearningModeRecommendPercentile();
        int index = (int) Math.ceil(percentile / 100.0 * ratios.size()) - 1;
        index = Math.min(index, ratios.size() - 1);
        index = Math.max(index, 0);
        double percentileRatio = ratios.isEmpty() ? 0 : ratios.get(index);

        double recommended = Math.max(meanRatio * 2, percentileRatio);

        logger.info("[Medusa] ===== Calibration Complete =====");
        logger.info("[Medusa] Players tracked: " + playerStats.size());
        logger.info("[Medusa] Total blocks mined: " + globalTotalBlocks);
        logger.info("[Medusa] Mean ratio: " + String.format("%.3f", meanRatio));
        logger.info("[Medusa] " + percentile + "th percentile: " + String.format("%.3f", percentileRatio));
        logger.info("[Medusa] Recommended alert-threshold: " + String.format("%.3f", recommended));
        logger.info("[Medusa] ================================");

        if (config.isLearningModePersist()) {
            logger.info("[Medusa] (Learning mode persist to DB not yet implemented — log only)");
        }

        startTime = 0;
        playerStats.clear();
    }
}
