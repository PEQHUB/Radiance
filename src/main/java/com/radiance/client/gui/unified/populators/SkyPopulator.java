package com.radiance.client.gui.unified.populators;

import static net.minecraft.client.option.GameOptions.getGenericValueText;

import com.radiance.client.gui.ResettableSliderWidget;
import com.radiance.client.gui.unified.*;
import com.radiance.client.option.Options;
import net.minecraft.text.Text;

public class SkyPopulator implements ContentPopulator {
    @Override
    public void populate(ContentPanelWidget panel, RadianceUnifiedScreen screen) {
        int dim = Options.getEnvironmentEditingDimension();

        SettingsSection section = panel.addSection("options.video.environment.sky.category");

        section.addSlider(new ResettableSliderWidget(
            0, 0, 150, 20,
            0, 300, Options.skyBrightnessPercent[dim], Options.PERCENT_DEFAULT,
            v -> getGenericValueText(Text.translatable("options.video.environment.sky_brightness"),
                Text.literal(v + "%")),
            v -> Options.setSkyBrightnessPercent(dim, v, true)));

        section.addTwoSliders(
            new ResettableSliderWidget(
                0, 0, 150, 20,
                0, 300, Options.rainBlendPercent[dim], Options.PERCENT_DEFAULT,
                v -> getGenericValueText(Text.translatable("options.video.environment.rain_blend"),
                    Text.literal(v + "%")),
                v -> Options.setRainBlendPercent(dim, v, true)),
            new ResettableSliderWidget(
                0, 0, 150, 20,
                0, 200, Options.wetSurfaceStrengthPercent, 100,
                v -> getGenericValueText(Text.translatable("options.video.environment.wet_surface"),
                    Text.literal(v + "%")),
                v -> Options.setWetSurfaceStrengthPercent(v, true)));
    }

    @Override
    public java.util.List<UnifiedSearchOverlay.SearchEntry> getSearchEntries(String nodeId, String category) {
        return java.util.List.of(
            new UnifiedSearchOverlay.SearchEntry("Sky Brightness", category, nodeId, false),
            new UnifiedSearchOverlay.SearchEntry("Rain Blend", category, nodeId, false)
        );
    }
}
