package com.radiance.client.texture.cache;

/**
 * Summary of the v4 texture cache state.
 */
public record TextureCacheManifest(
    int entryCount,
    long totalBytes,
    int hitCount,
    int missCount,
    long hitBytes,
    long missBytes
) {
    public double hitRate() {
        int total = hitCount + missCount;
        return total > 0 ? (double) hitCount / total : 0.0;
    }
}
