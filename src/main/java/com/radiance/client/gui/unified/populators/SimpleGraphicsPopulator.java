package com.radiance.client.gui.unified.populators;

import static net.minecraft.client.option.GameOptions.getGenericValueText;

import com.mojang.serialization.Codec;
import com.radiance.client.gui.ResettableSliderWidget;
import com.radiance.client.gui.SelectionDropdownWidget;
import com.radiance.client.gui.unified.*;
import com.radiance.client.option.Options;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.SimpleOption;
import net.minecraft.text.Text;

public class SimpleGraphicsPopulator implements ContentPopulator {
    @Override
    public void populate(ContentPanelWidget panel, RadianceUnifiedScreen screen) {
        var gameOptions = MinecraftClient.getInstance().options;

        SettingsSection section = panel.addSection("options.video.category.graphics");

        // Ray Bounces + Upscaler Mode
        SimpleOption<Integer> rayBounces = new SimpleOption<>(
            Options.RAY_BOUNCES_KEY,
            SimpleOption.emptyTooltip(),
            (optionText, value) -> getGenericValueText(optionText, Text.literal(Integer.toString(value))),
            new SimpleOption.ValidatingIntSliderCallbacks(0, 32),
            Codec.intRange(0, 32),
            Options.rayBounces,
            value -> Options.setRayBounces(value, true));

        SelectionDropdownWidget upscalerMode = new SelectionDropdownWidget(
            0, 0, 150, 20, "Upscaler",
            new String[]{"DLSS-RR", "FSR3", "Off"},
            Options.upscalerMode, value -> {
                Options.setUpscalerMode(value, true);
                screen.refreshContent();
            });

        section.addTwoWidgets(rayBounces.createWidget(gameOptions), upscalerMode)
              .tooltip("Ray Bounces = max light reflections. Upscaler reconstructs full-res image from lower-res render.");

        // Quality preset (when upscaler active)
        if (Options.upscalerMode != 2) {
            SelectionDropdownWidget quality = new SelectionDropdownWidget(
                0, 0, 150, 20, "Quality",
                new String[]{"Performance", "Balanced", "Quality", "Native", "Custom"},
                Options.upscalerQuality, value -> {
                    Options.setUpscalerQuality(value, true);
                    screen.refreshContent();
                });
            section.addTwoWidgets(quality, null)
                  .tooltip("Controls render resolution. Performance = fastest, Quality = best image.");

            if (Options.upscalerQuality == 4) {
                section.addSlider(new ResettableSliderWidget(0, 0, 150, 20,
                    1, 100, Options.upscalerResOverride, 67,
                    v -> getGenericValueText(Text.translatable(Options.UPSCALER_RES_OVERRIDE_KEY), Text.literal(v + "%")),
                    v -> Options.setUpscalerResOverride(v, true)));
            }
        }
    }

    @Override
    public java.util.List<UnifiedSearchOverlay.SearchEntry> getSearchEntries(String nodeId, String category) {
        return java.util.List.of(
            new UnifiedSearchOverlay.SearchEntry("Ray Bounces", category, nodeId, false),
            new UnifiedSearchOverlay.SearchEntry("Upscaler Mode", category, nodeId, false),
            new UnifiedSearchOverlay.SearchEntry("Upscaler Quality", category, nodeId, false),
            new UnifiedSearchOverlay.SearchEntry("Render Resolution", category, nodeId, false)
        );
    }
}
