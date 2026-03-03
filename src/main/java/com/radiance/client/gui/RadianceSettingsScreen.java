package com.radiance.client.gui;

import static net.minecraft.client.option.GameOptions.getGenericValueText;

import com.google.common.collect.ImmutableList;
import com.mojang.serialization.Codec;
import com.radiance.client.RadianceClient;
import com.radiance.client.option.Options;
import com.radiance.client.util.CategoryVideoOptionEntry;
import java.util.List;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.Selectable;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.option.GameOptionsScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.OptionListWidget;
import net.minecraft.client.option.SimpleOption;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public class RadianceSettingsScreen extends GameOptionsScreen {

    private final Screen parentScreen;

    public RadianceSettingsScreen(Screen parent) {
        super(parent, MinecraftClient.getInstance().options, Text.translatable("radiance.settings.title"));
        this.parentScreen = parent;
    }

    @Override
    protected void init() {
        super.init();

        // Shift the body list down to make room for the hint lines below the title
        this.body.setY(this.body.getY() + 22);
        this.body.setHeight(this.body.getHeight() - 22);

        // "Reset to Defaults" button — top-right corner
        int btnW = 110;
        int btnH = 18;
        int btnX = this.width - btnW - 6;
        int btnY = 6;
        this.addDrawableChild(ButtonWidget.builder(
            Text.translatable("radiance.settings.reset_defaults"),
            btn -> {
                Options.resetAllToDefaults();
                MinecraftClient.getInstance().setScreen(new RadianceSettingsScreen(parentScreen));
            })
            .dimensions(btnX, btnY, btnW, btnH)
            .build());

        // "Welcome Message: On/Off" toggle button — top-left corner
        this.addDrawableChild(ButtonWidget.builder(
            Text.translatable(Options.showWelcomeMessage
                ? "radiance.settings.welcome_message.on"
                : "radiance.settings.welcome_message.off"),
            btn -> {
                Options.showWelcomeMessage = !Options.showWelcomeMessage;
                Options.overwriteConfig();
                btn.setMessage(Text.translatable(Options.showWelcomeMessage
                    ? "radiance.settings.welcome_message.on"
                    : "radiance.settings.welcome_message.off"));
            })
            .dimensions(6, btnY, btnW, btnH)
            .build());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);

        // Hint lines below the title, above the body list
        int hintY = 26;
        int centerX = this.width / 2;
        context.drawCenteredTextWithShadow(
            this.textRenderer,
            Text.literal(Formatting.GRAY + "Ctrl+Click a slider to type a value  \u2502  Shift+Click to reset to default"),
            centerX, hintY, 0xFFFFFF);
    }

    @Override
    protected void addOptions() {

        // === Camera Controls ===
        this.body.addEntry(
            new CategoryVideoOptionEntry(Text.translatable(Options.CATEGORY_CAMERA_CONTROLS), body));

        SimpleOption<Boolean> cameraControls = new SimpleOption<>(
            "options.video.camera_controls",
            SimpleOption.emptyTooltip(),
            (optionText, value) -> optionText,
            new PotentialValuesBasedCallbacksNoValue<>(
                ImmutableList.of(Boolean.TRUE, Boolean.FALSE), Codec.BOOL),
            false,
            value -> MinecraftClient.getInstance().setScreen(new CameraControlsScreen(this)));
        this.body.addSingleOptionEntry(cameraControls);

        // === Tonemapping ===
        this.body.addEntry(
            new CategoryVideoOptionEntry(Text.translatable(Options.CATEGORY_TONEMAPPING), body));

        // Tone mapper mode selection is intentionally hidden.
        // SDR output is driven by the HDR pipeline tonemap for parity.

        SimpleOption<Integer> sdrTransferFn = new SimpleOption<>(
            Options.SDR_TRANSFER_FUNCTION_KEY,
            SimpleOption.emptyTooltip(),
            (optionText, value) -> getGenericValueText(optionText,
                Text.translatable(value == Options.SDR_TRANSFER_FUNCTION_SRGB
                    ? Options.SDR_TRANSFER_FUNCTION_SRGB_KEY
                    : Options.SDR_TRANSFER_FUNCTION_GAMMA_22_KEY)),
            new SimpleOption.ValidatingIntSliderCallbacks(0, 1),
            Codec.intRange(0, 1),
            Options.sdrTransferFunction,
            value -> Options.setSdrTransferFunction(value, true));
        this.body.addSingleOptionEntry(sdrTransferFn);

        // Saturation: 1.0 to 2.0 (stored as percent 100-200) - HDR-only
        ResettableSliderWidget satSlider = new ResettableSliderWidget(
            0, 0, 150, 20,
            100, 200, Options.saturationPercent, Options.SATURATION_DEFAULT_PERCENT,
            v -> getGenericValueText(
                Text.translatable(Options.SATURATION_KEY),
                Text.literal(String.format("%.2f", v / 100.0))),
            v -> Options.setSaturation(v, true));
        this.body.addEntry(new SliderEntry(satSlider, body));

        // White Point (Lwhite) slider is intentionally hidden.

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

        // === Environment ===
        this.body.addEntry(
            new CategoryVideoOptionEntry(Text.translatable(Options.CATEGORY_ENVIRONMENT), body));

        SimpleOption<Boolean> environmentSettings = new SimpleOption<>(
            Options.ENVIRONMENT_SETTINGS_KEY,
            SimpleOption.emptyTooltip(),
            (optionText, value) -> optionText,
            new PotentialValuesBasedCallbacksNoValue<>(
                ImmutableList.of(Boolean.TRUE, Boolean.FALSE), Codec.BOOL),
            false,
            value -> MinecraftClient.getInstance().setScreen(new EnvironmentalSettingsScreen(this)));
        this.body.addSingleOptionEntry(environmentSettings);

        // === HDR10 Output ===
        if (Options.isHdrSupported()) {
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
        }

        if (Options.isHdrSupported() && Options.hdrEnabled) {
            // Peak Brightness: 400–10000 nits (step 10)
            ResettableSliderWidget peakNitsSlider = new ResettableSliderWidget(
                0, 0, 150, 20,
                40, 1000, Options.hdrPeakNits / 10, 100,
                v -> getGenericValueText(
                    Text.translatable(Options.HDR_PEAK_NITS_KEY),
                    Text.literal(v * 10 + " nits")),
                v -> Options.setHdrPeakNits(v * 10, true));
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

        if (RadianceClient.dlssMissing) {
            SimpleOption<Boolean> dlssWarning = new SimpleOption<>(
                "options.video.dlss_missing_warning",
                SimpleOption.emptyTooltip(),
                (optionText, value) -> optionText,
                new PotentialValuesBasedCallbacksNoValue<>(
                    ImmutableList.of(Boolean.TRUE, Boolean.FALSE), Codec.BOOL),
                false,
                value -> MinecraftClient.getInstance().setScreen(new DlssMissingScreen(this)));
            this.body.addSingleOptionEntry(dlssWarning);
        }

        RadianceSettingsScreen self = this;
        SimpleOption<Boolean> dlssDEnabled = SimpleOption.ofBoolean(
            Options.DLSS_D_ENABLED_KEY,
            Options.dlssDEnabled,
            value -> {
                Options.setDlssDEnabled(value, true);
                // Refresh this screen so DLSS-only controls appear/disappear immediately.
                MinecraftClient.getInstance().setScreen(new RadianceSettingsScreen(self.parentScreen));
            });
        this.body.addSingleOptionEntry(dlssDEnabled);

        if (Options.dlssDEnabled) {
            // === DLSS Quality ===
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
        }

        SimpleOption<Boolean> outputScale2x = SimpleOption.ofBoolean(
            Options.OUTPUT_SCALE_2X_KEY, Options.outputScale2x,
            value -> Options.setOutputScale2x(value, true));
        this.body.addSingleOptionEntry(outputScale2x);

        // --- NVIDIA Reflex ---
        if (Options.isReflexSupported()) {
            SimpleOption<Boolean> reflexEnabled = SimpleOption.ofBoolean(
                Options.REFLEX_ENABLED_KEY,
                Options.reflexEnabled,
                value -> {
                    Options.setReflexEnabled(value, true);
                    MinecraftClient.getInstance().setScreen(new RadianceSettingsScreen(this.parentScreen));
                });
            this.body.addSingleOptionEntry(reflexEnabled);

            if (Options.reflexEnabled) {
                SimpleOption<Boolean> reflexBoost = SimpleOption.ofBoolean(
                    Options.REFLEX_BOOST_KEY,
                    Options.reflexBoost,
                    value -> Options.setReflexBoost(value, true));
                this.body.addSingleOptionEntry(reflexBoost);

                SimpleOption<Boolean> vrrMode = SimpleOption.ofBoolean(
                    Options.VRR_MODE_KEY,
                    Options.vrrMode,
                    value -> Options.setVrrMode(value, true));
                this.body.addSingleOptionEntry(vrrMode);
            }
        }

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

        SimpleOption<Boolean> ommEnabled = SimpleOption.ofBoolean(
            Options.OMM_ENABLED_KEY,
            Options.ommEnabled,
            value -> Options.setOMMEnabled(value, true));
        this.body.addSingleOptionEntry(ommEnabled);

        SimpleOption<Integer> ommBakerLevel = new SimpleOption<>(
            Options.OMM_BAKER_LEVEL_KEY,
            SimpleOption.emptyTooltip(),
            (optionText, value) -> getGenericValueText(optionText,
                Text.literal(Integer.toString(value))),
            new SimpleOption.ValidatingIntSliderCallbacks(1, 8),
            Codec.intRange(1, 8),
            Options.ommBakerLevel,
            value -> Options.setOMMBakerLevel(value, true));
        this.body.addSingleOptionEntry(ommBakerLevel);

        SimpleOption<Boolean> simplifiedIndirect = SimpleOption.ofBoolean(
            Options.SIMPLIFIED_INDIRECT_KEY,
            Options.simplifiedIndirect,
            value -> Options.setSimplifiedIndirect(value, true));
        this.body.addSingleOptionEntry(simplifiedIndirect);

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

    /** WidgetEntry that renders a single centered text label with no interactive widgets. */
    static class WarningLabelEntry extends OptionListWidget.WidgetEntry {
        private final Text label;

        WarningLabelEntry(Text label, OptionListWidget parent) {
            super(ImmutableList.of(), null);
            this.label = label;
        }

        @Override
        public void render(DrawContext context, int index, int y, int x, int entryWidth,
            int entryHeight, int mouseX, int mouseY, boolean hovered, float tickDelta) {
            net.minecraft.client.MinecraftClient mc = net.minecraft.client.MinecraftClient.getInstance();
            context.drawCenteredTextWithShadow(mc.textRenderer, label, x + entryWidth / 2, y + 6, 0xFFFFFF);
        }

        @Override
        public List<? extends Element> children() {
            return ImmutableList.of();
        }

        @Override
        public List<? extends Selectable> selectableChildren() {
            return ImmutableList.of();
        }
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
