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
        RadianceTheme.drawBreadcrumb(context, this.textRenderer, "Radiance > Environment > Sky", this.parent);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (RadianceTheme.handleBreadcrumbClick(mouseX, mouseY, this.parent)) return true;
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (RadianceTheme.handlePeekKeyPressed(keyCode)) return true;
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        if (RadianceTheme.handlePeekKeyReleased(keyCode)) return true;
        return super.keyReleased(keyCode, scanCode, modifiers);
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        // Transparent — game world shows through
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
