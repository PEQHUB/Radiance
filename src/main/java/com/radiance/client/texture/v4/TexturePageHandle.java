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

    public static final int PACKED_NAMESPACE_SHIFT = 28;
    public static final int PACKED_TIER_SHIFT = 24;
    public static final int PACKED_NAMESPACE_MASK = 0xF0000000;
    public static final int PACKED_TIER_MASK = 0x0F000000;
    public static final int PACKED_PAGE_MASK = 0x00FFFFFF;
    public static final int PACKED_VANILLA_NAMESPACE = 0x80000000;
    public static final int PACKED_CTM_NAMESPACE = 0x20000000;
    public static final int PACKED_DYNAMIC_NAMESPACE = 0x30000000;

    public static int packPage(int namespaceId, int tierIndex, int nativePage) {
        int namespaceBits = switch (namespaceId) {
            case NS_VANILLA -> PACKED_VANILLA_NAMESPACE;
            case NS_CTM -> PACKED_CTM_NAMESPACE;
            case NS_DYNAMIC -> PACKED_DYNAMIC_NAMESPACE;
            default -> 0;
        };
        return namespaceBits
            | ((Math.max(0, Math.min(15, tierIndex)) << PACKED_TIER_SHIFT) & PACKED_TIER_MASK)
            | (Math.max(0, nativePage) & PACKED_PAGE_MASK);
    }

    public int packedPage() {
        return packPage(namespace, tierIndex, page);
    }

    /** True if all fields are within valid ranges. */
    public boolean valid() {
        return namespace >= 0 && namespace <= NS_DYNAMIC
            && tierIndex >= 0 && tierIndex < 7
            && page >= 0
            && layer >= 0
            && mipCount > 0;
    }
}
