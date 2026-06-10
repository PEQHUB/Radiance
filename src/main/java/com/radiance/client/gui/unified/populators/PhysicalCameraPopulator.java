package com.radiance.client.gui.unified.populators;

import static net.minecraft.client.option.GameOptions.getGenericValueText;

import com.radiance.client.gui.ResettableSliderWidget;
import com.radiance.client.gui.SelectionDropdownWidget;
import com.radiance.client.gui.unified.ContentPanelWidget;
import com.radiance.client.gui.unified.ContentPopulator;
import com.radiance.client.gui.unified.RadianceUnifiedScreen;
import com.radiance.client.gui.unified.SettingsSection;
import com.radiance.client.gui.unified.UnifiedSearchOverlay;
import com.radiance.client.option.Options;
import java.util.List;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

public class PhysicalCameraPopulator implements ContentPopulator {
    @Override
    public void populate(ContentPanelWidget panel, RadianceUnifiedScreen screen) {
        SettingsSection camera = panel.addSection("Physical Camera");

        SelectionDropdownWidget sensorDropdown = new SelectionDropdownWidget(
            0, 0, 150, 20,
            "Sensor Size", Options.SENSOR_PRESET_NAMES, Options.sensorPreset,
            value -> {
                Options.setSensorPreset(value, true);
                if (Options.offlineState == 2) Options.nativeResetAccumulation();
                screen.refreshContent();
            });
        camera.addTwoWidgets(sensorDropdown, null);
        camera.tooltip("Selects the sensor format used for physical camera and depth-of-field calculations.");

        camera.addSlider(new ResettableSliderWidget(
            0, 0, 150, 20,
            14, 200, Options.focalLengthMM, 50,
            v -> getGenericValueText(Text.literal("Focal Length"), Text.literal(v + " mm")),
            v -> {
                Options.setFocalLengthMM(v, true);
                if (Options.offlineState == 2) Options.nativeResetAccumulation();
            }))
            .tooltip("Lens focal length in mm. Shorter = wider FOV. Longer = telephoto compression.");

        int fStopInt = Math.round(Options.fStop * 10.0f);
        camera.addSlider(new ResettableSliderWidget(
            0, 0, 150, 20,
            14, 220, fStopInt, 56,
            v -> getGenericValueText(Text.literal("F-Stop"),
                Text.literal("f/" + String.format("%.1f", v / 10.0))),
            v -> {
                Options.setFStop(v / 10.0f, true);
                if (Options.offlineState == 2) Options.nativeResetAccumulation();
            }))
            .tooltip("Aperture size. Lower f-stop = shallower depth of field and more bokeh.");

        SettingsSection focus = panel.addSection("Focus");
        SelectionDropdownWidget focusModeDropdown = new SelectionDropdownWidget(
            0, 0, 150, 20,
            "Focus Mode", Options.FOCUS_MODE_NAMES, Math.min(Options.focusMode, 2),
            value -> {
                Options.setFocusMode(value, true);
                if (Options.focusMode == 1) {
                    MinecraftClient.getInstance().setScreen(null);
                }
                screen.refreshContent();
            });
        focus.addTwoWidgets(focusModeDropdown, null)
            .tooltip("MF = manual focus. AF-S = single autofocus. AF-C = continuous autofocus tracking.");

        if (Options.focusMode == 0) {
            focus.addSlider(new ResettableSliderWidget(
                0, 0, 150, 20,
                1, 256, Math.round(Options.offlineFocalDistance), 10,
                v -> getGenericValueText(Text.literal("Focus Distance"), Text.literal(v + " blocks")),
                v -> {
                    Options.setOfflineFocalDistance((float) v, true);
                    if (Options.offlineState == 2) Options.nativeResetAccumulation();
                }));
            focus.tooltip("Manual focus distance in blocks. Used when Focus Mode is MF.");
        }
    }

    @Override
    public List<UnifiedSearchOverlay.SearchEntry> getSearchEntries(String nodeId, String category) {
        return List.of(
            new UnifiedSearchOverlay.SearchEntry("Sensor Size Preset", category, nodeId, false),
            new UnifiedSearchOverlay.SearchEntry("Focal Length", category, nodeId, false),
            new UnifiedSearchOverlay.SearchEntry("F-Stop", category, nodeId, false),
            new UnifiedSearchOverlay.SearchEntry("Focus Mode", category, nodeId, false),
            new UnifiedSearchOverlay.SearchEntry("Focus Distance", category, nodeId, false)
        );
    }
}
