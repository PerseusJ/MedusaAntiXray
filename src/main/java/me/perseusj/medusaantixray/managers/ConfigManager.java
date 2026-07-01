package me.perseusj.medusaantixray.managers;

import me.perseusj.medusaantixray.MedusaAntiXray;
import me.perseusj.medusaantixray.data.OreWeight;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

public class ConfigManager {

    /** All top-level keys present in the default config.yml. */
    private static final Set<String> KNOWN_TOP_LEVEL_KEYS =
            Set.of("detection", "worlds", "alerts", "messages", "database",
                    "false-positive-guards", "trust", "learning-mode", "commands", "gui");

    // -------------------------------------------------------------------------
    // B1: Depth-normalization helper record (package-private for tests if needed)
    // -------------------------------------------------------------------------
    private record DepthRange(int yMin, int yMax, double multiplier) {}

    private final MedusaAntiXray plugin;
    private FileConfiguration config;

    // -------------------------------------------------------------------------
    // B3: Per-ore weight caches.
    // Volatile references to immutable maps → safe for concurrent async reads.
    // Rebuilt atomically on every reload().
    // -------------------------------------------------------------------------
    private volatile Map<String, OreWeight> overworldOreWeights = Map.of();
    private volatile Map<String, OreWeight> netherOreWeights    = Map.of();
    private volatile Map<String, OreWeight> endOreWeights       = Map.of();

    // -------------------------------------------------------------------------
    // B1: Depth-profile cache (same concurrency model as above).
    // -------------------------------------------------------------------------
    private volatile Map<String, List<DepthRange>> depthProfileCache = Map.of();

    public ConfigManager(MedusaAntiXray plugin) {
        this.plugin = plugin;
        this.config = plugin.getConfig();
        loadCaches();
    }

    // -------------------------------------------------------------------------
    // Reload
    // -------------------------------------------------------------------------

    public void reload() {
        plugin.reloadConfig();
        this.config = plugin.getConfig();
        loadCaches();
    }

    // =========================================================================
    // Cache management
    // =========================================================================

    /**
     * Rebuilds all derived caches from the current {@link FileConfiguration}.
     * Called from the constructor and from {@link #reload()}.
     */
    private void loadCaches() {
        this.overworldOreWeights = Map.copyOf(loadOreWeightMap("worlds.overworld"));
        this.netherOreWeights    = Map.copyOf(loadOreWeightMap("worlds.nether"));
        this.endOreWeights       = Map.copyOf(loadOreWeightMap("worlds.end"));
        this.depthProfileCache   = Map.copyOf(loadDepthProfiles());
    }

    /**
     * Loads the ore→{@link OreWeight} mapping for {@code worldPath}.
     *
     * <p>Supports two config formats for {@code tracked-ores}:
     * <ul>
     *   <li><b>New map format (B3)</b> — each key is an ore name, value is a section with
     *       optional {@code hidden-weight} and {@code exposed-weight}.</li>
     *   <li><b>Old string-list format</b> — a plain {@code List<String>}; all ores receive
     *       the world-level (or global) default weights.</li>
     * </ul>
     */
    private Map<String, OreWeight> loadOreWeightMap(String worldPath) {
        // Per-world fallback defaults; if absent, use the global detection defaults.
        double defaultHidden  = config.getDouble(worldPath + ".default-hidden-ore-weight",  getHiddenOreWeight());
        double defaultExposed = config.getDouble(worldPath + ".default-exposed-ore-weight", getExposedOreWeight());

        Map<String, OreWeight> result = new LinkedHashMap<>();

        // Try new map format first (B3).
        ConfigurationSection section = config.getConfigurationSection(worldPath + ".tracked-ores");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                ConfigurationSection oreSection = section.getConfigurationSection(key);
                if (oreSection != null) {
                    double hidden  = oreSection.getDouble("hidden-weight",  defaultHidden);
                    double exposed = oreSection.getDouble("exposed-weight", defaultExposed);
                    result.put(key.toUpperCase(), new OreWeight(hidden, exposed));
                } else {
                    // Key present but no sub-section (e.g. bare key with null value in YAML).
                    result.put(key.toUpperCase(), new OreWeight(defaultHidden, defaultExposed));
                }
            }
            return result;
        }

        // Fall back to old string-list format.
        for (String ore : config.getStringList(worldPath + ".tracked-ores")) {
            result.put(ore.toUpperCase(), new OreWeight(defaultHidden, defaultExposed));
        }
        return result;
    }

    /**
     * Loads depth-normalization profiles from {@code detection.depth-normalization.profiles}.
     * Returns an empty map when depth normalization is disabled (avoids parsing cost).
     */
    private Map<String, List<DepthRange>> loadDepthProfiles() {
        if (!isDepthNormalizationEnabled()) return Map.of();

        ConfigurationSection profilesSection = config.getConfigurationSection(
                "detection.depth-normalization.profiles");
        if (profilesSection == null) return Map.of();

        Map<String, List<DepthRange>> result = new LinkedHashMap<>();
        for (String oreName : profilesSection.getKeys(false)) {
            List<Map<?, ?>> rangeMaps = profilesSection.getMapList(oreName);
            List<DepthRange> ranges   = new ArrayList<>(rangeMaps.size());
            for (Map<?, ?> rangeMap : rangeMaps) {
                Object yMinObj = rangeMap.get("y-min");
                Object yMaxObj = rangeMap.get("y-max");
                Object multObj = rangeMap.get("multiplier");
                int    yMin = (yMinObj instanceof Number n) ? n.intValue()    : Integer.MIN_VALUE;
                int    yMax = (yMaxObj instanceof Number n) ? n.intValue()    : Integer.MAX_VALUE;
                double mult = (multObj instanceof Number n) ? n.doubleValue() : 1.0;
                ranges.add(new DepthRange(yMin, yMax, mult));
            }
            result.put(oreName.toUpperCase(), Collections.unmodifiableList(ranges));
        }
        return result;
    }

    // =========================================================================
    // Detection settings
    // =========================================================================

    public int    getMinSampleSize()    { return config.getInt("detection.min-sample-size", 64); }
    public double getAlertThreshold()   { return config.getDouble("detection.alert-threshold", 0.08); }
    public int    getWindowMinutes()    { return config.getInt("detection.window-minutes", 30); }
    /** Global fallback hidden-ore weight. Per-ore overrides via {@link #getOverworldOreWeight} etc. */
    public double getHiddenOreWeight()  { return config.getDouble("detection.hidden-ore-weight", 1.0); }
    /** Global fallback exposed-ore weight. Per-ore overrides via {@link #getOverworldOreWeight} etc. */
    public double getExposedOreWeight() { return config.getDouble("detection.exposed-ore-weight", 0.25); }

    // =========================================================================
    // B1 — Depth normalization
    // =========================================================================

    public boolean isDepthNormalizationEnabled() {
        return config.getBoolean("detection.depth-normalization.enabled", false);
    }

    public double getDefaultDepthMultiplier() {
        return config.getDouble("detection.depth-normalization.default-multiplier", 1.0);
    }

    /**
     * Returns the depth-normalization multiplier for {@code materialName} at world-Y {@code y}.
     *
     * <p>Walks the cached profile ranges for the given ore; returns
     * {@link #getDefaultDepthMultiplier()} when no range matches or no profile exists.</p>
     *
     * <p>Safe to call from any thread — reads only from the volatile, immutable cache.</p>
     */
    public double getDepthMultiplier(String materialName, int y) {
        List<DepthRange> ranges = depthProfileCache.get(materialName.toUpperCase());
        if (ranges == null) return getDefaultDepthMultiplier();
        for (DepthRange range : ranges) {
            if (y >= range.yMin() && y <= range.yMax()) {
                return range.multiplier();
            }
        }
        return getDefaultDepthMultiplier();
    }

    // =========================================================================
    // B2 — Vein grouping
    // =========================================================================

    public boolean isVeinGroupingEnabled() {
        return config.getBoolean("detection.vein-grouping.enabled", true);
    }

    /** Maximum Chebyshev distance between consecutive ore blocks to count as the same vein. */
    public int getVeinMaxDistance() {
        return config.getInt("detection.vein-grouping.max-distance", 3);
    }

    /** Maximum server ticks between consecutive ore blocks to count as the same vein. */
    public int getVeinTimeoutTicks() {
        return config.getInt("detection.vein-grouping.timeout-ticks", 100);
    }

    /** Returns {@code "divide"} or {@code "first-only"}. Defaults to {@code "divide"}. */
    public String getVeinMode() {
        return config.getString("detection.vein-grouping.mode", "divide");
    }

    // =========================================================================
    // B3 — Per-ore configurable weights
    // =========================================================================

    /**
     * Returns the {@link OreWeight} for the given Overworld ore.
     * Falls back to the global {@link #getHiddenOreWeight()} / {@link #getExposedOreWeight()}
     * if the ore is not in the configured map.
     */
    public OreWeight getOverworldOreWeight(String materialName) {
        return overworldOreWeights.getOrDefault(materialName.toUpperCase(),
                new OreWeight(getHiddenOreWeight(), getExposedOreWeight()));
    }

    /** Same as {@link #getOverworldOreWeight} but for Nether ores. */
    public OreWeight getNetherOreWeight(String materialName) {
        return netherOreWeights.getOrDefault(materialName.toUpperCase(),
                new OreWeight(getHiddenOreWeight(), getExposedOreWeight()));
    }

    /** Same as {@link #getOverworldOreWeight} but for End ores (B3 + B6). */
    public OreWeight getEndOreWeight(String materialName) {
        return endOreWeights.getOrDefault(materialName.toUpperCase(),
                new OreWeight(getHiddenOreWeight(), getExposedOreWeight()));
    }

    // =========================================================================
    // B4 — Tool & enchantment modifiers
    // =========================================================================

    public boolean isToolModifiersEnabled() {
        return config.getBoolean("detection.tool-modifiers.enabled", true);
    }

    /** Multiplier applied to the ore weight when the player uses a Silk Touch tool. */
    public double getSilkTouchMultiplier() {
        return config.getDouble("detection.tool-modifiers.silk-touch-multiplier", 0.5);
    }

    /**
     * Per-Fortune-level multiplier. Applied as {@code pow(multiplier, fortuneLevel)}.
     * E.g., Fortune III → {@code 0.8^3 ≈ 0.51} suspicion reduction.
     */
    public double getFortuneMultiplierPerLevel() {
        return config.getDouble("detection.tool-modifiers.fortune-multiplier-per-level", 0.8);
    }

    /** Multiplier applied when the tool has no relevant enchantments (slightly more suspicious). */
    public double getNoEnchantmentsMultiplier() {
        return config.getDouble("detection.tool-modifiers.no-enchantments-multiplier", 1.2);
    }

    // =========================================================================
    // B5 — Multi-face exposure scoring
    // =========================================================================

    public boolean isExposureScoringEnabled() {
        return config.getBoolean("detection.exposure-scoring.enabled", false);
    }

    /**
     * Multiplier applied to the interpolated weight when any adjacent face has sky light {@code > 0}.
     * Reduces suspicion for surface or near-surface ores.
     */
    public double getSkyLightPenalty() {
        return config.getDouble("detection.exposure-scoring.sky-light-penalty", 0.5);
    }

    /**
     * If the number of air-adjacent faces is {@code <=} this threshold, the ore is treated as
     * fully hidden (receives maximum weight). Defaults to {@code 1}.
     */
    public int getHiddenThresholdFaces() {
        return config.getInt("detection.exposure-scoring.hidden-threshold-faces", 1);
    }

    // =========================================================================
    // C1 — Teleport-cooldown grace period
    // =========================================================================

    public boolean isTeleportCooldownEnabled() {
        return getTeleportCooldownSeconds() > 0;
    }

    public int getTeleportCooldownSeconds() {
        return config.getInt("false-positive-guards.teleport-cooldown-seconds", 10);
    }

    // =========================================================================
    // C2 — Mining-style classification multipliers
    // =========================================================================

    public boolean isStyleMultipliersEnabled() {
        return config.getBoolean("detection.style-multipliers.enabled", true);
    }

    public double getStyleMultiplier(String style) {
        String path = "detection.style-multipliers." + style.toLowerCase();
        double defaultValue = switch (style.toLowerCase()) {
            case "cave"    -> 0.6;
            case "branch"  -> 1.0;
            case "strip"   -> 1.1;
            default        -> 1.0;
        };
        return config.getDouble(path, defaultValue);
    }

    // =========================================================================
    // C3 — Learning / baseline calibration mode
    // =========================================================================

    public boolean isLearningModeEnabled() {
        return config.getBoolean("learning-mode.enabled", false);
    }

    public int getLearningModeDurationMinutes() {
        return config.getInt("learning-mode.duration-minutes", 1440);
    }

    public boolean isLearningModeLogOutput() {
        return config.getBoolean("learning-mode.log-output", true);
    }

    public boolean isLearningModePersist() {
        return config.getBoolean("learning-mode.persist", true);
    }

    public int getLearningModeRecommendPercentile() {
        return config.getInt("learning-mode.recommend-percentile", 99);
    }

    // =========================================================================
    // C4 — Trust tiers & player whitelist
    // =========================================================================

    public double getTrustDefaultMultiplier() {
        return config.getDouble("trust.default", 1.0);
    }

    /**
     * Returns an ordered map of permission node → trust multiplier.
     * First match wins when checking a player's permissions.
     */
    public Map<String, Double> getTrustPermMultipliers() {
        ConfigurationSection section = config.getConfigurationSection("trust.perm-multipliers");
        if (section == null) return Map.of();
        Map<String, Double> result = new LinkedHashMap<>();
        for (String key : section.getKeys(false)) {
            result.put(key, section.getDouble(key));
        }
        return result;
    }

    /**
     * Returns a map of player UUID string → trust multiplier from the whitelist.
     */
    public Map<String, Double> getTrustPlayers() {
        ConfigurationSection section = config.getConfigurationSection("trust.players");
        if (section == null) return Map.of();
        Map<String, Double> result = new LinkedHashMap<>();
        for (String key : section.getKeys(false)) {
            result.put(key, section.getDouble(key));
        }
        return result;
    }

    /**
     * C4: Writes {@code trust.players.<uuid>} into the live config and persists it to disk.
     * Pass {@code null} as the multiplier to remove a player from the whitelist.
     *
     * @param uuid       the player's UUID string
     * @param multiplier the trust multiplier (0.0 = fully exempt), or {@code null} to remove
     */
    public void setTrustPlayer(String uuid, Double multiplier) {
        config.set("trust.players." + uuid, multiplier);
        plugin.saveConfig();
    }


    // =========================================================================
    // C5 — Mine-gap multiplier (vein-first-discovery time metric)
    // =========================================================================

    public boolean isMineGapMultiplierEnabled() {
        return config.getBoolean("detection.mine-gap-multiplier.enabled", true);
    }

    public long getMineGapMinMs() {
        return config.getLong("detection.mine-gap-multiplier.min-gap-ms", 3000);
    }

    public long getMineGapMaxMs() {
        return config.getLong("detection.mine-gap-multiplier.max-gap-ms", 120000);
    }

    public double getMineGapMaxMultiplier() {
        return config.getDouble("detection.mine-gap-multiplier.max-multiplier", 2.0);
    }

    /**
     * Returns the gap multiplier for a given inter-ore time gap.
     * Linear interpolation between min-gap-ms (max multiplier) and max-gap-ms (1.0).
     * Gaps longer than max-gap-ms return 1.0 (no effect).
     * Gaps shorter than min-gap-ms return max-multiplier.
     */
    public double getMineGapMultiplier(long gapMs) {
        long minGap = getMineGapMinMs();
        long maxGap = getMineGapMaxMs();
        double maxMult = getMineGapMaxMultiplier();
        if (gapMs <= minGap) return maxMult;
        if (gapMs >= maxGap) return 1.0;
        double t = (double) (gapMs - minGap) / (double) (maxGap - minGap);
        return maxMult - t * (maxMult - 1.0);
    }

    // =========================================================================
    // Overworld settings
    // =========================================================================

    public boolean      isOverworldEnabled()    { return config.getBoolean("worlds.overworld.enabled", true); }
    public List<String> getOverworldNames()     { return config.getStringList("worlds.overworld.names"); }
    /** Returns the tracked-ore names for the Overworld (derived from the B3 weight map). */
    public List<String> getOverworldOres()      { return new ArrayList<>(overworldOreWeights.keySet()); }
    public List<String> getOverworldFillers()   { return config.getStringList("worlds.overworld.filler-blocks"); }

    // =========================================================================
    // Nether settings
    // =========================================================================

    public boolean      isNetherEnabled()       { return config.getBoolean("worlds.nether.enabled", true); }
    public List<String> getNetherNames()        { return config.getStringList("worlds.nether.names"); }
    /** Returns the tracked-ore names for the Nether (derived from the B3 weight map). */
    public List<String> getNetherOres()         { return new ArrayList<>(netherOreWeights.keySet()); }
    public List<String> getNetherFillers()      { return config.getStringList("worlds.nether.filler-blocks"); }

    // =========================================================================
    // B6 — End world settings
    // =========================================================================

    public boolean      isEndEnabled()          { return config.getBoolean("worlds.end.enabled", false); }
    public List<String> getEndNames()           { return config.getStringList("worlds.end.names"); }
    /** Returns the tracked-ore names for the End (derived from the B3 weight map). */
    public List<String> getEndOres()            { return new ArrayList<>(endOreWeights.keySet()); }
    public List<String> getEndFillers()         { return config.getStringList("worlds.end.filler-blocks"); }

    // =========================================================================
    // Alerts
    // =========================================================================

    public int    getCooldownSeconds()    { return config.getInt("alerts.cooldown-seconds", 60); }
    public String getStaffPermission()   { return config.getString("alerts.staff-permission", "medusa.staff"); }
    public String getPrefix()            { return config.getString("alerts.prefix", "&8[&4Medusa&8]&r"); }
    public String getAlertMessage()      { return config.getString("alerts.alert-message",
            "{prefix} &c⚠ {player} &7may be X-raying! &cRatio: &f{ratio}% &7({score} pts / {total} blocks)"); }
    public String getCheckMessage()      { return config.getString("alerts.check-message",
            "&7Player &f{player} &7| Score: &c{score} &7| Total: &f{total} &7| Ratio: &c{ratio}% &7| Window: &f{window}m"); }

    // =========================================================================
    // Messages
    // =========================================================================

    public String getNoPermissionMessage()   { return config.getString("messages.no-permission", "&cYou don't have permission to use this command."); }
    public String getReloadSuccessMessage()  { return config.getString("messages.reload-success", "&aConfiguration reloaded successfully."); }
    public String getPlayerNotFoundMessage() { return config.getString("messages.player-not-found", "&cPlayer &f{player} &cnot found or has no data."); }
    public String getUsageCheckMessage()     { return config.getString("messages.usage-check", "&cUsage: /medusa check <player>"); }

    // E1 — New message templates
    public String getTopHeaderMessage()      { return config.getString("messages.top-header",
            "&7=== Top Suspects (Page {page}/{totalPages}) ==="); }
    public String getTopEntryMessage()       { return config.getString("messages.top-entry",
            "&c#{rank} {player} &7| Ratio: &c{ratio}% &7| Score: &f{score} &7| Blocks: &f{total}"); }
    public String getTopNoResultsMessage()   { return config.getString("messages.top-no-results",
            "&7No flagged players."); }
    public String getHistoryHeaderMessage()  { return config.getString("messages.history-header",
            "&7Alert history for &f{player} &7(page {page}/{totalPages}):"); }
    public String getHistoryEntryMessage()   { return config.getString("messages.history-entry",
            "&7{timestamp} &c{tier} &7Ratio: &c{ratio}% &7Score: &f{score}"); }
    public String getHistoryEmptyMessage()   { return config.getString("messages.history-empty",
            "&7No alert history for &f{player}."); }
    public String getResetSuccessMessage()   { return config.getString("messages.reset-success",
            "&aDetection data for &f{player} &ahas been reset."); }
    public String getResetConfirmMessage()   { return config.getString("messages.reset-confirm",
            "&eUse &c/medusa reset {player} confirm &eto proceed."); }
    public String getStatsMessage()          { return config.getString("messages.stats-message",
            "&7Tracked: &f{tracked} &7| Flagged: &c{flagged} &7| Alerts today: &c{alertsToday}"); }
    public String getWatchEnabledMessage()   { return config.getString("messages.watch-enabled",
            "&aNow watching &f{player}&a. Live updates enabled."); }
    public String getWatchDisabledMessage()  { return config.getString("messages.watch-disabled",
            "&7Stopped watching &f{player}&7."); }
    public String getHelpHeaderMessage()     { return config.getString("messages.help-header",
            "&7=== Medusa Anti-Xray Help ==="); }
    public String getListEntryMessage()      { return config.getString("messages.list-entry",
            "&c{player} &7| Ratio: &c{ratio}% &7| Score: &f{score}"); }
    public String getListHeaderMessage()     { return config.getString("messages.list-header",
            "&7=== Flagged Players (Page {page}/{totalPages}) ==="); }
    public String getListEmptyMessage()      { return config.getString("messages.list-empty",
            "&7No flagged players currently online or cached."); }
    public String getResetNoConfirmMessage() { return config.getString("messages.reset-no-confirm",
            "&cWarning &7| &f{player} &7| Ratio: &c{ratio}% &7| Score: &f{score} &7| Blocks: &f{total}"); }
    public String getUsageMessage()          { return config.getString("messages.usage",
            "&cUsage: /medusa <reload|check|trust|top|history|reset|stats|watch|list|gui|help>"); }

    // E2 — Command settings
    public int getPageSize()                 { return config.getInt("commands.page-size", 10); }
    // E3 — GUI settings
    public boolean isGuiEnabled()            { return config.getBoolean("gui.enabled", true); }
    public String  getGuiTitle()             { return config.getString("gui.title", "Medusa \u2014 Suspects"); }
    public int     getGuiPageSize()          { return config.getInt("gui.page-size", 45); }
    public List<String> getGuiSkullLore()    { return config.getStringList("gui.skull-lore"); }

    // =========================================================================
    // Database settings
    // =========================================================================

    public String getDatabaseType()      { return config.getString("database.type", "sqlite"); }
    public String getSqliteFile()        { return config.getString("database.sqlite.file", "medusa_antixray.db"); }
    public String getMysqlHost()         { return config.getString("database.mysql.host", "localhost"); }
    public int    getMysqlPort()         { return config.getInt("database.mysql.port", 3306); }
    public String getMysqlDatabase()     { return config.getString("database.mysql.database", "medusa_antixray"); }
    public String getMysqlUsername()     { return config.getString("database.mysql.username", "root"); }
    public String getMysqlPassword()     { return config.getString("database.mysql.password", ""); }
    public int    getSaveIntervalMinutes(){ return config.getInt("database.save-interval-minutes", 5); }

    // A3
    public int getRetentionDays()        { return config.getInt("database.retention.days", 30); }
    // A5
    public int getRetryIntervalSeconds() { return config.getInt("database.retry-interval-seconds", 120); }

    // =========================================================================
    // A4 + B-phase: Config validation
    // =========================================================================

    /**
     * Validates all config values and logs {@code WARNING} for any that are invalid,
     * out-of-range, or unrecognized. Invalid values do not prevent the plugin from enabling.
     *
     * @param logger the server logger to write warnings to
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
            logger.warning("[Medusa] Config warning: detection.min-sample-size=" + minSampleSize + " must be >= 1.");
        }
        int windowMinutes = getWindowMinutes();
        if (windowMinutes < 1) {
            logger.warning("[Medusa] Config warning: detection.window-minutes=" + windowMinutes + " must be >= 1.");
        }
        int cooldownSeconds = getCooldownSeconds();
        if (cooldownSeconds < 0) {
            logger.warning("[Medusa] Config warning: alerts.cooldown-seconds=" + cooldownSeconds + " must be >= 0.");
        }
        if (getHiddenOreWeight() < 0) {
            logger.warning("[Medusa] Config warning: detection.hidden-ore-weight=" + getHiddenOreWeight() + " must be >= 0.");
        }
        if (getExposedOreWeight() < 0) {
            logger.warning("[Medusa] Config warning: detection.exposed-ore-weight=" + getExposedOreWeight() + " must be >= 0.");
        }

        // --- database type ---
        String dbType = getDatabaseType();
        if (!dbType.equalsIgnoreCase("sqlite") && !dbType.equalsIgnoreCase("mysql")) {
            logger.warning("[Medusa] Config warning: database.type=\"" + dbType
                    + "\" is not supported. Use \"sqlite\" or \"mysql\".");
        }
        if (dbType.equalsIgnoreCase("mysql")) {
            if (getMysqlHost()     == null || getMysqlHost().isBlank())
                logger.warning("[Medusa] Config warning: database.mysql.host is blank.");
            if (getMysqlDatabase() == null || getMysqlDatabase().isBlank())
                logger.warning("[Medusa] Config warning: database.mysql.database is blank.");
            if (getMysqlUsername() == null || getMysqlUsername().isBlank())
                logger.warning("[Medusa] Config warning: database.mysql.username is blank.");
        }

        // --- material names ---
        validateMaterials(logger, "worlds.overworld.tracked-ores",  getOverworldOres());
        validateMaterials(logger, "worlds.overworld.filler-blocks", getOverworldFillers());
        validateMaterials(logger, "worlds.nether.tracked-ores",     getNetherOres());
        validateMaterials(logger, "worlds.nether.filler-blocks",    getNetherFillers());
        if (isEndEnabled()) {
            validateMaterials(logger, "worlds.end.tracked-ores",  getEndOres());
            validateMaterials(logger, "worlds.end.filler-blocks", getEndFillers());
        }

        // --- world names non-empty when enabled ---
        if (isOverworldEnabled() && getOverworldNames().isEmpty())
            logger.warning("[Medusa] Config warning: worlds.overworld is enabled but worlds.overworld.names is empty.");
        if (isNetherEnabled() && getNetherNames().isEmpty())
            logger.warning("[Medusa] Config warning: worlds.nether is enabled but worlds.nether.names is empty.");
        if (isEndEnabled() && getEndNames().isEmpty())
            logger.warning("[Medusa] Config warning: worlds.end is enabled but worlds.end.names is empty.");

        // --- B1: depth-normalization ---
        if (isDepthNormalizationEnabled()) {
            double defMult = getDefaultDepthMultiplier();
            if (defMult < 0) {
                logger.warning("[Medusa] Config warning: detection.depth-normalization.default-multiplier="
                        + defMult + " must be >= 0.");
            }
            Set<String> allTracked = new HashSet<>();
            allTracked.addAll(getOverworldOres());
            allTracked.addAll(getNetherOres());
            allTracked.addAll(getEndOres());
            for (String oreName : depthProfileCache.keySet()) {
                if (!allTracked.contains(oreName)) {
                    logger.warning("[Medusa] Config warning: depth-normalization profile '"
                            + oreName + "' does not appear in any tracked-ores list.");
                }
            }
        }

        // --- B2: vein grouping ---
        if (isVeinGroupingEnabled()) {
            if (getVeinMaxDistance() < 0)
                logger.warning("[Medusa] Config warning: detection.vein-grouping.max-distance must be >= 0.");
            if (getVeinTimeoutTicks() < 0)
                logger.warning("[Medusa] Config warning: detection.vein-grouping.timeout-ticks must be >= 0.");
            String mode = getVeinMode();
            if (!"divide".equalsIgnoreCase(mode) && !"first-only".equalsIgnoreCase(mode)) {
                logger.warning("[Medusa] Config warning: detection.vein-grouping.mode='" + mode
                        + "' is invalid. Use 'divide' or 'first-only'.");
            }
        }

        // --- B3: per-ore weight values ---
        validateOreWeightMap(logger, "overworld", overworldOreWeights);
        validateOreWeightMap(logger, "nether",    netherOreWeights);
        if (isEndEnabled()) validateOreWeightMap(logger, "end", endOreWeights);

        // --- B4: tool modifiers ---
        if (isToolModifiersEnabled()) {
            if (getSilkTouchMultiplier() < 0)
                logger.warning("[Medusa] Config warning: detection.tool-modifiers.silk-touch-multiplier must be >= 0.");
            if (getFortuneMultiplierPerLevel() < 0)
                logger.warning("[Medusa] Config warning: detection.tool-modifiers.fortune-multiplier-per-level must be >= 0.");
            if (getNoEnchantmentsMultiplier() < 0)
                logger.warning("[Medusa] Config warning: detection.tool-modifiers.no-enchantments-multiplier must be >= 0.");
        }

        // --- B5: exposure scoring ---
        if (isExposureScoringEnabled()) {
            double penalty = getSkyLightPenalty();
            if (penalty < 0 || penalty > 1) {
                logger.warning("[Medusa] Config warning: detection.exposure-scoring.sky-light-penalty="
                        + penalty + " is out of range [0.0, 1.0].");
            }
            int threshold = getHiddenThresholdFaces();
            if (threshold < 0 || threshold > 6) {
                logger.warning("[Medusa] Config warning: detection.exposure-scoring.hidden-threshold-faces="
                        + threshold + " is out of range [0, 6].");
            }
        }

        // --- C1: teleport cooldown ---
        if (isTeleportCooldownEnabled() && getTeleportCooldownSeconds() < 0) {
            logger.warning("[Medusa] Config warning: false-positive-guards.teleport-cooldown-seconds must be >= 0.");
        }

        // --- C2: style multipliers ---
        if (isStyleMultipliersEnabled()) {
            String[] styles = {"cave", "branch", "strip", "unknown"};
            for (String s : styles) {
                double m = getStyleMultiplier(s);
                if (m < 0) {
                    logger.warning("[Medusa] Config warning: detection.style-multipliers." + s
                            + "=" + m + " must be >= 0.");
                }
            }
        }

        // --- C3: learning mode ---
        if (isLearningModeEnabled()) {
            if (getLearningModeDurationMinutes() <= 0) {
                logger.warning("[Medusa] Config warning: learning-mode.duration-minutes must be > 0.");
            }
            int pct = getLearningModeRecommendPercentile();
            if (pct < 1 || pct > 100) {
                logger.warning("[Medusa] Config warning: learning-mode.recommend-percentile="
                        + pct + " is out of range [1, 100].");
            }
        }

        // --- C4: trust ---
        if (getTrustDefaultMultiplier() < 0) {
            logger.warning("[Medusa] Config warning: trust.default must be >= 0.");
        }
        for (Map.Entry<String, Double> e : getTrustPermMultipliers().entrySet()) {
            if (e.getValue() < 0) {
                logger.warning("[Medusa] Config warning: trust.perm-multipliers." + e.getKey() + " must be >= 0.");
            }
        }
        for (Map.Entry<String, Double> e : getTrustPlayers().entrySet()) {
            if (e.getValue() < 0) {
                logger.warning("[Medusa] Config warning: trust.players." + e.getKey() + " must be >= 0.");
            }
        }

        // --- C5: mine-gap multiplier ---
        if (isMineGapMultiplierEnabled()) {
            long minGap = getMineGapMinMs();
            long maxGap = getMineGapMaxMs();
            double maxMult = getMineGapMaxMultiplier();
            if (minGap < 0) {
                logger.warning("[Medusa] Config warning: detection.mine-gap-multiplier.min-gap-ms must be >= 0.");
            }
            if (maxGap <= minGap) {
                logger.warning("[Medusa] Config warning: detection.mine-gap-multiplier.max-gap-ms must be > min-gap-ms.");
            }
            if (maxMult < 1.0) {
                logger.warning("[Medusa] Config warning: detection.mine-gap-multiplier.max-multiplier must be >= 1.0.");
            }
        }

        // --- D1: alert history ---
        if (getHistoryRetentionDays() < 0) {
            logger.warning("[Medusa] Config warning: alerts.history-retention-days must be >= 0.");
        }

        // --- D2: tier threshold ordering ---
        double warnThreshold  = getTierThreshold("warning");
        double tierAlertThreshold = getTierThreshold("alert");
        double critThreshold  = getTierThreshold("critical");
        if (warnThreshold >= tierAlertThreshold) {
            logger.warning("[Medusa] Config warning: alerts.tiers.warning.threshold (" + warnThreshold
                    + ") >= alerts.tiers.alert.threshold (" + tierAlertThreshold
                    + "). WARNING tier may never resolve separately from ALERT.");
        }
        if (tierAlertThreshold >= critThreshold) {
            logger.warning("[Medusa] Config warning: alerts.tiers.alert.threshold (" + tierAlertThreshold
                    + ") >= alerts.tiers.critical.threshold (" + critThreshold
                    + "). ALERT tier may never resolve separately from CRITICAL.");
        }

        // --- D5: boss-bar ---
        if (isAlertModeBossBar()) {
            String color = getAlertBossBarColor();
            try {
                org.bukkit.boss.BarColor.valueOf(color.toUpperCase());
            } catch (IllegalArgumentException e) {
                logger.warning("[Medusa] Config warning: alerts.alert-modes.boss-bar-color \"" + color
                        + "\" is not a valid BarColor (PINK, BLUE, RED, GREEN, YELLOW, PURPLE, WHITE).");
            }
            if (getAlertBossBarSeconds() < 1) {
                logger.warning("[Medusa] Config warning: alerts.alert-modes.boss-bar-seconds must be >= 1.");
            }
        }

        // --- D6: digest ---
        if (isDigestEnabled()) {
            if (getDigestIntervalMinutes() < 1) {
                logger.warning("[Medusa] Config warning: alerts.digest.interval-minutes must be >= 1.");
            }
            if (getDigestTopN() < 1) {
                logger.warning("[Medusa] Config warning: alerts.digest.top-n must be >= 1.");
            }
            if (getDigestMinRatio() < 0) {
                logger.warning("[Medusa] Config warning: alerts.digest.min-ratio must be >= 0.");
            }
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

    /** Logs a warning for each entry in {@code names} that is not a valid {@link org.bukkit.Material}. */
    private void validateMaterials(Logger logger, String path, List<String> names) {
        for (String name : names) {
            try {
                org.bukkit.Material.valueOf(name.toUpperCase());
            } catch (IllegalArgumentException e) {
                logger.warning("[Medusa] Config warning: \"" + name
                        + "\" in " + path + " is not a valid Material name.");
            }
        }
    }

    /** Logs warnings for any negative ore weights in the given map. */
    private void validateOreWeightMap(Logger logger, String worldName, Map<String, OreWeight> map) {
        for (Map.Entry<String, OreWeight> entry : map.entrySet()) {
            String ore = entry.getKey();
            OreWeight w = entry.getValue();
            if (w.hiddenWeight() < 0)
                logger.warning("[Medusa] Config warning: hidden-weight for " + ore
                        + " in worlds." + worldName + ".tracked-ores must be >= 0.");
            if (w.exposedWeight() < 0)
                logger.warning("[Medusa] Config warning: exposed-weight for " + ore
                        + " in worlds." + worldName + ".tracked-ores must be >= 0.");
        }
    }

    // =========================================================================
    // D1 — Alert history
    // =========================================================================

    /** Whether to persist each dispatched alert to the {@code medusa_alerts} DB table. */
    public boolean isPersistHistory()        { return config.getBoolean("alerts.persist-history", true); }
    /** Days to retain alert history rows (0 = keep forever). */
    public int     getHistoryRetentionDays() { return config.getInt("alerts.history-retention-days", 90); }

    // =========================================================================
    // D2 — Escalation tiers
    // =========================================================================

    /**
     * Returns the minimum ratio required to trigger the given {@code tier}.
     * Defaults: warning=0.06, alert=detection.alert-threshold (backward compat), critical=0.25.
     */
    public double getTierThreshold(String tier) {
        double def = switch (tier.toLowerCase()) {
            case "warning"  -> 0.06;
            case "alert"    -> getAlertThreshold(); // falls back to detection.alert-threshold
            case "critical" -> 0.25;
            default         -> 0.08;
        };
        return config.getDouble("alerts.tiers." + tier.toLowerCase() + ".threshold", def);
    }

    /** Returns the message template for the given tier. Falls back to the legacy alert-message. */
    public String getTierMessage(String tier) {
        String def = switch (tier.toLowerCase()) {
            case "warning"  -> "{prefix} &e\u26a0 {player} &7shows unusual mining \u2014 Ratio: &e{ratio}% &7({score} pts / {total} blocks)";
            case "critical" -> "{prefix} &4&l\u26a0 CRITICAL: {player} &7has extreme ratio! &4Ratio: &f{ratio}% &7({score} pts / {total} blocks)";
            default         -> getAlertMessage();
        };
        return config.getString("alerts.tiers." + tier.toLowerCase() + ".message", def);
    }

    /** Returns the permission required to receive alerts for the given tier. */
    public String getTierPermission(String tier) {
        return config.getString("alerts.tiers." + tier.toLowerCase() + ".permission",
                getStaffPermission());
    }

    /** Returns the Bukkit {@link org.bukkit.Sound} name for the given tier, or empty string if none. */
    public String getTierSound(String tier) {
        String def = switch (tier.toLowerCase()) {
            case "warning"  -> "BLOCK_NOTE_BLOCK_BELL";
            case "critical" -> "ENTITY_WITHER_SPAWN";
            default         -> "BLOCK_NOTE_BLOCK_PLING";
        };
        return config.getString("alerts.tiers." + tier.toLowerCase() + ".sound", def);
    }

    /** Returns the volume for the given tier's sound effect. */
    public float getTierVolume(String tier) {
        double def = "warning".equalsIgnoreCase(tier) ? 0.5 : 1.0;
        return (float) config.getDouble("alerts.tiers." + tier.toLowerCase() + ".volume", def);
    }

    /** Returns the pitch for the given tier's sound effect. */
    public float getTierPitch(String tier) {
        return (float) config.getDouble("alerts.tiers." + tier.toLowerCase() + ".pitch", 1.0);
    }

    /**
     * D2: Resolves the highest alert tier whose threshold the {@code ratio} meets.
     * Returns {@code null} when the ratio is below all tier thresholds (no alert).
     *
     * @param ratio the style-adjusted detection ratio
     * @return {@link AlertTier#CRITICAL}, {@link AlertTier#ALERT}, {@link AlertTier#WARNING},
     *         or {@code null}
     */
    public AlertTier resolveTier(double ratio) {
        if (ratio >= getTierThreshold("critical")) return AlertTier.CRITICAL;
        if (ratio >= getTierThreshold("alert"))    return AlertTier.ALERT;
        if (ratio >= getTierThreshold("warning"))  return AlertTier.WARNING;
        return null;
    }

    // =========================================================================
    // D3 — Clickable chat components
    // =========================================================================

    /** Whether to send Adventure component messages with clickable [TP] buttons. */
    public boolean isClickableComponentsEnabled() {
        return config.getBoolean("alerts.clickable-components.enabled", true);
    }
    /** The command run when the [TP] button is clicked; {@code {player}} is replaced. */
    public String getClickableTpCommand() {
        return config.getString("alerts.clickable-components.tp-command", "/tp {player}");
    }
    /** Hover text for the [TP] button; {@code {player}} is replaced. */
    public String getClickableTpHoverText() {
        return config.getString("alerts.clickable-components.tp-hover-text",
                "&7Click to teleport to &f{player}");
    }
    /** Hover text for the player-name portion of the alert. */
    public String getClickableCheckHoverText() {
        return config.getString("alerts.clickable-components.check-hover-text",
                "&7Click for detailed stats");
    }

    // =========================================================================
    // D4 — Webhooks
    // =========================================================================

    /** Whether webhook notifications are enabled. */
    public boolean      isWebhooksEnabled()    { return config.getBoolean("alerts.webhooks.enabled", false); }
    /** Tier names (lowercase) that should trigger webhooks. */
    public List<String> getWebhookTiers()      { return config.getStringList("alerts.webhooks.tiers"); }
    /** Discord webhook URL; empty string = disabled. */
    public String       getWebhookDiscordUrl() { return config.getString("alerts.webhooks.discord-url", ""); }
    /** Slack webhook URL; empty string = disabled. */
    public String       getWebhookSlackUrl()   { return config.getString("alerts.webhooks.slack-url", ""); }

    // =========================================================================
    // D5 — Alert modes (sound, title, boss-bar)
    // =========================================================================

    /** Whether to send a chat message for each alert. */
    public boolean isAlertModeChat()    { return config.getBoolean("alerts.alert-modes.chat",     true);  }
    /** Whether to play a sound for each alert. */
    public boolean isAlertModeSound()   { return config.getBoolean("alerts.alert-modes.sound",    true);  }
    /** Whether to show a title/subtitle for each alert. */
    public boolean isAlertModeTitle()   { return config.getBoolean("alerts.alert-modes.title",    false); }
    /** Whether to show a boss bar for each alert. */
    public boolean isAlertModeBossBar() { return config.getBoolean("alerts.alert-modes.boss-bar", false); }

    /** Title fade-in time in ticks. */
    public int    getAlertTitleFadeIn()   { return config.getInt("alerts.alert-modes.title-fade-in-ticks",  10); }
    /** Title stay time in ticks. */
    public int    getAlertTitleStay()     { return config.getInt("alerts.alert-modes.title-stay-ticks",     70); }
    /** Title fade-out time in ticks. */
    public int    getAlertTitleFadeOut()  { return config.getInt("alerts.alert-modes.title-fade-out-ticks", 20); }

    /** Boss-bar color name (e.g. {@code "RED"}). Must be a valid {@link org.bukkit.boss.BarColor}. */
    public String getAlertBossBarColor()   { return config.getString("alerts.alert-modes.boss-bar-color",   "RED"); }
    /** Seconds to display the boss bar before auto-removal. */
    public int    getAlertBossBarSeconds() { return config.getInt("alerts.alert-modes.boss-bar-seconds", 5); }

    // =========================================================================
    // D6 — Periodic digest
    // =========================================================================

    /** Whether to schedule periodic digest reports. */
    public boolean isDigestEnabled()        { return config.getBoolean("alerts.digest.enabled",          false); }
    /** Minutes between digest broadcasts. */
    public int     getDigestIntervalMinutes(){ return config.getInt("alerts.digest.interval-minutes",     15);    }
    /** Maximum number of suspects to include in each digest. */
    public int     getDigestTopN()          { return config.getInt("alerts.digest.top-n",                 5);     }
    /** Minimum ratio for a player to appear in the digest. */
    public double  getDigestMinRatio()      { return config.getDouble("alerts.digest.min-ratio",          0.04);  }
    /** Message template for the digest header; supports {@code {prefix}}, {@code {n}}, {@code {entries}}. */
    public String  getDigestMessage()       { return config.getString("alerts.digest.message",
            "{prefix} &7Top {n} suspects:\n{entries}"); }
    /** Format for each digest entry; supports {@code {player}}, {@code {ratio}}, {@code {score}}, {@code {total}}. */
    public String  getDigestEntryFormat()   { return config.getString("alerts.digest.entry-format",
            "  &c{player} &7\u2014 Ratio: &c{ratio}% &7({score} pts / {total} blocks)"); }
}
