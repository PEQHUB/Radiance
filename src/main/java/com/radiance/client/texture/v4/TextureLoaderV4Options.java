package com.radiance.client.texture.v4;

/**
 * Feature flag and options for Texture Loader v4.
 *
 * When enabled, the block atlas bypass uses the v4 pipeline:
 *   - Per-tier texture arrays (no fixed 128x expansion)
 *   - Generation-scoped uploads
 *   - Strict first-frame readiness (no timeout success)
 *   - Async GPU upload service
 */
public final class TextureLoaderV4Options {

    private TextureLoaderV4Options() {}

    /** True if texture_loader_v4 is the active upload mode. */
    public static boolean enabled() {
        return true;
    }

    /** Check if the v4 native bridge is loaded. */
    public static boolean nativeV4Available() {
        try {
            // Try to call a v4 method — if it throws UnsatisfiedLinkError, native not loaded
            Class<?> bridgeV4 = Class.forName(
                "com.radiance.client.proxy.vulkan.TextureArrayBridgeV4");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    /** Whether to fail closed (no legacy fallback) when v4 fails (default: true). */
    public static boolean failClosed() {
        return true;
    }

    /** Whether disk cache is enabled (default: true). */
    public static boolean diskCacheEnabled() {
        return !Boolean.getBoolean("radser.textureLoader.disableDiskCache");
    }

    /** CTM batch coalescing delay for near-visible materials (ms). */
    public static long nearVisibleCoalesceMs() {
        return Long.getLong("radser.textureLoader.nearVisibleCoalesceMs", 50L);
    }

    /** CTM batch coalescing delay for background materials (ms). */
    public static long backgroundCoalesceMs() {
        return Long.getLong("radser.textureLoader.backgroundCoalesceMs", 200L);
    }

    /** Maximum CTM batch size in materials. */
    public static int maxCtmBatchSize() {
        return Integer.getInteger("radser.textureLoader.maxCtmBatchSize", 256);
    }

    /** Maximum CTM batch size in MiB. */
    public static int maxCtmBatchMiB() {
        return Integer.getInteger("radser.textureLoader.maxCtmBatchMiB", 64);
    }
}
