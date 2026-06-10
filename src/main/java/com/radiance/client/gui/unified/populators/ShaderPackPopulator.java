package com.radiance.client.gui.unified.populators;

import com.radiance.client.debug.DebugRuntimeDiagnostics;
import com.radiance.client.gui.ShaderPackSettingsScreen;
import com.radiance.client.gui.SelectionDropdownWidget;
import com.radiance.client.gui.unified.ContentPanelWidget;
import com.radiance.client.gui.unified.ContentPopulator;
import com.radiance.client.gui.unified.RadianceUnifiedScreen;
import com.radiance.client.gui.unified.SettingsSection;
import com.radiance.client.gui.unified.UnifiedSearchOverlay;
import com.radiance.client.option.Options;
import com.radiance.client.pipeline.Pipeline;
import com.radiance.client.proxy.vulkan.RendererProxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

public class ShaderPackPopulator implements ContentPopulator {

    @Override
    public void populate(ContentPanelWidget panel, RadianceUnifiedScreen screen) {
        List<Pipeline.ShaderPackChoice> packs = new ArrayList<>(Pipeline.getAvailableShaderPacks());
        String activePath = activePath();
        int selected = selectedIndex(packs, activePath);
        if (!packs.isEmpty() && selected < 0) {
            packs.add(new Pipeline.ShaderPackChoice("active:" + activePath,
                Text.translatable("options.video.shader_pack.current_path", activePath).getString(),
                activePath));
            selected = packs.size() - 1;
        }

        SettingsSection selection = panel.addSection(Options.CATEGORY_SHADER_PACKS);

        if (packs.isEmpty()) {
            selection.addInfo(tr("options.video.shader_pack.available_packs"),
                tr("options.video.shader_pack.none_found"));
            selection.addInfo(tr("options.video.shader_pack.expected_builtin"),
                Pipeline.VANILLA_RAY_TRACING_SHADER_PACK_PATH);
        } else {
            String[] labels = packs.stream().map(Pipeline.ShaderPackChoice::displayName).toArray(String[]::new);
            SelectionDropdownWidget dropdown = new SelectionDropdownWidget(
                0, 0, 150, 20, tr("options.video.shader_pack"), labels, selected,
                value -> {
                    Options.setShaderPackPath(packs.get(value).relativePath(), true);
                    screen.refreshContent();
                });
            selection.addTwoWidgets(dropdown, null)
                .tooltip("Built-in packs are listed first. External Radiance shader packs are read from the Minecraft shaderpacks folder.");
            Pipeline.ShaderPackChoice active = packs.get(selected);
            selection.addInfo(tr("options.video.shader_pack.selected_path"), active.relativePath());
        }

        selection.addButton(ButtonWidget.builder(Text.translatable("options.video.shader_pack.settings"),
            button -> MinecraftClient.getInstance().setScreen(new ShaderPackSettingsScreen(screen)))
            .width(150).build());
        selection.tooltip("Opens all editable attributes exposed by the active shader pack.");
        selection.addButton(ButtonWidget.builder(Text.translatable("options.video.shader_pack.reload"),
            button -> screen.refreshContent()).width(150).build());
        selection.tooltip("Reloads the shader-pack list and refreshes shader-pack status.");
        selection.addInfo(tr("options.video.shader_pack.runtime_status"), runtimeStatus(activePath));
        selection.tooltip("Shows whether the active shader pack is running through the pack executor, partial adapter, or fallback path.");
    }

    private static int selectedIndex(List<Pipeline.ShaderPackChoice> packs, String activePath) {
        if (packs == null || packs.isEmpty()) {
            return -1;
        }
        String normalizedActive = normalize(activePath);
        for (int i = 0; i < packs.size(); i++) {
            if (normalize(packs.get(i).relativePath()).equals(normalizedActive)) {
                return i;
            }
        }
        return -1;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.replace('\\', '/').trim();
    }

    private static String activePath() {
        String path = Pipeline.getActiveRayTracingShaderPackPath();
        return path == null || path.isBlank()
            ? Pipeline.VANILLA_RAY_TRACING_SHADER_PACK_PATH
            : path.trim();
    }

    private static String runtimeStatus(String path) {
        String nativeStatus = nativeRuntimeStatus();
        if (!nativeStatus.isBlank()) {
            return nativeStatus;
        }
        if (path == null || path.isBlank()) {
            path = Pipeline.VANILLA_RAY_TRACING_SHADER_PACK_PATH;
        }
        try {
            Path candidate = Path.of(path);
            if (!candidate.isAbsolute() && com.radiance.client.RadianceClient.radianceDir != null) {
                candidate = com.radiance.client.RadianceClient.radianceDir.resolve(candidate);
            }
            return Files.exists(candidate)
                ? tr("options.video.shader_pack.status.ready")
                : tr("options.video.shader_pack.status.missing");
        } catch (Exception e) {
            return tr("options.video.shader_pack.status.invalid");
        }
    }

    private static String nativeRuntimeStatus() {
        String raw = DebugRuntimeDiagnostics.safeNative(RendererProxy::nativeGetFeatureTruth);
        String backendStatus = featureField(raw, "shaderPackBackendStatus");
        String runtimeStatus = featureField(raw, "shaderPackRuntimeStatus");
        String rawLower = raw == null ? "" : raw.toLowerCase(Locale.ROOT);
        String statusValues = (backendStatus + " " + runtimeStatus).toLowerCase(Locale.ROOT);
        if (rawLower.contains("pack_executor")) {
            return tr("options.video.shader_pack.status.pack_executor");
        }
        if (rawLower.contains("partial_adapter") || statusValues.contains("adapter_partial")) {
            return tr("options.video.shader_pack.status.partial_adapter");
        }
        if (rawLower.contains("fixed_fallback")
            || "fallback".equals(runtimeStatus)
            || backendStatus.startsWith("fallback")) {
            return tr("options.video.shader_pack.status.fixed_fallback");
        }
        return "";
    }

    private static String featureField(String raw, String key) {
        if (raw == null || key == null || key.isBlank()) {
            return "";
        }
        String prefix = key + ":";
        for (String part : raw.split(",")) {
            if (part.startsWith(prefix)) {
                return part.substring(prefix.length());
            }
        }
        return "";
    }

    private static String tr(String key) {
        return Text.translatable(key).getString();
    }

    @Override
    public List<UnifiedSearchOverlay.SearchEntry> getSearchEntries(String nodeId, String category) {
        return List.of(
            new UnifiedSearchOverlay.SearchEntry("Shader Pack", category, nodeId, true),
            new UnifiedSearchOverlay.SearchEntry("Pack Settings", category, nodeId, true),
            new UnifiedSearchOverlay.SearchEntry("Reload Packs", category, nodeId, true),
            new UnifiedSearchOverlay.SearchEntry("Shader Pack Status", category, nodeId, false),
            new UnifiedSearchOverlay.SearchEntry("Volumetric Clouds", category, nodeId, false),
            new UnifiedSearchOverlay.SearchEntry("Water Surface", category, nodeId, false),
            new UnifiedSearchOverlay.SearchEntry("Water Caustics", category, nodeId, false)
        );
    }
}
