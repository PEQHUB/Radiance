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
            .tooltip("Raw Fast keeps RR enabled without final denoise. Raw Accurate disables RR. Denoised uses epoch-based DLSS-RR convergence.");

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
            .tooltip("Maximum ray bounces during offline accumulation. Higher values improve global illumination accuracy at higher cost.");

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
                .tooltip("Number of frames per denoised accumulation epoch before statistics are snapped. Longer is smoother but slower to converge.");
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
            .tooltip("Uses Beer\u2019s law attenuation for translucent or absorbing shadow paths.");

        SimpleOption<Boolean> physSun = SimpleOption.ofBoolean(
            "Physical Sun Disk",
            Options.physicalSunDisk,
            value -> {
                Options.physicalSunDisk = value;
                Options.nativeSetPhysicalSunDisk(value, true);
                if (Options.offlineState == 2) Options.nativeResetAccumulation();
            });
        quality.addToggle(physSun.createWidget(MinecraftClient.getInstance().options))
            .tooltip("Renders the sun as a disk instead of only a directional light. Affects soft shadows and sun appearance.");

        SimpleOption<Boolean> noClamp = SimpleOption.ofBoolean(
            "Disable Emission Clamp",
            Options.noEmissionClamp,
            value -> {
                Options.noEmissionClamp = value;
                Options.nativeSetNoEmissionClamp(value, true);
                if (Options.offlineState == 2) Options.nativeResetAccumulation();
            });
        quality.addToggle(noClamp.createWidget(MinecraftClient.getInstance().options));
        quality.tooltip("Removes the emission clamp during offline rendering. Preserves very bright emitters but can increase fireflies/noise.");

        SimpleOption<Boolean> noHand = SimpleOption.ofBoolean(
            "Disable Hand Ambient",
            Options.noHandAmbient,
            value -> {
                Options.noHandAmbient = value;
                Options.nativeSetNoHandAmbient(value, true);
                if (Options.offlineState == 2) Options.nativeResetAccumulation();
            });
        quality.addToggle(noHand.createWidget(MinecraftClient.getInstance().options));
        quality.tooltip("Removes the extra hand/first-person ambient contribution during offline accumulation.");

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
        quality.tooltip("Disables Russian roulette path termination. More stable for reference captures, but more expensive.");
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
        quality.tooltip("Disables throughput clamping. Preserves extreme light paths but can increase noise and fireflies.");

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
        gtControls.tooltip("Applies the high-accuracy offline preset for reference rendering and accumulation validation.");

        gtControls.addRow(new KeyBindRow(
            new KeyBindButton(0, 0, KeyInputHandler.offlineModeKey),
            "Offline Mode"));
        gtControls.tooltip("Toggles offline/free-camera rendering mode. Use this before locking the camera for accumulation.");

        gtControls.addRow(new KeyBindRow(
            new KeyBindButton(0, 0, KeyInputHandler.lockCameraKey),
            "Lock Camera"));
        gtControls.tooltip("Locks the camera and starts accumulation; press again to unlock and reset accumulation.");
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
