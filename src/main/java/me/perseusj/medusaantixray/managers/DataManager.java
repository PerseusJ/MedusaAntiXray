package me.perseusj.medusaantixray.managers;

import me.perseusj.medusaantixray.data.PlayerData;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class DataManager {
    private final ConcurrentHashMap<UUID, PlayerData> cache;
    private final DatabaseManager database;
    private final ConfigManager config;

    /**
     * A2 FIX: Tracks UUIDs for which a database load is currently in-flight.
     * Prevents a second call to {@link #loadOrCreateAsync} (e.g., a rapid quit/rejoin)
     * from submitting a duplicate load that could race with the first callback.
     */
    private final Set<UUID> loadingInFlight;

    public DataManager(DatabaseManager database, ConfigManager config) {
        this.cache = new ConcurrentHashMap<>();
        this.database = database;
        this.config = config;
        this.loadingInFlight = Collections.newSetFromMap(new ConcurrentHashMap<>());
    }

    public DatabaseManager getDatabase() {
        return database;
    }

    public void createEntry(UUID uuid, String name) {
        cache.putIfAbsent(uuid, new PlayerData(uuid, name));
    }

    public PlayerData getEntry(UUID uuid) {
        return cache.get(uuid);
    }

    public Collection<PlayerData> getAllEntries() {
        return cache.values();
    }

    public void loadOrCreateAsync(UUID uuid, String name) {
        cache.putIfAbsent(uuid, new PlayerData(uuid, name));

        if (!database.isAvailable()) {
            return;
        }

        // A2 FIX: Guard against duplicate in-flight loads for the same UUID.
        if (!loadingInFlight.add(uuid)) {
            return; // Load already in progress; skip.
        }

        database.loadAsync(uuid, events -> {
            try {
                PlayerData data = cache.get(uuid);
                if (data != null && events != null && !events.isEmpty()) {
                    long cutoff = System.currentTimeMillis() - (config.getWindowMinutes() * 60_000L);
                    data.mergeLoadedEvents(events);
                    data.purgeExpired(cutoff);
                } else if (data != null) {
                    // No loaded events; still mark as merged so isMerged() is consistent.
                    data.mergeLoadedEvents(null);
                }
            } finally {
                loadingInFlight.remove(uuid);
            }
        });
    }

    public void saveAndRemoveAsync(UUID uuid) {
        PlayerData data = cache.get(uuid);
        if (data == null) {
            return;
        }
        // Remove from in-flight set in case the player disconnects before the load completes.
        loadingInFlight.remove(uuid);

        if (database.isAvailable()) {
            database.saveAsync(data, () -> cache.remove(uuid, data));
        } else {
            cache.remove(uuid);
        }
    }

    public void saveAll() {
        if (database.isAvailable()) {
            for (PlayerData data : cache.values()) {
                database.saveAsync(data, null);
            }
        }
    }

    /**
     * E1: Returns the top-N players by ratio, filtered to those above the alert threshold
     * and with enough blocks to meet the minimum sample size.
     */
    public List<PlayerData> getTopN(int n, double minRatio, int minBlocks) {
        long cutoff = System.currentTimeMillis() - (config.getWindowMinutes() * 60_000L);
        return cache.values().stream()
                .filter(p -> {
                    p.purgeExpired(cutoff);
                    return p.getTotalBlocks() >= minBlocks && p.calculateRatio() >= minRatio;
                })
                .sorted(java.util.Comparator.comparingDouble(PlayerData::calculateRatio).reversed())
                .limit(n)
                .toList();
    }

    /**
     * E1: Clears all in-memory event data for a player and deletes their DB rows.
     * The empty PlayerData stub remains in cache so a new window can accumulate.
     */
    public void resetPlayer(PlayerData data) {
        UUID uuid = data.getPlayerUuid();
        synchronized (data) {
            data.reset();
        }
        loadingInFlight.remove(uuid);
        if (database.isAvailable()) {
            database.deleteEventsAsync(uuid);
        }
    }

    /**
     * E1: Returns all cached players whose ratio is at or above the given threshold
     * (after purging expired events from their windows).
     */
    public List<PlayerData> getFlagged(double threshold, int minBlocks) {
        long cutoff = System.currentTimeMillis() - (config.getWindowMinutes() * 60_000L);
        return cache.values().stream()
                .filter(p -> {
                    p.purgeExpired(cutoff);
                    return p.getTotalBlocks() >= minBlocks && p.calculateRatio() >= threshold;
                })
                .sorted(java.util.Comparator.comparingDouble(PlayerData::calculateRatio).reversed())
                .toList();
    }

    public void shutdown() {
        saveAll();
        database.shutdown();
    }
}