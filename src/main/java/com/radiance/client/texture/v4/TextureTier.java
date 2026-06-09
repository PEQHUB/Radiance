package com.radiance.client.texture.v4;

/**
 * Texture tier classification for the v4 tiered texture array system.
 *
 * Each sprite is assigned to the smallest tier that fits its dimensions.
 * No fixed 128x expansion for 16/32/64 sprites — each tier has its own
 * texture array with appropriate layer size.
 *
 * Tier indices match the native side page layout:
 *   page = VANILLA_TIER_FIRST_PAGE + tier.index
 */
public enum TextureTier {

    T16(16, 0),
    T32(32, 1),
    T64(64, 2),
    T128(128, 3),
    T256(256, 4),
    T512(512, 5),
    T1024(1024, 6);

    /** Layer dimension in pixels (width = height = size). */
    public final int size;

    /** Tier index for page mapping: page = VANILLA_TIER_FIRST_PAGE + index. */
    public final int index;

    TextureTier(int size, int index) {
        this.size = size;
        this.index = index;
    }

    /** Select the smallest tier that fits the given dimensions. */
    public static TextureTier forDimensions(int width, int height) {
        int target = Math.max(1, Math.max(width, height));
        for (TextureTier tier : values()) {
            if (target <= tier.size) return tier;
        }
        return T1024;
    }

    /** Look up a tier by its index. */
    public static TextureTier fromIndex(int index) {
        for (TextureTier tier : values()) {
            if (tier.index == index) return tier;
        }
        throw new IllegalArgumentException("unknown texture tier index " + index);
    }

    /** Bytes per layer for RGBA8 format. */
    public int bytesPerLayerRgba8() {
        return size * size * 4;
    }
}
