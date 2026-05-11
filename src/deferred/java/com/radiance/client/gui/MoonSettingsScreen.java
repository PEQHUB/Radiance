package com.radiance.client.gui;

import static net.minecraft.client.option.GameOptions.getGenericValueText;

import com.radiance.client.option.Options;
import com.radiance.client.util.CategoryVideoOptionEntry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.option.GameOptionsScreen;
import net.minecraft.client.option.SimpleOption;
import net.minecraft.text.Text;

public class MoonSettingsScreen extends GameOptionsScreen {

    private final Screen parentScreen;

    public MoonSettingsScreen(Screen parent) {
        super(parent, MinecraftClient.getInstance().options,
            Text.translatable("radiance.settings.environment.moon.title"));
        this.parentScreen = parent;
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

        // Orbit controls — Overworld only
        if (dim == Options.DIM_OVERWORLD) {
            this.body.addEntry(new CategoryVideoOptionEntry(
                Text.translatable("options.video.environment.moon.orbit_category"), body));

            // "Follow Sun" toggle (default: ON)
            SimpleOption<Boolean> moonFollowSun = SimpleOption.ofBoolean(
                Options.MOON_FOLLOW_SUN_KEY, Options.moonFollowSun,
                value -> {
                    Options.setMoonFollowSun(value, true);
                    // Rebuild screen to show/hide independent orbit sliders
                    MinecraftClient.getInstance().setScreen(new MoonSettingsScreen(parentScreen));
                });
            this.body.addSingleOptionEntry(moonFollowSun);

            if (!Options.moonFollowSun) {
                // Independent inclination: 0–90 degrees
                ResettableSliderWidget inclSlider = new ResettableSliderWidget(
                    0, 0, 150, 20,
                    0, 90, Options.moonInclinationDeg, Options.MOON_INCLINATION_DEFAULT,
                    v -> getGenericValueText(Text.translatable(Options.MOON_INCLINATION_KEY),
                        Text.literal(v + "\u00B0")),
                    v -> Options.setMoonInclinationDeg(v, true));
                this.body.addEntry(new RadianceSettingsScreen.SliderEntry(inclSlider, body));

                // Independent azimuth offset: -180 to +180 degrees
                ResettableSliderWidget azSlider = new ResettableSliderWidget(
                    0, 0, 150, 20,
                    -180, 180, Options.moonAzimuthOffsetDeg, Options.MOON_AZIMUTH_OFFSET_DEFAULT,
                    v -> getGenericValueText(Text.translatable(Options.MOON_AZIMUTH_OFFSET_KEY),
                        Text.literal(v + "\u00B0")),
                    v -> Options.setMoonAzimuthOffsetDeg(v, true));
                this.body.addEntry(new RadianceSettingsScreen.SliderEntry(azSlider, body));
            }
        }
    }
}
