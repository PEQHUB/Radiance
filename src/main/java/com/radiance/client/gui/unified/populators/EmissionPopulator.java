package com.radiance.client.gui.unified.populators;

import com.radiance.client.gui.EmissiveBlockSettingsScreen;
import com.radiance.client.gui.unified.*;
import com.radiance.client.gui.unified.rows.LauncherRow;
import net.minecraft.text.Text;

/**
 * Emission settings populator.
 * The emission screen uses complex FiveColumnEmissionEntry widgets that are tightly
 * coupled to the old OptionListWidget system. For now, launch the old screen.
 * TODO: Migrate to inline emission rows in a future pass.
 */
public class EmissionPopulator implements ContentPopulator {
    @Override
    public void populate(ContentPanelWidget panel, RadianceUnifiedScreen screen) {
        SettingsSection section = panel.addSection(Text.literal("Emission Settings"));
        section.addLauncher("options.video.emission_settings",
            new EmissiveBlockSettingsScreen(screen), screen);
    }
}
