package com.radiance.client.gui;

import static net.minecraft.client.option.GameOptions.getGenericValueText;

import com.radiance.client.option.Options;
import com.radiance.client.util.CategoryVideoOptionEntry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.option.GameOptionsScreen;
import net.minecraft.text.Text;

public class SunSettingsScreen extends GameOptionsScreen {

    public SunSettingsScreen(Screen parent) {
        super(parent, MinecraftClient.getInstance().options,
            Text.translatable("radiance.settings.environment.sun.title"));
    }

    @Override
    protected void addOptions() {
        int dim = Options.getEnvironmentEditingDimension();

        this.body.addEntry(new CategoryVideoOptionEntry(
            Text.translatable("options.video.environment.sun.category"), body));

        ResettableSliderWidget sunSizeSlider = new ResettableSliderWidget(
            0, 0, 150, 20,
            0, 300, Options.sunSizePercent[dim], Options.PERCENT_DEFAULT,
            v -> getGenericValueText(Text.translatable("options.video.environment.sun_size"),
                Text.literal(v + "%")),
            v -> Options.setSunSizePercent(dim, v, true));
        this.body.addEntry(new RadianceSettingsScreen.SliderEntry(sunSizeSlider, body));

        ResettableSliderWidget sunIntensitySlider = new ResettableSliderWidget(
            0, 0, 150, 20,
            0, 300, Options.sunIntensityPercent[dim], Options.PERCENT_DEFAULT,
            v -> getGenericValueText(Text.translatable("options.video.environment.sun_intensity"),
                Text.literal(v + "%")),
            v -> Options.setSunIntensityPercent(dim, v, true));
        this.body.addEntry(new RadianceSettingsScreen.SliderEntry(sunIntensitySlider, body));
    }
}
