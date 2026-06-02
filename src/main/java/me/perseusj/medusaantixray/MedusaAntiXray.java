package me.perseusj.medusaantixray;

import org.bukkit.plugin.java.JavaPlugin;

import me.perseusj.medusaantixray.listeners.BlockBreakListener;
import me.perseusj.medusaantixray.listeners.SessionListener;
import me.perseusj.medusaantixray.managers.ConfigManager;
import me.perseusj.medusaantixray.managers.DataManager;
import me.perseusj.medusaantixray.managers.AlertManager;

public class MedusaAntiXray extends JavaPlugin {
    
    @Override
    public void onEnable() {
        
        // Save default config
        saveDefaultConfig();
        
        // Initialize managers
        ConfigManager.initialize(this);
        DataManager.initialize();
        AlertManager.initialize();
        
        // Register listeners
        getServer().getPluginManager().registerEvents(new SessionListener(), this);
        getServer().getPluginManager().registerEvents(new BlockBreakListener(), this);
        
        // Register command
        getCommand("medusa").setExecutor(new me.perseusj.medusaantixray.commands.MedusaCommand());
        getCommand("medusa").setTabCompleter(new me.perseusj.medusaantixray.commands.MedusaCommand());
        
        getLogger().info("Medusa-Anti-Xray has been enabled!");
    }

    @Override
    public void onDisable() {
        if (DataManager.getInstance() != null) {
            DataManager.getInstance().shutdown();
        }
        getLogger().info("Medusa-Anti-Xray has been disabled!");
    }
    
}