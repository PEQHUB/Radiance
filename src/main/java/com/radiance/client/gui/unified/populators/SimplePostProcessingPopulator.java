package com.radiance.client.gui.unified.populators;

import static net.minecraft.client.option.GameOptions.getGenericValueText;

import com.radiance.client.gui.ResettableSliderWidget;
import com.radiance.client.gui.SelectionDropdownWidget;
import com.radiance.client.gui.unified.*;
import com.radiance.client.option.Options;
import net.minecraft.text.Text;

public class SimplePostProcessingPopulator implements ContentPopulator {
    @Override
    public void populate(ContentPanelWidget panel, RadianceUnifiedScreen screen) {
        SettingsSection section = panel.addSection("options.video.category.post_processing");

        // Sharpener mode
        SelectionDropdownWidget sharpener = new SelectionDropdownWidget(
            0, 0, 150, 20, "Sharpener",
            new String[]{"None", "CAS", "RCAS"},
            Options.sharpenerMode, value -> {
                Options.setSharpenerMode(value, true);
                screen.refreshContent();
            });
        section.addTwoWidgets(sharpener, null);
        section.tooltip("Selects post-process sharpening filter. None disables sharpening; CAS and RCAS sharpen the final image.");

        if (Options.sharpenerMode != 0) {
            section.addSlider(new ResettableSliderWidget(0, 0, 150, 20,
                0, 100, Options.casSharpnessPercent, 50,
                v -> getGenericValueText(Text.translatable(Options.CAS_SHARPNESS_KEY), Text.literal(v + "%")),
                v -> Options.setCasSharpnessPercent(v, true)));
            section.tooltip("Sharpening intensity for CAS/RCAS. Higher values add clarity but can create halos or shimmer.");
        }

        // Saturation
        section.addSlider(new ResettableSliderWidget(0, 0, 150, 20,
            0, 200, Options.saturationPercent, Options.SATURATION_DEFAULT_PERCENT,
            v -> getGenericValueText(Text.translatable(Options.SATURATION_KEY), Text.literal(v + "%")),
            v -> Options.setSaturation(v, true)))
              .tooltip("Post-tonemap global color saturation. Higher values make colors more vivid.");
    }

    @Override
    public java.util.List<UnifiedSearchOverlay.SearchEntry> getSearchEntries(String nodeId, String category) {
        return java.util.List.of(
            new UnifiedSearchOverlay.SearchEntry("Sharpener", category, nodeId, false),
            new UnifiedSearchOverlay.SearchEntry("Sharpness", category, nodeId, false),
            new UnifiedSearchOverlay.SearchEntry("Saturation", category, nodeId, false)
        );
    }
}
