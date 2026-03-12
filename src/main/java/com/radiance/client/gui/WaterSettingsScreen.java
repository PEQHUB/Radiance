package com.radiance.client.gui;

import static net.minecraft.client.option.GameOptions.getGenericValueText;

import com.radiance.client.option.Options;
import com.radiance.client.util.CategoryVideoOptionEntry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.option.GameOptionsScreen;
import net.minecraft.text.Text;

public class WaterSettingsScreen extends GameOptionsScreen {

    public WaterSettingsScreen(Screen parent) {
        super(parent, MinecraftClient.getInstance().options,
            Text.translatable("radiance.settings.environment.water.title"));
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        RadianceTheme.drawOutlinedText(context, this.textRenderer,
            Text.literal("Radiance > Environment > Water"), 20, 26, RadianceTheme.textSecondary);

        // Water tint color swatch
        int dim = Options.getEnvironmentEditingDimension();
        int r = (int)(Options.waterTintR[dim] * 255 / 100.0f);
        int g = (int)(Options.waterTintG[dim] * 255 / 100.0f);
        int b = (int)(Options.waterTintB[dim] * 255 / 100.0f);
        int swatchColor = 0xFF000000 | (r << 16) | (g << 8) | b;
        int swatchX = this.width - 40;
        int swatchY = 22;
        int swatchSize = 16;
        context.fill(swatchX, swatchY, swatchX + swatchSize, swatchY + swatchSize, swatchColor);
        context.fill(swatchX - 1, swatchY - 1, swatchX + swatchSize + 1, swatchY, RadianceTheme.borderDefault);
        context.fill(swatchX - 1, swatchY + swatchSize, swatchX + swatchSize + 1, swatchY + swatchSize + 1, RadianceTheme.borderDefault);
        context.fill(swatchX - 1, swatchY, swatchX, swatchY + swatchSize, RadianceTheme.borderDefault);
        context.fill(swatchX + swatchSize, swatchY, swatchX + swatchSize + 1, swatchY + swatchSize, RadianceTheme.borderDefault);
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
