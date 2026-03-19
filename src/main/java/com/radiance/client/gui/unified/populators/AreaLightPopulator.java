package com.radiance.client.gui.unified.populators;

import static net.minecraft.client.option.GameOptions.getGenericValueText;

import com.google.common.collect.ImmutableList;
import com.mojang.serialization.Codec;
import com.radiance.client.gui.LightTypeDetailScreen;
import com.radiance.client.gui.PotentialValuesBasedCallbacksNoValue;
import com.radiance.client.gui.ResettableSliderWidget;
import com.radiance.client.gui.SelectionDropdownWidget;
import com.radiance.client.gui.unified.*;
import com.radiance.client.option.Options;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.option.SimpleOption;
import net.minecraft.text.Text;

public class AreaLightPopulator implements ContentPopulator {
    @Override
    public void populate(ContentPanelWidget panel, RadianceUnifiedScreen screen) {
        var gameOptions = MinecraftClient.getInstance().options;

        // Enable & Mode
        SettingsSection enableSection = panel.addSection("options.video.area_light.global_category");

        SimpleOption<Boolean> areaLightsEnabled = SimpleOption.ofBoolean(
            Options.AREA_LIGHTS_ENABLED_KEY, Options.areaLightsEnabled,
            value -> Options.setAreaLightsEnabled(value, true));

        SelectionDropdownWidget globalLightMode = new SelectionDropdownWidget(
            0, 0, 150, 20, "Light Mode",
            new String[]{"Auto", "Area Lights", "Emissive"},
            Options.globalLightMode, value -> Options.setGlobalLightMode(value, true));

        enableSection.addTwoWidgets(
            areaLightsEnabled.createWidget(gameOptions),
            globalLightMode);

        // Global Controls
        SettingsSection global = enableSection;

        global.addTwoSliders(
            new ResettableSliderWidget(0, 0, 150, 20,
                0, 500, Options.areaLightIntensityPercent, 100,
                v -> getGenericValueText(Text.translatable(Options.AREA_LIGHT_INTENSITY_KEY), Text.literal(v + "%")),
                v -> Options.setAreaLightIntensityPercent(v, true)),
            new ResettableSliderWidget(0, 0, 150, 20,
                8, 512, Options.areaLightRange, 128,
                v -> getGenericValueText(Text.translatable(Options.AREA_LIGHT_RANGE_KEY), Text.literal(v + " blocks")),
                v -> Options.setAreaLightRange(v, true)));

        global.addSlider(new ResettableSliderWidget(0, 0, 150, 20,
            0, 200, Options.shadowSoftnessPercent, 100,
            v -> getGenericValueText(Text.translatable(Options.AREA_LIGHT_SHADOW_SOFTNESS_KEY), Text.literal(v + "%")),
            v -> Options.setShadowSoftnessPercent(v, true)));

        SimpleOption<Boolean> restirToggle = SimpleOption.ofBoolean(
            "options.video.area_light.restir", Options.restirEnabled,
            value -> Options.setRestirEnabled(value, true));
        global.addToggle(restirToggle.createWidget(MinecraftClient.getInstance().options));

        // ReSTIR Tuning
        SettingsSection restir = panel.addSection(Options.CATEGORY_RESTIR);

        restir.addTwoSliders(
            new ResettableSliderWidget(0, 0, 150, 20,
                8, 64, Options.restirCandidates, 64,
                v -> getGenericValueText(Text.translatable(Options.RESTIR_CANDIDATES_KEY), Text.literal(String.valueOf(v))),
                v -> Options.setRestirCandidates(v, true)),
            new ResettableSliderWidget(0, 0, 150, 20,
                5, 50, Options.restirTemporalMClamp, 20,
                v -> getGenericValueText(Text.translatable(Options.RESTIR_TEMPORAL_M_CLAMP_KEY), Text.literal(String.valueOf(v))),
                v -> Options.setRestirTemporalMClamp(v, true)))
              .tooltip("RIS Candidates = lights evaluated per pixel for initial selection. M Clamp limits temporal history to prevent stale reservoirs.");

        restir.addTwoSliders(
            new ResettableSliderWidget(0, 0, 150, 20,
                10, 200, Options.restirWClamp, 30,
                v -> getGenericValueText(Text.translatable(Options.RESTIR_W_CLAMP_KEY), Text.literal(String.valueOf(v))),
                v -> Options.setRestirWClamp(v, true)),
            null)
              .tooltip("W Clamp limits the maximum importance weight. Lower = less noise but more bias.");

        // ReSTIR Performance
        SettingsSection perf = panel.addSection(Options.CATEGORY_RESTIR_PERFORMANCE);
        SimpleOption<Boolean> simplifiedBRDF = SimpleOption.ofBoolean(
            Options.RESTIR_SIMPLIFIED_BRDF_KEY, Options.restirSimplifiedBRDF,
            value -> Options.setRestirSimplifiedBRDF(value, true));
        SimpleOption<Boolean> bounceEnabled = SimpleOption.ofBoolean(
            Options.RESTIR_BOUNCE_ENABLED_KEY, Options.restirBounceEnabled,
            value -> Options.setRestirBounceEnabled(value, true));
        perf.addTwoWidgets(
            simplifiedBRDF.createWidget(MinecraftClient.getInstance().options),
            bounceEnabled.createWidget(MinecraftClient.getInstance().options))
              .tooltip("Simplified BRDF uses Lambertian instead of Disney for area light evaluation. Bounce enables ReSTIR on indirect bounces.");

        // Per-Block Controls
        SettingsSection perBlock = panel.addSection("options.video.area_light.per_block_category");
        String[] lightTypeKeys = Options.LIGHT_TYPE_KEYS;
        int count = Math.min(Options.AREA_LIGHT_TYPE_COUNT, lightTypeKeys.length);
        java.util.List<Integer> visibleTypes = new java.util.ArrayList<>();
        for (int i = 0; i < count; i++) {
            if (lightTypeKeys[i] != null) visibleTypes.add(i);
        }
        for (int j = 0; j < visibleTypes.size(); j += 2) {
            int leftId = visibleTypes.get(j);
            ClickableWidget leftBtn = createBlockSettingsWidget(leftId, lightTypeKeys[leftId], screen);
            ClickableWidget rightBtn = null;
            if (j + 1 < visibleTypes.size()) {
                int rightId = visibleTypes.get(j + 1);
                rightBtn = createBlockSettingsWidget(rightId, lightTypeKeys[rightId], screen);
            }
            perBlock.addTwoWidgets(leftBtn, rightBtn);
        }
    }

    @Override
    public java.util.List<UnifiedSearchOverlay.SearchEntry> getSearchEntries(String nodeId, String category) {
        return java.util.List.of(
            new UnifiedSearchOverlay.SearchEntry("Area Lights Enabled", category, nodeId, false),
            new UnifiedSearchOverlay.SearchEntry("Light Mode", category, nodeId, false),
            new UnifiedSearchOverlay.SearchEntry("Area Light Intensity", category, nodeId, false),
            new UnifiedSearchOverlay.SearchEntry("Area Light Range", category, nodeId, false),
            new UnifiedSearchOverlay.SearchEntry("Shadow Softness", category, nodeId, true),
            new UnifiedSearchOverlay.SearchEntry("ReSTIR Enabled", category, nodeId, true),
            new UnifiedSearchOverlay.SearchEntry("ReSTIR Candidates", category, nodeId, true),
            new UnifiedSearchOverlay.SearchEntry("ReSTIR Temporal M Clamp", category, nodeId, true),
            new UnifiedSearchOverlay.SearchEntry("ReSTIR W Clamp", category, nodeId, true)
        );
    }

    private ClickableWidget createBlockSettingsWidget(int lightTypeId, String translationKey, RadianceUnifiedScreen screen) {
        SimpleOption<Boolean> blockSettings = new SimpleOption<>(
            translationKey,
            SimpleOption.emptyTooltip(),
            (optionText, value) -> Text.translatable(translationKey).append(Text.literal("...")),
            new PotentialValuesBasedCallbacksNoValue<>(
                ImmutableList.of(Boolean.TRUE, Boolean.FALSE), Codec.BOOL),
            false,
            value -> screen.showOverlay(
                new LightTypeDetailScreen(screen, lightTypeId, translationKey)));
        return blockSettings.createWidget(MinecraftClient.getInstance().options);
    }
}
