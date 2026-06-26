package me.perseusj.medusaantixray.data;

/**
 * An immutable record representing a single block-break event that is tracked inside the
 * sliding detection window.
 *
 * <p>Fields added in v1.2:</p>
 * <ul>
 *   <li>{@link #y} — Y-level of the ore (B1 depth normalization).</li>
 *   <li>{@link #veinSize} — how many consecutive same-ore blocks formed the vein at break-time
 *       (B2 vein grouping).</li>
 *   <li>{@link #hasSilkTouch}, {@link #fortuneLevel}, {@link #efficiencyLevel},
 *       {@link #toolType} — enchantment data at break-time (B4 tool awareness).</li>
 * </ul>
 *
 * <p>Use {@link #of(long, boolean, double)} when constructing events outside of the detection
 * pipeline (e.g., DatabaseManager loads, unit tests) to get safe zero/false defaults for the
 * new fields.</p>
 */
public record MineEvent(
        long    timestamp,
        boolean isValuable,
        double  weight,
        // B1
        int     y,
        // B2
        int     veinSize,
        // B4
        boolean hasSilkTouch,
        int     fortuneLevel,
        int     efficiencyLevel,
        String  toolType
) {
    /**
     * Backward-compatible factory that fills v1.2 fields with safe defaults.
     * Intended for use in {@code DatabaseManager} (loading persisted events whose DB row
     * carries only {@code timestamp / is_valuable / weight}) and in unit tests.
     *
     * @param timestamp   epoch-millis at which the block was broken
     * @param isValuable  {@code true} if the block is a tracked ore
     * @param weight      the already-computed suspicion weight
     * @return a fully-constructed {@code MineEvent} with default v1.2 field values
     */
    public static MineEvent of(long timestamp, boolean isValuable, double weight) {
        return new MineEvent(timestamp, isValuable, weight,
                /* y */             0,
                /* veinSize */      1,
                /* hasSilkTouch */  false,
                /* fortuneLevel */  0,
                /* effLevel */      0,
                /* toolType */      "UNKNOWN");
    }
}
