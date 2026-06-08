package com.radiance.client.gui.unified.populators;

import static net.minecraft.client.option.GameOptions.getGenericValueText;

import com.mojang.serialization.Codec;
import com.radiance.client.gui.ResettableSliderWidget;
import com.radiance.client.gui.unified.ContentPanelWidget;
import com.radiance.client.gui.unified.ContentPopulator;
import com.radiance.client.gui.unified.RadianceUnifiedScreen;
import com.radiance.client.gui.unified.SettingsSection;
import com.radiance.client.gui.unified.UnifiedSearchOverlay;
import com.radiance.client.option.Options;
import java.util.List;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.option.SimpleOption;
import net.minecraft.text.Text;

public class PerformancePopulator implements ContentPopulator {
    @Override
    public void populate(ContentPanelWidget panel, RadianceUnifiedScreen screen) {
        var mc = MinecraftClient.getInstance();
        var gameOptions = mc.options;

        SettingsSection pacing = panel.addSection("Frame Pacing");

        SimpleOption<Boolean> vsyncToggle = SimpleOption.ofBoolean(
            Options.VSYNC_KEY, Options.vsync,
            value -> {
                Options.setVsync(value, true);
                screen.refreshContent();
            });

        if (Options.isReflexSupported()) {
            boolean fgForced = Options.frameGenMode != 0;
            if (fgForced) {
                ButtonWidget reflexLocked = ButtonWidget.builder(
                    Text.literal("Reflex: Locked (FG)"), btn -> {})
                    .width(150).build();
                reflexLocked.active = false;
                pacing.addTwoWidgets(vsyncToggle.createWidget(gameOptions), reflexLocked)
                      .tooltip("Locked by the upscaling page while generated frames are active.");
            } else {
                SimpleOption<Boolean> reflexEnabled = SimpleOption.ofBoolean(
                    Options.REFLEX_ENABLED_KEY, Options.reflexEnabled,
                    value -> {
                        Options.setReflexEnabled(value, true);
                        screen.refreshContent();
                    });
                pacing.addTwoWidgets(vsyncToggle.createWidget(gameOptions), reflexEnabled.createWidget(gameOptions));
            }
        } else {
            pacing.addTwoWidgets(vsyncToggle.createWidget(gameOptions), null);
        }

        pacing.addSlider(new ResettableSliderWidget(0, 0, 150, 20,
            0, 999, Options.maxFps, 0,
            v -> getGenericValueText(Text.translatable(Options.MAX_FPS_KEY),
                Text.literal(v == 0 ? "Unlimited" : v + " fps")),
            v -> Options.setMaxFps(v, true)));

        ButtonWidget vrrButton = ButtonWidget.builder(
            Text.literal("Auto VRR Cap"),
            btn -> {
                int hz = Options.nativeGetDisplayRefreshRate();
                if (hz > 0) {
                    int target = (3600 * hz) / (hz + 3600);
                    Options.setMaxFps(target, true);
                    screen.refreshContent();
                }
            }).width(150).build();
        pacing.addButton(vrrButton);

        SettingsSection renderDist = panel.addSection("Render Distance");

        SimpleOption<Integer> chunkCullDistance = new SimpleOption<>(
            Options.CHUNK_CULL_DISTANCE_KEY,
            SimpleOption.emptyTooltip(),
            (optionText, value) -> getGenericValueText(optionText,
                Text.literal(value == 0 ? "Unlimited" : value + " chunks")),
            new SimpleOption.ValidatingIntSliderCallbacks(0, 512),
            Codec.intRange(0, 512),
            Options.chunkCullDistance,
            value -> Options.setChunkCullDistance(value, true));

        renderDist.addTwoWidgets(chunkCullDistance.createWidget(gameOptions), null)
            .tooltip("Cull Distance controls maximum ray-traced chunk visibility.");

        SettingsSection terrain = panel.addSection("Chunk Building");

        SimpleOption<Integer> chunkBatchSize = new SimpleOption<>(
            Options.CHUNK_BUILDING_BATCH_SIZE_KEY,
            SimpleOption.emptyTooltip(),
            (optionText, value) -> getGenericValueText(optionText, Text.literal(Integer.toString(value))),
            new SimpleOption.ValidatingIntSliderCallbacks(1, 32),
            Codec.intRange(1, 32),
            Options.chunkBuildingBatchSize,
            value -> Options.setChunkBuildingBatchSize(value, true));

        SimpleOption<Integer> chunkTotalBatches = new SimpleOption<>(
            Options.CHUNK_BUILDING_TOTAL_BATCHES_KEY,
            SimpleOption.emptyTooltip(),
            (optionText, value) -> getGenericValueText(optionText, Text.literal(Integer.toString(value))),
            new SimpleOption.ValidatingIntSliderCallbacks(1, 32),
            Codec.intRange(1, 32),
            Options.chunkBuildingTotalBatches,
            value -> Options.setChunkBuildingTotalBatches(value, true));

        terrain.addTwoWidgets(chunkBatchSize.createWidget(gameOptions), chunkTotalBatches.createWidget(gameOptions));
    }

    @Override
    public List<UnifiedSearchOverlay.SearchEntry> getSearchEntries(String nodeId, String category) {
        return List.of(
            new UnifiedSearchOverlay.SearchEntry("VSync", category, nodeId, false),
            new UnifiedSearchOverlay.SearchEntry("Reflex", category, nodeId, false),
            new UnifiedSearchOverlay.SearchEntry("FPS Limit", category, nodeId, false),
            new UnifiedSearchOverlay.SearchEntry("Auto VRR Cap", category, nodeId, false),
            new UnifiedSearchOverlay.SearchEntry("Chunk Cull Distance", category, nodeId, false),
            new UnifiedSearchOverlay.SearchEntry("Chunk LOD Distance", category, nodeId, false),
            new UnifiedSearchOverlay.SearchEntry("Chunk Batch Size", category, nodeId, false),
            new UnifiedSearchOverlay.SearchEntry("Chunk Total Batches", category, nodeId, false)
        );
    }
}
