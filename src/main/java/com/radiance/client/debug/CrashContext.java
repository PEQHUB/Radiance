package com.radiance.client.debug;

import com.radiance.client.RadianceClient;
import com.radiance.client.option.Options;
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
 * <p>A shutdown hook dumps current settings, recent changes, and player state
 * to crash-context.txt. If the C++ crash ring was just written, it inspects the
 * ring entries for failed Vulkan results before labeling the exit as a GPU
 * crash.</p>
 */
public final class CrashContext {

    private static final int MAX_RECENT_CHANGES = 40;
    private static final int CRASH_RING_FRESH_WINDOW_MS = 5000;
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
            boolean freshRing = false;
            boolean gpuCrash = false;
            if (Files.exists(ringFile)) {
                long lastMod = Files.getLastModifiedTime(ringFile).toMillis();
                long now = System.currentTimeMillis();
                freshRing = (now - lastMod) < CRASH_RING_FRESH_WINDOW_MS;
                gpuCrash = freshRing && crashRingHasFailedVulkanResult(ringFile);
            }

            Files.createDirectories(logsDir);
            Path outFile = logsDir.resolve("crash-context.txt");
            StringBuilder sb = new StringBuilder(8192);

            sb.append("=== Radiance Crash Context ===\n");
            sb.append("Time: ").append(FMT.format(Instant.now())).append("\n");
            String exitType = gpuCrash
                ? "GPU crash (failed Vulkan result in crash ring)"
                : (freshRing ? "Clean shutdown (crash ring has no failed Vulkan results)"
                    : "Java/JVM crash or forced exit");
            sb.append("Type: ").append(exitType).append("\n\n");

            // Player position
            try {
                MinecraftClient mc = MinecraftClient.getInstance();
                if (mc != null && mc.player != null) {
                    BlockPos pos = mc.player.getBlockPos();
                    sb.append("Player: ").append(pos.getX()).append(", ")
                      .append(pos.getY()).append(", ").append(pos.getZ()).append("\n");
                }
            } catch (Exception ignored) {}

            sb.append("Pipeline: ").append(resolvePipelineName()).append("\n");
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
            sb.append("autoPBREnabled=").append(Options.autoPBREnabled).append("\n");
            sb.append("rayBounces=").append(Options.rayBounces).append("\n");
            sb.append("upscalerResOverride=").append(Options.upscalerResOverride).append("\n");
            sb.append("displacementEnabled=").append(Options.displacementEnabled).append("\n");
            sb.append("displacementDepthCapPercent=").append(Options.displacementDepthCapPercent).append("\n");
            sb.append("displacementQuality=").append(Options.displacementQuality)
                .append(" (").append(Options.displacementQualityName(Options.displacementQuality)).append(")\n");
            sb.append("displacementSteps=").append(Options.displacementSteps).append("\n");
            sb.append("displacementRefinement=").append(Options.displacementRefinement).append("\n");
            sb.append("displacementFadeDistance=").append(Options.displacementFadeDistance).append("\n");
            sb.append("sharcQualityPreset=").append(Options.sharcQualityPreset).append("\n");
            sb.append("sharcCapacityExponent=").append(Options.sharcCapacityExponent).append("\n");
            sb.append("reflexEnabled=").append(Options.reflexEnabled).append("\n");
            sb.append("vrrMode=").append(Options.vrrMode).append("\n");
            sb.append("chunkBuildingBatchSize=").append(Options.chunkBuildingBatchSize).append("\n");
            sb.append("chunkBuildingTotalBatches=").append(Options.chunkBuildingTotalBatches).append("\n");
            sb.append("\n");

            sb.append("--- Material Lab ---\n");
            sb.append("  mode=texture-primary\n");
            sb.append("\n=== End ===\n");

            Files.writeString(outFile, sb.toString(), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            System.err.println("[CrashContext] Wrote " + outFile);

        } catch (Exception e) {
            System.err.println("[CrashContext] Failed to write crash context: " + e.getMessage());
        }
    }

    private static boolean crashRingHasFailedVulkanResult(Path ringFile) {
        try {
            for (String line : Files.readAllLines(ringFile)) {
                if (line.contains("DEVICE_LOST") || line.contains("VK_ERROR")) {
                    return true;
                }

                int vkIndex = line.indexOf("vk=");
                if (vkIndex < 0) {
                    continue;
                }

                int valueStart = vkIndex + 3;
                while (valueStart < line.length() && Character.isWhitespace(line.charAt(valueStart))) {
                    valueStart++;
                }

                int valueEnd = valueStart;
                if (valueEnd < line.length() && line.charAt(valueEnd) == '-') {
                    valueEnd++;
                }
                while (valueEnd < line.length() && Character.isDigit(line.charAt(valueEnd))) {
                    valueEnd++;
                }

                if (valueEnd > valueStart) {
                    int vkResult = Integer.parseInt(line.substring(valueStart, valueEnd));
                    if (vkResult != 0) {
                        return true;
                    }
                }
            }
        } catch (Exception ignored) {}

        return false;
    }

    private static String resolvePipelineName() {
        if (logsDir == null || logsDir.getParent() == null) {
            return "unknown";
        }

        Path radianceDir = logsDir.getParent();
        if (Files.exists(radianceDir.resolve("pipeline_fork.yaml"))) {
            return "pipeline_fork.yaml";
        }
        if (Files.exists(radianceDir.resolve("pipeline.yaml"))) {
            return "pipeline.yaml";
        }
        return "default";
    }
}
