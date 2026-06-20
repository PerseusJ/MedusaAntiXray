package me.perseusj.medusaantixray;

import org.bukkit.plugin.java.JavaPlugin;

import me.perseusj.medusaantixray.commands.MedusaCommand;
import me.perseusj.medusaantixray.listeners.BlockBreakListener;
import me.perseusj.medusaantixray.listeners.SessionListener;
import me.perseusj.medusaantixray.managers.ConfigManager;
import me.perseusj.medusaantixray.managers.DataManager;
import me.perseusj.medusaantixray.managers.AlertManager;
import me.perseusj.medusaantixray.managers.DatabaseManager;

public class MedusaAntiXray extends JavaPlugin {

    private ConfigManager configManager;
    private DatabaseManager databaseManager;
    private DataManager dataManager;
    private AlertManager alertManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        configManager = new ConfigManager(this);

        databaseManager = new DatabaseManager(this, configManager);
        databaseManager.init();

        dataManager = new DataManager(databaseManager, configManager);
        alertManager = new AlertManager(configManager);

        getServer().getPluginManager().registerEvents(new SessionListener(dataManager), this);
        getServer().getPluginManager().registerEvents(new BlockBreakListener(this, configManager, dataManager, alertManager), this);

        MedusaCommand command = new MedusaCommand(configManager, dataManager);
        getCommand("medusa").setExecutor(command);
        getCommand("medusa").setTabCompleter(command);

        int interval = configManager.getSaveIntervalMinutes();
        if (interval > 0 && databaseManager.isAvailable()) {
            long ticks = interval * 60L * 20L;
            getServer().getScheduler().runTaskTimerAsynchronously(this, () -> dataManager.saveAll(), ticks, ticks);
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