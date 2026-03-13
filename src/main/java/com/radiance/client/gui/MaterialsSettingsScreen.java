package com.radiance.client.gui;

import static net.minecraft.client.option.GameOptions.getGenericValueText;

import com.radiance.client.option.Options;
import com.radiance.client.util.*;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.block.Block;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.option.GameOptionsScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.DirectionalLayoutWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.option.SimpleOption;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

public class MaterialsSettingsScreen extends GameOptionsScreen {

    private final Screen parentScreen;
    private static int currentBlockIndex = 0;  // persists across screen rebuilds
    private static int currentPresetIndex = 0; // persists across screen rebuilds
    private static String searchQuery = "";    // persists across screen rebuilds
    private static final List<Integer> searchMatches = new ArrayList<>();
    private static int searchMatchIndex = 0;
    private TextFieldWidget searchField;

    /** Set the block index to show when this screen opens (used by Texture Editor child clicks). */
    public static void setCurrentBlockIndex(int index) { currentBlockIndex = index; }

    /** Called after any slider changes to handle parent→child propagation or child override marking. */
    private static void onSliderChanged(int blockOrdinal) {
        MaterialBlock block = MaterialBlock.values()[blockOrdinal];
        if (block.isParent() && !block.getChildren().isEmpty()) {
            Options.propagateParentMaterial(blockOrdinal);
        } else if (!block.isParent()) {
            Options.materialChildOverride[blockOrdinal] = true;
        }
    }

    // Snapshot of all values when screen first opens — used by Cancel
    private static boolean snapshotTaken = false;
    private static final int[] snapF0R = new int[Options.MAX_MATERIALS], snapF0G = new int[Options.MAX_MATERIALS], snapF0B = new int[Options.MAX_MATERIALS];
    private static final int[] snapRoughness = new int[Options.MAX_MATERIALS], snapMetallic = new int[Options.MAX_MATERIALS];
    private static final int[] snapTransmission = new int[Options.MAX_MATERIALS], snapIOR = new int[Options.MAX_MATERIALS];
    private static final int[] snapSubsurface = new int[Options.MAX_MATERIALS], snapAnisotropic = new int[Options.MAX_MATERIALS];
    private static final int[] snapSheenWeight = new int[Options.MAX_MATERIALS], snapSheenTint = new int[Options.MAX_MATERIALS];
    private static final int[] snapCoatWeight = new int[Options.MAX_MATERIALS], snapCoatRoughness = new int[Options.MAX_MATERIALS];
    private static final int[] snapNoiseScale = new int[Options.MAX_MATERIALS], snapNoiseStrength = new int[Options.MAX_MATERIALS], snapNoiseOctaves = new int[Options.MAX_MATERIALS];
    private static final int[] snapChannelR = new int[Options.MAX_MATERIALS], snapChannelG = new int[Options.MAX_MATERIALS], snapChannelB = new int[Options.MAX_MATERIALS];
    private static final int[] snapTextureBlend = new int[Options.MAX_MATERIALS];
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
        System.arraycopy(Options.materialF0R, 0, snapF0R, 0, Options.MAX_MATERIALS);
        System.arraycopy(Options.materialF0G, 0, snapF0G, 0, Options.MAX_MATERIALS);
        System.arraycopy(Options.materialF0B, 0, snapF0B, 0, Options.MAX_MATERIALS);
        System.arraycopy(Options.materialRoughness, 0, snapRoughness, 0, Options.MAX_MATERIALS);
        System.arraycopy(Options.materialMetallic, 0, snapMetallic, 0, Options.MAX_MATERIALS);
        System.arraycopy(Options.materialTransmission, 0, snapTransmission, 0, Options.MAX_MATERIALS);
        System.arraycopy(Options.materialIOR, 0, snapIOR, 0, Options.MAX_MATERIALS);
        System.arraycopy(Options.materialSubsurface, 0, snapSubsurface, 0, Options.MAX_MATERIALS);
        System.arraycopy(Options.materialAnisotropic, 0, snapAnisotropic, 0, Options.MAX_MATERIALS);
        System.arraycopy(Options.materialSheenWeight, 0, snapSheenWeight, 0, Options.MAX_MATERIALS);
        System.arraycopy(Options.materialSheenTint, 0, snapSheenTint, 0, Options.MAX_MATERIALS);
        System.arraycopy(Options.materialCoatWeight, 0, snapCoatWeight, 0, Options.MAX_MATERIALS);
        System.arraycopy(Options.materialCoatRoughness, 0, snapCoatRoughness, 0, Options.MAX_MATERIALS);
        System.arraycopy(Options.materialNoiseScale, 0, snapNoiseScale, 0, Options.MAX_MATERIALS);
        System.arraycopy(Options.materialNoiseStrength, 0, snapNoiseStrength, 0, Options.MAX_MATERIALS);
        System.arraycopy(Options.materialNoiseOctaves, 0, snapNoiseOctaves, 0, Options.MAX_MATERIALS);
        System.arraycopy(Options.materialChannelR, 0, snapChannelR, 0, Options.MAX_MATERIALS);
        System.arraycopy(Options.materialChannelG, 0, snapChannelG, 0, Options.MAX_MATERIALS);
        System.arraycopy(Options.materialChannelB, 0, snapChannelB, 0, Options.MAX_MATERIALS);
        System.arraycopy(Options.materialTextureBlend, 0, snapTextureBlend, 0, Options.MAX_MATERIALS);
        snapAutoPBRRoughnessGamma = Options.autoPBRRoughnessGamma;
        snapAutoPBRRoughnessMin = Options.autoPBRRoughnessMin;
        snapAutoPBRRoughnessMax = Options.autoPBRRoughnessMax;
        snapAutoPBRNormalStrength = Options.autoPBRNormalStrength;
        snapAutoPBRVarianceWeight = Options.autoPBRVarianceWeight;
        snapAutoPBREdgeWeight = Options.autoPBREdgeWeight;
    }

    private static void restoreSnapshot() {
        System.arraycopy(snapF0R, 0, Options.materialF0R, 0, Options.MAX_MATERIALS);
        System.arraycopy(snapF0G, 0, Options.materialF0G, 0, Options.MAX_MATERIALS);
        System.arraycopy(snapF0B, 0, Options.materialF0B, 0, Options.MAX_MATERIALS);
        System.arraycopy(snapRoughness, 0, Options.materialRoughness, 0, Options.MAX_MATERIALS);
        System.arraycopy(snapMetallic, 0, Options.materialMetallic, 0, Options.MAX_MATERIALS);
        System.arraycopy(snapTransmission, 0, Options.materialTransmission, 0, Options.MAX_MATERIALS);
        System.arraycopy(snapIOR, 0, Options.materialIOR, 0, Options.MAX_MATERIALS);
        System.arraycopy(snapSubsurface, 0, Options.materialSubsurface, 0, Options.MAX_MATERIALS);
        System.arraycopy(snapAnisotropic, 0, Options.materialAnisotropic, 0, Options.MAX_MATERIALS);
        System.arraycopy(snapSheenWeight, 0, Options.materialSheenWeight, 0, Options.MAX_MATERIALS);
        System.arraycopy(snapSheenTint, 0, Options.materialSheenTint, 0, Options.MAX_MATERIALS);
        System.arraycopy(snapCoatWeight, 0, Options.materialCoatWeight, 0, Options.MAX_MATERIALS);
        System.arraycopy(snapCoatRoughness, 0, Options.materialCoatRoughness, 0, Options.MAX_MATERIALS);
        System.arraycopy(snapNoiseScale, 0, Options.materialNoiseScale, 0, Options.MAX_MATERIALS);
        System.arraycopy(snapNoiseStrength, 0, Options.materialNoiseStrength, 0, Options.MAX_MATERIALS);
        System.arraycopy(snapNoiseOctaves, 0, Options.materialNoiseOctaves, 0, Options.MAX_MATERIALS);
        System.arraycopy(snapChannelR, 0, Options.materialChannelR, 0, Options.MAX_MATERIALS);
        System.arraycopy(snapChannelG, 0, Options.materialChannelG, 0, Options.MAX_MATERIALS);
        System.arraycopy(snapChannelB, 0, Options.materialChannelB, 0, Options.MAX_MATERIALS);
        System.arraycopy(snapTextureBlend, 0, Options.materialTextureBlend, 0, Options.MAX_MATERIALS);
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
    protected void init() {
        super.init();
        // Shift body down to make room for search field
        this.body.setY(this.body.getY() + 22);
        this.body.setHeight(this.body.getHeight() - 22);

        int fieldW = Math.min(300, this.width - 80);
        int fieldX = (this.width - fieldW) / 2;
        int fieldY = this.body.getY() - 20;
        searchField = new TextFieldWidget(this.textRenderer, fieldX, fieldY, fieldW, 16,
            Text.literal("Search"));
        searchField.setMaxLength(50);
        searchField.setText(searchQuery);
        searchField.setChangedListener(q -> {
            if (!q.equals(searchQuery)) {
                searchQuery = q;
                searchMatchIndex = 0;
                updateSearchMatches();
            }
        });
        addDrawableChild(searchField);
        updateSearchMatches();
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        RadianceTheme.drawBreadcrumb(context, this.textRenderer, "Radiance > Lighting > Texture Editor", parentScreen);

        // Search match preview (to the right of the search field)
        if (searchField != null && !searchQuery.isEmpty()) {
            int infoX = searchField.getX() + searchField.getWidth() + 8;
            int infoY = searchField.getY() + 4;
            if (searchMatches.isEmpty()) {
                context.drawText(this.textRenderer, Text.literal("No matches"),
                    infoX, infoY, 0xFF808080, false);
            } else {
                int matchIdx = searchMatches.get(searchMatchIndex);
                MaterialBlock matchBlock = MaterialBlock.values()[matchIdx];
                String matchName = Text.translatable("options.video.materials." + matchBlock.getId()).getString();
                String info = matchName + " (" + (searchMatchIndex + 1) + "/" + searchMatches.size() + ")";
                int color = matchIdx == currentBlockIndex ? 0xFF80FF80 : 0xFFFFFFFF;
                context.drawText(this.textRenderer, Text.literal(info), infoX, infoY, color, false);
            }
        }

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
        if (searchField != null && searchField.isFocused()) {
            if (keyCode == GLFW.GLFW_KEY_ENTER) {
                commitSearch();
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_TAB && !searchMatches.isEmpty()) {
                // Tab = next match, Shift+Tab = previous match
                if ((modifiers & GLFW.GLFW_MOD_SHIFT) != 0) {
                    searchMatchIndex = (searchMatchIndex - 1 + searchMatches.size()) % searchMatches.size();
                } else {
                    searchMatchIndex = (searchMatchIndex + 1) % searchMatches.size();
                }
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_DOWN && !searchMatches.isEmpty()) {
                searchMatchIndex = (searchMatchIndex + 1) % searchMatches.size();
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_UP && !searchMatches.isEmpty()) {
                searchMatchIndex = (searchMatchIndex - 1 + searchMatches.size()) % searchMatches.size();
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                if (!searchQuery.isEmpty()) {
                    searchQuery = "";
                    searchField.setText("");
                    searchMatches.clear();
                    searchMatchIndex = 0;
                } else {
                    searchField.setFocused(false);
                }
                return true;
            }
        }
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

    private void commitSearch() {
        if (searchMatches.isEmpty()) return;
        int match = searchMatches.get(searchMatchIndex);
        // Advance index for next Enter press
        searchMatchIndex = (searchMatchIndex + 1) % searchMatches.size();
        if (match != currentBlockIndex) {
            currentBlockIndex = match;
            MinecraftClient.getInstance().setScreen(new MaterialsSettingsScreen(parentScreen));
        }
    }

    private static void updateSearchMatches() {
        searchMatches.clear();
        if (searchQuery == null || searchQuery.isEmpty()) return;
        String lower = searchQuery.toLowerCase();
        MaterialBlock[] blocks = MaterialBlock.values();
        for (int j = 0; j < blocks.length; j++) {
            String name = Text.translatable("options.video.materials." + blocks[j].getId()).getString().toLowerCase();
            String id = blocks[j].getId().toLowerCase();
            if (name.contains(lower) || id.contains(lower)) {
                searchMatches.add(j);
            }
        }
        if (searchMatchIndex >= searchMatches.size()) searchMatchIndex = 0;
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

        // === Parent/Child indicator ===
        if (!block.isParent()) {
            MaterialBlock parentBlock = block.getParentMaterial();
            String parentName = Text.translatable("options.video.materials." + parentBlock.getId()).getString();
            ButtonWidget resetParentBtn = ButtonWidget.builder(
                Text.translatable("radiance.texture_editor.reset_to_parent"),
                btn -> {
                    int pi = parentBlock.ordinal();
                    Options.materialF0R[i] = Options.materialF0R[pi];
                    Options.materialF0G[i] = Options.materialF0G[pi];
                    Options.materialF0B[i] = Options.materialF0B[pi];
                    Options.materialRoughness[i] = Options.materialRoughness[pi];
                    Options.materialMetallic[i] = Options.materialMetallic[pi];
                    Options.materialTransmission[i] = Options.materialTransmission[pi];
                    Options.materialIOR[i] = Options.materialIOR[pi];
                    Options.materialSubsurface[i] = Options.materialSubsurface[pi];
                    Options.materialAnisotropic[i] = Options.materialAnisotropic[pi];
                    Options.materialSheenWeight[i] = Options.materialSheenWeight[pi];
                    Options.materialSheenTint[i] = Options.materialSheenTint[pi];
                    Options.materialCoatWeight[i] = Options.materialCoatWeight[pi];
                    Options.materialCoatRoughness[i] = Options.materialCoatRoughness[pi];
                    Options.materialChannelR[i] = Options.materialChannelR[pi];
                    Options.materialChannelG[i] = Options.materialChannelG[pi];
                    Options.materialChannelB[i] = Options.materialChannelB[pi];
                    Options.materialTextureBlend[i] = Options.materialTextureBlend[pi];
                    Options.materialChildOverride[i] = false;
                    MinecraftClient.getInstance().setScreen(new MaterialsSettingsScreen(parentScreen));
                }).width(150).build();
            // Show as a label + button row
            ButtonWidget variantLabel = ButtonWidget.builder(
                Text.literal("Variant of: " + parentName),
                btn -> {
                    // Navigate to parent
                    currentBlockIndex = parentBlock.ordinal();
                    MinecraftClient.getInstance().setScreen(new MaterialsSettingsScreen(parentScreen));
                }).width(150).build();
            this.body.addEntry(new RadianceSettingsScreen.TwoColumnOptionEntry(variantLabel, resetParentBtn, body));
        }

        // === Material Properties (all 13 in paired rows) ===
        this.body.addEntry(new CategoryVideoOptionEntry(
            Text.translatable("options.video.category.materials.baseProperties"), body));

        // Metallic + Roughness
        ResettableSliderWidget metallic = new ResettableSliderWidget(0, 0, 150, 20,
            0, 1000, Options.materialMetallic[i], block.getDefaultMetallic(),
            v -> getGenericValueText(
                Text.translatable("options.video.materials.metallic"),
                Text.literal(String.format("%.1f%%", v / 10.0))),
            v -> { Options.materialMetallic[i] = v; onSliderChanged(i); });
        ResettableSliderWidget roughness = new ResettableSliderWidget(0, 0, 150, 20,
            0, 100, Options.materialRoughness[i], block.getDefaultRoughness(),
            v -> getGenericValueText(
                Text.translatable("options.video.materials.roughness"),
                Text.literal(v + "%")),
            v -> { Options.materialRoughness[i] = v; onSliderChanged(i); });
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
                onSliderChanged(i);
            });
        ResettableSliderWidget transmission = new ResettableSliderWidget(0, 0, 150, 20,
            0, 1000, Options.materialTransmission[i], block.getDefaultTransmission(),
            v -> getGenericValueText(
                Text.translatable("options.video.materials.transmission"),
                Text.literal(String.format("%.1f%%", v / 10.0))),
            v -> { Options.materialTransmission[i] = v; onSliderChanged(i); });
        this.body.addEntry(new RadianceSettingsScreen.TwoColumnSliderEntry(ior, transmission, body));

        // Subsurface + Anisotropic
        ResettableSliderWidget subsurface = new ResettableSliderWidget(0, 0, 150, 20,
            0, 1000, Options.materialSubsurface[i], block.getDefaultSubsurface(),
            v -> getGenericValueText(
                Text.translatable("options.video.materials.subsurface"),
                Text.literal(String.format("%.1f%%", v / 10.0))),
            v -> { Options.materialSubsurface[i] = v; onSliderChanged(i); });
        ResettableSliderWidget anisotropic = new ResettableSliderWidget(0, 0, 150, 20,
            0, 1000, Options.materialAnisotropic[i], block.getDefaultAnisotropic(),
            v -> getGenericValueText(
                Text.translatable("options.video.materials.anisotropic"),
                Text.literal(String.format("%.1f%%", v / 10.0))),
            v -> { Options.materialAnisotropic[i] = v; onSliderChanged(i); });
        this.body.addEntry(new RadianceSettingsScreen.TwoColumnSliderEntry(subsurface, anisotropic, body));

        // Sheen Weight + Tint + Coat Weight + Coat Roughness (4-column)
        ResettableSliderWidget sheenWeight = new ResettableSliderWidget(0, 0, 100, 20,
            0, 1000, Options.materialSheenWeight[i], block.getDefaultSheenWeight(),
            v -> getGenericValueText(
                Text.translatable("options.video.materials.sheenWeight"),
                Text.literal(String.format("%.1f%%", v / 10.0))),
            v -> { Options.materialSheenWeight[i] = v; onSliderChanged(i); });
        ResettableSliderWidget sheenTint = new ResettableSliderWidget(0, 0, 100, 20,
            0, 1000, Options.materialSheenTint[i], block.getDefaultSheenTint(),
            v -> getGenericValueText(
                Text.translatable("options.video.materials.sheenTint"),
                Text.literal(String.format("%.1f%%", v / 10.0))),
            v -> { Options.materialSheenTint[i] = v; onSliderChanged(i); });
        ResettableSliderWidget coatWeight = new ResettableSliderWidget(0, 0, 100, 20,
            0, 1000, Options.materialCoatWeight[i], block.getDefaultCoatWeight(),
            v -> getGenericValueText(
                Text.translatable("options.video.materials.coatWeight"),
                Text.literal(String.format("%.1f%%", v / 10.0))),
            v -> { Options.materialCoatWeight[i] = v; onSliderChanged(i); });
        ResettableSliderWidget coatRoughness = new ResettableSliderWidget(0, 0, 100, 20,
            0, 100, Options.materialCoatRoughness[i], block.getDefaultCoatRoughness(),
            v -> getGenericValueText(
                Text.translatable("options.video.materials.coatRoughness"),
                Text.literal(v + "%")),
            v -> { Options.materialCoatRoughness[i] = v; onSliderChanged(i); });
        this.body.addEntry(new RadianceSettingsScreen.FourColumnSliderEntry(sheenWeight, sheenTint, coatWeight, coatRoughness, body));

        // === Texture Roughness (channel routing, 4-column) ===
        this.body.addEntry(new CategoryVideoOptionEntry(
            Text.translatable("options.video.category.materials.textureRoughness"), body));

        ResettableSliderWidget channelR = new ResettableSliderWidget(0, 0, 100, 20,
            0, 1000, Options.materialChannelR[i], 213,
            v -> getGenericValueText(
                Text.translatable("options.video.materials.channelR"),
                Text.literal(String.format("%.1f%%", v / 10.0))),
            v -> { Options.materialChannelR[i] = v; onSliderChanged(i); });
        ResettableSliderWidget channelG = new ResettableSliderWidget(0, 0, 100, 20,
            0, 1000, Options.materialChannelG[i], 715,
            v -> getGenericValueText(
                Text.translatable("options.video.materials.channelG"),
                Text.literal(String.format("%.1f%%", v / 10.0))),
            v -> { Options.materialChannelG[i] = v; onSliderChanged(i); });
        ResettableSliderWidget channelB = new ResettableSliderWidget(0, 0, 100, 20,
            0, 1000, Options.materialChannelB[i], 72,
            v -> getGenericValueText(
                Text.translatable("options.video.materials.channelB"),
                Text.literal(String.format("%.1f%%", v / 10.0))),
            v -> { Options.materialChannelB[i] = v; onSliderChanged(i); });
        ResettableSliderWidget textureBlend = new ResettableSliderWidget(0, 0, 100, 20,
            0, 100, Options.materialTextureBlend[i], 30,
            v -> getGenericValueText(
                Text.translatable("options.video.materials.textureBlend"),
                Text.literal(v + "%")),
            v -> { Options.materialTextureBlend[i] = v; onSliderChanged(i); });
        this.body.addEntry(new RadianceSettingsScreen.FourColumnSliderEntry(channelR, channelG, channelB, textureBlend, body));

        // === Procedural Noise (metals, 3 sliders in one row) ===
        this.body.addEntry(new CategoryVideoOptionEntry(
            Text.translatable("options.video.category.materials.noise"), body));

        ResettableSliderWidget noiseScale = new ResettableSliderWidget(0, 0, 100, 20,
            1, 200, Options.materialNoiseScale[i], 50,
            v -> getGenericValueText(
                Text.translatable("options.video.materials.noiseScale"),
                Text.literal(String.format("%.1f", v / 10.0))),
            v -> { Options.materialNoiseScale[i] = v; onSliderChanged(i); });
        ResettableSliderWidget noiseStrength = new ResettableSliderWidget(0, 0, 100, 20,
            0, 100, Options.materialNoiseStrength[i], 0,
            v -> getGenericValueText(
                Text.translatable("options.video.materials.noiseStrength"),
                Text.literal(v + "%")),
            v -> { Options.materialNoiseStrength[i] = v; onSliderChanged(i); });
        ResettableSliderWidget noiseOctaves = new ResettableSliderWidget(0, 0, 100, 20,
            1, 4, Options.materialNoiseOctaves[i], 2,
            v -> getGenericValueText(
                Text.translatable("options.video.materials.noiseOctaves"),
                Text.literal(String.valueOf(v))),
            v -> { Options.materialNoiseOctaves[i] = v; onSliderChanged(i); });
        this.body.addEntry(new RadianceSettingsScreen.FourColumnSliderEntry(noiseScale, noiseStrength, noiseOctaves, null, body));

        // === Fresnel F0 (3 sliders in one row) ===
        this.body.addEntry(new CategoryVideoOptionEntry(
            Text.translatable("options.video.category.materials.fresnelF0"), body));

        ResettableSliderWidget f0r = new ResettableSliderWidget(0, 0, 100, 20,
            0, 1000, Options.materialF0R[i], block.getDefaultF0R(),
            v -> getGenericValueText(
                Text.translatable("options.video.materials.f0r"),
                Text.literal(String.format("%.1f%%", v / 10.0))),
            v -> { Options.materialF0R[i] = v; onSliderChanged(i); });
        ResettableSliderWidget f0g = new ResettableSliderWidget(0, 0, 100, 20,
            0, 1000, Options.materialF0G[i], block.getDefaultF0G(),
            v -> getGenericValueText(
                Text.translatable("options.video.materials.f0g"),
                Text.literal(String.format("%.1f%%", v / 10.0))),
            v -> { Options.materialF0G[i] = v; onSliderChanged(i); });
        ResettableSliderWidget f0b = new ResettableSliderWidget(0, 0, 100, 20,
            0, 1000, Options.materialF0B[i], block.getDefaultF0B(),
            v -> getGenericValueText(
                Text.translatable("options.video.materials.f0b"),
                Text.literal(String.format("%.1f%%", v / 10.0))),
            v -> { Options.materialF0B[i] = v; onSliderChanged(i); });
        this.body.addEntry(new RadianceSettingsScreen.FourColumnSliderEntry(f0r, f0g, f0b, null, body));

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
