package com.radiance.client.gui.unified.populators;

import static net.minecraft.client.option.GameOptions.getGenericValueText;

import com.radiance.client.gui.ResettableSliderWidget;
import com.radiance.client.gui.unified.*;
import com.radiance.client.option.Options;
import net.minecraft.text.Text;

public class CloudPopulator implements ContentPopulator {
    @Override
    public void populate(ContentPanelWidget panel, RadianceUnifiedScreen screen) {
        int dim = Options.getEnvironmentEditingDimension();

        SettingsSection basic = panel.addSection("options.video.environment.clouds.category");

        basic.addSlider(new ResettableSliderWidget(0, 0, 150, 20,
            0, 300, Options.cloudBrightnessPercent[dim], Options.PERCENT_DEFAULT,
            v -> getGenericValueText(Text.translatable("options.video.environment.cloud_brightness"), Text.literal(v + "%")),
            v -> Options.setCloudBrightnessPercent(dim, v, true)));

        basic.addSlider(new ResettableSliderWidget(0, 0, 150, 20,
            0, 300, Options.cloudAlphaPercent[dim], Options.PERCENT_DEFAULT,
            v -> getGenericValueText(Text.translatable("options.video.environment.cloud_alpha"), Text.literal(v + "%")),
            v -> Options.setCloudAlphaPercent(dim, v, true)));

        basic.addSlider(new ResettableSliderWidget(0, 0, 150, 20,
            -64, 64, Options.cloudHeightOffset[dim], 0,
            v -> getGenericValueText(Text.translatable("options.video.environment.cloud_height_offset"), Text.literal(v + "")),
            v -> Options.setCloudHeightOffset(dim, v, true)));

        SettingsSection volumetric = panel.addSection("options.video.environment.clouds.volumetric.category");

        volumetric.addSlider(new ResettableSliderWidget(0, 0, 150, 20,
            25, 300, Options.cloudDetailScalePercent[dim], Options.CLOUD_DETAIL_SCALE_DEFAULT_PERCENT,
            v -> getGenericValueText(Text.translatable("options.video.environment.cloud_detail_scale"), Text.literal(v + "%")),
            v -> Options.setCloudDetailScalePercent(dim, v, true)));

        volumetric.addSlider(new ResettableSliderWidget(0, 0, 150, 20,
            0, 300, Options.cloudDetailStrengthPercent[dim], Options.CLOUD_DETAIL_STRENGTH_DEFAULT_PERCENT,
            v -> getGenericValueText(Text.translatable("options.video.environment.cloud_detail_strength"), Text.literal(v + "%")),
            v -> Options.setCloudDetailStrengthPercent(dim, v, true)));

        volumetric.addSlider(new ResettableSliderWidget(0, 0, 150, 20,
            0, 200, Options.cloudShadowStrengthPercent[dim], Options.PERCENT_DEFAULT,
            v -> getGenericValueText(Text.translatable("options.video.environment.cloud_shadow_strength"), Text.literal(v + "%")),
            v -> Options.setCloudShadowStrengthPercent(dim, v, true)));

        volumetric.addSlider(new ResettableSliderWidget(0, 0, 150, 20,
            1, 16, Options.cloudThicknessBlocks[dim], 4,
            v -> getGenericValueText(Text.translatable("options.video.environment.cloud_thickness"), Text.literal(v + " blocks")),
            v -> Options.setCloudThicknessBlocks(dim, v, true)));

        volumetric.addSlider(new ResettableSliderWidget(0, 0, 150, 20,
            0, 300, Options.cloudDensityPercent[dim], Options.PERCENT_DEFAULT,
            v -> getGenericValueText(Text.translatable("options.video.environment.cloud_density"), Text.literal(v + "%")),
            v -> Options.setCloudDensityPercent(dim, v, true)));

        volumetric.addSlider(new ResettableSliderWidget(0, 0, 150, 20,
            0, 1, Options.cloudNoiseAffectsShadows[dim], dim == 0 ? 1 : 0,
            v -> getGenericValueText(Text.translatable("options.video.environment.cloud_shadow_noise"),
                v == 1 ? Text.translatable("options.on") : Text.translatable("options.off")),
            v -> Options.setCloudNoiseAffectsShadows(dim, v == 1, true)));
    }
}
