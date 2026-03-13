package com.radiance.client.gui.unified.populators;

import com.radiance.client.gui.RenderPipelineScreen;
import com.radiance.client.gui.unified.*;
import com.radiance.client.option.Options;
import net.minecraft.text.Text;

public class PipelinePopulator implements ContentPopulator {
    @Override
    public void populate(ContentPanelWidget panel, RadianceUnifiedScreen screen) {
        SettingsSection section = panel.addSection(Options.CATEGORY_PIPELINE);
        section.addLauncher(Options.PIPELINE_SETUP_KEY,
            new RenderPipelineScreen(screen), screen);
    }
}
