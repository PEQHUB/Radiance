package com.radiance.client.gui.unified.populators;

import static net.minecraft.client.option.GameOptions.getGenericValueText;

import com.mojang.serialization.Codec;
import com.radiance.client.gui.ResettableSliderWidget;
import com.radiance.client.gui.SelectionDropdownWidget;
import com.radiance.client.gui.unified.*;
import com.radiance.client.option.Options;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.option.SimpleOption;
import net.minecraft.text.Text;

import java.util.List;

public class PerformancePopulator implements ContentPopulator {
    @Override
    public void populate(ContentPanelWidget panel, RadianceUnifiedScreen screen) {
        var mc = MinecraftClient.getInstance();
        var gameOptions = mc.options;

        // ── Frame Pacing ──
        SettingsSection pacing = panel.addSection("Frame Pacing");

        // VSync (always available — not gated behind Reflex)
        SimpleOption<Boolean> vsyncToggle = SimpleOption.ofBoolean(
            Options.VSYNC_KEY, Options.vsync,
            value -> {
                Options.setVsync(value, true);
                screen.refreshContent();
            });

        if (Options.isReflexSupported()) {
            // Reflex toggle paired with VSync
            boolean fgForced = Options.frameGenMode != 0;
            if (fgForced) {
                ButtonWidget reflexLocked = ButtonWidget.builder(
                    Text.literal("Reflex: Locked (FG)"), btn -> {})
                    .width(150).build();
                reflexLocked.active = false;
                pacing.addTwoWidgets(vsyncToggle.createWidget(gameOptions), reflexLocked);
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
            // No Reflex hardware — VSync solo
            pacing.addTwoWidgets(vsyncToggle.createWidget(gameOptions), null);
        }

        // FPS Limit (always available — not gated behind Reflex)
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

        // ── Frame Generation ──
        if (Options.isFrameGenSupported()) {
            SettingsSection fg = panel.addSection("Frame Generation");

            String[] fgModeNames = {"Off", "On", "Auto"};
            SelectionDropdownWidget fgModeDropdown = new SelectionDropdownWidget(
                0, 0, 150, 20, "Frame Generation",
                fgModeNames, Options.frameGenMode, value -> {
                    Options.setFrameGenMode(value, true);
                    screen.refreshContent();
                });

            int maxMulti = Options.getFrameGenMaxMultiplier();
            if (Options.frameGenMode == 1 && maxMulti > 1) {
                String[] multiNames = new String[maxMulti];
                for (int i = 0; i < maxMulti; i++) multiNames[i] = (i + 2) + "x";
                SelectionDropdownWidget fgMultiDropdown = new SelectionDropdownWidget(
                    0, 0, 150, 20, "FG Multiplier",
                    multiNames, Options.frameGenMultiplier - 1, value -> {
                        Options.setFrameGenMultiplier(value + 1, true);
                    });
                fg.addTwoWidgets(fgModeDropdown, fgMultiDropdown)
                  .tooltip("Generates interpolated frames between real renders. Requires Reflex for frame pacing.");
            } else {
                fg.addTwoWidgets(fgModeDropdown, null)
                  .tooltip(Options.frameGenMode == 2
                      ? "Auto: dynamically varies frame generation multiplier based on scene load."
                      : "Generates interpolated frames between real renders. Requires Reflex for frame pacing.");
            }
        }

        // ── Terrain ──
        SettingsSection terrain = panel.addSection("Terrain");

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

        SimpleOption<Integer> chunkCullDistance = new SimpleOption<>(
            Options.CHUNK_CULL_DISTANCE_KEY,
            SimpleOption.emptyTooltip(),
            (optionText, value) -> getGenericValueText(optionText, Text.literal(value + " blocks")),
            new SimpleOption.ValidatingIntSliderCallbacks(64, 1024),
            Codec.intRange(64, 1024),
            Options.chunkCullDistance,
            value -> Options.setChunkCullDistance(value, true));

        SimpleOption<Integer> chunkLodDistance = new SimpleOption<>(
            Options.CHUNK_LOD_DISTANCE_KEY,
            SimpleOption.emptyTooltip(),
            (optionText, value) -> getGenericValueText(optionText, Text.literal(value + " blocks")),
            new SimpleOption.ValidatingIntSliderCallbacks(64, 512),
            Codec.intRange(64, 512),
            Options.chunkLodDistance,
            value -> Options.setChunkLodDistance(value, true));

        terrain.addTwoWidgets(chunkCullDistance.createWidget(gameOptions), chunkLodDistance.createWidget(gameOptions));
    }

    @Override
    public List<UnifiedSearchOverlay.SearchEntry> getSearchEntries(String nodeId, String category) {
        return List.of(
            new UnifiedSearchOverlay.SearchEntry("VSync", category, nodeId, false),
            new UnifiedSearchOverlay.SearchEntry("Reflex", category, nodeId, false),
            new UnifiedSearchOverlay.SearchEntry("FPS Limit", category, nodeId, false),
            new UnifiedSearchOverlay.SearchEntry("Auto VRR Cap", category, nodeId, false),
            new UnifiedSearchOverlay.SearchEntry("Frame Generation", category, nodeId, false),
            new UnifiedSearchOverlay.SearchEntry("FG Multiplier", category, nodeId, false),
            new UnifiedSearchOverlay.SearchEntry("Chunk Batch Size", category, nodeId, false),
            new UnifiedSearchOverlay.SearchEntry("Chunk Total Batches", category, nodeId, false),
            new UnifiedSearchOverlay.SearchEntry("Chunk Cull Distance", category, nodeId, false),
            new UnifiedSearchOverlay.SearchEntry("Chunk LOD Distance", category, nodeId, false)
        );
    }
}
