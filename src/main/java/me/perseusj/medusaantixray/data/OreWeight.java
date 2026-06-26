package me.perseusj.medusaantixray.data;

/**
 * Holds the hidden-weight and exposed-weight for a single ore type.
 *
 * <p>B3 — per-ore configurable weights. Instances are produced by
 * {@link me.perseusj.medusaantixray.managers.ConfigManager} and are immutable.</p>
 *
 * @param hiddenWeight  weight applied when no face of the ore is adjacent to air
 * @param exposedWeight weight applied when at least one face is adjacent to air
 */
public record OreWeight(double hiddenWeight, double exposedWeight) {}
