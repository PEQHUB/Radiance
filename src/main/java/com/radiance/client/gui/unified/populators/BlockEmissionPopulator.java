package com.radiance.client.gui.unified.populators;

import com.radiance.client.gui.unified.*;
import com.radiance.client.gui.unified.rows.FiveColumnEmissionRow;
import com.radiance.client.util.EmissiveBlock;
import java.util.Arrays;
import java.util.Comparator;

/**
 * Parameterized populator for a page of emissive blocks.
 * Each block gets 2 rows: spectral controls + gamut/glow pair.
 * Blocks are sorted alphabetically within each category page.
 */
public class BlockEmissionPopulator implements ContentPopulator {

    private final String sectionTitle;
    private final EmissiveBlock[] blocks;

    public BlockEmissionPopulator(String sectionTitle, EmissiveBlock... blocks) {
        this.sectionTitle = sectionTitle;
        // Sort alphabetically by display ID
        this.blocks = blocks.clone();
        Arrays.sort(this.blocks, Comparator.comparing(EmissiveBlock::getId));
    }

    @Override
    public void populate(ContentPanelWidget panel, RadianceUnifiedScreen screen) {
        SettingsSection section = panel.addSection(sectionTitle);

        for (EmissiveBlock block : blocks) {
            // Row 1: [Emission | Temp | Material | Wavelength | Purity]
            var wave = EmissionWidgetFactory.makeWavelengthSlider(block);
            var mat = EmissionWidgetFactory.makeMaterialDropdown(block, wave);
            var waveSync = EmissionWidgetFactory.makeWavelengthSliderWithSync(block, mat);
            section.addRow(new FiveColumnEmissionRow(
                EmissionWidgetFactory.makeEmissionSlider(block),
                EmissionWidgetFactory.makeTemperatureSlider(block),
                mat, waveSync,
                EmissionWidgetFactory.makePuritySlider(block)));

            // Row 2: [Gamut preset | Even Glow toggle] — both click controls
            section.addTwoWidgets(
                EmissionWidgetFactory.makeGamutBoostCycling(block),
                EmissionWidgetFactory.makeEvenGlowToggle(block));
        }
    }
}
