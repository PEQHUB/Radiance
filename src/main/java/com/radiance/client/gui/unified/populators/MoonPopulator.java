package com.radiance.client.gui.unified.populators;

import static net.minecraft.client.option.GameOptions.getGenericValueText;

import com.radiance.client.gui.ResettableSliderWidget;
import com.radiance.client.gui.unified.*;
import com.radiance.client.option.Options;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.SimpleOption;
import net.minecraft.text.Text;

public class MoonPopulator implements ContentPopulator {
    @Override
    public void populate(ContentPanelWidget panel, RadianceUnifiedScreen screen) {
        int dim = Options.getEnvironmentEditingDimension();

        SettingsSection section = panel.addSection("options.video.environment.moon.category");

        section.addSlider(new ResettableSliderWidget(0, 0, 150, 20,
            0, 300, Options.moonSizePercent[dim], Options.PERCENT_DEFAULT,
            v -> getGenericValueText(Text.translatable("options.video.environment.moon_size"), Text.literal(v + "%")),
            v -> Options.setMoonSizePercent(dim, v, true)));

        section.addSlider(new ResettableSliderWidget(0, 0, 150, 20,
            0, 300, Options.moonIntensityPercent[dim], Options.PERCENT_DEFAULT,
            v -> getGenericValueText(Text.translatable("options.video.environment.moon_intensity"), Text.literal(v + "%")),
            v -> Options.setMoonIntensityPercent(dim, v, true)));

        if (dim == Options.DIM_OVERWORLD) {
            SettingsSection orbit = panel.addSection("options.video.environment.moon.orbit_category");

            SimpleOption<Boolean> moonFollowSun = SimpleOption.ofBoolean(
                Options.MOON_FOLLOW_SUN_KEY, Options.moonFollowSun,
                value -> {
                    Options.setMoonFollowSun(value, true);
                    screen.refreshContent();
                });
            orbit.addToggle(moonFollowSun.createWidget(MinecraftClient.getInstance().options));

            if (!Options.moonFollowSun) {
                orbit.addSlider(new ResettableSliderWidget(0, 0, 150, 20,
                    0, 90, Options.moonInclinationDeg, Options.MOON_INCLINATION_DEFAULT,
                    v -> getGenericValueText(Text.translatable(Options.MOON_INCLINATION_KEY), Text.literal(v + "\u00B0")),
                    v -> Options.setMoonInclinationDeg(v, true)));

                orbit.addSlider(new ResettableSliderWidget(0, 0, 150, 20,
                    -180, 180, Options.moonAzimuthOffsetDeg, Options.MOON_AZIMUTH_OFFSET_DEFAULT,
                    v -> getGenericValueText(Text.translatable(Options.MOON_AZIMUTH_OFFSET_KEY), Text.literal(v + "\u00B0")),
                    v -> Options.setMoonAzimuthOffsetDeg(v, true)));
            }
        }
    }

    @Override
    public java.util.List<UnifiedSearchOverlay.SearchEntry> getSearchEntries(String nodeId, String category) {
        return java.util.List.of(
            new UnifiedSearchOverlay.SearchEntry("Moon Size", category, nodeId, false),
            new UnifiedSearchOverlay.SearchEntry("Moon Intensity", category, nodeId, false)
        );
    }
}
