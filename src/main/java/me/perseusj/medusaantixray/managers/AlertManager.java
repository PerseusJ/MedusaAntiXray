package me.perseusj.medusaantixray.managers;

import me.perseusj.medusaantixray.data.PlayerData;
import me.perseusj.medusaantixray.notifications.WebhookManager;
import me.perseusj.medusaantixray.utils.Utils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Manages all outbound alert delivery for Medusa-Anti-Xray.
 *
 * <p>Responsibilities (all configurable):
 * <ul>
 *   <li><b>D1</b> — Persist each dispatched alert to the {@code medusa_alerts} DB table.</li>
 *   <li><b>D2</b> — Tier-aware dispatch: WARNING, ALERT, CRITICAL with distinct messages, sounds, permissions.</li>
 *   <li><b>D3</b> — Clickable Adventure text components with {@code [TP]} button.</li>
 *   <li><b>D4</b> — Discord/Slack webhook notifications.</li>
 *   <li><b>D5</b> — Sound effects, title/subtitle overlays, and boss-bar alerts.</li>
 *   <li><b>D6</b> — Periodic digest reports listing top suspects.</li>
 * </ul>
 *
 * <p>{@link #dispatch} is always called on the <b>main server thread</b> (via a {@code runTask}
 * wrapper in {@code BlockBreakListener}), so Bukkit API calls are safe inside it.
 */
public class AlertManager {

    private final ConfigManager config;
    private final DatabaseManager database;
    private final JavaPlugin plugin;
    private final WebhookManager webhookManager;

    /**
     * @param config   configuration accessor
     * @param database may be {@code null} when the DB is unavailable; D1 persistence is skipped
     * @param plugin   plugin instance used for boss-bar removal scheduling
     */
    public AlertManager(ConfigManager config, DatabaseManager database, JavaPlugin plugin) {
        this.config         = config;
        this.database       = database;
        this.plugin         = plugin;
        this.webhookManager = new WebhookManager(plugin.getLogger());
    }

    // =========================================================================
    // D2 — Tier-aware dispatch (called on main thread)
    // =========================================================================

    /**
     * Dispatches an alert to all online staff and triggers all configured
     * notification channels (chat, sound, title, boss-bar, webhook, DB persistence).
     *
     * @param playerUuid  the flagged player's UUID (for DB insert)
     * @param playerName  the flagged player's name
     * @param ratio       the trust-adjusted detection ratio
     * @param score       the trust-adjusted suspicion score
     * @param totalBlocks total blocks mined in the detection window
     * @param worldName   the world where the detection occurred
     * @param tier        the resolved alert tier
     */
    public void dispatch(UUID playerUuid, String playerName, double ratio, double score,
                         int totalBlocks, String worldName, AlertTier tier) {
        String tierKey   = tier.name().toLowerCase();
        String permission = config.getTierPermission(tierKey);

        // Build formatted strings used by multiple channels.
        String prefix    = config.getPrefix();
        String ratioStr  = String.format("%.1f", ratio * 100);
        String scoreStr  = String.format("%.2f", score);
        String rawMsg    = config.getTierMessage(tierKey)
                .replace("{prefix}",  prefix)
                .replace("{player}",  playerName)
                .replace("{ratio}",   ratioStr)
                .replace("{score}",   scoreStr)
                .replace("{total}",   String.valueOf(totalBlocks));

        // D5: Resolve sound (null = config value invalid or blank).
        Sound sound = null;
        if (config.isAlertModeSound()) {
            sound = parseSound(config.getTierSound(tierKey));
        }

        float soundVolume = config.getTierVolume(tierKey);
        float soundPitch  = config.getTierPitch(tierKey);

        // Collect eligible recipients.
        List<Player> recipients = new ArrayList<>();
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.hasPermission(permission)) {
                recipients.add(online);
            }
        }

        // D3: Build clickable component once (reused per recipient).
        Component chatComponent = null;
        if (config.isAlertModeChat() && config.isClickableComponentsEnabled()) {
            chatComponent = buildClickableComponent(rawMsg, playerName);
        }
        String plainMessage = config.isAlertModeChat() ? Utils.colorize(rawMsg) : null;

        for (Player staff : recipients) {
            // D5 / D3: Chat message.
            if (config.isAlertModeChat()) {
                if (chatComponent != null) {
                    staff.sendMessage(chatComponent);
                } else {
                    staff.sendMessage(plainMessage);
                }
            }

            // D5: Sound effect.
            if (sound != null) {
                staff.playSound(staff.getLocation(), sound, soundVolume, soundPitch);
            }

            // D5: Title / subtitle overlay.
            if (config.isAlertModeTitle()) {
                staff.showTitle(buildTitle(playerName, ratioStr, tier));
            }
        }

        // D5: Boss bar (single instance shared across all recipients).
        if (config.isAlertModeBossBar() && !recipients.isEmpty()) {
            showBossBar(recipients, playerName, ratioStr);
        }

        // D1: Persist alert to the database.
        if (config.isPersistHistory() && database != null && database.isAvailable()) {
            database.insertAlertAsync(playerUuid, playerName, tier.name(),
                    ratio, score, totalBlocks, worldName);
        }

        // D4: Webhooks.
        if (config.isWebhooksEnabled()) {
            List<String> webhookTiers = config.getWebhookTiers();
            if (webhookTiers.contains(tierKey)) {
                long ts = System.currentTimeMillis();
                String discordUrl = config.getWebhookDiscordUrl();
                String slackUrl   = config.getWebhookSlackUrl();
                if (!discordUrl.isBlank()) {
                    webhookManager.postAsync(discordUrl,
                            webhookManager.formatDiscordPayload(playerName, tier.name(),
                                    ratio, score, totalBlocks, ts));
                }
                if (!slackUrl.isBlank()) {
                    webhookManager.postAsync(slackUrl,
                            webhookManager.formatSlackPayload(playerName, tier.name(),
                                    ratio, score, totalBlocks));
                }
            }
        }
    }

    // =========================================================================
    // D6 — Periodic digest
    // =========================================================================

    /**
     * Builds and broadcasts a digest of the top-N suspects by ratio.
     * Safe to call from an async context — the Bukkit send is dispatched on the main thread.
     *
     * @param allPlayers live snapshot of all tracked players
     */
    public void dispatchDigest(Collection<PlayerData> allPlayers) {
        if (!config.isDigestEnabled()) return;

        double minRatio = config.getDigestMinRatio();
        int    topN     = config.getDigestTopN();

        List<PlayerData> suspects = allPlayers.stream()
                .filter(p -> p.calculateRatio() >= minRatio && p.getTotalBlocks() > 0)
                .sorted(Comparator.comparingDouble(PlayerData::calculateRatio).reversed())
                .limit(topN)
                .toList();

        if (suspects.isEmpty()) return;

        String entryFormat = config.getDigestEntryFormat();
        StringBuilder entries = new StringBuilder();
        for (PlayerData suspect : suspects) {
            String entry = entryFormat
                    .replace("{player}", suspect.getPlayerName())
                    .replace("{ratio}",  String.format("%.1f", suspect.calculateRatio() * 100))
                    .replace("{score}",  String.format("%.2f", suspect.calculateScore()))
                    .replace("{total}",  String.valueOf(suspect.getTotalBlocks()));
            entries.append(entry).append("\n");
        }

        String header = config.getDigestMessage()
                .replace("{prefix}",  config.getPrefix())
                .replace("{n}",       String.valueOf(suspects.size()))
                .replace("{entries}", entries.toString().stripTrailing());

        String colored    = Utils.colorize(header);
        String permission = config.getStaffPermission();

        // Must send on main thread.
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            for (Player staff : Bukkit.getOnlinePlayers()) {
                if (staff.hasPermission(permission)) {
                    staff.sendMessage(colored);
                }
            }
        });
    }

    // =========================================================================
    // D3 — Clickable Adventure components
    // =========================================================================

    /**
     * Wraps the legacy-colour alert message in an Adventure {@link Component} and
     * appends a clickable {@code [TP]} button.
     */
    private Component buildClickableComponent(String rawMessage, String playerName) {
        LegacyComponentSerializer legacy = LegacyComponentSerializer.legacyAmpersand();

        Component base = legacy.deserialize(rawMessage);

        String tpCmd = config.getClickableTpCommand().replace("{player}", playerName);
        String tpHover = config.getClickableTpHoverText().replace("{player}", playerName);

        Component tpButton = Component.text(" [TP]")
                .color(TextColor.color(0x55FF55))   // bright green
                .clickEvent(ClickEvent.runCommand(tpCmd))
                .hoverEvent(HoverEvent.showText(legacy.deserialize(tpHover)));

        return Component.empty().append(base).append(tpButton);
    }

    // =========================================================================
    // D5 — Title & boss-bar helpers
    // =========================================================================

    private Title buildTitle(String playerName, String ratioStr, AlertTier tier) {
        String color = switch (tier) {
            case CRITICAL -> "&4";
            case WARNING  -> "&e";
            default       -> "&c";
        };
        Component subtitle = LegacyComponentSerializer.legacyAmpersand()
                .deserialize(color + playerName + " &7— Ratio: " + color + ratioStr + "%");

        return Title.title(
                Component.empty(),
                subtitle,
                Title.Times.times(
                        Duration.ofMillis(config.getAlertTitleFadeIn()  * 50L),
                        Duration.ofMillis(config.getAlertTitleStay()    * 50L),
                        Duration.ofMillis(config.getAlertTitleFadeOut() * 50L)));
    }

    private void showBossBar(List<Player> recipients, String playerName, String ratioStr) {
        BarColor barColor = BarColor.RED;
        try {
            barColor = BarColor.valueOf(config.getAlertBossBarColor().toUpperCase());
        } catch (IllegalArgumentException ignored) {}

        BossBar bossBar = Bukkit.createBossBar(
                Utils.colorize("&c" + playerName + " &7\u2014 Ratio: &c" + ratioStr + "%"),
                barColor, BarStyle.SOLID);
        bossBar.setProgress(1.0);

        for (Player staff : recipients) {
            bossBar.addPlayer(staff);
        }

        int seconds = Math.max(1, config.getAlertBossBarSeconds());
        plugin.getServer().getScheduler().runTaskLater(plugin, bossBar::removeAll, seconds * 20L);
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    /**
     * Parses a Bukkit {@link Sound} by name; returns {@code null} and logs nothing
     * on failure (config validation already warns about bad values).
     */
    private static Sound parseSound(String name) {
        if (name == null || name.isBlank()) return null;
        try {
            return Sound.valueOf(name.toUpperCase());
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}
