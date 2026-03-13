package com.radiance.client.gui.unified.populators;

import static net.minecraft.client.option.GameOptions.getGenericValueText;

import com.mojang.serialization.Codec;
import com.radiance.client.gui.unified.*;
import com.radiance.client.option.Options;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.SimpleOption;
import net.minecraft.text.Text;

public class SharcPopulator implements ContentPopulator {
    @Override
    public void populate(ContentPanelWidget panel, RadianceUnifiedScreen screen) {
        var gameOptions = MinecraftClient.getInstance().options;

        SettingsSection section = panel.addSection(Options.CATEGORY_SHARC);

        SimpleOption<Boolean> sharcEnabled = SimpleOption.ofBoolean(
            Options.SHARC_ENABLED_KEY, Options.sharcEnabled,
            value -> Options.setSharcEnabled(value, true));

        SimpleOption<Integer> sharcDownscale = new SimpleOption<>(
            Options.SHARC_DOWNSCALE_KEY, SimpleOption.emptyTooltip(),
            (optionText, value) -> getGenericValueText(optionText, Text.literal(value + "x")),
            new SimpleOption.ValidatingIntSliderCallbacks(1, 8), Codec.intRange(1, 8),
            Options.sharcDownscale, value -> Options.setSharcDownscale(value, true));
        section.addTwoWidgets(sharcEnabled.createWidget(gameOptions), sharcDownscale.createWidget(gameOptions));

        SimpleOption<Integer> sharcSceneScale = new SimpleOption<>(
            Options.SHARC_SCENE_SCALE_KEY, SimpleOption.emptyTooltip(),
            (optionText, value) -> getGenericValueText(optionText, Text.literal(String.format("%.1f", value / 10.0))),
            new SimpleOption.ValidatingIntSliderCallbacks(10, 200), Codec.intRange(10, 200),
            Options.sharcSceneScaleTenths, value -> Options.setSharcSceneScaleTenths(value, true));

        SimpleOption<Integer> sharcRoughnessThreshold = new SimpleOption<>(
            Options.SHARC_ROUGHNESS_THRESHOLD_KEY, SimpleOption.emptyTooltip(),
            (optionText, value) -> getGenericValueText(optionText, Text.literal(String.format("%.2f", value / 100.0))),
            new SimpleOption.ValidatingIntSliderCallbacks(0, 100), Codec.intRange(0, 100),
            Options.sharcRoughnessThresholdPercent, value -> Options.setSharcRoughnessThresholdPercent(value, true));
        section.addTwoWidgets(sharcSceneScale.createWidget(gameOptions), sharcRoughnessThreshold.createWidget(gameOptions));

        SimpleOption<Integer> sharcAccumulationFrames = new SimpleOption<>(
            Options.SHARC_ACCUMULATION_FRAMES_KEY, SimpleOption.emptyTooltip(),
            (optionText, value) -> getGenericValueText(optionText, Text.literal(Integer.toString(value))),
            new SimpleOption.ValidatingIntSliderCallbacks(4, 256), Codec.intRange(4, 256),
            Options.sharcAccumulationFrames, value -> Options.setSharcAccumulationFrames(value, true));

        SimpleOption<Integer> sharcStaleFrames = new SimpleOption<>(
            Options.SHARC_STALE_FRAMES_KEY, SimpleOption.emptyTooltip(),
            (optionText, value) -> getGenericValueText(optionText, Text.literal(Integer.toString(value))),
            new SimpleOption.ValidatingIntSliderCallbacks(4, 128), Codec.intRange(4, 128),
            Options.sharcStaleFrames, value -> Options.setSharcStaleFrames(value, true));
        section.addTwoWidgets(sharcAccumulationFrames.createWidget(gameOptions), sharcStaleFrames.createWidget(gameOptions));
    }
}
