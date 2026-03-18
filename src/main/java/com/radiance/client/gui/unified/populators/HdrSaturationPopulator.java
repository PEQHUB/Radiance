package com.radiance.client.gui.unified.populators;

import static net.minecraft.client.option.GameOptions.getGenericValueText;

import com.mojang.serialization.Codec;
import com.radiance.client.gui.ResettableSliderWidget;
import com.radiance.client.gui.unified.*;
import com.radiance.client.option.Options;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.SimpleOption;
import net.minecraft.text.Text;

public class HdrSaturationPopulator implements ContentPopulator {

    private static final String[] SDR_TONEMAP_MODE_KEYS = {
        Options.TONEMAP_MODE_PBR_NEUTRAL,
        Options.TONEMAP_MODE_REINHARD_EXTENDED,
        Options.TONEMAP_MODE_ACES,
        Options.TONEMAP_MODE_AGX,
        Options.TONEMAP_MODE_LOTTES,
        Options.TONEMAP_MODE_FROSTBITE,
        Options.TONEMAP_MODE_UNCHARTED2,
        Options.TONEMAP_MODE_GT,
        Options.TONEMAP_MODE_PSYCHOVISUAL,
    };

    // Parameter definitions per tonemapper mode: label, min, max, default
    private record TmParam(String label, float min, float max, float defaultVal) {}

    private static final TmParam[][] TM_PARAMS = {
        // Mode 0: PBR Neutral
        { new TmParam("Compression Start", 0.1f, 0.99f, 0.76f),
          new TmParam("Desaturation", 0.0f, 1.0f, 0.15f) },
        // Mode 1: Reinhard
        { new TmParam("White Point", 1.0f, 20.0f, 4.0f) },
        // Mode 2: ACES
        { new TmParam("Pre-Exposure", 0.5f, 2.0f, 1.0f) },
        // Mode 3: AgX (Blender Base look)
        { new TmParam("Contrast (Look)", 0.5f, 2.0f, 1.0f),
          new TmParam("Saturation (Look)", 0.5f, 2.0f, 1.0f) },
        // Mode 4: Lottes (GDC 2016 canonical)
        { new TmParam("Contrast", 0.5f, 3.0f, 2.0f),
          new TmParam("Shoulder", 0.5f, 1.5f, 1.0f),
          new TmParam("HDR Max", 2.0f, 32.0f, 16.0f),
          new TmParam("Mid In", 0.05f, 0.5f, 0.18f),
          new TmParam("Mid Out", 0.05f, 0.5f, 0.18f) },
        // Mode 5: Frostbite
        { new TmParam("Linear End", 0.05f, 0.5f, 0.25f),
          new TmParam("Shoulder Strength", 0.5f, 5.0f, 2.0f) },
        // Mode 6: Uncharted 2 (Hable canonical)
        { new TmParam("Shoulder Strength", 0.01f, 0.5f, 0.15f),
          new TmParam("Linear Strength", 0.1f, 1.0f, 0.50f),
          new TmParam("Linear Angle", 0.01f, 0.5f, 0.10f),
          new TmParam("Toe Strength", 0.01f, 0.5f, 0.20f),
          new TmParam("Toe Numerator", 0.001f, 0.1f, 0.02f),
          new TmParam("Toe Denominator", 0.1f, 0.5f, 0.30f),
          new TmParam("White Point", 2.0f, 20.0f, 11.2f) },
        // Mode 7: GT
        { new TmParam("Contrast", 0.5f, 2.0f, 1.0f),
          new TmParam("Linear Start", 0.05f, 0.5f, 0.22f),
          new TmParam("Linear Length", 0.1f, 0.9f, 0.4f),
          new TmParam("Black Curve", 0.5f, 3.0f, 1.33f),
          new TmParam("Black Lift", -0.05f, 0.1f, 0.0f) },
        // Mode 8: PsychoVisual (uses dedicated PsychoV sliders, no per-mode params)
        {},
    };

    @Override
    public void populate(ContentPanelWidget panel, RadianceUnifiedScreen screen) {
        var gameOptions = MinecraftClient.getInstance().options;

        // ── SDR Tonemapping ──
        SettingsSection tonemap = panel.addSection(Text.literal("SDR Tonemapping"));

        // Tonemapper mode selector (0-8)
        SimpleOption<Integer> tonemapMode = new SimpleOption<>(
            Options.TONEMAP_MODE_KEY,
            SimpleOption.emptyTooltip(),
            (optionText, value) -> getGenericValueText(optionText,
                Text.translatable(SDR_TONEMAP_MODE_KEYS[Math.max(0, Math.min(8, value))])),
            new SimpleOption.ValidatingIntSliderCallbacks(0, 8),
            Codec.intRange(0, 8),
            Options.tonemappingMode,
            value -> {
                Options.setTonemappingMode(value, true);
                screen.refreshContent();
            });

        SimpleOption<Integer> sdrTransferFn = new SimpleOption<>(
            Options.SDR_TRANSFER_FUNCTION_KEY,
            SimpleOption.emptyTooltip(),
            (optionText, value) -> getGenericValueText(optionText,
                Text.translatable(value == Options.SDR_TRANSFER_FUNCTION_SRGB
                    ? Options.SDR_TRANSFER_FUNCTION_SRGB_KEY
                    : Options.SDR_TRANSFER_FUNCTION_GAMMA_22_KEY)),
            new SimpleOption.ValidatingIntSliderCallbacks(0, 1),
            Codec.intRange(0, 1),
            Options.sdrTransferFunction,
            value -> Options.setSdrTransferFunction(value, true));
        tonemap.addTwoWidgets(tonemapMode.createWidget(gameOptions), sdrTransferFn.createWidget(gameOptions));

        tonemap.addSlider(new ResettableSliderWidget(0, 0, 150, 20,
            0, 400, Options.saturationPercent, Options.SATURATION_DEFAULT_PERCENT,
            v -> getGenericValueText(Text.translatable(Options.SATURATION_KEY),
                Text.literal(String.format("%.2f", v / 100.0))),
            v -> Options.setSaturation(v, true)));

        // Per-tonemapper parameter sliders
        int mode = Options.tonemappingMode;
        if (mode >= 0 && mode < TM_PARAMS.length) {
            TmParam[] params = TM_PARAMS[mode];
            for (int i = 0; i < params.length; i++) {
                TmParam p = params[i];
                final int paramIdx = i;
                final int currentMode = mode;
                int sliderMin = Math.round(p.min * 1000);
                int sliderMax = Math.round(p.max * 1000);
                int sliderVal = Math.round(Options.tonemapParams[mode][paramIdx] * 1000);
                int sliderDefault = Math.round(p.defaultVal * 1000);

                tonemap.addSlider(new ResettableSliderWidget(0, 0, 150, 20,
                    sliderMin, sliderMax, sliderVal, sliderDefault,
                    v -> getGenericValueText(Text.literal(p.label),
                        Text.literal(String.format("%.3f", v / 1000.0f))),
                    v -> Options.setTonemapParam(currentMode, paramIdx, v / 1000.0f, true)));
            }
        }

        // ── BRDF ──
        SettingsSection color = panel.addSection(Text.literal("BRDF"));

        SimpleOption<Boolean> msGGX = SimpleOption.ofBoolean(
            Options.MULTI_SCATTER_GGX_KEY, Options.multiScatterGGX,
            value -> Options.setMultiScatterGGX(value, true));
        SimpleOption<Boolean> eonDiffuse = SimpleOption.ofBoolean(
            Options.EON_DIFFUSE_KEY, Options.eonDiffuse,
            value -> Options.setEonDiffuse(value, true));
        color.addTwoWidgets(msGGX.createWidget(gameOptions), eonDiffuse.createWidget(gameOptions));

        // HDR10 Output (conditional)
        if (Options.isHdrSupported()) {
            SettingsSection hdr = panel.addSection(Options.CATEGORY_HDR);

            SimpleOption<Boolean> hdrEnabled = SimpleOption.ofBoolean(
                Options.HDR_ENABLED_KEY, Options.hdrEnabled,
                value -> {
                    Options.setHdrEnabled(value, true);
                    screen.refreshContent();
                });
            hdr.addToggle(hdrEnabled.createWidget(gameOptions));

            if (Options.hdrEnabled) {
                // HDR tonemapper selector: 0 = PsychoVisual, 1 = ITU EETF (Hermite)
                String[] HDR_TM_KEYS = { Options.HDR_TONEMAP_PSYCHOVISUAL, Options.HDR_TONEMAP_BT2390 };
                SimpleOption<Integer> hdrTmMode = new SimpleOption<>(
                    Options.HDR_TONEMAP_MODE_KEY,
                    SimpleOption.emptyTooltip(),
                    (optionText, value) -> getGenericValueText(optionText,
                        Text.translatable(HDR_TM_KEYS[Math.max(0, Math.min(1, value))])),
                    new SimpleOption.ValidatingIntSliderCallbacks(0, 1),
                    Codec.intRange(0, 1),
                    Options.hdrTonemapMode,
                    value -> {
                        Options.setHdrTonemapMode(value, true);
                        screen.refreshContent();
                    });
                hdr.addToggle(hdrTmMode.createWidget(gameOptions));

                hdr.addTwoSliders(
                    new ResettableSliderWidget(0, 0, 150, 20,
                        10, 1000, Options.hdrPeakNits / 10, 100,
                        v -> getGenericValueText(Text.translatable(Options.HDR_PEAK_NITS_KEY), Text.literal((v * 10) + " nits")),
                        v -> Options.setHdrPeakNits(v * 10, true)),
                    new ResettableSliderWidget(0, 0, 150, 20,
                        1, 500, Options.hdrPaperWhiteNits, 203,
                        v -> getGenericValueText(Text.translatable(Options.HDR_PAPER_WHITE_NITS_KEY), Text.literal(v + " nits")),
                        v -> Options.setHdrPaperWhiteNits(v, true)));

                hdr.addSlider(new ResettableSliderWidget(0, 0, 150, 20,
                    5, 30, Options.hdrUiBrightnessNits / 10, 10,
                    v -> getGenericValueText(Text.translatable("options.video.hdr_ui_brightness_nits"), Text.literal((v * 10) + " nits")),
                    v -> Options.setHdrUiBrightnessNits(v * 10, true)));
            }
        }
    }
}
