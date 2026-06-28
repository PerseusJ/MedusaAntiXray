package me.perseusj.medusaantixray.listeners;

import me.perseusj.medusaantixray.data.MineEvent;
import me.perseusj.medusaantixray.data.OreWeight;
import me.perseusj.medusaantixray.data.PlayerData;
import me.perseusj.medusaantixray.data.VeinContext;
import me.perseusj.medusaantixray.managers.AlertManager;
import me.perseusj.medusaantixray.managers.CalibrationManager;
import me.perseusj.medusaantixray.managers.ConfigManager;
import me.perseusj.medusaantixray.managers.DataManager;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class BlockBreakListener implements Listener {

    private enum WorldType {
        OVERWORLD, NETHER, END, IGNORED
    }

    private static final BlockFace[] FACES = {
            BlockFace.UP, BlockFace.DOWN, BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST
    };

    private final JavaPlugin  plugin;
    private final ConfigManager config;
    private final DataManager dataManager;
    private final AlertManager alertManager;
    private final CalibrationManager calibrationManager;

    /**
     * B2: Per-player ore-vein tracking context.
     * Accessed exclusively from the main server thread — a plain HashMap is sufficient.
     */
    private final Map<UUID, VeinContext> veinContexts = new HashMap<>();

    public BlockBreakListener(JavaPlugin plugin, ConfigManager config,
                              DataManager dataManager, AlertManager alertManager,
                              CalibrationManager calibrationManager) {
        this.plugin       = plugin;
        this.config       = config;
        this.dataManager  = dataManager;
        this.alertManager = alertManager;
        this.calibrationManager = calibrationManager;
    }

    // =========================================================================
    // B2: Release vein context when a player disconnects
    // =========================================================================

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        veinContexts.remove(event.getPlayer().getUniqueId());
    }

    // =========================================================================
    // Main detection handler
    // =========================================================================

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();

        if (player.getGameMode() != GameMode.SURVIVAL) return;
        if (player.hasPermission("medusa.bypass")) return;

        WorldType worldType = classifyWorld(player.getWorld().getName());
        if (worldType == WorldType.IGNORED) return;

        Block  block        = event.getBlock();
        String materialName = block.getType().name();

        boolean isValuable = isTrackedOre(worldType, materialName);
        boolean isFiller   = isTrackedFiller(worldType, materialName);
        if (!isValuable && !isFiller) return;

        // ---- Everything below runs synchronously on the main server thread ----

        final int blockX = block.getX();
        final int blockY = block.getY();
        final int blockZ = block.getZ();

        // B3: Resolve per-ore weight (hidden / exposed) for this world and material.
        final OreWeight oreWeight = isValuable
                ? getOreWeightForWorld(worldType, materialName)
                : new OreWeight(0.0, 0.0);

        // B5 / A1: Compute the base suspicion weight synchronously (reads world/chunk state).
        // When B5 is enabled, we use a graduated multi-face score; otherwise the original
        // binary hasExposedFace() check (the A1 fix) is used.
        final double baseWeight;
        if (isValuable) {
            if (config.isExposureScoringEnabled()) {
                // B5: graduated weight using exposed-face count + sky-light penalty.
                baseWeight = getExposureScore(block, oreWeight.hiddenWeight(), oreWeight.exposedWeight());
            } else {
                // Original binary exposure check (A1-safe: runs on main thread).
                boolean exposed = hasExposedFace(block);
                baseWeight = exposed ? oreWeight.exposedWeight() : oreWeight.hiddenWeight();
            }
        } else {
            baseWeight = 0.0;
        }

        // B4: Capture tool/enchantment data from the player's held item.
        // Must happen on the main thread (entity state access).
        final boolean hasSilkTouch;
        final int     fortuneLevel;
        final int     efficiencyLevel;
        final String  toolType;
        if (isValuable) {
            ItemStack held  = player.getInventory().getItemInMainHand();
            hasSilkTouch    = held.containsEnchantment(Enchantment.SILK_TOUCH);
            fortuneLevel    = held.getEnchantmentLevel(Enchantment.LOOT_BONUS_BLOCKS);
            efficiencyLevel = held.getEnchantmentLevel(Enchantment.DIG_SPEED);
            toolType        = held.getType().name();
        } else {
            hasSilkTouch    = false;
            fortuneLevel    = 0;
            efficiencyLevel = 0;
            toolType        = "NONE";
        }

        // B2: Vein grouping — check and update the per-player VeinContext synchronously.
        final int     veinSizeForEvent;
        final boolean skipAsValuable; // true = record this ore as a filler (first-only, non-first block)
        if (isValuable && config.isVeinGroupingEnabled()) {
            UUID uuid        = player.getUniqueId();
            long nowMs       = System.currentTimeMillis();
            long timeoutMs   = config.getVeinTimeoutTicks() * 50L; // ticks → ms

            VeinContext ctx = veinContexts.get(uuid);
            boolean sameVein = ctx != null
                    && ctx.getMaterialName().equals(materialName)
                    && (nowMs - ctx.getLastTimestampMs()) <= timeoutMs
                    && ctx.chebyshevDistance(blockX, blockY, blockZ) <= config.getVeinMaxDistance();

            if (sameVein) {
                ctx.extend(blockX, blockY, blockZ, nowMs);
                veinSizeForEvent = ctx.getVeinSize();
                // "first-only": all blocks after the first become non-valuable (filler-like).
                skipAsValuable = "first-only".equalsIgnoreCase(config.getVeinMode());
            } else {
                // New vein — start a fresh context for this block.
                veinContexts.put(uuid, new VeinContext(materialName, blockX, blockY, blockZ, nowMs));
                veinSizeForEvent = 1;
                skipAsValuable   = false;
            }
        } else {
            veinSizeForEvent = 1;
            skipAsValuable   = false;
        }

        // Collapse "first-only" follow-on ore blocks to non-valuable so they still count
        // in the block denominator without inflating the suspicion score.
        final boolean finalIsValuable  = isValuable && !skipAsValuable;
        final double  finalBaseWeight  = finalIsValuable ? baseWeight : 0.0;

        // Pre-read config flags once to avoid repeated volatile/map reads inside the lambda.
        final boolean b1Enabled  = finalIsValuable && config.isDepthNormalizationEnabled();
        final boolean b4Enabled  = finalIsValuable && config.isToolModifiersEnabled();
        // "divide" mode: apply vein divisor in the async section.
        final boolean veinDivide = finalIsValuable
                && config.isVeinGroupingEnabled()
                && "divide".equalsIgnoreCase(config.getVeinMode())
                && veinSizeForEvent > 1;

        // C1: Teleport cooldown — skip scoring entirely if the player is still in the grace period.
        if (config.isTeleportCooldownEnabled()) {
            PlayerData pd = dataManager.getEntry(player.getUniqueId());
            if (pd != null && pd.isInTeleportCooldown(System.currentTimeMillis(),
                    config.getTeleportCooldownSeconds() * 1000L)) {
                return;
            }
        }

        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {

            // Apply async weight modifiers (purely multiplicative; no world-state reads).
            double weight = finalBaseWeight;
            if (finalIsValuable) {

                // B1: Y-level depth multiplier.
                if (b1Enabled) {
                    weight *= config.getDepthMultiplier(materialName, blockY);
                }

                // B4: Tool/enchantment multiplier.
                if (b4Enabled) {
                    if (hasSilkTouch) {
                        weight *= config.getSilkTouchMultiplier();
                    } else if (fortuneLevel > 0) {
                        weight *= Math.pow(config.getFortuneMultiplierPerLevel(), fortuneLevel);
                    } else {
                        weight *= config.getNoEnchantmentsMultiplier();
                    }
                }

                // B2: Divide mode — spread the vein's total weight across its blocks.
                if (veinDivide) {
                    weight /= veinSizeForEvent;
                }
            }

            // Fetch or lazily create the player's data entry.
            PlayerData data = dataManager.getEntry(player.getUniqueId());
            if (data == null) {
                dataManager.createEntry(player.getUniqueId(), player.getName());
                data = dataManager.getEntry(player.getUniqueId());
            }

            long now    = System.currentTimeMillis();
            long cutoff = now - (config.getWindowMinutes() * 60_000L);

            data.purgeExpired(cutoff);

            // C5: Apply mine-gap multiplier based on time since last ore find.
            double effectiveWeight = weight;
            if (finalIsValuable && config.isMineGapMultiplierEnabled()) {
                long lastOre = data.getLastOreTimestamp();
                long gap = lastOre == 0 ? config.getMineGapMaxMs() : (now - lastOre);
                double gapMult = config.getMineGapMultiplier(gap);
                effectiveWeight *= gapMult;
                data.setLastOreTimestamp(now);
            }

            data.addEvent(new MineEvent(now, finalIsValuable, effectiveWeight,
                    blockY, veinSizeForEvent,
                    hasSilkTouch, fortuneLevel, efficiencyLevel, toolType));

            // C3: Record event in calibration manager (in learning mode, captures all data).
            calibrationManager.recordEvent(player.getUniqueId(), finalIsValuable, effectiveWeight);

            if (data.getTotalBlocks() < config.getMinSampleSize()) return;

            // C2: Classify mining style periodically.
            data.classifyMiningStyle();

            // C2: Adjust effective threshold based on mining style.
            double effectiveThreshold = config.getAlertThreshold();
            if (finalIsValuable && config.isStyleMultipliersEnabled()) {
                double styleMult = config.getStyleMultiplier(data.getMiningStyle().name());
                if (styleMult > 0) {
                    effectiveThreshold /= styleMult;
                }
            }

            long   cooldownMs  = config.getCooldownSeconds() * 1000L;
            double ratio       = data.calculateRatio();
            if (ratio < effectiveThreshold) return;
            if (!data.shouldAlert(now, cooldownMs)) return;

            double rawScore   = data.calculateScore();
            // C4: Apply trust multiplier to the final score.
            double trustMult  = data.getTrustMultiplier();
            double finalScore = trustMult != 1.0 ? rawScore * trustMult : rawScore;
            double finalRatio = data.getTotalBlocks() > 0 ? finalScore / data.getTotalBlocks() : 0;
            int    total      = data.getTotalBlocks();
            String playerName = player.getName();

            // C3: Learning mode — suppress alerts but still track data.
            if (config.isLearningModeEnabled()) {
                return;
            }

            plugin.getServer().getScheduler().runTask(plugin, () ->
                    alertManager.dispatch(playerName, finalRatio, finalScore, total));
        });
    }

    // =========================================================================
    // World classification helpers
    // =========================================================================

    private WorldType classifyWorld(String worldName) {
        if (config.isOverworldEnabled() && config.getOverworldNames().contains(worldName))
            return WorldType.OVERWORLD;
        if (config.isNetherEnabled()    && config.getNetherNames().contains(worldName))
            return WorldType.NETHER;
        if (config.isEndEnabled()       && config.getEndNames().contains(worldName))  // B6
            return WorldType.END;
        return WorldType.IGNORED;
    }

    private boolean isTrackedOre(WorldType worldType, String materialName) {
        List<String> ores = switch (worldType) {
            case OVERWORLD -> config.getOverworldOres();
            case NETHER    -> config.getNetherOres();
            case END       -> config.getEndOres();        // B6
            case IGNORED   -> List.of();
        };
        return ores.contains(materialName);
    }

    private boolean isTrackedFiller(WorldType worldType, String materialName) {
        List<String> fillers = switch (worldType) {
            case OVERWORLD -> config.getOverworldFillers();
            case NETHER    -> config.getNetherFillers();
            case END       -> config.getEndFillers();     // B6
            case IGNORED   -> List.of();
        };
        return fillers.contains(materialName);
    }

    /** B3: Returns the ore weight applicable to the given world and material. */
    private OreWeight getOreWeightForWorld(WorldType worldType, String materialName) {
        return switch (worldType) {
            case OVERWORLD -> config.getOverworldOreWeight(materialName);  // B3
            case NETHER    -> config.getNetherOreWeight(materialName);     // B3
            case END       -> config.getEndOreWeight(materialName);        // B3 + B6
            case IGNORED   -> new OreWeight(config.getHiddenOreWeight(), config.getExposedOreWeight());
        };
    }

    // =========================================================================
    // Exposure scoring helpers
    // =========================================================================

    /**
     * Returns {@code true} if at least one of the six cardinal faces of {@code block}
     * is adjacent to an air block.
     *
     * <p><strong>Must be called on the main server thread.</strong>
     * {@link Block#getRelative(BlockFace)} reads live chunk data which is not
     * thread-safe to access asynchronously (A1 fix).</p>
     */
    private boolean hasExposedFace(Block block) {
        for (BlockFace face : FACES) {
            Material type = block.getRelative(face).getType();
            if (type == Material.AIR || type == Material.CAVE_AIR || type == Material.VOID_AIR) {
                return true;
            }
        }
        return false;
    }

    /**
     * B5: Returns a graduated suspicion weight for {@code block} based on the number of
     * air-adjacent faces and whether any adjacent face is sky-lit.
     *
     * <p>Formula:
     * <pre>
     *   if airFaces &lt;= hiddenThreshold  → hiddenWeight  (fully hidden, maximum suspicion)
     *   else → hiddenWeight + (airFaces / 6.0) × (exposedWeight − hiddenWeight)
     *   if hasSkyLight → multiply by skyLightPenalty
     * </pre>
     *
     * <p><strong>Must be called on the main server thread</strong> — reads world/chunk state.</p>
     *
     * @param block         the ore block being broken
     * @param hiddenWeight  base weight when the ore is fully hidden (B3 per-ore or global)
     * @param exposedWeight base weight when the ore is fully exposed (B3 per-ore or global)
     * @return the interpolated suspicion weight
     */
    private double getExposureScore(Block block, double hiddenWeight, double exposedWeight) {
        int     airFaces    = 0;
        boolean hasSkyLight = false;

        for (BlockFace face : FACES) {
            Block    relative = block.getRelative(face);
            Material type     = relative.getType();
            if (type == Material.AIR || type == Material.CAVE_AIR || type == Material.VOID_AIR) {
                airFaces++;
                if (relative.getLightFromSky() > 0) {
                    hasSkyLight = true;
                }
            }
        }

        int threshold = config.getHiddenThresholdFaces();
        if (airFaces <= threshold) {
            return hiddenWeight; // Fully hidden — maximum suspicion.
        }

        // Interpolate between max (hidden) and min (exposed) suspicion.
        double weight = hiddenWeight + (airFaces / 6.0) * (exposedWeight - hiddenWeight);

        // Sky-lit ores are trivially findable; reduce suspicion further.
        if (hasSkyLight) {
            weight *= config.getSkyLightPenalty();
        }

        return weight;
    }
}