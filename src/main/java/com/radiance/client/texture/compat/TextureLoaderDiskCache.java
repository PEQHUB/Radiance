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
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.client.MinecraftClient;

public final class TextureLoaderDiskCache {
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();
    private static final int ROOT_VERSION = 1;
    private static final int LAYER_VERSION = 1;
    private static final int BYTE_VERSION = 1;
    private static final String LAYER_MAGIC = "radser-layer-payload";
    private static final String BYTE_MAGIC = "radser-byte-payload";
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
    private static final AtomicLong BYTE_READS = new AtomicLong();
    private static final AtomicLong BYTE_WRITES = new AtomicLong();
    private static final AtomicLong BYTE_HITS = new AtomicLong();
    private static final AtomicLong BYTE_MISSES = new AtomicLong();
    private static final AtomicLong BYTE_FAILURES = new AtomicLong();
    private static final AtomicLong ASYNC_WRITE_QUEUED = new AtomicLong();
    private static final AtomicLong ASYNC_WRITE_STARTED = new AtomicLong();
    private static final AtomicLong ASYNC_WRITE_COMPLETED = new AtomicLong();
    private static final AtomicLong ASYNC_WRITE_REJECTED = new AtomicLong();
    private static final AtomicLong ASYNC_WRITE_PENDING = new AtomicLong();
    private static final AtomicInteger CACHE_WRITE_THREAD_IDS = new AtomicInteger();
    private static final ThreadPoolExecutor CACHE_WRITE_EXECUTOR = createCacheWriteExecutor();
    private static volatile String lastKey = "";
    private static volatile String lastPath = "";
    private static volatile String lastFailure = "";
    private static volatile String lastLayerKey = "";
    private static volatile String lastLayerPath = "";
    private static volatile String lastLayerFailure = "";
    private static volatile String lastByteKey = "";
    private static volatile String lastBytePath = "";
    private static volatile String lastByteFailure = "";

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

    public static void writeRootAsync(String key, JsonObject root) {
        if (key == null || key.isBlank() || root == null) {
            return;
        }
        JsonObject copy = root.deepCopy();
        submitAsyncCacheWrite(() -> writeRoot(key, copy));
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
            moveAtomicallyOrReplace(temp, path);
            LAYER_WRITES.incrementAndGet();
            lastLayerFailure = "";
        } catch (IOException e) {
            LAYER_FAILURES.incrementAndGet();
            lastLayerFailure = e.toString();
        }
    }

    public static void writeLayerPayloadAsync(String rootKey, String layerKey, LayerPayload payload) {
        if (rootKey == null || rootKey.isBlank() || layerKey == null || layerKey.isBlank()
            || payload == null || !payload.hasCompletePlanes()) {
            return;
        }
        LayerPayload copy = payload.copy();
        submitAsyncCacheWrite(() -> writeLayerPayload(rootKey, layerKey, copy));
    }

    public static byte[] readBytePayload(String rootKey, String payloadKey, int expectedBytes) {
        BYTE_READS.incrementAndGet();
        lastByteKey = payloadKey == null ? "" : payloadKey;
        Path path = bytePayloadPath(rootKey, payloadKey);
        lastBytePath = path.toAbsolutePath().toString();
        if (rootKey == null || rootKey.isBlank() || payloadKey == null || payloadKey.isBlank()
            || expectedBytes <= 0 || !Files.isRegularFile(path)) {
            BYTE_MISSES.incrementAndGet();
            return null;
        }
        try (DataInputStream input = new DataInputStream(Files.newInputStream(path))) {
            String magic = input.readUTF();
            int version = input.readInt();
            String storedRootKey = input.readUTF();
            String storedPayloadKey = input.readUTF();
            int storedBytes = input.readInt();
            if (!BYTE_MAGIC.equals(magic)
                || version != BYTE_VERSION
                || !rootKey.equals(storedRootKey)
                || !payloadKey.equals(storedPayloadKey)
                || storedBytes != expectedBytes) {
                BYTE_MISSES.incrementAndGet();
                return null;
            }
            byte[] data = new byte[storedBytes];
            input.readFully(data);
            BYTE_HITS.incrementAndGet();
            lastByteFailure = "";
            return data;
        } catch (Exception e) {
            BYTE_FAILURES.incrementAndGet();
            lastByteFailure = e.toString();
            return null;
        }
    }

    public static void writeBytePayload(String rootKey, String payloadKey, byte[] payload) {
        if (rootKey == null || rootKey.isBlank() || payloadKey == null || payloadKey.isBlank()
            || payload == null || payload.length <= 0) {
            return;
        }
        Path path = bytePayloadPath(rootKey, payloadKey);
        lastByteKey = payloadKey;
        lastBytePath = path.toAbsolutePath().toString();
        try {
            Files.createDirectories(path.getParent());
            Path temp = path.resolveSibling(path.getFileName().toString() + ".tmp");
            try (DataOutputStream output = new DataOutputStream(Files.newOutputStream(temp))) {
                output.writeUTF(BYTE_MAGIC);
                output.writeInt(BYTE_VERSION);
                output.writeUTF(rootKey);
                output.writeUTF(payloadKey);
                output.writeInt(payload.length);
                output.write(payload);
            }
            moveAtomicallyOrReplace(temp, path);
            BYTE_WRITES.incrementAndGet();
            lastByteFailure = "";
        } catch (IOException e) {
            BYTE_FAILURES.incrementAndGet();
            lastByteFailure = e.toString();
        }
    }

    public static void writeBytePayloadAsync(String rootKey, String payloadKey, byte[] payload) {
        if (rootKey == null || rootKey.isBlank() || payloadKey == null || payloadKey.isBlank()
            || payload == null || payload.length <= 0) {
            return;
        }
        byte[] copy = payload.clone();
        submitAsyncCacheWrite(() -> writeBytePayload(rootKey, payloadKey, copy));
    }

    public static JsonObject statusJson() {
        JsonObject json = new JsonObject();
        json.addProperty("schema", "texture_loader_cache_status_v1");
        json.addProperty("diskCacheEnabled", true);
        json.addProperty("cacheKind", "runtime_ctm_dependency_root_and_layer_payloads");
        json.addProperty("rootVersion", ROOT_VERSION);
        json.addProperty("layerVersion", LAYER_VERSION);
        json.addProperty("byteVersion", BYTE_VERSION);
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
        json.addProperty("byteReads", BYTE_READS.get());
        json.addProperty("byteWrites", BYTE_WRITES.get());
        json.addProperty("byteHits", BYTE_HITS.get());
        json.addProperty("byteMisses", BYTE_MISSES.get());
        json.addProperty("byteFailures", BYTE_FAILURES.get());
        json.addProperty("lastByteKey", lastByteKey);
        json.addProperty("lastBytePath", lastBytePath);
        json.addProperty("lastByteFailure", lastByteFailure);
        json.addProperty("asyncCacheWrites", true);
        json.addProperty("asyncWriteThreads", CACHE_WRITE_EXECUTOR.getCorePoolSize());
        json.addProperty("asyncWriteQueueCapacity", 4096);
        json.addProperty("asyncWriteQueueDepth", CACHE_WRITE_EXECUTOR.getQueue().size());
        json.addProperty("asyncWritesQueued", ASYNC_WRITE_QUEUED.get());
        json.addProperty("asyncWritesStarted", ASYNC_WRITE_STARTED.get());
        json.addProperty("asyncWritesCompleted", ASYNC_WRITE_COMPLETED.get());
        json.addProperty("asyncWritesRejected", ASYNC_WRITE_REJECTED.get());
        json.addProperty("asyncWritesPending", ASYNC_WRITE_PENDING.get());
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

    private static Path bytePayloadPath(String rootKey, String payloadKey) {
        String safeRoot = rootKey == null ? "unknown" : rootKey.replaceAll("[^a-zA-Z0-9._-]", "_");
        String safePayload = payloadKey == null ? "unknown" : payloadKey.replaceAll("[^a-zA-Z0-9._-]", "_");
        return cacheDirectory().resolve("bytes").resolve(safeRoot).resolve(safePayload + ".bin");
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

    private static void moveAtomicallyOrReplace(Path temp, Path path) throws IOException {
        try {
            Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException atomicMoveFailure) {
            Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static ThreadPoolExecutor createCacheWriteExecutor() {
        int cores = Math.max(1, Runtime.getRuntime().availableProcessors());
        int threads = Math.max(1, Math.min(4, cores / 2));
        ThreadPoolExecutor executor = new ThreadPoolExecutor(threads, threads, 30L, TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(4096), runnable -> {
                Thread thread = new Thread(runnable,
                    "RadSER Texture Cache Writer " + CACHE_WRITE_THREAD_IDS.incrementAndGet());
                thread.setDaemon(true);
                return thread;
            });
        executor.allowCoreThreadTimeOut(true);
        return executor;
    }

    private static void submitAsyncCacheWrite(Runnable runnable) {
        if (runnable == null) {
            return;
        }
        ASYNC_WRITE_QUEUED.incrementAndGet();
        ASYNC_WRITE_PENDING.incrementAndGet();
        try {
            CACHE_WRITE_EXECUTOR.execute(() -> {
                ASYNC_WRITE_STARTED.incrementAndGet();
                try {
                    runnable.run();
                } finally {
                    ASYNC_WRITE_COMPLETED.incrementAndGet();
                    ASYNC_WRITE_PENDING.decrementAndGet();
                }
            });
        } catch (RejectedExecutionException e) {
            ASYNC_WRITE_REJECTED.incrementAndGet();
            ASYNC_WRITE_PENDING.decrementAndGet();
        }
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

        LayerPayload copy() {
            return new LayerPayload(
                albedo == null ? null : albedo.clone(),
                specular == null ? null : specular.clone(),
                normal == null ? null : normal.clone(),
                flag == null ? null : flag.clone(),
                hasSpecular,
                displacementEligible,
                displacementBlocked,
                heightRangePacked);
        }
    }
}
