package me.perseusj.medusaantixray.managers;

import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import me.perseusj.medusaantixray.MedusaAntiXray;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class ConfigManager {
    /** All top-level keys present in the default config.yml. */
    private static final Set<String> KNOWN_TOP_LEVEL_KEYS =
            Set.of("detection", "worlds", "alerts", "messages", "database");

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

    // -------------------------------------------------------------------------
    // Detection settings
    // -------------------------------------------------------------------------

    public int getMinSampleSize() { return config.getInt("detection.min-sample-size", 64); }
    public double getAlertThreshold() { return config.getDouble("detection.alert-threshold", 0.08); }
    public int getWindowMinutes() { return config.getInt("detection.window-minutes", 30); }
    public double getHiddenOreWeight() { return config.getDouble("detection.hidden-ore-weight", 1.0); }
    public double getExposedOreWeight() { return config.getDouble("detection.exposed-ore-weight", 0.25); }

    // -------------------------------------------------------------------------
    // Overworld settings
    // -------------------------------------------------------------------------

    public boolean isOverworldEnabled() { return config.getBoolean("worlds.overworld.enabled", true); }
    public List<String> getOverworldNames() { return config.getStringList("worlds.overworld.names"); }
    public List<String> getOverworldOres() { return config.getStringList("worlds.overworld.tracked-ores"); }
    public List<String> getOverworldFillers() { return config.getStringList("worlds.overworld.filler-blocks"); }

    // -------------------------------------------------------------------------
    // Nether settings
    // -------------------------------------------------------------------------

    public boolean isNetherEnabled() { return config.getBoolean("worlds.nether.enabled", true); }
    public List<String> getNetherNames() { return config.getStringList("worlds.nether.names"); }
    public List<String> getNetherOres() { return config.getStringList("worlds.nether.tracked-ores"); }
    public List<String> getNetherFillers() { return config.getStringList("worlds.nether.filler-blocks"); }

    // -------------------------------------------------------------------------
    // Alerts
    // -------------------------------------------------------------------------

    public int getCooldownSeconds() { return config.getInt("alerts.cooldown-seconds", 60); }
    public String getStaffPermission() { return config.getString("alerts.staff-permission", "medusa.staff"); }
    public String getPrefix() { return config.getString("alerts.prefix", "&8[&4Medusa&8]&r"); }
    public String getAlertMessage() { return config.getString("alerts.alert-message", "{prefix} &c⚠ {player} &7may be X-raying! &cRatio: &f{ratio}% &7({score} pts / {total} blocks)"); }
    public String getCheckMessage() { return config.getString("alerts.check-message", "&7Player &f{player} &7| Score: &c{score} &7| Total: &f{total} &7| Ratio: &c{ratio}% &7| Window: &f{window}m"); }

    // -------------------------------------------------------------------------
    // Messages
    // -------------------------------------------------------------------------

    public String getNoPermissionMessage() { return config.getString("messages.no-permission", "&cYou don't have permission to use this command."); }
    public String getReloadSuccessMessage() { return config.getString("messages.reload-success", "&aConfiguration reloaded successfully."); }
    public String getPlayerNotFoundMessage() { return config.getString("messages.player-not-found", "&cPlayer &f{player} &cnot found or has no data."); }
    public String getUsageCheckMessage() { return config.getString("messages.usage-check", "&cUsage: /medusa check <player>"); }

    // -------------------------------------------------------------------------
    // Database settings
    // -------------------------------------------------------------------------

    public String getDatabaseType() { return config.getString("database.type", "sqlite"); }
    public String getSqliteFile() { return config.getString("database.sqlite.file", "medusa_antixray.db"); }
    public String getMysqlHost() { return config.getString("database.mysql.host", "localhost"); }
    public int getMysqlPort() { return config.getInt("database.mysql.port", 3306); }
    public String getMysqlDatabase() { return config.getString("database.mysql.database", "medusa_antixray"); }
    public String getMysqlUsername() { return config.getString("database.mysql.username", "root"); }
    public String getMysqlPassword() { return config.getString("database.mysql.password", ""); }
    public int getSaveIntervalMinutes() { return config.getInt("database.save-interval-minutes", 5); }

    // A3: Number of days to retain events in the database (0 = never purge).
    public int getRetentionDays() { return config.getInt("database.retention.days", 30); }

    // A5: Seconds between reconnection attempts when the database is unavailable (0 = never retry).
    public int getRetryIntervalSeconds() { return config.getInt("database.retry-interval-seconds", 120); }

    // -------------------------------------------------------------------------
    // A4: Config validation
    // -------------------------------------------------------------------------

    /**
     * Validates all config values and logs {@code WARNING} for any that are invalid,
     * out-of-range, or unrecognized. Invalid values do not prevent the plugin from enabling;
     * the plugin falls back to defaults in those cases.
     *
     * @param logger the server logger to write warnings to.
     */
    public void validate(Logger logger) {
        // --- detection ---
        double alertThreshold = getAlertThreshold();
        if (alertThreshold < 0.0 || alertThreshold > 1.0) {
            logger.warning("[Medusa] Config warning: detection.alert-threshold=" + alertThreshold
                    + " is out of range [0.0, 1.0]. Expected a fraction such as 0.08.");
        }

        int minSampleSize = getMinSampleSize();
        if (minSampleSize < 1) {
            logger.warning("[Medusa] Config warning: detection.min-sample-size=" + minSampleSize
                    + " must be >= 1.");
        }

        int windowMinutes = getWindowMinutes();
        if (windowMinutes < 1) {
            logger.warning("[Medusa] Config warning: detection.window-minutes=" + windowMinutes
                    + " must be >= 1.");
        }

        int cooldownSeconds = getCooldownSeconds();
        if (cooldownSeconds < 0) {
            logger.warning("[Medusa] Config warning: alerts.cooldown-seconds=" + cooldownSeconds
                    + " must be >= 0.");
        }

        double hiddenWeight = getHiddenOreWeight();
        if (hiddenWeight < 0) {
            logger.warning("[Medusa] Config warning: detection.hidden-ore-weight=" + hiddenWeight
                    + " must be >= 0.");
        }

        double exposedWeight = getExposedOreWeight();
        if (exposedWeight < 0) {
            logger.warning("[Medusa] Config warning: detection.exposed-ore-weight=" + exposedWeight
                    + " must be >= 0.");
        }

        // --- database type ---
        String dbType = getDatabaseType();
        if (!dbType.equalsIgnoreCase("sqlite") && !dbType.equalsIgnoreCase("mysql")) {
            logger.warning("[Medusa] Config warning: database.type=\"" + dbType
                    + "\" is not supported. Use \"sqlite\" or \"mysql\".");
        }

        if (dbType.equalsIgnoreCase("mysql")) {
            if (getMysqlHost() == null || getMysqlHost().isBlank()) {
                logger.warning("[Medusa] Config warning: database.mysql.host is blank.");
            }
            if (getMysqlDatabase() == null || getMysqlDatabase().isBlank()) {
                logger.warning("[Medusa] Config warning: database.mysql.database is blank.");
            }
            if (getMysqlUsername() == null || getMysqlUsername().isBlank()) {
                logger.warning("[Medusa] Config warning: database.mysql.username is blank.");
            }
        }

        // --- material name validation ---
        validateMaterials(logger, "worlds.overworld.tracked-ores", getOverworldOres());
        validateMaterials(logger, "worlds.overworld.filler-blocks", getOverworldFillers());
        validateMaterials(logger, "worlds.nether.tracked-ores", getNetherOres());
        validateMaterials(logger, "worlds.nether.filler-blocks", getNetherFillers());

        // --- world names non-empty when enabled ---
        if (isOverworldEnabled() && getOverworldNames().isEmpty()) {
            logger.warning("[Medusa] Config warning: worlds.overworld is enabled but worlds.overworld.names is empty.");
        }
        if (isNetherEnabled() && getNetherNames().isEmpty()) {
            logger.warning("[Medusa] Config warning: worlds.nether is enabled but worlds.nether.names is empty.");
        }

        // --- unknown top-level keys ---
        if (config.getKeys(false) != null) {
            for (String key : config.getKeys(false)) {
                if (!KNOWN_TOP_LEVEL_KEYS.contains(key)) {
                    logger.warning("[Medusa] Config warning: unrecognized top-level key \"" + key
                            + "\" in config.yml — did you make a typo?");
                }
            }
        }
    }

    /** Logs a warning for each entry in {@code names} that is not a valid {@link Material}. */
    private void validateMaterials(Logger logger, String path, List<String> names) {
        for (String name : names) {
            try {
                Material.valueOf(name.toUpperCase());
            } catch (IllegalArgumentException e) {
                logger.warning("[Medusa] Config warning: \"" + name
                        + "\" in " + path + " is not a valid Material name.");
            }
        }
    }
}
