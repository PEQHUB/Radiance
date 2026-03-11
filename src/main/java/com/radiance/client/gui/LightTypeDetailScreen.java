package com.radiance.client.gui;

import static net.minecraft.client.option.GameOptions.getGenericValueText;

import com.radiance.client.option.Options;
import com.radiance.client.util.CategoryVideoOptionEntry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.option.GameOptionsScreen;
import net.minecraft.text.Text;

public class LightTypeDetailScreen extends GameOptionsScreen {

    private final Screen parentScreen;
    private final int lightTypeId;
    private final String translationKey;

    public LightTypeDetailScreen(Screen parent, int lightTypeId, String translationKey) {
        super(parent, MinecraftClient.getInstance().options,
            Text.translatable(translationKey));
        this.parentScreen = parent;
        this.lightTypeId = lightTypeId;
        this.translationKey = translationKey;
    }

    @Override
    protected void addOptions() {
        int id = this.lightTypeId;

        // --- Light Mode (Auto / Force Area Light / Force Emissive) ---
        ResettableSliderWidget modeSlider = new ResettableSliderWidget(
            0, 0, 150, 20,
            0, 2, Options.blockLightMode[id], Options.LIGHT_MODE_AUTO,
            v -> {
                String[] labels = {"Auto", "Force Area Light", "Force Emissive"};
                return getGenericValueText(
                    Text.translatable("options.video.area_light.block.light_mode"),
                    Text.literal(labels[v]));
            },
            v -> Options.setBlockLightMode(id, v, true));
        this.body.addEntry(new RadianceSettingsScreen.SliderEntry(modeSlider, body));

        // --- Intensity ---
        ResettableSliderWidget intensitySlider = new ResettableSliderWidget(
            0, 0, 150, 20,
            0, 500, Options.areaLightBlockIntensity[id], 100,
            v -> getGenericValueText(
                Text.translatable("options.video.area_light.block.intensity"),
                Text.literal(v + "%")),
            v -> Options.setAreaLightBlockIntensityPercent(id, v, true));
        this.body.addEntry(new RadianceSettingsScreen.SliderEntry(intensitySlider, body));

        // --- Scale ---
        ResettableSliderWidget scaleSlider = new ResettableSliderWidget(
            0, 0, 150, 20,
            10, 500, Options.areaLightBlockScale[id], 100,
            v -> getGenericValueText(
                Text.translatable("options.video.area_light.block.scale"),
                Text.literal(v + "%")),
            v -> Options.setAreaLightBlockScale(id, v, true));
        this.body.addEntry(new RadianceSettingsScreen.SliderEntry(scaleSlider, body));

        // --- Y Offset ---
        ResettableSliderWidget yOffsetSlider = new ResettableSliderWidget(
            0, 0, 150, 20,
            -50, 50, Options.areaLightBlockYOffset[id], 0,
            v -> getGenericValueText(
                Text.translatable("options.video.area_light.block.y_offset"),
                Text.literal(String.format("%.2f blocks", v / 100.0))),
            v -> Options.setAreaLightBlockYOffset(id, v, true));
        this.body.addEntry(new RadianceSettingsScreen.SliderEntry(yOffsetSlider, body));

        // --- Color ---
        this.body.addEntry(new CategoryVideoOptionEntry(
            Text.literal("Color"), body));

        int defR = (id < Options.DEFAULT_LIGHT_COLORS.length) ? Options.DEFAULT_LIGHT_COLORS[id][0] : 255;
        int defG = (id < Options.DEFAULT_LIGHT_COLORS.length) ? Options.DEFAULT_LIGHT_COLORS[id][1] : 255;
        int defB = (id < Options.DEFAULT_LIGHT_COLORS.length) ? Options.DEFAULT_LIGHT_COLORS[id][2] : 255;

        ResettableSliderWidget rSlider = new ResettableSliderWidget(
            0, 0, 150, 20,
            0, 255, Options.areaLightBlockColorR[id], defR,
            v -> getGenericValueText(
                Text.translatable("options.video.area_light.block.color_r"),
                Text.literal(String.valueOf(v))),
            v -> Options.setAreaLightBlockColorR(id, v, true));
        this.body.addEntry(new RadianceSettingsScreen.SliderEntry(rSlider, body));

        ResettableSliderWidget gSlider = new ResettableSliderWidget(
            0, 0, 150, 20,
            0, 255, Options.areaLightBlockColorG[id], defG,
            v -> getGenericValueText(
                Text.translatable("options.video.area_light.block.color_g"),
                Text.literal(String.valueOf(v))),
            v -> Options.setAreaLightBlockColorG(id, v, true));
        this.body.addEntry(new RadianceSettingsScreen.SliderEntry(gSlider, body));

        ResettableSliderWidget bSlider = new ResettableSliderWidget(
            0, 0, 150, 20,
            0, 255, Options.areaLightBlockColorB[id], defB,
            v -> getGenericValueText(
                Text.translatable("options.video.area_light.block.color_b"),
                Text.literal(String.valueOf(v))),
            v -> Options.setAreaLightBlockColorB(id, v, true));
        this.body.addEntry(new RadianceSettingsScreen.SliderEntry(bSlider, body));
    }
}
