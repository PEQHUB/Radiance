package com.radiance.client.gui.unified.populators;

import com.radiance.client.gui.unified.*;
import com.radiance.client.gui.unified.rows.FiveColumnEmissionRow;

/**
 * Populator for the Particles emission page.
 * Each particle gets a single 5-column row (no gamut/glow row).
 */
public class ParticlesEmissionPopulator implements ContentPopulator {
    @Override
    public void populate(ContentPanelWidget panel, RadianceUnifiedScreen screen) {
        SettingsSection section = panel.addSection("Particles");

        for (int i = 0; i < EmissionWidgetFactory.PARTICLE_DEFS.length; i++) {
            EmissionWidgetFactory.ParticleDef def = EmissionWidgetFactory.PARTICLE_DEFS[i];

            // Row: [Emission | Temp | Material | Wavelength | Purity]
            var wave = EmissionWidgetFactory.makeParticleWavelengthSlider(i);
            var mat = EmissionWidgetFactory.makeParticleMaterialDropdown(i, wave);
            var waveSync = EmissionWidgetFactory.makeParticleWavelengthSliderWithSync(i, mat);
            section.addRow(new FiveColumnEmissionRow(
                EmissionWidgetFactory.makeParticleEmissionSlider(def),
                EmissionWidgetFactory.makeParticleTemperatureSlider(i),
                mat, waveSync,
                EmissionWidgetFactory.makeParticlePuritySlider(i)));
        }
    }
}
