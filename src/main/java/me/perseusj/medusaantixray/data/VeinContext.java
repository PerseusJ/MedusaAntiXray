package me.perseusj.medusaantixray.data;

/**
 * Tracks the active ore-vein context for a single player.
 *
 * <p>B2 — vein-awareness grouping. A vein is defined as a run of the same
 * {@link org.bukkit.Material} mined within a configurable Chebyshev distance and
 * time window. All access must occur on the <strong>main server thread</strong>;
 * a plain (non-concurrent) map is therefore sufficient for the owning collection
 * in {@code BlockBreakListener}.</p>
 */
public final class VeinContext {

    private final String materialName;
    private int lastX;
    private int lastY;
    private int lastZ;
    private long lastTimestampMs;
    private int veinSize;

    /**
     * Creates a new vein context for the first block of a potential vein.
     *
     * @param materialName  the {@link org.bukkit.Material#name()} of the first ore block
     * @param x             block X coordinate
     * @param y             block Y coordinate
     * @param z             block Z coordinate
     * @param timestampMs   wall-clock time at which the first block was broken
     */
    public VeinContext(String materialName, int x, int y, int z, long timestampMs) {
        this.materialName   = materialName;
        this.lastX          = x;
        this.lastY          = y;
        this.lastZ          = z;
        this.lastTimestampMs = timestampMs;
        this.veinSize       = 1;
    }

    /** The {@link org.bukkit.Material#name()} of this vein's ore type. */
    public String getMaterialName() { return materialName; }

    /** Current number of blocks that have been grouped into this vein. */
    public int getVeinSize() { return veinSize; }

    /** Wall-clock time (ms) at which the most recent vein block was broken. */
    public long getLastTimestampMs() { return lastTimestampMs; }

    /**
     * Chebyshev (L∞) distance from the last recorded vein block to the given coordinates.
     * Used to decide whether a new ore break is close enough to continue this vein.
     */
    public int chebyshevDistance(int x, int y, int z) {
        return Math.max(Math.abs(x - lastX),
               Math.max(Math.abs(y - lastY),
                        Math.abs(z - lastZ)));
    }

    /**
     * Extends the vein by one block, updating the tracked position and timestamp.
     * Call this after confirming the new block qualifies as part of this vein.
     */
    public void extend(int x, int y, int z, long timestampMs) {
        this.lastX           = x;
        this.lastY           = y;
        this.lastZ           = z;
        this.lastTimestampMs = timestampMs;
        this.veinSize++;
    }
}
