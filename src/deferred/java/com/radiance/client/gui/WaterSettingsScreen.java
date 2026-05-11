package com.radiance.client.gui;

import static net.minecraft.client.option.GameOptions.getGenericValueText;

import com.radiance.client.option.Options;
import com.radiance.client.util.CategoryVideoOptionEntry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.option.GameOptionsScreen;
import net.minecraft.text.Text;

public class WaterSettingsScreen extends GameOptionsScreen {

    public WaterSettingsScreen(Screen parent) {
        super(parent, MinecraftClient.getInstance().options,
            Text.translatable("radiance.settings.environment.water.title"));
    }

    @Override
    protected void addOptions() {
        int dim = Options.getEnvironmentEditingDimension();

        this.body.addEntry(new CategoryVideoOptionEntry(
            Text.translatable("options.video.environment.water.category"), body));

        ResettableSliderWidget tintRSlider = new ResettableSliderWidget(
            0, 0, 150, 20,
            0, 100, Options.waterTintR[dim], Options.WATER_TINT_R_DEFAULT,
            v -> getGenericValueText(Text.translatable("options.video.environment.water_tint_r"),
                Text.literal(v + "%")),
            v -> Options.setWaterTintRPercent(dim, v, true));
        this.body.addEntry(new RadianceSettingsScreen.SliderEntry(tintRSlider, body));

        ResettableSliderWidget tintGSlider = new ResettableSliderWidget(
            0, 0, 150, 20,
            0, 100, Options.waterTintG[dim], Options.WATER_TINT_G_DEFAULT,
            v -> getGenericValueText(Text.translatable("options.video.environment.water_tint_g"),
                Text.literal(v + "%")),
            v -> Options.setWaterTintGPercent(dim, v, true));
        this.body.addEntry(new RadianceSettingsScreen.SliderEntry(tintGSlider, body));

        ResettableSliderWidget tintBSlider = new ResettableSliderWidget(
            0, 0, 150, 20,
            0, 100, Options.waterTintB[dim], Options.WATER_TINT_B_DEFAULT,
            v -> getGenericValueText(Text.translatable("options.video.environment.water_tint_b"),
                Text.literal(v + "%")),
            v -> Options.setWaterTintBPercent(dim, v, true));
        this.body.addEntry(new RadianceSettingsScreen.SliderEntry(tintBSlider, body));

        ResettableSliderWidget waterFogSlider = new ResettableSliderWidget(
            0, 0, 150, 20,
            0, 300, Options.waterFogStrengthPercent[dim], Options.PERCENT_DEFAULT,
            v -> getGenericValueText(Text.translatable("options.video.environment.water_fog_strength"),
                Text.literal(v + "%")),
            v -> Options.setWaterFogStrengthPercent(dim, v, true));
        this.body.addEntry(new RadianceSettingsScreen.SliderEntry(waterFogSlider, body));
    }
}
