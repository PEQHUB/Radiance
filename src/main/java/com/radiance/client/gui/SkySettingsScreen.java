package com.radiance.client.gui;

import static net.minecraft.client.option.GameOptions.getGenericValueText;

import com.radiance.client.option.Options;
import com.radiance.client.util.CategoryVideoOptionEntry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.option.GameOptionsScreen;
import net.minecraft.text.Text;

public class SkySettingsScreen extends GameOptionsScreen {

    public SkySettingsScreen(Screen parent) {
        super(parent, MinecraftClient.getInstance().options,
            Text.translatable("radiance.settings.environment.sky.title"));
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        RadianceTheme.drawOutlinedText(context, this.textRenderer,
            Text.literal("Radiance > Environment > Sky"), 20, 26, RadianceTheme.textSecondary);
    }

    @Override
    protected void initBody() {
        this.body = this.layout.addBody(
            new WideOptionListWidget(this.client, this.width, this));
        addOptions();
    }

    @Override
    protected void addOptions() {
        int dim = Options.getEnvironmentEditingDimension();

        this.body.addEntry(new CategoryVideoOptionEntry(
            Text.translatable("options.video.environment.sky.category"), body));

        ResettableSliderWidget skyBrightnessSlider = new ResettableSliderWidget(
            0, 0, 150, 20,
            0, 300, Options.skyBrightnessPercent[dim], Options.PERCENT_DEFAULT,
            v -> getGenericValueText(Text.translatable("options.video.environment.sky_brightness"),
                Text.literal(v + "%")),
            v -> Options.setSkyBrightnessPercent(dim, v, true));
        this.body.addEntry(new RadianceSettingsScreen.SliderEntry(skyBrightnessSlider, body));

        ResettableSliderWidget rainBlendSlider = new ResettableSliderWidget(
            0, 0, 150, 20,
            0, 300, Options.rainBlendPercent[dim], Options.PERCENT_DEFAULT,
            v -> getGenericValueText(Text.translatable("options.video.environment.rain_blend"),
                Text.literal(v + "%")),
            v -> Options.setRainBlendPercent(dim, v, true));
        this.body.addEntry(new RadianceSettingsScreen.SliderEntry(rainBlendSlider, body));
    }
}
