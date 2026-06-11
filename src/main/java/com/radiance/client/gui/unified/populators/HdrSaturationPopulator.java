package com.radiance.client.gui.unified.populators;

import static net.minecraft.client.option.GameOptions.getGenericValueText;

import com.radiance.client.gui.ResettableSliderWidget;
import com.radiance.client.gui.SelectionDropdownWidget;
import com.radiance.client.gui.unified.*;
import com.radiance.client.option.Options;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.SimpleOption;
import net.minecraft.text.Text;

public class HdrSaturationPopulator implements ContentPopulator {

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
        // Mode 3: AgX Eary Chroma — P0 = Look Preset (handled as dropdown below), P1/P2 = fine-tune
        { new TmParam("Extra Contrast", 0.5f, 2.0f, 1.0f),
          new TmParam("Extra Saturation", 0.5f, 2.0f, 1.0f) },
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
        SelectionDropdownWidget tonemapMode = new SelectionDropdownWidget(
            0, 0, 150, 20, "Tonemapper",
            new String[]{"PBR Neutral", "Reinhard", "ACES", "AgX", "Lottes",
                         "Frostbite", "Uncharted 2", "GT", "PsychoVisual"},
            Options.tonemappingMode, value -> {
                Options.setTonemappingMode(value, true);
                screen.refreshContent();
            });

        SelectionDropdownWidget sdrTransferFn = new SelectionDropdownWidget(
            0, 0, 150, 20, "Transfer Fn",
            new String[]{"Gamma 2.2", "sRGB"},
            Options.sdrTransferFunction,
            value -> Options.setSdrTransferFunction(value, true));
        tonemap.addTwoWidgets(tonemapMode, sdrTransferFn)
              .tooltip("Tonemapper maps HDR scene values into the displayable SDR range. Transfer Fn selects the SDR output transfer function: Gamma 2.2 or sRGB.");

        tonemap.addSlider(new ResettableSliderWidget(0, 0, 150, 20,
            0, 400, Options.saturationPercent, Options.SATURATION_DEFAULT_PERCENT,
            v -> getGenericValueText(Text.translatable(Options.SATURATION_KEY),
                Text.literal(String.format("%.2f", v / 100.0))),
            v -> Options.setSaturation(v, true)))
              .tooltip("Post-tonemap global color saturation. Higher values make colors more vivid.");

        // AgX look preset (mode 3 only) — displayed as dropdown for tonemapParam0
        int mode = Options.tonemappingMode;
        if (mode == 3) {
            // AgX Eary Chroma look preset selector
            int currentLook = Math.round(Options.tonemapParams[3][0]);
            SelectionDropdownWidget agxLook = new SelectionDropdownWidget(
                0, 0, 150, 20, "AgX Look",
                new String[]{"Base", "Punchy", "Golden"},
                currentLook, value -> {
                    Options.setTonemapParam(3, 0, (float) value, true);
                    screen.refreshContent();
                });
            tonemap.addTwoWidgets(agxLook, null)
                  .tooltip("Selects the AgX look preset. Base is neutral, Punchy increases contrast, Golden adds warmth.");
        }

        // Per-tonemapper parameter sliders (skip param 0 for AgX since it's a dropdown above)
        if (mode >= 0 && mode < TM_PARAMS.length) {
            TmParam[] params = TM_PARAMS[mode];
            int startIdx = (mode == 3) ? 0 : 0;  // AgX params are Extra Contrast/Saturation (P1/P2 in shader)
            for (int i = startIdx; i < params.length; i++) {
                TmParam p = params[i];
                final int paramIdx;
                if (mode == 3) {
                    paramIdx = i + 1;  // AgX: TM_PARAMS[0] -> tonemapParam1, [1] -> tonemapParam2
                } else {
                    paramIdx = i;
                }
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
        SelectionDropdownWidget diffuseModel = new SelectionDropdownWidget(
            0, 0, 150, 20, "Diffuse",
            new String[]{"EON", "VMF (Experimental)"},
            Options.diffuseModel == Options.DIFFUSE_MODEL_VMF ? 1 : 0,
            value -> Options.setDiffuseModel(value == 1 ? Options.DIFFUSE_MODEL_VMF : Options.DIFFUSE_MODEL_EON, true));
        color.addTwoWidgets(msGGX.createWidget(gameOptions), diffuseModel)
              .tooltipEach(
                  "Multi-Scatter GGX: preserves rough-surface energy in the GGX BRDF. More accurate, with a small cost.",
                  "Diffuse: selects the rough diffuse BRDF. EON is the default; VMF is experimental.");

        // HDR Output (conditional)
        if (Options.isHdrSupported()) {
            SettingsSection hdr = panel.addSection(Options.CATEGORY_HDR);

            SimpleOption<Boolean> hdrEnabled = SimpleOption.ofBoolean(
                Options.HDR_ENABLED_KEY, Options.hdrEnabled,
                value -> {
                    Options.setHdrEnabled(value, true);
                    screen.refreshContent();
                });
            hdr.addToggle(hdrEnabled.createWidget(gameOptions))
                  .tooltip("Enables HDR output. Default path is HDR10 using BT.2020/PQ.");

            if (Options.hdrEnabled) {
                // HDR output mode: HDR10 (default) or scRGB (optional)
                SelectionDropdownWidget hdrOutputMode = new SelectionDropdownWidget(
                    0, 0, 150, 20, "HDR Format",
                    new String[]{"HDR10 (PQ)", "scRGB (Linear)"},
                    Options.hdrScrgbMode ? 1 : 0, value -> {
                        Options.setHdrScrgbMode(value == 1, true);
                        screen.refreshContent();
                    });

                // HDR tonemapper selector: 0 = PsychoVisual, 1 = ITU EETF (Hermite)
                SelectionDropdownWidget hdrTmMode = new SelectionDropdownWidget(
                    0, 0, 150, 20, "HDR Tonemapper",
                    new String[]{"PsychoVisual", "ITU EETF"},
                    Options.hdrTonemapMode, value -> {
                        Options.setHdrTonemapMode(value, true);
                        screen.refreshContent();
                    });
                hdr.addTwoWidgets(hdrOutputMode, hdrTmMode)
                      .tooltipEach(
                          "HDR10 uses BT.2020/PQ and supports DLSS-FG. scRGB uses linear FP16; use it only for driver compatibility issues.",
                          "HDR Tonemapper maps scene luminance for HDR output. PsychoVisual is the default; ITU EETF is standards-oriented.");

                hdr.addTwoSliders(
                    new ResettableSliderWidget(0, 0, 150, 20,
                        10, 1000, Options.hdrPeakNits / 10, 100,
                        v -> getGenericValueText(Text.translatable(Options.HDR_PEAK_NITS_KEY), Text.literal((v * 10) + " nits")),
                        v -> Options.setHdrPeakNits(v * 10, true)),
                    new ResettableSliderWidget(0, 0, 150, 20,
                        1, 500, Options.hdrPaperWhiteNits, 203,
                        v -> getGenericValueText(Text.translatable(Options.HDR_PAPER_WHITE_NITS_KEY), Text.literal(v + " nits")),
                        v -> Options.setHdrPaperWhiteNits(v, true)))
                      .tooltipEach(
                          "Peak Nits: display maximum brightness target. Match this to your display peak brightness.",
                          "Paper White: reference white for UI and diffuse white in HDR output.");

                hdr.addSlider(new ResettableSliderWidget(0, 0, 150, 20,
                    5, 30, Options.hdrUiBrightnessNits / 10, 10,
                    v -> getGenericValueText(Text.translatable("options.video.hdr_ui_brightness_nits"), Text.literal((v * 10) + " nits")),
                    v -> Options.setHdrUiBrightnessNits(v * 10, true)));
                hdr.tooltip("Brightness target for UI elements in HDR output.");
            }
        }
    }

    @Override
    public java.util.List<UnifiedSearchOverlay.SearchEntry> getSearchEntries(String nodeId, String category) {
        return java.util.List.of(
            new UnifiedSearchOverlay.SearchEntry("Tone Mapper", category, nodeId, false),
            new UnifiedSearchOverlay.SearchEntry("Saturation", category, nodeId, false),
            new UnifiedSearchOverlay.SearchEntry("SDR Transfer Function", category, nodeId, false),
            new UnifiedSearchOverlay.SearchEntry("Multi-Scatter GGX", category, nodeId, true),
            new UnifiedSearchOverlay.SearchEntry("Diffuse Model", category, nodeId, true),
            new UnifiedSearchOverlay.SearchEntry("HDR Enabled", category, nodeId, false),
            new UnifiedSearchOverlay.SearchEntry("HDR Format", category, nodeId, false),
            new UnifiedSearchOverlay.SearchEntry("HDR Peak Nits", category, nodeId, false),
            new UnifiedSearchOverlay.SearchEntry("HDR Paper White Nits", category, nodeId, false)
        );
    }
}
