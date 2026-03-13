package com.radiance.client.gui.unified.populators;

import static net.minecraft.client.option.GameOptions.getGenericValueText;

import com.radiance.client.gui.ResettableSliderWidget;
import com.radiance.client.gui.unified.*;
import com.radiance.client.gui.unified.rows.SliderRow;
import com.radiance.client.gui.unified.rows.ToggleRow;
import com.radiance.client.option.Options;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.SimpleOption;
import net.minecraft.text.Text;

public class PostProcessingPopulator implements ContentPopulator {
    @Override
    public void populate(ContentPanelWidget panel, RadianceUnifiedScreen screen) {
        SettingsSection section = panel.addSection("options.video.category.post_processing");

        SimpleOption<Boolean> casEnabled = SimpleOption.ofBoolean(
            Options.CAS_ENABLED_KEY,
            Options.casEnabled,
            value -> {
                Options.setCasEnabled(value, true);
                screen.refreshContent();
            });
        section.addToggle(casEnabled.createWidget(MinecraftClient.getInstance().options));

        if (Options.casEnabled) {
            ResettableSliderWidget casSharpnessSlider = new ResettableSliderWidget(
                0, 0, 150, 20,
                0, 100, Options.casSharpnessPercent, 50,
                v -> getGenericValueText(
                    Text.translatable(Options.CAS_SHARPNESS_KEY),
                    Text.literal(v + "%")),
                v -> Options.setCasSharpnessPercent(v, true));
            section.addSlider(casSharpnessSlider);
        }
    }
}
