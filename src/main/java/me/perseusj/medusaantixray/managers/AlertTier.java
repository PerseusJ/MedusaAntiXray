package me.perseusj.medusaantixray.managers;

/**
 * D2 — Alert escalation tier.
 *
 * <p>Tiers are ordered by severity: {@code WARNING < ALERT < CRITICAL}.
 * The tier is resolved in {@link ConfigManager#resolveTier(double)} and
 * passed through the dispatch pipeline so that each tier can use its own
 * message template, permission node, sound, and visual effect.
 */
public enum AlertTier {
    WARNING, ALERT, CRITICAL
}
