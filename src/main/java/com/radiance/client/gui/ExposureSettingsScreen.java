package com.radiance.client.gui;

import static net.minecraft.client.option.GameOptions.getGenericValueText;

import com.mojang.serialization.Codec;
import com.radiance.client.option.Options;
import com.radiance.client.util.CategoryVideoOptionEntry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.option.GameOptionsScreen;
import net.minecraft.client.option.SimpleOption;
import net.minecraft.text.Text;

public class ExposureSettingsScreen extends GameOptionsScreen {

    public ExposureSettingsScreen(Screen parent) {
        super(parent, MinecraftClient.getInstance().options,
            Text.translatable("radiance.settings.exposure.title"));
    }

    @Override
    protected void addOptions() {
        this.body.addEntry(
            new CategoryVideoOptionEntry(Text.translatable("options.video.category.exposure"), body));

        // Legacy Exposure toggle: preserves the original failure modes by disabling highlight protection.
        SimpleOption<Boolean> legacyExposure = SimpleOption.ofBoolean(
            Options.LEGACY_EXPOSURE_KEY,
            Options.legacyExposure,
            value -> Options.setLegacyExposure(value, true));
        this.body.addSingleOptionEntry(legacyExposure);

        // Min Exposure: 0.0001 to 1.0 (stored as ten-thousandths 1-10000)
        ResettableSliderWidget minExpSlider = new ResettableSliderWidget(
            0, 0, 150, 20,
            1, 10000, Options.minExposureTenK, 1,
            v -> getGenericValueText(
                Text.translatable(Options.MIN_EXPOSURE_KEY),
                Text.literal(String.format("%.4f", v / 10000.0))),
            v -> Options.setMinExposure(v, true));
        this.body.addEntry(new RadianceSettingsScreen.SliderEntry(minExpSlider, body));

        SimpleOption<Integer> maxExposure = new SimpleOption<>(
            Options.MAX_EXPOSURE_KEY,
            SimpleOption.emptyTooltip(),
            (optionText, value) -> getGenericValueText(optionText, Text.literal(Integer.toString(value))),
            new SimpleOption.ValidatingIntSliderCallbacks(1, 20),
            Codec.intRange(1, 20),
            Options.maxExposure,
            value -> Options.setMaxExposure(value, true));
        this.body.addSingleOptionEntry(maxExposure);

        // Exposure Compensation: -3.0 to +3.0 EV (stored as tenths, slider 0-60 offset by 30)
        ResettableSliderWidget ecSlider = new ResettableSliderWidget(
            0, 0, 150, 20,
            0, 60, Options.exposureCompensation + 30, 0 + 30,
            v -> getGenericValueText(
                Text.translatable(Options.EXPOSURE_COMPENSATION_KEY),
                Text.literal(String.format("%+.1f EV", (v - 30) / 10.0))),
            v -> Options.setExposureCompensation(v - 30, true));
        this.body.addEntry(new RadianceSettingsScreen.SliderEntry(ecSlider, body));

        // Middle Grey: 0.01 to 0.50 (stored as percent 1-50)
        ResettableSliderWidget mgSlider = new ResettableSliderWidget(
            0, 0, 150, 20,
            1, 50, Options.middleGreyPercent, 18,
            v -> getGenericValueText(
                Text.translatable(Options.MIDDLE_GREY_KEY),
                Text.literal(String.format("%.2f", v / 100.0))),
            v -> Options.setMiddleGrey(v, true));
        this.body.addEntry(new RadianceSettingsScreen.SliderEntry(mgSlider, body));
    }
}
