package me.perseusj.medusaantixray.managers;

import me.perseusj.medusaantixray.data.PlayerData;

import java.util.Collection;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class DataManager {
    private static DataManager instance;
    private final ConcurrentHashMap<UUID, PlayerData> cache;

    private DataManager() {
        this.cache = new ConcurrentHashMap<>();
    }

    public static void initialize() {
        if (instance == null) {
            instance = new DataManager();
        }
    }

    public static DataManager getInstance() {
        return instance;
    }

    public void createEntry(UUID uuid, String name) {
        cache.putIfAbsent(uuid, new PlayerData(uuid, name));
    }

    public PlayerData getEntry(UUID uuid) {
        return cache.get(uuid);
    }

    public void removeEntry(UUID uuid) {
        cache.remove(uuid);
    }

    public Collection<PlayerData> getAllEntries() {
        return cache.values();
    }

    public void shutdown() {
        cache.clear();
    }
}
