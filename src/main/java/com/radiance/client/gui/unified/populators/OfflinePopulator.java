package com.radiance.client.gui.unified.populators;

import static net.minecraft.client.option.GameOptions.getGenericValueText;

import com.radiance.client.gui.KeyBindButton;
import com.radiance.client.gui.ResettableSliderWidget;
import com.radiance.client.gui.unified.*;
import com.radiance.client.gui.unified.rows.KeyBindRow;
import com.radiance.client.input.KeyInputHandler;
import com.radiance.client.option.Options;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.SimpleOption;
import net.minecraft.text.Text;

public class OfflinePopulator implements ContentPopulator {
    @Override
    public void populate(ContentPanelWidget panel, RadianceUnifiedScreen screen) {

        // ── Ground Truth Preset ──
        SettingsSection preset = panel.addSection("Ground Truth");

        SimpleOption<Boolean> groundTruth = SimpleOption.ofBoolean(
            "Ground Truth Preset",
            Options.offlineGroundTruth,
            value -> {
                Options.offlineGroundTruth = value;
                if (value) {
                    KeyInputHandler.applyGroundTruthPreset();
                } else {
                    KeyInputHandler.restoreGroundTruthPreset();
                }
                Options.nativeSetOfflineGroundTruth(value, false);
                if (Options.offlineState == 2) {
                    Options.nativeResetAccumulation();
                }
                screen.refreshContent();
            });
        preset.addRow(new KeyBindRow(
            new KeyBindButton(0, 0, KeyInputHandler.offlineGroundTruthKey),
            groundTruth.createWidget(MinecraftClient.getInstance().options)));

        // ── Shader Quality ──
        SettingsSection quality = panel.addSection("Shader Quality");

        SimpleOption<Boolean> beerLaw = SimpleOption.ofBoolean(
            "Beer's Law Shadows",
            Options.beerLawShadows,
            value -> {
                Options.beerLawShadows = value;
                Options.nativeSetBeerLawShadows(value, true);
                if (Options.offlineState == 2) Options.nativeResetAccumulation();
            });
        quality.addToggle(beerLaw.createWidget(MinecraftClient.getInstance().options));

        SimpleOption<Boolean> noClamp = SimpleOption.ofBoolean(
            "Disable Emission Clamp",
            Options.noEmissionClamp,
            value -> {
                Options.noEmissionClamp = value;
                Options.nativeSetNoEmissionClamp(value, true);
                if (Options.offlineState == 2) Options.nativeResetAccumulation();
            });
        quality.addToggle(noClamp.createWidget(MinecraftClient.getInstance().options));

        SimpleOption<Boolean> physSun = SimpleOption.ofBoolean(
            "Physical Sun Disk",
            Options.physicalSunDisk,
            value -> {
                Options.physicalSunDisk = value;
                Options.nativeSetPhysicalSunDisk(value, true);
                if (Options.offlineState == 2) Options.nativeResetAccumulation();
            });
        quality.addToggle(physSun.createWidget(MinecraftClient.getInstance().options));

        SimpleOption<Boolean> noHand = SimpleOption.ofBoolean(
            "Disable Hand Ambient",
            Options.noHandAmbient,
            value -> {
                Options.noHandAmbient = value;
                Options.nativeSetNoHandAmbient(value, true);
                if (Options.offlineState == 2) Options.nativeResetAccumulation();
            });
        quality.addToggle(noHand.createWidget(MinecraftClient.getInstance().options));

        // ── Variance Reduction ──
        SettingsSection variance = panel.addSection("Variance Reduction");

        SimpleOption<Boolean> disableRR = SimpleOption.ofBoolean(
            "Disable Russian Roulette",
            Options.offlineDisableRR,
            value -> {
                Options.offlineDisableRR = value;
                Options.nativeSetOfflineDisableRR(value, true);
                if (Options.offlineState == 2) Options.nativeResetAccumulation();
            });
        variance.addToggle(disableRR.createWidget(MinecraftClient.getInstance().options));

        SimpleOption<Boolean> disableClamp = SimpleOption.ofBoolean(
            "Disable Throughput Clamp",
            Options.offlineDisableClamp,
            value -> {
                Options.offlineDisableClamp = value;
                Options.nativeSetOfflineDisableClamp(value, true);
                if (Options.offlineState == 2) Options.nativeResetAccumulation();
            });
        variance.addToggle(disableClamp.createWidget(MinecraftClient.getInstance().options));

        // ── Accumulation ──
        SettingsSection accum = panel.addSection("Accumulation");

        ResettableSliderWidget bouncesSlider = new ResettableSliderWidget(
            0, 0, 150, 20,
            1, 128, Options.offlineBounces, 16,
            v -> getGenericValueText(
                Text.literal("Ray Bounces"),
                Text.literal(String.valueOf(v))),
            v -> {
                Options.offlineBounces = v;
                Options.nativeSetOfflineBounces(v, true);
                Options.nativeResetAccumulation();
            });
        accum.addSlider(bouncesSlider);

        ResettableSliderWidget apertureSlider = new ResettableSliderWidget(
            0, 0, 150, 20,
            0, 100, Options.offlineAperturePercent, 0,
            v -> getGenericValueText(
                Text.literal("Aperture"),
                Text.literal(String.format("%.3f", v / 1000.0))),
            v -> {
                Options.offlineAperturePercent = v;
                Options.nativeSetOfflineAperture(v / 1000.0f, true);
                Options.nativeResetAccumulation();
            });
        accum.addSlider(apertureSlider);

        ResettableSliderWidget focalSlider = new ResettableSliderWidget(
            0, 0, 150, 20,
            1, 256, Options.offlineFocalDistance, 10,
            v -> getGenericValueText(
                Text.literal("Focal Distance"),
                Text.literal(v + " blocks")),
            v -> {
                Options.offlineFocalDistance = v;
                Options.nativeSetOfflineFocalDistance(v, true);
                Options.nativeResetAccumulation();
            });
        accum.addSlider(focalSlider);

        // ── Controls ──
        SettingsSection controls = panel.addSection("Controls");

        controls.addRow(new KeyBindRow(
            new KeyBindButton(0, 0, KeyInputHandler.offlineModeKey),
            "Offline Mode"));

        controls.addRow(new KeyBindRow(
            new KeyBindButton(0, 0, KeyInputHandler.lockCameraKey),
            "Lock Camera"));

        String[] modeNames = {"Raw", "DLSS + Welford", "DLSS-RR Temporal"};
        String currentMode = modeNames[Math.min(Options.offlineDenoised, 2)];
        SimpleOption<Boolean> denoisedCycle = SimpleOption.ofBoolean(
            "Denoised: " + currentMode,
            Options.offlineDenoised > 0,
            value -> {
                Options.offlineDenoised = (Options.offlineDenoised + 1) % 3;
                Options.nativeSetOfflineDenoised(Options.offlineDenoised, true);
                Options.nativeResetAccumulation();
                screen.refreshContent();
            });
        controls.addRow(new KeyBindRow(
            new KeyBindButton(0, 0, KeyInputHandler.offlineDenoisedKey),
            denoisedCycle.createWidget(MinecraftClient.getInstance().options)));
    }
}
