package com.radiance.client.gui.unified.populators;

import static net.minecraft.client.option.GameOptions.getGenericValueText;

import com.radiance.client.RadianceClient;
import com.radiance.client.gui.DlssMissingScreen;
import com.radiance.client.gui.ResettableSliderWidget;
import com.radiance.client.gui.unified.*;
import com.radiance.client.gui.unified.rows.*;
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

        // Row 1: Upscaler mode (solo — pairs poorly with anything else)
        String[] upscalerModeNames = {"DLSS-RR", "FSR3", "Off"};
        CyclingButtonWidget<Integer> upscalerModeBtn = CyclingButtonWidget.<Integer>builder(
                (value) -> Text.literal(upscalerModeNames[value]))
            .values(0, 1, 2)
            .initially(Options.upscalerMode)
            .build(0, 0, 150, 20, Text.translatable(Options.UPSCALER_MODE_KEY), (btn, value) -> {
                Options.setUpscalerMode(value, true);
                screen.refreshContent();
            });
        section.addToggle(upscalerModeBtn);

        // Controls shown for DLSS-RR and FSR3
        if (Options.upscalerMode != 2) {
            // Row 2: Quality preset dropdown + RR Model (DLSS) or solo (FSR)
            String[] qualityNames = {"Performance", "Balanced", "Quality", "Native", "Custom"};
            CyclingButtonWidget<Integer> qualityBtn = CyclingButtonWidget.<Integer>builder(
                    (value) -> Text.literal(qualityNames[value]))
                .values(0, 1, 2, 3, 4)
                .initially(Options.upscalerQuality)
                .build(0, 0, 150, 20, Text.translatable(Options.UPSCALER_QUALITY_KEY), (btn, value) -> {
                    Options.setUpscalerQuality(value, true);
                    screen.refreshContent(); // show/hide resolution slider
                });

            if (Options.upscalerMode == 0) {
                CyclingButtonWidget<Integer> presetBtn = CyclingButtonWidget.<Integer>builder(
                        (value) -> Text.literal(value == 4 ? "D" : "E"))
                    .values(4, 5)
                    .initially(Options.upscalerPreset)
                    .build(0, 0, 150, 20, Text.literal("RR Model"), (btn, value) -> {
                        Options.setUpscalerPreset(value, true);
                    });
                section.addTwoWidgets(qualityBtn, presetBtn);
            } else {
                section.addToggle(qualityBtn);
            }

            // Row 3: Resolution override — only visible when Custom quality selected
            if (Options.upscalerQuality == 4) {
                section.addSlider(new ResettableSliderWidget(0, 0, 150, 20,
                    33, 100, Options.upscalerResOverride, 67,
                    v -> getGenericValueText(Text.translatable(Options.UPSCALER_RES_OVERRIDE_KEY), Text.literal(v + "%")),
                    v -> Options.setUpscalerResOverride(v, true)));
            }
        }

        // Sharpener dropdown + 4xSSAA toggle (two click controls paired)
        String[] sharpenerNames = {"None", "CAS", "RCAS"};
        CyclingButtonWidget<Integer> sharpenerBtn = CyclingButtonWidget.<Integer>builder(
                (value) -> Text.literal(sharpenerNames[value]))
            .values(0, 1, 2)
            .initially(Options.sharpenerMode)
            .build(0, 0, 150, 20, Text.translatable(Options.SHARPENER_MODE_KEY), (btn, value) -> {
                Options.setSharpenerMode(value, true);
                screen.refreshContent();
            });

        SimpleOption<Boolean> ssaa4x = SimpleOption.ofBoolean(
            Options.OUTPUT_SCALE_2X_KEY,
            SimpleOption.constantTooltip(Text.translatable("options.video.output_scale_2x.tooltip")),
            Options.outputScale2x,
            value -> Options.setOutputScale2x(value, true));

        section.addTwoWidgets(sharpenerBtn, ssaa4x.createWidget(gameOptions));

        // Sharpness slider (conditional — only when sharpener is active)
        if (Options.sharpenerMode != 0) {
            section.addSlider(new ResettableSliderWidget(0, 0, 150, 20,
                0, 100, Options.casSharpnessPercent, 50,
                v -> getGenericValueText(Text.translatable(Options.CAS_SHARPNESS_KEY), Text.literal(v + "%")),
                v -> Options.setCasSharpnessPercent(v, true)));
        }

        // Reflex + VRR
        if (Options.isReflexSupported()) {
            SimpleOption<Boolean> reflexEnabled = SimpleOption.ofBoolean(
                Options.REFLEX_ENABLED_KEY, Options.reflexEnabled,
                value -> {
                    Options.setReflexEnabled(value, true);
                    screen.refreshContent();
                });

            if (Options.reflexEnabled) {
                SimpleOption<Boolean> vrrMode = SimpleOption.ofBoolean(
                    Options.VRR_MODE_KEY, Options.vrrMode,
                    value -> Options.setVrrMode(value, true));
                section.addTwoWidgets(reflexEnabled.createWidget(gameOptions), vrrMode.createWidget(gameOptions));
            } else {
                section.addToggle(reflexEnabled.createWidget(gameOptions));
            }
        }
    }
}
