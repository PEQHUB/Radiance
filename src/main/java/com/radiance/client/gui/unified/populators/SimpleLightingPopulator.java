package com.radiance.client.gui.unified.populators;

import static net.minecraft.client.option.GameOptions.getGenericValueText;

import com.radiance.client.gui.ResettableSliderWidget;
import com.radiance.client.gui.unified.*;
import com.radiance.client.option.Options;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.SimpleOption;
import net.minecraft.text.Text;

public class SimpleLightingPopulator implements ContentPopulator {
    @Override
    public void populate(ContentPanelWidget panel, RadianceUnifiedScreen screen) {
        var gameOptions = MinecraftClient.getInstance().options;

        // Exposure
        SettingsSection exposure = panel.addSection("options.video.category.exposure");

        SimpleOption<Boolean> manualExposure = SimpleOption.ofBoolean(
            Options.MANUAL_EXPOSURE_ENABLED_KEY,
            Options.manualExposureEnabled,
            value -> {
                Options.setManualExposureEnabled(value, true);
                screen.refreshContent();
            });
        exposure.addToggle(manualExposure.createWidget(gameOptions))
              .tooltip("Override auto-exposure with a fixed brightness value.");

        if (Options.manualExposureEnabled) {
            int sliderValue = Options.manualExposureEV100Tenths + 40;
            exposure.addSlider(new ResettableSliderWidget(0, 0, 150, 20,
                0, 240, sliderValue, 190,
                v -> getGenericValueText(Text.translatable(Options.MANUAL_EXPOSURE_KEY),
                    Text.literal(String.format("EV %.1f", (v - 40) / 10.0))),
                v -> Options.setManualExposureEV100Tenths(v - 40, true)));
        } else {
            exposure.addSlider(new ResettableSliderWidget(0, 0, 150, 20,
                0, 60, Options.exposureCompensation + 30, 30,
                v -> getGenericValueText(Text.translatable(Options.EXPOSURE_COMPENSATION_KEY),
                    Text.literal(String.format("%+.1f EV", (v - 30) / 10.0))),
                v -> Options.setExposureCompensation(v - 30, true)))
                  .tooltip("Brightness offset applied on top of auto-exposure.");
        }
    }

    @Override
    public java.util.List<UnifiedSearchOverlay.SearchEntry> getSearchEntries(String nodeId, String category) {
        return java.util.List.of(
            new UnifiedSearchOverlay.SearchEntry("Manual Exposure", category, nodeId, false),
            new UnifiedSearchOverlay.SearchEntry("Exposure Compensation", category, nodeId, false)
        );
    }
}
