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
    @Override
    public void populate(ContentPanelWidget panel, RadianceUnifiedScreen screen) {
        var gameOptions = MinecraftClient.getInstance().options;

        // SDR Transfer Function + Saturation
        SettingsSection color = panel.addSection(Text.literal("Color & Saturation"));

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
        color.addToggle(sdrTransferFn.createWidget(gameOptions));

        color.addSlider(new ResettableSliderWidget(0, 0, 150, 20,
            100, 200, Options.saturationPercent, Options.SATURATION_DEFAULT_PERCENT,
            v -> getGenericValueText(Text.translatable(Options.SATURATION_KEY),
                Text.literal(String.format("%.2f", v / 100.0))),
            v -> Options.setSaturation(v, true)));

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
