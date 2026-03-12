package com.radiance.client.gui;

import static net.minecraft.client.option.GameOptions.getGenericValueText;

import com.google.common.collect.ImmutableList;
import com.mojang.serialization.Codec;
import com.radiance.client.option.Options;
import com.radiance.client.util.CategoryVideoOptionEntry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.option.GameOptionsScreen;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.option.SimpleOption;
import net.minecraft.text.Text;


public class AreaLightSettingsScreen extends GameOptionsScreen {

    private final Screen parentScreen;

    // Human-readable names for each light type ID (indices match C++ LightTypeId enum)
    static final String[] LIGHT_TYPE_KEYS = {
        "options.video.area_light.block.torch",
        "options.video.area_light.block.soul_torch",
        "options.video.area_light.block.lantern",
        "options.video.area_light.block.soul_lantern",
        "options.video.area_light.block.campfire",
        "options.video.area_light.block.soul_campfire",
        "options.video.area_light.block.glowstone",
        "options.video.area_light.block.sea_lantern",
        "options.video.area_light.block.shroomlight",
        "options.video.area_light.block.jack_o_lantern",
        "options.video.area_light.block.end_rod",
        "options.video.area_light.block.beacon",
        "options.video.area_light.block.ochre_froglight",
        "options.video.area_light.block.verdant_froglight",
        "options.video.area_light.block.pearl_froglight",
        "options.video.area_light.block.redstone_torch",
        "options.video.area_light.block.redstone_lamp",
        "options.video.area_light.block.candle",
        null, // 18: unused (formerly candle_2)
        null, // 19: unused (formerly candle_3)
        null, // 20: unused (formerly candle_4)
        "options.video.area_light.block.cave_vines",
        "options.video.area_light.block.glow_lichen",
        "options.video.area_light.block.furnace",
        "options.video.area_light.block.blast_furnace",
        "options.video.area_light.block.smoker",
        "options.video.area_light.block.ender_chest",
        "options.video.area_light.block.crying_obsidian",
        "options.video.area_light.block.nether_portal",
        "options.video.area_light.block.conduit",
        "options.video.area_light.block.respawn_anchor_1",
        "options.video.area_light.block.respawn_anchor_2",
        "options.video.area_light.block.respawn_anchor_3",
        "options.video.area_light.block.respawn_anchor_4",
        "options.video.area_light.block.amethyst_cluster",
        "options.video.area_light.block.large_amethyst_bud",
        "options.video.area_light.block.copper_bulb",
        "options.video.area_light.block.enchanting_table",
        // --- New: formerly emissive-only blocks ---
        "options.video.area_light.block.lava",
        "options.video.area_light.block.fire",
        "options.video.area_light.block.soul_fire",
        "options.video.area_light.block.magma_block",
        "options.video.area_light.block.sculk_sensor",
        "options.video.area_light.block.sculk_catalyst",
        "options.video.area_light.block.sculk_vein",
        "options.video.area_light.block.sculk",
        "options.video.area_light.block.sculk_shrieker",
        "options.video.area_light.block.brewing_stand",
        "options.video.area_light.block.end_portal",
        "options.video.area_light.block.end_portal_frame",
    };

    public AreaLightSettingsScreen(Screen parent) {
        super(parent, MinecraftClient.getInstance().options, Text.translatable("radiance.settings.area_lights.title"));
        this.parentScreen = parent;
    }

    @Override
    protected void init() {
        super.init();

        // Shift the body list down to make room for hint text below the title
        this.body.setY(this.body.getY() + 22);
        this.body.setHeight(this.body.getHeight() - 22);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);

        // Breadcrumb
        RadianceTheme.drawOutlinedText(context, this.textRenderer,
            Text.literal("Radiance > Lighting > Area Lights"), 20, 26, RadianceTheme.textSecondary);

        // Hint line below the breadcrumb
        int hintY = 38;
        RadianceTheme.drawCenteredOutlinedText(context, this.textRenderer,
            Text.literal("Ctrl+Click a slider to type a value  \u2502  Shift+Click to reset to default"),
            this.width / 2, hintY, RadianceTheme.textSecondary);
    }

    @Override
    protected void initBody() {
        this.body = this.layout.addBody(
            new WideOptionListWidget(this.client, this.width, this));
        addOptions();
    }

    @Override
    protected void addOptions() {
        // === Global Controls ===
        this.body.addEntry(new CategoryVideoOptionEntry(
            Text.translatable("options.video.area_light.global_category"), body));

        // Row 1: Intensity + Range
        ResettableSliderWidget intensitySlider = new ResettableSliderWidget(
            0, 0, 150, 20,
            0, 500, Options.areaLightIntensityPercent, 100,
            v -> getGenericValueText(
                Text.translatable(Options.AREA_LIGHT_INTENSITY_KEY),
                Text.literal(v + "%")),
            v -> Options.setAreaLightIntensityPercent(v, true));

        ResettableSliderWidget rangeSlider = new ResettableSliderWidget(
            0, 0, 150, 20,
            8, 512, Options.areaLightRange, 128,
            v -> getGenericValueText(
                Text.translatable(Options.AREA_LIGHT_RANGE_KEY),
                Text.literal(v + " blocks")),
            v -> Options.setAreaLightRange(v, true));

        this.body.addEntry(new RadianceSettingsScreen.TwoColumnSliderEntry(intensitySlider, rangeSlider, body));

        // Row 2: Shadow Softness + ReSTIR Toggle
        ResettableSliderWidget shadowSlider = new ResettableSliderWidget(
            0, 0, 150, 20,
            0, 200, Options.shadowSoftnessPercent, 100,
            v -> getGenericValueText(
                Text.translatable(Options.AREA_LIGHT_SHADOW_SOFTNESS_KEY),
                Text.literal(v + "%")),
            v -> Options.setShadowSoftnessPercent(v, true));

        SimpleOption<Boolean> restirToggle = SimpleOption.ofBoolean(
            "options.video.area_light.restir",
            Options.restirEnabled,
            value -> Options.setRestirEnabled(value, true));

        ClickableWidget restirWidget = restirToggle.createWidget(this.gameOptions);
        this.body.addEntry(new RadianceSettingsScreen.TwoColumnOptionEntry(shadowSlider, restirWidget, body));

        // === ReSTIR Tuning ===
        this.body.addEntry(new CategoryVideoOptionEntry(
            Text.translatable(Options.CATEGORY_RESTIR), body));

        // Row 6: RIS Candidates + Temporal M Clamp
        ResettableSliderWidget candidatesSlider = new ResettableSliderWidget(
            0, 0, 150, 20,
            8, 64, Options.restirCandidates, 64,
            v -> getGenericValueText(
                Text.translatable(Options.RESTIR_CANDIDATES_KEY),
                Text.literal(String.valueOf(v))),
            v -> Options.setRestirCandidates(v, true));

        ResettableSliderWidget mClampSlider = new ResettableSliderWidget(
            0, 0, 150, 20,
            5, 50, Options.restirTemporalMClamp, 20,
            v -> getGenericValueText(
                Text.translatable(Options.RESTIR_TEMPORAL_M_CLAMP_KEY),
                Text.literal(String.valueOf(v))),
            v -> Options.setRestirTemporalMClamp(v, true));

        this.body.addEntry(new RadianceSettingsScreen.TwoColumnSliderEntry(candidatesSlider, mClampSlider, body));

        // Row 7: W Clamp
        ResettableSliderWidget wClampSlider = new ResettableSliderWidget(
            0, 0, 150, 20,
            10, 200, Options.restirWClamp, 30,
            v -> getGenericValueText(
                Text.translatable(Options.RESTIR_W_CLAMP_KEY),
                Text.literal(String.valueOf(v))),
            v -> Options.setRestirWClamp(v, true));

        this.body.addEntry(new RadianceSettingsScreen.TwoColumnSliderEntry(wClampSlider, null, body));

        // === ReSTIR Performance ===
        this.body.addEntry(new CategoryVideoOptionEntry(
            Text.translatable(Options.CATEGORY_RESTIR_PERFORMANCE), body));

        // Row 9: Simplified BRDF + Bounce Enabled (two toggles side by side)
        SimpleOption<Boolean> simplifiedBRDFToggle = SimpleOption.ofBoolean(
            Options.RESTIR_SIMPLIFIED_BRDF_KEY,
            Options.restirSimplifiedBRDF,
            value -> Options.setRestirSimplifiedBRDF(value, true));

        SimpleOption<Boolean> bounceEnabledToggle = SimpleOption.ofBoolean(
            Options.RESTIR_BOUNCE_ENABLED_KEY,
            Options.restirBounceEnabled,
            value -> Options.setRestirBounceEnabled(value, true));

        this.body.addWidgetEntry(
            simplifiedBRDFToggle.createWidget(this.gameOptions),
            bounceEnabledToggle.createWidget(this.gameOptions));

        // === Per-Block Controls (two-column grid) ===
        this.body.addEntry(new CategoryVideoOptionEntry(
            Text.translatable("options.video.area_light.per_block_category"), body));

        int count = Math.min(Options.AREA_LIGHT_TYPE_COUNT, LIGHT_TYPE_KEYS.length);
        // Collect non-null entries (skip unused/tombstoned type IDs)
        java.util.List<Integer> visibleTypes = new java.util.ArrayList<>();
        for (int i = 0; i < count; i++) {
            if (LIGHT_TYPE_KEYS[i] != null) visibleTypes.add(i);
        }
        for (int j = 0; j < visibleTypes.size(); j += 2) {
            int leftId = visibleTypes.get(j);
            ClickableWidget leftBtn = createBlockSettingsWidget(leftId, LIGHT_TYPE_KEYS[leftId]);
            ClickableWidget rightBtn = null;
            if (j + 1 < visibleTypes.size()) {
                int rightId = visibleTypes.get(j + 1);
                rightBtn = createBlockSettingsWidget(rightId, LIGHT_TYPE_KEYS[rightId]);
            }
            this.body.addWidgetEntry(leftBtn, rightBtn);
        }
    }

    private ClickableWidget createBlockSettingsWidget(int lightTypeId, String translationKey) {
        SimpleOption<Boolean> blockSettings = new SimpleOption<>(
            translationKey,
            SimpleOption.emptyTooltip(),
            (optionText, value) -> Text.translatable(translationKey).append(Text.literal("...")),
            new PotentialValuesBasedCallbacksNoValue<>(
                ImmutableList.of(Boolean.TRUE, Boolean.FALSE), Codec.BOOL),
            false,
            value -> MinecraftClient.getInstance().setScreen(
                new LightTypeDetailScreen(this, lightTypeId, translationKey)));
        return blockSettings.createWidget(this.gameOptions);
    }
}
