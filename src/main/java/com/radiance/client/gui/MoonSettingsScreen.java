package com.radiance.client.gui;

import static net.minecraft.client.option.GameOptions.getGenericValueText;

import com.radiance.client.option.Options;
import com.radiance.client.util.CategoryVideoOptionEntry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.option.GameOptionsScreen;
import net.minecraft.text.Text;

public class MoonSettingsScreen extends GameOptionsScreen {

    public MoonSettingsScreen(Screen parent) {
        super(parent, MinecraftClient.getInstance().options,
            Text.translatable("radiance.settings.environment.moon.title"));
    }

    @Override
    protected void addOptions() {
        int dim = Options.getEnvironmentEditingDimension();

        this.body.addEntry(new CategoryVideoOptionEntry(
            Text.translatable("options.video.environment.moon.category"), body));

        ResettableSliderWidget moonSizeSlider = new ResettableSliderWidget(
            0, 0, 150, 20,
            0, 300, Options.moonSizePercent[dim], Options.PERCENT_DEFAULT,
            v -> getGenericValueText(Text.translatable("options.video.environment.moon_size"),
                Text.literal(v + "%")),
            v -> Options.setMoonSizePercent(dim, v, true));
        this.body.addEntry(new RadianceSettingsScreen.SliderEntry(moonSizeSlider, body));

        ResettableSliderWidget moonIntensitySlider = new ResettableSliderWidget(
            0, 0, 150, 20,
            0, 300, Options.moonIntensityPercent[dim], Options.PERCENT_DEFAULT,
            v -> getGenericValueText(Text.translatable("options.video.environment.moon_intensity"),
                Text.literal(v + "%")),
            v -> Options.setMoonIntensityPercent(dim, v, true));
        this.body.addEntry(new RadianceSettingsScreen.SliderEntry(moonIntensitySlider, body));
    }
}
