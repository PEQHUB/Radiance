package com.radiance.client.gui.unified.populators;

import static net.minecraft.client.option.GameOptions.getGenericValueText;

import com.radiance.client.gui.ResettableSliderWidget;
import com.radiance.client.gui.SelectionDropdownWidget;
import com.radiance.client.gui.unified.*;
import com.radiance.client.option.Options;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.widget.CyclingButtonWidget;
import net.minecraft.text.Text;

public class CloudPopulator implements ContentPopulator {
    @Override
    public void populate(ContentPanelWidget panel, RadianceUnifiedScreen screen) {
        int dim = Options.getEnvironmentEditingDimension();

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

        volumetric.addSlider(new ResettableSliderWidget(0, 0, 150, 20,
            0, 200, Options.cloudShadowStrengthPercent[dim], Options.PERCENT_DEFAULT,
            v -> getGenericValueText(Text.translatable("options.video.environment.cloud_shadow_strength"), Text.literal(v + "%")),
            v -> Options.setCloudShadowStrengthPercent(dim, v, true)));

        volumetric.addSlider(new ResettableSliderWidget(0, 0, 150, 20,
            1, 16, Options.cloudThicknessBlocks[dim], 4,
            v -> getGenericValueText(Text.translatable("options.video.environment.cloud_thickness"), Text.literal(v + " blocks")),
            v -> Options.setCloudThicknessBlocks(dim, v, true)));

        volumetric.addTwoSliders(
            new ResettableSliderWidget(0, 0, 150, 20,
                0, 300, Options.cloudDensityPercent[dim], Options.PERCENT_DEFAULT,
                v -> getGenericValueText(Text.translatable("options.video.environment.cloud_density"), Text.literal(v + "%")),
                v -> Options.setCloudDensityPercent(dim, v, true)),
            new ResettableSliderWidget(0, 0, 150, 20,
                0, 300, Options.cloudPuffinessPercent[dim], 3,
                v -> getGenericValueText(Text.translatable("options.video.environment.cloud_puffiness"), Text.literal(v + "%")),
                v -> Options.setCloudPuffinessPercent(dim, v, true)));

        volumetric.addSlider(new ResettableSliderWidget(0, 0, 150, 20,
            0, 1, Options.cloudNoiseAffectsShadows[dim], dim == 0 ? 1 : 0,
            v -> getGenericValueText(Text.translatable("options.video.environment.cloud_shadow_noise"),
                v == 1 ? Text.translatable("options.on") : Text.translatable("options.off")),
            v -> Options.setCloudNoiseAffectsShadows(dim, v == 1, true)));

        // --- Volumetric Cloud Module ---
        SettingsSection volModule = panel.addSection("options.video.environment.clouds.volumetric_module.category");

        // Quality dropdown (6 values → SelectionDropdownWidget)
        SelectionDropdownWidget qualityDropdown = new SelectionDropdownWidget(
            0, 0, 150, 20, "Quality",
            Options.VOL_CLOUD_QUALITY_NAMES, Options.volCloudQuality,
            value -> {
                Options.setVolCloudQuality(value, true);
                screen.refreshContent();
            });

        // Scatter Octaves (4 values → CyclingButtonWidget per R9)
        CyclingButtonWidget<Integer> scatterBtn = CyclingButtonWidget.<Integer>builder(
            value -> Text.literal(String.valueOf(value)))
            .values(1, 2, 3, 4)
            .initially(Options.volCloudScatterOctaves)
            .build(0, 0, 150, 20,
                Text.translatable("options.video.environment.vol_cloud_scatter"),
                (button, value) -> Options.setVolCloudScatterOctaves(value, true));

        volModule.addTwoWidgets(qualityDropdown, scatterBtn);

        // Density + Detail Strength paired sliders
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

        // Coverage + Type paired sliders
        volModule.addTwoSliders(
            new ResettableSliderWidget(0, 0, 150, 20,
                0, 100, Options.volCloudCoveragePercent, 35,
                v -> getGenericValueText(Text.translatable("options.video.environment.vol_cloud_coverage"),
                    Text.literal(v + "%")),
                v -> Options.setVolCloudCoveragePercent(v, true)),
            new ResettableSliderWidget(0, 0, 150, 20,
                0, 100, Options.volCloudTypePercent, 0,
                v -> {
                    int idx = Math.min(v * 4 / 101, 3);
                    return getGenericValueText(Text.translatable("options.video.environment.vol_cloud_type"),
                        Text.literal(Options.VOL_CLOUD_TYPE_NAMES[idx]));
                },
                v -> Options.setVolCloudTypePercent(v, true)));

        // Wind Speed + Altitude paired sliders
        volModule.addTwoSliders(
            new ResettableSliderWidget(0, 0, 150, 20,
                0, 300, Options.volCloudSpeedTenths, 50,
                v -> getGenericValueText(Text.translatable("options.video.environment.vol_cloud_wind_speed"),
                    Text.literal(String.format("%.1f m/s", v / 10.0))),
                v -> Options.setVolCloudSpeedTenths(v, true)),
            new ResettableSliderWidget(0, 0, 150, 20,
                128, 320, Options.volCloudAltitude, 192,
                v -> getGenericValueText(Text.translatable("options.video.environment.vol_cloud_altitude"),
                    Text.literal(v + " blocks")),
                v -> Options.setVolCloudAltitude(v, true)));

        // Thickness + Temporal Blend paired
        volModule.addTwoSliders(
            new ResettableSliderWidget(0, 0, 150, 20,
                32, 128, Options.volCloudThickness, 64,
                v -> getGenericValueText(Text.translatable("options.video.environment.vol_cloud_thickness"),
                    Text.literal(v + " blocks")),
                v -> Options.setVolCloudThickness(v, true)),
            new ResettableSliderWidget(0, 0, 150, 20,
                80, 99, Math.max(Options.volCloudTemporalPercent, 80), 95,
                v -> getGenericValueText(Text.translatable("options.video.environment.vol_cloud_temporal"),
                    Text.literal(v + "%")),
                v -> Options.setVolCloudTemporalPercent(v, true)));

        // Powder Strength + Ambient Occlusion paired
        volModule.addTwoSliders(
            new ResettableSliderWidget(0, 0, 150, 20,
                0, 200, Options.volCloudPowderPercent, 100,
                v -> getGenericValueText(Text.translatable("options.video.environment.vol_cloud_powder"),
                    Text.literal(v + "%")),
                v -> Options.setVolCloudPowderPercent(v, true)),
            new ResettableSliderWidget(0, 0, 150, 20,
                0, 200, Options.volCloudAmbientPercent, 100,
                v -> getGenericValueText(Text.translatable("options.video.environment.vol_cloud_ambient"),
                    Text.literal(v + "%")),
                v -> Options.setVolCloudAmbientPercent(v, true)));
    }
}
