package com.radiance.client.texture.compat;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.client.MinecraftClient;

public final class TextureLoaderDiskCache {
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();
    private static final int ROOT_VERSION = 1;
    private static final int LAYER_VERSION = 1;
    private static final String LAYER_MAGIC = "radser-layer-payload";
    private static final AtomicLong READS = new AtomicLong();
    private static final AtomicLong WRITES = new AtomicLong();
    private static final AtomicLong HITS = new AtomicLong();
    private static final AtomicLong MISSES = new AtomicLong();
    private static final AtomicLong FAILURES = new AtomicLong();
    private static final AtomicLong LAYER_READS = new AtomicLong();
    private static final AtomicLong LAYER_WRITES = new AtomicLong();
    private static final AtomicLong LAYER_HITS = new AtomicLong();
    private static final AtomicLong LAYER_MISSES = new AtomicLong();
    private static final AtomicLong LAYER_FAILURES = new AtomicLong();
    private static volatile String lastKey = "";
    private static volatile String lastPath = "";
    private static volatile String lastFailure = "";
    private static volatile String lastLayerKey = "";
    private static volatile String lastLayerPath = "";
    private static volatile String lastLayerFailure = "";

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
            if (wrapper.get("version").getAsInt() != ROOT_VERSION
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
            wrapper.addProperty("version", ROOT_VERSION);
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

    public static LayerPayload readLayerPayload(String rootKey, String layerKey, int bytesPerLayer) {
        LAYER_READS.incrementAndGet();
        lastLayerKey = layerKey == null ? "" : layerKey;
        Path path = layerPayloadPath(rootKey, layerKey);
        lastLayerPath = path.toAbsolutePath().toString();
        if (rootKey == null || rootKey.isBlank() || layerKey == null || layerKey.isBlank()
            || bytesPerLayer <= 0 || !Files.isRegularFile(path)) {
            LAYER_MISSES.incrementAndGet();
            return null;
        }
        try (DataInputStream input = new DataInputStream(Files.newInputStream(path))) {
            String magic = input.readUTF();
            int version = input.readInt();
            String storedRootKey = input.readUTF();
            String storedLayerKey = input.readUTF();
            int storedBytesPerLayer = input.readInt();
            boolean hasSpecular = input.readBoolean();
            boolean displacementEligible = input.readBoolean();
            boolean displacementBlocked = input.readBoolean();
            int heightRangePacked = input.readInt();
            if (!LAYER_MAGIC.equals(magic)
                || version != LAYER_VERSION
                || !rootKey.equals(storedRootKey)
                || !layerKey.equals(storedLayerKey)
                || storedBytesPerLayer != bytesPerLayer) {
                LAYER_MISSES.incrementAndGet();
                return null;
            }
            byte[] albedo = readPlane(input, bytesPerLayer);
            byte[] specular = readPlane(input, bytesPerLayer);
            byte[] normal = readPlane(input, bytesPerLayer);
            byte[] flag = readPlane(input, bytesPerLayer);
            LAYER_HITS.incrementAndGet();
            lastLayerFailure = "";
            return new LayerPayload(albedo, specular, normal, flag, hasSpecular,
                displacementEligible, displacementBlocked, heightRangePacked);
        } catch (Exception e) {
            LAYER_FAILURES.incrementAndGet();
            lastLayerFailure = e.toString();
            return null;
        }
    }

    public static void writeLayerPayload(String rootKey, String layerKey, LayerPayload payload) {
        if (rootKey == null || rootKey.isBlank() || layerKey == null || layerKey.isBlank()
            || payload == null || !payload.hasCompletePlanes()) {
            return;
        }
        Path path = layerPayloadPath(rootKey, layerKey);
        lastLayerKey = layerKey;
        lastLayerPath = path.toAbsolutePath().toString();
        try {
            Files.createDirectories(path.getParent());
            Path temp = path.resolveSibling(path.getFileName().toString() + ".tmp");
            try (DataOutputStream output = new DataOutputStream(Files.newOutputStream(temp))) {
                output.writeUTF(LAYER_MAGIC);
                output.writeInt(LAYER_VERSION);
                output.writeUTF(rootKey);
                output.writeUTF(layerKey);
                output.writeInt(payload.bytesPerLayer());
                output.writeBoolean(payload.hasSpecular());
                output.writeBoolean(payload.displacementEligible());
                output.writeBoolean(payload.displacementBlocked());
                output.writeInt(payload.heightRangePacked());
                writePlane(output, payload.albedo());
                writePlane(output, payload.specular());
                writePlane(output, payload.normal());
                writePlane(output, payload.flag());
            }
            try {
                Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException atomicMoveFailure) {
                Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING);
            }
            LAYER_WRITES.incrementAndGet();
            lastLayerFailure = "";
        } catch (IOException e) {
            LAYER_FAILURES.incrementAndGet();
            lastLayerFailure = e.toString();
        }
    }

    public static JsonObject statusJson() {
        JsonObject json = new JsonObject();
        json.addProperty("schema", "texture_loader_cache_status_v1");
        json.addProperty("diskCacheEnabled", true);
        json.addProperty("cacheKind", "runtime_ctm_dependency_root_and_layer_payloads");
        json.addProperty("rootVersion", ROOT_VERSION);
        json.addProperty("layerVersion", LAYER_VERSION);
        json.addProperty("cacheDirectory", cacheDirectory().toAbsolutePath().toString());
        json.addProperty("lastKey", lastKey);
        json.addProperty("lastPath", lastPath);
        json.addProperty("reads", READS.get());
        json.addProperty("writes", WRITES.get());
        json.addProperty("hits", HITS.get());
        json.addProperty("misses", MISSES.get());
        json.addProperty("failures", FAILURES.get());
        json.addProperty("lastFailure", lastFailure);
        json.addProperty("layerReads", LAYER_READS.get());
        json.addProperty("layerWrites", LAYER_WRITES.get());
        json.addProperty("layerHits", LAYER_HITS.get());
        json.addProperty("layerMisses", LAYER_MISSES.get());
        json.addProperty("layerFailures", LAYER_FAILURES.get());
        json.addProperty("lastLayerKey", lastLayerKey);
        json.addProperty("lastLayerPath", lastLayerPath);
        json.addProperty("lastLayerFailure", lastLayerFailure);
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
        return cacheDirectory().resolve("roots").resolve(safe + ".json");
    }

    private static Path layerPayloadPath(String rootKey, String layerKey) {
        String safeRoot = rootKey == null ? "unknown" : rootKey.replaceAll("[^a-zA-Z0-9._-]", "_");
        String safeLayer = layerKey == null ? "unknown" : layerKey.replaceAll("[^a-zA-Z0-9._-]", "_");
        return cacheDirectory().resolve("layers").resolve(safeRoot).resolve(safeLayer + ".bin");
    }

    private static Path cacheDirectory() {
        MinecraftClient client = MinecraftClient.getInstance();
        Path runDirectory = client == null || client.runDirectory == null
            ? Path.of(".")
            : client.runDirectory.toPath();
        return runDirectory.resolve("radiance").resolve("cache").resolve("texture-loader-v3");
    }

    private static byte[] readPlane(DataInputStream input, int expectedBytes) throws IOException {
        int bytes = input.readInt();
        if (bytes != expectedBytes) {
            throw new IOException("cached layer plane size mismatch expected=" + expectedBytes + " actual=" + bytes);
        }
        byte[] data = new byte[bytes];
        input.readFully(data);
        return data;
    }

    private static void writePlane(DataOutputStream output, byte[] data) throws IOException {
        output.writeInt(data.length);
        output.write(data);
    }

    public record LayerPayload(byte[] albedo,
                               byte[] specular,
                               byte[] normal,
                               byte[] flag,
                               boolean hasSpecular,
                               boolean displacementEligible,
                               boolean displacementBlocked,
                               int heightRangePacked) {
        public int bytesPerLayer() {
            return albedo == null ? 0 : albedo.length;
        }

        boolean hasCompletePlanes() {
            int bytes = bytesPerLayer();
            return bytes > 0
                && specular != null && specular.length == bytes
                && normal != null && normal.length == bytes
                && flag != null && flag.length == bytes;
        }
    }
}
