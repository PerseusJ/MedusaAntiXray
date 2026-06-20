package me.perseusj.medusaantixray.listeners;

import me.perseusj.medusaantixray.data.MineEvent;
import me.perseusj.medusaantixray.data.PlayerData;
import me.perseusj.medusaantixray.managers.AlertManager;
import me.perseusj.medusaantixray.managers.ConfigManager;
import me.perseusj.medusaantixray.managers.DataManager;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

public class BlockBreakListener implements Listener {

    private enum WorldType {
        OVERWORLD, NETHER, IGNORED
    }

    private static final BlockFace[] FACES = {
            BlockFace.UP, BlockFace.DOWN, BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST
    };

    private final JavaPlugin plugin;
    private final ConfigManager config;
    private final DataManager dataManager;
    private final AlertManager alertManager;

    public BlockBreakListener(JavaPlugin plugin, ConfigManager config, DataManager dataManager, AlertManager alertManager) {
        this.plugin = plugin;
        this.config = config;
        this.dataManager = dataManager;
        this.alertManager = alertManager;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();

        if (player.getGameMode() != GameMode.SURVIVAL) return;
        if (player.hasPermission("medusa.bypass")) return;

        WorldType worldType = classifyWorld(player.getWorld().getName());
        if (worldType == WorldType.IGNORED) return;

        Block block = event.getBlock();
        String materialName = block.getType().name();

        boolean isValuable = false;
        boolean isFiller = false;

        if (worldType == WorldType.OVERWORLD) {
            isValuable = config.getOverworldOres().contains(materialName);
            isFiller = config.getOverworldFillers().contains(materialName);
        } else {
            isValuable = config.getNetherOres().contains(materialName);
            isFiller = config.getNetherFillers().contains(materialName);
        }

        if (!isValuable && !isFiller) return;

        Block finalBlock = block;
        boolean finalIsValuable = isValuable;

        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            double weight = 0;
            if (finalIsValuable) {
                boolean exposed = isBlockExposed(finalBlock);
                weight = exposed ? config.getExposedOreWeight() : config.getHiddenOreWeight();
            }

            PlayerData data = dataManager.getEntry(player.getUniqueId());
            if (data == null) {
                dataManager.createEntry(player.getUniqueId(), player.getName());
                data = dataManager.getEntry(player.getUniqueId());
            }

            long now = System.currentTimeMillis();
            long cutoff = now - (config.getWindowMinutes() * 60_000L);

            data.purgeExpired(cutoff);
            data.addEvent(new MineEvent(now, finalIsValuable, weight));

            if (data.getTotalBlocks() < config.getMinSampleSize()) return;

            long cooldownMs = config.getCooldownSeconds() * 1000L;
            double ratio = data.calculateRatio();
            if (ratio < config.getAlertThreshold()) return;
            if (!data.shouldAlert(now, cooldownMs)) return;

            double score = data.calculateScore();
            int total = data.getTotalBlocks();
            String playerName = player.getName();

            plugin.getServer().getScheduler().runTask(plugin, () ->
                    alertManager.dispatch(playerName, ratio, score, total));
        });
    }

    private WorldType classifyWorld(String worldName) {
        if (config.isOverworldEnabled() && config.getOverworldNames().contains(worldName)) {
            return WorldType.OVERWORLD;
        }
        if (config.isNetherEnabled() && config.getNetherNames().contains(worldName)) {
            return WorldType.NETHER;
        }
        return WorldType.IGNORED;
    }

    private boolean isBlockExposed(Block block) {
        for (BlockFace face : FACES) {
            Material type = block.getRelative(face).getType();
            if (type == Material.AIR || type == Material.CAVE_AIR || type == Material.VOID_AIR) {
                return true;
            }
        }
        return false;
    }
}