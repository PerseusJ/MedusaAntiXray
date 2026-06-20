package me.perseusj.medusaantixray.managers;

import me.perseusj.medusaantixray.data.PlayerData;

import java.util.Collection;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class DataManager {
    private final ConcurrentHashMap<UUID, PlayerData> cache;
    private final DatabaseManager database;
    private final ConfigManager config;

    public DataManager(DatabaseManager database, ConfigManager config) {
        this.cache = new ConcurrentHashMap<>();
        this.database = database;
        this.config = config;
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
        if (database.isAvailable()) {
            database.loadAsync(uuid, events -> {
                PlayerData data = cache.get(uuid);
                if (data != null && events != null && !events.isEmpty()) {
                    long cutoff = System.currentTimeMillis() - (config.getWindowMinutes() * 60_000L);
                    data.mergeLoadedEvents(events);
                    data.purgeExpired(cutoff);
                }
            });
        }
    }

    public void saveAndRemoveAsync(UUID uuid) {
        PlayerData data = cache.get(uuid);
        if (data == null) {
            return;
        }
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

    public void shutdown() {
        saveAll();
        database.shutdown();
    }
}