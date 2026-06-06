package com.radiance.client.gui.unified.populators;

import static net.minecraft.client.option.GameOptions.getGenericValueText;

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

        ResettableSliderWidget rayBounces = new ResettableSliderWidget(0, 0, 150, 20,
            0, 32, Options.rayBounces, 16,
            v -> getGenericValueText(Text.translatable(Options.RAY_BOUNCES_KEY),
                Text.literal(Integer.toString(v))),
            v -> Options.setRayBounces(v, true));
        rayBounces.settingKey = Options.RAY_BOUNCES_KEY;
        section.addSlider(rayBounces)
              .tooltip("Maximum ray-traced light reflections per pixel.");

        SimpleOption<Boolean> simplifiedIndirect = SimpleOption.ofBoolean(
            Options.SIMPLIFIED_INDIRECT_KEY, Options.simplifiedIndirect,
            value -> Options.setSimplifiedIndirect(value, true));

        section.addToggle(simplifiedIndirect.createWidget(gameOptions))
              .tooltip("Simplified Indirect uses Lambertian instead of Disney BRDF for bounced light.");

        SimpleOption<Boolean> multiScatterGGX = SimpleOption.ofBoolean(
            Options.MULTI_SCATTER_GGX_KEY, Options.multiScatterGGX,
            value -> Options.setMultiScatterGGX(value, true));

        SelectionDropdownWidget diffuseModel = new SelectionDropdownWidget(
            0, 0, 150, 20, "Diffuse",
            new String[]{"EON", "VMF (Experimental)"},
            Options.diffuseModel == Options.DIFFUSE_MODEL_VMF ? 1 : 0,
            value -> Options.setDiffuseModel(value == 1 ? Options.DIFFUSE_MODEL_VMF : Options.DIFFUSE_MODEL_EON, true));
        section.addTwoWidgets(multiScatterGGX.createWidget(gameOptions), diffuseModel)
              .tooltip("Multi-Scatter GGX adds energy-conserving multiple bounces in microfacet BRDF. Diffuse selects the rough diffuse model.");

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

    }

    @Override
    public java.util.List<UnifiedSearchOverlay.SearchEntry> getSearchEntries(String nodeId, String category) {
        return java.util.List.of(
            new UnifiedSearchOverlay.SearchEntry("Ray Bounces", category, nodeId, true),
            new UnifiedSearchOverlay.SearchEntry("Simplified Indirect", category, nodeId, true),
            new UnifiedSearchOverlay.SearchEntry("Diffuse Model", category, nodeId, true),
            new UnifiedSearchOverlay.SearchEntry("SER Enabled", category, nodeId, true),
            new UnifiedSearchOverlay.SearchEntry("SER Hints", category, nodeId, true)
        );
    }
}
