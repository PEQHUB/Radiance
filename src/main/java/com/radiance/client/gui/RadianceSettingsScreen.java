package com.radiance.client.gui;

import static net.minecraft.client.option.GameOptions.getGenericValueText;

import com.google.common.collect.ImmutableList;
import com.mojang.serialization.Codec;
import com.radiance.client.option.Options;
import com.radiance.client.option.TonemappingMode;
import com.radiance.client.util.CategoryVideoOptionEntry;
import java.util.List;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.Selectable;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.option.GameOptionsScreen;
import net.minecraft.client.gui.widget.OptionListWidget;
import net.minecraft.client.option.SimpleOption;
import net.minecraft.text.Text;

public class RadianceSettingsScreen extends GameOptionsScreen {

    private final Screen parentScreen;

    public RadianceSettingsScreen(Screen parent) {
        super(parent, MinecraftClient.getInstance().options, Text.translatable("radiance.settings.title"));
        this.parentScreen = parent;
    }

    @Override
    protected void addOptions() {

        // === Tonemapping ===
        this.body.addEntry(
            new CategoryVideoOptionEntry(Text.translatable(Options.CATEGORY_TONEMAPPING), body));

        // HDR mode: 4 HDR-capable operators. SDR mode: all 8 original operators.
        String[] hdrTonemapKeys = {
            Options.TONEMAP_MODE_HDR_HERMITE_REINHARD,
            Options.TONEMAP_MODE_HDR_REINHARD_EXTENDED,
            Options.TONEMAP_MODE_HDR_BT2390,
            Options.TONEMAP_MODE_HDR_FROSTBITE
        };
        int maxMode = Options.hdrEnabled ? hdrTonemapKeys.length - 1 : 7;
        // Clamp current mode to valid range when switching HDR on/off
        int currentMode = Math.min(Options.tonemappingMode, maxMode);
        if (currentMode != Options.tonemappingMode) {
            Options.setTonemappingMode(currentMode, true);
        }
        SimpleOption<Integer> tonemapMode = new SimpleOption<>(
            Options.TONEMAP_MODE_KEY,
            SimpleOption.emptyTooltip(),
            (optionText, value) -> {
                if (Options.hdrEnabled) {
                    int idx = Math.min(value, hdrTonemapKeys.length - 1);
                    return getGenericValueText(optionText, Text.translatable(hdrTonemapKeys[idx]));
                } else {
                    return getGenericValueText(optionText,
                        Text.translatable(TonemappingMode.byOrdinal(value).getTranslationKey()));
                }
            },
            new SimpleOption.ValidatingIntSliderCallbacks(0, maxMode),
            Codec.intRange(0, maxMode),
            currentMode,
            value -> {
                Options.setTonemappingMode(value, true);
                // Rebuild the screen so exposure sliders pick up the new preset values
                MinecraftClient.getInstance().setScreen(new RadianceSettingsScreen(parentScreen));
            });
        this.body.addSingleOptionEntry(tonemapMode);

        // Saturation: 1.0 to 2.0 (stored as percent 100-200) - HDR-only
        ResettableSliderWidget satSlider = new ResettableSliderWidget(
            0, 0, 150, 20,
            100, 200, Options.saturationPercent, 100,
            v -> getGenericValueText(
                Text.translatable(Options.SATURATION_KEY),
                Text.literal(String.format("%.2f", v / 100.0))),
            v -> Options.setSaturation(v, true));
        this.body.addEntry(new SliderEntry(satSlider, body));

        // Min Exposure: 0.0001 to 1.0 (stored as ten-thousandths 1-10000)
        ResettableSliderWidget minExpSlider = new ResettableSliderWidget(
            0, 0, 150, 20,
            1, 10000, Options.minExposureTenK, 1,
            v -> getGenericValueText(
                Text.translatable(Options.MIN_EXPOSURE_KEY),
                Text.literal(String.format("%.4f", v / 10000.0))),
            v -> Options.setMinExposure(v, true));
        this.body.addEntry(new SliderEntry(minExpSlider, body));

        SimpleOption<Integer> maxExposure = new SimpleOption<>(
            Options.MAX_EXPOSURE_KEY,
            SimpleOption.emptyTooltip(),
            (optionText, value) -> getGenericValueText(optionText,
                Text.literal(Integer.toString(value))),
            new SimpleOption.ValidatingIntSliderCallbacks(1, 20),
            Codec.intRange(1, 20),
            Options.maxExposure,
            value -> Options.setMaxExposure(value, true));
        this.body.addSingleOptionEntry(maxExposure);

        int mode = Options.tonemappingMode;

        // Exposure Compensation: -3.0 to +3.0 EV (stored as tenths, slider 0-60 offset by 30)
        ResettableSliderWidget ecSlider = new ResettableSliderWidget(
            0, 0, 150, 20,
            0, 60, Options.exposureCompensation + 30, 0 + 30,
            v -> getGenericValueText(
                Text.translatable(Options.EXPOSURE_COMPENSATION_KEY),
                Text.literal(String.format("%+.1f EV", (v - 30) / 10.0))),
            v -> Options.setExposureCompensation(v - 30, true));
        this.body.addEntry(new SliderEntry(ecSlider, body));

        // Middle Grey: 0.01 to 0.50 (stored as percent 1-50)
        ResettableSliderWidget mgSlider = new ResettableSliderWidget(
            0, 0, 150, 20,
            1, 50, Options.middleGreyPercent, 18,
            v -> getGenericValueText(
                Text.translatable(Options.MIDDLE_GREY_KEY),
                Text.literal(String.format("%.2f", v / 100.0))),
            v -> Options.setMiddleGrey(v, true));
        this.body.addEntry(new SliderEntry(mgSlider, body));

        // White Point (Lwhite): shown for modes that use Lwhite parameter
        // SDR: mode 1 (Reinhard Extended). HDR: mode 0 (Hermite Spline Reinhard), mode 1 (Reinhard Extended)
        boolean showLwhite = Options.hdrEnabled ? (mode == 0 || mode == 1) : (mode == 1);
        if (showLwhite) {
            ResettableSliderWidget lwSlider = new ResettableSliderWidget(
                0, 0, 150, 20,
                10, 200, Options.LwhiteTenths, 40,
                v -> getGenericValueText(
                    Text.translatable(Options.LWHITE_KEY),
                    Text.literal(String.format("%.1f", v / 10.0))),
                v -> Options.setLwhite(v, true));
            this.body.addEntry(new SliderEntry(lwSlider, body));
        }

        // === Emission ===
        this.body.addEntry(
            new CategoryVideoOptionEntry(Text.translatable(Options.CATEGORY_EMISSION), body));

        SimpleOption<Boolean> emissionSettings = new SimpleOption<>(
            "options.video.emission_settings",
            SimpleOption.emptyTooltip(),
            (optionText, value) -> optionText,
            new PotentialValuesBasedCallbacksNoValue<>(
                ImmutableList.of(Boolean.TRUE, Boolean.FALSE), Codec.BOOL),
            false,
            value -> MinecraftClient.getInstance().setScreen(new EmissiveBlockSettingsScreen(this)));
        this.body.addSingleOptionEntry(emissionSettings);

        // === HDR10 Output ===
        this.body.addEntry(
            new CategoryVideoOptionEntry(Text.translatable(Options.CATEGORY_HDR), body));

        SimpleOption<Boolean> hdrEnabled = SimpleOption.ofBoolean(
            Options.HDR_ENABLED_KEY, Options.hdrEnabled,
            value -> {
                Options.setHdrEnabled(value, true);
                // Rebuild screen to show/hide HDR sliders
                MinecraftClient.getInstance().setScreen(new RadianceSettingsScreen(parentScreen));
            });
        this.body.addSingleOptionEntry(hdrEnabled);

        if (Options.hdrEnabled) {
            // Peak Brightness: 400–10000 nits (step ~100)
            ResettableSliderWidget peakNitsSlider = new ResettableSliderWidget(
                0, 0, 150, 20,
                4, 100, Options.hdrPeakNits / 100, 10,
                v -> getGenericValueText(
                    Text.translatable(Options.HDR_PEAK_NITS_KEY),
                    Text.literal(v * 100 + " nits")),
                v -> Options.setHdrPeakNits(v * 100, true));
            this.body.addEntry(new SliderEntry(peakNitsSlider, body));

            // Paper White: 80–500 nits
            ResettableSliderWidget paperWhiteSlider = new ResettableSliderWidget(
                0, 0, 150, 20,
                80, 500, Options.hdrPaperWhiteNits, 203,
                v -> getGenericValueText(
                    Text.translatable(Options.HDR_PAPER_WHITE_NITS_KEY),
                    Text.literal(v + " nits")),
                v -> Options.setHdrPaperWhiteNits(v, true));
            this.body.addEntry(new SliderEntry(paperWhiteSlider, body));

            // UI Brightness: 50–300 nits (default 100)
            ResettableSliderWidget uiBrightnessSlider = new ResettableSliderWidget(
                0, 0, 150, 20,
                50, 300, Options.hdrUiBrightnessNits, 100,
                v -> getGenericValueText(
                    Text.translatable("options.video.hdr_ui_brightness_nits"),
                    Text.literal(v + " nits")),
                v -> Options.setHdrUiBrightnessNits(v, true));
            this.body.addEntry(new SliderEntry(uiBrightnessSlider, body));
        }

        // === Upscaler ===
        this.body.addEntry(
            new CategoryVideoOptionEntry(Text.translatable(Options.CATEGORY_UPSCALER), body));

        String[] upscalerModes = {"Off", "FSR3", "DLSS SR"};
        String[] upscalerModeKeys = {
            Options.UPSCALER_MODE_OFF, Options.UPSCALER_MODE_FSR3, Options.UPSCALER_MODE_DLSS_SR
        };
        SimpleOption<Integer> upscalerMode = new SimpleOption<>(
            Options.UPSCALER_MODE_KEY,
            SimpleOption.emptyTooltip(),
            (optionText, value) -> getGenericValueText(optionText,
                Text.translatable(upscalerModeKeys[Math.min(value, upscalerModeKeys.length - 1)])),
            new SimpleOption.ValidatingIntSliderCallbacks(0, upscalerModes.length - 1),
            Codec.intRange(0, upscalerModes.length - 1),
            Options.upscalerMode,
            value -> { Options.upscalerMode = value; Options.overwriteConfig(); });
        this.body.addSingleOptionEntry(upscalerMode);

        // === Upscaler Quality (works with DLSS, FSR, and future upscalers) ===
        String[] upscalerQualities = {"Performance", "Balanced", "Quality", "Native", "Custom"};
        String[] upscalerQualityKeys = {
            Options.UPSCALER_QUALITY_PERFORMANCE, Options.UPSCALER_QUALITY_BALANCED,
            Options.UPSCALER_QUALITY_QUALITY, Options.UPSCALER_QUALITY_NATIVE,
            Options.UPSCALER_QUALITY_CUSTOM
        };
        SimpleOption<Integer> upscalerQuality = new SimpleOption<>(
            Options.UPSCALER_QUALITY_KEY,
            SimpleOption.emptyTooltip(),
            (optionText, value) -> getGenericValueText(optionText,
                Text.translatable(upscalerQualityKeys[Math.min(value, upscalerQualityKeys.length - 1)])),
            new SimpleOption.ValidatingIntSliderCallbacks(0, upscalerQualities.length - 1),
            Codec.intRange(0, upscalerQualities.length - 1),
            Options.upscalerQuality,
            value -> Options.setUpscalerQuality(value, true));
        this.body.addSingleOptionEntry(upscalerQuality);

        String[] upscalerPresets = {"D", "E"};
        SimpleOption<Integer> upscalerPreset = new SimpleOption<>(
            Options.UPSCALER_PRESET_KEY,
            SimpleOption.emptyTooltip(),
            (optionText, value) -> getGenericValueText(optionText,
                Text.literal(upscalerPresets[Math.min(value, upscalerPresets.length - 1)])),
            new SimpleOption.ValidatingIntSliderCallbacks(0, upscalerPresets.length - 1),
            Codec.intRange(0, upscalerPresets.length - 1),
            Options.upscalerPreset == 5 ? 1 : 0,
            value -> Options.setUpscalerPreset(value == 0 ? 4 : 5, true));
        this.body.addSingleOptionEntry(upscalerPreset);

        SimpleOption<Integer> upscalerResOverride = new SimpleOption<>(
            Options.UPSCALER_RES_OVERRIDE_KEY,
            SimpleOption.emptyTooltip(),
            (optionText, value) -> getGenericValueText(optionText,
                Text.literal(value + "%")),
            new SimpleOption.ValidatingIntSliderCallbacks(33, 100),
            Codec.intRange(33, 100),
            Options.upscalerResOverride,
            value -> Options.setUpscalerResOverride(value, true));
        this.body.addSingleOptionEntry(upscalerResOverride);

        // === Ray Tracing ===
        this.body.addEntry(
            new CategoryVideoOptionEntry(Text.translatable(Options.CATEGORY_RAY_TRACING), body));

        SimpleOption<Integer> rayBounces = new SimpleOption<>(
            Options.RAY_BOUNCES_KEY,
            SimpleOption.emptyTooltip(),
            (optionText, value) -> getGenericValueText(optionText,
                Text.literal(Integer.toString(value))),
            new SimpleOption.ValidatingIntSliderCallbacks(0, 16),
            Codec.intRange(0, 16),
            Options.rayBounces,
            value -> Options.setRayBounces(value, true));
        this.body.addSingleOptionEntry(rayBounces);

        // === Window ===
        this.body.addEntry(
            new CategoryVideoOptionEntry(Text.translatable(Options.CATEGORY_WINDOW), body));

        SimpleOption<Integer> maxFps = new SimpleOption<>(
            "options.framerateLimit",
            SimpleOption.emptyTooltip(),
            (optionText, value) -> value == 260
                ? getGenericValueText(optionText, Text.translatable("options.framerateLimit.max"))
                : getGenericValueText(optionText, Text.translatable("options.framerate", value)),
            new SimpleOption.ValidatingIntSliderCallbacks(1, 26).withModifier(
                value -> value * 10, value -> value / 10),
            Codec.intRange(10, 260),
            Options.maxFps,
            value -> {
                MinecraftClient.getInstance().getInactivityFpsLimiter().setMaxFps(value);
                Options.setMaxFps(value, true);
            });
        this.body.addSingleOptionEntry(maxFps);

        SimpleOption<Boolean> enableVsync = SimpleOption.ofBoolean("options.vsync", Options.vsync,
            value -> Options.setVsync(value, true));
        this.body.addSingleOptionEntry(enableVsync);

        // === Terrain ===
        this.body.addEntry(
            new CategoryVideoOptionEntry(Text.translatable(Options.CATEGORY_TERRAIN), body));

        SimpleOption<Integer> chunkBatchSize = new SimpleOption<>(
            Options.CHUNK_BUILDING_BATCH_SIZE_KEY,
            SimpleOption.emptyTooltip(),
            (optionText, value) -> getGenericValueText(optionText,
                Text.literal(Integer.toString(value))),
            new SimpleOption.ValidatingIntSliderCallbacks(1, 32),
            Codec.intRange(1, 32),
            Options.chunkBuildingBatchSize,
            value -> Options.setChunkBuildingBatchSize(value, true));
        this.body.addSingleOptionEntry(chunkBatchSize);

        SimpleOption<Integer> chunkTotalBatches = new SimpleOption<>(
            Options.CHUNK_BUILDING_TOTAL_BATCHES_KEY,
            SimpleOption.emptyTooltip(),
            (optionText, value) -> getGenericValueText(optionText,
                Text.literal(Integer.toString(value))),
            new SimpleOption.ValidatingIntSliderCallbacks(1, 32),
            Codec.intRange(1, 32),
            Options.chunkBuildingTotalBatches,
            value -> Options.setChunkBuildingTotalBatches(value, true));
        this.body.addSingleOptionEntry(chunkTotalBatches);

        // === Pipeline ===
        this.body.addEntry(
            new CategoryVideoOptionEntry(Text.translatable(Options.CATEGORY_PIPELINE), body));

        SimpleOption<Boolean> pipelineSettings = new SimpleOption<>(
            Options.PIPELINE_SETUP_KEY,
            SimpleOption.emptyTooltip(),
            (optionText, value) -> optionText,
            new PotentialValuesBasedCallbacksNoValue<>(
                ImmutableList.of(Boolean.TRUE, Boolean.FALSE), Codec.BOOL),
            false,
            value -> MinecraftClient.getInstance().setScreen(new RenderPipelineScreen(this)));
        this.body.addSingleOptionEntry(pipelineSettings);

    }

    /** WidgetEntry that holds a single ResettableSliderWidget, centered like SimpleOption entries. */
    static class SliderEntry extends OptionListWidget.WidgetEntry {
        private final ResettableSliderWidget slider;
        private final OptionListWidget parent;

        SliderEntry(ResettableSliderWidget slider, OptionListWidget parent) {
            super(ImmutableList.of(slider), null);
            this.slider = slider;
            this.parent = parent;
        }

        @Override
        public void render(DrawContext context, int index, int y, int x, int entryWidth,
            int entryHeight, int mouseX, int mouseY, boolean hovered, float tickDelta) {
            // Center the slider like SimpleOption entries do
            slider.setX(x + entryWidth / 2 - 75);
            slider.setY(y);
            slider.setWidth(150);
            slider.render(context, mouseX, mouseY, tickDelta);
        }

        @Override
        public List<? extends Element> children() {
            return ImmutableList.of(slider);
        }

        @Override
        public List<? extends Selectable> selectableChildren() {
            return ImmutableList.of(slider);
        }
    }
}
