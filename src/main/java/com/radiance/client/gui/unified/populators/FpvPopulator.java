package com.radiance.client.gui.unified.populators;

import static net.minecraft.client.option.GameOptions.getGenericValueText;

import com.radiance.client.gui.ResettableSliderWidget;
import com.radiance.client.gui.unified.*;
import com.radiance.client.option.Options;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.SimpleOption;
import net.minecraft.text.Text;

import java.util.List;

public class FpvPopulator implements ContentPopulator {
    @Override
    public void populate(ContentPanelWidget panel, RadianceUnifiedScreen screen) {
        var gameOptions = MinecraftClient.getInstance().options;

        SettingsSection section = panel.addSection(Options.CATEGORY_FPV);

        // FPV enabled toggle
        SimpleOption<Boolean> fpvEnabled = SimpleOption.ofBoolean(
            Options.FPV_ENABLED_KEY, Options.fpvEnabled,
            value -> {
                Options.setFpvEnabled(value, true);
                screen.refreshContent();
            });
        section.addToggle(fpvEnabled.createWidget(gameOptions))
              .tooltip("Show your player body in first-person view.");

        if (Options.fpvEnabled) {
            // Forward + Vertical offset (paired)
            section.addTwoSliders(
                new ResettableSliderWidget(0, 0, 150, 20,
                    -30, 30, Options.fpvOffsetForward, -20,
                    v -> getGenericValueText(Text.translatable(Options.FPV_OFFSET_FORWARD_KEY), Text.literal(v + " cm")),
                    v -> Options.setFpvOffsetForward(v, true)),
                new ResettableSliderWidget(0, 0, 150, 20,
                    -30, 30, Options.fpvOffsetVertical, 0,
                    v -> getGenericValueText(Text.translatable(Options.FPV_OFFSET_VERTICAL_KEY), Text.literal(v + " cm")),
                    v -> Options.setFpvOffsetVertical(v, true)))
                  .tooltip("Forward = body+head distance from camera (negative = closer). Vertical = up/down.");

            // Lateral offset (solo)
            section.addSlider(new ResettableSliderWidget(0, 0, 150, 20,
                -20, 20, Options.fpvOffsetLateral, 0,
                v -> getGenericValueText(Text.translatable(Options.FPV_OFFSET_LATERAL_KEY), Text.literal(v + " cm")),
                v -> Options.setFpvOffsetLateral(v, true)))
                  .tooltip("Left/right offset from body center.");
        }
    }

    @Override
    public List<UnifiedSearchOverlay.SearchEntry> getSearchEntries(String nodeId, String category) {
        return List.of(
            new UnifiedSearchOverlay.SearchEntry("First-Person View", category, nodeId, false),
            new UnifiedSearchOverlay.SearchEntry("FPV Forward Offset", category, nodeId, false),
            new UnifiedSearchOverlay.SearchEntry("FPV Vertical Offset", category, nodeId, false),
            new UnifiedSearchOverlay.SearchEntry("FPV Lateral Offset", category, nodeId, false)
        );
    }
}
