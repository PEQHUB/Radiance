package com.radiance.client.debug;

import com.radiance.client.RadianceClient;
import com.radiance.client.autopbr.AutoPbrRuntime;
import com.radiance.client.option.Options;
import com.radiance.client.proxy.vulkan.RendererProxy;
import com.radiance.client.proxy.vulkan.TextureArrayBridge;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import net.minecraft.world.GameRules;

/**
 * File-writing diagnostics and heavy debug actions used by the in-game debug menu.
 */
public final class DebugRuntimeDiagnostics {
    public static final Path TEXTURE_FULL_CSV = Path.of("C:/RadSER/texture_system_full.csv");

    private static final DateTimeFormatter FILE_TIME =
        DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss_SSS").withZone(ZoneId.systemDefault());
    private static final DateTimeFormatter REPORT_TIME =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS z").withZone(ZoneId.systemDefault());

    private DebugRuntimeDiagnostics() {
    }

    public static Path logsDir() {
        Path base = RadianceClient.radianceDir != null
            ? RadianceClient.radianceDir
            : MinecraftClient.getInstance().runDirectory.toPath().resolve("radiance");
        return base.resolve("logs");
    }

    public static Path latestRuntimeSnapshotPath() {
        return logsDir().resolve("runtime_snapshot_latest.txt");
    }

    public static Path latestBundlePath() {
        return logsDir().resolve("debug_bundle_latest.zip");
    }

    public static Path textureManifestPath() {
        return logsDir().resolve("texture_manifest.json");
    }

    public static Path writeRuntimeSnapshot(MinecraftClient client) throws IOException {
        Files.createDirectories(logsDir());
        String report = buildRuntimeSnapshot(client);
        Path latest = latestRuntimeSnapshotPath();
        Path stamped = logsDir().resolve("runtime_snapshot_" + FILE_TIME.format(Instant.now()) + ".txt");
        Files.writeString(latest, report, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        Files.writeString(stamped, report, StandardOpenOption.CREATE_NEW);
        RadianceLogger.log("DebugMenu", "INFO", "runtimeSnapshot=" + latest);
        return latest;
    }

    public static Path writeTextureReloadSnapshot() throws IOException {
        Files.createDirectories(logsDir());
        String body = "Radiance Texture Reload Snapshot\n"
            + "================================\n"
            + "capturedAt=" + REPORT_TIME.format(Instant.now()) + "\n"
            + "nativeTextureDiagnostics=" + safeNative(RendererProxy::nativeGetTextureReloadDiagnostics) + "\n"
            + "textureFullCsv=" + TEXTURE_FULL_CSV.toAbsolutePath() + "\n"
            + "textureFullCsvExists=" + Files.exists(TEXTURE_FULL_CSV) + "\n"
            + "javaTextureGeneration=" + TextureArrayBridge.getActiveTextureGeneration() + "\n"
            + "javaSpriteCount=" + TextureArrayBridge.sortedSpriteIds.size() + "\n";
        Path latest = logsDir().resolve("texture_reload_snapshot_latest.txt");
        Path stamped = logsDir().resolve("texture_reload_snapshot_" + FILE_TIME.format(Instant.now()) + ".txt");
        Files.writeString(latest, body, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        Files.writeString(stamped, body, StandardOpenOption.CREATE_NEW);
        RadianceLogger.log("DebugMenu", "INFO", "textureReloadSnapshot=" + latest);
        return latest;
    }

    public static Path writeVmaSnapshot() throws IOException {
        return writeSingleNativeSnapshot("vma_snapshot", "Radiance VMA Snapshot",
            RendererProxy::nativeGetVmaStats);
    }

    public static Path writeOverlaySnapshot() throws IOException {
        return writeSingleNativeSnapshot("overlay_snapshot", "Radiance Overlay Snapshot",
            RendererProxy::nativeGetOverlayDiag);
    }

    public static Path writeDlssgLatencySnapshot() throws IOException {
        return writeSingleNativeSnapshot("dlssg_latency_snapshot", "Radiance DLSS-G Latency Snapshot",
            RendererProxy::nativeGetDlssgLatencyDiag);
    }

    public static Path writeDebugBundle(MinecraftClient client) throws IOException {
        Path snapshot = writeRuntimeSnapshot(client);
        Path inspect = null;
        try {
            inspect = DebugInspectReporter.captureCurrentTargetReport(client);
        } catch (Exception e) {
            RadianceLogger.log("DebugMenu", "WARN", "targetInspectForBundleFailed=" + e.getMessage());
        }
        return writeDebugBundleFromPrepared(client, snapshot, inspect);
    }

    public static Path writeDebugBundleFromPrepared(MinecraftClient client, Path snapshot, Path inspect)
        throws IOException {
        Files.createDirectories(logsDir());
        Path latest = latestBundlePath();
        Path stamped = logsDir().resolve("debug_bundle_" + FILE_TIME.format(Instant.now()) + ".zip");
        writeBundle(latest, client, snapshot, inspect);
        writeBundle(stamped, client, snapshot, inspect);
        RadianceLogger.log("DebugMenu", "INFO", "debugBundle=" + latest);
        return latest;
    }

    public static void copyPathToClipboard(MinecraftClient client, Path path) {
        client.keyboard.setClipboard(path.toAbsolutePath().toString());
        toast(client, "Copied path: " + path.getFileName());
    }

    public static void resetAccumulation(MinecraftClient client) {
        Options.nativeResetAccumulation();
        action(client, "Reset accumulation");
    }

    public static void rebuildChunks(MinecraftClient client) {
        if (client.worldRenderer != null) {
            client.worldRenderer.reload();
        }
        Options.nativeRebuildChunks();
        action(client, "Rebuild chunks requested");
    }

    public static void clearWeather(MinecraftClient client) {
        if (!hasIntegratedServer(client)) {
            toast(client, "Clear weather requires an integrated server");
            return;
        }
        var server = client.getServer();
        server.getOverworld().setWeather(1000000, 0, false, false);
        server.getGameRules().get(GameRules.DO_WEATHER_CYCLE).set(false, server);
        action(client, "Weather cleared");
    }

    public static void setNoon(MinecraftClient client) {
        if (!hasIntegratedServer(client)) {
            toast(client, "Set noon requires an integrated server");
            return;
        }
        var server = client.getServer();
        server.getOverworld().setTimeOfDay(6000);
        server.getGameRules().get(GameRules.DO_DAYLIGHT_CYCLE).set(false, server);
        action(client, "Time set to noon");
    }

    public static boolean hasIntegratedServer(MinecraftClient client) {
        return client.world != null && client.getServer() != null;
    }

    public static void toast(MinecraftClient client, String message) {
        if (client.inGameHud != null) {
            client.inGameHud.getChatHud().addMessage(Text.literal(message));
        }
    }

    public static String safeNative(StringSupplier supplier) {
        try {
            String value = supplier.get();
            if (value == null || value.isBlank()) return "unavailable";
            return value.replace('\n', ' ').trim();
        } catch (Throwable t) {
            return "error:" + t.getClass().getSimpleName() + ":" + t.getMessage();
        }
    }

    private static String buildRuntimeSnapshot(MinecraftClient client) {
        StringBuilder sb = new StringBuilder(12_000);
        sb.append("Radiance Runtime Snapshot\n");
        sb.append("=========================\n");
        sb.append("capturedAt=").append(REPORT_TIME.format(Instant.now())).append('\n');
        sb.append("minecraftVersion=").append(client.getGameVersion()).append('\n');
        sb.append("radianceDir=").append(RadianceClient.radianceDir).append('\n');
        sb.append("runDirectory=").append(client.runDirectory.toPath().toAbsolutePath()).append('\n');
        sb.append('\n');

        sb.append("World\n-----\n");
        sb.append("hasWorld=").append(client.world != null).append('\n');
        if (client.world != null) {
            sb.append("dimension=").append(client.world.getRegistryKey().getValue()).append('\n');
            sb.append("timeOfDay=").append(client.world.getTimeOfDay()).append('\n');
        }
        sb.append("hasIntegratedServer=").append(hasIntegratedServer(client)).append('\n');
        if (client.player != null) {
            sb.append("playerBlockPos=").append(client.player.getBlockPos().toShortString()).append('\n');
            sb.append("yaw=").append(client.player.getYaw()).append('\n');
            sb.append("pitch=").append(client.player.getPitch()).append('\n');
        }
        sb.append('\n');

        sb.append("Renderer\n--------\n");
        sb.append("gpuProfile=").append(safeNative(RendererProxy::nativeGetGpuProfile)).append('\n');
        sb.append("featureTruth=").append(safeNative(RendererProxy::nativeGetFeatureTruth)).append('\n');
        sb.append("colorPipeline=").append(safeNative(RendererProxy::nativeGetColorPipelineDiagnostics)).append('\n');
        sb.append("dlssgLatency=").append(safeNative(RendererProxy::nativeGetDlssgLatencyDiag)).append('\n');
        sb.append("vmaStats=").append(safeNative(RendererProxy::nativeGetVmaStats)).append('\n');
        sb.append("overlayDiag=").append(safeNative(RendererProxy::nativeGetOverlayDiag)).append('\n');
        sb.append("textureReload=").append(safeNative(RendererProxy::nativeGetTextureReloadDiagnostics)).append('\n');
        sb.append("javaTextureGeneration=").append(TextureArrayBridge.getActiveTextureGeneration()).append('\n');
        sb.append("javaSpriteCount=").append(TextureArrayBridge.sortedSpriteIds.size()).append('\n');
        sb.append('\n');

        sb.append("Material Lab\n");
        sb.append("------------\n");
        sb.append("lastRecipeRehydrate=").append(AutoPbrRuntime.lastRehydrateReport()).append('\n');
        sb.append("lastRecipeRehydrateAtMillis=").append(AutoPbrRuntime.lastRehydrateAtMillis()).append('\n');
        sb.append("spriteProvenanceCounts=").append(AutoPbrRuntime.spriteProvenanceCounts()).append('\n');
        sb.append("textureRuleDiagnostics=").append(AutoPbrRuntime.diagnosticsSummary()).append('\n');
        sb.append("displacementHeightSource=authored_normal_alpha_only\n");
        sb.append("generatedHeightDisplacement=not_supported\n");
        sb.append("displacementKnownBlockers=global_disabled,zero_depth,renderer_variant_no_height,non_block_geometry,hand_geometry,fluid_geometry,thin_cutout_plant,missing_native_sprite_entry,missing_normal_layer,no_height_flag,normal_source_generated_or_flat,uniform_normal_alpha,fade_distance,invalid_uv_basis,grazing_view\n");
        sb.append('\n');

        sb.append("Options\n-------\n");
        sb.append("loggingEnabled=").append(Options.loggingEnabled).append('\n');
        sb.append("gpuDebugLabels=").append(Options.gpuDebugLabels).append('\n');
        sb.append("displacementEnabled=").append(Options.displacementEnabled).append('\n');
        sb.append("displacementQuality=").append(Options.displacementQualityName(Options.displacementQuality)).append('\n');
        sb.append("displacementHeightSource=authored_normal_alpha_only\n");
        sb.append("displacementProxyGeometry=disabled\n");
        sb.append("displacementPath=shader_height_field\n");
        sb.append("upscalerMode=").append(Options.upscalerMode).append('\n');
        sb.append("frameGenMode=").append(Options.frameGenMode).append('\n');
        sb.append("frameGenMultiplier=").append(Options.frameGenMultiplier).append('\n');
        sb.append("dlssgQueueParallelism=").append(Options.dlssgQueueParallelism).append('\n');
        sb.append("reflexEnabled=").append(Options.reflexEnabled).append('\n');
        sb.append("rayBounces=").append(Options.rayBounces).append('\n');
        sb.append("sharcEnabled=").append(Options.sharcEnabled).append('\n');
        sb.append('\n');

        sb.append("Paths\n-----\n");
        sb.append("latestRuntimeSnapshot=").append(latestRuntimeSnapshotPath().toAbsolutePath()).append('\n');
        sb.append("latestTargetInspect=").append(DebugInspectReporter.latestReportPath().toAbsolutePath()).append('\n');
        sb.append("latestDebugBundle=").append(latestBundlePath().toAbsolutePath()).append('\n');
        sb.append("textureManifest=").append(textureManifestPath().toAbsolutePath()).append('\n');
        sb.append("textureFullCsv=").append(TEXTURE_FULL_CSV.toAbsolutePath()).append('\n');
        return sb.toString();
    }

    private static Path writeSingleNativeSnapshot(String prefix, String title, StringSupplier supplier)
        throws IOException {
        Files.createDirectories(logsDir());
        String body = title + "\n"
            + "=".repeat(title.length()) + "\n"
            + "capturedAt=" + REPORT_TIME.format(Instant.now()) + "\n"
            + "data=" + safeNative(supplier) + "\n";
        Path latest = logsDir().resolve(prefix + "_latest.txt");
        Path stamped = logsDir().resolve(prefix + "_" + FILE_TIME.format(Instant.now()) + ".txt");
        Files.writeString(latest, body, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        Files.writeString(stamped, body, StandardOpenOption.CREATE_NEW);
        RadianceLogger.log("DebugMenu", "INFO", prefix + "=" + latest);
        return latest;
    }

    private static void writeBundle(Path output, MinecraftClient client, Path snapshot, Path inspect)
        throws IOException {
        Set<Path> added = new LinkedHashSet<>();
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(
            output, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING))) {
            addIfExists(zip, added, snapshot, "runtime_snapshot.txt");
            addIfExists(zip, added, inspect, "debug_inspect_latest.txt");
            addIfExists(zip, added, DebugInspectReporter.latestReportPath(), "debug_inspect_latest_existing.txt");
            addIfExists(zip, added, logsDir().resolve("dlssg_latency_snapshot_latest.txt"),
                "radiance_logs/dlssg_latency_snapshot_latest.txt");
            addIfExists(zip, added, logsDir().resolve("crash_ring.txt"), "radiance_logs/crash_ring.txt");
            addIfExists(zip, added, logsDir().resolve("engine.log"), "radiance_logs/engine.log");
            addIfExists(zip, added, logsDir().resolve("engine.jsonl"), "radiance_logs/engine.jsonl");
            addIfExists(zip, added, logsDir().resolve("render_diag.log"), "radiance_logs/render_diag.log");
            addIfExists(zip, added, logsDir().resolve("blas_diag.log"), "radiance_logs/blas_diag.log");
            addIfExists(zip, added, textureManifestPath(), "radiance_logs/texture_manifest.json");
            addIfExists(zip, added, TEXTURE_FULL_CSV, "texture_system_full.csv");
            addIfExists(zip, added, client.runDirectory.toPath().resolve("logs").resolve("latest.log"),
                "minecraft_logs/latest.log");
        }
    }

    private static void addIfExists(ZipOutputStream zip, Set<Path> added, Path path, String entryName)
        throws IOException {
        if (path == null || !Files.exists(path) || !Files.isRegularFile(path)) return;
        Path normalized = path.toAbsolutePath().normalize();
        if (!added.add(normalized)) return;
        zip.putNextEntry(new ZipEntry(entryName));
        Files.copy(normalized, zip);
        zip.closeEntry();
    }

    private static void action(MinecraftClient client, String message) {
        RadianceLogger.log("DebugMenu", "INFO", message);
        toast(client, "Radiance: " + message);
    }

    @FunctionalInterface
    public interface StringSupplier {
        String get();
    }
}
