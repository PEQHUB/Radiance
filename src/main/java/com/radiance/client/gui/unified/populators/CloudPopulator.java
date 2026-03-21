package com.radiance.client.gui.unified.populators;

import static net.minecraft.client.option.GameOptions.getGenericValueText;

import com.radiance.client.gui.ResettableSliderWidget;
import com.radiance.client.gui.SelectionDropdownWidget;
import com.radiance.client.gui.unified.*;
import com.radiance.client.option.Options;
import net.minecraft.client.MinecraftClient;

import net.minecraft.text.Text;

public class CloudPopulator implements ContentPopulator {
    @Override
    public void populate(ContentPanelWidget panel, RadianceUnifiedScreen screen) {
        int dim = Options.getEnvironmentEditingDimension();

        // Vanilla cloud sliders — only shown when volumetric clouds are OFF.
        // These per-dimension params are NOT connected to the volumetric cloud module.
        if (Options.volCloudQuality == 0) {
            SettingsSection basic = panel.addSection("options.video.environment.clouds.category");

            basic.addSlider(new ResettableSliderWidget(0, 0, 150, 20,
                0, 300, Options.cloudBrightnessPercent[dim], Options.PERCENT_DEFAULT,
                v -> getGenericValueText(Text.translatable("options.video.environment.cloud_brightness"), Text.literal(v + "%")),
                v -> Options.setCloudBrightnessPercent(dim, v, true)));

            basic.addSlider(new ResettableSliderWidget(0, 0, 150, 20,
                0, 300, Options.cloudAlphaPercent[dim], Options.PERCENT_DEFAULT,
                v -> getGenericValueText(Text.translatable("options.video.environment.cloud_alpha"), Text.literal(v + "%")),
                v -> Options.setCloudAlphaPercent(dim, v, true)));

            basic.addSlider(new ResettableSliderWidget(0, 0, 150, 20,
                -64, 64, Options.cloudHeightOffset[dim], 0,
                v -> getGenericValueText(Text.translatable("options.video.environment.cloud_height_offset"), Text.literal(v + "")),
                v -> Options.setCloudHeightOffset(dim, v, true)));

            SettingsSection volumetric = panel.addSection("options.video.environment.clouds.volumetric.category");

            volumetric.addSlider(new ResettableSliderWidget(0, 0, 150, 20,
                25, 300, Options.cloudDetailScalePercent[dim], Options.CLOUD_DETAIL_SCALE_DEFAULT_PERCENT,
                v -> getGenericValueText(Text.translatable("options.video.environment.cloud_detail_scale"), Text.literal(v + "%")),
                v -> Options.setCloudDetailScalePercent(dim, v, true)));

            volumetric.addSlider(new ResettableSliderWidget(0, 0, 150, 20,
                0, 300, Options.cloudDetailStrengthPercent[dim], Options.CLOUD_DETAIL_STRENGTH_DEFAULT_PERCENT,
                v -> getGenericValueText(Text.translatable("options.video.environment.cloud_detail_strength"), Text.literal(v + "%")),
                v -> Options.setCloudDetailStrengthPercent(dim, v, true)));

            volumetric.addTwoSliders(
                new ResettableSliderWidget(0, 0, 150, 20,
                    0, 200, Options.cloudShadowStrengthPercent[dim], Options.PERCENT_DEFAULT,
                    v -> getGenericValueText(Text.translatable("options.video.environment.cloud_shadow_strength"), Text.literal(v + "%")),
                    v -> Options.setCloudShadowStrengthPercent(dim, v, true)),
                new ResettableSliderWidget(0, 0, 150, 20,
                    0, 300, Options.cloudPuffinessPercent[dim], 3,
                    v -> getGenericValueText(Text.translatable("options.video.environment.cloud_puffiness"), Text.literal(v + "%")),
                    v -> Options.setCloudPuffinessPercent(dim, v, true)));
        }

        // --- Volumetric Cloud Module ---
        SettingsSection volModule = panel.addSection("options.video.environment.clouds.volumetric_module.category");

        // Row 1: Quality + Scatter Octaves
        SelectionDropdownWidget qualityDropdown = new SelectionDropdownWidget(
            0, 0, 150, 20, "Quality",
            Options.VOL_CLOUD_QUALITY_NAMES, Options.volCloudQuality,
            value -> {
                Options.setVolCloudQuality(value, true);
                screen.refreshContent();
            });

        ResettableSliderWidget scatterSlider = new ResettableSliderWidget(0, 0, 150, 20,
            1, 8, Options.volCloudScatterOctaves, 3,
            v -> getGenericValueText(Text.literal("Scatter Octaves"), Text.literal(String.valueOf(v))),
            v -> Options.setVolCloudScatterOctaves(v, true));

        volModule.addTwoWidgets(qualityDropdown, scatterSlider);

        // Row 2: Coverage + Cloud Type
        volModule.addTwoSliders(
            new ResettableSliderWidget(0, 0, 150, 20,
                0, 100, Options.volCloudCoveragePercent, 50,
                v -> getGenericValueText(Text.literal("Coverage"), Text.literal(v + "%")),
                v -> Options.setVolCloudCoveragePercent(v, true)),
            new ResettableSliderWidget(0, 0, 150, 20,
                0, 100, Options.volCloudTypePercent, 0,
                v -> {
                    int idx = Math.min(v * 4 / 101, 3);
                    return getGenericValueText(Text.literal("Cloud Type"),
                        Text.literal(Options.VOL_CLOUD_TYPE_NAMES[idx]));
                },
                v -> Options.setVolCloudTypePercent(v, true)));

        // Row 3: Density + Detail Erosion
        volModule.addTwoSliders(
            new ResettableSliderWidget(0, 0, 150, 20,
                1, 30, Options.volCloudDensityTenths, 10,
                v -> getGenericValueText(Text.literal("Density"), Text.literal(String.format("%.1f", v / 10.0))),
                v -> Options.setVolCloudDensityTenths(v, true)),
            new ResettableSliderWidget(0, 0, 150, 20,
                0, 300, Options.volCloudDetailStrengthPercent, 100,
                v -> getGenericValueText(Text.literal("Detail Erosion"), Text.literal(v + "%")),
                v -> Options.setVolCloudDetailStrengthPercent(v, true)));

        // Row 4: Altitude + Thickness
        volModule.addTwoSliders(
            new ResettableSliderWidget(0, 0, 150, 20,
                128, 320, Options.volCloudAltitude, 192,
                v -> getGenericValueText(Text.literal("Altitude"), Text.literal(v + " blk")),
                v -> Options.setVolCloudAltitude(v, true)),
            new ResettableSliderWidget(0, 0, 150, 20,
                16, 256, Options.volCloudThickness, 64,
                v -> getGenericValueText(Text.literal("Thickness"), Text.literal(v + " blk")),
                v -> Options.setVolCloudThickness(v, true)));

        // Row 5: Powder Edge + Ambient AO
        volModule.addTwoSliders(
            new ResettableSliderWidget(0, 0, 150, 20,
                0, 300, Options.volCloudPowderPercent, 100,
                v -> getGenericValueText(Text.literal("Powder Edge"), Text.literal(v + "%")),
                v -> Options.setVolCloudPowderPercent(v, true)),
            new ResettableSliderWidget(0, 0, 150, 20,
                0, 300, Options.volCloudAmbientPercent, 100,
                v -> getGenericValueText(Text.literal("Ambient AO"), Text.literal(v + "%")),
                v -> Options.setVolCloudAmbientPercent(v, true)));

        // Row 6: Noise Scale + Cell Frequency
        volModule.addTwoSliders(
            new ResettableSliderWidget(0, 0, 150, 20,
                64, 2048, Options.volCloudNoiseScale, 214,
                v -> getGenericValueText(Text.literal("Noise Scale"), Text.literal(v + " blk")),
                v -> Options.setVolCloudNoiseScale(v, true)),
            new ResettableSliderWidget(0, 0, 150, 20,
                10, 320, Options.volCloudCellFrequencyTenths, 40,
                v -> getGenericValueText(Text.literal("Cell Freq"), Text.literal(String.format("%.1f", v / 10.0))),
                v -> Options.setVolCloudCellFrequencyTenths(v, true)));

        // Row 7: Wind Speed + Wind Direction
        volModule.addTwoSliders(
            new ResettableSliderWidget(0, 0, 150, 20,
                0, 300, Options.volCloudSpeedTenths, 50,
                v -> getGenericValueText(Text.literal("Wind Speed"), Text.literal(String.format("%.1f m/s", v / 10.0))),
                v -> Options.setVolCloudSpeedTenths(v, true)),
            new ResettableSliderWidget(0, 0, 150, 20,
                0, 360, Options.volCloudWindAngleDegrees, 0,
                v -> getGenericValueText(Text.literal("Wind Dir"), Text.literal(v + "\u00B0")),
                v -> Options.setVolCloudWindAngleDegrees(v, true)));

        // Row 8: Atmosphere Fade + Temporal Blend (0 = Auto)
        volModule.addTwoSliders(
            new ResettableSliderWidget(0, 0, 150, 20,
                100, 4000, Options.volCloudAtmosphereFadeDist, 800,
                v -> getGenericValueText(Text.literal("Atmo Fade"), Text.literal(v + " blk")),
                v -> Options.setVolCloudAtmosphereFadeDist(v, true)),
            new ResettableSliderWidget(0, 0, 150, 20,
                0, 99, Options.volCloudTemporalPercent, 0,
                v -> getGenericValueText(Text.literal("Temporal"),
                    Text.literal(v == 0 ? "Auto" : v + "%")),
                v -> Options.setVolCloudTemporalPercent(v, true)));

        // --- Advanced Quality Overrides ---
        SettingsSection advanced = panel.addSection("options.video.environment.clouds.advanced.category");

        // March Steps + Light Steps
        advanced.addTwoSliders(
            new ResettableSliderWidget(0, 0, 150, 20,
                0, 256, Options.volCloudMarchSteps, 0,
                v -> getGenericValueText(Text.literal("March Steps"),
                    Text.literal(v == 0 ? "Auto" : String.valueOf(v))),
                v -> Options.setVolCloudMarchSteps(v, true)),
            new ResettableSliderWidget(0, 0, 150, 20,
                0, 16, Options.volCloudLightSteps, 0,
                v -> getGenericValueText(Text.literal("Light Steps"),
                    Text.literal(v == 0 ? "Auto" : String.valueOf(v))),
                v -> Options.setVolCloudLightSteps(v, true)));

        // Resolution Divisor
        advanced.addSlider(new ResettableSliderWidget(0, 0, 150, 20,
            0, 4, Options.volCloudResDivisor, 0,
            v -> getGenericValueText(Text.literal("Resolution"),
                Text.literal(v == 0 ? "Auto" : v == 1 ? "Full" : v == 2 ? "Half" : v == 3 ? "Third" : "Quarter")),
            v -> Options.setVolCloudResDivisor(v, true)));

        // --- Debug ---
        SettingsSection debug = panel.addSection("options.video.environment.clouds.debug.category");

        String[] debugNames = {"Off", "Weather Cov", "Weather Type",
            "Noise R (Perlin-Worley)", "Noise G (Worley 2x)", "Noise B (FBM Erosion)",
            "Height Profile", "Raw Density", "Final Density"};
        debug.addSlider(new ResettableSliderWidget(0, 0, 150, 20,
            0, 8, Options.volCloudDebugMode, 0,
            v -> getGenericValueText(Text.literal("Debug View"), Text.literal(debugNames[v])),
            v -> Options.setVolCloudDebugMode(v, true)));
    }
}
