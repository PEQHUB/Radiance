package com.radiance.client.debug;

import com.radiance.client.RadianceClient;
import com.radiance.client.option.Options;
import com.radiance.client.util.MaterialBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;

import java.io.IOException;
import java.nio.file.*;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Captures diagnostic context on crash. Always active, near-zero overhead.
 *
 * <p>A shutdown hook checks whether the C++ crash ring buffer was just written
 * (radiance/logs/crash_ring.txt modified within 5 seconds). If so, it dumps
 * all current settings, recent changes, and player state to crash-context.txt
 * in the same directory.</p>
 */
public final class CrashContext {

    private static final int MAX_RECENT_CHANGES = 40;
    private static final DateTimeFormatter FMT =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS").withZone(ZoneId.systemDefault());

    // Circular buffer of recent settings changes
    private static final List<String> recentChanges =
        Collections.synchronizedList(new ArrayList<>());

    private static Path logsDir;
    private static volatile boolean initialized = false;

    private CrashContext() {}

    /** Call once at mod init (after native DLL is loaded). */
    public static void init() {
        if (initialized) return;
        initialized = true;

        // Resolve logs directory
        try {
            Path mcDir = MinecraftClient.getInstance().runDirectory.toPath();
            logsDir = mcDir.resolve("radiance").resolve("logs");
        } catch (Exception e) {
            // Fallback if MC client not ready
            logsDir = Path.of("radiance", "logs");
        }

        Runtime.getRuntime().addShutdownHook(new Thread(CrashContext::onShutdown, "RadianceCrashDump"));
        RadianceClient.LOGGER.info("[CrashContext] Shutdown hook registered");
    }

    /** Record a settings change. Called from Options setters. */
    public static void recordChange(String description) {
        if (!initialized) return;
        String entry = FMT.format(Instant.now()) + " " + description;
        synchronized (recentChanges) {
            recentChanges.add(entry);
            while (recentChanges.size() > MAX_RECENT_CHANGES) {
                recentChanges.remove(0);
            }
        }
    }

    private static void onShutdown() {
        try {
            if (logsDir == null) return;

            // Always dump on shutdown — distinguishes clean vs crash via ring file presence
            Path ringFile = logsDir.resolve("crash_ring.txt");
            boolean gpuCrash = false;
            if (Files.exists(ringFile)) {
                long lastMod = Files.getLastModifiedTime(ringFile).toMillis();
                long now = System.currentTimeMillis();
                gpuCrash = (now - lastMod) < 5000;
            }

            Files.createDirectories(logsDir);
            Path outFile = logsDir.resolve("crash-context.txt");
            StringBuilder sb = new StringBuilder(8192);

            sb.append("=== Radiance Crash Context ===\n");
            sb.append("Time: ").append(FMT.format(Instant.now())).append("\n");
            sb.append("Type: ").append(gpuCrash ? "GPU crash (VK_ERROR_DEVICE_LOST)" : "Java/JVM crash or forced exit").append("\n\n");

            // Player position
            try {
                MinecraftClient mc = MinecraftClient.getInstance();
                if (mc != null && mc.player != null) {
                    BlockPos pos = mc.player.getBlockPos();
                    sb.append("Player: ").append(pos.getX()).append(", ")
                      .append(pos.getY()).append(", ").append(pos.getZ()).append("\n");
                }
            } catch (Exception ignored) {}

            sb.append("Pipeline: default\n");
            sb.append("\n");

            // Recent settings changes
            sb.append("--- Recent Settings Changes (newest last) ---\n");
            synchronized (recentChanges) {
                for (String change : recentChanges) {
                    sb.append("  ").append(change).append("\n");
                }
            }
            if (recentChanges.isEmpty()) {
                sb.append("  (none recorded)\n");
            }
            sb.append("\n");

            // Global settings
            sb.append("--- Global Settings ---\n");
            sb.append("materialOverridesEnabled=").append(Options.materialOverridesEnabled).append("\n");
            sb.append("autoPBREnabled=").append(Options.autoPBREnabled).append("\n");
            sb.append("rayBounces=").append(Options.rayBounces).append("\n");
            sb.append("upscalerResOverride=").append(Options.upscalerResOverride).append("\n");
            sb.append("pomEnabled=").append(Options.pomEnabled).append("\n");
            sb.append("pomHeightScalePercent=").append(Options.pomHeightScalePercent).append("\n");
            sb.append("pomSteps=").append(Options.pomSteps).append("\n");
            sb.append("pomFadeDistance=").append(Options.pomFadeDistance).append("\n");
            sb.append("sharcQualityPreset=").append(Options.sharcQualityPreset).append("\n");
            sb.append("sharcCapacityExponent=").append(Options.sharcCapacityExponent).append("\n");
            sb.append("reflexEnabled=").append(Options.reflexEnabled).append("\n");
            sb.append("vrrMode=").append(Options.vrrMode).append("\n");
            sb.append("chunkBuildingBatchSize=").append(Options.chunkBuildingBatchSize).append("\n");
            sb.append("chunkBuildingTotalBatches=").append(Options.chunkBuildingTotalBatches).append("\n");
            sb.append("\n");

            // Per-block materials with non-default POM or transmission (most crash-relevant)
            sb.append("--- Per-Block Materials (non-default POM/Transmission) ---\n");
            for (MaterialBlock mb : MaterialBlock.values()) {
                int i = mb.ordinal();
                boolean hasPom = Options.materialPomDepth[i] > 0;
                boolean hasTrans = Options.materialTransmission[i] > 0;
                boolean hasAutoPBR = Options.materialAutoPBR[i];
                if (hasPom || hasTrans) {
                    sb.append("  ").append(mb.getId()).append(": ");
                    sb.append("roughness=").append(Options.materialRoughness[i]);
                    sb.append(" transmission=").append(Options.materialTransmission[i]);
                    sb.append(" pomDepth=").append(Options.materialPomDepth[i]);
                    sb.append(" normalStr=").append(Options.materialNormalStrength[i]);
                    sb.append(" normalSmooth=").append(Options.materialNormalSmoothing[i]);
                    sb.append(" texBlend=").append(Options.materialTextureBlend[i]);
                    sb.append(" autoPBR=").append(hasAutoPBR);
                    sb.append(" flags=").append(Options.materialAutoPBRFlags[i]);
                    sb.append("\n");
                }
            }
            sb.append("\n=== End ===\n");

            Files.writeString(outFile, sb.toString(), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            System.err.println("[CrashContext] Wrote " + outFile);

        } catch (Exception e) {
            System.err.println("[CrashContext] Failed to write crash context: " + e.getMessage());
        }
    }
}
