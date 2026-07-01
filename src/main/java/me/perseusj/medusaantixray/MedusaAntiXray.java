package me.perseusj.medusaantixray;

import org.bukkit.plugin.java.JavaPlugin;

import me.perseusj.medusaantixray.commands.MedusaCommand;
import me.perseusj.medusaantixray.listeners.BlockBreakListener;
import me.perseusj.medusaantixray.listeners.SessionListener;
import me.perseusj.medusaantixray.managers.CalibrationManager;
import me.perseusj.medusaantixray.managers.ConfigManager;
import me.perseusj.medusaantixray.managers.DataManager;
import me.perseusj.medusaantixray.managers.AlertManager;
import me.perseusj.medusaantixray.managers.DatabaseManager;
import me.perseusj.medusaantixray.managers.WatchManager;
import me.perseusj.medusaantixray.ui.GuiListener;
import me.perseusj.medusaantixray.ui.MedusaGui;

public class MedusaAntiXray extends JavaPlugin {

    private ConfigManager configManager;
    private DatabaseManager databaseManager;
    private DataManager dataManager;
    private AlertManager alertManager;
    private CalibrationManager calibrationManager;
    private WatchManager watchManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        configManager = new ConfigManager(this);

        // A4: Validate config on startup and log warnings for any bad values.
        configManager.validate(getLogger());

        databaseManager = new DatabaseManager(this, configManager);
        databaseManager.init();

        dataManager = new DataManager(databaseManager, configManager);
        alertManager = new AlertManager(configManager, databaseManager, this);
        calibrationManager = new CalibrationManager(configManager, getLogger(), databaseManager);

        getServer().getPluginManager().registerEvents(new SessionListener(dataManager, configManager), this);
        watchManager = new WatchManager();

        getServer().getPluginManager().registerEvents(new BlockBreakListener(this, configManager, dataManager, alertManager, calibrationManager, watchManager), this);

        MedusaGui gui = new MedusaGui(configManager, dataManager);
        getServer().getPluginManager().registerEvents(new GuiListener(gui, dataManager), this);

        MedusaCommand command = new MedusaCommand(configManager, dataManager, watchManager, gui);
        getCommand("medusa").setExecutor(command);
        getCommand("medusa").setTabCompleter(command);

        // Autosave scheduler
        int interval = configManager.getSaveIntervalMinutes();
        if (interval > 0 && databaseManager.isAvailable()) {
            long ticks = interval * 60L * 20L;
            getServer().getScheduler().runTaskTimerAsynchronously(this, () -> dataManager.saveAll(), ticks, ticks);
        }

        // A3 + D1: Schedule combined daily data-retention purge (events + alerts).
        long purgeIntervalTicks = 20L * 60 * 60 * 24; // 24 hours in ticks
        getServer().getScheduler().runTaskTimerAsynchronously(this, () -> {
            int evtDays = configManager.getRetentionDays();
            if (evtDays > 0) {
                long cutoffMs = System.currentTimeMillis() - ((long) evtDays * 24 * 60 * 60 * 1000L);
                databaseManager.purgeExpiredGlobal(cutoffMs);
            }
            int alertDays = configManager.getHistoryRetentionDays();
            if (alertDays > 0) {
                long alertCutoffMs = System.currentTimeMillis() - ((long) alertDays * 24 * 60 * 60 * 1000L);
                databaseManager.purgeAlertsOlderThan(alertCutoffMs);
            }
        }, purgeIntervalTicks, purgeIntervalTicks);

        // A5: Schedule periodic DB reconnection attempts when the database is unavailable.
        int retryIntervalSeconds = configManager.getRetryIntervalSeconds();
        if (retryIntervalSeconds > 0) {
            long retryTicks = retryIntervalSeconds * 20L;
            getServer().getScheduler().runTaskTimerAsynchronously(this, () -> {
                if (!databaseManager.isAvailable()) {
                    getLogger().info("[Medusa] Attempting database reconnect...");
                    databaseManager.retryConnect();
                }
            }, retryTicks, retryTicks);
        }

        // A5: Log memory-only mode warning if the database never initialised.
        if (!databaseManager.isAvailable()) {
            getLogger().warning("[Medusa] Database unavailable — running in memory-only mode."
                    + (retryIntervalSeconds > 0
                        ? " Will retry in " + retryIntervalSeconds + "s."
                        : " Retries disabled (retry-interval-seconds=0)."));
        }

        // C3: Start learning mode if enabled; schedule periodic tick.
        if (configManager.isLearningModeEnabled()) {
            calibrationManager.start();
            long tickInterval = 20L * 60; // Check every minute
            getServer().getScheduler().runTaskTimerAsynchronously(this,
                    () -> calibrationManager.tick(), tickInterval, tickInterval);
        }

        // D6: Schedule periodic digest report if enabled.
        if (configManager.isDigestEnabled()) {
            long digestTicks = (long) configManager.getDigestIntervalMinutes() * 60L * 20L;
            getServer().getScheduler().runTaskTimerAsynchronously(this,
                    () -> alertManager.dispatchDigest(dataManager.getAllEntries()),
                    digestTicks, digestTicks);
        }

        getLogger().info("Medusa-Anti-Xray has been enabled!");
    }

    @Override
    public void onDisable() {
        if (dataManager != null) {
            dataManager.shutdown();
        }
        getLogger().info("Medusa-Anti-Xray has been disabled!");
    }
}