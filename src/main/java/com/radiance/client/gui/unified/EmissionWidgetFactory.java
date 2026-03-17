package com.radiance.client.gui.unified;

import static net.minecraft.client.option.GameOptions.getGenericValueText;

import com.radiance.client.gui.MaterialDropdownWidget;
import com.radiance.client.gui.ResettableSliderWidget;
import com.radiance.client.option.Options;
import com.radiance.client.util.EmissiveBlock;
import com.radiance.client.util.FlameColorant;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.client.gui.widget.CyclingButtonWidget;
import net.minecraft.text.Text;

/**
 * Shared factory for emission control widgets. Used by both unified populators
 * and the legacy EmissiveBlockSettingsScreen.
 */
public class EmissionWidgetFactory {

    // ========================================================================
    // Block widgets
    // ========================================================================

    public static ResettableSliderWidget makeEmissionSlider(EmissiveBlock block) {
        String key = "options.video.emission." + block.getId();
        int initialValue = (int) (block.getValue() * 100);
        return new ResettableSliderWidget(0, 0, 100, 20,
            0, 500, initialValue, (int)(block.getDefaultValue() * 100),
            v -> getGenericValueText(Text.translatable(key),
                Text.literal(String.format("%.2f", v / 100.0f))),
            v -> block.setValue(v / 100.0f, true));
    }

    public static ResettableSliderWidget makeTemperatureSlider(EmissiveBlock block) {
        int currentTemp = Options.getBlockTemperature(block);
        int defaultTemp = block.getDefaultTemperatureCelsius();
        return new ResettableSliderWidget(0, 0, 100, 20,
            500, 4000, currentTemp, defaultTemp,
            v -> getGenericValueText(Text.literal("Temp"), Text.literal(v + "\u00B0C")),
            v -> Options.setBlockTemperature(block, v, true));
    }

    public static ResettableSliderWidget makeWavelengthSlider(EmissiveBlock block) {
        int current = Options.getBlockWavelength(block);
        int defaultVal = block.getDefaultWavelengthNm();
        return new ResettableSliderWidget(0, 0, 100, 20,
            0, 780, current, defaultVal,
            v -> getGenericValueText(Text.literal("\u03BB"), Text.literal(v == 0 ? "Off" : v + "nm")),
            v -> Options.setBlockWavelength(block, v, true));
    }

    public static ResettableSliderWidget makeWavelengthSliderWithSync(EmissiveBlock block, MaterialDropdownWidget dropdown) {
        int current = Options.getBlockWavelength(block);
        int defaultVal = block.getDefaultWavelengthNm();
        return new ResettableSliderWidget(0, 0, 100, 20,
            0, 780, current, defaultVal,
            v -> getGenericValueText(Text.literal("\u03BB"), Text.literal(v == 0 ? "Off" : v + "nm")),
            v -> { Options.setBlockWavelength(block, v, true); dropdown.updateFromWavelength(v); });
    }

    public static ResettableSliderWidget makePuritySlider(EmissiveBlock block) {
        int current = Options.getBlockPurity(block);
        int defaultVal = block.getDefaultPurityPercent();
        return new ResettableSliderWidget(0, 0, 100, 20,
            0, 100, current, defaultVal,
            v -> getGenericValueText(Text.literal("Purity"), Text.literal(v + "%")),
            v -> Options.setBlockPurity(block, v, true));
    }

    public static ResettableSliderWidget makeGamutBoostSlider(EmissiveBlock block) {
        int current = Options.getBlockGamutBoost(block);
        return new ResettableSliderWidget(0, 0, 100, 20,
            0, 200, current, 100,
            v -> getGenericValueText(Text.translatable("options.video.emission.gamutBoost"),
                Text.literal(String.format("\u00D7%.2f", v / 100.0f))),
            v -> Options.setBlockGamutBoost(block, v, true));
    }

    public static CyclingButtonWidget<Boolean> makeEvenGlowToggle(EmissiveBlock block) {
        return CyclingButtonWidget.onOffBuilder()
            .initially(block.isUniformGlow())
            .build(0, 0, 100, 20, Text.literal("Even Glow"), (btn, value) -> {
                block.setUniformGlow(value, true);
            });
    }

    public static MaterialDropdownWidget makeMaterialDropdown(EmissiveBlock block, ResettableSliderWidget waveSlider) {
        int currentWl = Options.getBlockWavelength(block);
        MaterialDropdownWidget dropdown = new MaterialDropdownWidget(0, 0, 100, 20, colorant -> {
            if (colorant != FlameColorant.CUSTOM && colorant != FlameColorant.NONE) {
                int wl = colorant.getWavelengthNm();
                Options.setBlockWavelength(block, wl, true);
                if (waveSlider != null) waveSlider.setCurrentValue(wl);
            } else if (colorant == FlameColorant.NONE) {
                Options.setBlockWavelength(block, 0, true);
                if (waveSlider != null) waveSlider.setCurrentValue(0);
            }
        });
        dropdown.updateFromWavelength(currentWl);
        return dropdown;
    }

    // ========================================================================
    // Particle widgets
    // ========================================================================

    public record ParticleDef(String label, Supplier<Float> getter, Consumer<Float> setter, int defaultNits) {}

    public static final ParticleDef[] PARTICLE_DEFS = {
        new ParticleDef("Flame", () -> Options.flameParticleEmission, v -> Options.flameParticleEmission = v, 800),
        new ParticleDef("Lava Ember", () -> Options.lavaParticleEmission, v -> Options.lavaParticleEmission = v, 400),
        new ParticleDef("FW Spark", () -> Options.fireworkSparkEmission, v -> { Options.fireworkSparkEmission = v; Options.fireworkSparkEmissionNits = v.intValue(); }, 20),
        new ParticleDef("FW Flash", () -> Options.fireworkFlashEmission, v -> { Options.fireworkFlashEmission = v; Options.fireworkFlashEmissionNits = v.intValue(); }, 2000),
        new ParticleDef("Portal", () -> Options.portalParticleEmission, v -> Options.portalParticleEmission = v, 1500),
        new ParticleDef("End Rod", () -> Options.endRodParticleEmission, v -> Options.endRodParticleEmission = v, 200),
        new ParticleDef("Glow", () -> Options.glowParticleEmission, v -> Options.glowParticleEmission = v, 30),
        new ParticleDef("Soul Flame", () -> Options.soulFireFlameParticleEmission, v -> Options.soulFireFlameParticleEmission = v, 500),
        new ParticleDef("Candle", () -> Options.candleFlameParticleEmission, v -> Options.candleFlameParticleEmission = v, 300),
        new ParticleDef("Enchant", () -> Options.enchantingParticleEmission, v -> Options.enchantingParticleEmission = v, 300),
        new ParticleDef("Sculk", () -> Options.sculkParticleEmission, v -> Options.sculkParticleEmission = v, 175),
        new ParticleDef("Totem", () -> Options.totemParticleEmission, v -> Options.totemParticleEmission = v, 500),
        new ParticleDef("Dragon", () -> Options.dragonBreathParticleEmission, v -> Options.dragonBreathParticleEmission = v, 500),
        new ParticleDef("Lava Drip", () -> Options.lavaDripParticleEmission, v -> Options.lavaDripParticleEmission = v, 200),
    };

    public static ResettableSliderWidget makeParticleEmissionSlider(ParticleDef def) {
        int current = def.getter.get().intValue();
        return new ResettableSliderWidget(0, 0, 100, 20,
            0, 5000, current, def.defaultNits,
            v -> getGenericValueText(Text.literal(def.label), Text.literal(v + " nits")),
            v -> { def.setter.accept((float) v); Options.overwriteConfig(); });
    }

    public static ResettableSliderWidget makeParticleTemperatureSlider(int idx) {
        return new ResettableSliderWidget(0, 0, 100, 20,
            500, 10000, Options.particleTemperatures[idx], Options.PARTICLE_TEMP_DEFAULTS[idx],
            v -> getGenericValueText(Text.literal("Temp"), Text.literal(v + "\u00B0C")),
            v -> { Options.particleTemperatures[idx] = v; Options.overwriteConfig(); });
    }

    public static ResettableSliderWidget makeParticleWavelengthSlider(int idx) {
        return new ResettableSliderWidget(0, 0, 100, 20,
            0, 780, Options.particleWavelengths[idx], Options.PARTICLE_WL_DEFAULTS[idx],
            v -> getGenericValueText(Text.literal("\u03BB"), Text.literal(v == 0 ? "Off" : v + "nm")),
            v -> { Options.particleWavelengths[idx] = v; Options.overwriteConfig(); });
    }

    public static ResettableSliderWidget makeParticleWavelengthSliderWithSync(int idx, MaterialDropdownWidget dropdown) {
        return new ResettableSliderWidget(0, 0, 100, 20,
            0, 780, Options.particleWavelengths[idx], Options.PARTICLE_WL_DEFAULTS[idx],
            v -> getGenericValueText(Text.literal("\u03BB"), Text.literal(v == 0 ? "Off" : v + "nm")),
            v -> { Options.particleWavelengths[idx] = v; Options.overwriteConfig(); dropdown.updateFromWavelength(v); });
    }

    public static ResettableSliderWidget makeParticlePuritySlider(int idx) {
        return new ResettableSliderWidget(0, 0, 100, 20,
            0, 100, Options.particlePurities[idx], Options.PARTICLE_PUR_DEFAULTS[idx],
            v -> getGenericValueText(Text.literal("Purity"), Text.literal(v + "%")),
            v -> { Options.particlePurities[idx] = v; Options.overwriteConfig(); });
    }

    public static MaterialDropdownWidget makeParticleMaterialDropdown(int idx, ResettableSliderWidget waveSlider) {
        int currentWl = Options.particleWavelengths[idx];
        MaterialDropdownWidget dropdown = new MaterialDropdownWidget(0, 0, 100, 20, colorant -> {
            if (colorant != FlameColorant.CUSTOM && colorant != FlameColorant.NONE) {
                int wl = colorant.getWavelengthNm();
                Options.particleWavelengths[idx] = wl;
                Options.overwriteConfig();
                if (waveSlider != null) waveSlider.setCurrentValue(wl);
            } else if (colorant == FlameColorant.NONE) {
                Options.particleWavelengths[idx] = 0;
                Options.overwriteConfig();
                if (waveSlider != null) waveSlider.setCurrentValue(0);
            }
        });
        dropdown.updateFromWavelength(currentWl);
        return dropdown;
    }
}
