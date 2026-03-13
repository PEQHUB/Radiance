package com.radiance.client.gui.unified.populators;

import static net.minecraft.client.option.GameOptions.getGenericValueText;

import com.mojang.serialization.Codec;
import com.radiance.client.gui.ResettableSliderWidget;
import com.radiance.client.gui.unified.*;
import com.radiance.client.option.Options;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.SimpleOption;
import net.minecraft.text.Text;

public class SunPopulator implements ContentPopulator {
    @Override
    public void populate(ContentPanelWidget panel, RadianceUnifiedScreen screen) {
        int dim = Options.getEnvironmentEditingDimension();

        SettingsSection section = panel.addSection("options.video.environment.sun.category");

        section.addSlider(new ResettableSliderWidget(0, 0, 150, 20,
            0, 300, Options.sunSizePercent[dim], Options.PERCENT_DEFAULT,
            v -> getGenericValueText(Text.translatable("options.video.environment.sun_size"), Text.literal(v + "%")),
            v -> Options.setSunSizePercent(dim, v, true)));

        section.addSlider(new ResettableSliderWidget(0, 0, 150, 20,
            0, 300, Options.sunIntensityPercent[dim], Options.PERCENT_DEFAULT,
            v -> getGenericValueText(Text.translatable("options.video.environment.sun_intensity"), Text.literal(v + "%")),
            v -> Options.setSunIntensityPercent(dim, v, true)));

        // Orbit controls — Overworld only
        if (dim == Options.DIM_OVERWORLD) {
            SettingsSection orbit = panel.addSection("options.video.environment.sun.orbit_category");

            SimpleOption<Integer> sunPathMode = new SimpleOption<>(
                Options.SUN_PATH_MODE_KEY,
                SimpleOption.emptyTooltip(),
                (optionText, value) -> getGenericValueText(optionText,
                    Text.translatable(value == 0 ? Options.SUN_PATH_MODE_LEGACY : Options.SUN_PATH_MODE_PHYSICAL)),
                new SimpleOption.ValidatingIntSliderCallbacks(0, 1),
                Codec.intRange(0, 1),
                Options.sunPathMode,
                value -> {
                    Options.setSunPathMode(value, true);
                    screen.refreshContent();
                });
            orbit.addToggle(sunPathMode.createWidget(MinecraftClient.getInstance().options));

            if (Options.sunPathMode == 1) {
                orbit.addSlider(new ResettableSliderWidget(0, 0, 150, 20,
                    0, 90, Options.sunInclinationDeg, Options.SUN_INCLINATION_DEFAULT,
                    v -> getGenericValueText(Text.translatable(Options.SUN_INCLINATION_KEY), Text.literal(v + "\u00B0")),
                    v -> Options.setSunInclinationDeg(v, true)));

                orbit.addSlider(new ResettableSliderWidget(0, 0, 150, 20,
                    -180, 180, Options.sunAzimuthOffsetDeg, Options.SUN_AZIMUTH_OFFSET_DEFAULT,
                    v -> getGenericValueText(Text.translatable(Options.SUN_AZIMUTH_OFFSET_KEY), Text.literal(v + "\u00B0")),
                    v -> Options.setSunAzimuthOffsetDeg(v, true)));
            }
        }
    }
}
