package me.perseusj.medusaantixray.managers;

import me.perseusj.medusaantixray.utils.Utils;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public class AlertManager {
    private final ConfigManager config;

    public AlertManager(ConfigManager config) {
        this.config = config;
    }

    public void dispatch(String playerName, double ratio, double score, int totalBlocks) {
        String template = config.getAlertMessage();
        String prefix = config.getPrefix();

        String message = template
                .replace("{prefix}", prefix)
                .replace("{player}", playerName)
                .replace("{ratio}", String.format("%.1f", ratio * 100))
                .replace("{score}", String.format("%.2f", score))
                .replace("{total}", String.valueOf(totalBlocks));

        String coloredMessage = Utils.colorize(message);
        String permission = config.getStaffPermission();

        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.hasPermission(permission)) {
                online.sendMessage(coloredMessage);
            }
        }
    }
}
