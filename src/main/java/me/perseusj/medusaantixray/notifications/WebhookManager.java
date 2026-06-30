package me.perseusj.medusaantixray.notifications;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * D4 — Webhook notification manager.
 *
 * <p>Posts JSON payloads to Discord and/or Slack webhook URLs asynchronously
 * using the built-in Java 11+ {@link HttpClient}. No additional dependencies
 * are required. Repeated failures are throttled to one WARNING log per URL
 * per minute to prevent log spam.
 */
public class WebhookManager {

    /** Minimum milliseconds between failure log messages per URL. */
    private static final long THROTTLE_MS = 60_000L;

    private final HttpClient httpClient;
    private final Logger logger;

    /** Epoch-millis of last logged failure, keyed by URL. */
    private final Map<String, Long> lastFailureLogged = new ConcurrentHashMap<>();

    public WebhookManager(Logger logger) {
        this.logger = logger;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    /**
     * Asynchronously POSTs {@code payload} (JSON) to {@code url}.
     * Failures are silently ignored except for a throttled WARNING log.
     *
     * @param url     the full webhook URL; no-op if blank/null
     * @param payload the JSON body to send
     */
    public void postAsync(String url, String payload) {
        if (url == null || url.isBlank()) return;
        HttpRequest request;
        try {
            request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload))
                    .build();
        } catch (IllegalArgumentException e) {
            logger.warning("[Medusa] Invalid webhook URL \"" + url + "\": " + e.getMessage());
            return;
        }
        httpClient.sendAsync(request, HttpResponse.BodyHandlers.discarding())
                .whenComplete((response, ex) -> {
                    if (ex != null) {
                        logThrottled(url, "Webhook POST failed for " + url + ": " + ex.getMessage());
                    } else if (response.statusCode() < 200 || response.statusCode() >= 300) {
                        logThrottled(url, "Webhook POST to " + url
                                + " returned HTTP " + response.statusCode());
                    }
                });
    }

    /**
     * Builds a Discord embed JSON payload for the given detection event.
     *
     * @param playerName  the flagged player's name
     * @param tier        the tier label (e.g. "WARNING", "ALERT", "CRITICAL")
     * @param ratio       detection ratio (0.0–1.0)
     * @param score       suspicion score
     * @param totalBlocks total blocks mined in the detection window
     * @param timestamp   epoch-millis of the alert
     * @return a JSON string suitable for a Discord webhook POST body
     */
    public String formatDiscordPayload(String playerName, String tier,
                                       double ratio, double score,
                                       int totalBlocks, long timestamp) {
        int color = switch (tier.toUpperCase()) {
            case "CRITICAL" -> 0xFF0000; // Red
            case "WARNING"  -> 0xFFFF00; // Yellow
            default         -> 0xFF8800; // Orange
        };
        String iso = Instant.ofEpochMilli(timestamp).toString();
        // Use manual JSON building to avoid requiring a JSON library.
        return "{"
                + "\"embeds\":[{"
                + "\"title\":\"Medusa X-Ray " + escape(tier) + "\","
                + "\"description\":\"Player **" + escape(playerName) + "** triggered a **"
                + escape(tier) + "** alert.\","
                + "\"color\":" + color + ","
                + "\"fields\":["
                + "{\"name\":\"Ratio\",\"value\":\"" + String.format("%.1f%%", ratio * 100) + "\",\"inline\":true},"
                + "{\"name\":\"Score\",\"value\":\"" + String.format("%.2f", score) + "\",\"inline\":true},"
                + "{\"name\":\"Total Blocks\",\"value\":\"" + totalBlocks + "\",\"inline\":true}"
                + "],"
                + "\"timestamp\":\"" + iso + "\""
                + "}]}";
    }

    /**
     * Builds a Slack incoming-webhook JSON payload.
     *
     * @param playerName  the flagged player's name
     * @param tier        the tier label
     * @param ratio       detection ratio (0.0–1.0)
     * @param score       suspicion score
     * @param totalBlocks total blocks mined in the detection window
     * @return a JSON string suitable for a Slack webhook POST body
     */
    public String formatSlackPayload(String playerName, String tier,
                                     double ratio, double score, int totalBlocks) {
        String text = String.format(
                "*Medusa %s*: Player *%s* triggered a %s alert — Ratio: %.1f%%, Score: %.2f, Blocks: %d",
                tier, playerName, tier, ratio * 100, score, totalBlocks);
        return "{\"text\":\"" + escape(text) + "\"}";
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private void logThrottled(String url, String message) {
        long now = System.currentTimeMillis();
        Long last = lastFailureLogged.get(url);
        if (last == null || now - last >= THROTTLE_MS) {
            logger.warning("[Medusa] " + message);
            lastFailureLogged.put(url, now);
        }
    }

    /** Escapes a string for safe inclusion in a JSON string literal. */
    private static String escape(String s) {
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
