package com.radiance.client.gui.unified.populators;

import com.radiance.client.debug.DebugInspectReporter;
import com.radiance.client.debug.DebugRuntimeDiagnostics;
import com.radiance.client.debug.DebugRuntimeSampler;
import com.radiance.client.gui.KeyBindButton;
import com.radiance.client.gui.SelectionDropdownWidget;
import com.radiance.client.gui.unified.ContentPanelWidget;
import com.radiance.client.gui.unified.ContentPopulator;
import com.radiance.client.gui.unified.RadianceUnifiedScreen;
import com.radiance.client.gui.unified.SettingsSection;
import com.radiance.client.gui.unified.UnifiedSearchOverlay;
import com.radiance.client.gui.unified.rows.KeyBindRow;
import com.radiance.client.input.KeyInputHandler;
import com.radiance.client.option.Options;
import com.radiance.client.proxy.vulkan.RendererProxy;
import com.radiance.client.proxy.world.BlockModelBridge;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.option.SimpleOption;
import net.minecraft.text.Text;

public class DebugPopulator implements ContentPopulator {
    public enum Page {
        STATUS,
        CAPTURE_LOGS,
        GPU_PROFILING,
        RESOURCES,
        POWER_ACTIONS
    }

    private final Page page;

    public DebugPopulator(Page page) {
        this.page = page;
    }

    @Override
    public void populate(ContentPanelWidget panel, RadianceUnifiedScreen screen) {
        switch (page) {
            case STATUS -> populateStatus(panel);
            case CAPTURE_LOGS -> populateCaptureLogs(panel);
            case GPU_PROFILING -> populateGpuProfiling(panel, screen);
            case RESOURCES -> populateResources(panel);
            case POWER_ACTIONS -> populatePowerActions(panel);
        }
    }

    private void populateStatus(ContentPanelWidget panel) {
        MinecraftClient mc = MinecraftClient.getInstance();

        SettingsSection runtime = panel.addSection("Runtime Status").setLinear();
        runtime.addInfo("World", mc.world == null ? "None" : mc.world.getRegistryKey().getValue().toString());
        runtime.addInfo("Player", mc.player == null ? "None" : mc.player.getBlockPos().toShortString());
        runtime.addInfo("Integrated Server", String.valueOf(DebugRuntimeDiagnostics.hasIntegratedServer(mc)));
        runtime.addInfo("Window", mc.getWindow().getFramebufferWidth() + "x" + mc.getWindow().getFramebufferHeight());
        runtime.addInfo("Debug Task", DebugRuntimeSampler.status());

        SettingsSection renderer = panel.addSection("Renderer Status").setLinear();
        renderer.addInfo("GPU Profile", compact(DebugRuntimeDiagnostics.safeNative(RendererProxy::nativeGetGpuProfile)));
        renderer.addInfo("Feature Truth", compact(DebugRuntimeDiagnostics.safeNative(RendererProxy::nativeGetFeatureTruth)));
        renderer.addInfo("VMA", compact(DebugRuntimeDiagnostics.safeNative(RendererProxy::nativeGetVmaStats)));
        renderer.addInfo("Overlay", compact(DebugRuntimeDiagnostics.safeNative(RendererProxy::nativeGetOverlayDiag)));
        renderer.addInfo("Texture Generation", String.valueOf(BlockModelBridge.getActiveTextureGeneration()));
        renderer.addInfo("Sprite Count", String.valueOf(BlockModelBridge.sortedSpriteIds.size()));

        SettingsSection options = panel.addSection("Debug Options").setLinear();
        options.addInfo("Java Logging", String.valueOf(Options.loggingEnabled));
        options.addInfo("GPU Debug Labels", String.valueOf(Options.gpuDebugLabels));
        options.addInfo("GPU Profiler Toggle", String.valueOf(DebugRuntimeSampler.isGpuProfilerEnabled()));
        options.addInfo("Displacement", Options.displacementEnabled + " / "
            + Options.displacementQualityName(Options.displacementQuality));
        options.addInfo("Frame Generation", frameGenLabel());

        SettingsSection paths = panel.addSection("Diagnostic Paths").setLinear();
        paths.addInfo("Runtime Snapshot", DebugRuntimeDiagnostics.latestRuntimeSnapshotPath().toString());
        paths.addInfo("Target Inspect", DebugInspectReporter.latestReportPath().toString());
        paths.addInfo("Debug Bundle", DebugRuntimeDiagnostics.latestBundlePath().toString());
        Path nsightManifest = DebugRuntimeSampler.lastNsightCaptureManifestPath();
        paths.addInfo("Nsight Context", nsightManifest == null ? "None" : nsightManifest.toString());
        paths.addInfo("Texture Manifest", DebugRuntimeDiagnostics.textureManifestPath().toString());
        paths.addInfo("Texture CSV", DebugRuntimeDiagnostics.TEXTURE_FULL_CSV.toString());
    }

    private void populateCaptureLogs(ContentPanelWidget panel) {
        MinecraftClient mc = MinecraftClient.getInstance();
        SettingsSection capture = panel.addSection("Capture").setLinear();
        capture.addButton(button("Capture Current Target", () -> DebugInspectReporter.captureCurrentTarget(mc)))
            .tooltip("Writes radiance/logs/debug_inspect_latest.txt for the current crosshair target.");
        capture.addRow(new KeyBindRow(
            new KeyBindButton(0, 0, KeyInputHandler.debugInspectKey),
            "Log Debug Inspect"));
        capture.addButton(button("Write Runtime Snapshot", () -> runFileAction(mc,
            () -> DebugRuntimeDiagnostics.writeRuntimeSnapshot(mc), "Runtime snapshot saved")))
            .tooltip("Writes renderer, world, option, and path state to radiance/logs.");
        capture.addButton(button("Write Debug Bundle", () -> DebugRuntimeSampler.writeDebugBundleAsync(mc)))
            .tooltip("Builds a zip with current diagnostics and key logs in the background.");
        capture.addButton(button("Write Nsight Context", () -> DebugRuntimeSampler.writeNsightCaptureContextAsync(mc)))
            .tooltip("Closes this menu, then asks DebugBridge to write an Nsight capture manifest under C:\\RadSER\\results\\nsight.");

        SettingsSection logging = panel.addSection("Logging").setLinear();
        var gameOptions = mc.options;
        SimpleOption<Boolean> javaLogging = SimpleOption.ofBoolean(
            "Java / Native File Logging", Options.loggingEnabled,
            value -> Options.setLoggingEnabled(value, true));
        logging.addToggle(javaLogging.createWidget(gameOptions))
            .tooltip("Enables Radiance Java/native diagnostic logs.");
        SimpleOption<Boolean> gpuLabels = SimpleOption.ofBoolean(
            "GPU Debug Labels", Options.gpuDebugLabels,
            value -> Options.setGpuDebugLabels(value, true));
        logging.addToggle(gpuLabels.createWidget(gameOptions))
            .tooltip("Adds GPU debug labels for external profilers.");
    }

    private void populateGpuProfiling(ContentPanelWidget panel, RadianceUnifiedScreen screen) {
        MinecraftClient mc = MinecraftClient.getInstance();
        SettingsSection profiler = panel.addSection("GPU Profiler").setLinear();
        profiler.addInfo("Status", DebugRuntimeSampler.status());
        Path last = DebugRuntimeSampler.lastGpuProfilePath();
        profiler.addInfo("Last GPU Profile", last == null ? "None" : last.toString());
        Path lastSharcProbe = DebugRuntimeSampler.lastSharcProbePath();
        profiler.addInfo("Last SHARC Probe", lastSharcProbe == null ? "None" : lastSharcProbe.toString());
        Path lastRtDirectLightProbe = DebugRuntimeSampler.lastRtDirectLightProbePath();
        profiler.addInfo("Last RT Direct-Light Probe", lastRtDirectLightProbe == null ? "None" : lastRtDirectLightProbe.toString());
        Path lastRtSweep = DebugRuntimeSampler.lastRtMainTraceSweepPath();
        profiler.addInfo("Last RT Sweep", lastRtSweep == null ? "None" : lastRtSweep.toString());
        Path lastRtFloorSweep = DebugRuntimeSampler.lastRtMainTraceFloorSweepPath();
        profiler.addInfo("Last RT Floor Sweep", lastRtFloorSweep == null ? "None" : lastRtFloorSweep.toString());
        profiler.addButton(button(
            DebugRuntimeSampler.isGpuProfilerEnabled() ? "Disable GPU Profiler" : "Enable GPU Profiler",
            () -> {
                DebugRuntimeSampler.setGpuProfilerEnabled(!DebugRuntimeSampler.isGpuProfilerEnabled(), mc);
                screen.refreshContent();
            }));
        profiler.addTwoWidgets(
            button("Capture 30 Samples", () -> {
                DebugRuntimeSampler.startGpuProfileCapture(30, mc);
                screen.refreshContent();
            }),
            button("Capture 120 Samples", () -> {
                DebugRuntimeSampler.startGpuProfileCapture(120, mc);
                screen.refreshContent();
            })).tooltip("Samples native GPU timestamp results without blocking the menu.");
        profiler.addButton(button("Run RT MainTrace Sweep", () -> {
            DebugRuntimeSampler.startRtMainTraceSweep(mc);
            screen.refreshContent();
        })).tooltip("Closes this menu, temporarily sweeps RT shader options, restores them, and writes C:\\RadSER\\results\\rt_sweeps.");
        profiler.addButton(button("Run SHARC Probe", () -> {
            DebugRuntimeSampler.startSharcProbe(mc);
            screen.refreshContent();
        })).tooltip("Closes this menu, enables SHARC temporarily, captures staged SHARC feature truth/GPU timings, restores settings, and writes C:\\RadSER\\results\\sharc_probes.");
        profiler.addButton(button("Run RT MainTrace Floor Sweep", () -> {
            DebugRuntimeSampler.startRtMainTraceFloorSweep(mc);
            screen.refreshContent();
        })).tooltip("Closes this menu, toggles transient RT.MainTrace floor diagnostics, restores them, and writes C:\\RadSER\\results\\rt_sweeps.");

        SettingsSection directLight = panel.addSection("RT Upstream / Direct Light").setLinear();
        SelectionDropdownWidget directLightBackend = new SelectionDropdownWidget(
            0, 0, 170, 20, "Backend",
            Options.DIRECT_LIGHT_BACKEND_NAMES,
            Options.directLightBackend,
            value -> {
                Options.setDirectLightBackend(value, false);
                screen.refreshContent();
            });
        directLight.addTwoWidgets(directLightBackend, button("Run Upstream Readiness", () -> {
            DebugRuntimeSampler.startRtDirectLightProbe(mc);
            screen.refreshContent();
        })).tooltip("Checks upstream RT asset/pass readiness and writes C:\\RadSER\\results\\rtxdi. Legacy remains the startup-safe path.");
        directLight.addButton(button("Run Direct-Light Ablation", () -> {
            DebugRuntimeSampler.startRtDirectLightAblationProbe(mc);
            screen.refreshContent();
        })).tooltip("Debug-only darkening probe: disables sampled direct lighting in RT.MainTrace to measure the maximum removable direct-light cost.");
        directLight.addButton(button("Run Legacy vs Upstream", () -> {
            DebugRuntimeSampler.startRtDirectLightCompare(mc);
            screen.refreshContent();
        })).tooltip("Runs Legacy and the current UpstreamReSTIR path back-to-back, restores settings, and marks the result readiness-only until the upstream executor is active.");

        SettingsSection snapshots = panel.addSection("Snapshots").setLinear();
        snapshots.addTwoWidgets(
            button("Write VMA Snapshot", () -> runFileAction(mc,
                DebugRuntimeDiagnostics::writeVmaSnapshot, "VMA snapshot saved")),
            button("Write Overlay Snapshot", () -> runFileAction(mc,
                DebugRuntimeDiagnostics::writeOverlaySnapshot, "Overlay snapshot saved")));
    }

    private void populateResources(ContentPanelWidget panel) {
        MinecraftClient mc = MinecraftClient.getInstance();
        SettingsSection textures = panel.addSection("Texture Resources").setLinear();
        textures.addInfo("Java Texture Generation", String.valueOf(BlockModelBridge.getActiveTextureGeneration()));
        textures.addInfo("Java Sprite Count", String.valueOf(BlockModelBridge.sortedSpriteIds.size()));
        textures.addInfo("Texture CSV Exists", String.valueOf(Files.exists(DebugRuntimeDiagnostics.TEXTURE_FULL_CSV)));
        textures.addButton(button("Write Texture Reload Snapshot", () -> runFileAction(mc,
            DebugRuntimeDiagnostics::writeTextureReloadSnapshot, "Texture reload snapshot saved")))
            .tooltip("Also asks native to dump C:\\RadSER\\texture_system_full.csv.");

        SettingsSection paths = panel.addSection("Copy Paths").setLinear();
        paths.addTwoWidgets(
            button("Copy Inspect Path", () -> DebugRuntimeDiagnostics.copyPathToClipboard(
                mc, DebugInspectReporter.latestReportPath())),
            button("Copy Bundle Path", () -> DebugRuntimeDiagnostics.copyPathToClipboard(
                mc, DebugRuntimeDiagnostics.latestBundlePath())));
        paths.addTwoWidgets(
            button("Copy Manifest Path", () -> DebugRuntimeDiagnostics.copyPathToClipboard(
                mc, DebugRuntimeDiagnostics.textureManifestPath())),
            button("Copy Texture CSV Path", () -> DebugRuntimeDiagnostics.copyPathToClipboard(
                mc, DebugRuntimeDiagnostics.TEXTURE_FULL_CSV)));
    }

    private void populatePowerActions(ContentPanelWidget panel) {
        MinecraftClient mc = MinecraftClient.getInstance();
        SettingsSection renderer = panel.addSection("Renderer Actions").setLinear();
        renderer.addTwoWidgets(
            button("Reset Accumulation", () -> DebugRuntimeDiagnostics.resetAccumulation(mc)),
            button("Rebuild Chunks", () -> DebugRuntimeDiagnostics.rebuildChunks(mc)))
            .tooltip("Rebuild Chunks reloads Minecraft chunk meshes and requests native BLAS rebuild.");

        SettingsSection world = panel.addSection("World Actions").setLinear();
        boolean server = DebugRuntimeDiagnostics.hasIntegratedServer(mc);
        ButtonWidget clearWeather = button("Clear Weather", () -> DebugRuntimeDiagnostics.clearWeather(mc));
        clearWeather.active = server;
        ButtonWidget noon = button("Set Noon", () -> DebugRuntimeDiagnostics.setNoon(mc));
        noon.active = server;
        world.addTwoWidgets(clearWeather, noon)
            .tooltip("Available only in an integrated single-player server.");
    }

    @Override
    public List<UnifiedSearchOverlay.SearchEntry> getSearchEntries(String nodeId, String category) {
        return switch (page) {
            case STATUS -> List.of(
                new UnifiedSearchOverlay.SearchEntry("Runtime Status", category, nodeId, false),
                new UnifiedSearchOverlay.SearchEntry("Renderer Status", category, nodeId, false),
                new UnifiedSearchOverlay.SearchEntry("Diagnostic Paths", category, nodeId, false));
            case CAPTURE_LOGS -> List.of(
                new UnifiedSearchOverlay.SearchEntry("Capture Current Target", category, nodeId, false),
                new UnifiedSearchOverlay.SearchEntry("Log Debug Inspect", category, nodeId, false),
                new UnifiedSearchOverlay.SearchEntry("Runtime Snapshot", category, nodeId, false),
                new UnifiedSearchOverlay.SearchEntry("Debug Bundle", category, nodeId, false),
                new UnifiedSearchOverlay.SearchEntry("Nsight Context", category, nodeId, false),
                new UnifiedSearchOverlay.SearchEntry("Java Logging", category, nodeId, false),
                new UnifiedSearchOverlay.SearchEntry("GPU Debug Labels", category, nodeId, false));
            case GPU_PROFILING -> List.of(
                new UnifiedSearchOverlay.SearchEntry("GPU Profiler", category, nodeId, false),
                new UnifiedSearchOverlay.SearchEntry("GPU Profile 30 Samples", category, nodeId, false),
                new UnifiedSearchOverlay.SearchEntry("GPU Profile 120 Samples", category, nodeId, false),
                new UnifiedSearchOverlay.SearchEntry("RT MainTrace Sweep", category, nodeId, false),
                new UnifiedSearchOverlay.SearchEntry("SHARC Probe", category, nodeId, false),
                new UnifiedSearchOverlay.SearchEntry("RT Upstream Readiness", category, nodeId, false),
                new UnifiedSearchOverlay.SearchEntry("RT Legacy vs Upstream", category, nodeId, false),
                new UnifiedSearchOverlay.SearchEntry("VMA Snapshot", category, nodeId, false),
                new UnifiedSearchOverlay.SearchEntry("Overlay Snapshot", category, nodeId, false));
            case RESOURCES -> List.of(
                new UnifiedSearchOverlay.SearchEntry("Texture Diagnostics", category, nodeId, false),
                new UnifiedSearchOverlay.SearchEntry("Texture Reload Snapshot", category, nodeId, false),
                new UnifiedSearchOverlay.SearchEntry("Texture Manifest", category, nodeId, false),
                new UnifiedSearchOverlay.SearchEntry("Texture CSV", category, nodeId, false));
            case POWER_ACTIONS -> List.of(
                new UnifiedSearchOverlay.SearchEntry("Reset Accumulation", category, nodeId, false),
                new UnifiedSearchOverlay.SearchEntry("Rebuild Chunks", category, nodeId, false),
                new UnifiedSearchOverlay.SearchEntry("Clear Weather", category, nodeId, false),
                new UnifiedSearchOverlay.SearchEntry("Set Noon", category, nodeId, false));
        };
    }

    private static ButtonWidget button(String label, Runnable action) {
        return ButtonWidget.builder(Text.literal(label), btn -> action.run()).width(150).build();
    }

    private static void runFileAction(MinecraftClient mc, FileAction action, String success) {
        try {
            Path path = action.run();
            DebugRuntimeDiagnostics.toast(mc, "Radiance: " + success + " (" + path.getFileName() + ")");
        } catch (Exception e) {
            DebugRuntimeDiagnostics.toast(mc, "Radiance debug action failed: " + e.getMessage());
        }
    }

    private static String frameGenLabel() {
        String mode = switch (Options.frameGenMode) {
            case 1 -> "On";
            case 2 -> "Auto";
            default -> "Off";
        };
        return mode + " / multiplier=" + Options.frameGenMultiplier;
    }

    private static String compact(String value) {
        if (value == null || value.isBlank()) return "unavailable";
        return value.length() <= 96 ? value : value.substring(0, 93) + "...";
    }

    @FunctionalInterface
    private interface FileAction {
        Path run() throws Exception;
    }
}
