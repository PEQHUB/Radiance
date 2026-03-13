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

        if (Options.manualExposureEnabled) {
            int sliderValue = Options.manualExposureEV100Tenths + EV100_SLIDER_OFFSET;
            int sliderDefault = 150 + EV100_SLIDER_OFFSET;
            section.addSlider(new ResettableSliderWidget(0, 0, 150, 20,
                0, 240, sliderValue, sliderDefault,
                v -> getGenericValueText(Text.translatable(Options.MANUAL_EXPOSURE_KEY),
                    Text.literal(String.format("EV %.1f", (v - EV100_SLIDER_OFFSET) / 10.0))),
                v -> Options.setManualExposureEV100Tenths(v - EV100_SLIDER_OFFSET, true)));
            return;
        }

        // Legacy Exposure toggle
        SimpleOption<Boolean> legacyExposure = SimpleOption.ofBoolean(
            Options.LEGACY_EXPOSURE_KEY,
            Options.legacyExposure,
            value -> {
                Options.setLegacyExposure(value, true);
                screen.refreshContent();
            });
        section.addToggle(legacyExposure.createWidget(MinecraftClient.getInstance().options));

        // Speed controls
        SettingsSection speeds = panel.addSection(Text.literal("Adaptation Speed"));
        speeds.addSlider(new ResettableSliderWidget(0, 0, 150, 20,
            1, 200, Options.exposureUpSpeedTenths, 8,
            v -> getGenericValueText(Text.translatable(Options.EXPOSURE_UP_SPEED_KEY), Text.literal(String.format("%.1f", v / 10.0))),
            v -> Options.setExposureUpSpeedTenths(v, true)));
        speeds.addSlider(new ResettableSliderWidget(0, 0, 150, 20,
            1, 200, Options.exposureDownSpeedTenths, 15,
            v -> getGenericValueText(Text.translatable(Options.EXPOSURE_DOWN_SPEED_KEY), Text.literal(String.format("%.1f", v / 10.0))),
            v -> Options.setExposureDownSpeedTenths(v, true)));
        speeds.addSlider(new ResettableSliderWidget(0, 0, 150, 20,
            10, 80, Options.exposureBrightAdaptBoostTenths, 10,
            v -> getGenericValueText(Text.translatable(Options.EXPOSURE_BRIGHT_ADAPT_BOOST_KEY), Text.literal(String.format("x%.1f", v / 10.0))),
            v -> Options.setExposureBrightAdaptBoostTenths(v, true)));

        if (!Options.legacyExposure) {
            SettingsSection highlight = panel.addSection(Text.literal("Highlight Protection"));
            highlight.addSlider(new ResettableSliderWidget(0, 0, 150, 20,
                8, 24, Options.exposureLog2Max, 18,
                v -> getGenericValueText(Text.translatable(Options.EXPOSURE_LOG2_MAX_KEY), Text.literal(Integer.toString(v))),
                v -> Options.setExposureLog2Max(v, true)));
            highlight.addSlider(new ResettableSliderWidget(0, 0, 150, 20,
                0, 100, Options.exposureHighlightProtectionPercent, 30,
                v -> getGenericValueText(Text.translatable(Options.EXPOSURE_HIGHLIGHT_PROTECTION_KEY), Text.literal(v + "%")),
                v -> Options.setExposureHighlightProtectionPercent(v, true)));
            highlight.addSlider(new ResettableSliderWidget(0, 0, 150, 20,
                0, 300, Options.exposureHighlightSmoothSpeedTenths, 20,
                v -> getGenericValueText(Text.translatable(Options.EXPOSURE_HIGHLIGHT_SMOOTH_SPEED_KEY), Text.literal(String.format("%.1f", v / 10.0))),
                v -> Options.setExposureHighlightSmoothSpeedTenths(v, true)));
            highlight.addSlider(new ResettableSliderWidget(0, 0, 150, 20,
                9000, 9999, Options.exposureHighlightPercentileTenK, 9800,
                v -> getGenericValueText(Text.translatable(Options.EXPOSURE_HIGHLIGHT_PERCENTILE_KEY), Text.literal(String.format("%.2f%%", v / 100.0))),
                v -> Options.setExposureHighlightPercentileTenK(v, true)));
        }

        // Range & Compensation
        SettingsSection range = panel.addSection(Text.literal("Range & Compensation"));
        range.addSlider(new ResettableSliderWidget(0, 0, 150, 20,
            1, 10000, Options.minExposureTenK, 1,
            v -> getGenericValueText(Text.translatable(Options.MIN_EXPOSURE_KEY), Text.literal(String.format("%.1e", v * 1e-7))),
            v -> Options.setMinExposure(v, true)));
        range.addSlider(new ResettableSliderWidget(0, 0, 150, 20,
            1, 100, Options.maxExposure, 20,
            v -> getGenericValueText(Text.translatable(Options.MAX_EXPOSURE_KEY), Text.literal(String.format("%.1f", v / 10.0))),
            v -> Options.setMaxExposure(v, true)));
        range.addSlider(new ResettableSliderWidget(0, 0, 150, 20,
            0, 60, Options.exposureCompensation + 30, 0 + 30,
            v -> getGenericValueText(Text.translatable(Options.EXPOSURE_COMPENSATION_KEY), Text.literal(String.format("%+.1f EV", (v - 30) / 10.0))),
            v -> Options.setExposureCompensation(v - 30, true)));
        range.addSlider(new ResettableSliderWidget(0, 0, 150, 20,
            1, 50, Options.middleGreyPercent, 18,
            v -> getGenericValueText(Text.translatable(Options.MIDDLE_GREY_KEY), Text.literal(String.format("%.2f", v / 100.0))),
            v -> Options.setMiddleGrey(v, true)));
    }
}
