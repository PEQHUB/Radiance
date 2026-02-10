package com.radiance.client.gui;

import static net.minecraft.client.option.GameOptions.getGenericValueText;

import com.google.common.collect.ImmutableList;
import com.mojang.serialization.Codec;
import com.radiance.client.option.Options;
import com.radiance.client.option.TonemappingMode;
import com.radiance.client.util.CategoryVideoOptionEntry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.option.GameOptionsScreen;
import net.minecraft.client.option.SimpleOption;
import net.minecraft.text.Text;

public class RadianceSettingsScreen extends GameOptionsScreen {

    public RadianceSettingsScreen(Screen parent) {
        super(parent, MinecraftClient.getInstance().options, Text.translatable("radiance.settings.title"));
    }

    @Override
    protected void addOptions() {

        // === Tonemapping ===
        this.body.addEntry(
            new CategoryVideoOptionEntry(Text.translatable(Options.CATEGORY_TONEMAPPING), body));

        String[] tonemapModes = {"PBR Neutral", "Reinhard Extended", "ACES", "AgX", "Lottes", "Frostbite", "Uncharted 2"};
        SimpleOption<Integer> tonemapMode = new SimpleOption<>(
            Options.TONEMAP_MODE_KEY,
            SimpleOption.emptyTooltip(),
            (optionText, value) -> getGenericValueText(optionText,
                Text.translatable(TonemappingMode.byOrdinal(value).getTranslationKey())),
            new SimpleOption.ValidatingIntSliderCallbacks(0, tonemapModes.length - 1),
            Codec.intRange(0, tonemapModes.length - 1),
            Options.tonemappingMode,
            value -> Options.setTonemappingMode(value, true));
        this.body.addSingleOptionEntry(tonemapMode);

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
}
