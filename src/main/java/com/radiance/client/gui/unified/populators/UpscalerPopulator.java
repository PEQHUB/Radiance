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
                btn -> mc.setScreen(new DlssMissingScreen(screen)))
                .width(150).build());
        }

        // Row 1: Upscaler mode dropdown + DLSS-D toggle (two click controls paired)
        SelectionDropdownWidget upscalerModeDropdown = new SelectionDropdownWidget(
            0, 0, 150, 20, "Upscaler",
            new String[]{"DLSS-RR", "FSR3", "Off"},
            Options.upscalerMode, value -> {
                Options.setUpscalerMode(value, true);
                screen.refreshContent();
            });

        SimpleOption<Boolean> dlssDToggle = SimpleOption.ofBoolean(
            "options.video.dlss_d_enabled", Options.dlssDEnabled,
            value -> Options.setDlssDEnabled(value, true));

        section.addTwoWidgets(upscalerModeDropdown, dlssDToggle.createWidget(gameOptions));

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
                section.addTwoWidgets(qualityDropdown, presetDropdown);
            } else {
                // FSR3: quality dropdown paired with 4xSSAA toggle
                SimpleOption<Boolean> ssaa4x = SimpleOption.ofBoolean(
                    Options.OUTPUT_SCALE_2X_KEY,
                    SimpleOption.constantTooltip(Text.translatable("options.video.output_scale_2x.tooltip")),
                    Options.outputScale2x,
                    value -> Options.setOutputScale2x(value, true));
                section.addTwoWidgets(qualityDropdown, ssaa4x.createWidget(gameOptions));
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
            section.addTwoWidgets(sharpenerDropdown, ssaa4x.createWidget(gameOptions));
        } else {
            // FSR/Off path: pair sharpener with something else or solo
            section.addTwoWidgets(sharpenerDropdown, null);
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

            SimpleOption<Boolean> reflexEnabled = SimpleOption.ofBoolean(
                Options.REFLEX_ENABLED_KEY, Options.reflexEnabled,
                value -> {
                    Options.setReflexEnabled(value, true);
                    screen.refreshContent();
                });

            section.addTwoWidgets(vsyncToggle.createWidget(gameOptions), reflexEnabled.createWidget(gameOptions));

            if (Options.reflexEnabled) {
                // Row: FPS limit slider (Reflex frame limiter — authoritative when Reflex is on)
                section.addSlider(new ResettableSliderWidget(0, 0, 150, 20,
                    5, 999, Math.max(5, Options.maxFps), 260,
                    v -> getGenericValueText(Text.translatable(Options.MAX_FPS_KEY),
                        Text.literal(v + " fps")),
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

        // Frame Generation — backend selector: Off / DLSS-G / FSR FG
        if (Options.isFrameGenSupported()) {
            // Build backend list: Off always, DLSS-G only if hardware supports it, FSR FG always
            java.util.List<String> backendNames = new java.util.ArrayList<>();
            java.util.List<Integer> backendValues = new java.util.ArrayList<>();
            backendNames.add("Off");          backendValues.add(0);
            if (Options.isDlssFrameGenSupported()) {
                backendNames.add("DLSS-G");   backendValues.add(1);
            }
            backendNames.add("FSR FG");       backendValues.add(2);

            int selectedIdx = backendValues.indexOf(Options.frameGenBackend);
            if (selectedIdx < 0) selectedIdx = 0;

            SelectionDropdownWidget backendDropdown = new SelectionDropdownWidget(
                0, 0, 150, 20, "Frame Generation",
                backendNames.toArray(new String[0]), selectedIdx, value -> {
                    Options.setFrameGenBackend(backendValues.get(value), true);
                    screen.refreshContent();
                });

            if (Options.frameGenBackend != 0) {
                // FG Mode: On / Auto
                String[] fgModeNames = {"Off", "On", "Auto"};
                SelectionDropdownWidget fgModeDropdown = new SelectionDropdownWidget(
                    0, 0, 150, 20, "FG Mode",
                    fgModeNames, Options.frameGenMode, value -> {
                        Options.setFrameGenMode(value, true);
                        screen.refreshContent();
                    });
                section.addTwoWidgets(backendDropdown, fgModeDropdown);
            } else {
                section.addTwoWidgets(backendDropdown, null);
            }
        }
    }
}
