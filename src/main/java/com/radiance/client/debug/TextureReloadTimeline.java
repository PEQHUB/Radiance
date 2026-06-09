package com.radiance.client.debug;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.radiance.client.RadianceClient;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.MinecraftClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class TextureReloadTimeline {
    private static final Logger LOGGER = LoggerFactory.getLogger("RadSER Texture Timeline");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Object LOCK = new Object();
    private static final List<Event> EVENTS = new ArrayList<>();
    private static JsonObject metadata = new JsonObject();
    private static JsonObject summary = new JsonObject();
    private static long startedNs = 0L;
    private static volatile JsonObject latest = new JsonObject();

    private TextureReloadTimeline() {
    }

    public static void begin(String atlasId, int atlasSprites, int atlasWidth, int atlasHeight) {
        synchronized (LOCK) {
            EVENTS.clear();
            summary = new JsonObject();
            metadata = new JsonObject();
            metadata.addProperty("schema", "radser_texture_reload_timeline_v1");
            metadata.addProperty("capturedAt", Instant.now().toString());
            metadata.addProperty("atlasId", atlasId);
            metadata.addProperty("atlasSprites", atlasSprites);
            metadata.addProperty("atlasWidth", atlasWidth);
            metadata.addProperty("atlasHeight", atlasHeight);
            startedNs = System.nanoTime();
            EVENTS.add(new Event("reloadStart", 0.0, 0.0));
        }
    }

    public static long start(String name) {
        mark(name + "Start");
        return System.nanoTime();
    }

    public static void end(String name, long phaseStartNs) {
        double elapsedMs = elapsedMs(phaseStartNs);
        synchronized (LOCK) {
            EVENTS.add(new Event(name + "End", sinceStartMs(), elapsedMs));
        }
    }

    public static void mark(String name) {
        synchronized (LOCK) {
            EVENTS.add(new Event(name, sinceStartMs(), 0.0));
        }
    }

    public static void addSummary(String name, Number value) {
        synchronized (LOCK) {
            summary.addProperty(name, value);
        }
    }

    public static void addSummary(String name, String value) {
        synchronized (LOCK) {
            summary.addProperty(name, value);
        }
    }

    public static void fail(Throwable t) {
        synchronized (LOCK) {
            summary.addProperty("failed", true);
            summary.addProperty("failureType", t.getClass().getSimpleName());
            summary.addProperty("failureMessage", t.getMessage());
        }
        finish();
    }

    public static void finish() {
        JsonObject json;
        synchronized (LOCK) {
            markLocked("reloadEnd");
            summary.addProperty("totalMs", round3(sinceStartMs()));
            json = new JsonObject();
            for (String key : metadata.keySet()) {
                json.add(key, metadata.get(key));
            }
            json.add("summary", summary.deepCopy());
            JsonArray events = new JsonArray();
            for (Event event : EVENTS) {
                events.add(event.toJson());
            }
            json.add("events", events);
            latest = json;
        }
        writeLatest(json);
    }

    public static String latestJson() {
        synchronized (LOCK) {
            return GSON.toJson(latest);
        }
    }

    public static Path latestPath() {
        return debugDir().resolve("texture_reload_timeline.json");
    }

    private static void writeLatest(JsonObject json) {
        try {
            Path path = latestPath();
            Files.createDirectories(path.getParent());
            Files.writeString(path, GSON.toJson(json));
        } catch (IOException e) {
            LOGGER.warn("[TextureTimeline] Failed to write texture reload timeline: {}", e.getMessage());
        }
    }

    private static Path debugDir() {
        Path base = RadianceClient.radianceDir != null
            ? RadianceClient.radianceDir
            : MinecraftClient.getInstance().runDirectory.toPath().resolve("radiance");
        return base.resolve("debug");
    }

    private static void markLocked(String name) {
        EVENTS.add(new Event(name, sinceStartMs(), 0.0));
    }

    private static double sinceStartMs() {
        return elapsedMs(startedNs);
    }

    private static double elapsedMs(long fromNs) {
        if (fromNs <= 0L) {
            return 0.0;
        }
        return round3((System.nanoTime() - fromNs) / 1_000_000.0);
    }

    private static double round3(double value) {
        return Math.round(value * 1000.0) / 1000.0;
    }

    private record Event(String name, double atMs, double durationMs) {
        JsonObject toJson() {
            JsonObject json = new JsonObject();
            json.addProperty("name", name);
            json.addProperty("atMs", atMs);
            if (durationMs > 0.0) {
                json.addProperty("durationMs", durationMs);
            }
            return json;
        }
    }
}
