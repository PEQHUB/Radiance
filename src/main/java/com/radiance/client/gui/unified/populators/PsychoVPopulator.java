package com.radiance.client.gui.unified.populators;

import static net.minecraft.client.option.GameOptions.getGenericValueText;

import com.radiance.client.gui.ResettableSliderWidget;
import com.radiance.client.gui.unified.*;
import com.radiance.client.option.Options;
import net.minecraft.text.Text;

public class PsychoVPopulator implements ContentPopulator {
    @Override
    public void populate(ContentPanelWidget panel, RadianceUnifiedScreen screen) {
        // Tone Curve
        SettingsSection toneCurve = panel.addSection(Text.literal("Tone Curve"));
        toneCurve.addSlider(new ResettableSliderWidget(0, 0, 150, 20,
            0, 300, Options.psychoHighlightsPercent, 100,
            v -> getGenericValueText(Text.translatable(Options.PSYCHO_HIGHLIGHTS_KEY), Text.literal(String.format("%.2f", v / 100.0))),
            v -> Options.setPsychoHighlights(v, true)));
        toneCurve.tooltip("Controls highlight rolloff. Higher values compress bright areas for a more filmic look.");
        toneCurve.addSlider(new ResettableSliderWidget(0, 0, 150, 20,
            0, 300, Options.psychoShadowsPercent, 100,
            v -> getGenericValueText(Text.translatable(Options.PSYCHO_SHADOWS_KEY), Text.literal(String.format("%.2f", v / 100.0))),
            v -> Options.setPsychoShadows(v, true)));
        toneCurve.tooltip("Lifts or crushes shadow detail. Higher values reveal more shadow information.");
        toneCurve.addSlider(new ResettableSliderWidget(0, 0, 150, 20,
            0, 300, Options.psychoContrastPercent, 100,
            v -> getGenericValueText(Text.translatable(Options.PSYCHO_CONTRAST_KEY), Text.literal(String.format("%.2f", v / 100.0))),
            v -> Options.setPsychoContrast(v, true)));
        toneCurve.tooltip("S-curve contrast applied to the tone curve. 1.0 is neutral.");

        // Color
        SettingsSection color = panel.addSection(Text.literal("Color"));
        color.addSlider(new ResettableSliderWidget(0, 0, 150, 20,
            0, 300, Options.psychoPurityPercent, 105,
            v -> getGenericValueText(Text.translatable(Options.PSYCHO_PURITY_KEY), Text.literal(String.format("%.2f", v / 100.0))),
            v -> Options.setPsychoPurity(v, true)));
        color.tooltip("Color saturation boost applied during tonemapping. Higher values look more vivid.");
        color.addSlider(new ResettableSliderWidget(0, 0, 150, 20,
            0, 100, Options.psychoBleachingPercent, 0,
            v -> getGenericValueText(Text.translatable(Options.PSYCHO_BLEACHING_KEY), Text.literal(String.format("%.2f", v / 100.0))),
            v -> Options.setPsychoBleaching(v, true)));
        color.tooltip("Desaturates bright highlights to simulate photochemical bleaching.");
        color.addSlider(new ResettableSliderWidget(0, 0, 150, 20,
            0, 100, Options.psychoHueRestorePercent, 0,
            v -> getGenericValueText(Text.translatable(Options.PSYCHO_HUE_RESTORE_KEY), Text.literal(String.format("%.2f", v / 100.0))),
            v -> Options.setPsychoHueRestore(v, true)));
        color.tooltip("Corrects hue shifts from tonemapping. Higher values preserve original hue.");

        // Adaptation
        SettingsSection adapt = panel.addSection(Text.literal("Adaptation"));
        adapt.addSlider(new ResettableSliderWidget(0, 0, 150, 20,
            0, 300, Options.psychoAdaptContrastPercent, 100,
            v -> getGenericValueText(Text.translatable(Options.PSYCHO_ADAPT_CONTRAST_KEY), Text.literal(String.format("%.2f", v / 100.0))),
            v -> Options.setPsychoAdaptContrast(v, true)));
        adapt.tooltip("Contrast adaptation based on scene luminance. Simulates eye adaptation.");

        // White Curve
        SettingsSection whiteCurve = panel.addSection(Text.literal("White Curve"));
        whiteCurve.addSlider(new ResettableSliderWidget(0, 0, 150, 20,
            0, 1, Options.psychoWhiteCurve, 1,
            v -> {
                String[] labels = {"Neutwo", "Naka-Rushton"};
                return getGenericValueText(Text.translatable(Options.PSYCHO_WHITE_CURVE_KEY), Text.literal(labels[v]));
            },
            v -> Options.setPsychoWhiteCurve(v, true)));
        whiteCurve.tooltip("Selects the highlight compression model. Naka-Rushton is more perceptual.");
        whiteCurve.addSlider(new ResettableSliderWidget(0, 0, 150, 20,
            10, 5000, Options.psychoClipPointTenths, 1000,
            v -> getGenericValueText(Text.translatable(Options.PSYCHO_CLIP_POINT_KEY), Text.literal(String.format("%.1f", v / 10.0))),
            v -> Options.setPsychoClipPoint(v, true)));
        whiteCurve.tooltip("Luminance where highlights approach white. Lower values roll off earlier.");
        whiteCurve.addSlider(new ResettableSliderWidget(0, 0, 150, 20,
            10, 300, Options.psychoConeExponentPercent, 100,
            v -> getGenericValueText(Text.translatable(Options.PSYCHO_CONE_EXPONENT_KEY), Text.literal(String.format("%.2f", v / 100.0))),
            v -> Options.setPsychoConeExponent(v, true)));
        whiteCurve.tooltip("Controls cone response curve shape and affects mid-tone contrast.");
        whiteCurve.addSlider(new ResettableSliderWidget(0, 0, 150, 20,
            5, 80, Options.psychoPeakSDRTenths, 20,
            v -> getGenericValueText(Text.translatable(Options.PSYCHO_PEAK_SDR_KEY), Text.literal(String.format("%.1f", v / 10.0))),
            v -> Options.setPsychoPeakSDR(v, true)));
        whiteCurve.tooltip("SDR peak luminance reference for PsychoV. Higher values preserve more highlight detail.");
    }

    @Override
    public java.util.List<UnifiedSearchOverlay.SearchEntry> getSearchEntries(String nodeId, String category) {
        return java.util.List.of(
            new UnifiedSearchOverlay.SearchEntry("PsychoV Highlights", category, nodeId, false),
            new UnifiedSearchOverlay.SearchEntry("PsychoV Shadows", category, nodeId, false),
            new UnifiedSearchOverlay.SearchEntry("PsychoV Contrast", category, nodeId, false),
            new UnifiedSearchOverlay.SearchEntry("PsychoV Purity", category, nodeId, false),
            new UnifiedSearchOverlay.SearchEntry("PsychoV Bleaching", category, nodeId, false),
            new UnifiedSearchOverlay.SearchEntry("PsychoV White Curve", category, nodeId, false),
            new UnifiedSearchOverlay.SearchEntry("PsychoV SDR Peak", category, nodeId, false)
        );
    }
}
