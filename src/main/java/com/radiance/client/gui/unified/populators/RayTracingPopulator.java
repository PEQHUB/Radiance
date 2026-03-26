package com.radiance.client.gui.unified.populators;

import static net.minecraft.client.option.GameOptions.getGenericValueText;

import com.mojang.serialization.Codec;
import com.radiance.client.gui.ResettableSliderWidget;
import com.radiance.client.gui.SelectionDropdownWidget;
import com.radiance.client.gui.unified.*;
import com.radiance.client.option.Options;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.SimpleOption;
import net.minecraft.text.Text;

public class RayTracingPopulator implements ContentPopulator {
    @Override
    public void populate(ContentPanelWidget panel, RadianceUnifiedScreen screen) {
        var gameOptions = MinecraftClient.getInstance().options;

        SettingsSection section = panel.addSection(Options.CATEGORY_RAY_TRACING);

        SimpleOption<Integer> rayBounces = new SimpleOption<>(
            Options.RAY_BOUNCES_KEY,
            SimpleOption.emptyTooltip(),
            (optionText, value) -> getGenericValueText(optionText, Text.literal(Integer.toString(value))),
            new SimpleOption.ValidatingIntSliderCallbacks(0, 32),
            Codec.intRange(0, 32),
            Options.rayBounces,
            value -> Options.setRayBounces(value, true));

        SimpleOption<Boolean> ommEnabled = SimpleOption.ofBoolean(
            Options.OMM_ENABLED_KEY, Options.ommEnabled,
            value -> {
                Options.setOMMEnabled(value, true);
                screen.refreshContent();
            });
        section.addTwoWidgets(rayBounces.createWidget(gameOptions), ommEnabled.createWidget(gameOptions))
              .tooltip("Ray Bounces = max light reflections per pixel. OMM = opacity micromaps for faster alpha-tested geometry.");

        SimpleOption<Boolean> simplifiedIndirect = SimpleOption.ofBoolean(
            Options.SIMPLIFIED_INDIRECT_KEY, Options.simplifiedIndirect,
            value -> Options.setSimplifiedIndirect(value, true));

        SimpleOption<Boolean> greedyMeshing = SimpleOption.ofBoolean(
            Options.GREEDY_MESHING_KEY, Options.greedyMeshingEnabled,
            value -> Options.setGreedyMeshingEnabled(value, true));

        if (Options.ommEnabled) {
            // OMM Baker Level only visible when OMM is enabled
            SelectionDropdownWidget ommBakerLevel = new SelectionDropdownWidget(
                0, 0, 150, 20, "OMM Baker Level",
                new String[]{"1", "2", "3", "4", "5", "6", "7", "8"},
                Options.ommBakerLevel - 1,
                value -> Options.setOMMBakerLevel(value + 1, true));
            section.addTwoWidgets(ommBakerLevel, simplifiedIndirect.createWidget(gameOptions))
                  .tooltip("Baker Level = OMM resolution (higher = more accurate transparency). Simplified Indirect uses Lambertian instead of Disney BRDF for bounced light.");
        } else {
            section.addToggle(simplifiedIndirect.createWidget(gameOptions))
                  .tooltip("Simplified Indirect uses Lambertian instead of Disney BRDF for bounced light.");
        }

        section.addToggle(greedyMeshing.createWidget(gameOptions))
              .tooltip("Merges adjacent block faces with identical materials into larger quads, reducing triangle count by 50-70%. Rebuilds chunks on toggle.");

        SimpleOption<Boolean> multiScatterGGX = SimpleOption.ofBoolean(
            Options.MULTI_SCATTER_GGX_KEY, Options.multiScatterGGX,
            value -> Options.setMultiScatterGGX(value, true));

        SimpleOption<Boolean> eonDiffuse = SimpleOption.ofBoolean(
            Options.EON_DIFFUSE_KEY, Options.eonDiffuse,
            value -> Options.setEonDiffuse(value, true));
        section.addTwoWidgets(multiScatterGGX.createWidget(gameOptions), eonDiffuse.createWidget(gameOptions))
              .tooltip("Multi-Scatter GGX adds energy-conserving multiple bounces in microfacet BRDF. EON uses the Estevez-Kulla-Conty diffuse model.");

        SimpleOption<Boolean> noiseLOD = SimpleOption.ofBoolean(
            "options.video.noise_lod", Options.noiseLOD,
            value -> Options.setNoiseLOD(value, true));
        section.addToggle(noiseLOD.createWidget(gameOptions))
              .tooltip("Reduces procedural noise quality at distance for better performance.");

        // SER (Shader Execution Reordering)
        SimpleOption<Boolean> serEnabled = SimpleOption.ofBoolean(
            "options.video.ser_enabled", Options.serEnabled,
            value -> {
                Options.setSEREnabled(value, true);
                screen.refreshContent();
            });
        if (Options.serEnabled) {
            // SER Hints only visible when SER is enabled
            SimpleOption<Boolean> serHints = SimpleOption.ofBoolean(
                "options.video.ser_hints", Options.serHintsEnabled,
                value -> Options.setSERHintsEnabled(value, true));
            section.addTwoWidgets(serEnabled.createWidget(gameOptions), serHints.createWidget(gameOptions))
                  .tooltip("SER reorders shader invocations for better GPU occupancy. Hints guide the reorder heuristic.");
        } else {
            section.addToggle(serEnabled.createWidget(gameOptions))
                  .tooltip("SER reorders shader invocations for better GPU occupancy.");
        }

        // Displacement (global on/off + LOD)
        SettingsSection disp = panel.addSection(Text.literal("Displacement"));
        SimpleOption<Boolean> dispToggle = SimpleOption.ofBoolean(
            "options.video.displacement_enabled",
            Options.displacementQuality >= 1,
            value -> {
                Options.setDisplacementQuality(value ? 2 : 0, true);
                screen.refreshContent();
            });
        disp.addToggle(dispToggle.createWidget(gameOptions))
              .tooltip("Enable per-block geometric displacement. Set depth and method per block in the Materials screen.");

        if (Options.displacementQuality >= 1) {
            disp.addTwoSliders(
                new ResettableSliderWidget(0, 0, 150, 20,
                    2, 32, Options.tessMaxLevel, 16,
                    v -> getGenericValueText(Text.literal("Tess Level"), Text.literal(Integer.toString(v))),
                    v -> Options.setTessMaxLevel(v, true)),
                new ResettableSliderWidget(0, 0, 150, 20,
                    8, 256, Options.tessNearDist, 32,
                    v -> getGenericValueText(Text.literal("Near Dist"), Text.literal(v + " blocks")),
                    v -> Options.setTessNearDist(v, true)))
                  .tooltip("Max tessellation grid NxN. Near distance = full-quality radius.");

            disp.addTwoSliders(
                new ResettableSliderWidget(0, 0, 150, 20,
                    16, 384, Options.tessMidDist, 96,
                    v -> getGenericValueText(Text.literal("Mid Dist"), Text.literal(v + " blocks")),
                    v -> Options.setTessMidDist(v, true)),
                new ResettableSliderWidget(0, 0, 150, 20,
                    32, 512, Options.tessFarDist, 192,
                    v -> getGenericValueText(Text.literal("Far Dist"), Text.literal(v + " blocks")),
                    v -> Options.setTessFarDist(v, true)))
                  .tooltip("Mid = half quality. Far = quarter quality. Beyond = minimal.");
        }
    }

    @Override
    public java.util.List<UnifiedSearchOverlay.SearchEntry> getSearchEntries(String nodeId, String category) {
        return java.util.List.of(
            new UnifiedSearchOverlay.SearchEntry("Ray Bounces", category, nodeId, true),
            new UnifiedSearchOverlay.SearchEntry("OMM Enabled", category, nodeId, true),
            new UnifiedSearchOverlay.SearchEntry("OMM Baker Level", category, nodeId, true),
            new UnifiedSearchOverlay.SearchEntry("Simplified Indirect", category, nodeId, true),
            new UnifiedSearchOverlay.SearchEntry("Greedy Meshing", category, nodeId, true),
            new UnifiedSearchOverlay.SearchEntry("Noise LOD", category, nodeId, true),
            new UnifiedSearchOverlay.SearchEntry("SER Enabled", category, nodeId, true),
            new UnifiedSearchOverlay.SearchEntry("SER Hints", category, nodeId, true),
            new UnifiedSearchOverlay.SearchEntry("Displacement", category, nodeId, true),
            new UnifiedSearchOverlay.SearchEntry("Parallax Mapping", category, nodeId, true),
            new UnifiedSearchOverlay.SearchEntry("POM Height Scale", category, nodeId, true),
            new UnifiedSearchOverlay.SearchEntry("POM Steps", category, nodeId, true)
        );
    }
}
