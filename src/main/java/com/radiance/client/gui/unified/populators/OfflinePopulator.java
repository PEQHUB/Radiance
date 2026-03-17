package com.radiance.client.gui.unified.populators;

import static net.minecraft.client.option.GameOptions.getGenericValueText;

import com.mojang.serialization.Codec;
import com.radiance.client.gui.KeyBindButton;
import com.radiance.client.gui.ResettableSliderWidget;
import com.radiance.client.gui.unified.*;
import com.radiance.client.gui.unified.rows.ButtonRow;
import com.radiance.client.gui.unified.rows.KeyBindRow;
import com.radiance.client.input.KeyInputHandler;
import com.radiance.client.option.Options;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.widget.ButtonWidget;
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

        // ── Camera ──
        SettingsSection camera = panel.addSection("Camera");

        // Sensor size preset (cycle toggle)
        SimpleOption<Integer> sensorPresetOpt = new SimpleOption<>(
            "Sensor Size",
            SimpleOption.emptyTooltip(),
            (optionText, value) -> getGenericValueText(optionText,
                Text.literal(Options.SENSOR_PRESET_NAMES[Math.min(value, Options.SENSOR_PRESET_NAMES.length - 1)])),
            new SimpleOption.ValidatingIntSliderCallbacks(0, Options.SENSOR_PRESET_NAMES.length - 1),
            Codec.intRange(0, Options.SENSOR_PRESET_NAMES.length - 1),
            Options.sensorPreset,
            value -> {
                Options.applySensorPreset(value);
                Options.syncApertureToNative();
                if (Options.offlineState == 2) Options.nativeResetAccumulation();
                screen.refreshContent();
            });
        camera.addToggle(sensorPresetOpt.createWidget(MinecraftClient.getInstance().options));

        // Focal length (14-200mm)
        ResettableSliderWidget focalSlider = new ResettableSliderWidget(
            0, 0, 150, 20,
            14, 200, Options.focalLengthMM, 50,
            v -> getGenericValueText(
                Text.literal("Focal Length"),
                Text.literal(v + " mm")),
            v -> {
                Options.focalLengthMM = v;
                Options.syncApertureToNative();
                if (Options.offlineState == 2) Options.nativeResetAccumulation();
            });
        camera.addSlider(focalSlider);

        // F-stop (1.4 - 22.0, stored as int 14-220 → display as f/x.x)
        int fStopInt = Math.round(Options.fStop * 10.0f);
        ResettableSliderWidget fStopSlider = new ResettableSliderWidget(
            0, 0, 150, 20,
            14, 220, fStopInt, 56,
            v -> getGenericValueText(
                Text.literal("F-Stop"),
                Text.literal("f/" + String.format("%.1f", v / 10.0))),
            v -> {
                Options.fStop = v / 10.0f;
                Options.syncApertureToNative();
                if (Options.offlineState == 2) Options.nativeResetAccumulation();
            });
        camera.addSlider(fStopSlider);

        // ── Focus ──
        SettingsSection focus = panel.addSection("Focus");

        // Focus mode cycle toggle (MF → AF-S → AF-C)
        String currentFocusMode = Options.FOCUS_MODE_NAMES[Math.min(Options.focusMode, 2)];
        focus.addButton(ButtonWidget.builder(
            Text.literal("Focus Mode: " + currentFocusMode), button -> {
                Options.focusMode = (Options.focusMode + 1) % 3;
                button.setMessage(Text.literal("Focus Mode: "
                    + Options.FOCUS_MODE_NAMES[Math.min(Options.focusMode, 2)]));
                if (Options.focusMode == 1) {
                    // AF-S: close menu to pick in world
                    MinecraftClient.getInstance().setScreen(null);
                }
            }).size(150, 20).build());

        // Focus distance slider (always visible)
        ResettableSliderWidget focusSlider = new ResettableSliderWidget(
            0, 0, 150, 20,
            1, 256, Options.offlineFocalDistance, 10,
            v -> getGenericValueText(
                Text.literal("Focus Distance"),
                Text.literal(v + " blocks")),
            v -> {
                Options.offlineFocalDistance = v;
                Options.nativeSetOfflineFocalDistance(v, true);
                if (Options.offlineState == 2) Options.nativeResetAccumulation();
            });
        focus.addSlider(focusSlider);

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
                if (Options.offlineState == 2) Options.nativeResetAccumulation();
            });
        accum.addSlider(bouncesSlider);

        // ── Freecam ──
        SettingsSection freecam = panel.addSection("Freecam");

        SimpleOption<Boolean> freecamToggle = SimpleOption.ofBoolean(
            "Freecam Mode",
            Options.freecamEnabled,
            value -> {
                Options.freecamEnabled = value;
            });
        freecam.addToggle(freecamToggle.createWidget(MinecraftClient.getInstance().options));

        SimpleOption<Boolean> showPlayerToggle = SimpleOption.ofBoolean(
            "Show Player Model",
            Options.freecamShowPlayer,
            value -> {
                Options.freecamShowPlayer = value;
            });
        freecam.addToggle(showPlayerToggle.createWidget(MinecraftClient.getInstance().options));

        // Movement speed (0.1-10.0, stored as int 1-100 → display as float)
        int speedInt = Math.round(Options.freecamSpeed * 10.0f);
        ResettableSliderWidget speedSlider = new ResettableSliderWidget(
            0, 0, 150, 20,
            1, 100, speedInt, 10,
            v -> getGenericValueText(
                Text.literal("Movement Speed"),
                Text.literal(String.format("%.1f×", v / 10.0))),
            v -> {
                Options.freecamSpeed = v / 10.0f;
            });
        freecam.addSlider(speedSlider);

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
