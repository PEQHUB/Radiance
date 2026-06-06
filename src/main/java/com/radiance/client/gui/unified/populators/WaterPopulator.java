package com.radiance.client.gui.unified.populators;

import static net.minecraft.client.option.GameOptions.getGenericValueText;

import com.radiance.client.gui.ResettableSliderWidget;
import com.radiance.client.gui.unified.*;
import com.radiance.client.option.Options;
import net.minecraft.text.Text;

public class WaterPopulator implements ContentPopulator {
    @Override
    public void populate(ContentPanelWidget panel, RadianceUnifiedScreen screen) {
        ShaderPackAttributeControls.addSection(panel,
            "options.video.environment.water.shader_pack.category",
            name -> ShaderPackAttributeControls.nameContainsAny(name, "water", "caustic"));

        int dim = Options.getEnvironmentEditingDimension();

        SettingsSection section = panel.addSection("options.video.environment.water.category");

        section.addSlider(new ResettableSliderWidget(0, 0, 150, 20,
            0, 100, Options.waterTintR[dim], Options.WATER_TINT_R_DEFAULT,
            v -> getGenericValueText(Text.translatable("options.video.environment.water_tint_r"), Text.literal(v + "%")),
            v -> Options.setWaterTintRPercent(dim, v, true)));

        section.addSlider(new ResettableSliderWidget(0, 0, 150, 20,
            0, 100, Options.waterTintG[dim], Options.WATER_TINT_G_DEFAULT,
            v -> getGenericValueText(Text.translatable("options.video.environment.water_tint_g"), Text.literal(v + "%")),
            v -> Options.setWaterTintGPercent(dim, v, true)));

        section.addSlider(new ResettableSliderWidget(0, 0, 150, 20,
            0, 100, Options.waterTintB[dim], Options.WATER_TINT_B_DEFAULT,
            v -> getGenericValueText(Text.translatable("options.video.environment.water_tint_b"), Text.literal(v + "%")),
            v -> Options.setWaterTintBPercent(dim, v, true)));

        section.addSlider(new ResettableSliderWidget(0, 0, 150, 20,
            0, 300, Options.waterFogStrengthPercent[dim], Options.PERCENT_DEFAULT,
            v -> getGenericValueText(Text.translatable("options.video.environment.water_fog_strength"), Text.literal(v + "%")),
            v -> Options.setWaterFogStrengthPercent(dim, v, true)));
    }

    @Override
    public java.util.List<UnifiedSearchOverlay.SearchEntry> getSearchEntries(String nodeId, String category) {
        return java.util.List.of(
            new UnifiedSearchOverlay.SearchEntry("Water Tint", category, nodeId, false),
            new UnifiedSearchOverlay.SearchEntry("Water Fog", category, nodeId, false),
            new UnifiedSearchOverlay.SearchEntry("Water Surface", category, nodeId, false),
            new UnifiedSearchOverlay.SearchEntry("Water Caustics", category, nodeId, false)
        );
    }
}
