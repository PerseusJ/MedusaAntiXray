package me.perseusj.medusaantixray.listeners;

import me.perseusj.medusaantixray.data.PlayerData;
import me.perseusj.medusaantixray.managers.ConfigManager;
import me.perseusj.medusaantixray.managers.DataManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.util.Map;
import java.util.UUID;

public class SessionListener implements Listener {

    private final DataManager dataManager;
    private final ConfigManager config;

    public SessionListener(DataManager dataManager, ConfigManager config) {
        this.dataManager = dataManager;
        this.config = config;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (player.hasPermission("medusa.bypass")) {
            return;
        }
        dataManager.loadOrCreateAsync(player.getUniqueId(), player.getName());

        // C4: Apply trust multiplier on join
        PlayerData data = dataManager.getEntry(player.getUniqueId());
        if (data != null) {
            double multiplier = resolveTrustMultiplier(player);
            data.setTrustMultiplier(multiplier);
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        dataManager.saveAndRemoveAsync(event.getPlayer().getUniqueId());
    }

    // =========================================================================
    // C1 — Teleport-cooldown grace period
    // =========================================================================

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        if (!config.isTeleportCooldownEnabled()) return;
        Player player = event.getPlayer();
        if (player.hasPermission("medusa.bypass")) return;
        PlayerData data = dataManager.getEntry(player.getUniqueId());
        if (data != null) {
            data.setLastTeleportTimestamp(System.currentTimeMillis());
        }
    }

    // =========================================================================
    // C4 — Trust multiplier resolution
    // =========================================================================

    /**
     * Resolves the trust multiplier for a player.
     * Order: UUID whitelist > permission-based > default.
     */
    private double resolveTrustMultiplier(Player player) {
        UUID uuid = player.getUniqueId();

        // UUID whitelist has highest priority
        Map<String, Double> whitelist = config.getTrustPlayers();
        Double uuidMult = whitelist.get(uuid.toString());
        if (uuidMult != null) return uuidMult;

        // Permission-based multipliers (first match wins)
        Map<String, Double> permMults = config.getTrustPermMultipliers();
        for (Map.Entry<String, Double> entry : permMults.entrySet()) {
            if (player.hasPermission(entry.getKey())) {
                return entry.getValue();
            }
        }

        return config.getTrustDefaultMultiplier();
    }
}