package com.radiance.client.texture.cache;

import com.google.gson.JsonObject;
import com.radiance.client.build.BuildInfo;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicLong;
import java.util.zip.CRC32;
import net.minecraft.client.MinecraftClient;

/**
 * Content-addressed v4 texture cache.
 *
 * Cache root: .minecraft/radiance/cache/texture-loader-v4/
 * Writes use temp-file + atomic rename. Corrupt entries are deleted and treated
 * as misses.
 */
public final class TextureCacheV4 {

    private static final String CACHE_DIR_NAME = "texture-loader-v4";
    private static final int MAGIC = 0x52545634; // RTV4
    private static final int HEADER_BYTES = 32;

    private static final AtomicLong PAYLOAD_HITS = new AtomicLong();
    private static final AtomicLong PAYLOAD_MISSES = new AtomicLong();
    private static final AtomicLong CACHE_READ_BYTES = new AtomicLong();
    private static final AtomicLong CACHE_WRITTEN_BYTES = new AtomicLong();
    private static final AtomicLong CACHE_READ_NANOS = new AtomicLong();
    private static final AtomicLong CACHE_WRITE_QUEUED = new AtomicLong();
    private static final AtomicLong CORRUPT_DELETED = new AtomicLong();

    private TextureCacheV4() {}

    public static Path cacheRoot() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.runDirectory == null) return null;
        return mc.runDirectory.toPath()
            .resolve("radiance").resolve("cache").resolve(CACHE_DIR_NAME);
    }

    public static boolean exists(TextureCacheKey key) {
        Path file = cachePath(key);
        return file != null && Files.isRegularFile(file);
    }

    public static byte[] read(TextureCacheKey key) {
        Path file = cachePath(key);
        if (file == null || !Files.isRegularFile(file)) {
            PAYLOAD_MISSES.incrementAndGet();
            return null;
        }
        long started = System.nanoTime();
        try {
            byte[] data = Files.readAllBytes(file);
            CACHE_READ_NANOS.addAndGet(System.nanoTime() - started);
            if (data.length <= HEADER_BYTES) {
                deleteCorrupt(key);
                return null;
            }
            ByteBuffer header = ByteBuffer.wrap(data, 0, HEADER_BYTES).order(ByteOrder.BIG_ENDIAN);
            int magic = header.getInt();
            int schema = header.getInt();
            int abi = header.getInt();
            long expectedCrc = header.getLong();
            int keyHash = header.getInt();
            int payloadLength = header.getInt();
            header.getLong();
            if (magic != MAGIC
                || schema != BuildInfo.CACHE_SCHEMA_VERSION
                || abi != BuildInfo.TEXTURE_LOADER_ABI_VERSION
                || keyHash != key.toKeyString().hashCode()
                || payloadLength != data.length - HEADER_BYTES) {
                deleteCorrupt(key);
                return null;
            }
            CRC32 crc = new CRC32();
            crc.update(data, HEADER_BYTES, payloadLength);
            if (crc.getValue() != expectedCrc) {
                deleteCorrupt(key);
                return null;
            }
            byte[] payload = new byte[payloadLength];
            System.arraycopy(data, HEADER_BYTES, payload, 0, payloadLength);
            PAYLOAD_HITS.incrementAndGet();
            CACHE_READ_BYTES.addAndGet(payloadLength);
            return payload;
        } catch (IOException e) {
            deleteCorrupt(key);
            return null;
        }
    }

    public static void write(TextureCacheKey key, byte[] data) {
        Path file = cachePath(key);
        if (file == null || data == null || data.length == 0) return;
        CACHE_WRITE_QUEUED.incrementAndGet();
        try {
            Files.createDirectories(file.getParent());
            Path temp = file.resolveSibling(file.getFileName() + ".tmp");
            CRC32 crc = new CRC32();
            crc.update(data);
            ByteBuffer encoded = ByteBuffer.allocate(HEADER_BYTES + data.length).order(ByteOrder.BIG_ENDIAN);
            encoded.putInt(MAGIC);
            encoded.putInt(BuildInfo.CACHE_SCHEMA_VERSION);
            encoded.putInt(BuildInfo.TEXTURE_LOADER_ABI_VERSION);
            encoded.putLong(crc.getValue());
            encoded.putInt(key.toKeyString().hashCode());
            encoded.putInt(data.length);
            encoded.putLong(0L);
            encoded.put(data);
            Files.write(temp, encoded.array());
            try {
                Files.move(temp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException atomicMoveFailure) {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
            }
            CACHE_WRITTEN_BYTES.addAndGet(data.length);
        } catch (IOException ignored) {
        }
    }

    public static void delete(TextureCacheKey key) {
        Path file = cachePath(key);
        if (file != null) {
            try { Files.deleteIfExists(file); } catch (IOException ignored) {}
        }
    }

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
            props.setProperty("hitCount", String.valueOf(manifest.hitCount()));
            props.setProperty("missCount", String.valueOf(manifest.missCount()));
            props.setProperty("hitBytes", String.valueOf(manifest.hitBytes()));
            props.setProperty("missBytes", String.valueOf(manifest.missBytes()));
            try (var out = Files.newOutputStream(manifestFile)) {
                props.store(out, "RadSER Texture Loader v4 Cache Manifest");
            }
        } catch (IOException ignored) {
        }
    }

    public static JsonObject statusJson() {
        long hits = PAYLOAD_HITS.get();
        long misses = PAYLOAD_MISSES.get();
        long total = hits + misses;
        JsonObject json = new JsonObject();
        json.addProperty("schema", "radser_texture_cache_v4_status");
        json.addProperty("enabled", cacheRoot() != null);
        json.addProperty("cacheSchemaVersion", BuildInfo.CACHE_SCHEMA_VERSION);
        json.addProperty("textureLoaderAbiVersion", BuildInfo.TEXTURE_LOADER_ABI_VERSION);
        json.addProperty("rootCacheHit", hits > 0);
        json.addProperty("spritePayloadHitRate", total == 0 ? 0.0 : (double) hits / (double) total);
        json.addProperty("ctmPayloadHitRate", total == 0 ? 0.0 : (double) hits / (double) total);
        json.addProperty("decodeMs", 0);
        json.addProperty("transformMs", 0);
        json.addProperty("cacheReadMs", CACHE_READ_NANOS.get() / 1_000_000L);
        json.addProperty("cacheWriteQueued", CACHE_WRITE_QUEUED.get());
        json.addProperty("cacheBytesRead", CACHE_READ_BYTES.get());
        json.addProperty("cacheBytesWritten", CACHE_WRITTEN_BYTES.get());
        json.addProperty("corruptEntriesDeleted", CORRUPT_DELETED.get());
        json.addProperty("payloadHits", hits);
        json.addProperty("payloadMisses", misses);
        return json;
    }

    private static void deleteCorrupt(TextureCacheKey key) {
        delete(key);
        CORRUPT_DELETED.incrementAndGet();
        PAYLOAD_MISSES.incrementAndGet();
    }

    private static Path cachePath(TextureCacheKey key) {
        Path root = cacheRoot();
        if (root == null || key == null) return null;
        String hash = sha256(key.toKeyString());
        return root.resolve(hash.substring(0, 2)).resolve(hash);
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            return Integer.toHexString(value.hashCode());
        }
    }
}
