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
    }
}
