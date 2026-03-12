package com.radiance.client.gui;

import static net.minecraft.client.option.GameOptions.getGenericValueText;

import com.radiance.client.option.Options;
import com.radiance.client.util.CategoryVideoOptionEntry;
import com.radiance.client.util.MaterialBlock;
import com.radiance.client.util.MetalPreset;
import net.minecraft.block.Block;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.option.GameOptionsScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.DirectionalLayoutWidget;
import net.minecraft.client.option.SimpleOption;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

public class MaterialsSettingsScreen extends GameOptionsScreen {

    private final Screen parentScreen;
    private static int currentBlockIndex = 0;  // persists across screen rebuilds
    private static int currentPresetIndex = 0; // persists across screen rebuilds

    // Snapshot of all values when screen first opens — used by Cancel
    private static boolean snapshotTaken = false;
    private static final int[] snapF0R = new int[40], snapF0G = new int[40], snapF0B = new int[40];
    private static final int[] snapRoughness = new int[40], snapMetallic = new int[40];
    private static final int[] snapTransmission = new int[40], snapIOR = new int[40];
    private static final int[] snapSubsurface = new int[40], snapAnisotropic = new int[40];
    private static final int[] snapSheenWeight = new int[40], snapSheenTint = new int[40];
    private static final int[] snapCoatWeight = new int[40], snapCoatRoughness = new int[40];
    private static int snapAutoPBRRoughnessGamma, snapAutoPBRRoughnessMin, snapAutoPBRRoughnessMax;
    private static int snapAutoPBRNormalStrength, snapAutoPBRVarianceWeight, snapAutoPBREdgeWeight;

    public MaterialsSettingsScreen(Screen parent) {
        super(parent, MinecraftClient.getInstance().options, Text.translatable("radiance.settings.materials.title"));
        this.parentScreen = parent;
        if (!snapshotTaken) {
            takeSnapshot();
            snapshotTaken = true;
        }
    }

    private static void takeSnapshot() {
        System.arraycopy(Options.materialF0R, 0, snapF0R, 0, 40);
        System.arraycopy(Options.materialF0G, 0, snapF0G, 0, 40);
        System.arraycopy(Options.materialF0B, 0, snapF0B, 0, 40);
        System.arraycopy(Options.materialRoughness, 0, snapRoughness, 0, 40);
        System.arraycopy(Options.materialMetallic, 0, snapMetallic, 0, 40);
        System.arraycopy(Options.materialTransmission, 0, snapTransmission, 0, 40);
        System.arraycopy(Options.materialIOR, 0, snapIOR, 0, 40);
        System.arraycopy(Options.materialSubsurface, 0, snapSubsurface, 0, 40);
        System.arraycopy(Options.materialAnisotropic, 0, snapAnisotropic, 0, 40);
        System.arraycopy(Options.materialSheenWeight, 0, snapSheenWeight, 0, 40);
        System.arraycopy(Options.materialSheenTint, 0, snapSheenTint, 0, 40);
        System.arraycopy(Options.materialCoatWeight, 0, snapCoatWeight, 0, 40);
        System.arraycopy(Options.materialCoatRoughness, 0, snapCoatRoughness, 0, 40);
        snapAutoPBRRoughnessGamma = Options.autoPBRRoughnessGamma;
        snapAutoPBRRoughnessMin = Options.autoPBRRoughnessMin;
        snapAutoPBRRoughnessMax = Options.autoPBRRoughnessMax;
        snapAutoPBRNormalStrength = Options.autoPBRNormalStrength;
        snapAutoPBRVarianceWeight = Options.autoPBRVarianceWeight;
        snapAutoPBREdgeWeight = Options.autoPBREdgeWeight;
    }

    private static void restoreSnapshot() {
        System.arraycopy(snapF0R, 0, Options.materialF0R, 0, 40);
        System.arraycopy(snapF0G, 0, Options.materialF0G, 0, 40);
        System.arraycopy(snapF0B, 0, Options.materialF0B, 0, 40);
        System.arraycopy(snapRoughness, 0, Options.materialRoughness, 0, 40);
        System.arraycopy(snapMetallic, 0, Options.materialMetallic, 0, 40);
        System.arraycopy(snapTransmission, 0, Options.materialTransmission, 0, 40);
        System.arraycopy(snapIOR, 0, Options.materialIOR, 0, 40);
        System.arraycopy(snapSubsurface, 0, Options.materialSubsurface, 0, 40);
        System.arraycopy(snapAnisotropic, 0, Options.materialAnisotropic, 0, 40);
        System.arraycopy(snapSheenWeight, 0, Options.materialSheenWeight, 0, 40);
        System.arraycopy(snapSheenTint, 0, Options.materialSheenTint, 0, 40);
        System.arraycopy(snapCoatWeight, 0, Options.materialCoatWeight, 0, 40);
        System.arraycopy(snapCoatRoughness, 0, Options.materialCoatRoughness, 0, 40);
        Options.autoPBRRoughnessGamma = snapAutoPBRRoughnessGamma;
        Options.autoPBRRoughnessMin = snapAutoPBRRoughnessMin;
        Options.autoPBRRoughnessMax = snapAutoPBRRoughnessMax;
        Options.autoPBRNormalStrength = snapAutoPBRNormalStrength;
        Options.autoPBRVarianceWeight = snapAutoPBRVarianceWeight;
        Options.autoPBREdgeWeight = snapAutoPBREdgeWeight;
    }

    private void applyChanges() {
        snapshotTaken = false;
        Options.overwriteConfig();
        MinecraftClient.getInstance().worldRenderer.reload();
        this.client.setScreen(this.parentScreen);
    }

    private void cancelChanges() {
        restoreSnapshot();
        snapshotTaken = false;
        Options.overwriteConfig();
        this.client.setScreen(this.parentScreen);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        RadianceTheme.drawOutlinedText(context, this.textRenderer,
            Text.literal("Radiance > Lighting > Materials"), 20, 26, RadianceTheme.textSecondary);

        // Block icon next to the block selector
        MaterialBlock[] blocks = MaterialBlock.values();
        if (currentBlockIndex < blocks.length) {
            MaterialBlock mb = blocks[currentBlockIndex];
            Block block = Registries.BLOCK.get(Identifier.of("minecraft", mb.getId()));
            if (block != null) {
                RadianceBlockIcon.drawBlockIcon(context, block, this.width - 44, 20, 24);
            }
        }
    }

    @Override
    protected void initBody() {
        this.body = this.layout.addBody(
            new WideOptionListWidget(this.client, this.width, this));
        addOptions();
    }

    @Override
    protected void initFooter() {
        DirectionalLayoutWidget footer = DirectionalLayoutWidget.horizontal().spacing(8);
        footer.add(ButtonWidget.builder(
            Text.translatable("radiance.settings.materials.apply"), btn -> applyChanges())
            .width(150).build());
        footer.add(ButtonWidget.builder(
            Text.translatable("radiance.settings.materials.cancel"), btn -> cancelChanges())
            .width(150).build());
        this.layout.addFooter(footer);
    }

    @Override
    public void close() {
        cancelChanges();
    }

    @Override
    protected void addOptions() {
        MaterialBlock[] blocks = MaterialBlock.values();
        if (currentBlockIndex >= blocks.length) currentBlockIndex = 0;
        MaterialBlock block = blocks[currentBlockIndex];
        int i = block.ordinal();

        // === Global toggles (immediate effect) ===
        SimpleOption<Boolean> overridesToggle = SimpleOption.ofBoolean(
            "options.video.materials.overridesEnabled",
            Options.materialOverridesEnabled,
            value -> {
                Options.materialOverridesEnabled = value;
                Options.overwriteConfig();
            });

        SimpleOption<Boolean> autoPBRToggle = SimpleOption.ofBoolean(
            "options.video.materials.autoPBR",
            Options.autoPBREnabled,
            value -> {
                Options.autoPBREnabled = value;
                Options.overwriteConfig();
                MinecraftClient.getInstance().worldRenderer.reload();
            });

        this.body.addAll(new SimpleOption[]{overridesToggle, autoPBRToggle});

        // === Block selector (full-width slider) ===
        // onChange only updates the index; screen rebuild is deferred to mouse release
        ResettableSliderWidget blockSelector = new ResettableSliderWidget(0, 0, 150, 20,
            0, blocks.length - 1, currentBlockIndex, 0,
            v -> {
                MaterialBlock b = MaterialBlock.values()[v];
                String name = Text.translatable("options.video.materials." + b.getId()).getString();
                return Text.literal(name + " (" + (v + 1) + "/" + blocks.length + ")");
            },
            v -> { currentBlockIndex = v; });
        blockSelector.setOnRelease(() -> {
            if (currentBlockIndex != i) {
                MinecraftClient.getInstance().setScreen(new MaterialsSettingsScreen(parentScreen));
            }
        });
        this.body.addEntry(new RadianceSettingsScreen.SliderEntry(blockSelector, body));

        // === Base Properties ===
        this.body.addEntry(new CategoryVideoOptionEntry(
            Text.translatable("options.video.category.materials.baseProperties"), body));

        // Metallic
        this.body.addEntry(new RadianceSettingsScreen.SliderEntry(
            new ResettableSliderWidget(0, 0, 150, 20,
                0, 1000, Options.materialMetallic[i], block.getDefaultMetallic(),
                v -> getGenericValueText(
                    Text.translatable("options.video.materials.metallic"),
                    Text.literal(String.format("%.1f%%", v / 10.0))),
                v -> { Options.materialMetallic[i] = v; }),
            body));

        // Roughness
        this.body.addEntry(new RadianceSettingsScreen.SliderEntry(
            new ResettableSliderWidget(0, 0, 150, 20,
                0, 100, Options.materialRoughness[i], block.getDefaultRoughness(),
                v -> getGenericValueText(
                    Text.translatable("options.video.materials.roughness"),
                    Text.literal(v + "%")),
                v -> { Options.materialRoughness[i] = v; }),
            body));

        // IOR
        this.body.addEntry(new RadianceSettingsScreen.SliderEntry(
            new ResettableSliderWidget(0, 0, 150, 20,
                1000, 3000, Math.max(Options.materialIOR[i], 1000), Math.max(block.getDefaultIOR(), 1000),
                v -> getGenericValueText(
                    Text.translatable("options.video.materials.ior"),
                    Text.literal(String.format("%.3f", v / 1000.0))),
                v -> {
                    Options.materialIOR[i] = v;
                    if (Options.materialMetallic[i] < 500) {
                        int f0pm = MaterialBlock.iorToF0Permille(v);
                        Options.materialF0R[i] = f0pm;
                        Options.materialF0G[i] = f0pm;
                        Options.materialF0B[i] = f0pm;
                    }
                }),
            body));

        // Transmission
        this.body.addEntry(new RadianceSettingsScreen.SliderEntry(
            new ResettableSliderWidget(0, 0, 150, 20,
                0, 1000, Options.materialTransmission[i], block.getDefaultTransmission(),
                v -> getGenericValueText(
                    Text.translatable("options.video.materials.transmission"),
                    Text.literal(String.format("%.1f%%", v / 10.0))),
                v -> { Options.materialTransmission[i] = v; }),
            body));

        // Subsurface
        this.body.addEntry(new RadianceSettingsScreen.SliderEntry(
            new ResettableSliderWidget(0, 0, 150, 20,
                0, 1000, Options.materialSubsurface[i], block.getDefaultSubsurface(),
                v -> getGenericValueText(
                    Text.translatable("options.video.materials.subsurface"),
                    Text.literal(String.format("%.1f%%", v / 10.0))),
                v -> { Options.materialSubsurface[i] = v; }),
            body));

        // Anisotropic
        this.body.addEntry(new RadianceSettingsScreen.SliderEntry(
            new ResettableSliderWidget(0, 0, 150, 20,
                0, 1000, Options.materialAnisotropic[i], block.getDefaultAnisotropic(),
                v -> getGenericValueText(
                    Text.translatable("options.video.materials.anisotropic"),
                    Text.literal(String.format("%.1f%%", v / 10.0))),
                v -> { Options.materialAnisotropic[i] = v; }),
            body));

        // === Sheen ===
        this.body.addEntry(new CategoryVideoOptionEntry(
            Text.translatable("options.video.category.materials.sheen"), body));

        this.body.addEntry(new RadianceSettingsScreen.SliderEntry(
            new ResettableSliderWidget(0, 0, 150, 20,
                0, 1000, Options.materialSheenWeight[i], block.getDefaultSheenWeight(),
                v -> getGenericValueText(
                    Text.translatable("options.video.materials.sheenWeight"),
                    Text.literal(String.format("%.1f%%", v / 10.0))),
                v -> { Options.materialSheenWeight[i] = v; }),
            body));

        this.body.addEntry(new RadianceSettingsScreen.SliderEntry(
            new ResettableSliderWidget(0, 0, 150, 20,
                0, 1000, Options.materialSheenTint[i], block.getDefaultSheenTint(),
                v -> getGenericValueText(
                    Text.translatable("options.video.materials.sheenTint"),
                    Text.literal(String.format("%.1f%%", v / 10.0))),
                v -> { Options.materialSheenTint[i] = v; }),
            body));

        // === Coat ===
        this.body.addEntry(new CategoryVideoOptionEntry(
            Text.translatable("options.video.category.materials.coat"), body));

        this.body.addEntry(new RadianceSettingsScreen.SliderEntry(
            new ResettableSliderWidget(0, 0, 150, 20,
                0, 1000, Options.materialCoatWeight[i], block.getDefaultCoatWeight(),
                v -> getGenericValueText(
                    Text.translatable("options.video.materials.coatWeight"),
                    Text.literal(String.format("%.1f%%", v / 10.0))),
                v -> { Options.materialCoatWeight[i] = v; }),
            body));

        this.body.addEntry(new RadianceSettingsScreen.SliderEntry(
            new ResettableSliderWidget(0, 0, 150, 20,
                0, 100, Options.materialCoatRoughness[i], block.getDefaultCoatRoughness(),
                v -> getGenericValueText(
                    Text.translatable("options.video.materials.coatRoughness"),
                    Text.literal(v + "%")),
                v -> { Options.materialCoatRoughness[i] = v; }),
            body));

        // === Fresnel F0 ===
        this.body.addEntry(new CategoryVideoOptionEntry(
            Text.translatable("options.video.category.materials.fresnelF0"), body));

        this.body.addEntry(new RadianceSettingsScreen.SliderEntry(
            new ResettableSliderWidget(0, 0, 150, 20,
                0, 1000, Options.materialF0R[i], block.getDefaultF0R(),
                v -> getGenericValueText(
                    Text.translatable("options.video.materials.f0r"),
                    Text.literal(String.format("%.1f%%", v / 10.0))),
                v -> { Options.materialF0R[i] = v; }),
            body));

        this.body.addEntry(new RadianceSettingsScreen.SliderEntry(
            new ResettableSliderWidget(0, 0, 150, 20,
                0, 1000, Options.materialF0G[i], block.getDefaultF0G(),
                v -> getGenericValueText(
                    Text.translatable("options.video.materials.f0g"),
                    Text.literal(String.format("%.1f%%", v / 10.0))),
                v -> { Options.materialF0G[i] = v; }),
            body));

        this.body.addEntry(new RadianceSettingsScreen.SliderEntry(
            new ResettableSliderWidget(0, 0, 150, 20,
                0, 1000, Options.materialF0B[i], block.getDefaultF0B(),
                v -> getGenericValueText(
                    Text.translatable("options.video.materials.f0b"),
                    Text.literal(String.format("%.1f%%", v / 10.0))),
                v -> { Options.materialF0B[i] = v; }),
            body));

        // === Metal Presets ===
        this.body.addEntry(new CategoryVideoOptionEntry(
            Text.translatable("options.video.category.materials.presets"), body));

        MetalPreset[] presets = MetalPreset.values();
        if (currentPresetIndex >= presets.length) currentPresetIndex = 0;

        // Preset selector slider — shows preset name + F0 summary
        ResettableSliderWidget presetSelector = new ResettableSliderWidget(0, 0, 150, 20,
            0, presets.length - 1, currentPresetIndex, 0,
            v -> {
                MetalPreset p = MetalPreset.values()[v];
                return Text.literal(p.getDisplayName()
                    + String.format("  R:%.0f G:%.0f B:%.0f  %d%%",
                        p.getF0R() / 10.0, p.getF0G() / 10.0, p.getF0B() / 10.0,
                        p.getRoughness()));
            },
            v -> { currentPresetIndex = v; });
        this.body.addEntry(new RadianceSettingsScreen.SliderEntry(presetSelector, body));

        // Apply button — loads the selected preset F0/roughness/metallic into this block
        final int blockIdx = i;
        ButtonWidget loadPresetBtn = ButtonWidget.builder(
            Text.translatable("options.video.materials.loadPreset"),
            btn -> {
                MetalPreset p = MetalPreset.values()[currentPresetIndex];
                Options.materialF0R[blockIdx]       = p.getF0R();
                Options.materialF0G[blockIdx]       = p.getF0G();
                Options.materialF0B[blockIdx]       = p.getF0B();
                Options.materialRoughness[blockIdx] = p.getRoughness();
                Options.materialMetallic[blockIdx]  = 1000;
                MinecraftClient.getInstance().setScreen(new MaterialsSettingsScreen(parentScreen));
            })
            .width(150).build();
        this.body.addEntry(new RadianceSettingsScreen.ButtonEntry(loadPresetBtn, body));

        // === Auto-PBR Parameters ===
        this.body.addEntry(new CategoryVideoOptionEntry(
            Text.translatable("options.video.category.materials.autoPBR"), body));

        ResettableSliderWidget roughGamma = new ResettableSliderWidget(
            0, 0, 150, 20,
            10, 200, Options.autoPBRRoughnessGamma, 50,
            v -> getGenericValueText(
                Text.translatable("options.video.autoPBR.roughnessGamma"),
                Text.literal(String.format("%.2f", v / 100.0))),
            v -> { Options.autoPBRRoughnessGamma = v; });

        ResettableSliderWidget roughMin = new ResettableSliderWidget(
            0, 0, 150, 20,
            0, 100, Options.autoPBRRoughnessMin, 30,
            v -> getGenericValueText(
                Text.translatable("options.video.autoPBR.roughnessMin"),
                Text.literal(v + "%")),
            v -> { Options.autoPBRRoughnessMin = v; });

        this.body.addEntry(new RadianceSettingsScreen.TwoColumnSliderEntry(
            roughGamma, roughMin, body));

        ResettableSliderWidget roughMax = new ResettableSliderWidget(
            0, 0, 150, 20,
            0, 100, Options.autoPBRRoughnessMax, 95,
            v -> getGenericValueText(
                Text.translatable("options.video.autoPBR.roughnessMax"),
                Text.literal(v + "%")),
            v -> { Options.autoPBRRoughnessMax = v; });

        ResettableSliderWidget normStr = new ResettableSliderWidget(
            0, 0, 150, 20,
            0, 1000, Options.autoPBRNormalStrength, 250,
            v -> getGenericValueText(
                Text.translatable("options.video.autoPBR.normalStrength"),
                Text.literal(String.format("%.1f", v / 100.0))),
            v -> { Options.autoPBRNormalStrength = v; });

        this.body.addEntry(new RadianceSettingsScreen.TwoColumnSliderEntry(
            roughMax, normStr, body));

        ResettableSliderWidget varWt = new ResettableSliderWidget(
            0, 0, 150, 20,
            0, 100, Options.autoPBRVarianceWeight, 30,
            v -> getGenericValueText(
                Text.translatable("options.video.autoPBR.varianceWeight"),
                Text.literal(v + "%")),
            v -> { Options.autoPBRVarianceWeight = v; });

        ResettableSliderWidget edgeWt = new ResettableSliderWidget(
            0, 0, 150, 20,
            0, 100, Options.autoPBREdgeWeight, 15,
            v -> getGenericValueText(
                Text.translatable("options.video.autoPBR.edgeWeight"),
                Text.literal(v + "%")),
            v -> { Options.autoPBREdgeWeight = v; });

        this.body.addEntry(new RadianceSettingsScreen.TwoColumnSliderEntry(
            varWt, edgeWt, body));
    }

}
