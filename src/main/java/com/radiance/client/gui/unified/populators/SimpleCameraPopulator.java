package com.radiance.client.gui.unified.populators;

import static net.minecraft.client.option.GameOptions.getGenericValueText;

import com.radiance.client.gui.ResettableSliderWidget;
import com.radiance.client.gui.unified.*;
import com.radiance.client.option.Options;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.SimpleOption;
import net.minecraft.text.Text;

public class SimpleCameraPopulator implements ContentPopulator {
    @Override
    public void populate(ContentPanelWidget panel, RadianceUnifiedScreen screen) {
        var gameOptions = MinecraftClient.getInstance().options;

        SettingsSection section = panel.addSection("options.video.category.camera");

        // FPV toggle
        SimpleOption<Boolean> fpvEnabled = SimpleOption.ofBoolean(
            Options.FPV_ENABLED_KEY, Options.fpvEnabled,
            value -> Options.setFpvEnabled(value, true));
        section.addToggle(fpvEnabled.createWidget(gameOptions))
              .tooltip("Shows your player body in first-person view.");

        // DOF strength — only visible in offline mode (DOF requires offline rendering)
        if (Options.offlineState != 0) {
            section.addSlider(new ResettableSliderWidget(0, 0, 150, 20,
                0, 200, Options.dofStrengthPercent, 100,
                v -> getGenericValueText(Text.literal("DOF Strength"), Text.literal(v + "%")),
                v -> Options.setDofStrength(v, true)))
                  .tooltip("Depth of field blur intensity for offline/photo mode. Higher values increase blur strength.");
        }
    }

    @Override
    public java.util.List<UnifiedSearchOverlay.SearchEntry> getSearchEntries(String nodeId, String category) {
        return java.util.List.of(
            new UnifiedSearchOverlay.SearchEntry("First-Person View", category, nodeId, false),
            new UnifiedSearchOverlay.SearchEntry("DOF Strength", category, nodeId, false)
        );
    }
}
