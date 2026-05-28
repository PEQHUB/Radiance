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
        SettingsSection section = panel.addSection("Particles").setLinear();

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

    @Override
    public java.util.List<UnifiedSearchOverlay.SearchEntry> getSearchEntries(String nodeId, String category) {
        java.util.List<UnifiedSearchOverlay.SearchEntry> entries = new java.util.ArrayList<>();
        entries.add(new UnifiedSearchOverlay.SearchEntry("Particle Emission", category, nodeId, false));
        for (EmissionWidgetFactory.ParticleDef def : EmissionWidgetFactory.PARTICLE_DEFS) {
            entries.add(new UnifiedSearchOverlay.SearchEntry(def.label(), category, nodeId, false));
        }
        return entries;
    }
}
