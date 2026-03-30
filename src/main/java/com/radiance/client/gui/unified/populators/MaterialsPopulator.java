package com.radiance.client.gui.unified.populators;

import com.radiance.client.gui.MaterialsSettingsScreen;
import com.radiance.client.gui.unified.*;
import com.radiance.client.option.Options;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.SimpleOption;
import net.minecraft.text.Text;

/**
 * Materials settings populator.
 * The materials editor is the most complex screen (block selector, sphere preview,
 * 13 sliders, presets, copy/paste, snapshot/restore). For now, launch old screens.
 * TODO: Migrate to inline materials editor in a future pass.
 */
public class MaterialsPopulator implements ContentPopulator {
    @Override
    public void populate(ContentPanelWidget panel, RadianceUnifiedScreen screen) {
        var gameOptions = MinecraftClient.getInstance().options;

        SettingsSection section = panel.addSection(Text.literal("Materials"));

        SimpleOption<Boolean> overridesEnabled = SimpleOption.ofBoolean(
            "options.video.materials.overridesEnabled", Options.materialOverridesEnabled,
            value -> Options.setMaterialOverridesEnabled(value, true));
        SimpleOption<Boolean> autoPBR = SimpleOption.ofBoolean(
            "options.video.materials.autoPBR", Options.autoPBREnabled,
            value -> Options.setAutoPBREnabled(value, true));
        section.addTwoWidgets(overridesEnabled.createWidget(gameOptions), autoPBR.createWidget(gameOptions));
        section.tooltip("Material Overrides applies per-block PBR properties. AutoPBR generates roughness/metalness from textures.");

        section.addLauncher("options.video.materials_settings",
            new MaterialsSettingsScreen(screen), screen);
    }

    @Override
    public java.util.List<UnifiedSearchOverlay.SearchEntry> getSearchEntries(String nodeId, String category) {
        return java.util.List.of(
            new UnifiedSearchOverlay.SearchEntry("Material Overrides", category, nodeId, false),
            new UnifiedSearchOverlay.SearchEntry("AutoPBR", category, nodeId, false),
            new UnifiedSearchOverlay.SearchEntry("Materials Editor", category, nodeId, false)
        );
    }
}
