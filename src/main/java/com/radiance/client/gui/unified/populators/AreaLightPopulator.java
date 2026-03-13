package com.radiance.client.gui.unified.populators;

import static net.minecraft.client.option.GameOptions.getGenericValueText;

import com.google.common.collect.ImmutableList;
import com.mojang.serialization.Codec;
import com.radiance.client.gui.AreaLightSettingsScreen;
import com.radiance.client.gui.LightTypeDetailScreen;
import com.radiance.client.gui.PotentialValuesBasedCallbacksNoValue;
import com.radiance.client.gui.ResettableSliderWidget;
import com.radiance.client.gui.unified.*;
import com.radiance.client.gui.unified.rows.*;
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

        String[] modeLabels = {
            Options.GLOBAL_LIGHT_MODE_AUTO_KEY,
            Options.GLOBAL_LIGHT_MODE_AREA_KEY,
            Options.GLOBAL_LIGHT_MODE_EMISSIVE_KEY
        };
        SimpleOption<Integer> globalLightMode = new SimpleOption<>(
            Options.GLOBAL_LIGHT_MODE_KEY,
            SimpleOption.emptyTooltip(),
            (optionText, value) -> getGenericValueText(optionText,
                Text.translatable(modeLabels[Math.min(value, modeLabels.length - 1)])),
            new SimpleOption.ValidatingIntSliderCallbacks(0, 2),
            Codec.intRange(0, 2),
            Options.globalLightMode,
            value -> Options.setGlobalLightMode(value, true));

        enableSection.addTwoWidgets(
            areaLightsEnabled.createWidget(gameOptions),
            globalLightMode.createWidget(gameOptions));

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

        ResettableSliderWidget shadowSlider = new ResettableSliderWidget(0, 0, 150, 20,
            0, 200, Options.shadowSoftnessPercent, 100,
            v -> getGenericValueText(Text.translatable(Options.AREA_LIGHT_SHADOW_SOFTNESS_KEY), Text.literal(v + "%")),
            v -> Options.setShadowSoftnessPercent(v, true));

        SimpleOption<Boolean> restirToggle = SimpleOption.ofBoolean(
            "options.video.area_light.restir", Options.restirEnabled,
            value -> Options.setRestirEnabled(value, true));
        ClickableWidget restirWidget = restirToggle.createWidget(MinecraftClient.getInstance().options);
        global.addTwoWidgets(shadowSlider, restirWidget);

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
                v -> Options.setRestirTemporalMClamp(v, true)));

        restir.addTwoSliders(
            new ResettableSliderWidget(0, 0, 150, 20,
                10, 200, Options.restirWClamp, 30,
                v -> getGenericValueText(Text.translatable(Options.RESTIR_W_CLAMP_KEY), Text.literal(String.valueOf(v))),
                v -> Options.setRestirWClamp(v, true)),
            null);

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
            bounceEnabled.createWidget(MinecraftClient.getInstance().options));

        // Per-Block Controls
        SettingsSection perBlock = panel.addSection("options.video.area_light.per_block_category");
        String[] lightTypeKeys = AreaLightSettingsScreen.LIGHT_TYPE_KEYS;
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

    private ClickableWidget createBlockSettingsWidget(int lightTypeId, String translationKey, RadianceUnifiedScreen screen) {
        SimpleOption<Boolean> blockSettings = new SimpleOption<>(
            translationKey,
            SimpleOption.emptyTooltip(),
            (optionText, value) -> Text.translatable(translationKey).append(Text.literal("...")),
            new PotentialValuesBasedCallbacksNoValue<>(
                ImmutableList.of(Boolean.TRUE, Boolean.FALSE), Codec.BOOL),
            false,
            value -> MinecraftClient.getInstance().setScreen(
                new LightTypeDetailScreen(screen, lightTypeId, translationKey)));
        return blockSettings.createWidget(MinecraftClient.getInstance().options);
    }
}
