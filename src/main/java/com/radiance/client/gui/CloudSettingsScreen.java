package com.radiance.client.gui;

import static net.minecraft.client.option.GameOptions.getGenericValueText;

import com.radiance.client.option.Options;
import com.radiance.client.util.CategoryVideoOptionEntry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.option.GameOptionsScreen;
import net.minecraft.text.Text;

public class CloudSettingsScreen extends GameOptionsScreen {

    public CloudSettingsScreen(Screen parent) {
        super(parent, MinecraftClient.getInstance().options,
            Text.translatable("radiance.settings.environment.clouds.title"));
    }

    @Override
    protected void addOptions() {
        int dim = Options.getEnvironmentEditingDimension();

        this.body.addEntry(new CategoryVideoOptionEntry(
            Text.translatable("options.video.environment.clouds.category"), body));

        ResettableSliderWidget cloudBrightnessSlider = new ResettableSliderWidget(
            0, 0, 150, 20,
            0, 300, Options.cloudBrightnessPercent[dim], Options.PERCENT_DEFAULT,
            v -> getGenericValueText(Text.translatable("options.video.environment.cloud_brightness"),
                Text.literal(v + "%")),
            v -> Options.setCloudBrightnessPercent(dim, v, true));
        this.body.addEntry(new RadianceSettingsScreen.SliderEntry(cloudBrightnessSlider, body));

        ResettableSliderWidget cloudAlphaSlider = new ResettableSliderWidget(
            0, 0, 150, 20,
            0, 300, Options.cloudAlphaPercent[dim], Options.PERCENT_DEFAULT,
            v -> getGenericValueText(Text.translatable("options.video.environment.cloud_alpha"),
                Text.literal(v + "%")),
            v -> Options.setCloudAlphaPercent(dim, v, true));
        this.body.addEntry(new RadianceSettingsScreen.SliderEntry(cloudAlphaSlider, body));

        ResettableSliderWidget cloudHeightSlider = new ResettableSliderWidget(
            0, 0, 150, 20,
            -64, 64, Options.cloudHeightOffset[dim], 0,
            v -> getGenericValueText(Text.translatable("options.video.environment.cloud_height_offset"),
                Text.literal(v + "")),
            v -> Options.setCloudHeightOffset(dim, v, true));
        this.body.addEntry(new RadianceSettingsScreen.SliderEntry(cloudHeightSlider, body));

        this.body.addEntry(new CategoryVideoOptionEntry(
            Text.translatable("options.video.environment.clouds.volumetric.category"), body));

        ResettableSliderWidget cloudDetailScaleSlider = new ResettableSliderWidget(
            0, 0, 150, 20,
            25, 300, Options.cloudDetailScalePercent[dim], Options.CLOUD_DETAIL_SCALE_DEFAULT_PERCENT,
            v -> getGenericValueText(Text.translatable("options.video.environment.cloud_detail_scale"),
                Text.literal(v + "%")),
            v -> Options.setCloudDetailScalePercent(dim, v, true));
        this.body.addEntry(new RadianceSettingsScreen.SliderEntry(cloudDetailScaleSlider, body));

        ResettableSliderWidget cloudDetailStrengthSlider = new ResettableSliderWidget(
            0, 0, 150, 20,
            0, 300, Options.cloudDetailStrengthPercent[dim], Options.CLOUD_DETAIL_STRENGTH_DEFAULT_PERCENT,
            v -> getGenericValueText(Text.translatable("options.video.environment.cloud_detail_strength"),
                Text.literal(v + "%")),
            v -> Options.setCloudDetailStrengthPercent(dim, v, true));
        this.body.addEntry(new RadianceSettingsScreen.SliderEntry(cloudDetailStrengthSlider, body));

        ResettableSliderWidget cloudShadowStrengthSlider = new ResettableSliderWidget(
            0, 0, 150, 20,
            0, 200, Options.cloudShadowStrengthPercent[dim], Options.PERCENT_DEFAULT,
            v -> getGenericValueText(Text.translatable("options.video.environment.cloud_shadow_strength"),
                Text.literal(v + "%")),
            v -> Options.setCloudShadowStrengthPercent(dim, v, true));
        this.body.addEntry(new RadianceSettingsScreen.SliderEntry(cloudShadowStrengthSlider, body));

        ResettableSliderWidget cloudThicknessSlider = new ResettableSliderWidget(
            0, 0, 150, 20,
            1, 16, Options.cloudThicknessBlocks[dim], 4,
            v -> getGenericValueText(Text.translatable("options.video.environment.cloud_thickness"),
                Text.literal(v + " blocks")),
            v -> Options.setCloudThicknessBlocks(dim, v, true));
        this.body.addEntry(new RadianceSettingsScreen.SliderEntry(cloudThicknessSlider, body));

        ResettableSliderWidget cloudDensitySlider = new ResettableSliderWidget(
            0, 0, 150, 20,
            0, 300, Options.cloudDensityPercent[dim], Options.PERCENT_DEFAULT,
            v -> getGenericValueText(Text.translatable("options.video.environment.cloud_density"),
                Text.literal(v + "%")),
            v -> Options.setCloudDensityPercent(dim, v, true));
        this.body.addEntry(new RadianceSettingsScreen.SliderEntry(cloudDensitySlider, body));

        ResettableSliderWidget cloudShadowNoiseSlider = new ResettableSliderWidget(
            0, 0, 150, 20,
            0, 1, Options.cloudNoiseAffectsShadows[dim], dim == 0 ? 1 : 0,
            v -> getGenericValueText(Text.translatable("options.video.environment.cloud_shadow_noise"),
                v == 1 ? Text.translatable("options.on") : Text.translatable("options.off")),
            v -> Options.setCloudNoiseAffectsShadows(dim, v == 1, true));
        this.body.addEntry(new RadianceSettingsScreen.SliderEntry(cloudShadowNoiseSlider, body));

    }
}
