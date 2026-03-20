package com.radiance.client.gui.unified.populators;

import static net.minecraft.client.option.GameOptions.getGenericValueText;

import com.radiance.client.gui.ResettableSliderWidget;
import com.radiance.client.gui.unified.*;
import com.radiance.client.option.Options;
import net.minecraft.text.Text;

public class SimpleEnvironmentPopulator implements ContentPopulator {
    @Override
    public void populate(ContentPanelWidget panel, RadianceUnifiedScreen screen) {
        int dim = Options.getEnvironmentEditingDimension();

        SettingsSection section = panel.addSection("options.video.category.environment");

        // Sun intensity + Sky brightness
        section.addTwoSliders(
            new ResettableSliderWidget(0, 0, 150, 20,
                0, 300, Options.sunIntensityPercent[dim], Options.PERCENT_DEFAULT,
                v -> getGenericValueText(Text.translatable("options.video.environment.sun_intensity"), Text.literal(v + "%")),
                v -> Options.setSunIntensityPercent(dim, v, true)),
            new ResettableSliderWidget(0, 0, 150, 20,
                0, 300, Options.skyBrightnessPercent[dim], Options.PERCENT_DEFAULT,
                v -> getGenericValueText(Text.translatable("options.video.environment.sky_brightness"), Text.literal(v + "%")),
                v -> Options.setSkyBrightnessPercent(dim, v, true)));

        // Cloud brightness + alpha
        section.addTwoSliders(
            new ResettableSliderWidget(0, 0, 150, 20,
                0, 300, Options.cloudBrightnessPercent[dim], Options.PERCENT_DEFAULT,
                v -> getGenericValueText(Text.translatable("options.video.environment.cloud_brightness"), Text.literal(v + "%")),
                v -> Options.setCloudBrightnessPercent(dim, v, true)),
            new ResettableSliderWidget(0, 0, 150, 20,
                0, 300, Options.cloudAlphaPercent[dim], Options.PERCENT_DEFAULT,
                v -> getGenericValueText(Text.translatable("options.video.environment.cloud_alpha"), Text.literal(v + "%")),
                v -> Options.setCloudAlphaPercent(dim, v, true)));

        // Water fog strength
        section.addSlider(new ResettableSliderWidget(0, 0, 150, 20,
            0, 300, Options.waterFogStrengthPercent[dim], Options.PERCENT_DEFAULT,
            v -> getGenericValueText(Text.translatable("options.video.environment.water_fog_strength"), Text.literal(v + "%")),
            v -> Options.setWaterFogStrengthPercent(dim, v, true)));
    }

    @Override
    public java.util.List<UnifiedSearchOverlay.SearchEntry> getSearchEntries(String nodeId, String category) {
        return java.util.List.of(
            new UnifiedSearchOverlay.SearchEntry("Sun Intensity", category, nodeId, false),
            new UnifiedSearchOverlay.SearchEntry("Sky Brightness", category, nodeId, false),
            new UnifiedSearchOverlay.SearchEntry("Cloud Brightness", category, nodeId, false),
            new UnifiedSearchOverlay.SearchEntry("Cloud Alpha", category, nodeId, false),
            new UnifiedSearchOverlay.SearchEntry("Water Fog", category, nodeId, false)
        );
    }
}
