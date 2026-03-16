package com.radiance.client.gui;

import static net.minecraft.client.option.GameOptions.getGenericValueText;

import com.radiance.client.option.Options;
import com.radiance.client.util.CategoryVideoOptionEntry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.option.GameOptionsScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.option.SimpleOption;
import net.minecraft.text.Text;

public class RenderingSettingsScreen extends GameOptionsScreen {

    private final Screen parentScreen;

    public RenderingSettingsScreen(Screen parent) {
        super(parent, MinecraftClient.getInstance().options,
            Text.translatable("options.video.rendering_settings"));
        this.parentScreen = parent;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        RadianceTheme.drawBreadcrumb(context, this.textRenderer, "Radiance > Rendering", parentScreen);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (RadianceTheme.handleBreadcrumbClick(mouseX, mouseY, parentScreen)) return true;
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

    /** Helper: create a ButtonWidget that opens a sub-screen. */
    private ButtonWidget subScreenButton(String translationKey, Screen target) {
        return ButtonWidget.builder(
            Text.translatable(translationKey),
            btn -> MinecraftClient.getInstance().setScreen(target))
            .width(150).build();
    }

    @Override
    protected void addOptions() {
        MinecraftClient mc = MinecraftClient.getInstance();
        RenderingSettingsScreen self = this;

        // ── DISPLAY MODE ──
        this.body.addEntry(new CategoryVideoOptionEntry(
            Text.literal("Display Mode"), body));

        // [Tonemapper: PsychoV/BT.2390 cycle] | [SDR: sRGB/Gamma 2.2 cycle]
        SimpleOption<Boolean> psychoToggle = SimpleOption.ofBoolean(
            Options.PSYCHO_ENABLED_KEY,
            Options.psychoEnabled,
            value -> {
                Options.setPsychoEnabled(value, true);
                mc.setScreen(new RenderingSettingsScreen(parentScreen));
            });

        SimpleOption<Integer> sdrTransferFunction = new SimpleOption<>(
            Options.SDR_TRANSFER_FUNCTION_KEY,
            SimpleOption.emptyTooltip(),
            (optionText, value) -> getGenericValueText(optionText,
                Text.translatable(value == Options.SDR_TRANSFER_FUNCTION_SRGB
                    ? Options.SDR_TRANSFER_FUNCTION_SRGB_KEY
                    : Options.SDR_TRANSFER_FUNCTION_GAMMA_22_KEY)),
            new SimpleOption.ValidatingIntSliderCallbacks(0, 1),
            com.mojang.serialization.Codec.intRange(0, 1),
            Options.sdrTransferFunction,
            value -> Options.setSdrTransferFunction(value, true));

        this.body.addEntry(new RadianceSettingsScreen.TwoColumnOptionEntry(
            psychoToggle.createWidget(gameOptions),
            sdrTransferFunction.createWidget(gameOptions), body));

        // [HDR: ON/OFF toggle] — only if HDR is supported
        if (Options.isHdrSupported()) {
            SimpleOption<Boolean> hdrToggle = SimpleOption.ofBoolean(
                Options.HDR_ENABLED_KEY,
                Options.hdrEnabled,
                value -> {
                    Options.setHdrEnabled(value, true);
                    mc.setScreen(new RenderingSettingsScreen(parentScreen));
                });
            this.body.addEntry(new RadianceSettingsScreen.TwoColumnOptionEntry(
                hdrToggle.createWidget(gameOptions), null, body));
        }

        // ── HDR ── (only if HDR enabled and supported)
        if (Options.hdrEnabled && Options.isHdrSupported()) {
            this.body.addEntry(new CategoryVideoOptionEntry(
                Text.translatable(Options.CATEGORY_HDR), body));

            // [HDR Peak Nits: 10-1000 (x10)] | [Paper White: 1-500]
            ResettableSliderWidget peakNitsSlider = new ResettableSliderWidget(
                0, 0, 150, 20,
                10, 1000, Options.hdrPeakNits / 10, 107,
                v -> getGenericValueText(
                    Text.translatable(Options.HDR_PEAK_NITS_KEY),
                    Text.literal(Integer.toString(v * 10))),
                v -> Options.setHdrPeakNits(v * 10, true));

            ResettableSliderWidget paperWhiteSlider = new ResettableSliderWidget(
                0, 0, 150, 20,
                1, 500, Options.hdrPaperWhiteNits, 203,
                v -> getGenericValueText(
                    Text.translatable(Options.HDR_PAPER_WHITE_NITS_KEY),
                    Text.literal(Integer.toString(v))),
                v -> Options.setHdrPaperWhiteNits(v, true));

            this.body.addEntry(new RadianceSettingsScreen.TwoColumnSliderEntry(
                peakNitsSlider, paperWhiteSlider, body));

            // [UI Brightness: 5-30 (x10)]
            ResettableSliderWidget uiBrightnessSlider = new ResettableSliderWidget(
                0, 0, 150, 20,
                5, 30, Options.hdrUiBrightnessNits / 10, 10,
                v -> getGenericValueText(
                    Text.literal("UI Brightness"),
                    Text.literal(Integer.toString(v * 10))),
                v -> Options.setHdrUiBrightnessNits(v * 10, true));
            this.body.addEntry(new RadianceSettingsScreen.SliderEntry(uiBrightnessSlider, body));
        }

        // ── EXPOSURE ──
        this.body.addEntry(new CategoryVideoOptionEntry(
            Text.translatable(Options.CATEGORY_TONEMAPPING), body));

        // [Exposure Settings... | Manual Exposure ON/OFF]
        SimpleOption<Boolean> manualExposure = SimpleOption.ofBoolean(
            Options.MANUAL_EXPOSURE_ENABLED_KEY,
            Options.manualExposureEnabled,
            value -> {
                Options.setManualExposureEnabled(value, true);
                mc.setScreen(new RenderingSettingsScreen(parentScreen));
            });
        this.body.addEntry(new RadianceSettingsScreen.TwoColumnOptionEntry(
            subScreenButton(Options.EXPOSURE_SETTINGS_KEY, new ExposureSettingsScreen(this)),
            manualExposure.createWidget(gameOptions), body));

        // Exposure Compensation: -3.0 to +3.0 EV (slider 0-60 offset by 30)
        ResettableSliderWidget ecSlider = new ResettableSliderWidget(
            0, 0, 150, 20,
            0, 60, Options.exposureCompensation + 30, 0 + 30,
            v -> getGenericValueText(
                Text.translatable(Options.EXPOSURE_COMPENSATION_KEY),
                Text.literal(String.format("%+.1f EV", (v - 30) / 10.0))),
            v -> Options.setExposureCompensation(v - 30, true));
        this.body.addEntry(new RadianceSettingsScreen.SliderEntry(ecSlider, body));

        // Max Exposure: 1-100, display as "X.X"
        ResettableSliderWidget maxExpSlider = new ResettableSliderWidget(
            0, 0, 150, 20,
            1, 100, Options.maxExposure, 20,
            v -> getGenericValueText(
                Text.translatable(Options.MAX_EXPOSURE_KEY),
                Text.literal(String.format("%.1f", v / 10.0))),
            v -> Options.setMaxExposure(v, true));
        this.body.addEntry(new RadianceSettingsScreen.SliderEntry(maxExpSlider, body));

        // ── PSYCHOV ──
        this.body.addEntry(new CategoryVideoOptionEntry(
            Text.translatable(Options.CATEGORY_PSYCHO), body));

        // [PsychoV Settings... | (empty)]
        this.body.addEntry(new RadianceSettingsScreen.TwoColumnOptionEntry(
            subScreenButton(Options.CATEGORY_PSYCHO, new PsychoVSettingsScreen(this)),
            null, body));

        // ── COLOR ──
        this.body.addEntry(new CategoryVideoOptionEntry(
            Text.literal("Color"), body));

        // Saturation: 100-200, display as "X.XX"
        ResettableSliderWidget satSlider = new ResettableSliderWidget(
            0, 0, 150, 20,
            100, 200, Options.saturationPercent, Options.SATURATION_DEFAULT_PERCENT,
            v -> getGenericValueText(
                Text.translatable(Options.SATURATION_KEY),
                Text.literal(String.format("%.2f", v / 100.0))),
            v -> Options.setSaturation(v, true));
        this.body.addEntry(new RadianceSettingsScreen.SliderEntry(satSlider, body));

        // [Adaptive Saturation | Noise LOD]
        SimpleOption<Boolean> adaptiveSat = SimpleOption.ofBoolean(
            "options.video.saturation_adaptive",
            Options.saturationAdaptive,
            value -> Options.setSaturationAdaptive(value, true));
        SimpleOption<Boolean> noiseLod = SimpleOption.ofBoolean(
            "options.video.noise_lod",
            Options.noiseLOD,
            value -> Options.setNoiseLOD(value, true));
        this.body.addEntry(new RadianceSettingsScreen.TwoColumnOptionEntry(
            adaptiveSat.createWidget(gameOptions), noiseLod.createWidget(gameOptions), body));

        // Color Expansion: 0-200, display as "X.XX"
        ResettableSliderWidget ceSlider = new ResettableSliderWidget(
            0, 0, 150, 20,
            0, 200, Options.colorExpansionPercent, Options.COLOR_EXPANSION_DEFAULT_PERCENT,
            v -> getGenericValueText(
                Text.translatable(Options.COLOR_EXPANSION_KEY),
                Text.literal(String.format("%.2f", v / 100.0))),
            v -> Options.setColorExpansion(v, true));
        this.body.addEntry(new RadianceSettingsScreen.SliderEntry(ceSlider, body));

        // ── BRDF ──
        this.body.addEntry(new CategoryVideoOptionEntry(
            Text.literal("BRDF"), body));

        // [Multi-Scatter GGX | EON Diffuse]
        SimpleOption<Boolean> multiScatter = SimpleOption.ofBoolean(
            Options.MULTI_SCATTER_GGX_KEY,
            Options.multiScatterGGX,
            value -> Options.setMultiScatterGGX(value, true));
        SimpleOption<Boolean> eonDiffuse = SimpleOption.ofBoolean(
            Options.EON_DIFFUSE_KEY,
            Options.eonDiffuse,
            value -> Options.setEonDiffuse(value, true));
        this.body.addEntry(new RadianceSettingsScreen.TwoColumnOptionEntry(
            multiScatter.createWidget(gameOptions), eonDiffuse.createWidget(gameOptions), body));
    }
}
