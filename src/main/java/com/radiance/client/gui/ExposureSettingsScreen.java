package com.radiance.client.gui;

import static net.minecraft.client.option.GameOptions.getGenericValueText;

import com.radiance.client.option.Options;
import com.radiance.client.util.CategoryVideoOptionEntry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.option.GameOptionsScreen;
import net.minecraft.client.option.SimpleOption;
import net.minecraft.text.Text;

public class ExposureSettingsScreen extends GameOptionsScreen {

    // EV100 slider: offset by 40 so slider range is [0, 240] representing EV [-4.0, 20.0]
    private static final int EV100_SLIDER_OFFSET = 40;

    private final Screen parentScreen;

    public ExposureSettingsScreen(Screen parent) {
        super(parent, MinecraftClient.getInstance().options,
            Text.translatable("radiance.settings.exposure.title"));
        this.parentScreen = parent;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        RadianceTheme.drawOutlinedText(context, this.textRenderer,
            Text.literal("Radiance > Light & Color > Exposure"), 20, 26, RadianceTheme.textSecondary);
    }

    @Override
    protected void initBody() {
        this.body = this.layout.addBody(
            new WideOptionListWidget(this.client, this.width, this));
        addOptions();
    }

    @Override
    protected void addOptions() {
        this.body.addEntry(
            new CategoryVideoOptionEntry(Text.translatable("options.video.category.exposure"), body));

        SimpleOption<Boolean> manualExposure = SimpleOption.ofBoolean(
            Options.MANUAL_EXPOSURE_ENABLED_KEY,
            Options.manualExposureEnabled,
            value -> {
                Options.setManualExposureEnabled(value, true);
                MinecraftClient.getInstance().setScreen(new ExposureSettingsScreen(parentScreen));
            });
        this.body.addSingleOptionEntry(manualExposure);

        if (Options.manualExposureEnabled) {
            // Manual Exposure: EV100 slider, range EV -4.0 to EV 20.0
            // Stored as tenths (-40 to 200), slider offset by 40 -> [0, 240]
            int sliderValue = Options.manualExposureEV100Tenths + EV100_SLIDER_OFFSET;
            int sliderDefault = 150 + EV100_SLIDER_OFFSET; // EV 15.0 (sunny day)
            ResettableSliderWidget manualSlider = new ResettableSliderWidget(
                0, 0, 150, 20,
                0, 240, sliderValue, sliderDefault,
                v -> getGenericValueText(
                    Text.translatable(Options.MANUAL_EXPOSURE_KEY),
                    Text.literal(String.format("EV %.1f", (v - EV100_SLIDER_OFFSET) / 10.0))),
                v -> Options.setManualExposureEV100Tenths(v - EV100_SLIDER_OFFSET, true));
            this.body.addEntry(new RadianceSettingsScreen.SliderEntry(manualSlider, body));
            return;
        }

        // Legacy Exposure toggle: preserves the original failure modes by disabling highlight protection.
        SimpleOption<Boolean> legacyExposure = SimpleOption.ofBoolean(
            Options.LEGACY_EXPOSURE_KEY,
            Options.legacyExposure,
            value -> {
                Options.setLegacyExposure(value, true);
                // Rebuild screen to show/hide improved-only controls.
                MinecraftClient.getInstance().setScreen(new ExposureSettingsScreen(parentScreen));
            });
        this.body.addSingleOptionEntry(legacyExposure);

        // Exposure Up Speed: 0.1 to 20.0 (stored as tenths 1-200)
        ResettableSliderWidget upSpeedSlider = new ResettableSliderWidget(
            0, 0, 150, 20,
            1, 200, Options.exposureUpSpeedTenths, 8,
            v -> getGenericValueText(
                Text.translatable(Options.EXPOSURE_UP_SPEED_KEY),
                Text.literal(String.format("%.1f", v / 10.0))),
            v -> Options.setExposureUpSpeedTenths(v, true));
        this.body.addEntry(new RadianceSettingsScreen.SliderEntry(upSpeedSlider, body));

        // Exposure Down Speed: 0.1 to 20.0 (stored as tenths 1-200)
        ResettableSliderWidget downSpeedSlider = new ResettableSliderWidget(
            0, 0, 150, 20,
            1, 200, Options.exposureDownSpeedTenths, 15,
            v -> getGenericValueText(
                Text.translatable(Options.EXPOSURE_DOWN_SPEED_KEY),
                Text.literal(String.format("%.1f", v / 10.0))),
            v -> Options.setExposureDownSpeedTenths(v, true));
        this.body.addEntry(new RadianceSettingsScreen.SliderEntry(downSpeedSlider, body));

        // Bright Adapt Boost: 1.0 to 8.0 (stored as tenths 10-80)
        ResettableSliderWidget boostSlider = new ResettableSliderWidget(
            0, 0, 150, 20,
            10, 80, Options.exposureBrightAdaptBoostTenths, 10,
            v -> getGenericValueText(
                Text.translatable(Options.EXPOSURE_BRIGHT_ADAPT_BOOST_KEY),
                Text.literal(String.format("x%.1f", v / 10.0))),
            v -> Options.setExposureBrightAdaptBoostTenths(v, true));
        this.body.addEntry(new RadianceSettingsScreen.SliderEntry(boostSlider, body));

        if (!Options.legacyExposure) {
            // Histogram Max EV: 8 to 24 (controls max log2 luminance for histogram mapping)
            ResettableSliderWidget log2MaxSlider = new ResettableSliderWidget(
                0, 0, 150, 20,
                8, 24, Options.exposureLog2Max, 18,
                v -> getGenericValueText(
                    Text.translatable(Options.EXPOSURE_LOG2_MAX_KEY),
                    Text.literal(Integer.toString(v))),
                v -> Options.setExposureLog2Max(v, true));
            this.body.addEntry(new RadianceSettingsScreen.SliderEntry(log2MaxSlider, body));

            // Highlight Protection: 0% to 100%
            ResettableSliderWidget hpSlider = new ResettableSliderWidget(
                0, 0, 150, 20,
                0, 100, Options.exposureHighlightProtectionPercent, 30,
                v -> getGenericValueText(
                    Text.translatable(Options.EXPOSURE_HIGHLIGHT_PROTECTION_KEY),
                    Text.literal(v + "%")),
                v -> Options.setExposureHighlightProtectionPercent(v, true));
            this.body.addEntry(new RadianceSettingsScreen.SliderEntry(hpSlider, body));

            // Highlight Smoothing Speed: 0.0 to 30.0 (stored as tenths 0-300)
            ResettableSliderWidget smoothSlider = new ResettableSliderWidget(
                0, 0, 150, 20,
                0, 300, Options.exposureHighlightSmoothSpeedTenths, 20,
                v -> getGenericValueText(
                    Text.translatable(Options.EXPOSURE_HIGHLIGHT_SMOOTH_SPEED_KEY),
                    Text.literal(String.format("%.1f", v / 10.0))),
                v -> Options.setExposureHighlightSmoothSpeedTenths(v, true));
            this.body.addEntry(new RadianceSettingsScreen.SliderEntry(smoothSlider, body));

            // Highlight Percentile: 0.9000 to 0.9999 (stored as ten-thousandths 9000-9999)
            ResettableSliderWidget percSlider = new ResettableSliderWidget(
                0, 0, 150, 20,
                9000, 9999, Options.exposureHighlightPercentileTenK, 9800,
                v -> getGenericValueText(
                    Text.translatable(Options.EXPOSURE_HIGHLIGHT_PERCENTILE_KEY),
                    Text.literal(String.format("%.2f%%", v / 100.0))),
                v -> Options.setExposureHighlightPercentileTenK(v, true));
            this.body.addEntry(new RadianceSettingsScreen.SliderEntry(percSlider, body));
        }

        // Min Exposure: 1e-7 to 1e-3 (slider 1-10000, physical light range)
        ResettableSliderWidget minExpSlider = new ResettableSliderWidget(
            0, 0, 150, 20,
            1, 10000, Options.minExposureTenK, 1,
            v -> getGenericValueText(
                Text.translatable(Options.MIN_EXPOSURE_KEY),
                Text.literal(String.format("%.1e", v * 1e-7))),
            v -> Options.setMinExposure(v, true));
        this.body.addEntry(new RadianceSettingsScreen.SliderEntry(minExpSlider, body));

        // Max Exposure: 0.1 to 10.0 (stored as tenths 1-100, default 20 = 2.0)
        ResettableSliderWidget maxExpSlider = new ResettableSliderWidget(
            0, 0, 150, 20,
            1, 100, Options.maxExposure, 20,
            v -> getGenericValueText(
                Text.translatable(Options.MAX_EXPOSURE_KEY),
                Text.literal(String.format("%.1f", v / 10.0))),
            v -> Options.setMaxExposure(v, true));
        this.body.addEntry(new RadianceSettingsScreen.SliderEntry(maxExpSlider, body));

        // Exposure Compensation: -3.0 to +3.0 EV (stored as tenths, slider 0-60 offset by 30)
        ResettableSliderWidget ecSlider = new ResettableSliderWidget(
            0, 0, 150, 20,
            0, 60, Options.exposureCompensation + 30, 0 + 30,
            v -> getGenericValueText(
                Text.translatable(Options.EXPOSURE_COMPENSATION_KEY),
                Text.literal(String.format("%+.1f EV", (v - 30) / 10.0))),
            v -> Options.setExposureCompensation(v - 30, true));
        this.body.addEntry(new RadianceSettingsScreen.SliderEntry(ecSlider, body));

        // Middle Grey: 0.01 to 0.50 (stored as percent 1-50)
        ResettableSliderWidget mgSlider = new ResettableSliderWidget(
            0, 0, 150, 20,
            1, 50, Options.middleGreyPercent, 18,
            v -> getGenericValueText(
                Text.translatable(Options.MIDDLE_GREY_KEY),
                Text.literal(String.format("%.2f", v / 100.0))),
            v -> Options.setMiddleGrey(v, true));
        this.body.addEntry(new RadianceSettingsScreen.SliderEntry(mgSlider, body));
    }
}
