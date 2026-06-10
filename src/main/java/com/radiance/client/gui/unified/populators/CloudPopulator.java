package com.radiance.client.gui.unified.populators;

import static net.minecraft.client.option.GameOptions.getGenericValueText;

import com.radiance.client.gui.ResettableSliderWidget;
import com.radiance.client.gui.unified.ContentPanelWidget;
import com.radiance.client.gui.unified.ContentPopulator;
import com.radiance.client.gui.unified.RadianceUnifiedScreen;
import com.radiance.client.gui.unified.SettingsSection;
import com.radiance.client.gui.unified.UnifiedSearchOverlay;
import com.radiance.client.option.Options;
import net.minecraft.text.Text;

public class CloudPopulator implements ContentPopulator {
    @Override
    public void populate(ContentPanelWidget panel, RadianceUnifiedScreen screen) {
        ShaderPackAttributeControls.addSection(panel,
            "options.video.environment.clouds.shader_pack.category",
            name -> ShaderPackAttributeControls.nameContainsAny(name,
                "cloud_mode",
                "volumetric_cloud",
                "indirect_volumetric_cloud",
                "capture_volumetric_cloud"));

        ShaderPackAttributeControls.addSection(panel,
            "options.video.environment.volumetric_lighting.category",
            name -> ShaderPackAttributeControls.nameContainsAny(name, "volumetric_light"));

        int dim = Options.getEnvironmentEditingDimension();
        SettingsSection basic = panel.addSection("options.video.environment.clouds.legacy_layer.category");

        basic.addTwoSliders(
            new ResettableSliderWidget(0, 0, 150, 20,
                0, 300, Options.cloudBrightnessPercent[dim], Options.PERCENT_DEFAULT,
                v -> getGenericValueText(Text.translatable("options.video.environment.cloud_brightness"),
                    Text.literal(v + "%")),
                v -> Options.setCloudBrightnessPercent(dim, v, true)),
            new ResettableSliderWidget(0, 0, 150, 20,
                0, 300, Options.cloudAlphaPercent[dim], Options.PERCENT_DEFAULT,
                v -> getGenericValueText(Text.translatable("options.video.environment.cloud_alpha"),
                    Text.literal(v + "%")),
                v -> Options.setCloudAlphaPercent(dim, v, true)));
        basic.tooltip("Cloud Brightness scales the legacy/fallback cloud layer brightness for the current dimension. Cloud Alpha controls opacity of the legacy/fallback cloud layer for the current dimension.");

        basic.addSlider(new ResettableSliderWidget(0, 0, 150, 20,
            -64, 64, Options.cloudHeightOffset[dim], 0,
            v -> getGenericValueText(Text.translatable("options.video.environment.cloud_height_offset"),
                Text.literal(v + "")),
            v -> Options.setCloudHeightOffset(dim, v, true)));
        basic.tooltip("Moves the legacy/fallback cloud layer up or down in blocks.");
    }

    @Override
    public java.util.List<UnifiedSearchOverlay.SearchEntry> getSearchEntries(String nodeId, String category) {
        return java.util.List.of(
            new UnifiedSearchOverlay.SearchEntry("Cloud Brightness", category, nodeId, false),
            new UnifiedSearchOverlay.SearchEntry("Cloud Alpha", category, nodeId, false),
            new UnifiedSearchOverlay.SearchEntry("Cloud Height", category, nodeId, false),
            new UnifiedSearchOverlay.SearchEntry("Cloud Mode", category, nodeId, false),
            new UnifiedSearchOverlay.SearchEntry("Volumetric Clouds", category, nodeId, false),
            new UnifiedSearchOverlay.SearchEntry("Cloud Shadows", category, nodeId, false),
            new UnifiedSearchOverlay.SearchEntry("Volumetric Lighting", category, nodeId, false)
        );
    }
}
