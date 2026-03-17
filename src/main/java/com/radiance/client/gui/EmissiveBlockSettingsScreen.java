package com.radiance.client.gui;

import static net.minecraft.client.option.GameOptions.getGenericValueText;

import com.google.common.collect.ImmutableList;
import com.radiance.client.option.Options;
import com.radiance.client.util.CategoryVideoOptionEntry;
import com.radiance.client.util.EmissiveBlock;
import com.radiance.client.util.FlameColorant;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.Selectable;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.option.GameOptionsScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.CyclingButtonWidget;
import net.minecraft.client.gui.widget.OptionListWidget;
import net.minecraft.text.Text;

public class EmissiveBlockSettingsScreen extends GameOptionsScreen {

    private final Screen parentScreen;
    private final List<MaterialDropdownWidget> dropdowns = new ArrayList<>();
    private int currentPage = 0;

    // Page definitions: blocks grouped by category
    private static final EmissiveBlock[][] BLOCK_PAGES = {
        // Page 0: Flames
        { EmissiveBlock.LAVA, EmissiveBlock.FIRE, EmissiveBlock.SOUL_FIRE,
          EmissiveBlock.TORCH, EmissiveBlock.SOUL_TORCH,
          EmissiveBlock.CAMPFIRE, EmissiveBlock.SOUL_CAMPFIRE,
          EmissiveBlock.LANTERN, EmissiveBlock.SOUL_LANTERN },
        // Page 1: Heat Sources
        { EmissiveBlock.MAGMA_BLOCK, EmissiveBlock.FURNACE, EmissiveBlock.BLAST_FURNACE,
          EmissiveBlock.SMOKER, EmissiveBlock.BREWING_STAND,
          EmissiveBlock.CANDLE, EmissiveBlock.JACK_O_LANTERN, EmissiveBlock.COPPER_BULB },
        // Page 2: Luminous Blocks
        { EmissiveBlock.GLOWSTONE, EmissiveBlock.SHROOMLIGHT, EmissiveBlock.SEA_LANTERN,
          EmissiveBlock.FROGLIGHT, EmissiveBlock.BEACON, EmissiveBlock.END_ROD,
          EmissiveBlock.REDSTONE_TORCH, EmissiveBlock.REDSTONE_LAMP,
          EmissiveBlock.CAVE_VINES, EmissiveBlock.GLOW_LICHEN,
          EmissiveBlock.AMETHYST_CLUSTER, EmissiveBlock.SEA_PICKLE,
          EmissiveBlock.ENCHANTING_TABLE, EmissiveBlock.ENDER_CHEST },
        // Page 3: Portals & Sculk
        { EmissiveBlock.NETHER_PORTAL, EmissiveBlock.END_PORTAL, EmissiveBlock.END_GATEWAY,
          EmissiveBlock.CRYING_OBSIDIAN, EmissiveBlock.RESPAWN_ANCHOR, EmissiveBlock.CONDUIT,
          EmissiveBlock.SCULK, EmissiveBlock.SCULK_VEIN, EmissiveBlock.SCULK_SENSOR,
          EmissiveBlock.SCULK_CATALYST, EmissiveBlock.SCULK_SHRIEKER,
          EmissiveBlock.CALIBRATED_SCULK_SENSOR,
          EmissiveBlock.TRIAL_SPAWNER, EmissiveBlock.VAULT },
    };

    private static final String[] PAGE_NAMES = {
        "Flames", "Heat Sources", "Luminous", "Portals & Sculk", "Particles"
    };

    // Particle emission definitions: [label key, Options field getter, Options field setter, default nits]
    private static final ParticleDef[] PARTICLE_DEFS = {
        new ParticleDef("Flame", () -> Options.flameParticleEmission, v -> Options.flameParticleEmission = v, 800),
        new ParticleDef("Lava Ember", () -> Options.lavaParticleEmission, v -> Options.lavaParticleEmission = v, 400),
        new ParticleDef("Firework Spark", () -> Options.fireworkSparkEmission, v -> { Options.fireworkSparkEmission = v; Options.fireworkSparkEmissionNits = v.intValue(); }, 20),
        new ParticleDef("Firework Flash", () -> Options.fireworkFlashEmission, v -> { Options.fireworkFlashEmission = v; Options.fireworkFlashEmissionNits = v.intValue(); }, 2000),
        new ParticleDef("Portal", () -> Options.portalParticleEmission, v -> Options.portalParticleEmission = v, 1500),
        new ParticleDef("End Rod", () -> Options.endRodParticleEmission, v -> Options.endRodParticleEmission = v, 200),
        new ParticleDef("Glow", () -> Options.glowParticleEmission, v -> Options.glowParticleEmission = v, 30),
        new ParticleDef("Soul Fire Flame", () -> Options.soulFireFlameParticleEmission, v -> Options.soulFireFlameParticleEmission = v, 500),
        new ParticleDef("Candle Flame", () -> Options.candleFlameParticleEmission, v -> Options.candleFlameParticleEmission = v, 300),
        new ParticleDef("Enchanting", () -> Options.enchantingParticleEmission, v -> Options.enchantingParticleEmission = v, 300),
        new ParticleDef("Sculk", () -> Options.sculkParticleEmission, v -> Options.sculkParticleEmission = v, 175),
        new ParticleDef("Totem", () -> Options.totemParticleEmission, v -> Options.totemParticleEmission = v, 500),
        new ParticleDef("Dragon Breath", () -> Options.dragonBreathParticleEmission, v -> Options.dragonBreathParticleEmission = v, 500),
        new ParticleDef("Lava Drip", () -> Options.lavaDripParticleEmission, v -> Options.lavaDripParticleEmission = v, 200),
    };

    public EmissiveBlockSettingsScreen(Screen parent) {
        super(parent, MinecraftClient.getInstance().options, Text.translatable("radiance.settings.emission.title"));
        this.parentScreen = parent;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (RadianceTheme.handlePeekKeyPressed(keyCode)) return true;
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        if (RadianceTheme.handlePeekKeyReleased(keyCode)) return true;
        return super.keyReleased(keyCode, scanCode, modifiers);
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        // Transparent — game world shows through
    }

    @Override
    protected void initBody() {
        MaterialDropdownWidget.clearInstances();
        dropdowns.clear();
        this.body = this.layout.addBody(
            new WideOptionListWidget(this.client, this.width, this));
        addOptions();
    }

    @Override
    protected void addOptions() {
        rebuildPage();
    }

    private void rebuildPage() {
        this.body.children().clear();
        MaterialDropdownWidget.clearInstances();
        dropdowns.clear();

        // Tab bar (always first)
        this.body.addEntry(new TabBarEntry(this.body));

        if (currentPage < BLOCK_PAGES.length) {
            // Block emission pages
            for (EmissiveBlock block : BLOCK_PAGES[currentPage]) {
                addBlockEntries(block);
            }
        } else {
            // Particle emission page
            addParticleEntries();
        }

        this.body.setScrollY(0);
    }

    private void addBlockEntries(EmissiveBlock block) {
        ResettableSliderWidget em = makeEmissionSlider(block);
        ResettableSliderWidget gamut = makeGamutBoostSlider(block);
        ResettableSliderWidget temp = makeTemperatureSlider(block);
        ResettableSliderWidget wave = makeWavelengthSlider(block);
        ResettableSliderWidget pur = makePuritySlider(block);
        MaterialDropdownWidget mat = makeMaterialDropdown(block, wave);
        dropdowns.add(mat);

        // Row 1: full 5-column [Emission | Temp | Material | Wavelength | Purity]
        ResettableSliderWidget waveWithSync = makeWavelengthSliderWithSync(block, mat);
        this.body.addEntry(new RadianceSettingsScreen.FiveColumnEmissionEntry(
            em, temp, mat, waveWithSync, pur, body));

        // Row 2: [Gamut Boost | Even Glow toggle]
        CyclingButtonWidget<Boolean> evenGlowToggle = makeEvenGlowToggle(block);
        this.body.addEntry(new SliderAndToggleEntry(gamut, evenGlowToggle, body));
    }

    private void addParticleEntries() {
        for (int i = 0; i < PARTICLE_DEFS.length; i++) {
            ParticleDef def = PARTICLE_DEFS[i];
            ResettableSliderWidget em = makeParticleSlider(def);
            ResettableSliderWidget temp = makeParticleTemperatureSlider(i);
            ResettableSliderWidget wave = makeParticleWavelengthSlider(i);
            ResettableSliderWidget pur = makeParticlePuritySlider(i);
            MaterialDropdownWidget mat = makeParticleMaterialDropdown(i, wave);
            dropdowns.add(mat);
            ResettableSliderWidget waveSync = makeParticleWavelengthSliderWithSync(i, mat);
            this.body.addEntry(new RadianceSettingsScreen.FiveColumnEmissionEntry(
                em, temp, mat, waveSync, pur, body));
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        RadianceTheme.drawBreadcrumb(context, this.textRenderer, "Radiance > Lighting > Emission", parentScreen);
        for (MaterialDropdownWidget dd : dropdowns) {
            dd.renderDropdownOverlay(context, mouseX, mouseY);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (RadianceTheme.handleBreadcrumbClick(mouseX, mouseY, parentScreen)) return true;
        for (MaterialDropdownWidget dd : dropdowns) {
            if (dd.isOpen() && dd.isInDropdownBounds(mouseX, mouseY)) {
                return dd.mouseClicked(mouseX, mouseY, button);
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY)) return true;
        if (button == 1) {
            Element focused = getFocused();
            if (focused != null) return focused.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
        }
        return false;
    }

    @Override
    public void removed() {
        super.removed();
        MaterialDropdownWidget.clearInstances();
    }

    // ========================================================================
    // Tab bar entry — renders clickable page buttons
    // ========================================================================

    private class TabBarEntry extends OptionListWidget.WidgetEntry {
        private final ButtonWidget[] tabs;

        TabBarEntry(OptionListWidget parent) {
            super(ImmutableList.of(), null);
            tabs = new ButtonWidget[PAGE_NAMES.length];
            for (int i = 0; i < PAGE_NAMES.length; i++) {
                final int page = i;
                tabs[i] = ButtonWidget.builder(Text.literal(PAGE_NAMES[i]), btn -> {
                    if (currentPage != page) {
                        currentPage = page;
                        rebuildPage();
                    }
                }).dimensions(0, 0, 60, 20).build();
            }
        }

        @Override
        public void render(DrawContext context, int index, int y, int x, int entryWidth,
            int entryHeight, int mouseX, int mouseY, boolean hovered, float tickDelta) {
            int gap = 4;
            int bw = (entryWidth - gap * (tabs.length - 1)) / tabs.length;
            int bx = x;
            for (int i = 0; i < tabs.length; i++) {
                tabs[i].setX(bx);
                tabs[i].setY(y);
                tabs[i].setWidth(bw);
                boolean isSelected = (i == currentPage);
                boolean isHovered = mouseX >= bx && mouseX < bx + bw
                    && mouseY >= y && mouseY < y + entryHeight;
                // Custom rendering for selected/unselected tab
                RadianceTheme.drawCustomButton(context, bx, y, bw, entryHeight,
                    isHovered || isSelected,
                    MinecraftClient.getInstance().textRenderer,
                    tabs[i].getMessage());
                if (isSelected) {
                    // Accent underline for selected tab
                    context.fill(bx + 2, y + entryHeight - 2, bx + bw - 2, y + entryHeight,
                        0xFF40E0D0); // teal accent
                }
                bx += bw + gap;
            }
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            for (ButtonWidget tab : tabs) {
                if (tab.isMouseOver(mouseX, mouseY)) {
                    tab.onClick(mouseX, mouseY);
                    return true;
                }
            }
            return false;
        }

        @Override
        public List<? extends Element> children() {
            return List.of(tabs);
        }

        @Override
        public List<? extends Selectable> selectableChildren() {
            return List.of(tabs);
        }
    }

    // ========================================================================
    // Entry types for emission rows with uniform glow toggle
    // ========================================================================

    /** Thermal block second row: [Gamut Boost slider | Uniform Glow toggle] */
    static class SliderAndToggleEntry extends OptionListWidget.WidgetEntry {
        private final ResettableSliderWidget slider;
        private final CyclingButtonWidget<Boolean> toggle;

        SliderAndToggleEntry(ResettableSliderWidget slider, CyclingButtonWidget<Boolean> toggle,
                             OptionListWidget parent) {
            super(ImmutableList.of(slider, toggle), null);
            this.slider = slider;
            this.toggle = toggle;
        }

        @Override
        public void render(DrawContext context, int index, int y, int x, int entryWidth,
            int entryHeight, int mouseX, int mouseY, boolean hovered, float tickDelta) {
            float fadeFactor = RadianceTheme.isActiveEntry(children()) ? 1f : RadianceTheme.inactiveFadeFactor();
            if (fadeFactor <= 0f) return;
            int gap = 8;
            int colW = (entryWidth - gap) / 2;
            slider.setX(x);
            slider.setY(y);
            slider.setWidth(colW);
            slider.render(context, mouseX, mouseY, tickDelta);
            RadianceTheme.drawModifiedDot(context, slider.getX(), y, entryHeight, !slider.isDefault());
            toggle.setX(x + colW + gap);
            toggle.setY(y);
            toggle.setWidth(colW);
            toggle.render(context, mouseX, mouseY, tickDelta);
        }

        @Override
        public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
            return slider.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
        }

        @Override
        public List<? extends Element> children() { return ImmutableList.of(slider, toggle); }

        @Override
        public List<? extends Selectable> selectableChildren() { return ImmutableList.of(slider, toggle); }
    }


    // ========================================================================
    // Widget factories
    // ========================================================================

    private CyclingButtonWidget<Boolean> makeEvenGlowToggle(EmissiveBlock block) {
        return CyclingButtonWidget.onOffBuilder()
            .initially(block.isUniformGlow())
            .build(0, 0, 100, 20, Text.literal("Even Glow"), (btn, value) -> {
                block.setUniformGlow(value, true);
            });
    }

    private ResettableSliderWidget makeEmissionSlider(EmissiveBlock block) {
        String key = "options.video.emission." + block.getId();
        int initialValue = (int) (block.getValue() * 100);

        return new ResettableSliderWidget(
            0, 0, 100, 20,
            0, 500, initialValue, (int)(block.getDefaultValue() * 100),
            v -> getGenericValueText(
                Text.translatable(key),
                Text.literal(String.format("%.2f", v / 100.0f))),
            v -> block.setValue(v / 100.0f, true));
    }

    private ResettableSliderWidget makeTemperatureSlider(EmissiveBlock block) {
        int currentTemp = Options.getBlockTemperature(block);
        int defaultTemp = block.getDefaultTemperatureCelsius();

        return new ResettableSliderWidget(
            0, 0, 100, 20,
            500, 4000, currentTemp, defaultTemp,
            v -> getGenericValueText(
                Text.literal("Temp"),
                Text.literal(v + "\u00B0C")),
            v -> Options.setBlockTemperature(block, v, true));
    }

    private ResettableSliderWidget makeWavelengthSlider(EmissiveBlock block) {
        int current = Options.getBlockWavelength(block);
        int defaultVal = block.getDefaultWavelengthNm();

        return new ResettableSliderWidget(
            0, 0, 100, 20,
            0, 780, current, defaultVal,
            v -> getGenericValueText(
                Text.literal("\u03BB"),
                Text.literal(v == 0 ? "Off" : v + "nm")),
            v -> Options.setBlockWavelength(block, v, true));
    }

    private ResettableSliderWidget makeWavelengthSliderWithSync(EmissiveBlock block, MaterialDropdownWidget dropdown) {
        int current = Options.getBlockWavelength(block);
        int defaultVal = block.getDefaultWavelengthNm();

        return new ResettableSliderWidget(
            0, 0, 100, 20,
            0, 780, current, defaultVal,
            v -> getGenericValueText(
                Text.literal("\u03BB"),
                Text.literal(v == 0 ? "Off" : v + "nm")),
            v -> {
                Options.setBlockWavelength(block, v, true);
                dropdown.updateFromWavelength(v);
            });
    }

    private ResettableSliderWidget makePuritySlider(EmissiveBlock block) {
        int current = Options.getBlockPurity(block);
        int defaultVal = block.getDefaultPurityPercent();

        return new ResettableSliderWidget(
            0, 0, 100, 20,
            0, 100, current, defaultVal,
            v -> getGenericValueText(
                Text.literal("Purity"),
                Text.literal(v + "%")),
            v -> Options.setBlockPurity(block, v, true));
    }

    private ResettableSliderWidget makeGamutBoostSlider(EmissiveBlock block) {
        int current = Options.getBlockGamutBoost(block);

        return new ResettableSliderWidget(
            0, 0, 100, 20,
            0, 200, current, 100,
            v -> getGenericValueText(
                Text.translatable("options.video.emission.gamutBoost"),
                Text.literal(String.format("\u00d7%.2f", v / 100.0))),
            v -> Options.setBlockGamutBoost(block, v, true));
    }

    private ResettableSliderWidget makeParticleSlider(ParticleDef def) {
        int current = def.getter.get().intValue();
        return new ResettableSliderWidget(
            0, 0, 100, 20,
            0, 5000, current, def.defaultNits,
            v -> getGenericValueText(
                Text.literal(def.label),
                Text.literal(v + " nits")),
            v -> {
                def.setter.accept((float) v);
                Options.overwriteConfig();
            });
    }

    private ResettableSliderWidget makeParticleTemperatureSlider(int idx) {
        return new ResettableSliderWidget(0, 0, 100, 20,
            500, 10000, Options.particleTemperatures[idx], Options.PARTICLE_TEMP_DEFAULTS[idx],
            v -> getGenericValueText(Text.literal(Options.PARTICLE_TYPE_NAMES[idx] + " T"),
                Text.literal(v + "\u00B0C")),
            v -> { Options.particleTemperatures[idx] = v; Options.overwriteConfig(); });
    }

    private ResettableSliderWidget makeParticleWavelengthSlider(int idx) {
        return new ResettableSliderWidget(0, 0, 100, 20,
            0, 780, Options.particleWavelengths[idx], Options.PARTICLE_WL_DEFAULTS[idx],
            v -> getGenericValueText(Text.literal(Options.PARTICLE_TYPE_NAMES[idx] + " \u03bb"),
                Text.literal(v == 0 ? "Off" : v + "nm")),
            v -> { Options.particleWavelengths[idx] = v; Options.overwriteConfig(); });
    }

    private ResettableSliderWidget makeParticleWavelengthSliderWithSync(int idx, MaterialDropdownWidget dropdown) {
        return new ResettableSliderWidget(0, 0, 100, 20,
            0, 780, Options.particleWavelengths[idx], Options.PARTICLE_WL_DEFAULTS[idx],
            v -> getGenericValueText(Text.literal(Options.PARTICLE_TYPE_NAMES[idx] + " \u03bb"),
                Text.literal(v == 0 ? "Off" : v + "nm")),
            v -> { Options.particleWavelengths[idx] = v; Options.overwriteConfig(); dropdown.updateFromWavelength(v); });
    }

    private ResettableSliderWidget makeParticlePuritySlider(int idx) {
        return new ResettableSliderWidget(0, 0, 100, 20,
            0, 100, Options.particlePurities[idx], Options.PARTICLE_PUR_DEFAULTS[idx],
            v -> getGenericValueText(Text.literal(Options.PARTICLE_TYPE_NAMES[idx] + " Pur"),
                Text.literal(v + "%")),
            v -> { Options.particlePurities[idx] = v; Options.overwriteConfig(); });
    }

    private MaterialDropdownWidget makeParticleMaterialDropdown(int idx, ResettableSliderWidget waveSlider) {
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

    private MaterialDropdownWidget makeMaterialDropdown(EmissiveBlock block, ResettableSliderWidget waveSlider) {
        int currentWl = Options.getBlockWavelength(block);
        MaterialDropdownWidget dropdown = new MaterialDropdownWidget(0, 0, 100, 20, colorant -> {
            if (colorant != FlameColorant.CUSTOM && colorant != FlameColorant.NONE) {
                int wl = colorant.getWavelengthNm();
                Options.setBlockWavelength(block, wl, true);
                if (waveSlider != null) {
                    waveSlider.setCurrentValue(wl);
                }
            } else if (colorant == FlameColorant.NONE) {
                Options.setBlockWavelength(block, 0, true);
                if (waveSlider != null) {
                    waveSlider.setCurrentValue(0);
                }
            }
        });
        dropdown.updateFromWavelength(currentWl);
        return dropdown;
    }

    // ========================================================================
    // Particle definition record
    // ========================================================================

    private record ParticleDef(String label, java.util.function.Supplier<Float> getter,
                                java.util.function.Consumer<Float> setter, int defaultNits) {}
}
