package com.radiance.client.gui;

import com.google.common.collect.ImmutableList;
import com.mojang.serialization.Codec;
import com.radiance.client.option.Options;
import com.radiance.client.util.CategoryVideoOptionEntry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.option.GameOptionsScreen;
import net.minecraft.client.option.SimpleOption;
import net.minecraft.text.Text;

public class CameraControlsScreen extends GameOptionsScreen {

    public CameraControlsScreen(Screen parent) {
        super(parent, MinecraftClient.getInstance().options,
            Text.translatable("radiance.settings.camera_controls.title"));
    }

    @Override
    protected void addOptions() {
        this.body.addEntry(
            new CategoryVideoOptionEntry(Text.translatable(Options.CATEGORY_CAMERA_CONTROLS), body));

        this.body.addSingleOptionEntry(openScreenOption(
            "options.video.camera_controls.exposure",
            new ExposureSettingsScreen(this)));

        this.body.addSingleOptionEntry(openScreenOption(
            "options.video.camera_controls.post_processing",
            new PostProcessingSettingsScreen(this)));
    }

    private SimpleOption<Boolean> openScreenOption(String key, Screen screen) {
        return new SimpleOption<>(
            key,
            SimpleOption.emptyTooltip(),
            (optionText, value) -> optionText,
            new PotentialValuesBasedCallbacksNoValue<>(
                ImmutableList.of(Boolean.TRUE, Boolean.FALSE), Codec.BOOL),
            false,
            value -> MinecraftClient.getInstance().setScreen(screen));
    }
}
