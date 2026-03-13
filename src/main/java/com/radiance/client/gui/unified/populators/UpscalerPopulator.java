package com.radiance.client.gui.unified.populators;

import static net.minecraft.client.option.GameOptions.getGenericValueText;

import com.mojang.serialization.Codec;
import com.radiance.client.RadianceClient;
import com.radiance.client.gui.DlssMissingScreen;
import com.radiance.client.gui.unified.*;
import com.radiance.client.gui.unified.rows.*;
import com.radiance.client.option.Options;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.widget.ButtonWidget;
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

        SimpleOption<Boolean> dlssDEnabled = SimpleOption.ofBoolean(
            Options.DLSS_D_ENABLED_KEY, Options.dlssDEnabled,
            value -> {
                Options.setDlssDEnabled(value, true);
                screen.refreshContent();
            });

        if (Options.dlssDEnabled) {
            String[] upscalerQualityKeys = {
                Options.UPSCALER_QUALITY_PERFORMANCE, Options.UPSCALER_QUALITY_BALANCED,
                Options.UPSCALER_QUALITY_QUALITY, Options.UPSCALER_QUALITY_NATIVE,
                Options.UPSCALER_QUALITY_CUSTOM
            };
            SimpleOption<Integer> upscalerQuality = new SimpleOption<>(
                Options.UPSCALER_QUALITY_KEY,
                SimpleOption.emptyTooltip(),
                (optionText, value) -> getGenericValueText(optionText,
                    Text.translatable(upscalerQualityKeys[Math.min(value, upscalerQualityKeys.length - 1)])),
                new SimpleOption.ValidatingIntSliderCallbacks(0, upscalerQualityKeys.length - 1),
                Codec.intRange(0, upscalerQualityKeys.length - 1),
                Options.upscalerQuality,
                value -> Options.setUpscalerQuality(value, true));
            section.addTwoWidgets(dlssDEnabled.createWidget(gameOptions), upscalerQuality.createWidget(gameOptions));

            String[] upscalerPresets = {"D", "E"};
            SimpleOption<Integer> upscalerPreset = new SimpleOption<>(
                Options.UPSCALER_PRESET_KEY,
                SimpleOption.emptyTooltip(),
                (optionText, value) -> getGenericValueText(optionText,
                    Text.literal(upscalerPresets[Math.min(value, upscalerPresets.length - 1)])),
                new SimpleOption.ValidatingIntSliderCallbacks(0, upscalerPresets.length - 1),
                Codec.intRange(0, upscalerPresets.length - 1),
                Options.upscalerPreset == 5 ? 1 : 0,
                value -> Options.setUpscalerPreset(value == 0 ? 4 : 5, true));
            SimpleOption<Integer> upscalerResOverride = new SimpleOption<>(
                Options.UPSCALER_RES_OVERRIDE_KEY,
                SimpleOption.emptyTooltip(),
                (optionText, value) -> getGenericValueText(optionText, Text.literal(value + "%")),
                new SimpleOption.ValidatingIntSliderCallbacks(33, 100),
                Codec.intRange(33, 100),
                Options.upscalerResOverride,
                value -> Options.setUpscalerResOverride(value, true));
            section.addTwoWidgets(upscalerPreset.createWidget(gameOptions), upscalerResOverride.createWidget(gameOptions));

            SimpleOption<Boolean> outputScale2x = SimpleOption.ofBoolean(
                Options.OUTPUT_SCALE_2X_KEY, Options.outputScale2x,
                value -> Options.setOutputScale2x(value, true));
            if (Options.isReflexSupported()) {
                SimpleOption<Boolean> reflexEnabled = SimpleOption.ofBoolean(
                    Options.REFLEX_ENABLED_KEY, Options.reflexEnabled,
                    value -> {
                        Options.setReflexEnabled(value, true);
                        screen.refreshContent();
                    });
                section.addTwoWidgets(outputScale2x.createWidget(gameOptions), reflexEnabled.createWidget(gameOptions));

                if (Options.reflexEnabled) {
                    SimpleOption<Boolean> vrrMode = SimpleOption.ofBoolean(
                        Options.VRR_MODE_KEY, Options.vrrMode,
                        value -> Options.setVrrMode(value, true));
                    section.addToggle(vrrMode.createWidget(gameOptions));
                }
            } else {
                section.addToggle(outputScale2x.createWidget(gameOptions));
            }
        } else {
            SimpleOption<Boolean> outputScale2x = SimpleOption.ofBoolean(
                Options.OUTPUT_SCALE_2X_KEY, Options.outputScale2x,
                value -> Options.setOutputScale2x(value, true));
            section.addTwoWidgets(dlssDEnabled.createWidget(gameOptions), outputScale2x.createWidget(gameOptions));

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
}
