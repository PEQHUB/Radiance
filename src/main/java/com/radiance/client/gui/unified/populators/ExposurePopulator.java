package com.radiance.client.gui.unified.populators;

import static net.minecraft.client.option.GameOptions.getGenericValueText;

import com.radiance.client.gui.ResettableSliderWidget;
import com.radiance.client.gui.unified.*;
import com.radiance.client.option.Options;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.SimpleOption;
import net.minecraft.text.Text;

public class ExposurePopulator implements ContentPopulator {

    private static final int EV100_SLIDER_OFFSET = 40;

    @Override
    public void populate(ContentPanelWidget panel, RadianceUnifiedScreen screen) {
        SettingsSection section = panel.addSection("options.video.category.exposure");

        // Manual Exposure toggle
        SimpleOption<Boolean> manualExposure = SimpleOption.ofBoolean(
            Options.MANUAL_EXPOSURE_ENABLED_KEY,
            Options.manualExposureEnabled,
            value -> {
                Options.setManualExposureEnabled(value, true);
                screen.refreshContent();
            });
        section.addToggle(manualExposure.createWidget(MinecraftClient.getInstance().options));
        section.tooltip("Overrides auto-exposure with a fixed EV value for consistent brightness.");

        if (Options.manualExposureEnabled) {
            int sliderValue = Options.manualExposureEV100Tenths + EV100_SLIDER_OFFSET;
            int sliderDefault = 150 + EV100_SLIDER_OFFSET;
            section.addSlider(new ResettableSliderWidget(0, 0, 150, 20,
                0, 240, sliderValue, sliderDefault,
                v -> getGenericValueText(Text.translatable(Options.MANUAL_EXPOSURE_KEY),
                    Text.literal(String.format("EV %.1f", (v - EV100_SLIDER_OFFSET) / 10.0))),
                v -> Options.setManualExposureEV100Tenths(v - EV100_SLIDER_OFFSET, true)));
            section.tooltip("EV 0 = sunny daylight. Negative = darker. Positive = brighter.");
            return;
        }

        // Eye Adaptation section
        SettingsSection adapt = panel.addSection(Text.literal("Eye Adaptation"));

        // Row 1: Bright Adapt | Dark Adapt
        adapt.addTwoSliders(
            new ResettableSliderWidget(0, 0, 150, 20,
                1, 50, Options.brightAdaptSpeedTenths, 5,
                v -> getGenericValueText(Text.translatable(Options.BRIGHT_ADAPT_SPEED_KEY),
                    Text.literal(String.format("%.1fs", v / 10.0))),
                v -> Options.setBrightAdaptSpeedTenths(v, true)),
            new ResettableSliderWidget(0, 0, 150, 20,
                5, 100, Options.darkAdaptSpeedTenths, 20,
                v -> getGenericValueText(Text.translatable(Options.DARK_ADAPT_SPEED_KEY),
                    Text.literal(String.format("%.1fs", v / 10.0))),
                v -> Options.setDarkAdaptSpeedTenths(v, true)));
        adapt.tooltip("How quickly exposure adapts. Bright adapt = entering sunlight. Dark adapt = entering caves.");

        // Row 2: Scene Cut | Center Weight
        adapt.addTwoSliders(
            new ResettableSliderWidget(0, 0, 150, 20,
                20, 100, Options.sceneChangeThresholdTenths, 50,
                v -> getGenericValueText(Text.translatable(Options.SCENE_CHANGE_THRESHOLD_KEY),
                    Text.literal(String.format("%.1f EV", v / 10.0))),
                v -> Options.setSceneChangeThresholdTenths(v, true)),
            new ResettableSliderWidget(0, 0, 150, 20,
                0, 100, Options.centerWeightPercent, 0,
                v -> getGenericValueText(Text.translatable(Options.CENTER_WEIGHT_STRENGTH_KEY),
                    Text.literal(v + "%")),
                v -> Options.setCenterWeightPercent(v, true)));
        adapt.tooltip("Scene Cut: EV jump for instant snap. Center Weight: how much center of screen influences metering.");

        // Row 3: Highlight Weight | Compensation
        adapt.addTwoSliders(
            new ResettableSliderWidget(0, 0, 150, 20,
                0, 100, Options.highlightWeightPercent, 50,
                v -> getGenericValueText(Text.translatable(Options.HIGHLIGHT_WEIGHT_KEY),
                    Text.literal(v + "%")),
                v -> Options.setHighlightWeight(v, true)),
            new ResettableSliderWidget(0, 0, 150, 20,
                0, 60, Options.exposureCompensation + 30, 0 + 30,
                v -> getGenericValueText(Text.translatable(Options.EXPOSURE_COMPENSATION_KEY),
                    Text.literal(String.format("%+.1f EV", (v - 30) / 10.0))),
                v -> Options.setExposureCompensation(v - 30, true)));
        adapt.tooltip("Highlight Weight: bright pixels influence metering more (reduces highlight blowout). Compensation: manual EV offset.");

        // Row 4: Middle Grey
        adapt.addSlider(new ResettableSliderWidget(0, 0, 150, 20,
                1, 50, Options.middleGreyPercent, 18,
                v -> getGenericValueText(Text.translatable(Options.MIDDLE_GREY_KEY),
                    Text.literal(String.format("%.2f", v / 100.0))),
                v -> Options.setMiddleGrey(v, true)));
        adapt.tooltip("Target luminance for 18% grey.");
    }

    @Override
    public java.util.List<UnifiedSearchOverlay.SearchEntry> getSearchEntries(String nodeId, String category) {
        return java.util.List.of(
            new UnifiedSearchOverlay.SearchEntry("Manual Exposure", category, nodeId, false),
            new UnifiedSearchOverlay.SearchEntry("Exposure EV", category, nodeId, false),
            new UnifiedSearchOverlay.SearchEntry("Bright Adapt Speed", category, nodeId, false),
            new UnifiedSearchOverlay.SearchEntry("Dark Adapt Speed", category, nodeId, false),
            new UnifiedSearchOverlay.SearchEntry("Scene Cut Threshold", category, nodeId, false),
            new UnifiedSearchOverlay.SearchEntry("Center Weight", category, nodeId, false),
            new UnifiedSearchOverlay.SearchEntry("Highlight Weight", category, nodeId, false),
            new UnifiedSearchOverlay.SearchEntry("Exposure Compensation", category, nodeId, false),
            new UnifiedSearchOverlay.SearchEntry("Middle Grey", category, nodeId, false)
        );
    }
}
