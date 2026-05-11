package com.radiance.client.gui;

import static net.minecraft.client.option.GameOptions.getGenericValueText;

import com.radiance.client.option.Options;
import com.radiance.client.util.CategoryVideoOptionEntry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.option.GameOptionsScreen;
import net.minecraft.text.Text;

public class PsychoVSettingsScreen extends GameOptionsScreen {

    private final Screen parentScreen;

    public PsychoVSettingsScreen(Screen parent) {
        super(parent, MinecraftClient.getInstance().options,
            Text.translatable(Options.CATEGORY_PSYCHO));
        this.parentScreen = parent;
    }

    @Override
    protected void addOptions() {
        // === Tone Curve ===
        this.body.addEntry(new CategoryVideoOptionEntry(
            Text.literal("Tone Curve"), body));

        // Highlights (0-300 -> 0.0 to 3.0)
        ResettableSliderWidget highlightsSlider = new ResettableSliderWidget(
            0, 0, 150, 20,
            0, 300, Options.psychoHighlightsPercent, 100,
            v -> getGenericValueText(
                Text.translatable(Options.PSYCHO_HIGHLIGHTS_KEY),
                Text.literal(String.format("%.2f", v / 100.0))),
            v -> Options.setPsychoHighlights(v, true));
        this.body.addEntry(new RadianceSettingsScreen.SliderEntry(highlightsSlider, body));

        // Shadows (0-300 -> 0.0 to 3.0)
        ResettableSliderWidget shadowsSlider = new ResettableSliderWidget(
            0, 0, 150, 20,
            0, 300, Options.psychoShadowsPercent, 100,
            v -> getGenericValueText(
                Text.translatable(Options.PSYCHO_SHADOWS_KEY),
                Text.literal(String.format("%.2f", v / 100.0))),
            v -> Options.setPsychoShadows(v, true));
        this.body.addEntry(new RadianceSettingsScreen.SliderEntry(shadowsSlider, body));

        // Contrast (0-300 -> 0.0 to 3.0)
        ResettableSliderWidget contrastSlider = new ResettableSliderWidget(
            0, 0, 150, 20,
            0, 300, Options.psychoContrastPercent, 100,
            v -> getGenericValueText(
                Text.translatable(Options.PSYCHO_CONTRAST_KEY),
                Text.literal(String.format("%.2f", v / 100.0))),
            v -> Options.setPsychoContrast(v, true));
        this.body.addEntry(new RadianceSettingsScreen.SliderEntry(contrastSlider, body));

        // === Color ===
        this.body.addEntry(new CategoryVideoOptionEntry(
            Text.literal("Color"), body));

        // Purity / Saturation (0-300 -> 0.0 to 3.0)
        ResettableSliderWidget puritySlider = new ResettableSliderWidget(
            0, 0, 150, 20,
            0, 300, Options.psychoPurityPercent, 100,
            v -> getGenericValueText(
                Text.translatable(Options.PSYCHO_PURITY_KEY),
                Text.literal(String.format("%.2f", v / 100.0))),
            v -> Options.setPsychoPurity(v, true));
        this.body.addEntry(new RadianceSettingsScreen.SliderEntry(puritySlider, body));

        // Bleaching (0-100 -> 0.0 to 1.0)
        ResettableSliderWidget bleachingSlider = new ResettableSliderWidget(
            0, 0, 150, 20,
            0, 100, Options.psychoBleachingPercent, 0,
            v -> getGenericValueText(
                Text.translatable(Options.PSYCHO_BLEACHING_KEY),
                Text.literal(String.format("%.2f", v / 100.0))),
            v -> Options.setPsychoBleaching(v, true));
        this.body.addEntry(new RadianceSettingsScreen.SliderEntry(bleachingSlider, body));

        // Hue Restore (0-100 -> 0.0 to 1.0)
        ResettableSliderWidget hueRestoreSlider = new ResettableSliderWidget(
            0, 0, 150, 20,
            0, 100, Options.psychoHueRestorePercent, 100,
            v -> getGenericValueText(
                Text.translatable(Options.PSYCHO_HUE_RESTORE_KEY),
                Text.literal(String.format("%.2f", v / 100.0))),
            v -> Options.setPsychoHueRestore(v, true));
        this.body.addEntry(new RadianceSettingsScreen.SliderEntry(hueRestoreSlider, body));

        // === Adaptation ===
        this.body.addEntry(new CategoryVideoOptionEntry(
            Text.literal("Adaptation"), body));

        // Adaptation Contrast (0-300 -> 0.0 to 3.0)
        ResettableSliderWidget adaptContrastSlider = new ResettableSliderWidget(
            0, 0, 150, 20,
            0, 300, Options.psychoAdaptContrastPercent, 100,
            v -> getGenericValueText(
                Text.translatable(Options.PSYCHO_ADAPT_CONTRAST_KEY),
                Text.literal(String.format("%.2f", v / 100.0))),
            v -> Options.setPsychoAdaptContrast(v, true));
        this.body.addEntry(new RadianceSettingsScreen.SliderEntry(adaptContrastSlider, body));

        // === White Curve ===
        this.body.addEntry(new CategoryVideoOptionEntry(
            Text.literal("White Curve"), body));

        // White Curve Mode (0 = Neutwo, 1 = Naka-Rushton)
        ResettableSliderWidget whiteCurveSlider = new ResettableSliderWidget(
            0, 0, 150, 20,
            0, 1, Options.psychoWhiteCurve, 0,
            v -> {
                String[] labels = {"Neutwo", "Naka-Rushton"};
                return getGenericValueText(
                    Text.translatable(Options.PSYCHO_WHITE_CURVE_KEY),
                    Text.literal(labels[v]));
            },
            v -> Options.setPsychoWhiteCurve(v, true));
        this.body.addEntry(new RadianceSettingsScreen.SliderEntry(whiteCurveSlider, body));

        // Clip Point (10-5000 -> 1.0 to 500.0) — Neutwo only
        ResettableSliderWidget clipPointSlider = new ResettableSliderWidget(
            0, 0, 150, 20,
            10, 5000, Options.psychoClipPointTenths, 1000,
            v -> getGenericValueText(
                Text.translatable(Options.PSYCHO_CLIP_POINT_KEY),
                Text.literal(String.format("%.1f", v / 10.0))),
            v -> Options.setPsychoClipPoint(v, true));
        this.body.addEntry(new RadianceSettingsScreen.SliderEntry(clipPointSlider, body));

        // Cone Exponent (10-300 -> 0.1 to 3.0) — Naka-Rushton only
        ResettableSliderWidget coneExpSlider = new ResettableSliderWidget(
            0, 0, 150, 20,
            10, 300, Options.psychoConeExponentPercent, 100,
            v -> getGenericValueText(
                Text.translatable(Options.PSYCHO_CONE_EXPONENT_KEY),
                Text.literal(String.format("%.2f", v / 100.0))),
            v -> Options.setPsychoConeExponent(v, true));
        this.body.addEntry(new RadianceSettingsScreen.SliderEntry(coneExpSlider, body));
    }
}
