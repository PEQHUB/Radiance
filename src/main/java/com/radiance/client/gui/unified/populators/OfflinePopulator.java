package com.radiance.client.gui.unified.populators;

import static net.minecraft.client.option.GameOptions.getGenericValueText;

import com.radiance.client.gui.KeyBindButton;
import com.radiance.client.gui.ResettableSliderWidget;
import com.radiance.client.gui.SelectionDropdownWidget;
import com.radiance.client.gui.unified.ContentPanelWidget;
import com.radiance.client.gui.unified.ContentPopulator;
import com.radiance.client.gui.unified.RadianceUnifiedScreen;
import com.radiance.client.gui.unified.SettingsSection;
import com.radiance.client.gui.unified.UnifiedSearchOverlay;
import com.radiance.client.gui.unified.rows.KeyBindRow;
import com.radiance.client.input.KeyInputHandler;
import com.radiance.client.option.Options;
import java.util.List;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.SimpleOption;
import net.minecraft.text.Text;

public class OfflinePopulator implements ContentPopulator {
    @Override
    public void populate(ContentPanelWidget panel, RadianceUnifiedScreen screen) {
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
            .tooltip("Raw Fast = RR on, no denoise. Raw Accurate = RR off. Denoised = epoch-based DLSS-RR convergence.");

        SettingsSection accum = panel.addSection("Accumulation");
        accum.addSlider(new ResettableSliderWidget(
            0, 0, 150, 20,
            1, 128, Options.offlineBounces, 16,
            v -> getGenericValueText(Text.literal("Ray Bounces"), Text.literal(String.valueOf(v))),
            v -> {
                Options.offlineBounces = v;
                Options.nativeSetOfflineBounces(v, true);
                if (Options.offlineState == 2) Options.nativeResetAccumulation();
            }))
            .tooltip("Maximum ray bounces during accumulation. Higher = more accurate global illumination.");

        if (Options.offlineDenoised == 2) {
            accum.addSlider(new ResettableSliderWidget(
                0, 0, 150, 20,
                4, 64, Options.dlssEpochLength, 16,
                v -> getGenericValueText(Text.literal("Epoch Length"), Text.literal(v + " frames")),
                v -> {
                    Options.dlssEpochLength = v;
                    Options.nativeSetDlssEpochLength(v, true);
                    if (Options.offlineState == 2) Options.nativeResetAccumulation();
                }))
                .tooltip("Frames per epoch before Welford statistics snapshot. Longer = smoother but slower convergence.");
        }

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

        if (Options.offlineDenoised != 1) {
            SimpleOption<Boolean> disableRR = SimpleOption.ofBoolean(
                "Disable Russian Roulette",
                Options.offlineDisableRR,
                value -> {
                    Options.offlineDisableRR = value;
                    Options.nativeSetOfflineDisableRR(value, true);
                    if (Options.offlineState == 2) Options.nativeResetAccumulation();
                });
            quality.addToggle(disableRR.createWidget(MinecraftClient.getInstance().options));
        }

        SimpleOption<Boolean> disableClamp = SimpleOption.ofBoolean(
            "Disable Throughput Clamp",
            Options.offlineDisableClamp,
            value -> {
                Options.offlineDisableClamp = value;
                Options.nativeSetOfflineDisableClamp(value, true);
                if (Options.offlineState == 2) Options.nativeResetAccumulation();
            });
        quality.addToggle(disableClamp.createWidget(MinecraftClient.getInstance().options));

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
    public List<UnifiedSearchOverlay.SearchEntry> getSearchEntries(String nodeId, String category) {
        return List.of(
            new UnifiedSearchOverlay.SearchEntry("Render Preset", category, nodeId, false),
            new UnifiedSearchOverlay.SearchEntry("Ray Bounces (Offline)", category, nodeId, true),
            new UnifiedSearchOverlay.SearchEntry("Epoch Length", category, nodeId, false),
            new UnifiedSearchOverlay.SearchEntry("Beer's Law Shadows", category, nodeId, true),
            new UnifiedSearchOverlay.SearchEntry("Physical Sun Disk", category, nodeId, true),
            new UnifiedSearchOverlay.SearchEntry("Ground Truth", category, nodeId, false)
        );
    }
}
