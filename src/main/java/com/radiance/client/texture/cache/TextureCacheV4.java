package com.radiance.client.texture.cache;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Properties;

import com.radiance.client.build.BuildInfo;

import net.minecraft.client.MinecraftClient;

/**
 * Content-addressed v4 texture cache.
 *
 * Cache root: .minecraft/radiance/cache/texture-loader-v4/
 *
 * Writes use temp-file + atomic rename to prevent corrupt entries.
 * Corrupt entries are deleted and treated as misses.
 *
 * Phase 7 requirement: warm cache must be materially faster.
 */
public final class TextureCacheV4 {

    private static final String CACHE_DIR_NAME = "texture-loader-v4";

    private TextureCacheV4() {}

    /** Get the cache root directory. */
    public static Path cacheRoot() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.runDirectory == null) return null;
        return mc.runDirectory.toPath()
            .resolve("radiance").resolve("cache").resolve(CACHE_DIR_NAME);
    }

    /** Check if a cache entry exists. */
    public static boolean exists(TextureCacheKey key) {
        Path file = cachePath(key);
        return file != null && Files.isRegularFile(file);
    }

    /** Read a cache entry. Returns null on miss or corruption. */
    public static byte[] read(TextureCacheKey key) {
        Path file = cachePath(key);
        if (file == null || !Files.isRegularFile(file)) return null;
        try {
            byte[] data = Files.readAllBytes(file);
            if (data.length == 0) {
                // Empty file = corrupt entry
                delete(key);
                return null;
            }
            return data;
        } catch (IOException e) {
            // Corrupt or unreadable — delete and treat as miss
            delete(key);
            return null;
        }
    }

    /**
     * Write a cache entry using temp-file + atomic rename.
     * This prevents partial writes from creating corrupt entries.
     */
    public static void write(TextureCacheKey key, byte[] data) {
        Path file = cachePath(key);
        if (file == null || data == null || data.length == 0) return;
        try {
            Files.createDirectories(file.getParent());
            Path temp = file.resolveSibling(file.getFileName() + ".tmp");
            Files.write(temp, data);
            Files.move(temp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            // Cache write failure is non-fatal
        }
    }

    /** Delete a corrupt cache entry. */
    public static void delete(TextureCacheKey key) {
        Path file = cachePath(key);
        if (file != null) {
            try { Files.deleteIfExists(file); } catch (IOException ignored) {}
        }
    }

    /** Write the cache manifest (metadata about what is cached). */
    public static void writeManifest(TextureCacheManifest manifest) {
        Path root = cacheRoot();
        if (root == null) return;
        try {
            Files.createDirectories(root);
            Path manifestFile = root.resolve("cache-manifest.properties");
            Properties props = new Properties();
            props.setProperty("cacheSchemaVersion", String.valueOf(BuildInfo.CACHE_SCHEMA_VERSION));
            props.setProperty("textureLoaderAbiVersion", String.valueOf(BuildInfo.TEXTURE_LOADER_ABI_VERSION));
            props.setProperty("entryCount", String.valueOf(manifest.entryCount()));
            props.setProperty("totalBytes", String.valueOf(manifest.totalBytes()));
            try (var out = Files.newOutputStream(manifestFile)) {
                props.store(out, "RadSER Texture Loader v4 Cache Manifest");
            }
        } catch (IOException ignored) {}
    }

    private static Path cachePath(TextureCacheKey key) {
        Path root = cacheRoot();
        if (root == null) return null;
        // Use hash of key string to avoid filesystem issues with special characters
        String hash = Integer.toHexString(key.toKeyString().hashCode());
        return root.resolve(hash.substring(0, 2)).resolve(hash);
    }
}
