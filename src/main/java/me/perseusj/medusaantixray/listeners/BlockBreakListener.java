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

import java.util.List;

public class BlockBreakListener implements Listener {

    private enum WorldType {
        OVERWORLD, NETHER, IGNORED
    }

    private final BlockFace[] FACES = {
            BlockFace.UP, BlockFace.DOWN, BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST
    };

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();

        if (player.getGameMode() != GameMode.SURVIVAL) return;
        if (player.hasPermission("medusa.bypass")) return;

        ConfigManager config = ConfigManager.getInstance();
        WorldType worldType = classifyWorld(player.getWorld().getName(), config);
        
        if (worldType == WorldType.IGNORED) return;

        Block block = event.getBlock();
        String materialName = block.getType().name();

        boolean isValuable = false;
        boolean isFiller = false;

        if (worldType == WorldType.OVERWORLD) {
            isValuable = config.getOverworldOres().contains(materialName);
            isFiller = config.getOverworldFillers().contains(materialName);
        } else if (worldType == WorldType.NETHER) {
            isValuable = config.getNetherOres().contains(materialName);
            isFiller = config.getNetherFillers().contains(materialName);
        }

        if (!isValuable && !isFiller) return;

        double weight = 0;
        if (isValuable) {
            boolean exposed = isBlockExposed(block);
            weight = exposed ? config.getExposedOreWeight() : config.getHiddenOreWeight();
        }

        PlayerData data = DataManager.getInstance().getEntry(player.getUniqueId());
        if (data == null) {
            // Player might have bypassed on join but lost permission, or reloaded
            DataManager.getInstance().createEntry(player.getUniqueId(), player.getName());
            data = DataManager.getInstance().getEntry(player.getUniqueId());
        }

        long now = System.currentTimeMillis();
        long cutoff = now - (config.getWindowMinutes() * 60_000L);

        data.purgeExpired(cutoff);
        data.addEvent(new MineEvent(now, isValuable, weight));

        if (data.getTotalBlocks() < config.getMinSampleSize()) return;

        double ratio = data.calculateRatio();
        if (ratio < config.getAlertThreshold()) return;

        if ((now - data.getLastAlertTimestamp()) < (config.getCooldownSeconds() * 1000L)) return;

        data.setLastAlertTimestamp(now);
        AlertManager.getInstance().dispatch(player, ratio, data.calculateScore(), data.getTotalBlocks());
    }

    private WorldType classifyWorld(String worldName, ConfigManager config) {
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
