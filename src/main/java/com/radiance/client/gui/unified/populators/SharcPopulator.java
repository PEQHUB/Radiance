package com.radiance.client.gui.unified.populators;

import static net.minecraft.client.option.GameOptions.getGenericValueText;

import com.radiance.client.gui.RadianceTheme;
import com.radiance.client.gui.ResettableSliderWidget;
import com.radiance.client.gui.SelectionDropdownWidget;
import com.radiance.client.gui.unified.*;
import com.radiance.client.option.Options;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Element;
import net.minecraft.client.option.SimpleOption;
import net.minecraft.text.Text;

public class SharcPopulator implements ContentPopulator {

    /** All individual sliders — stored so the preset slider can live-update them. */
    private final List<ResettableSliderWidget> paramSliders = new ArrayList<>();

    @Override
    public void populate(ContentPanelWidget panel, RadianceUnifiedScreen screen) {
        var gameOptions = MinecraftClient.getInstance().options;
        paramSliders.clear();

        SettingsSection section = panel.addSection(Options.CATEGORY_SHARC);

        // ── Row 1: Enable toggle + Downscale ──
        SimpleOption<Boolean> sharcEnabled = SimpleOption.ofBoolean(
            Options.SHARC_ENABLED_KEY, Options.sharcEnabled,
            value -> Options.setSharcEnabled(value, true));

        SelectionDropdownWidget sharcDownscale = new SelectionDropdownWidget(
            0, 0, 150, 20, "Downscale",
            new String[]{"1x", "2x", "3x", "4x", "5x", "6x", "7x", "8x"},
            Options.sharcDownscale - 1,
            value -> Options.setSharcDownscale(value + 1, true));
        section.addTwoWidgets(sharcEnabled.createWidget(gameOptions), sharcDownscale);
        section.tooltip("SHARC caches indirect lighting in a hash grid. Downscale reduces update cost.");

        if (Options.sharcEnabled) {
            // ── Row 2: Quality Preset slider (full width) ──
            // 0=Low, 1=Medium, 2=High, 3=Ultra, 4=Overkill, 5=Custom
            ResettableSliderWidget presetSlider = new ResettableSliderWidget(
                0, 0, 200, 20, 0, 5,
                Options.sharcQualityPreset, 1,
                value -> {
                    String name = Options.SHARC_PRESET_NAMES[value];
                    return Text.translatable(Options.SHARC_QUALITY_PRESET_KEY)
                        .append(": ").append(Text.literal(name));
                },
                value -> {
                    Options.applySharcPreset(value, true);
                    refreshSliders();
                });
            presetSlider.settingKey = Options.SHARC_QUALITY_PRESET_KEY;
            section.addSlider(presetSlider);

            // ── Info Row: VRAM + Coverage summary ──
            section.addRow(new SharcInfoRow());

            // ── Individual parameter sliders (2 per row) ──

            // Scene Scale (tenths: 10-200 → display 1.0-20.0)
            ResettableSliderWidget sceneScale = new ResettableSliderWidget(
                0, 0, 200, 20, 10, 200,
                Options.sharcSceneScaleTenths, 40,
                value -> Text.translatable(Options.SHARC_SCENE_SCALE_KEY)
                    .append(": ").append(Text.literal(String.format("%.1f", value / 10.0))),
                value -> { Options.setSharcSceneScaleTenths(value, true); markCustom(presetSlider); });
            sceneScale.settingKey = Options.SHARC_SCENE_SCALE_KEY;

            // Roughness Threshold (percent: 0-100 → display 0.00-1.00)
            ResettableSliderWidget roughness = new ResettableSliderWidget(
                0, 0, 200, 20, 0, 100,
                Options.sharcRoughnessThresholdPercent, 25,
                value -> Text.translatable(Options.SHARC_ROUGHNESS_THRESHOLD_KEY)
                    .append(": ").append(Text.literal(String.format("%.0f%%", (double) value))),
                value -> { Options.setSharcRoughnessThresholdPercent(value, true); markCustom(presetSlider); });
            roughness.settingKey = Options.SHARC_ROUGHNESS_THRESHOLD_KEY;

            paramSliders.add(sceneScale);
            paramSliders.add(roughness);
            section.addTwoSliders(sceneScale, roughness);
            section.tooltip("Scene Scale controls grid cell size (larger = coarser). Roughness Threshold limits SHARC to rough surfaces.");

            // Accumulation Frames + Stale Frames
            ResettableSliderWidget accumFrames = new ResettableSliderWidget(
                0, 0, 200, 20, 4, 256,
                Options.sharcAccumulationFrames, 32,
                value -> Text.translatable(Options.SHARC_ACCUMULATION_FRAMES_KEY)
                    .append(": ").append(Text.literal(Integer.toString(value))),
                value -> { Options.setSharcAccumulationFrames(value, true); markCustom(presetSlider); });
            accumFrames.settingKey = Options.SHARC_ACCUMULATION_FRAMES_KEY;

            ResettableSliderWidget staleFrames = new ResettableSliderWidget(
                0, 0, 200, 20, 4, 128,
                Options.sharcStaleFrames, 16,
                value -> Text.translatable(Options.SHARC_STALE_FRAMES_KEY)
                    .append(": ").append(Text.literal(Integer.toString(value))),
                value -> { Options.setSharcStaleFrames(value, true); markCustom(presetSlider); });
            staleFrames.settingKey = Options.SHARC_STALE_FRAMES_KEY;

            paramSliders.add(accumFrames);
            paramSliders.add(staleFrames);
            section.addTwoSliders(accumFrames, staleFrames);
            section.tooltip("Accumulation = frames to blend for stability. Stale = frames before evicting unused entries.");

            // Update Block Size + Update Bounces
            ResettableSliderWidget blockSize = new ResettableSliderWidget(
                0, 0, 200, 20, 1, 8,
                Options.sharcUpdateBlockSize, 5,
                value -> Text.translatable(Options.SHARC_UPDATE_BLOCK_SIZE_KEY)
                    .append(": ").append(Text.literal(value + "x" + value)),
                value -> { Options.setSharcUpdateBlockSize(value, true); markCustom(presetSlider); });
            blockSize.settingKey = Options.SHARC_UPDATE_BLOCK_SIZE_KEY;

            ResettableSliderWidget bounces = new ResettableSliderWidget(
                0, 0, 200, 20, 2, 16,
                Options.sharcUpdateBounces, 4,
                value -> Text.translatable(Options.SHARC_UPDATE_BOUNCES_KEY)
                    .append(": ").append(Text.literal(Integer.toString(value))),
                value -> { Options.setSharcUpdateBounces(value, true); markCustom(presetSlider); });
            bounces.settingKey = Options.SHARC_UPDATE_BOUNCES_KEY;

            paramSliders.add(blockSize);
            paramSliders.add(bounces);
            section.addTwoSliders(blockSize, bounces);
            section.tooltip("Block Size = NxN pixel blocks per frame (lower = more coverage). Bounces = ray depth for cache updates.");

            // Cache Capacity (exponent: 20-24 → display 2^N entries + VRAM)
            ResettableSliderWidget capacity = new ResettableSliderWidget(
                0, 0, 200, 20, 18, 26,
                Options.sharcCapacityExponent, 21,
                value -> {
                    long entries = 1L << value;
                    String entryStr = entries >= 1048576
                        ? String.format("%.0fM", entries / 1048576.0)
                        : String.format("%.0fK", entries / 1024.0);
                    int vramMb = (int) (entries * 40 / (1024 * 1024)); // 40 bytes/entry
                    return Text.translatable(Options.SHARC_CAPACITY_EXPONENT_KEY)
                        .append(": ").append(Text.literal(entryStr + " (" + vramMb + " MB)"));
                },
                value -> { Options.setSharcCapacityExponent(value, true); markCustom(presetSlider); });
            capacity.settingKey = Options.SHARC_CAPACITY_EXPONENT_KEY;

            paramSliders.add(capacity);
            section.addSlider(capacity);
            section.tooltip("Maximum hash grid entries. More entries = more cached lighting but more VRAM (40 bytes/entry).");
        }
    }

    /** When an individual slider is moved, detect if we still match a preset or go Custom. */
    private void markCustom(ResettableSliderWidget presetSlider) {
        int detected = Options.detectSharcPreset();
        Options.sharcQualityPreset = detected;
        // Update the preset slider display to reflect the detected preset
        presetSlider.setCurrentValue(detected);
    }

    /** After a preset is applied, refresh all individual sliders to reflect new values. */
    private void refreshSliders() {
        if (paramSliders.size() >= 7) {
            paramSliders.get(0).setCurrentValue(Options.sharcSceneScaleTenths);     // sceneScale
            paramSliders.get(1).setCurrentValue(Options.sharcRoughnessThresholdPercent); // roughness
            paramSliders.get(2).setCurrentValue(Options.sharcAccumulationFrames);   // accumFrames
            paramSliders.get(3).setCurrentValue(Options.sharcStaleFrames);          // staleFrames
            paramSliders.get(4).setCurrentValue(Options.sharcUpdateBlockSize);      // blockSize
            paramSliders.get(5).setCurrentValue(Options.sharcUpdateBounces);        // bounces
            paramSliders.get(6).setCurrentValue(Options.sharcCapacityExponent);     // capacity
        }
    }

    /**
     * Info row that displays computed VRAM usage and update coverage % based on current settings.
     * Re-reads Options.* each frame for live updates.
     */
    private static class SharcInfoRow extends SettingsRow {
        @Override
        public void render(DrawContext context, int x, int y, int width,
                           int mouseX, int mouseY, float delta, float alphaMult) {
            if (alphaMult <= 0f) return;
            var renderer = MinecraftClient.getInstance().textRenderer;

            // Compute live stats
            long entries = 1L << Options.sharcCapacityExponent;
            int vramMb = (int) (entries * 40 / (1024 * 1024));
            int bs = Options.sharcUpdateBlockSize;
            float coveragePercent = 100.0f / (bs * bs);

            String left = String.format("VRAM: %d MB  |  Entries: %s",
                vramMb, entries >= 1048576
                    ? String.format("%.1fM", entries / 1048576.0)
                    : String.format("%.0fK", entries / 1024.0));
            String right = String.format("Coverage: %.1f%%  |  Bounces: %d",
                coveragePercent, Options.sharcUpdateBounces);

            RadianceTheme.drawOutlinedText(context, renderer,
                Text.literal(left), x + 4, y + (ROW_HEIGHT - 8) / 2,
                RadianceTheme.textSecondary, alphaMult);

            int rightW = renderer.getWidth(right);
            RadianceTheme.drawOutlinedText(context, renderer,
                Text.literal(right), x + width - rightW - 4, y + (ROW_HEIGHT - 8) / 2,
                RadianceTheme.textSecondary, alphaMult);
        }

        @Override
        public List<? extends Element> children() {
            return List.of();
        }

        @Override
        public boolean isGridFullSpan() {
            return true;
        }
    }

    @Override
    public java.util.List<UnifiedSearchOverlay.SearchEntry> getSearchEntries(String nodeId, String category) {
        return java.util.List.of(
            new UnifiedSearchOverlay.SearchEntry("SHARC Enabled", category, nodeId, true),
            new UnifiedSearchOverlay.SearchEntry("SHARC Quality Preset", category, nodeId, true),
            new UnifiedSearchOverlay.SearchEntry("SHARC Scene Scale", category, nodeId, true),
            new UnifiedSearchOverlay.SearchEntry("SHARC Roughness Threshold", category, nodeId, true),
            new UnifiedSearchOverlay.SearchEntry("SHARC Accumulation Frames", category, nodeId, true),
            new UnifiedSearchOverlay.SearchEntry("SHARC Cache Capacity", category, nodeId, true)
        );
    }
}
