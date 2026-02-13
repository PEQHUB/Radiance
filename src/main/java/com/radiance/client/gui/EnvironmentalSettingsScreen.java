package com.radiance.client.gui;

import static net.minecraft.client.option.GameOptions.getGenericValueText;

import com.google.common.collect.ImmutableList;
import com.mojang.serialization.Codec;
import com.radiance.client.option.Options;
import com.radiance.client.util.CategoryVideoOptionEntry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.option.GameOptionsScreen;
import net.minecraft.client.option.SimpleOption;
import net.minecraft.text.Text;

public class EnvironmentalSettingsScreen extends GameOptionsScreen {

    private final Screen parentScreen;

    public EnvironmentalSettingsScreen(Screen parent) {
        super(parent, MinecraftClient.getInstance().options,
            Text.translatable("radiance.settings.environment.title"));
        this.parentScreen = parent;
    }

    @Override
    protected void addOptions() {
        this.body.addEntry(
            new CategoryVideoOptionEntry(Text.translatable(Options.CATEGORY_ENVIRONMENT), body));

        String[] dimKeys = {
            Options.ENVIRONMENT_DIMENSION_OVERWORLD,
            Options.ENVIRONMENT_DIMENSION_NETHER,
            Options.ENVIRONMENT_DIMENSION_END
        };

        SimpleOption<Integer> dimensionSelector = new SimpleOption<>(
            Options.ENVIRONMENT_DIMENSION_KEY,
            SimpleOption.emptyTooltip(),
            (optionText, value) -> getGenericValueText(optionText,
                Text.translatable(dimKeys[Math.max(0, Math.min(value, dimKeys.length - 1))])),
            new SimpleOption.ValidatingIntSliderCallbacks(0, dimKeys.length - 1),
            Codec.intRange(0, dimKeys.length - 1),
            Options.getEnvironmentEditingDimension(),
            value -> Options.setEnvironmentEditingDimension(value, true));
        this.body.addSingleOptionEntry(dimensionSelector);

        this.body.addSingleOptionEntry(openScreenOption(
            "options.video.environment.sky_settings",
            new SkySettingsScreen(this)));
        this.body.addSingleOptionEntry(openScreenOption(
            "options.video.environment.cloud_settings",
            new CloudSettingsScreen(this)));
        this.body.addSingleOptionEntry(openScreenOption(
            "options.video.environment.water_settings",
            new WaterSettingsScreen(this)));
        this.body.addSingleOptionEntry(openScreenOption(
            "options.video.environment.sun_settings",
            new SunSettingsScreen(this)));
        this.body.addSingleOptionEntry(openScreenOption(
            "options.video.environment.moon_settings",
            new MoonSettingsScreen(this)));
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
