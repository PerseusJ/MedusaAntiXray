package me.perseusj.medusaantixray.managers;

import me.perseusj.medusaantixray.utils.Utils;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public class AlertManager {
    private static AlertManager instance;

    private AlertManager() {}

    public static void initialize() {
        if (instance == null) {
            instance = new AlertManager();
        }
    }

    public static AlertManager getInstance() {
        return instance;
    }

    public void dispatch(Player suspect, double ratio, double score, int totalBlocks) {
        ConfigManager config = ConfigManager.getInstance();
        
        String template = config.getAlertMessage();
        String prefix = config.getPrefix();
        
        String message = template
                .replace("{prefix}", prefix)
                .replace("{player}", suspect.getName())
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
