package com.radiance.client.gui;

import static net.minecraft.client.option.GameOptions.getGenericValueText;

import com.radiance.client.option.Options;
import com.radiance.client.util.CategoryVideoOptionEntry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.option.GameOptionsScreen;
import net.minecraft.text.Text;

public class CloudSettingsScreen extends GameOptionsScreen {

    public CloudSettingsScreen(Screen parent) {
        super(parent, MinecraftClient.getInstance().options,
            Text.translatable("radiance.settings.environment.clouds.title"));
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        RadianceTheme.drawBreadcrumb(context, this.textRenderer, "Radiance > Environment > Clouds", this.parent);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (RadianceTheme.handleBreadcrumbClick(mouseX, mouseY, this.parent)) return true;
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY)) return true;
        if (button == 1) {
            Element focused = getFocused();
            if (focused != null) return focused.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
        }
        return false;
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
            Text.translatable("options.video.environment.clouds.category"), body));

        ResettableSliderWidget cloudBrightnessSlider = new ResettableSliderWidget(
            0, 0, 150, 20,
            0, 300, Options.cloudBrightnessPercent[dim], Options.PERCENT_DEFAULT,
            v -> getGenericValueText(Text.translatable("options.video.environment.cloud_brightness"),
                Text.literal(v + "%")),
            v -> Options.setCloudBrightnessPercent(dim, v, true));
        this.body.addEntry(new RadianceSettingsScreen.SliderEntry(cloudBrightnessSlider, body));

        ResettableSliderWidget cloudAlphaSlider = new ResettableSliderWidget(
            0, 0, 150, 20,
            0, 300, Options.cloudAlphaPercent[dim], Options.PERCENT_DEFAULT,
            v -> getGenericValueText(Text.translatable("options.video.environment.cloud_alpha"),
                Text.literal(v + "%")),
            v -> Options.setCloudAlphaPercent(dim, v, true));
        this.body.addEntry(new RadianceSettingsScreen.SliderEntry(cloudAlphaSlider, body));

        ResettableSliderWidget cloudHeightSlider = new ResettableSliderWidget(
            0, 0, 150, 20,
            -64, 64, Options.cloudHeightOffset[dim], 0,
            v -> getGenericValueText(Text.translatable("options.video.environment.cloud_height_offset"),
                Text.literal(v + "")),
            v -> Options.setCloudHeightOffset(dim, v, true));
        this.body.addEntry(new RadianceSettingsScreen.SliderEntry(cloudHeightSlider, body));

        this.body.addEntry(new CategoryVideoOptionEntry(
            Text.translatable("options.video.environment.clouds.volumetric.category"), body));

        ResettableSliderWidget cloudDetailScaleSlider = new ResettableSliderWidget(
            0, 0, 150, 20,
            25, 300, Options.cloudDetailScalePercent[dim], Options.CLOUD_DETAIL_SCALE_DEFAULT_PERCENT,
            v -> getGenericValueText(Text.translatable("options.video.environment.cloud_detail_scale"),
                Text.literal(v + "%")),
            v -> Options.setCloudDetailScalePercent(dim, v, true));
        this.body.addEntry(new RadianceSettingsScreen.SliderEntry(cloudDetailScaleSlider, body));

        ResettableSliderWidget cloudDetailStrengthSlider = new ResettableSliderWidget(
            0, 0, 150, 20,
            0, 300, Options.cloudDetailStrengthPercent[dim], Options.CLOUD_DETAIL_STRENGTH_DEFAULT_PERCENT,
            v -> getGenericValueText(Text.translatable("options.video.environment.cloud_detail_strength"),
                Text.literal(v + "%")),
            v -> Options.setCloudDetailStrengthPercent(dim, v, true));
        this.body.addEntry(new RadianceSettingsScreen.SliderEntry(cloudDetailStrengthSlider, body));

        ResettableSliderWidget cloudShadowStrengthSlider = new ResettableSliderWidget(
            0, 0, 150, 20,
            0, 200, Options.cloudShadowStrengthPercent[dim], Options.PERCENT_DEFAULT,
            v -> getGenericValueText(Text.translatable("options.video.environment.cloud_shadow_strength"),
                Text.literal(v + "%")),
            v -> Options.setCloudShadowStrengthPercent(dim, v, true));
        this.body.addEntry(new RadianceSettingsScreen.SliderEntry(cloudShadowStrengthSlider, body));

        ResettableSliderWidget cloudThicknessSlider = new ResettableSliderWidget(
            0, 0, 150, 20,
            1, 16, Options.cloudThicknessBlocks[dim], 4,
            v -> getGenericValueText(Text.translatable("options.video.environment.cloud_thickness"),
                Text.literal(v + " blocks")),
            v -> Options.setCloudThicknessBlocks(dim, v, true));
        this.body.addEntry(new RadianceSettingsScreen.SliderEntry(cloudThicknessSlider, body));

        ResettableSliderWidget cloudDensitySlider = new ResettableSliderWidget(
            0, 0, 150, 20,
            0, 300, Options.cloudDensityPercent[dim], Options.PERCENT_DEFAULT,
            v -> getGenericValueText(Text.translatable("options.video.environment.cloud_density"),
                Text.literal(v + "%")),
            v -> Options.setCloudDensityPercent(dim, v, true));
        this.body.addEntry(new RadianceSettingsScreen.SliderEntry(cloudDensitySlider, body));

        ResettableSliderWidget cloudShadowNoiseSlider = new ResettableSliderWidget(
            0, 0, 150, 20,
            0, 1, Options.cloudNoiseAffectsShadows[dim], dim == 0 ? 1 : 0,
            v -> getGenericValueText(Text.translatable("options.video.environment.cloud_shadow_noise"),
                v == 1 ? Text.translatable("options.on") : Text.translatable("options.off")),
            v -> Options.setCloudNoiseAffectsShadows(dim, v == 1, true));
        this.body.addEntry(new RadianceSettingsScreen.SliderEntry(cloudShadowNoiseSlider, body));

        // --- Volumetric Cloud Module ---
        this.body.addEntry(new CategoryVideoOptionEntry(
            Text.translatable("options.video.environment.clouds.volumetric_module.category"), body));

        ResettableSliderWidget volCloudQualitySlider = new ResettableSliderWidget(
            0, 0, 150, 20,
            0, 5, Options.volCloudQuality, 3,
            v -> getGenericValueText(Text.translatable("options.video.environment.vol_cloud_quality"),
                Text.literal(Options.VOL_CLOUD_QUALITY_NAMES[v])),
            v -> Options.setVolCloudQuality(v, true));
        this.body.addEntry(new RadianceSettingsScreen.SliderEntry(volCloudQualitySlider, body));

        ResettableSliderWidget volCloudDensitySlider = new ResettableSliderWidget(
            0, 0, 150, 20,
            1, 30, Options.volCloudDensityTenths, 10,
            v -> getGenericValueText(Text.translatable("options.video.environment.vol_cloud_density"),
                Text.literal(String.format("%.1f", v / 10.0))),
            v -> Options.setVolCloudDensityTenths(v, true));
        this.body.addEntry(new RadianceSettingsScreen.SliderEntry(volCloudDensitySlider, body));

        ResettableSliderWidget volCloudCoverageSlider = new ResettableSliderWidget(
            0, 0, 150, 20,
            0, 100, Options.volCloudCoveragePercent, 35,
            v -> getGenericValueText(Text.translatable("options.video.environment.vol_cloud_coverage"),
                Text.literal(v + "%")),
            v -> Options.setVolCloudCoveragePercent(v, true));
        this.body.addEntry(new RadianceSettingsScreen.SliderEntry(volCloudCoverageSlider, body));

        ResettableSliderWidget volCloudTypeSlider = new ResettableSliderWidget(
            0, 0, 150, 20,
            0, 100, Options.volCloudTypePercent, 67,
            v -> {
                // New ordering: 0=Stratus, 33=Stratocumulus, 67=Cumulus, 100=Cumulonimbus
                int idx = Math.min(v * 4 / 101, 3);
                return getGenericValueText(Text.translatable("options.video.environment.vol_cloud_type"),
                    Text.literal(Options.VOL_CLOUD_TYPE_NAMES[idx]));
            },
            v -> Options.setVolCloudTypePercent(v, true));
        this.body.addEntry(new RadianceSettingsScreen.SliderEntry(volCloudTypeSlider, body));

        ResettableSliderWidget volCloudSpeedSlider = new ResettableSliderWidget(
            0, 0, 150, 20,
            0, 50, Options.volCloudSpeedTenths, 10,
            v -> getGenericValueText(Text.translatable("options.video.environment.vol_cloud_wind_speed"),
                Text.literal(String.format("%.1f", v / 10.0))),
            v -> Options.setVolCloudSpeedTenths(v, true));
        this.body.addEntry(new RadianceSettingsScreen.SliderEntry(volCloudSpeedSlider, body));

        ResettableSliderWidget volCloudAltitudeSlider = new ResettableSliderWidget(
            0, 0, 150, 20,
            128, 320, Options.volCloudAltitude, 192,
            v -> getGenericValueText(Text.translatable("options.video.environment.vol_cloud_altitude"),
                Text.literal(v + " blocks")),
            v -> Options.setVolCloudAltitude(v, true));
        this.body.addEntry(new RadianceSettingsScreen.SliderEntry(volCloudAltitudeSlider, body));

        ResettableSliderWidget volCloudThicknessSlider = new ResettableSliderWidget(
            0, 0, 150, 20,
            32, 128, Options.volCloudThickness, 64,
            v -> getGenericValueText(Text.translatable("options.video.environment.vol_cloud_thickness"),
                Text.literal(v + " blocks")),
            v -> Options.setVolCloudThickness(v, true));
        this.body.addEntry(new RadianceSettingsScreen.SliderEntry(volCloudThicknessSlider, body));

    }
}
