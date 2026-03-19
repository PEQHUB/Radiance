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
            value -> Options.setOMMEnabled(value, true));
        section.addTwoWidgets(rayBounces.createWidget(gameOptions), ommEnabled.createWidget(gameOptions))
              .tooltip("Ray Bounces = max light reflections per pixel. OMM = opacity micromaps for faster alpha-tested geometry.");

        SelectionDropdownWidget ommBakerLevel = new SelectionDropdownWidget(
            0, 0, 150, 20, "OMM Baker Level",
            new String[]{"1", "2", "3", "4", "5", "6", "7", "8"},
            Options.ommBakerLevel - 1,
            value -> Options.setOMMBakerLevel(value + 1, true));

        SimpleOption<Boolean> simplifiedIndirect = SimpleOption.ofBoolean(
            Options.SIMPLIFIED_INDIRECT_KEY, Options.simplifiedIndirect,
            value -> Options.setSimplifiedIndirect(value, true));
        section.addTwoWidgets(ommBakerLevel, simplifiedIndirect.createWidget(gameOptions))
              .tooltip("Baker Level = OMM resolution (higher = more accurate transparency). Simplified Indirect uses Lambertian instead of Disney BRDF for bounced light.");

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
            value -> Options.setSEREnabled(value, true));
        SimpleOption<Boolean> serHints = SimpleOption.ofBoolean(
            "options.video.ser_hints", Options.serHintsEnabled,
            value -> Options.setSERHintsEnabled(value, true));
        section.addTwoWidgets(serEnabled.createWidget(gameOptions), serHints.createWidget(gameOptions))
              .tooltip("SER reorders shader invocations for better GPU occupancy. Hints guide the reorder heuristic.");

        // Displacement
        SettingsSection disp = panel.addSection(Text.literal("Displacement"));
        SelectionDropdownWidget dispQuality = new SelectionDropdownWidget(
            0, 0, 150, 20, "Quality",
            new String[]{"Off", "Intersection DDA", "Micro-Tessellation", "CLAS (RTX 5090+)"},
            Options.displacementQuality,
            value -> Options.setDisplacementQuality(value, true));
        disp.addTwoWidgets(dispQuality, null);
        disp.tooltip("Real geometry displacement. Replaces POM with correct RT silhouettes, shadows, and reflections.");

        // POM (Parallax Occlusion Mapping)
        SettingsSection pom = panel.addSection(Text.literal("Parallax Mapping"));
        SimpleOption<Boolean> pomToggle = SimpleOption.ofBoolean(
            "options.video.pom_enabled", Options.pomEnabled,
            value -> {
                Options.setPOMEnabled(value, true);
                screen.refreshContent();
            });
        pom.addToggle(pomToggle.createWidget(gameOptions))
              .tooltip("Adds depth to textured surfaces using ray-marched height maps. Disabled by default (uses intersection shaders).");

        if (Options.pomEnabled) {
            pom.addTwoSliders(
                new ResettableSliderWidget(0, 0, 150, 20,
                    1, 50, Options.pomHeightScalePercent, 5,
                    v -> getGenericValueText(Text.literal("Height Scale"), Text.literal(v + "%")),
                    v -> Options.setPOMHeightScalePercent(v, true)),
                new ResettableSliderWidget(0, 0, 150, 20,
                    8, 512, Options.pomSteps, 64,
                    v -> getGenericValueText(Text.literal("Steps"), Text.literal(Integer.toString(v))),
                    v -> Options.setPOMSteps(v, true)))
                  .tooltip("Height Scale = depth intensity. Steps = ray-march iterations (more = sharper but slower).");

            SelectionDropdownWidget pomRefinement = new SelectionDropdownWidget(
                0, 0, 150, 20, "Refinement",
                new String[]{"0", "1", "2", "3", "4", "5", "6", "7", "8"},
                Options.pomRefinement,
                value -> Options.setPOMRefinement(value, true));
            pom.addTwoWidgets(pomRefinement, null);
            pom.tooltip("Binary search passes after ray march — more = sharper silhouettes but slower.");

            pom.addSlider(new ResettableSliderWidget(0, 0, 150, 20,
                    8, 256, Options.pomFadeDistance, 64,
                    v -> getGenericValueText(Text.literal("Fade Distance"), Text.literal(v + " blocks")),
                    v -> Options.setPOMFadeDistance(v, true)));
            pom.tooltip("Distance where POM fades to flat textures.");
        }
    }

    @Override
    public java.util.List<UnifiedSearchOverlay.SearchEntry> getSearchEntries(String nodeId, String category) {
        return java.util.List.of(
            new UnifiedSearchOverlay.SearchEntry("Ray Bounces", category, nodeId, true),
            new UnifiedSearchOverlay.SearchEntry("OMM Enabled", category, nodeId, true),
            new UnifiedSearchOverlay.SearchEntry("OMM Baker Level", category, nodeId, true),
            new UnifiedSearchOverlay.SearchEntry("Simplified Indirect", category, nodeId, true),
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
