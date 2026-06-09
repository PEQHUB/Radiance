package com.radiance.client.texture.cache;

import java.nio.file.Path;
import java.util.Objects;

/**
 * Content-addressed cache key for texture loader v4.
 *
 * Cache keys must include all inputs that affect the output:
 * minecraft version, radiance/mcvr commits, ABI version, cache schema,
 * pack stack hash, resource path, pack identity (zip CRC or mtime+size),
 * transform version, and loader options.
 */
public record TextureCacheKey(
    String minecraftVersion,
    String radianceCommit,
    String mcvrCommit,
    int textureLoaderAbiVersion,
    int cacheSchemaVersion,
    String packStackHash,
    String resourcePath,
    String packIdentity,
    int transformVersion,
    String loaderOptionsHash,
    String sidecarPresenceHash,
    String mipPolicy,
    String formatPolicy
) {
    /** Build the string key used for file lookup. */
    public String toKeyString() {
        return minecraftVersion + "|" + radianceCommit + "|" + mcvrCommit
            + "|abi" + textureLoaderAbiVersion + "|cs" + cacheSchemaVersion
            + "|" + packStackHash + "|" + resourcePath + "|" + packIdentity
            + "|tv" + transformVersion + "|" + loaderOptionsHash
            + "|sidecars=" + sidecarPresenceHash
            + "|mip=" + mipPolicy
            + "|fmt=" + formatPolicy;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TextureCacheKey that)) return false;
        return toKeyString().equals(that.toKeyString());
    }

    @Override
    public int hashCode() {
        return Objects.hash(toKeyString());
    }
}
