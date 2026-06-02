package me.perseusj.medusaantixray.listeners;

import me.perseusj.medusaantixray.managers.DataManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class SessionListener implements Listener {

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (player.hasPermission("medusa.bypass")) {
            return;
        }
        DataManager.getInstance().createEntry(player.getUniqueId(), player.getName());
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        DataManager.getInstance().removeEntry(event.getPlayer().getUniqueId());
    }
}
