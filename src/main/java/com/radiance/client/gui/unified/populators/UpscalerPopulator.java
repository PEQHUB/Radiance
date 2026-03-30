package com.radiance.client.gui.unified.populators;

import static net.minecraft.client.option.GameOptions.getGenericValueText;

import com.radiance.client.RadianceClient;
import com.radiance.client.gui.DlssMissingScreen;
import com.radiance.client.gui.ResettableSliderWidget;
import com.radiance.client.gui.SelectionDropdownWidget;
import com.radiance.client.gui.unified.*;
import com.radiance.client.option.Options;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.CyclingButtonWidget;
import net.minecraft.client.option.SimpleOption;
import net.minecraft.text.Text;

public class UpscalerPopulator implements ContentPopulator {
    @Override
    public void populate(ContentPanelWidget panel, RadianceUnifiedScreen screen) {
        var mc = MinecraftClient.getInstance();
        var gameOptions = mc.options;

        SettingsSection section = panel.addSection(Options.CATEGORY_UPSCALER);

        if (RadianceClient.dlssMissing) {
            section.addButton(ButtonWidget.builder(
                Text.translatable("options.video.dlss_missing_warning"),
                btn -> screen.showOverlay(new DlssMissingScreen(screen)))
                .width(150).build());
        }

        // Row 1: Upscaler mode dropdown + DLSS-D toggle (only when DLSS is selected)
        SelectionDropdownWidget upscalerModeDropdown = new SelectionDropdownWidget(
            0, 0, 150, 20, "Upscaler",
            new String[]{"DLSS-RR", "FSR3", "Off"},
            Options.upscalerMode, value -> {
                Options.setUpscalerMode(value, true);
                screen.refreshContent();
            });

        if (Options.upscalerMode == 0) {
            // DLSS-D only relevant when DLSS is the active upscaler
            SimpleOption<Boolean> dlssDToggle = SimpleOption.ofBoolean(
                "options.video.dlss_d_enabled", Options.dlssDEnabled,
                value -> Options.setDlssDEnabled(value, true));

            section.addTwoWidgets(upscalerModeDropdown, dlssDToggle.createWidget(gameOptions))
                  .tooltip("DLSS-RR = NVIDIA ray reconstruction. FSR3 = AMD upscaler. DLSS-D denoises without upscaling.");
        } else {
            section.addTwoWidgets(upscalerModeDropdown, null)
                  .tooltip("DLSS-RR = NVIDIA ray reconstruction. FSR3 = AMD upscaler.");
        }

        // Controls shown for DLSS-RR and FSR3
        if (Options.upscalerMode != 2) {
            // Row 2: Quality preset dropdown (+ RR Model for DLSS)
            SelectionDropdownWidget qualityDropdown = new SelectionDropdownWidget(
                0, 0, 150, 20, "Quality",
                new String[]{"Performance", "Balanced", "Quality", "Native", "Custom"},
                Options.upscalerQuality, value -> {
                    Options.setUpscalerQuality(value, true);
                    screen.refreshContent();
                });

            if (Options.upscalerMode == 0) {
                // DLSS-RR: pair quality dropdown with RR model dropdown
                SelectionDropdownWidget presetDropdown = new SelectionDropdownWidget(
                    0, 0, 150, 20, "RR Model",
                    new String[]{"Model D", "Model E"},
                    Options.upscalerPreset == 5 ? 1 : 0, value -> {
                        Options.setUpscalerPreset(value == 1 ? 5 : 4, true);
                    });
                section.addTwoWidgets(qualityDropdown, presetDropdown)
                      .tooltip("Quality preset controls render resolution. RR Model D/E are different neural network weights.");
            } else {
                // FSR3: quality dropdown paired with 4xSSAA toggle
                SimpleOption<Boolean> ssaa4x = SimpleOption.ofBoolean(
                    Options.OUTPUT_SCALE_2X_KEY,
                    SimpleOption.constantTooltip(Text.translatable("options.video.output_scale_2x.tooltip")),
                    Options.outputScale2x,
                    value -> Options.setOutputScale2x(value, true));
                section.addTwoWidgets(qualityDropdown, ssaa4x.createWidget(gameOptions))
                      .tooltip("Quality preset controls render resolution. RR Model D/E are different neural network weights.");
            }

            // Row 3: Resolution override — only visible when Custom quality selected
            if (Options.upscalerQuality == 4) {
                section.addSlider(new ResettableSliderWidget(0, 0, 150, 20,
                    33, 100, Options.upscalerResOverride, 67,
                    v -> getGenericValueText(Text.translatable(Options.UPSCALER_RES_OVERRIDE_KEY), Text.literal(v + "%")),
                    v -> Options.setUpscalerResOverride(v, true)));
            }
        }

        // Sharpener dropdown + 4xSSAA toggle (DLSS path) or solo (FSR already paired above)
        SelectionDropdownWidget sharpenerDropdown = new SelectionDropdownWidget(
            0, 0, 150, 20, "Sharpener",
            new String[]{"None", "CAS", "RCAS"},
            Options.sharpenerMode, value -> {
                Options.setSharpenerMode(value, true);
                screen.refreshContent();
            });

        if (Options.upscalerMode == 0) {
            // DLSS path: pair sharpener with 4xSSAA
            SimpleOption<Boolean> ssaa4x = SimpleOption.ofBoolean(
                Options.OUTPUT_SCALE_2X_KEY,
                SimpleOption.constantTooltip(Text.translatable("options.video.output_scale_2x.tooltip")),
                Options.outputScale2x,
                value -> Options.setOutputScale2x(value, true));
            section.addTwoWidgets(sharpenerDropdown, ssaa4x.createWidget(gameOptions))
                  .tooltip("CAS = AMD Contrast Adaptive Sharpening. RCAS = FidelityFX RCAS from FSR pipeline.");
        } else {
            // FSR/Off path: pair sharpener with something else or solo
            section.addTwoWidgets(sharpenerDropdown, null)
                  .tooltip("CAS = AMD Contrast Adaptive Sharpening. RCAS = FidelityFX RCAS from FSR pipeline.");
        }

        // Sharpness slider (conditional — only when sharpener is active)
        if (Options.sharpenerMode != 0) {
            section.addSlider(new ResettableSliderWidget(0, 0, 150, 20,
                0, 100, Options.casSharpnessPercent, 50,
                v -> getGenericValueText(Text.translatable(Options.CAS_SHARPNESS_KEY), Text.literal(v + "%")),
                v -> Options.setCasSharpnessPercent(v, true)));
        }

        // VSync + Reflex + FPS Limit
        if (Options.isReflexSupported()) {
            // Row: VSync + Reflex
            SimpleOption<Boolean> vsyncToggle = SimpleOption.ofBoolean(
                Options.VSYNC_KEY, Options.vsync,
                value -> {
                    Options.setVsync(value, true);
                    screen.refreshContent();
                });

            // DLSS-G requires Reflex — show locked label when FG is active
            boolean fgForced = Options.frameGenMode != 0;
            if (fgForced) {
                // Reflex is forced on by Frame Generation — show a non-interactive label
                ButtonWidget reflexLocked = ButtonWidget.builder(
                    Text.literal("Reflex: Locked (FG)"), btn -> {})
                    .width(150).build();
                reflexLocked.active = false;
                section.addTwoWidgets(vsyncToggle.createWidget(gameOptions), reflexLocked);
            } else {
                SimpleOption<Boolean> reflexEnabled = SimpleOption.ofBoolean(
                    Options.REFLEX_ENABLED_KEY, Options.reflexEnabled,
                    value -> {
                        Options.setReflexEnabled(value, true);
                        screen.refreshContent();
                    });
                section.addTwoWidgets(vsyncToggle.createWidget(gameOptions), reflexEnabled.createWidget(gameOptions));
            }

            if (Options.reflexEnabled) {
                // Row: FPS limit slider
                section.addSlider(new ResettableSliderWidget(0, 0, 150, 20,
                    0, 999, Options.maxFps, 0,
                    v -> getGenericValueText(Text.translatable(Options.MAX_FPS_KEY),
                        Text.literal(v == 0 ? "Unlimited" : v + " fps")),
                    v -> Options.setMaxFps(v, true)));

                // Row: Auto VRR Cap button — computes target from display Hz, writes to maxFps
                ButtonWidget vrrButton = ButtonWidget.builder(
                    Text.literal("Auto VRR Cap"),
                    btn -> {
                        int hz = Options.nativeGetDisplayRefreshRate();
                        if (hz > 0) {
                            int target = (3600 * hz) / (hz + 3600);
                            Options.setMaxFps(target, true);
                            screen.refreshContent();
                        }
                    }).width(150).build();
                section.addButton(vrrButton);
            }
        }

        // Frame Generation (DLSS-G) — requires Reflex
        if (Options.isFrameGenSupported()) {
            String[] fgModeNames = {"Off", "On", "Auto"};
            SelectionDropdownWidget fgModeDropdown = new SelectionDropdownWidget(
                0, 0, 150, 20, "Frame Generation",
                fgModeNames, Options.frameGenMode, value -> {
                    Options.setFrameGenMode(value, true);
                    screen.refreshContent();
                });

            int maxMulti = Options.getFrameGenMaxMultiplier();
            // Show multiplier selector for "On" mode (manual); Auto mode handles it dynamically
            if (Options.frameGenMode == 1 && maxMulti > 1) {
                String[] multiNames = new String[maxMulti];
                for (int i = 0; i < maxMulti; i++) multiNames[i] = (i + 2) + "x";
                SelectionDropdownWidget fgMultiDropdown = new SelectionDropdownWidget(
                    0, 0, 150, 20, "FG Multiplier",
                    multiNames, Options.frameGenMultiplier - 1, value -> {
                        Options.setFrameGenMultiplier(value + 1, true);
                    });
                section.addTwoWidgets(fgModeDropdown, fgMultiDropdown)
                      .tooltip("Generates interpolated frames between real renders. Requires Reflex for frame pacing.");
            } else {
                section.addTwoWidgets(fgModeDropdown, null)
                      .tooltip(Options.frameGenMode == 2
                          ? "Auto: dynamically varies frame generation multiplier based on scene load."
                          : "Generates interpolated frames between real renders. Requires Reflex for frame pacing.");
            }
        }
    }

    @Override
    public java.util.List<UnifiedSearchOverlay.SearchEntry> getSearchEntries(String nodeId, String category) {
        return java.util.List.of(
            new UnifiedSearchOverlay.SearchEntry("Upscaler Mode", category, nodeId, true),
            new UnifiedSearchOverlay.SearchEntry("DLSS-D Enabled", category, nodeId, true),
            new UnifiedSearchOverlay.SearchEntry("Upscaler Quality", category, nodeId, true),
            new UnifiedSearchOverlay.SearchEntry("RR Model", category, nodeId, true),
            new UnifiedSearchOverlay.SearchEntry("4x SSAA", category, nodeId, true),
            new UnifiedSearchOverlay.SearchEntry("Resolution Override", category, nodeId, true),
            new UnifiedSearchOverlay.SearchEntry("VSync", category, nodeId, false),
            new UnifiedSearchOverlay.SearchEntry("Reflex", category, nodeId, false),
            new UnifiedSearchOverlay.SearchEntry("FPS Limit", category, nodeId, false),
            new UnifiedSearchOverlay.SearchEntry("Frame Generation", category, nodeId, true)
        );
    }
}
