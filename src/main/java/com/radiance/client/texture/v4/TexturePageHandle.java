package com.radiance.client.texture.v4;

/**
 * Handle to a texture page allocation within the v4 tiered page pool.
 *
 * Encodes namespace, tier, page, layer, mip count, and flags.
 * This is the Java-side mirror of the native TexturePagePool::PageHandle.
 */
public record TexturePageHandle(
    int namespace,
    int tierIndex,
    int page,
    int layer,
    int mipCount,
    int flags
) {
    /** Fallback namespace — used for error/missing textures. */
    public static final int NS_FALLBACK = 0;
    /** Vanilla namespace — block atlas sprites. */
    public static final int NS_VANILLA = 1;
    /** CTM namespace — connected texture materials. */
    public static final int NS_CTM = 2;
    /** Dynamic namespace — runtime-generated textures. */
    public static final int NS_DYNAMIC = 3;

    /** True if all fields are within valid ranges. */
    public boolean valid() {
        return namespace >= 0 && namespace <= NS_DYNAMIC
            && tierIndex >= 0 && tierIndex < 7
            && page >= 0
            && layer >= 0
            && mipCount > 0;
    }
}
