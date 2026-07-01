package me.perseusj.medusaantixray.managers;

import me.perseusj.medusaantixray.data.MineEvent;
import me.perseusj.medusaantixray.utils.Utils;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * E1 — Tracks staff who have enabled {@code /medusa watch} on a player and
 * dispatches a live verbose line to each watcher whenever a new MineEvent is
 * recorded for that player.
 *
 * <p>Watchers are identified by their UUID; the watched player is identified by
 * their PlayerData UUID. A watcher may watch multiple players, and multiple
 * watchers may watch the same player.
 */
public class WatchManager {

    /** watchedUuid → set of watcher (staff) UUIDs */
    private final Map<UUID, Set<UUID>> watchers = new ConcurrentHashMap<>();

    /** Returns {@code true} if anyone is currently watching {@code watchedUuid}. */
    public boolean isWatched(UUID watchedUuid) {
        Set<UUID> set = watchers.get(watchedUuid);
        return set != null && !set.isEmpty();
    }

    /**
     * Toggles watch state for a watcher on a watched player.
     *
     * @return {@code true} if watch was enabled, {@code false} if disabled.
     */
    public boolean toggleWatch(UUID watcherUuid, UUID watchedUuid) {
        Set<UUID> set = watchers.computeIfAbsent(watchedUuid, k -> ConcurrentHashMap.newKeySet());
        if (set.contains(watcherUuid)) {
            set.remove(watcherUuid);
            if (set.isEmpty()) watchers.remove(watchedUuid);
            return false;
        }
        set.add(watcherUuid);
        return true;
    }

    /** Returns {@code true} if {@code watcherUuid} is currently watching {@code watchedUuid}. */
    public boolean isWatching(UUID watcherUuid, UUID watchedUuid) {
        Set<UUID> set = watchers.get(watchedUuid);
        return set != null && set.contains(watcherUuid);
    }

    /**
     * Sends a verbose line describing {@code event} for {@code playerName} to
     * every online staff member watching that player.
     *
     * @param watchedUuid the UUID of the watched player
     * @param playerName  the watched player's display name
     * @param event       the MineEvent that was just recorded
     */
    public void notifyWatchers(UUID watchedUuid, String playerName, MineEvent event) {
        Set<UUID> set = watchers.get(watchedUuid);
        if (set == null || set.isEmpty()) return;

        String line = Utils.colorize("&8[&4Medusa&8]&r &7Watch &f" + playerName
                + " &7| valuable=&f" + event.isValuable()
                + " &7weight=&f" + String.format("%.2f", event.weight())
                + " &7y=&f" + event.y()
                + " &7vein=&f" + event.veinSize());

        for (UUID watcherUuid : set) {
            Player watcher = Bukkit.getPlayer(watcherUuid);
            if (watcher != null && watcher.isOnline()) {
                watcher.sendMessage(line);
            }
        }
    }
}
