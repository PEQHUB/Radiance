package com.radiance.client.util;

/**
 * Pre-tuned AutoPBR roughness presets for common material types.
 * Each preset defines percentile center/spread and roughness min/max
 * that produce good results for its material category.
 */
public enum MaterialPreset {
    CUSTOM          ("Custom",           50, 80, 30, 95),
    POLISHED_STONE  ("Polished Stone",   20, 60,  5, 25),
    ROUGH_STONE     ("Rough Stone",      60, 80, 40, 85),
    WOOD            ("Wood",             50, 70, 45, 80),
    BRUSHED_METAL   ("Brushed Metal",    40, 50, 15, 45),
    SMOOTH_METAL    ("Smooth Metal",     30, 40,  2, 15),
    ROUGH_METAL     ("Rough Metal",      65, 80, 40, 80),
    FABRIC          ("Fabric",           55, 60, 65, 95),
    GLASS           ("Glass",            20, 30,  0,  8),
    EARTH           ("Earth",            60, 90, 50, 100),
    FOLIAGE         ("Foliage",          50, 70, 55, 85),
    SAND            ("Sand/Gravel",      55, 75, 55, 90);

    private final String displayName;
    public final int center;    // 0-100
    public final int spread;    // 1-100
    public final int roughMin;  // 0-100
    public final int roughMax;  // 0-100

    MaterialPreset(String displayName, int center, int spread, int roughMin, int roughMax) {
        this.displayName = displayName;
        this.center = center;
        this.spread = spread;
        this.roughMin = roughMin;
        this.roughMax = roughMax;
    }

    public String getDisplayName() { return displayName; }

    /** Find the preset that matches current settings, or CUSTOM if none match. */
    public static MaterialPreset fromSettings(int center, int spread, int roughMin, int roughMax) {
        for (MaterialPreset p : values()) {
            if (p == CUSTOM) continue;
            if (p.center == center && p.spread == spread && p.roughMin == roughMin && p.roughMax == roughMax) {
                return p;
            }
        }
        return CUSTOM;
    }
}
