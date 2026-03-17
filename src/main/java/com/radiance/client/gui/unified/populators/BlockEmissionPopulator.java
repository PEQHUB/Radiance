package com.radiance.client.gui.unified.populators;

import com.radiance.client.gui.unified.*;
import com.radiance.client.gui.unified.rows.FiveColumnEmissionRow;
import com.radiance.client.util.EmissiveBlock;

/**
 * Parameterized populator for a page of emissive blocks.
 * Each block gets a 5-column emission row + a gamut/glow row.
 */
public class BlockEmissionPopulator implements ContentPopulator {

    private final String sectionTitle;
    private final EmissiveBlock[] blocks;

    public BlockEmissionPopulator(String sectionTitle, EmissiveBlock... blocks) {
        this.sectionTitle = sectionTitle;
        this.blocks = blocks;
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

            // Row 2: [Gamut Boost | Even Glow]
            section.addTwoWidgets(
                EmissionWidgetFactory.makeGamutBoostSlider(block),
                EmissionWidgetFactory.makeEvenGlowToggle(block));
        }
    }
}
