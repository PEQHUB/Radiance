package com.radiance.client.gui;

import static net.minecraft.client.option.GameOptions.getGenericValueText;

import com.radiance.client.option.Options;
import com.radiance.client.util.*;
import java.nio.file.Path;
import net.minecraft.block.Block;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Element;
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
        RadianceTheme.drawBreadcrumb(context, this.textRenderer, "Radiance > Lighting > Materials", parentScreen);

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
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (RadianceTheme.handleBreadcrumbClick(mouseX, mouseY, parentScreen)) return true;
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY)) return true;
        // Propagate right-click drag for precision slider mode (button 1)
        // Minecraft's default only propagates button 0.
        if (button == 1) {
            Element focused = getFocused();
            if (focused != null) return focused.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
        }
        return false;
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
        this.body = this.layout.addBody(
            new WideOptionListWidget(this.client, this.width, this));
        addOptions();
    }

    @Override
    protected void initFooter() {
        DirectionalLayoutWidget footer = DirectionalLayoutWidget.horizontal().spacing(8);
        footer.add(ButtonWidget.builder(
            Text.translatable("radiance.settings.materials.apply"), btn -> applyChanges())
            .width(100).build());
        footer.add(ButtonWidget.builder(
            Text.translatable("radiance.settings.materials.cancel"), btn -> cancelChanges())
            .width(100).build());
        footer.add(ButtonWidget.builder(
            Text.translatable("radiance.materials.exportAll"), btn -> {
                MaterialsPack pack = MaterialsPack.fromCurrentOptions();
                pack.name = "All Materials";
                MaterialFileManager.savePack(pack, "all-materials");
            }).width(100).build());
        footer.add(ButtonWidget.builder(
            Text.translatable("radiance.materials.importPack"), btn -> {
                MinecraftClient.getInstance().setScreen(
                    new MaterialBrowserScreen(this, MaterialBrowserScreen.TAB_PACKS));
            }).width(100).build());
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

        // === Material Actions (Copy/Paste | Export/Browser) ===
        final int currentIdx = i;
        ButtonWidget copyBtn = ButtonWidget.builder(
            Text.translatable("radiance.materials.copy"),
            btn -> {
                MaterialClipboard.copy(currentIdx);
                btn.setMessage(Text.translatable("radiance.materials.copied"));
            }).width(70).build();

        ButtonWidget pasteBtn = ButtonWidget.builder(
            Text.translatable("radiance.materials.paste"),
            btn -> {
                if (MaterialClipboard.paste(currentIdx)) {
                    MinecraftClient.getInstance().setScreen(new MaterialsSettingsScreen(parentScreen));
                }
            }).width(70).build();

        ButtonWidget exportBtn = ButtonWidget.builder(
            Text.translatable("radiance.materials.export"),
            btn -> {
                MaterialData data = MaterialData.fromOptions(currentIdx);
                if (data != null) {
                    Path path = MaterialFileManager.saveMaterial(data, data.blockId);
                    if (path != null) btn.setMessage(Text.literal("Saved!"));
                }
            }).width(70).build();

        ButtonWidget browserBtn = ButtonWidget.builder(
            Text.translatable("radiance.materials.browser"),
            btn -> MinecraftClient.getInstance().setScreen(new MaterialBrowserScreen(this))
        ).width(70).build();

        this.body.addEntry(new RadianceSettingsScreen.TwoColumnOptionEntry(copyBtn, pasteBtn, body));
        this.body.addEntry(new RadianceSettingsScreen.TwoColumnOptionEntry(exportBtn, browserBtn, body));

        // === Material Properties (all 13 in paired rows) ===
        this.body.addEntry(new CategoryVideoOptionEntry(
            Text.translatable("options.video.category.materials.baseProperties"), body));

        // Metallic + Roughness
        ResettableSliderWidget metallic = new ResettableSliderWidget(0, 0, 150, 20,
            0, 1000, Options.materialMetallic[i], block.getDefaultMetallic(),
            v -> getGenericValueText(
                Text.translatable("options.video.materials.metallic"),
                Text.literal(String.format("%.1f%%", v / 10.0))),
            v -> { Options.materialMetallic[i] = v; });
        ResettableSliderWidget roughness = new ResettableSliderWidget(0, 0, 150, 20,
            0, 100, Options.materialRoughness[i], block.getDefaultRoughness(),
            v -> getGenericValueText(
                Text.translatable("options.video.materials.roughness"),
                Text.literal(v + "%")),
            v -> { Options.materialRoughness[i] = v; });
        this.body.addEntry(new RadianceSettingsScreen.TwoColumnSliderEntry(metallic, roughness, body));

        // IOR + Transmission
        ResettableSliderWidget ior = new ResettableSliderWidget(0, 0, 150, 20,
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
            });
        ResettableSliderWidget transmission = new ResettableSliderWidget(0, 0, 150, 20,
            0, 1000, Options.materialTransmission[i], block.getDefaultTransmission(),
            v -> getGenericValueText(
                Text.translatable("options.video.materials.transmission"),
                Text.literal(String.format("%.1f%%", v / 10.0))),
            v -> { Options.materialTransmission[i] = v; });
        this.body.addEntry(new RadianceSettingsScreen.TwoColumnSliderEntry(ior, transmission, body));

        // Subsurface + Anisotropic
        ResettableSliderWidget subsurface = new ResettableSliderWidget(0, 0, 150, 20,
            0, 1000, Options.materialSubsurface[i], block.getDefaultSubsurface(),
            v -> getGenericValueText(
                Text.translatable("options.video.materials.subsurface"),
                Text.literal(String.format("%.1f%%", v / 10.0))),
            v -> { Options.materialSubsurface[i] = v; });
        ResettableSliderWidget anisotropic = new ResettableSliderWidget(0, 0, 150, 20,
            0, 1000, Options.materialAnisotropic[i], block.getDefaultAnisotropic(),
            v -> getGenericValueText(
                Text.translatable("options.video.materials.anisotropic"),
                Text.literal(String.format("%.1f%%", v / 10.0))),
            v -> { Options.materialAnisotropic[i] = v; });
        this.body.addEntry(new RadianceSettingsScreen.TwoColumnSliderEntry(subsurface, anisotropic, body));

        // Sheen Weight + Sheen Tint
        ResettableSliderWidget sheenWeight = new ResettableSliderWidget(0, 0, 150, 20,
            0, 1000, Options.materialSheenWeight[i], block.getDefaultSheenWeight(),
            v -> getGenericValueText(
                Text.translatable("options.video.materials.sheenWeight"),
                Text.literal(String.format("%.1f%%", v / 10.0))),
            v -> { Options.materialSheenWeight[i] = v; });
        ResettableSliderWidget sheenTint = new ResettableSliderWidget(0, 0, 150, 20,
            0, 1000, Options.materialSheenTint[i], block.getDefaultSheenTint(),
            v -> getGenericValueText(
                Text.translatable("options.video.materials.sheenTint"),
                Text.literal(String.format("%.1f%%", v / 10.0))),
            v -> { Options.materialSheenTint[i] = v; });
        this.body.addEntry(new RadianceSettingsScreen.TwoColumnSliderEntry(sheenWeight, sheenTint, body));

        // Coat Weight + Coat Roughness
        ResettableSliderWidget coatWeight = new ResettableSliderWidget(0, 0, 150, 20,
            0, 1000, Options.materialCoatWeight[i], block.getDefaultCoatWeight(),
            v -> getGenericValueText(
                Text.translatable("options.video.materials.coatWeight"),
                Text.literal(String.format("%.1f%%", v / 10.0))),
            v -> { Options.materialCoatWeight[i] = v; });
        ResettableSliderWidget coatRoughness = new ResettableSliderWidget(0, 0, 150, 20,
            0, 100, Options.materialCoatRoughness[i], block.getDefaultCoatRoughness(),
            v -> getGenericValueText(
                Text.translatable("options.video.materials.coatRoughness"),
                Text.literal(v + "%")),
            v -> { Options.materialCoatRoughness[i] = v; });
        this.body.addEntry(new RadianceSettingsScreen.TwoColumnSliderEntry(coatWeight, coatRoughness, body));

        // === Fresnel F0 ===
        this.body.addEntry(new CategoryVideoOptionEntry(
            Text.translatable("options.video.category.materials.fresnelF0"), body));

        // F0 Red + F0 Green
        ResettableSliderWidget f0r = new ResettableSliderWidget(0, 0, 150, 20,
            0, 1000, Options.materialF0R[i], block.getDefaultF0R(),
            v -> getGenericValueText(
                Text.translatable("options.video.materials.f0r"),
                Text.literal(String.format("%.1f%%", v / 10.0))),
            v -> { Options.materialF0R[i] = v; });
        ResettableSliderWidget f0g = new ResettableSliderWidget(0, 0, 150, 20,
            0, 1000, Options.materialF0G[i], block.getDefaultF0G(),
            v -> getGenericValueText(
                Text.translatable("options.video.materials.f0g"),
                Text.literal(String.format("%.1f%%", v / 10.0))),
            v -> { Options.materialF0G[i] = v; });
        this.body.addEntry(new RadianceSettingsScreen.TwoColumnSliderEntry(f0r, f0g, body));

        // F0 Blue (solo)
        ResettableSliderWidget f0b = new ResettableSliderWidget(0, 0, 150, 20,
            0, 1000, Options.materialF0B[i], block.getDefaultF0B(),
            v -> getGenericValueText(
                Text.translatable("options.video.materials.f0b"),
                Text.literal(String.format("%.1f%%", v / 10.0))),
            v -> { Options.materialF0B[i] = v; });
        this.body.addEntry(new RadianceSettingsScreen.TwoColumnSliderEntry(f0b, null, body));

        // === Metal Presets (selector + load in one row) ===
        this.body.addEntry(new CategoryVideoOptionEntry(
            Text.translatable("options.video.category.materials.presets"), body));

        MetalPreset[] presets = MetalPreset.values();
        if (currentPresetIndex >= presets.length) currentPresetIndex = 0;

        ResettableSliderWidget presetSelector = new ResettableSliderWidget(0, 0, 150, 20,
            0, presets.length - 1, currentPresetIndex, 0,
            v -> {
                MetalPreset p = MetalPreset.values()[v];
                return Text.literal(p.getDisplayName());
            },
            v -> { currentPresetIndex = v; });

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
        this.body.addEntry(new RadianceSettingsScreen.TwoColumnOptionEntry(presetSelector, loadPresetBtn, body));

        // === Auto-PBR Parameters ===
        this.body.addEntry(new CategoryVideoOptionEntry(
            Text.translatable("options.video.category.materials.autoPBR"), body));

        ResettableSliderWidget roughGamma = new ResettableSliderWidget(0, 0, 150, 20,
            10, 200, Options.autoPBRRoughnessGamma, 50,
            v -> getGenericValueText(
                Text.translatable("options.video.autoPBR.roughnessGamma"),
                Text.literal(String.format("%.2f", v / 100.0))),
            v -> { Options.autoPBRRoughnessGamma = v; });
        ResettableSliderWidget roughMin = new ResettableSliderWidget(0, 0, 150, 20,
            0, 100, Options.autoPBRRoughnessMin, 30,
            v -> getGenericValueText(
                Text.translatable("options.video.autoPBR.roughnessMin"),
                Text.literal(v + "%")),
            v -> { Options.autoPBRRoughnessMin = v; });
        this.body.addEntry(new RadianceSettingsScreen.TwoColumnSliderEntry(roughGamma, roughMin, body));

        ResettableSliderWidget roughMax = new ResettableSliderWidget(0, 0, 150, 20,
            0, 100, Options.autoPBRRoughnessMax, 95,
            v -> getGenericValueText(
                Text.translatable("options.video.autoPBR.roughnessMax"),
                Text.literal(v + "%")),
            v -> { Options.autoPBRRoughnessMax = v; });
        ResettableSliderWidget normStr = new ResettableSliderWidget(0, 0, 150, 20,
            0, 1000, Options.autoPBRNormalStrength, 250,
            v -> getGenericValueText(
                Text.translatable("options.video.autoPBR.normalStrength"),
                Text.literal(String.format("%.1f", v / 100.0))),
            v -> { Options.autoPBRNormalStrength = v; });
        this.body.addEntry(new RadianceSettingsScreen.TwoColumnSliderEntry(roughMax, normStr, body));

        ResettableSliderWidget varWt = new ResettableSliderWidget(0, 0, 150, 20,
            0, 100, Options.autoPBRVarianceWeight, 30,
            v -> getGenericValueText(
                Text.translatable("options.video.autoPBR.varianceWeight"),
                Text.literal(v + "%")),
            v -> { Options.autoPBRVarianceWeight = v; });
        ResettableSliderWidget edgeWt = new ResettableSliderWidget(0, 0, 150, 20,
            0, 100, Options.autoPBREdgeWeight, 15,
            v -> getGenericValueText(
                Text.translatable("options.video.autoPBR.edgeWeight"),
                Text.literal(v + "%")),
            v -> { Options.autoPBREdgeWeight = v; });
        this.body.addEntry(new RadianceSettingsScreen.TwoColumnSliderEntry(varWt, edgeWt, body));
    }

}
