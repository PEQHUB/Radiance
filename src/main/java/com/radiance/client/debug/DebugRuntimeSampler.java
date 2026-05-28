package com.radiance.client.debug;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.radiance.client.proxy.vulkan.RendererProxy;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.client.MinecraftClient;

/**
 * Background diagnostics sampler for menu-triggered GPU captures and bundle writes.
 */
public final class DebugRuntimeSampler {
    private static final DateTimeFormatter FILE_TIME =
        DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss_SSS").withZone(ZoneId.systemDefault());
    private static final Gson PRETTY_GSON = new GsonBuilder().setPrettyPrinting().create();

    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "RadianceDebugRuntimeSampler");
        t.setDaemon(true);
        return t;
    });
    private static final AtomicBoolean BUSY = new AtomicBoolean(false);

    private static volatile boolean gpuProfilerEnabled = false;
    private static volatile String status = "Idle";
    private static volatile Path lastGpuProfilePath = null;
    private static volatile Path lastRtMainTraceSweepPath = null;
    private static volatile Path lastNsightCaptureManifestPath = null;
    private static volatile Path lastNsightCaptureResponsePath = null;

    private DebugRuntimeSampler() {
    }

    public static boolean isBusy() {
        return BUSY.get();
    }

    public static boolean isGpuProfilerEnabled() {
        return gpuProfilerEnabled;
    }

    public static String status() {
        return status;
    }

    public static Path lastGpuProfilePath() {
        return lastGpuProfilePath;
    }

    public static Path lastRtMainTraceSweepPath() {
        return lastRtMainTraceSweepPath;
    }

    public static Path lastNsightCaptureManifestPath() {
        return lastNsightCaptureManifestPath;
    }

    public static Path lastNsightCaptureResponsePath() {
        return lastNsightCaptureResponsePath;
    }

    public static void setGpuProfilerEnabled(boolean enabled, MinecraftClient client) {
        try {
            RendererProxy.nativeSetGpuProfileEnabled(enabled);
            gpuProfilerEnabled = enabled;
            status = enabled ? "GPU profiler enabled" : "GPU profiler disabled";
            DebugRuntimeDiagnostics.toast(client, "Radiance: " + status);
            RadianceLogger.log("DebugMenu", "INFO", status);
        } catch (Throwable t) {
            status = "GPU profiler toggle failed: " + t.getMessage();
            DebugRuntimeDiagnostics.toast(client, "Radiance: " + status);
            RadianceLogger.log("DebugMenu", "ERROR", status);
        }
    }

    public static void startGpuProfileCapture(int samples, MinecraftClient client) {
        if (!BUSY.compareAndSet(false, true)) {
            DebugRuntimeDiagnostics.toast(client, "Radiance debug task already running: " + status);
            return;
        }
        status = "Capturing GPU profile (" + samples + " samples)";
        DebugRuntimeDiagnostics.toast(client, "Radiance: " + status);
        EXECUTOR.submit(() -> {
            try {
                RendererProxy.nativeSetGpuProfileEnabled(true);
                gpuProfilerEnabled = true;
                Path report = captureGpuProfile(samples);
                lastGpuProfilePath = report;
                status = "GPU profile saved: " + report.getFileName();
                client.execute(() -> DebugRuntimeDiagnostics.toast(client, "Radiance: " + status));
                RadianceLogger.log("DebugMenu", "INFO", "gpuProfile=" + report);
            } catch (Exception e) {
                status = "GPU profile failed: " + e.getMessage();
                client.execute(() -> DebugRuntimeDiagnostics.toast(client, "Radiance: " + status));
                RadianceLogger.log("DebugMenu", "ERROR", status);
            } finally {
                BUSY.set(false);
            }
        });
    }

    public static void writeDebugBundleAsync(MinecraftClient client) {
        if (!BUSY.compareAndSet(false, true)) {
            DebugRuntimeDiagnostics.toast(client, "Radiance debug task already running: " + status);
            return;
        }
        Path snapshot = null;
        Path inspect = null;
        try {
            snapshot = DebugRuntimeDiagnostics.writeRuntimeSnapshot(client);
            inspect = DebugInspectReporter.captureCurrentTargetReport(client);
        } catch (Exception e) {
            RadianceLogger.log("DebugMenu", "WARN", "debugBundlePrepFailed=" + e.getMessage());
        }
        final Path preparedSnapshot = snapshot;
        final Path preparedInspect = inspect;
        status = "Writing debug bundle";
        DebugRuntimeDiagnostics.toast(client, "Radiance: " + status);
        EXECUTOR.submit(() -> {
            try {
                Path bundle = DebugRuntimeDiagnostics.writeDebugBundleFromPrepared(
                    client, preparedSnapshot, preparedInspect);
                status = "Debug bundle saved: " + bundle.getFileName();
                client.execute(() -> DebugRuntimeDiagnostics.toast(client, "Radiance: " + status));
            } catch (Exception e) {
                status = "Debug bundle failed: " + e.getMessage();
                client.execute(() -> DebugRuntimeDiagnostics.toast(client, "Radiance: " + status));
                RadianceLogger.log("DebugMenu", "ERROR", status);
            } finally {
                BUSY.set(false);
            }
        });
    }

    public static void writeNsightCaptureContextAsync(MinecraftClient client) {
        if (!BUSY.compareAndSet(false, true)) {
            DebugRuntimeDiagnostics.toast(client, "Radiance debug task already running: " + status);
            return;
        }
        status = "Closing menu for Nsight capture context";
        DebugRuntimeDiagnostics.toast(client, "Radiance: " + status);
        if (client.currentScreen != null) {
            client.setScreen(null);
        }
        EXECUTOR.submit(() -> {
            try {
                waitForGameplayCaptureSurface(client);
                status = "Writing Nsight capture context";
                JsonObject response = requestBridgeCaptureContext();
                Path responsePath = writeNsightCaptureResponse(response);
                lastNsightCaptureResponsePath = responsePath;
                if (response.has("manifest")) {
                    lastNsightCaptureManifestPath = Path.of(response.get("manifest").getAsString());
                    status = "Nsight context saved: " + lastNsightCaptureManifestPath.getParent().getFileName();
                } else {
                    status = "Nsight context response saved: " + responsePath.getFileName();
                }
                client.execute(() -> DebugRuntimeDiagnostics.toast(client, "Radiance: " + status));
                RadianceLogger.log("DebugMenu", "INFO", "nsightCaptureContext=" + responsePath);
            } catch (Exception e) {
                status = "Nsight context failed: " + e.getMessage();
                client.execute(() -> DebugRuntimeDiagnostics.toast(client, "Radiance: " + status));
                RadianceLogger.log("DebugMenu", "ERROR", status);
            } finally {
                BUSY.set(false);
            }
        });
    }

    public static void startRtMainTraceSweep(MinecraftClient client) {
        if (!BUSY.compareAndSet(false, true)) {
            DebugRuntimeDiagnostics.toast(client, "Radiance debug task already running: " + status);
            return;
        }
        status = "Closing menu for RT MainTrace sweep";
        DebugRuntimeDiagnostics.toast(client, "Radiance: " + status);
        if (client.currentScreen != null) {
            client.setScreen(null);
        }
        EXECUTOR.submit(() -> {
            try {
                waitForGameplayCaptureSurface(client);
                status = "Running RT MainTrace sweep";
                JsonObject response = requestBridgeRtMainTraceSweep();
                Path responsePath = writeRtMainTraceSweepResponse(response);
                if (response.has("path")) {
                    lastRtMainTraceSweepPath = Path.of(response.get("path").getAsString());
                    status = "RT sweep saved: " + lastRtMainTraceSweepPath.getFileName();
                } else {
                    lastRtMainTraceSweepPath = responsePath;
                    status = "RT sweep response saved: " + responsePath.getFileName();
                }
                client.execute(() -> DebugRuntimeDiagnostics.toast(client, "Radiance: " + status));
                RadianceLogger.log("DebugMenu", "INFO", "rtMainTraceSweep=" + responsePath);
            } catch (Exception e) {
                status = "RT sweep failed: " + e.getMessage();
                client.execute(() -> DebugRuntimeDiagnostics.toast(client, "Radiance: " + status));
                RadianceLogger.log("DebugMenu", "ERROR", status);
            } finally {
                BUSY.set(false);
            }
        });
    }

    private static void waitForGameplayCaptureSurface(MinecraftClient client) throws IOException {
        long deadline = System.nanoTime() + 5_000_000_000L;
        while (System.nanoTime() < deadline) {
            boolean ready = client.world != null
                && client.player != null
                && client.currentScreen == null
                && client.getWindow().getFramebufferWidth() > 0
                && client.getWindow().getFramebufferHeight() > 0;
            if (ready) {
                try {
                    Thread.sleep(750);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IOException("Interrupted while waiting for gameplay capture surface", e);
                }
                return;
            }
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Interrupted while waiting for gameplay capture surface", e);
            }
        }
        throw new IOException("Gameplay did not resume before captureContext timeout");
    }

    private static Path captureGpuProfile(int samples) throws IOException {
        Map<String, Double> sums = new LinkedHashMap<>();
        int validSamples = 0;
        for (int i = 0; i < samples; i++) {
            try {
                Thread.sleep(16);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
            String raw = DebugRuntimeDiagnostics.safeNative(RendererProxy::nativeGetGpuProfile);
            if (raw.isBlank() || raw.equals("unavailable") || raw.startsWith("error:")) continue;
            boolean any = false;
            for (String part : raw.split(",")) {
                int colon = part.indexOf(':');
                if (colon < 0) continue;
                String name = part.substring(0, colon);
                try {
                    double ms = Double.parseDouble(part.substring(colon + 1));
                    sums.merge(name, ms, Double::sum);
                    any = true;
                } catch (NumberFormatException ignored) {
                }
            }
            if (any) validSamples++;
        }

        Files.createDirectories(DebugRuntimeDiagnostics.logsDir());
        StringBuilder sb = new StringBuilder();
        sb.append("Radiance GPU Profile\n");
        sb.append("====================\n");
        sb.append("samplesRequested=").append(samples).append('\n');
        sb.append("samplesValid=").append(validSamples).append('\n');
        sb.append("capturedAt=").append(Instant.now()).append('\n');
        sb.append('\n');
        if (validSamples == 0) {
            sb.append("No valid GPU profile samples. The profiler may not have produced timestamps yet.\n");
        } else {
            for (Map.Entry<String, Double> entry : sums.entrySet()) {
                sb.append(entry.getKey()).append('=')
                    .append(String.format(Locale.ROOT, "%.4f ms", entry.getValue() / validSamples))
                    .append('\n');
            }
        }

        Path latest = DebugRuntimeDiagnostics.logsDir().resolve("gpu_profile_latest.txt");
        Path stamped = DebugRuntimeDiagnostics.logsDir().resolve(
            "gpu_profile_" + FILE_TIME.format(Instant.now()) + ".txt");
        Files.writeString(latest, sb.toString(),
            StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        Files.writeString(stamped, sb.toString(), StandardOpenOption.CREATE_NEW);
        return latest;
    }

    private static JsonObject requestBridgeCaptureContext() throws IOException {
        JsonObject req = new JsonObject();
        req.addProperty("cmd", "captureContext");
        req.addProperty("label", "debug_menu");
        req.addProperty("gpuFrames", 30);
        req.addProperty("histogramFrames", 120);
        req.addProperty("bucketMs", 2.0);

        try (Socket socket = new Socket("127.0.0.1", 19845)) {
            socket.setSoTimeout(120_000);
            try (PrintWriter out = new PrintWriter(socket.getOutputStream(), true, StandardCharsets.UTF_8);
                 BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(),
                     StandardCharsets.UTF_8))) {
                out.println(req);
                String line = in.readLine();
                if (line == null || line.isBlank()) {
                    throw new IOException("DebugBridge returned no response");
                }
                return JsonParser.parseString(line).getAsJsonObject();
            }
        }
    }

    private static JsonObject requestBridgeRtMainTraceSweep() throws IOException {
        JsonObject req = new JsonObject();
        req.addProperty("cmd", "rtMainTraceSweep");
        req.addProperty("frames", 45);
        req.addProperty("settleMs", 500);

        try (Socket socket = new Socket("127.0.0.1", 19845)) {
            socket.setSoTimeout(180_000);
            try (PrintWriter out = new PrintWriter(socket.getOutputStream(), true, StandardCharsets.UTF_8);
                 BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(),
                     StandardCharsets.UTF_8))) {
                out.println(req);
                String line = in.readLine();
                if (line == null || line.isBlank()) {
                    throw new IOException("DebugBridge returned no response");
                }
                return JsonParser.parseString(line).getAsJsonObject();
            }
        }
    }

    private static Path writeNsightCaptureResponse(JsonObject response) throws IOException {
        Files.createDirectories(DebugRuntimeDiagnostics.logsDir());
        Path latest = DebugRuntimeDiagnostics.logsDir().resolve("nsight_capture_context_response_latest.json");
        Path stamped = DebugRuntimeDiagnostics.logsDir().resolve(
            "nsight_capture_context_response_" + FILE_TIME.format(Instant.now()) + ".json");
        String body = PRETTY_GSON.toJson(response);
        Files.writeString(latest, body, StandardCharsets.UTF_8,
            StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        Files.writeString(stamped, body, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
        return latest;
    }

    private static Path writeRtMainTraceSweepResponse(JsonObject response) throws IOException {
        Files.createDirectories(DebugRuntimeDiagnostics.logsDir());
        Path latest = DebugRuntimeDiagnostics.logsDir().resolve("rt_main_trace_sweep_response_latest.json");
        Path stamped = DebugRuntimeDiagnostics.logsDir().resolve(
            "rt_main_trace_sweep_response_" + FILE_TIME.format(Instant.now()) + ".json");
        String body = PRETTY_GSON.toJson(response);
        Files.writeString(latest, body, StandardCharsets.UTF_8,
            StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        Files.writeString(stamped, body, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
        return latest;
    }
}
