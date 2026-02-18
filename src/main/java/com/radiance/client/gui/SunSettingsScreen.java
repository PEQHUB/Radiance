package com.radiance.client.gui;

import static net.minecraft.client.option.GameOptions.getGenericValueText;

import com.mojang.serialization.Codec;
import com.radiance.client.option.Options;
import com.radiance.client.util.CategoryVideoOptionEntry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.option.GameOptionsScreen;
import net.minecraft.client.option.SimpleOption;
import net.minecraft.text.Text;

public class SunSettingsScreen extends GameOptionsScreen {

    private final Screen parentScreen;

    public SunSettingsScreen(Screen parent) {
        super(parent, MinecraftClient.getInstance().options,
            Text.translatable("radiance.settings.environment.sun.title"));
        this.parentScreen = parent;
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

        // Orbit controls — Overworld only
        if (dim == Options.DIM_OVERWORLD) {
            this.body.addEntry(new CategoryVideoOptionEntry(
                Text.translatable("options.video.environment.sun.orbit_category"), body));

            // Sun Path Mode toggle: Legacy / Physical
            SimpleOption<Integer> sunPathMode = new SimpleOption<>(
                Options.SUN_PATH_MODE_KEY,
                SimpleOption.emptyTooltip(),
                (optionText, value) -> getGenericValueText(optionText,
                    Text.translatable(value == 0
                        ? Options.SUN_PATH_MODE_LEGACY
                        : Options.SUN_PATH_MODE_PHYSICAL)),
                new SimpleOption.ValidatingIntSliderCallbacks(0, 1),
                Codec.intRange(0, 1),
                Options.sunPathMode,
                value -> {
                    Options.setSunPathMode(value, true);
                    // Rebuild screen to show/hide inclination & azimuth sliders
                    MinecraftClient.getInstance().setScreen(new SunSettingsScreen(parentScreen));
                });
            this.body.addSingleOptionEntry(sunPathMode);

            if (Options.sunPathMode == 1) {
                // Inclination slider: 0–90 degrees, default 23 (Earth-like tilt)
                ResettableSliderWidget inclSlider = new ResettableSliderWidget(
                    0, 0, 150, 20,
                    0, 90, Options.sunInclinationDeg, Options.SUN_INCLINATION_DEFAULT,
                    v -> getGenericValueText(Text.translatable(Options.SUN_INCLINATION_KEY),
                        Text.literal(v + "\u00B0")),
                    v -> Options.setSunInclinationDeg(v, true));
                this.body.addEntry(new RadianceSettingsScreen.SliderEntry(inclSlider, body));

                // Azimuth Offset slider: -180 to +180 degrees, default 0
                ResettableSliderWidget azSlider = new ResettableSliderWidget(
                    0, 0, 150, 20,
                    -180, 180, Options.sunAzimuthOffsetDeg, Options.SUN_AZIMUTH_OFFSET_DEFAULT,
                    v -> getGenericValueText(Text.translatable(Options.SUN_AZIMUTH_OFFSET_KEY),
                        Text.literal(v + "\u00B0")),
                    v -> Options.setSunAzimuthOffsetDeg(v, true));
                this.body.addEntry(new RadianceSettingsScreen.SliderEntry(azSlider, body));
            }
        }
    }
}
