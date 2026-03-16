package com.radiance.client.gui.unified.populators;

import static net.minecraft.client.option.GameOptions.getGenericValueText;

import com.radiance.client.gui.ResettableSliderWidget;
import com.radiance.client.gui.unified.*;
import com.radiance.client.option.Options;
import net.minecraft.text.Text;

public class FireworksPopulator implements ContentPopulator {
    @Override
    public void populate(ContentPanelWidget panel, RadianceUnifiedScreen screen) {
        SettingsSection emission = panel.addSection(Text.literal("Firework Emission"));

        // Spark emission in nits (0-5000)
        emission.addSlider(new ResettableSliderWidget(0, 0, 150, 20,
            0, 5000, Options.fireworkSparkEmissionNits, 20,
            v -> getGenericValueText(Text.literal("Spark Intensity"),
                Text.literal(v + " nits")),
            v -> Options.setFireworkSparkEmission(v, true)));

        // Flash emission in nits (0-10000)
        emission.addSlider(new ResettableSliderWidget(0, 0, 150, 20,
            0, 10000, Options.fireworkFlashEmissionNits, 2000,
            v -> getGenericValueText(Text.literal("Flash Intensity"),
                Text.literal(v + " nits")),
            v -> Options.setFireworkFlashEmission(v, true)));

        // Per-dye-color: Temperature + Wavelength + Purity (same as emissive blocks)
        for (int i = 0; i < Options.FIREWORK_COLOR_COUNT; i++) {
            final int idx = i;
            String name = Options.FIREWORK_COLOR_NAMES[idx];

            SettingsSection color = panel.addSection(Text.literal(name));

            // Temperature (800K - 15000K)
            color.addSlider(new ResettableSliderWidget(0, 0, 150, 20,
                800, 15000, Options.fireworkColorTemperatures[idx],
                Options.FIREWORK_COLOR_TEMP_DEFAULTS[idx],
                v -> getGenericValueText(Text.literal("Temperature"),
                    Text.literal(v + "K")),
                v -> Options.setFireworkColorTemperature(idx, v, true)));

            // Wavelength (0 = pure blackbody, 380-780 nm = spectral line)
            color.addSlider(new ResettableSliderWidget(0, 0, 150, 20,
                0, 780, Options.fireworkColorWavelength[idx],
                Options.FIREWORK_WAVELENGTH_DEFAULTS[idx],
                v -> getGenericValueText(Text.literal("Wavelength"),
                    Text.literal(v == 0 ? "Blackbody" : v + " nm")),
                v -> Options.setFireworkColorWavelength(idx, v, true)));

            // Purity (0-100%)
            color.addSlider(new ResettableSliderWidget(0, 0, 150, 20,
                0, 100, Options.fireworkColorPurity[idx],
                Options.FIREWORK_PURITY_DEFAULTS[idx],
                v -> getGenericValueText(Text.literal("Purity"),
                    Text.literal(v + "%")),
                v -> Options.setFireworkColorPurity(idx, v, true)));
        }
    }
}
