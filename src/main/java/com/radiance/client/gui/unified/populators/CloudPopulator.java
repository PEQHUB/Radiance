package com.radiance.client.gui.unified.populators;

import static net.minecraft.client.option.GameOptions.getGenericValueText;

import com.radiance.client.gui.ResettableSliderWidget;
import com.radiance.client.gui.SelectionDropdownWidget;
import com.radiance.client.gui.unified.*;
import com.radiance.client.option.Options;
import net.minecraft.client.gui.widget.CyclingButtonWidget;
import net.minecraft.client.option.SimpleOption;
import net.minecraft.text.Text;

public class CloudPopulator implements ContentPopulator {
    @Override
    public void populate(ContentPanelWidget panel, RadianceUnifiedScreen screen) {
        int dim = Options.getEnvironmentEditingDimension();

        // ── Section 1: Basic Clouds (per-dimension) ──
        SettingsSection basic = panel.addSection("options.video.environment.clouds.category");

        basic.addTwoSliders(
            new ResettableSliderWidget(0, 0, 150, 20,
                0, 300, Options.cloudBrightnessPercent[dim], Options.PERCENT_DEFAULT,
                v -> getGenericValueText(Text.translatable("options.video.environment.cloud_brightness"), Text.literal(v + "%")),
                v -> Options.setCloudBrightnessPercent(dim, v, true)),
            new ResettableSliderWidget(0, 0, 150, 20,
                0, 300, Options.cloudAlphaPercent[dim], Options.PERCENT_DEFAULT,
                v -> getGenericValueText(Text.translatable("options.video.environment.cloud_alpha"), Text.literal(v + "%")),
                v -> Options.setCloudAlphaPercent(dim, v, true)));
        basic.tooltip("Brightness = how lit clouds are. Alpha = how solid (0% = invisible).");

        basic.addTwoSliders(
            new ResettableSliderWidget(0, 0, 150, 20,
                -64, 64, Options.cloudHeightOffset[dim], 0,
                v -> getGenericValueText(Text.translatable("options.video.environment.cloud_height_offset"), Text.literal(v + "")),
                v -> Options.setCloudHeightOffset(dim, v, true)),
            new ResettableSliderWidget(0, 0, 150, 20,
                0, 300, Options.cloudPuffinessPercent[dim], Options.PERCENT_DEFAULT,
                v -> getGenericValueText(Text.translatable("options.video.environment.cloud_puffiness"), Text.literal(v + "%")),
                v -> Options.setCloudPuffinessPercent(dim, v, true)));
        basic.tooltip("Height Offset = vertical shift in blocks. Puffiness = roundness of cloud edges.");

        if (Options.volCloudQuality > 0) {
            // ── Section 2: Volumetric Tuning (per-dimension) ──
            SettingsSection volumetric = panel.addSection("options.video.environment.clouds.volumetric.category");

            volumetric.addTwoSliders(
                new ResettableSliderWidget(0, 0, 150, 20,
                    25, 300, Options.cloudDetailScalePercent[dim], Options.CLOUD_DETAIL_SCALE_DEFAULT_PERCENT,
                    v -> getGenericValueText(Text.translatable("options.video.environment.cloud_detail_scale"), Text.literal(v + "%")),
                    v -> Options.setCloudDetailScalePercent(dim, v, true)),
                new ResettableSliderWidget(0, 0, 150, 20,
                    0, 300, Options.cloudDetailStrengthPercent[dim], Options.CLOUD_DETAIL_STRENGTH_DEFAULT_PERCENT,
                    v -> getGenericValueText(Text.translatable("options.video.environment.cloud_detail_strength"), Text.literal(v + "%")),
                    v -> Options.setCloudDetailStrengthPercent(dim, v, true)));
            volumetric.tooltip("Detail Scale = size of noise features. Strength = how much they erode the base shape.");

            volumetric.addTwoSliders(
                new ResettableSliderWidget(0, 0, 150, 20,
                    0, 200, Options.cloudShadowStrengthPercent[dim], Options.PERCENT_DEFAULT,
                    v -> getGenericValueText(Text.translatable("options.video.environment.cloud_shadow_strength"), Text.literal(v + "%")),
                    v -> Options.setCloudShadowStrengthPercent(dim, v, true)),
                new ResettableSliderWidget(0, 0, 150, 20,
                    1, 16, Options.cloudThicknessBlocks[dim], 4,
                    v -> getGenericValueText(Text.translatable("options.video.environment.cloud_thickness"), Text.literal(v + " blocks")),
                    v -> Options.setCloudThicknessBlocks(dim, v, true)));
            volumetric.tooltip("Shadow Strength = cloud self-shadowing. Thickness = depth in blocks.");

            volumetric.addSlider(new ResettableSliderWidget(0, 0, 150, 20,
                0, 300, Options.cloudDensityPercent[dim], Options.PERCENT_DEFAULT,
                v -> getGenericValueText(Text.translatable("options.video.environment.cloud_density"), Text.literal(v + "%")),
                v -> Options.setCloudDensityPercent(dim, v, true)));

            // Noise Affects Shadows: boolean → addToggle (FIX: was 0-1 slider)
            SimpleOption<Boolean> noiseAffectsShadows = SimpleOption.ofBoolean(
                "options.video.environment.cloud_shadow_noise",
                Options.cloudNoiseAffectsShadows[dim] == 1,
                value -> Options.setCloudNoiseAffectsShadows(dim, value, true));
            volumetric.addToggle(noiseAffectsShadows.createWidget(
                net.minecraft.client.MinecraftClient.getInstance().options));
            volumetric.tooltip("Density = cloud opacity. Toggle = noise modulates ground shadow shapes.");
        }

        // ── Section 3: Volumetric Cloud Module (global) ──
        SettingsSection volModule = panel.addSection("options.video.environment.clouds.volumetric_module.category");

        // Quality dropdown (6 values) — always visible so user can toggle volumetric clouds on/off
        SelectionDropdownWidget qualityDropdown = new SelectionDropdownWidget(
            0, 0, 150, 20, "Quality",
            Options.VOL_CLOUD_QUALITY_NAMES, Options.volCloudQuality,
            value -> {
                Options.setVolCloudQuality(value, true);
                screen.refreshContent();
            });

        volModule.addTwoWidgets(qualityDropdown, null);
        volModule.tooltip("Quality = overall cloud resolution (Off disables volumetric clouds).");

        if (Options.volCloudQuality > 0) {
            // Density + Detail Erosion (paired sliders)
            volModule.addTwoSliders(
                new ResettableSliderWidget(0, 0, 150, 20,
                    1, 30, Options.volCloudDensityTenths, 10,
                    v -> getGenericValueText(Text.translatable("options.video.environment.vol_cloud_density"),
                        Text.literal(String.format("%.1f", v / 10.0))),
                    v -> Options.setVolCloudDensityTenths(v, true)),
                new ResettableSliderWidget(0, 0, 150, 20,
                    0, 200, Options.volCloudDetailStrengthPercent, 100,
                    v -> getGenericValueText(Text.translatable("options.video.environment.vol_cloud_detail"),
                        Text.literal(v + "%")),
                    v -> Options.setVolCloudDetailStrengthPercent(v, true)));
            volModule.tooltip("Density = how thick clouds are. Detail = fine noise erosion strength.");

            // Coverage + Wind Speed (paired sliders)
            volModule.addTwoSliders(
                new ResettableSliderWidget(0, 0, 150, 20,
                    0, 100, Options.volCloudCoveragePercent, 35,
                    v -> getGenericValueText(Text.translatable("options.video.environment.vol_cloud_coverage"),
                        Text.literal(v + "%")),
                    v -> Options.setVolCloudCoveragePercent(v, true)),
                new ResettableSliderWidget(0, 0, 150, 20,
                    0, 300, Options.volCloudSpeedTenths, 50,
                    v -> getGenericValueText(Text.translatable("options.video.environment.vol_cloud_wind_speed"),
                        Text.literal(String.format("%.1f m/s", v / 10.0))),
                    v -> Options.setVolCloudSpeedTenths(v, true)));
            volModule.tooltip("Coverage = sky fraction covered. Wind Speed = cloud movement speed.");

            // Cloud Type dropdown (4 discrete values → SelectionDropdownWidget per R9)
            SelectionDropdownWidget typeDropdown = new SelectionDropdownWidget(
                0, 0, 150, 20, "Cloud Type",
                Options.VOL_CLOUD_TYPE_NAMES,
                Math.min(Options.volCloudTypePercent * 4 / 101, 3),
                value -> {
                    // Map index back to percent: 0→0, 1→33, 2→67, 3→100
                    int percent = value * 100 / 3;
                    Options.setVolCloudTypePercent(percent, true);
                });

            volModule.addTwoWidgets(typeDropdown, null);
            volModule.tooltip("Cloud Type: Stratus (flat), Stratocumulus (patchy), Cumulus (fluffy), Cumulonimbus (towering).");

            // Altitude + Thickness (paired sliders)
            volModule.addTwoSliders(
                new ResettableSliderWidget(0, 0, 150, 20,
                    128, 320, Options.volCloudAltitude, 192,
                    v -> getGenericValueText(Text.translatable("options.video.environment.vol_cloud_altitude"),
                        Text.literal(v + " blocks")),
                    v -> Options.setVolCloudAltitude(v, true)),
                new ResettableSliderWidget(0, 0, 150, 20,
                    32, 128, Options.volCloudThickness, 64,
                    v -> getGenericValueText(Text.translatable("options.video.environment.vol_cloud_thickness"),
                        Text.literal(v + " blocks")),
                    v -> Options.setVolCloudThickness(v, true)));
            volModule.tooltip("Altitude = cloud base height. Thickness = vertical depth of cloud layer.");

            // Ambient AO
            volModule.addSlider(
                new ResettableSliderWidget(0, 0, 150, 20,
                    0, 300, Options.volCloudAmbientPercent, 100,
                    v -> getGenericValueText(Text.translatable("options.video.environment.vol_cloud_ambient"),
                        Text.literal(v + "%")),
                    v -> Options.setVolCloudAmbientPercent(v, true)));
            volModule.tooltip("Ambient occlusion strength for volumetric clouds.");

            // Noise Scale + Cell Frequency (paired sliders)
            volModule.addTwoSliders(
                new ResettableSliderWidget(0, 0, 150, 20,
                    64, 2048, Options.volCloudNoiseScale, 214,
                    v -> getGenericValueText(Text.translatable("options.video.environment.vol_cloud_noise_scale"),
                        Text.literal(v + " blk")),
                    v -> Options.setVolCloudNoiseScale(v, true)),
                new ResettableSliderWidget(0, 0, 150, 20,
                    10, 160, Options.volCloudCellFrequencyTenths, 50,
                    v -> getGenericValueText(Text.translatable("options.video.environment.vol_cloud_weather_scale"),
                        Text.literal(String.format("%.1f", v / 10.0))),
                    v -> Options.setVolCloudCellFrequencyTenths(v, true)));
            volModule.tooltip("Noise Scale = 3D noise period in blocks. Weather Scale = cloud formation size (higher = smaller, more clouds).");

            // Wind Direction + Atmosphere Fade (paired sliders)
            volModule.addTwoSliders(
                new ResettableSliderWidget(0, 0, 150, 20,
                    0, 360, Options.volCloudWindAngleDegrees, 0,
                    v -> getGenericValueText(Text.translatable("options.video.environment.vol_cloud_wind_dir"),
                        Text.literal(v + "\u00B0")),
                    v -> Options.setVolCloudWindAngleDegrees(v, true)),
                new ResettableSliderWidget(0, 0, 150, 20,
                    100, 4000, Options.volCloudAtmosphereFadeDist, 800,
                    v -> getGenericValueText(Text.translatable("options.video.environment.vol_cloud_atmo_fade"),
                        Text.literal(v + " blk")),
                    v -> Options.setVolCloudAtmosphereFadeDist(v, true)));
            volModule.tooltip("Wind Dir = wind heading in degrees. Atmo Fade = visual fade distance in blocks.");

            // Temporal Blend (0 = Auto)
            volModule.addSlider(
                new ResettableSliderWidget(0, 0, 150, 20,
                    0, 99, Options.volCloudTemporalPercent, 0,
                    v -> getGenericValueText(Text.translatable("options.video.environment.vol_cloud_temporal"),
                        Text.literal(v == 0 ? "Auto" : v + "%")),
                    v -> Options.setVolCloudTemporalPercent(v, true)));
            volModule.tooltip("Temporal reprojection blend. Auto = quality-based. Higher = smoother but more ghosting.");
        }

        // ── Section 4: Advanced Quality Overrides ──
        if (Options.volCloudQuality > 0) {
            SettingsSection advanced = panel.addSection("options.video.environment.clouds.advanced.category");

            // March Steps + Light Steps
            advanced.addTwoSliders(
                new ResettableSliderWidget(0, 0, 150, 20,
                    0, 256, Options.volCloudMarchSteps, 0,
                    v -> getGenericValueText(Text.translatable("options.video.environment.vol_cloud_march_steps"),
                        Text.literal(v == 0 ? "Auto" : String.valueOf(v))),
                    v -> Options.setVolCloudMarchSteps(v, true)),
                new ResettableSliderWidget(0, 0, 150, 20,
                    0, 16, Options.volCloudLightSteps, 0,
                    v -> getGenericValueText(Text.translatable("options.video.environment.vol_cloud_light_steps"),
                        Text.literal(v == 0 ? "Auto" : String.valueOf(v))),
                    v -> Options.setVolCloudLightSteps(v, true)));
            advanced.tooltip("March Steps = ray march iterations. Light Steps = light sampling iterations. Auto uses quality preset.");

            // Resolution Divisor + Noise Resolution (paired)
            advanced.addTwoSliders(
                new ResettableSliderWidget(0, 0, 150, 20,
                    0, 4, Options.volCloudResDivisor, 0,
                    v -> getGenericValueText(Text.translatable("options.video.environment.vol_cloud_res_divisor"),
                        Text.literal(v == 0 ? "Auto" : v == 1 ? "Full" : v == 2 ? "Half" : v == 3 ? "Third" : "Quarter")),
                    v -> Options.setVolCloudResDivisor(v, true)),
                new ResettableSliderWidget(0, 0, 150, 20,
                    128, 512, Options.volCloudNoiseRes, 128,
                    v -> {
                        int snapped = v <= 192 ? 128 : v <= 384 ? 256 : 512;
                        String label = snapped + "\u00B3";
                        String vram = snapped == 128 ? "8 MB" : snapped == 256 ? "64 MB" : "512 MB";
                        return getGenericValueText(Text.translatable("options.video.environment.vol_cloud_noise_res"),
                            Text.literal(label + " (" + vram + ")"));
                    },
                    v -> Options.setVolCloudNoiseRes(v, true)));
            advanced.tooltip("Render Res = cloud pass resolution. Noise Res = 3D noise texture size (higher = less tiling, more VRAM).");

            // Debug View
            String[] debugNames = {"Off", "Weather Cov", "Weather Type",
                "Noise R (Perlin-Worley)", "Noise G (Worley 2x)", "Noise B (FBM Erosion)",
                "Height Profile", "Raw Density", "Final Density"};
            advanced.addSlider(new ResettableSliderWidget(0, 0, 150, 20,
                0, 8, Options.volCloudDebugMode, 0,
                v -> getGenericValueText(Text.translatable("options.video.environment.vol_cloud_debug"),
                    Text.literal(debugNames[v])),
                v -> Options.setVolCloudDebugMode(v, true)));
            advanced.tooltip("Debug visualization for cloud noise channels and density evaluation.");
        }
    }

    @Override
    public java.util.List<UnifiedSearchOverlay.SearchEntry> getSearchEntries(String nodeId, String category) {
        return java.util.List.of(
            new UnifiedSearchOverlay.SearchEntry("Cloud Brightness", category, nodeId, false),
            new UnifiedSearchOverlay.SearchEntry("Cloud Alpha", category, nodeId, false),
            new UnifiedSearchOverlay.SearchEntry("Cloud Height", category, nodeId, false),
            new UnifiedSearchOverlay.SearchEntry("Cloud Puffiness", category, nodeId, false),
            new UnifiedSearchOverlay.SearchEntry("Cloud Detail Scale", category, nodeId, false),
            new UnifiedSearchOverlay.SearchEntry("Cloud Detail Strength", category, nodeId, false),
            new UnifiedSearchOverlay.SearchEntry("Cloud Shadow", category, nodeId, false),
            new UnifiedSearchOverlay.SearchEntry("Cloud Thickness", category, nodeId, false),
            new UnifiedSearchOverlay.SearchEntry("Cloud Density", category, nodeId, false),
            new UnifiedSearchOverlay.SearchEntry("Noise Affects Shadows", category, nodeId, false),
            new UnifiedSearchOverlay.SearchEntry("Cloud Quality", category, nodeId, false),
            new UnifiedSearchOverlay.SearchEntry("Cloud Coverage", category, nodeId, false),
            new UnifiedSearchOverlay.SearchEntry("Cloud Type", category, nodeId, false),
            new UnifiedSearchOverlay.SearchEntry("Cloud Altitude", category, nodeId, false),
            new UnifiedSearchOverlay.SearchEntry("Wind Speed", category, nodeId, false),
            new UnifiedSearchOverlay.SearchEntry("Ambient AO", category, nodeId, false),
            new UnifiedSearchOverlay.SearchEntry("Noise Scale", category, nodeId, false),
            new UnifiedSearchOverlay.SearchEntry("Weather Scale", category, nodeId, false),
            new UnifiedSearchOverlay.SearchEntry("Wind Direction", category, nodeId, false),
            new UnifiedSearchOverlay.SearchEntry("Atmosphere Fade", category, nodeId, false),
            new UnifiedSearchOverlay.SearchEntry("Temporal Blend", category, nodeId, false),
            new UnifiedSearchOverlay.SearchEntry("March Steps", category, nodeId, false),
            new UnifiedSearchOverlay.SearchEntry("Light Steps", category, nodeId, false),
            new UnifiedSearchOverlay.SearchEntry("Resolution Divisor", category, nodeId, false),
            new UnifiedSearchOverlay.SearchEntry("Noise Resolution", category, nodeId, false),
            new UnifiedSearchOverlay.SearchEntry("Debug View", category, nodeId, false)
        );
    }
}
