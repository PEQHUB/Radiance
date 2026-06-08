package com.radiance.client.texture.material;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.radiance.client.RadianceClient;
import com.radiance.client.texture.compat.ResourcePackTextureVariantResolver;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import net.minecraft.client.MinecraftClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Small live material-runtime status artifact.
 *
 * <p>This intentionally does not run resource-pack diagnostics. It snapshots
 * the active material registry plus the current bootstrap/residency event so
 * runtime truth stays cheap and current while the large pack report remains a
 * static pack-universe artifact.</p>
 */
public final class ResourceMaterialRuntimeStatus {
    public static final String FILE_NAME = "radser-material-runtime-status.json";
    private static final Logger LOGGER = LoggerFactory.getLogger("RadSER Material Compat");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Object WRITE_LOCK = new Object();

    private ResourceMaterialRuntimeStatus() {
    }

    public static void write(String status, long generation, JsonObject event) {
        JsonObject root = new JsonObject();
        root.addProperty("schema", "radser_material_runtime_status_v1");
        root.addProperty("createdAt", Instant.now().toString());
        root.addProperty("status", status == null || status.isBlank() ? "unknown" : status);
        root.addProperty("generation", generation);
        root.add("materialRegistry", ResourceMaterialRegistry.activeSummaryJson());
        root.add("visibleResidency", ResourceMaterialResidencyDemand.summaryJson(generation));
        root.add("textureVariantResolution", ResourcePackTextureVariantResolver.runtimeResolutionStatsJson());
        if (event != null) {
            root.add("event", event.deepCopy());
        }
        writeJson(root);
    }

    public static String latestJson() {
        try {
            Path path = latestPath();
            if (Files.exists(path)) {
                return Files.readString(path, StandardCharsets.UTF_8);
            }
        } catch (Throwable ignored) {
        }
        JsonObject root = new JsonObject();
        root.addProperty("schema", "radser_material_runtime_status_v1");
        root.addProperty("status", "unavailable");
        root.add("materialRegistry", ResourceMaterialRegistry.activeSummaryJson());
        return GSON.toJson(root);
    }

    public static Path latestPath() {
        return logsDirectory().resolve(FILE_NAME);
    }

    private static void writeJson(JsonObject root) {
        synchronized (WRITE_LOCK) {
            try {
                Path logs = logsDirectory();
                Files.createDirectories(logs);
                Path target = latestPath();
                Path tmp = logs.resolve(FILE_NAME + ".tmp");
                Files.writeString(tmp, GSON.toJson(root), StandardCharsets.UTF_8);
                try {
                    Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
                } catch (IOException atomicMoveFailed) {
                    Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (Throwable t) {
                LOGGER.debug("[MaterialCompat] Failed to write runtime material status", t);
            }
        }
    }

    private static Path logsDirectory() {
        if (RadianceClient.radianceDir != null) {
            return RadianceClient.radianceDir.resolve("logs");
        }
        try {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client != null && client.runDirectory != null) {
                return client.runDirectory.toPath().resolve("radiance/logs");
            }
        } catch (Throwable ignored) {
        }
        return Path.of("radiance/logs");
    }
}
