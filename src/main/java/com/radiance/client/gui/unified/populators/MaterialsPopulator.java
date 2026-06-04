package com.radiance.client.gui.unified.populators;

import static net.minecraft.client.option.GameOptions.getGenericValueText;

import com.radiance.client.gui.MaterialsSettingsScreen;
import com.radiance.client.gui.ResettableSliderWidget;
import com.radiance.client.gui.unified.*;
import com.radiance.client.option.Options;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.option.SimpleOption;
import net.minecraft.text.Text;

/**
 * Materials settings populator.
 */
public class MaterialsPopulator implements ContentPopulator {
    @Override
    public void populate(ContentPanelWidget panel, RadianceUnifiedScreen screen) {
        var gameOptions = MinecraftClient.getInstance().options;

        SettingsSection section = panel.addSection(Text.literal("Material Lab"));

        SimpleOption<Boolean> autoPBR = SimpleOption.ofBoolean(
            "options.video.materials.autoPBR", Options.autoPBREnabled,
            value -> Options.setAutoPBREnabled(value, true));
        section.addTwoWidgets(autoPBR.createWidget(gameOptions), null);
        section.tooltip("Material Lab is texture-primary. Blocks are discovery context, not material categories.");

        SettingsSection displacement = panel.addSection(Text.literal("Geometry Displacement"));
        SimpleOption<Boolean> displacementEnabled = SimpleOption.ofBoolean(
            "options.video.materials.displacementEnabled",
            Options.displacementEnabled,
            value -> {
                Options.setDisplacementEnabled(value, true);
                screen.refreshContent();
            });
        ButtonWidget quality = ButtonWidget.builder(qualityText(), btn -> {
            int next = Options.displacementQuality >= Options.DISPLACEMENT_QUALITY_ULTRA
                ? Options.DISPLACEMENT_QUALITY_LOW
                : Options.displacementQuality + 1;
            Options.setDisplacementQuality(next, true);
            btn.setMessage(qualityText());
        }).dimensions(0, 0, 150, 20).build();
        quality.active = Options.displacementEnabled;
        displacement.addTwoWidgets(displacementEnabled.createWidget(gameOptions), quality)
            .tooltip("Authored normal-alpha height maps displace cube faces. Quality controls the shader DDA step budget.");

        ResettableSliderWidget depthCap = new ResettableSliderWidget(0, 0, 150, 20,
            1, 50, Options.displacementDepthCapPercent, 5,
            v -> getGenericValueText(Text.literal("Depth Cap"), Text.literal(String.format("%.2f blocks", v / 100.0f))),
            v -> Options.setDisplacementDepthCapPercent(v, true));
        ResettableSliderWidget fadeDistance = new ResettableSliderWidget(0, 0, 150, 20,
            8, 256, Options.displacementFadeDistance, 64,
            v -> getGenericValueText(Text.literal("Fade Distance"), Text.literal(v + " blocks")),
            v -> Options.setDisplacementFadeDistance(v, true));
        depthCap.active = Options.displacementEnabled;
        fadeDistance.active = Options.displacementEnabled;
        displacement.addTwoSliders(depthCap, fadeDistance)
            .tooltip("Depth Cap limits every material. Fade Distance removes the effect at range to protect performance.");

        section.addLauncher("options.video.materials_settings",
            new MaterialsSettingsScreen(screen), screen);
    }

    private static Text qualityText() {
        return Text.literal("Quality: " + Options.displacementQualityName(Options.displacementQuality)
            + " (" + Options.displacementSteps + "/" + Options.displacementRefinement + ")");
    }

    @Override
    public java.util.List<UnifiedSearchOverlay.SearchEntry> getSearchEntries(String nodeId, String category) {
        return java.util.List.of(
            new UnifiedSearchOverlay.SearchEntry("Material Lab", category, nodeId, false),
            new UnifiedSearchOverlay.SearchEntry("Geometry Displacement", category, nodeId, false),
            new UnifiedSearchOverlay.SearchEntry("Displacement Quality", category, nodeId, false),
            new UnifiedSearchOverlay.SearchEntry("Depth Cap", category, nodeId, false),
            new UnifiedSearchOverlay.SearchEntry("Material Lab Browser", category, nodeId, false)
        );
    }
}
