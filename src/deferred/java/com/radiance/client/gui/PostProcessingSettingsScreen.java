package com.radiance.client.gui;

import static net.minecraft.client.option.GameOptions.getGenericValueText;

import com.radiance.client.option.Options;
import com.radiance.client.util.CategoryVideoOptionEntry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.option.GameOptionsScreen;
import net.minecraft.client.option.SimpleOption;
import net.minecraft.text.Text;

public class PostProcessingSettingsScreen extends GameOptionsScreen {

    private final Screen parentScreen;

    public PostProcessingSettingsScreen(Screen parent) {
        super(parent, MinecraftClient.getInstance().options,
            Text.translatable("radiance.settings.post_processing.title"));
        this.parentScreen = parent;
    }

    @Override
    protected void addOptions() {
        this.body.addEntry(
            new CategoryVideoOptionEntry(Text.translatable(Options.CATEGORY_POST_PROCESSING), body));

        SimpleOption<Boolean> casEnabled = SimpleOption.ofBoolean(
            Options.CAS_ENABLED_KEY,
            Options.casEnabled,
            value -> {
                Options.setCasEnabled(value, true);
                MinecraftClient.getInstance().setScreen(new PostProcessingSettingsScreen(parentScreen));
            });
        this.body.addSingleOptionEntry(casEnabled);

        if (Options.casEnabled) {
            ResettableSliderWidget casSharpnessSlider = new ResettableSliderWidget(
                0, 0, 150, 20,
                0, 100, Options.casSharpnessPercent, 50,
                v -> getGenericValueText(
                    Text.translatable(Options.CAS_SHARPNESS_KEY),
                    Text.literal(v + "%")),
                v -> Options.setCasSharpnessPercent(v, true));
            this.body.addEntry(new RadianceSettingsScreen.SliderEntry(casSharpnessSlider, body));
        }
    }
}
