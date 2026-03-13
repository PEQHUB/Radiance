package com.radiance.client.gui.unified.populators;

import com.radiance.client.gui.MaterialBrowserScreen;
import com.radiance.client.gui.MaterialsSettingsScreen;
import com.radiance.client.gui.unified.*;
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
        SettingsSection section = panel.addSection(Text.literal("Materials"));
        section.addLauncher("options.video.materials_settings",
            new MaterialsSettingsScreen(screen), screen);
        section.addLauncher("radiance.materials.browser",
            new MaterialBrowserScreen(screen), screen);
    }
}
