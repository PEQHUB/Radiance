package com.radiance.client.gui.unified.populators;

import static net.minecraft.client.option.GameOptions.getGenericValueText;

import com.radiance.client.gui.ResettableSliderWidget;
import com.radiance.client.gui.unified.*;
import com.radiance.client.gui.unified.rows.SliderRow;
import com.radiance.client.gui.unified.rows.ToggleRow;
import com.radiance.client.option.Options;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.widget.CyclingButtonWidget;
import net.minecraft.client.option.SimpleOption;
import net.minecraft.text.Text;

public class PostProcessingPopulator implements ContentPopulator {
    @Override
    public void populate(ContentPanelWidget panel, RadianceUnifiedScreen screen) {
        SettingsSection section = panel.addSection("options.video.category.post_processing");

        // Sharpener mode: None / CAS / RCAS
        String[] sharpenerNames = {"None", "CAS", "RCAS"};
        CyclingButtonWidget<Integer> sharpenerBtn = CyclingButtonWidget.<Integer>builder(
                (value) -> Text.literal(sharpenerNames[value]))
            .values(0, 1, 2)
            .initially(Options.sharpenerMode)
            .build(0, 0, 150, 20, Text.translatable(Options.SHARPENER_MODE_KEY), (btn, value) -> {
                Options.setSharpenerMode(value, true);
                screen.refreshContent();
            });
        section.addToggle(sharpenerBtn);

        if (Options.sharpenerMode != 0) {
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
