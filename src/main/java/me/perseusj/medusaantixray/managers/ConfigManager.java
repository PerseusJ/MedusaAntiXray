package me.perseusj.medusaantixray.managers;

import org.bukkit.configuration.file.FileConfiguration;
import me.perseusj.medusaantixray.MedusaAntiXray;

import java.util.List;

public class ConfigManager {
    private final MedusaAntiXray plugin;
    private FileConfiguration config;

    public ConfigManager(MedusaAntiXray plugin) {
        this.plugin = plugin;
        this.config = plugin.getConfig();
    }

    public void reload() {
        plugin.reloadConfig();
        this.config = plugin.getConfig();
    }

    // Detection settings
    public int getMinSampleSize() { return config.getInt("detection.min-sample-size", 64); }
    public double getAlertThreshold() { return config.getDouble("detection.alert-threshold", 0.08); }
    public int getWindowMinutes() { return config.getInt("detection.window-minutes", 30); }
    public double getHiddenOreWeight() { return config.getDouble("detection.hidden-ore-weight", 1.0); }
    public double getExposedOreWeight() { return config.getDouble("detection.exposed-ore-weight", 0.25); }

    // Overworld settings
    public boolean isOverworldEnabled() { return config.getBoolean("worlds.overworld.enabled", true); }
    public List<String> getOverworldNames() { return config.getStringList("worlds.overworld.names"); }
    public List<String> getOverworldOres() { return config.getStringList("worlds.overworld.tracked-ores"); }
    public List<String> getOverworldFillers() { return config.getStringList("worlds.overworld.filler-blocks"); }

    // Nether settings
    public boolean isNetherEnabled() { return config.getBoolean("worlds.nether.enabled", true); }
    public List<String> getNetherNames() { return config.getStringList("worlds.nether.names"); }
    public List<String> getNetherOres() { return config.getStringList("worlds.nether.tracked-ores"); }
    public List<String> getNetherFillers() { return config.getStringList("worlds.nether.filler-blocks"); }

    // Alerts
    public int getCooldownSeconds() { return config.getInt("alerts.cooldown-seconds", 60); }
    public String getStaffPermission() { return config.getString("alerts.staff-permission", "medusa.staff"); }
    public String getPrefix() { return config.getString("alerts.prefix", "&8[&4Medusa&8]&r"); }
    public String getAlertMessage() { return config.getString("alerts.alert-message", "{prefix} &c⚠ {player} &7may be X-raying! &cRatio: &f{ratio}% &7({score} pts / {total} blocks)"); }
    public String getCheckMessage() { return config.getString("alerts.check-message", "&7Player &f{player} &7| Score: &c{score} &7| Total: &f{total} &7| Ratio: &c{ratio}% &7| Window: &f{window}m"); }

    // Messages
    public String getNoPermissionMessage() { return config.getString("messages.no-permission", "&cYou don't have permission to use this command."); }
    public String getReloadSuccessMessage() { return config.getString("messages.reload-success", "&aConfiguration reloaded successfully."); }
    public String getPlayerNotFoundMessage() { return config.getString("messages.player-not-found", "&cPlayer &f{player} &cnot found or has no data."); }
    public String getUsageCheckMessage() { return config.getString("messages.usage-check", "&cUsage: /medusa check <player>"); }

    // Database settings
    public String getDatabaseType() { return config.getString("database.type", "sqlite"); }
    public String getSqliteFile() { return config.getString("database.sqlite.file", "medusa_antixray.db"); }
    public String getMysqlHost() { return config.getString("database.mysql.host", "localhost"); }
    public int getMysqlPort() { return config.getInt("database.mysql.port", 3306); }
    public String getMysqlDatabase() { return config.getString("database.mysql.database", "medusa_antixray"); }
    public String getMysqlUsername() { return config.getString("database.mysql.username", "root"); }
    public String getMysqlPassword() { return config.getString("database.mysql.password", ""); }
    public int getSaveIntervalMinutes() { return config.getInt("database.save-interval-minutes", 5); }
}
