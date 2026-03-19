package com.radiance.client.gui.unified.populators;

import static net.minecraft.client.option.GameOptions.getGenericValueText;

import com.radiance.client.gui.KeyBindButton;
import com.radiance.client.gui.ResettableSliderWidget;
import com.radiance.client.gui.SelectionDropdownWidget;
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

        // ── Render Preset ──
        SettingsSection preset = panel.addSection("Render Preset");

        String[] presetNames = {"Raw Fast", "Raw Accurate", "Denoised"};
        SelectionDropdownWidget presetDropdown = new SelectionDropdownWidget(
            0, 0, 150, 20,
            "Render Preset", presetNames, Math.min(Options.offlineDenoised, 2),
            value -> {
                Options.offlineDenoised = value;
                Options.nativeSetOfflineDenoised(value, true);
                Options.nativeResetAccumulation();
                screen.refreshContent();
            });
        preset.addRow(new KeyBindRow(
            new KeyBindButton(0, 0, KeyInputHandler.offlineDenoisedKey),
            presetDropdown))
              .tooltip("Raw Fast = RR on, no denoise. Raw Accurate = RR off, no denoise. Denoised = epoch-based DLSS-RR convergence.");

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
        accum.addSlider(bouncesSlider)
              .tooltip("Maximum ray bounces during accumulation. Higher = more accurate global illumination.");

        // Epoch length slider (only visible when Denoised preset is selected)
        if (Options.offlineDenoised == 2) {
            ResettableSliderWidget epochSlider = new ResettableSliderWidget(
                0, 0, 150, 20,
                4, 64, Options.dlssEpochLength, 16,
                v -> getGenericValueText(
                    Text.literal("Epoch Length"),
                    Text.literal(v + " frames")),
                v -> {
                    Options.dlssEpochLength = v;
                    Options.nativeSetDlssEpochLength(v, true);
                    if (Options.offlineState == 2) Options.nativeResetAccumulation();
                });
            accum.addSlider(epochSlider)
                  .tooltip("Frames per epoch before Welford statistics snapshot. Longer = smoother but slower convergence.");
        }

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
        quality.addToggle(beerLaw.createWidget(MinecraftClient.getInstance().options))
              .tooltip("Accurate volumetric shadow attenuation through translucent materials.");

        SimpleOption<Boolean> physSun = SimpleOption.ofBoolean(
            "Physical Sun Disk",
            Options.physicalSunDisk,
            value -> {
                Options.physicalSunDisk = value;
                Options.nativeSetPhysicalSunDisk(value, true);
                if (Options.offlineState == 2) Options.nativeResetAccumulation();
            });
        quality.addToggle(physSun.createWidget(MinecraftClient.getInstance().options))
              .tooltip("Renders the sun as a physical disk instead of a directional light. Affects soft shadows.");

        SimpleOption<Boolean> noClamp = SimpleOption.ofBoolean(
            "Disable Emission Clamp",
            Options.noEmissionClamp,
            value -> {
                Options.noEmissionClamp = value;
                Options.nativeSetNoEmissionClamp(value, true);
                if (Options.offlineState == 2) Options.nativeResetAccumulation();
            });
        quality.addToggle(noClamp.createWidget(MinecraftClient.getInstance().options));

        SimpleOption<Boolean> noHand = SimpleOption.ofBoolean(
            "Disable Hand Ambient",
            Options.noHandAmbient,
            value -> {
                Options.noHandAmbient = value;
                Options.nativeSetNoHandAmbient(value, true);
                if (Options.offlineState == 2) Options.nativeResetAccumulation();
            });
        quality.addToggle(noHand.createWidget(MinecraftClient.getInstance().options));

        SimpleOption<Boolean> disableRR = SimpleOption.ofBoolean(
            "Disable Russian Roulette",
            Options.offlineDisableRR || Options.offlineDenoised == 1,
            value -> {
                // Raw Accurate forces RR off — don't allow toggle
                if (Options.offlineDenoised == 1) return;
                Options.offlineDisableRR = value;
                Options.nativeSetOfflineDisableRR(value, true);
                if (Options.offlineState == 2) Options.nativeResetAccumulation();
            });
        quality.addToggle(disableRR.createWidget(MinecraftClient.getInstance().options));

        SimpleOption<Boolean> disableClamp = SimpleOption.ofBoolean(
            "Disable Throughput Clamp",
            Options.offlineDisableClamp,
            value -> {
                Options.offlineDisableClamp = value;
                Options.nativeSetOfflineDisableClamp(value, true);
                if (Options.offlineState == 2) Options.nativeResetAccumulation();
            });
        quality.addToggle(disableClamp.createWidget(MinecraftClient.getInstance().options));

        // ── Camera ──
        SettingsSection camera = panel.addSection("Camera");

        // Sensor size preset (dropdown)
        SelectionDropdownWidget sensorDropdown = new SelectionDropdownWidget(
            0, 0, 150, 20,
            "Sensor Size", Options.SENSOR_PRESET_NAMES, Options.sensorPreset,
            value -> {
                Options.applySensorPreset(value);
                Options.syncApertureToNative();
                if (Options.offlineState == 2) Options.nativeResetAccumulation();
                screen.refreshContent();
            });
        camera.addTwoWidgets(sensorDropdown, null);

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
        camera.addSlider(focalSlider)
              .tooltip("Lens focal length in mm. Shorter = wider FOV. Longer = telephoto compression.");

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
        camera.addSlider(fStopSlider)
              .tooltip("Aperture size. Lower f-stop = shallower depth of field and more bokeh.");

        // ── Focus ──
        SettingsSection focus = panel.addSection("Focus");

        // Focus mode dropdown (MF / AF-S / AF-C)
        SelectionDropdownWidget focusModeDropdown = new SelectionDropdownWidget(
            0, 0, 150, 20,
            "Focus Mode", Options.FOCUS_MODE_NAMES, Math.min(Options.focusMode, 2),
            value -> {
                Options.focusMode = value;
                if (Options.focusMode == 1) {
                    // AF-S: close menu to pick in world
                    MinecraftClient.getInstance().setScreen(null);
                }
            });
        focus.addTwoWidgets(focusModeDropdown, null)
              .tooltip("MF = manual focus. AF-S = single autofocus. AF-C = continuous autofocus tracking.");

        // Focus distance slider (1-256 blocks, scroll wheel gives sub-block precision)
        ResettableSliderWidget focusSlider = new ResettableSliderWidget(
            0, 0, 150, 20,
            1, 256, Math.round(Options.offlineFocalDistance), 10,
            v -> getGenericValueText(
                Text.literal("Focus Distance"),
                Text.literal(v + " blocks")),
            v -> {
                Options.offlineFocalDistance = (float) v;
                Options.nativeSetOfflineFocalDistance((float) v, true);
                if (Options.offlineState == 2) Options.nativeResetAccumulation();
            });
        focus.addSlider(focusSlider);

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
            "Show Player",
            Options.freecamShowPlayer,
            value -> {
                Options.freecamShowPlayer = value;
            });
        freecam.addToggle(showPlayerToggle.createWidget(MinecraftClient.getInstance().options));

        // Movement speed (0.1-50.0, stored as int 1-500 → display as float)
        int speedInt = Math.round(Options.freecamSpeed * 10.0f);
        ResettableSliderWidget speedSlider = new ResettableSliderWidget(
            0, 0, 150, 20,
            1, 500, speedInt, 100,
            v -> getGenericValueText(
                Text.literal("Movement Speed"),
                Text.literal(String.format("%.1f×", v / 10.0))),
            v -> {
                Options.freecamSpeed = v / 10.0f;
            });
        freecam.addSlider(speedSlider);

        // ── Ground Truth & Controls ──
        SettingsSection gtControls = panel.addSection("Ground Truth & Controls");

        SimpleOption<Boolean> groundTruth = SimpleOption.ofBoolean(
            "Ground Truth",
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
        gtControls.addRow(new KeyBindRow(
            new KeyBindButton(0, 0, KeyInputHandler.offlineGroundTruthKey),
            groundTruth.createWidget(MinecraftClient.getInstance().options)));

        gtControls.addRow(new KeyBindRow(
            new KeyBindButton(0, 0, KeyInputHandler.offlineModeKey),
            "Offline Mode"));

        gtControls.addRow(new KeyBindRow(
            new KeyBindButton(0, 0, KeyInputHandler.lockCameraKey),
            "Lock Camera"));
    }

    @Override
    public java.util.List<UnifiedSearchOverlay.SearchEntry> getSearchEntries(String nodeId, String category) {
        return java.util.List.of(
            new UnifiedSearchOverlay.SearchEntry("Render Preset", category, nodeId, false),
            new UnifiedSearchOverlay.SearchEntry("Ray Bounces (Offline)", category, nodeId, true),
            new UnifiedSearchOverlay.SearchEntry("Epoch Length", category, nodeId, false),
            new UnifiedSearchOverlay.SearchEntry("Beer's Law Shadows", category, nodeId, true),
            new UnifiedSearchOverlay.SearchEntry("Physical Sun Disk", category, nodeId, true),
            new UnifiedSearchOverlay.SearchEntry("Sensor Size Preset", category, nodeId, false),
            new UnifiedSearchOverlay.SearchEntry("Focal Length", category, nodeId, false),
            new UnifiedSearchOverlay.SearchEntry("F-Stop", category, nodeId, false),
            new UnifiedSearchOverlay.SearchEntry("Focus Mode", category, nodeId, false),
            new UnifiedSearchOverlay.SearchEntry("Focus Distance", category, nodeId, false),
            new UnifiedSearchOverlay.SearchEntry("Freecam Mode", category, nodeId, false),
            new UnifiedSearchOverlay.SearchEntry("Ground Truth", category, nodeId, false)
        );
    }
}
