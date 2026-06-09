package com.radiance.client.gui.unified.populators;

import com.radiance.client.gui.SelectionDropdownWidget;
import com.radiance.client.gui.unified.*;
import com.radiance.client.gui.unified.rows.FiveColumnEmissionRow;
import com.radiance.client.util.EmissiveBlock;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * Unified emission populator with a dropdown to switch between 11 categories.
 * Replaces the 5-child emission subtree with a single leaf node.
 */
public class UnifiedEmissionPopulator implements ContentPopulator {

    private static final String[] CATEGORY_NAMES = {
        "Fire & Lava", "Torches & Lanterns", "Furnaces", "Crafted Light",
        "Nature", "Crystal & Arcane", "Portals", "Sculk & Deep Dark",
        "Dungeon", "Particles", "Fireworks"
    };

    private static final EmissiveBlock[][] BLOCK_CATEGORIES = {
        // 0: Fire & Lava
        { EmissiveBlock.LAVA, EmissiveBlock.FIRE, EmissiveBlock.SOUL_FIRE,
          EmissiveBlock.CAMPFIRE, EmissiveBlock.SOUL_CAMPFIRE, EmissiveBlock.MAGMA_BLOCK },
        // 1: Torches & Lanterns
        { EmissiveBlock.TORCH, EmissiveBlock.SOUL_TORCH, EmissiveBlock.LANTERN,
          EmissiveBlock.SOUL_LANTERN, EmissiveBlock.CANDLE, EmissiveBlock.REDSTONE_TORCH },
        // 2: Furnaces
        { EmissiveBlock.FURNACE, EmissiveBlock.BLAST_FURNACE, EmissiveBlock.SMOKER,
          EmissiveBlock.BREWING_STAND },
        // 3: Crafted Light
        { EmissiveBlock.GLOWSTONE, EmissiveBlock.SEA_LANTERN, EmissiveBlock.COPPER_BULB,
          EmissiveBlock.REDSTONE_LAMP, EmissiveBlock.JACK_O_LANTERN },
        // 4: Nature
        { EmissiveBlock.SHROOMLIGHT, EmissiveBlock.FROGLIGHT, EmissiveBlock.CAVE_VINES,
          EmissiveBlock.GLOW_LICHEN, EmissiveBlock.SEA_PICKLE, EmissiveBlock.BEACON },
        // 5: Crystal & Arcane
        { EmissiveBlock.AMETHYST_CLUSTER, EmissiveBlock.ENCHANTING_TABLE,
          EmissiveBlock.ENDER_CHEST, EmissiveBlock.END_ROD },
        // 6: Portals
        { EmissiveBlock.NETHER_PORTAL, EmissiveBlock.END_PORTAL, EmissiveBlock.END_GATEWAY,
          EmissiveBlock.CRYING_OBSIDIAN, EmissiveBlock.RESPAWN_ANCHOR, EmissiveBlock.CONDUIT },
        // 7: Sculk & Deep Dark
        { EmissiveBlock.SCULK, EmissiveBlock.SCULK_VEIN, EmissiveBlock.SCULK_SENSOR,
          EmissiveBlock.SCULK_CATALYST, EmissiveBlock.SCULK_SHRIEKER,
          EmissiveBlock.CALIBRATED_SCULK_SENSOR },
        // 8: Dungeon
        { EmissiveBlock.TRIAL_SPAWNER, EmissiveBlock.VAULT },
    };

    /** Persists selected category across screen rebuilds. */
    private static int selectedCategory = 0;

    /** Set category from inspect routing — finds which category contains the block. */
    public static void navigateToBlock(EmissiveBlock block) {
        for (int i = 0; i < BLOCK_CATEGORIES.length; i++) {
            for (EmissiveBlock b : BLOCK_CATEGORIES[i]) {
                if (b == block) {
                    selectedCategory = i;
                    return;
                }
            }
        }
    }

    @Override
    public void populate(ContentPanelWidget panel, RadianceUnifiedScreen screen) {
        SettingsSection header = panel.addSection("Emission");

        SelectionDropdownWidget categoryDropdown = new SelectionDropdownWidget(
            0, 0, 200, 20, "Category",
            CATEGORY_NAMES, selectedCategory, value -> {
                selectedCategory = value;
                screen.refreshContent();
            });
        header.addTwoWidgets(categoryDropdown, null);

        if (selectedCategory < BLOCK_CATEGORIES.length) {
            populateBlockCategory(panel, BLOCK_CATEGORIES[selectedCategory], CATEGORY_NAMES[selectedCategory]);
        } else if (selectedCategory == 9) {
            new ParticlesEmissionPopulator().populate(panel, screen);
        } else if (selectedCategory == 10) {
            new FireworksPopulator().populate(panel, screen);
        }
    }

    private void populateBlockCategory(ContentPanelWidget panel, EmissiveBlock[] blocks, String title) {
        EmissiveBlock[] sorted = blocks.clone();
        Arrays.sort(sorted, Comparator.comparing(EmissiveBlock::getId));

        SettingsSection section = panel.addSection(title).setLinear();
        for (EmissiveBlock block : sorted) {
            var wave = EmissionWidgetFactory.makeWavelengthSlider(block);
            var mat = EmissionWidgetFactory.makeMaterialDropdown(block, wave);
            var waveSync = EmissionWidgetFactory.makeWavelengthSliderWithSync(block, mat);
            section.addRow(new FiveColumnEmissionRow(
                EmissionWidgetFactory.makeEmissionSlider(block),
                EmissionWidgetFactory.makeTemperatureSlider(block),
                mat, waveSync,
                EmissionWidgetFactory.makePuritySlider(block)));

            section.addTwoWidgets(
                EmissionWidgetFactory.makeGamutBoostCycling(block),
                EmissionWidgetFactory.makeEvenGlowToggle(block));
        }
    }

    @Override
    public List<UnifiedSearchOverlay.SearchEntry> getSearchEntries(String nodeId, String category) {
        List<UnifiedSearchOverlay.SearchEntry> entries = new ArrayList<>();
        entries.add(new UnifiedSearchOverlay.SearchEntry("Emission", category, nodeId, true));
        for (String cat : CATEGORY_NAMES) {
            entries.add(new UnifiedSearchOverlay.SearchEntry(cat, category, nodeId, false));
        }
        for (EmissiveBlock[] group : BLOCK_CATEGORIES) {
            for (EmissiveBlock block : group) {
                entries.add(new UnifiedSearchOverlay.SearchEntry(block.getId(), category, nodeId, false));
            }
        }
        for (EmissionWidgetFactory.ParticleDef def : EmissionWidgetFactory.PARTICLE_DEFS) {
            entries.add(new UnifiedSearchOverlay.SearchEntry(def.label(), category, nodeId, false));
        }
        for (String name : com.radiance.client.option.Options.FIREWORK_COLOR_NAMES) {
            entries.add(new UnifiedSearchOverlay.SearchEntry("Firework " + name, category, nodeId, false));
        }
        return entries;
    }
}
