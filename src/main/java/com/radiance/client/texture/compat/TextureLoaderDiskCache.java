package com.radiance.client.texture.compat;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.client.MinecraftClient;

public final class TextureLoaderDiskCache {
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();
    private static final int VERSION = 1;
    private static final AtomicLong READS = new AtomicLong();
    private static final AtomicLong WRITES = new AtomicLong();
    private static final AtomicLong HITS = new AtomicLong();
    private static final AtomicLong MISSES = new AtomicLong();
    private static final AtomicLong FAILURES = new AtomicLong();
    private static volatile String lastKey = "";
    private static volatile String lastPath = "";
    private static volatile String lastFailure = "";

    private TextureLoaderDiskCache() {
    }

    public static JsonObject readRoot(String key) {
        READS.incrementAndGet();
        lastKey = key == null ? "" : key;
        Path path = cachePath(key);
        lastPath = path.toAbsolutePath().toString();
        if (key == null || key.isBlank() || !Files.isRegularFile(path)) {
            MISSES.incrementAndGet();
            return null;
        }
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            JsonObject wrapper = JsonParser.parseReader(reader).getAsJsonObject();
            if (wrapper.get("version").getAsInt() != VERSION
                || !key.equals(wrapper.get("key").getAsString())
                || !wrapper.has("root") || !wrapper.get("root").isJsonObject()) {
                MISSES.incrementAndGet();
                return null;
            }
            HITS.incrementAndGet();
            lastFailure = "";
            return wrapper.getAsJsonObject("root").deepCopy();
        } catch (Exception e) {
            FAILURES.incrementAndGet();
            lastFailure = e.toString();
            return null;
        }
    }

    public static void writeRoot(String key, JsonObject root) {
        if (key == null || key.isBlank() || root == null) {
            return;
        }
        Path path = cachePath(key);
        lastKey = key;
        lastPath = path.toAbsolutePath().toString();
        try {
            Files.createDirectories(path.getParent());
            JsonObject wrapper = new JsonObject();
            wrapper.addProperty("version", VERSION);
            wrapper.addProperty("key", key);
            wrapper.addProperty("createdAtMillis", System.currentTimeMillis());
            wrapper.add("root", root.deepCopy());
            try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
                GSON.toJson(wrapper, writer);
            }
            WRITES.incrementAndGet();
            lastFailure = "";
        } catch (IOException e) {
            FAILURES.incrementAndGet();
            lastFailure = e.toString();
        }
    }

    public static JsonObject statusJson() {
        JsonObject json = new JsonObject();
        json.addProperty("schema", "texture_loader_cache_status_v1");
        json.addProperty("diskCacheEnabled", true);
        json.addProperty("cacheKind", "runtime_ctm_dependency_root");
        json.addProperty("version", VERSION);
        json.addProperty("cacheDirectory", cacheDirectory().toAbsolutePath().toString());
        json.addProperty("lastKey", lastKey);
        json.addProperty("lastPath", lastPath);
        json.addProperty("reads", READS.get());
        json.addProperty("writes", WRITES.get());
        json.addProperty("hits", HITS.get());
        json.addProperty("misses", MISSES.get());
        json.addProperty("failures", FAILURES.get());
        json.addProperty("lastFailure", lastFailure);
        return json;
    }

    public static String keyFor(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest((input == null ? "" : input).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash).substring(0, 32);
        } catch (Exception ignored) {
            return Integer.toHexString((input == null ? "" : input).hashCode()).toLowerCase(Locale.ROOT);
        }
    }

    private static Path cachePath(String key) {
        String safe = key == null ? "unknown" : key.replaceAll("[^a-zA-Z0-9._-]", "_");
        return cacheDirectory().resolve(safe + ".json");
    }

    private static Path cacheDirectory() {
        MinecraftClient client = MinecraftClient.getInstance();
        Path runDirectory = client == null || client.runDirectory == null
            ? Path.of(".")
            : client.runDirectory.toPath();
        return runDirectory.resolve("radiance").resolve("cache").resolve("texture-loader-v3");
    }
}
