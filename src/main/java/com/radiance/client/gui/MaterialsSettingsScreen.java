package com.radiance.client.gui;

import static net.minecraft.client.option.GameOptions.getGenericValueText;

import com.radiance.client.option.Options;
import com.radiance.client.texture.AutoPBRGenerator;
import com.radiance.client.texture.LiveNormalReuploader;
import com.radiance.client.texture.NoisePreviewGenerator;
import com.radiance.client.util.*;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.block.Block;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.resource.Resource;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

public class MaterialsSettingsScreen extends Screen {

    private final Screen parentScreen;
    private static int currentBlockIndex = 0;  // persists across screen rebuilds
    private static int currentPresetIndex = 0; // persists across screen rebuilds
    private static String searchQuery = "";    // persists across screen rebuilds
    private static final List<Integer> searchMatches = new ArrayList<>();
    private static int searchMatchIndex = 0;
    private TextFieldWidget searchField;
    private NoiseTypeDropdownWidget noiseDropdown;

    // Auto-PBR preview textures — STATIC to survive screen rebuilds and avoid Vulkan destroy/recreate crashes
    private NativeImage sourceAlbedo;
    private static final Identifier PREVIEW_ALBEDO_ID = Identifier.of("radiance", "autopbr_preview/albedo");
    private static final Identifier PREVIEW_NORMAL_ID = Identifier.of("radiance", "autopbr_preview/normal");
    private static final Identifier PREVIEW_ROUGHNESS_ID = Identifier.of("radiance", "autopbr_preview/roughness");
    private static final Identifier PREVIEW_HEIGHT_ID = Identifier.of("radiance", "autopbr_preview/height");
    private static NativeImageBackedTexture previewAlbedoTex;
    private static NativeImageBackedTexture previewNormalTex;
    private static NativeImageBackedTexture previewRoughTex;
    private static NativeImageBackedTexture previewHeightTex;
    private static final Identifier PREVIEW_NOISE_ID = Identifier.of("radiance", "autopbr_preview/noise");
    private static NativeImageBackedTexture previewNoiseTex;
    private static boolean previewsRegistered = false;
    // 0=Albedo, 1=Normal, 2=Roughness, 3=Height
    private static int selectedPreviewMask = 2;

    // Preview layout (computed in init, used in render + click)
    private int previewStripY;  // Y of the bottom preview area

    // Layout constants
    private static final int MARGIN = 10;
    private static final int COL_GAP = 10;
    private static final int HEADER_H = 48;
    private static final int FOOTER_H = 24;
    private static final int ROW_H = 22;
    private static final int WIDGET_H = 20;

    // Computed layout
    private int colW;
    private int leftX, centerX, rightX;
    private int bodyTop, bodyBot;

    // Category headers to render
    private record Header(int x, int y, int w, String text) {}
    private final List<Header> headers = new ArrayList<>();

    // Warning texts to render
    private record Warning(int x, int y, int w, String text) {}
    private final List<Warning> warnings = new ArrayList<>();

    /** Set the block index to show when this screen opens (used by Texture Editor child clicks). */
    public static void setCurrentBlockIndex(int index) { currentBlockIndex = index; }

    /** Set the current block by material ordinal (finds the index in the combined list). */
    public static void setCurrentOrdinal(int ordinal) {
        currentBlockIndex = MaterialBlock.indexOfOrdinal(ordinal);
    }

    /** Called after any slider changes to handle parent->child propagation or child override marking. */
    private static void onSliderChanged(int blockOrdinal) {
        if (blockOrdinal >= 0 && blockOrdinal < MaterialBlock.COUNT) {
            MaterialBlock block = MaterialBlock.values()[blockOrdinal];
            if (block.isParent() && !block.getChildren().isEmpty()) {
                Options.propagateParentMaterial(blockOrdinal);
            } else if (!block.isParent()) {
                Options.materialChildOverride[blockOrdinal] = true;
            }
        }
        Options.markMaterialDirty();
        com.radiance.client.material.MaterialRegistry.markDirty();
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
    private static final int[] snapNoiseType = new int[Options.MAX_MATERIALS], snapNoiseSeed = new int[Options.MAX_MATERIALS];
    private static final int[] snapGamutBoost = new int[Options.MAX_MATERIALS];
    private static final int[] snapGamutBoostMode = new int[Options.MAX_MATERIALS];
    private static final int[] snapPomDepth = new int[Options.MAX_MATERIALS];
    private static final int[] snapAutoPBRRoughnessMin = new int[Options.MAX_MATERIALS];
    private static final int[] snapAutoPBRRoughnessMax = new int[Options.MAX_MATERIALS];
    private static final int[] snapPercentileCenter = new int[Options.MAX_MATERIALS];
    private static final int[] snapPercentileSpread = new int[Options.MAX_MATERIALS];
    private static final int[] snapPerBlockAutoPBRHtGamma = new int[Options.MAX_MATERIALS];
    private static final int[] snapPerBlockAutoPBRFlags = new int[Options.MAX_MATERIALS];
    private static final boolean[] snapAutoPBR = new boolean[Options.MAX_MATERIALS];
    private static final boolean[] snapChildOverride = new boolean[Options.MAX_MATERIALS];
    private static boolean snapAutoPBREnabled;

    public MaterialsSettingsScreen(Screen parent) {
        super(Text.translatable("radiance.settings.materials.title"));
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
        System.arraycopy(Options.materialNoiseType, 0, snapNoiseType, 0, Options.MAX_MATERIALS);
        System.arraycopy(Options.materialNoiseSeed, 0, snapNoiseSeed, 0, Options.MAX_MATERIALS);
        System.arraycopy(Options.materialGamutBoost, 0, snapGamutBoost, 0, Options.MAX_MATERIALS);
        System.arraycopy(Options.materialGamutBoostMode, 0, snapGamutBoostMode, 0, Options.MAX_MATERIALS);
        System.arraycopy(Options.materialPomDepth, 0, snapPomDepth, 0, Options.MAX_MATERIALS);
        System.arraycopy(Options.materialAutoPBRRoughnessMin, 0, snapAutoPBRRoughnessMin, 0, Options.MAX_MATERIALS);
        System.arraycopy(Options.materialAutoPBRRoughnessMax, 0, snapAutoPBRRoughnessMax, 0, Options.MAX_MATERIALS);
        System.arraycopy(Options.materialPercentileCenter, 0, snapPercentileCenter, 0, Options.MAX_MATERIALS);
        System.arraycopy(Options.materialPercentileSpread, 0, snapPercentileSpread, 0, Options.MAX_MATERIALS);
        System.arraycopy(Options.materialAutoPBRHeightGamma, 0, snapPerBlockAutoPBRHtGamma, 0, Options.MAX_MATERIALS);
        System.arraycopy(Options.materialAutoPBRFlags, 0, snapPerBlockAutoPBRFlags, 0, Options.MAX_MATERIALS);
        System.arraycopy(Options.materialAutoPBR, 0, snapAutoPBR, 0, Options.MAX_MATERIALS);
        System.arraycopy(Options.materialChildOverride, 0, snapChildOverride, 0, Options.MAX_MATERIALS);
        snapAutoPBREnabled = Options.autoPBREnabled;
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
        System.arraycopy(snapNoiseType, 0, Options.materialNoiseType, 0, Options.MAX_MATERIALS);
        System.arraycopy(snapNoiseSeed, 0, Options.materialNoiseSeed, 0, Options.MAX_MATERIALS);
        System.arraycopy(snapGamutBoost, 0, Options.materialGamutBoost, 0, Options.MAX_MATERIALS);
        System.arraycopy(snapGamutBoostMode, 0, Options.materialGamutBoostMode, 0, Options.MAX_MATERIALS);
        System.arraycopy(snapPomDepth, 0, Options.materialPomDepth, 0, Options.MAX_MATERIALS);
        System.arraycopy(snapAutoPBRRoughnessMin, 0, Options.materialAutoPBRRoughnessMin, 0, Options.MAX_MATERIALS);
        System.arraycopy(snapAutoPBRRoughnessMax, 0, Options.materialAutoPBRRoughnessMax, 0, Options.MAX_MATERIALS);
        System.arraycopy(snapPercentileCenter, 0, Options.materialPercentileCenter, 0, Options.MAX_MATERIALS);
        System.arraycopy(snapPercentileSpread, 0, Options.materialPercentileSpread, 0, Options.MAX_MATERIALS);
        System.arraycopy(snapPerBlockAutoPBRHtGamma, 0, Options.materialAutoPBRHeightGamma, 0, Options.MAX_MATERIALS);
        System.arraycopy(snapPerBlockAutoPBRFlags, 0, Options.materialAutoPBRFlags, 0, Options.MAX_MATERIALS);
        System.arraycopy(snapAutoPBR, 0, Options.materialAutoPBR, 0, Options.MAX_MATERIALS);
        System.arraycopy(snapChildOverride, 0, Options.materialChildOverride, 0, Options.MAX_MATERIALS);
        Options.autoPBREnabled = snapAutoPBREnabled;
        Options.markMaterialDirty();
        com.radiance.client.material.MaterialRegistry.markDirty();
    }

    private boolean autoPBRParamsChanged() {
        if (Options.autoPBREnabled != snapAutoPBREnabled) return true;
        for (int j = 0; j < Options.MAX_MATERIALS; j++) {
            if (Options.materialAutoPBR[j] != snapAutoPBR[j]) return true;
            if (Options.materialAutoPBRRoughnessMin[j] != snapAutoPBRRoughnessMin[j]) return true;
            if (Options.materialAutoPBRRoughnessMax[j] != snapAutoPBRRoughnessMax[j]) return true;
            if (Options.materialPercentileCenter[j] != snapPercentileCenter[j]) return true;
            if (Options.materialPercentileSpread[j] != snapPercentileSpread[j]) return true;
            if (Options.materialAutoPBRHeightGamma[j] != snapPerBlockAutoPBRHtGamma[j]) return true;
            if (Options.materialAutoPBRFlags[j] != snapPerBlockAutoPBRFlags[j]) return true;
        }
        return false;
    }

    private void applyChanges() {
        snapshotTaken = false;
        Options.overwriteConfig();
        this.client.setScreen(this.parentScreen);
    }

    private void cancelChanges() {
        restoreSnapshot();
        snapshotTaken = false;
        Options.overwriteConfig();
        this.client.setScreen(this.parentScreen);
    }

    // ──────────────────────────────────────────────────────────────
    //  Layout helpers
    // ──────────────────────────────────────────────────────────────

    /** Place a single widget spanning the full column width. Returns next Y. */
    private int addSingle(int x, int y, int cw, ClickableWidget w) {
        w.setX(x); w.setY(y); w.setWidth(cw);
        addDrawableChild(w);
        return y + ROW_H;
    }

    /** Place two widgets side by side in a column. Returns next Y. */
    private int addPair(int x, int y, int cw, ClickableWidget left, ClickableWidget right) {
        int hw = (cw - 4) / 2;
        if (left != null) { left.setX(x); left.setY(y); left.setWidth(hw); addDrawableChild(left); }
        if (right != null) { right.setX(x + hw + 4); right.setY(y); right.setWidth(hw); addDrawableChild(right); }
        return y + ROW_H;
    }

    /** Place four widgets in a column. Returns next Y. */
    private int addQuad(int x, int y, int cw, ClickableWidget a, ClickableWidget b, ClickableWidget c, ClickableWidget d) {
        int qw = (cw - 12) / 4;
        if (a != null) { a.setX(x); a.setY(y); a.setWidth(qw); addDrawableChild(a); }
        if (b != null) { b.setX(x + qw + 4); b.setY(y); b.setWidth(qw); addDrawableChild(b); }
        if (c != null) { c.setX(x + 2 * (qw + 4)); c.setY(y); c.setWidth(qw); addDrawableChild(c); }
        if (d != null) { d.setX(x + 3 * (qw + 4)); d.setY(y); d.setWidth(qw); addDrawableChild(d); }
        return y + ROW_H;
    }

    /** Record a category header for painting in render(). Returns next Y. */
    private int addHeader(int x, int y, int cw, String text) {
        headers.add(new Header(x, y, cw, text));
        return y + ROW_H;
    }

    /** Record a warning for painting in render(). Returns next Y. */
    private int addWarning(int x, int y, int cw, String text) {
        warnings.add(new Warning(x, y, cw, text));
        return y + ROW_H;
    }

    /** Create a themed toggle button matching RadianceTheme style. */
    private ButtonWidget makeToggle(String label, boolean initial, java.util.function.Consumer<Boolean> onChange) {
        final boolean[] state = {initial};
        ButtonWidget btn = ButtonWidget.builder(Text.literal(label + ": " + (initial ? "ON" : "OFF")), b -> {
            state[0] = !state[0];
            b.setMessage(Text.literal(label + ": " + (state[0] ? "ON" : "OFF")));
            onChange.accept(state[0]);
        }).dimensions(0, 0, 100, WIDGET_H).build();
        return btn;
    }

    /** Render a toggle-style button with teal ON / dim OFF. Called per-frame from render(). */
    private void renderToggleOverlay(DrawContext context, ButtonWidget btn, boolean isOn) {
        int x = btn.getX(), y = btn.getY(), w = btn.getWidth(), h = btn.getHeight();
        int bg = isOn ? RadianceTheme.scaleAlpha(0x2AB5A0, 0.25f) : RadianceTheme.scaleAlpha(0x1A1A1A, 0.6f);
        context.fill(x, y, x + w, y + h, bg);
        int border = isOn ? RadianceTheme.scaleAlpha(0x2AB5A0, 0.6f) : RadianceTheme.scaleAlpha(0x333333, 0.4f);
        context.drawBorder(x, y, w, h, border);
    }

    // ──────────────────────────────────────────────────────────────
    //  init()
    // ──────────────────────────────────────────────────────────────

    @Override
    protected void init() {
        applyRadianceScale();
        NoiseTypeDropdownWidget.clearInstances();
        headers.clear();
        warnings.clear();

        // Compute layout
        colW = (width - 2 * MARGIN - 2 * COL_GAP) / 3;
        leftX = MARGIN;
        centerX = leftX + colW + COL_GAP;
        rightX = centerX + colW + COL_GAP;
        bodyTop = HEADER_H;
        bodyBot = height - FOOTER_H;

        java.util.List<Integer> allOrdinals = MaterialBlock.getUniqueOrdinals();
        if (currentBlockIndex >= allOrdinals.size()) currentBlockIndex = 0;
        int i = allOrdinals.get(currentBlockIndex);
        final int idx = i;
        // Nullable — only set for hand-tuned enum entries (ordinal < COUNT)
        MaterialBlock block = (i < MaterialBlock.COUNT) ? MaterialBlock.values()[i] : null;
        // Helper: get enum default or fallback for dynamic blocks
        java.util.function.Function<java.util.function.ToIntFunction<MaterialBlock>, Integer> def =
            getter -> block != null ? getter.applyAsInt(block) : 0;

        // Load source albedo for Auto-PBR preview
        loadSourceAlbedo(MaterialBlock.getIdForOrdinal(i));
        regeneratePreview();

        boolean autoPBRActive = Options.autoPBREnabled || Options.materialAutoPBR[i];

        // ════════════════════════════════════════════════════════
        //  HEADER (full width, y=2..46)
        // ════════════════════════════════════════════════════════

        // Row 1 (y=2): Block selector (left ~60%) + search field (right ~35%)
        int selectorW = (int)(width * 0.58) - MARGIN;
        final int totalBlocks = allOrdinals.size();
        ResettableSliderWidget blockSelector = new ResettableSliderWidget(leftX, 2, selectorW, WIDGET_H,
            0, totalBlocks - 1, currentBlockIndex, 0,
            v -> {
                int ord = MaterialBlock.getUniqueOrdinals().get(v);
                String name = MaterialBlock.getDisplayNameForOrdinal(ord);
                return Text.literal(name + " (" + (v + 1) + "/" + totalBlocks + ")");
            },
            v -> { currentBlockIndex = v; });
        blockSelector.setOnRelease(() -> rebuildSelf());
        addDrawableChild(blockSelector);

        int searchW = width - leftX - selectorW - 8 - MARGIN;
        searchField = new TextFieldWidget(this.textRenderer, leftX + selectorW + 8, 2, searchW, WIDGET_H,
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
        searchField.setFocused(true);
        setFocused(searchField);
        updateSearchMatches();

        // Row 2 (y=24): Action buttons — 3 groups: [Block actions] [Preset] [File actions]
        int btnW = 56;
        int btnGap = 3;
        int bx = leftX;

        // Group 1: Block actions
        ButtonWidget overridesBtn = makeToggle("Overrides", Options.materialOverridesEnabled, value -> {
            Options.materialOverridesEnabled = value;
            Options.markMaterialDirty();
            com.radiance.client.material.MaterialRegistry.markDirty();
            Options.overwriteConfig();
        });
        overridesBtn.setX(bx); overridesBtn.setY(24); overridesBtn.setWidth(72);
        addDrawableChild(overridesBtn);
        bx += 72 + btnGap;

        for (var entry : new Object[][]{
            {"Reset", (Runnable) () -> { if (block != null) { MaterialData defaults = MaterialData.fromBlock(block); if (defaults != null) { defaults.applyToOptions(idx); rebuildSelf(); } } }},
            {"Copy",  (Runnable) () -> MaterialClipboard.copy(idx)},
            {"Paste", (Runnable) () -> { if (MaterialClipboard.paste(idx)) rebuildSelf(); }}
        }) {
            String label = (String) entry[0];
            Runnable action = (Runnable) entry[1];
            ButtonWidget btn = ButtonWidget.builder(Text.literal(label), b -> action.run())
                .dimensions(bx, 24, btnW, WIDGET_H).build();
            addDrawableChild(btn);
            bx += btnW + btnGap;
        }

        bx += 6; // gap between groups

        // Group 2: Preset
        MetalPreset[] presets = MetalPreset.values();
        if (currentPresetIndex >= presets.length) currentPresetIndex = 0;
        int presetSliderW = 130;
        ResettableSliderWidget presetSelector = new ResettableSliderWidget(bx, 24, presetSliderW, WIDGET_H,
            0, presets.length - 1, currentPresetIndex, 0,
            v -> Text.literal("Preset: " + MetalPreset.values()[v].getDisplayName()),
            v -> { currentPresetIndex = v; });
        addDrawableChild(presetSelector);
        bx += presetSliderW + btnGap;

        ButtonWidget loadPresetBtn = ButtonWidget.builder(Text.literal("Apply"), btn -> {
            MetalPreset p = MetalPreset.values()[currentPresetIndex];
            Options.materialF0R[idx] = p.getF0R(); Options.materialF0G[idx] = p.getF0G(); Options.materialF0B[idx] = p.getF0B();
            Options.materialRoughness[idx] = p.getRoughness(); Options.materialMetallic[idx] = 1000;
            Options.markMaterialDirty();
            com.radiance.client.material.MaterialRegistry.markDirty();
            rebuildSelf();
        }).dimensions(bx, 24, btnW, WIDGET_H).build();
        addDrawableChild(loadPresetBtn);

        // Group 3: File actions (right-aligned)
        int fileX = width - MARGIN - 3 * (btnW + btnGap) + btnGap;
        ButtonWidget saveBtn = ButtonWidget.builder(Text.literal("Save"), btn -> applyChanges())
            .dimensions(fileX, 24, btnW, WIDGET_H).build();
        addDrawableChild(saveBtn);
        ButtonWidget cancelBtn = ButtonWidget.builder(Text.literal("Cancel"), btn -> cancelChanges())
            .dimensions(fileX + btnW + btnGap, 24, btnW + 8, WIDGET_H).build();
        addDrawableChild(cancelBtn);
        ButtonWidget exportBtn = ButtonWidget.builder(Text.literal("Export"), btn -> {
            MaterialsPack pack = MaterialsPack.fromCurrentOptions();
            pack.name = "All Materials";
            MaterialFileManager.savePack(pack, "all-materials");
        }).dimensions(fileX + 2 * (btnW + btnGap) + 8, 24, btnW + 4, WIDGET_H).build();
        addDrawableChild(exportBtn);

        // ════════════════════════════════════════════════════════
        //  LEFT COLUMN: Core Material
        // ════════════════════════════════════════════════════════
        int ly = bodyTop + 4;

        // -- Surface --
        ly = addHeader(leftX, ly, colW, "Surface");

        ResettableSliderWidget metallic = new ResettableSliderWidget(0, 0, 150, WIDGET_H,
            0, 1000, Options.materialMetallic[i], def.apply(MaterialBlock::getDefaultMetallic),
            v -> getGenericValueText(Text.literal("Metallic"), Text.literal(String.format("%.1f%%", v / 10.0))),
            v -> {
                Options.materialMetallic[i] = v;
                if (v >= 500 && Options.materialF0R[i] == 0 && Options.materialF0G[i] == 0 && Options.materialF0B[i] == 0) {
                    Options.materialF0R[i] = 500; Options.materialF0G[i] = 500; Options.materialF0B[i] = 500;
                }
                onSliderChanged(i);
            });
        ResettableSliderWidget roughness = new ResettableSliderWidget(0, 0, 150, WIDGET_H,
            0, 100, Options.materialRoughness[i], def.apply(MaterialBlock::getDefaultRoughness),
            v -> getGenericValueText(Text.literal("Roughness"), Text.literal(v + "%")),
            v -> { Options.materialRoughness[i] = v; onSliderChanged(i); });
        ly = addPair(leftX, ly, colW, metallic, roughness);

        ResettableSliderWidget ior = new ResettableSliderWidget(0, 0, 100, WIDGET_H,
            1000, 3000, Math.max(Options.materialIOR[i], 1000), Math.max(def.apply(MaterialBlock::getDefaultIOR), 1000),
            v -> getGenericValueText(Text.literal("IOR"), Text.literal(String.format("%.3f", v / 1000.0))),
            v -> {
                Options.materialIOR[i] = v;
                if (Options.materialMetallic[i] < 500) {
                    int f0pm = MaterialBlock.iorToF0Permille(v);
                    Options.materialF0R[i] = f0pm; Options.materialF0G[i] = f0pm; Options.materialF0B[i] = f0pm;
                }
                onSliderChanged(i);
            });
        ResettableSliderWidget transmission = new ResettableSliderWidget(0, 0, 100, WIDGET_H,
            0, 1000, Options.materialTransmission[i], def.apply(MaterialBlock::getDefaultTransmission),
            v -> getGenericValueText(Text.literal("Transmission"), Text.literal(String.format("%.1f%%", v / 10.0))),
            v -> { Options.materialTransmission[i] = v; onSliderChanged(i); });
        ly = addPair(leftX, ly, colW, ior, transmission);

        ResettableSliderWidget subsurface = new ResettableSliderWidget(0, 0, 100, WIDGET_H,
            0, 1000, Options.materialSubsurface[i], def.apply(MaterialBlock::getDefaultSubsurface),
            v -> getGenericValueText(Text.literal("Subsurface"), Text.literal(String.format("%.1f%%", v / 10.0))),
            v -> { Options.materialSubsurface[i] = v; onSliderChanged(i); });
        ResettableSliderWidget anisotropic = new ResettableSliderWidget(0, 0, 100, WIDGET_H,
            0, 1000, Options.materialAnisotropic[i], def.apply(MaterialBlock::getDefaultAnisotropic),
            v -> getGenericValueText(Text.literal("Anisotropic"), Text.literal(String.format("%.1f%%", v / 10.0))),
            v -> { Options.materialAnisotropic[i] = v; onSliderChanged(i); });
        ly = addPair(leftX, ly, colW, subsurface, anisotropic);

        // Material validation warnings
        List<String> warningTexts = Options.validateMaterial(i);
        for (String w : warningTexts) {
            ly = addWarning(leftX, ly, colW, w);
        }

        // -- Coating --
        ly = addHeader(leftX, ly, colW, "Coating");

        ResettableSliderWidget coatWeight = new ResettableSliderWidget(0, 0, 150, WIDGET_H,
            0, 1000, Options.materialCoatWeight[i], def.apply(MaterialBlock::getDefaultCoatWeight),
            v -> getGenericValueText(Text.literal("Clear Coat"), Text.literal(String.format("%.1f%%", v / 10.0))),
            v -> { Options.materialCoatWeight[i] = v; onSliderChanged(i); });
        ResettableSliderWidget coatRoughness = new ResettableSliderWidget(0, 0, 150, WIDGET_H,
            0, 100, Options.materialCoatRoughness[i], def.apply(MaterialBlock::getDefaultCoatRoughness),
            v -> getGenericValueText(Text.literal("Coat Roughness"), Text.literal(v + "%")),
            v -> { Options.materialCoatRoughness[i] = v; onSliderChanged(i); });
        ly = addPair(leftX, ly, colW, coatWeight, coatRoughness);

        ResettableSliderWidget sheenWeight = new ResettableSliderWidget(0, 0, 100, WIDGET_H,
            0, 1000, Options.materialSheenWeight[i], def.apply(MaterialBlock::getDefaultSheenWeight),
            v -> getGenericValueText(Text.literal("Sheen"), Text.literal(String.format("%.1f%%", v / 10.0))),
            v -> { Options.materialSheenWeight[i] = v; onSliderChanged(i); });
        ResettableSliderWidget sheenTint = new ResettableSliderWidget(0, 0, 100, WIDGET_H,
            0, 1000, Options.materialSheenTint[i], def.apply(MaterialBlock::getDefaultSheenTint),
            v -> getGenericValueText(Text.literal("Sheen Tint"), Text.literal(String.format("%.1f%%", v / 10.0))),
            v -> { Options.materialSheenTint[i] = v; onSliderChanged(i); });
        ly = addPair(leftX, ly, colW, sheenWeight, sheenTint);

        // -- Advanced --
        ly = addHeader(leftX, ly, colW, "Advanced");

        ResettableSliderWidget f0r = new ResettableSliderWidget(0, 0, 100, WIDGET_H,
            0, 1000, Options.materialF0R[i], def.apply(MaterialBlock::getDefaultF0R),
            v -> getGenericValueText(Text.literal("F0 R"), Text.literal(String.format("%.1f%%", v / 10.0))),
            v -> { Options.materialF0R[i] = v; onSliderChanged(i); });
        ResettableSliderWidget f0g = new ResettableSliderWidget(0, 0, 100, WIDGET_H,
            0, 1000, Options.materialF0G[i], def.apply(MaterialBlock::getDefaultF0G),
            v -> getGenericValueText(Text.literal("F0 G"), Text.literal(String.format("%.1f%%", v / 10.0))),
            v -> { Options.materialF0G[i] = v; onSliderChanged(i); });
        ly = addPair(leftX, ly, colW, f0r, f0g);

        ResettableSliderWidget f0b = new ResettableSliderWidget(0, 0, 100, WIDGET_H,
            0, 1000, Options.materialF0B[i], def.apply(MaterialBlock::getDefaultF0B),
            v -> getGenericValueText(Text.literal("F0 B"), Text.literal(String.format("%.1f%%", v / 10.0))),
            v -> { Options.materialF0B[i] = v; onSliderChanged(i); });
        ResettableSliderWidget gamutBoost = new ResettableSliderWidget(0, 0, 100, WIDGET_H,
            0, 200, Options.materialGamutBoost[i], 100,
            v -> getGenericValueText(Text.literal("Gamut"), Text.literal(String.format("\u00d7%.2f", v / 100.0))),
            v -> { Options.materialGamutBoost[i] = v; onSliderChanged(i); });
        ly = addPair(leftX, ly, colW, f0b, gamutBoost);

        String[] gamutModeLabels = {"Uniform", "Saturate"};
        final int[] gamutModeState = {Options.materialGamutBoostMode[i]};
        ButtonWidget gamutModeBtn = ButtonWidget.builder(
            Text.literal("Gamut: " + gamutModeLabels[gamutModeState[0]]),
            b -> {
                gamutModeState[0] = (gamutModeState[0] + 1) % 2;
                Options.materialGamutBoostMode[i] = gamutModeState[0];
                b.setMessage(Text.literal("Gamut: " + gamutModeLabels[gamutModeState[0]]));
                onSliderChanged(i);
            }).dimensions(0, 0, colW, WIDGET_H).build();
        gamutModeBtn.setPosition(leftX, ly);
        addDrawableChild(gamutModeBtn);
        ly += WIDGET_H + 2;

        // ════════════════════════════════════════════════════════
        //  CENTER COLUMN: Noise + Displacement
        // ════════════════════════════════════════════════════════
        int cy = bodyTop + 4;

        // -- Procedural Noise --
        cy = addHeader(centerX, cy, colW, "Procedural Noise");

        noiseDropdown = new NoiseTypeDropdownWidget(0, 0, 100, WIDGET_H, type -> {
            Options.materialNoiseType[i] = type; onSliderChanged(i); });
        noiseDropdown.setNoiseType(Options.materialNoiseType[i]);

        // Noise target
        String[] targetNames = {"Rough", "Rough+", "Normal", "Metal"};
        int[] targetValues = {1, 8, 2, 4};
        int currentTarget = Options.materialNoiseTarget[i];
        int targetIdx = 0;
        for (int t = 0; t < targetValues.length; t++) {
            if (targetValues[t] == currentTarget) { targetIdx = t; break; }
        }
        final int tIdx = targetIdx;
        ButtonWidget noiseTargetBtn = ButtonWidget.builder(
            Text.literal("Target: " + targetNames[tIdx]),
            btn -> {
                int cur = Options.materialNoiseTarget[i];
                int next = 0;
                for (int t = 0; t < targetValues.length; t++) {
                    if (targetValues[t] == cur) { next = (t + 1) % targetValues.length; break; }
                }
                Options.materialNoiseTarget[i] = targetValues[next];
                onSliderChanged(i);
                rebuildSelf();
            }).dimensions(0, 0, 100, WIDGET_H).build();
        cy = addPair(centerX, cy, colW, noiseDropdown, noiseTargetBtn);

        // Wrap + Mask
        String[] wrapLabels = {"3D", "Surface", "Triplanar", "XZ", "XY", "YZ"};
        ButtonWidget wrapBtn = ButtonWidget.builder(
            Text.literal("Wrap: " + wrapLabels[Options.materialNoiseWrap[i]]),
            btn -> {
                int next = (Options.materialNoiseWrap[i] + 1) % wrapLabels.length;
                Options.materialNoiseWrap[i] = next;
                btn.setMessage(Text.literal("Wrap: " + wrapLabels[next]));
                onSliderChanged(i);
            }).dimensions(0, 0, 100, WIDGET_H).build();
        String[] maskModeLabels = {"None", "Lum", "Rough", "Metal", "NrmDev"};
        ButtonWidget maskModeBtn = ButtonWidget.builder(
            Text.literal("Mask: " + maskModeLabels[Options.materialNoiseMaskMode[i]]),
            btn -> {
                int next = (Options.materialNoiseMaskMode[i] + 1) % maskModeLabels.length;
                Options.materialNoiseMaskMode[i] = next;
                btn.setMessage(Text.literal("Mask: " + maskModeLabels[next]));
                onSliderChanged(i);
            }).dimensions(0, 0, 100, WIDGET_H).build();
        cy = addPair(centerX, cy, colW, wrapBtn, maskModeBtn);

        // Str / Scale
        ResettableSliderWidget noiseStrength = new ResettableSliderWidget(0, 0, 100, WIDGET_H,
            0, 1000, Options.materialNoiseStrength[i], 0,
            v -> getGenericValueText(Text.literal("Str"), Text.literal(String.format("%.0f%%", v / 10.0))),
            v -> { Options.materialNoiseStrength[i] = v; onSliderChanged(i); regenerateNoisePreview(); });
        ResettableSliderWidget noiseScale = new ResettableSliderWidget(0, 0, 100, WIDGET_H,
            1, 5000, Math.min(Options.materialNoiseScale[i], 5000), 50,
            v -> getGenericValueText(Text.literal("Scale"), Text.literal(String.format("%.1fx", v / 10.0))),
            v -> { Options.materialNoiseScale[i] = v; onSliderChanged(i); regenerateNoisePreview(); });
        cy = addPair(centerX, cy, colW, noiseStrength, noiseScale);

        // Oct / Seed
        ResettableSliderWidget noiseOctaves = new ResettableSliderWidget(0, 0, 100, WIDGET_H,
            1, 12, Options.materialNoiseOctaves[i], 2,
            v -> getGenericValueText(Text.literal("Oct"), Text.literal(String.valueOf(v))),
            v -> { Options.materialNoiseOctaves[i] = v; onSliderChanged(i); regenerateNoisePreview(); });
        ResettableSliderWidget noiseSeed = new ResettableSliderWidget(0, 0, 100, WIDGET_H,
            0, 999, Options.materialNoiseSeed[i], 0,
            v -> getGenericValueText(Text.literal("Seed"), Text.literal(String.valueOf(v))),
            v -> { Options.materialNoiseSeed[i] = v; onSliderChanged(i); regenerateNoisePreview(); });
        cy = addPair(centerX, cy, colW, noiseOctaves, noiseSeed);

        // Rot / Asp
        ResettableSliderWidget noiseRotation = new ResettableSliderWidget(0, 0, 100, WIDGET_H,
            0, 3600, Options.materialNoiseRotation[i], 0,
            v -> getGenericValueText(Text.literal("Rot"), Text.literal(v / 10 + "\u00B0")),
            v -> { Options.materialNoiseRotation[i] = v; onSliderChanged(i); regenerateNoisePreview(); });
        ResettableSliderWidget noiseAspect = new ResettableSliderWidget(0, 0, 100, WIDGET_H,
            10, 500, Options.materialNoiseAspect[i], 100,
            v -> getGenericValueText(Text.literal("Asp"), Text.literal(String.format("%.1fx", v / 100.0))),
            v -> { Options.materialNoiseAspect[i] = v; onSliderChanged(i); regenerateNoisePreview(); });
        cy = addPair(centerX, cy, colW, noiseRotation, noiseAspect);

        // Lac / Con
        ResettableSliderWidget noiseLacunarity = new ResettableSliderWidget(0, 0, 100, WIDGET_H,
            10, 40, Options.materialNoiseLacunarity[i], 20,
            v -> getGenericValueText(Text.literal("Lac"), Text.literal(String.format("%.1f", v / 10.0))),
            v -> { Options.materialNoiseLacunarity[i] = v; onSliderChanged(i); regenerateNoisePreview(); });
        ResettableSliderWidget noiseContrast = new ResettableSliderWidget(0, 0, 100, WIDGET_H,
            10, 200, Options.materialNoiseContrast[i], 100,
            v -> getGenericValueText(Text.literal("Con"), Text.literal(String.format("%.1f", v / 100.0))),
            v -> { Options.materialNoiseContrast[i] = v; onSliderChanged(i); regenerateNoisePreview(); });
        cy = addPair(centerX, cy, colW, noiseLacunarity, noiseContrast);

        // Conditional: Mask Threshold + Invert
        if (Options.materialNoiseMaskMode[i] > 0) {
            ResettableSliderWidget maskThreshold = new ResettableSliderWidget(0, 0, 100, WIDGET_H,
                0, 1000, Options.materialNoiseMaskThreshold[i], 500,
                v -> getGenericValueText(Text.literal("Threshold"), Text.literal(String.format("%.0f%%", v / 10.0))),
                v -> { Options.materialNoiseMaskThreshold[i] = v; onSliderChanged(i); });
            ButtonWidget maskInvertBtn = ButtonWidget.builder(
                Text.literal("Invert: " + (Options.materialNoiseMaskInvert[i] ? "ON" : "OFF")),
                btn -> {
                    Options.materialNoiseMaskInvert[i] = !Options.materialNoiseMaskInvert[i];
                    btn.setMessage(Text.literal("Invert: " + (Options.materialNoiseMaskInvert[i] ? "ON" : "OFF")));
                    onSliderChanged(i);
                }).dimensions(0, 0, 100, WIDGET_H).build();
            cy = addPair(centerX, cy, colW, maskThreshold, maskInvertBtn);
        }

        // -- Displacement --
        cy = addHeader(centerX, cy, colW, "Displacement");

        String[] dispMethodNames = {"Off (Global)", "DDA", "Tessellation", "Hybrid"};
        ButtonWidget dispMethodBtn = ButtonWidget.builder(
            Text.literal("Method: " + dispMethodNames[Math.min(Options.materialPomMode[i], 3)]),
            btn -> {
                Options.materialPomMode[i] = (Options.materialPomMode[i] + 1) % 4;
                btn.setMessage(Text.literal("Method: " + dispMethodNames[Options.materialPomMode[i]]));
                onSliderChanged(i);
                rebuildSelf();
            }).dimensions(0, 0, 150, WIDGET_H).build();
        ButtonWidget heightFilterBtn = ButtonWidget.builder(
            Text.literal("Filter: " + new String[]{"Nearest", "Bilinear", "Bicubic"}[Math.min(Options.materialHeightFilter[i], 2)]),
            btn -> {
                Options.materialHeightFilter[i] = (Options.materialHeightFilter[i] + 1) % 3;
                btn.setMessage(Text.literal("Filter: " + new String[]{"Nearest", "Bilinear", "Bicubic"}[Options.materialHeightFilter[i]]));
                onSliderChanged(i);
            }).dimensions(0, 0, 100, WIDGET_H).build();
        cy = addPair(centerX, cy, colW, dispMethodBtn, heightFilterBtn);

        ResettableSliderWidget dispDepth = new ResettableSliderWidget(0, 0, 100, WIDGET_H,
            0, 50, Options.materialPomDepth[i], 0,
            v -> getGenericValueText(Text.literal("Depth"),
                v == 0 ? Text.literal("Off") : Text.literal(String.format("%.2f", v / 100.0) + " blk")),
            v -> { Options.materialPomDepth[i] = v; onSliderChanged(i); });

        if (Options.materialPomDepth[i] > 0 || Options.materialPomMode[i] > 0) {
            String[] srcNames = {"Luminance", "Red", "Green", "Blue", "Alpha", "MaxRGB", "MinRGB"};
            ButtonWidget heightSourceBtn = ButtonWidget.builder(
                Text.literal("Source: " + srcNames[Math.min(Options.materialHeightSource[i], 6)]),
                btn -> {
                    Options.materialHeightSource[i] = (Options.materialHeightSource[i] + 1) % 7;
                    btn.setMessage(Text.literal("Source: " + srcNames[Options.materialHeightSource[i]]));
                    onSliderChanged(i);
                }).dimensions(0, 0, 100, WIDGET_H).build();
            cy = addPair(centerX, cy, colW, dispDepth, heightSourceBtn);

            // Steps / Refine
            ResettableSliderWidget ddaSteps = new ResettableSliderWidget(0, 0, 100, WIDGET_H,
                4, 127, Options.materialPomSteps[i], 64,
                v -> getGenericValueText(Text.literal("Steps"), Text.literal(Integer.toString(v))),
                v -> { Options.materialPomSteps[i] = v; onSliderChanged(i); });
            ResettableSliderWidget ddaRefine = new ResettableSliderWidget(0, 0, 100, WIDGET_H,
                0, 8, Options.materialPomRefinement[i], 4,
                v -> getGenericValueText(Text.literal("Refine"), Text.literal(Integer.toString(v))),
                v -> { Options.materialPomRefinement[i] = v; onSliderChanged(i); });
            cy = addPair(centerX, cy, colW, ddaSteps, ddaRefine);

            // Contrast / Offset
            ResettableSliderWidget heightContrast = new ResettableSliderWidget(0, 0, 100, WIDGET_H,
                0, 30, Options.materialHeightContrast[i], 10,
                v -> getGenericValueText(Text.literal("Contrast"), Text.literal(String.format("%.1f", v / 10.0))),
                v -> { Options.materialHeightContrast[i] = v; onSliderChanged(i); });
            ResettableSliderWidget heightOffset = new ResettableSliderWidget(0, 0, 100, WIDGET_H,
                0, 200, Options.materialHeightOffset[i], 100,
                v -> getGenericValueText(Text.literal("Offset"), Text.literal(String.format("%.2f", (v - 100) / 100.0))),
                v -> { Options.materialHeightOffset[i] = v; onSliderChanged(i); });
            cy = addPair(centerX, cy, colW, heightContrast, heightOffset);

            // RemapMin / RemapMax
            ResettableSliderWidget remapMin = new ResettableSliderWidget(0, 0, 100, WIDGET_H,
                0, 100, Options.materialHeightRemapMin[i], 0,
                v -> getGenericValueText(Text.literal("Remap Min"), Text.literal(v + "%")),
                v -> { Options.materialHeightRemapMin[i] = v; onSliderChanged(i); });
            ResettableSliderWidget remapMax = new ResettableSliderWidget(0, 0, 100, WIDGET_H,
                0, 100, Options.materialHeightRemapMax[i], 100,
                v -> getGenericValueText(Text.literal("Remap Max"), Text.literal(v + "%")),
                v -> { Options.materialHeightRemapMax[i] = v; onSliderChanged(i); });
            cy = addPair(centerX, cy, colW, remapMin, remapMax);

            // AO
            ResettableSliderWidget pomAO = new ResettableSliderWidget(0, 0, 100, WIDGET_H,
                0, 100, Options.materialPomAOStrength[i], 0,
                v -> getGenericValueText(Text.literal("AO"),
                    v == 0 ? Text.literal("Off") : Text.literal(v + "%")),
                v -> { Options.materialPomAOStrength[i] = v; onSliderChanged(i); });
            cy = addPair(centerX, cy, colW, pomAO, null);
        } else {
            cy = addPair(centerX, cy, colW, dispDepth, null);
        }

        // -- Parent/Child (enum blocks only) --
        if (block != null && !block.isParent()) {
            cy = addHeader(centerX, cy, colW, "Parent / Child");

            MaterialBlock parentBlock = block.getParentMaterial();
            String parentName = Text.translatable("options.video.materials." + parentBlock.getId()).getString();
            ButtonWidget variantLabel = ButtonWidget.builder(Text.literal("Variant of: " + parentName), btn -> {
                currentBlockIndex = parentBlock.ordinal();
                rebuildSelf();
            }).dimensions(0, 0, 150, WIDGET_H).build();
            ButtonWidget resetParentBtn = ButtonWidget.builder(Text.literal("Reset to Parent"), btn -> {
                int pi = parentBlock.ordinal();
                MaterialData.fromOptions(pi).applyToOptions(i);
                Options.materialChildOverride[i] = false;
                rebuildSelf();
            }).dimensions(0, 0, 150, WIDGET_H).build();
            cy = addPair(centerX, cy, colW, variantLabel, resetParentBtn);
        }

        // ════════════════════════════════════════════════════════
        //  RIGHT COLUMN: Auto-PBR
        // ════════════════════════════════════════════════════════
        int ry = bodyTop + 4;

        // Auto-PBR toggles (paired)
        ButtonWidget perBlockAutoPBRBtn = makeToggle("This Block", Options.materialAutoPBR[i], value -> {
            Options.materialAutoPBR[i] = value;
            onSliderChanged(i); LiveNormalReuploader.scheduleGeneratedReupload(i, true, true);
            rebuildSelf();
        });
        ButtonWidget globalAutoPBRBtn = makeToggle("Global", Options.autoPBREnabled, value -> {
            Options.autoPBREnabled = value;
            LiveNormalReuploader.scheduleGeneratedReupload(true, true);
            rebuildSelf();
        });
        ry = addPair(rightX, ry, colW, perBlockAutoPBRBtn, globalAutoPBRBtn);

        // === Roughness mask controls ===
        ry = addHeader(rightX, ry, colW, "Roughness");

        ButtonWidget presetBtn = ButtonWidget.builder(
            Text.literal("Preset: " + MaterialPreset.fromSettings(
                Options.materialPercentileCenter[i], Options.materialPercentileSpread[i],
                Options.materialAutoPBRRoughnessMin[i], Options.materialAutoPBRRoughnessMax[i]).getDisplayName()),
            btn -> {
                MaterialPreset current = MaterialPreset.fromSettings(
                    Options.materialPercentileCenter[i], Options.materialPercentileSpread[i],
                    Options.materialAutoPBRRoughnessMin[i], Options.materialAutoPBRRoughnessMax[i]);
                MaterialPreset[] matPresets = MaterialPreset.values();
                int nextIdx = (current.ordinal() + 1) % matPresets.length;
                MaterialPreset nextP = matPresets[nextIdx];
                Options.materialPercentileCenter[i] = nextP.center;
                Options.materialPercentileSpread[i] = nextP.spread;
                Options.materialAutoPBRRoughnessMin[i] = nextP.roughMin;
                Options.materialAutoPBRRoughnessMax[i] = nextP.roughMax;
                onSliderChanged(i); regeneratePreview(); LiveNormalReuploader.scheduleGeneratedReupload(i, true, false);
                rebuildSelf();
            }).dimensions(0, 0, colW, WIDGET_H).build();
        presetBtn.active = autoPBRActive;
        ry = addPair(rightX, ry, colW, presetBtn, null);

        ResettableSliderWidget roughMin = new ResettableSliderWidget(0, 0, 100, WIDGET_H,
            0, 100, Options.materialAutoPBRRoughnessMin[i], 30,
            v -> getGenericValueText(Text.literal("R. Min"), Text.literal(v + "%")),
            v -> { Options.materialAutoPBRRoughnessMin[i] = v; onSliderChanged(i); regeneratePreview(); LiveNormalReuploader.scheduleGeneratedReupload(i, true, false); });
        ResettableSliderWidget roughMax = new ResettableSliderWidget(0, 0, 100, WIDGET_H,
            0, 100, Options.materialAutoPBRRoughnessMax[i], 95,
            v -> getGenericValueText(Text.literal("R. Max"), Text.literal(v + "%")),
            v -> { Options.materialAutoPBRRoughnessMax[i] = v; onSliderChanged(i); regeneratePreview(); LiveNormalReuploader.scheduleGeneratedReupload(i, true, false); });
        roughMin.active = autoPBRActive;
        roughMax.active = autoPBRActive;
        ry = addPair(rightX, ry, colW, roughMin, roughMax);

        ResettableSliderWidget perCenter = new ResettableSliderWidget(0, 0, 100, WIDGET_H,
            0, 100, Options.materialPercentileCenter[i], 50,
            v -> getGenericValueText(Text.literal("Center"), Text.literal(v + "%")),
            v -> { Options.materialPercentileCenter[i] = v; onSliderChanged(i); regeneratePreview(); LiveNormalReuploader.scheduleGeneratedReupload(i, true, false); });
        ResettableSliderWidget perSpread = new ResettableSliderWidget(0, 0, 100, WIDGET_H,
            1, 100, Options.materialPercentileSpread[i], 80,
            v -> getGenericValueText(Text.literal("Spread"), Text.literal(v + "%")),
            v -> { Options.materialPercentileSpread[i] = v; onSliderChanged(i); regeneratePreview(); LiveNormalReuploader.scheduleGeneratedReupload(i, true, false); });
        perCenter.active = autoPBRActive;
        perSpread.active = autoPBRActive;
        ry = addPair(rightX, ry, colW, perCenter, perSpread);

        ButtonWidget invertRoughBtn = makeToggle("Invert", (Options.materialAutoPBRFlags[i] & 1) != 0, value -> {
            Options.materialAutoPBRFlags[i] = (Options.materialAutoPBRFlags[i] & ~1) | (value ? 1 : 0);
            onSliderChanged(i); regeneratePreview(); LiveNormalReuploader.scheduleGeneratedReupload(i, true, false);
        });
        ry = addPair(rightX, ry, colW, invertRoughBtn, null);

        // === Normal mask controls ===
        ry = addHeader(rightX, ry, colW, "Normal");

        ResettableSliderWidget normalStrength = new ResettableSliderWidget(0, 0, 150, WIDGET_H,
            0, 200, Options.materialNormalStrength[i], 100,
            v -> getGenericValueText(Text.literal("Strength"),
                Text.literal(String.format("\u00d7%.2f", v / 100.0))),
            v -> { Options.materialNormalStrength[i] = v; onSliderChanged(i); regeneratePreview(); });
        normalStrength.active = autoPBRActive;
        ButtonWidget invertNormalBtn = makeToggle("Invert", (Options.materialAutoPBRFlags[i] & 2) != 0, value -> {
            Options.materialAutoPBRFlags[i] = (Options.materialAutoPBRFlags[i] & ~2) | (value ? 2 : 0);
            onSliderChanged(i); regeneratePreview(); LiveNormalReuploader.scheduleGeneratedReupload(i, false, true);
        });
        ry = addPair(rightX, ry, colW, normalStrength, invertNormalBtn);

        // === Height mask controls ===
        ry = addHeader(rightX, ry, colW, "Height");

        ResettableSliderWidget perHeightGamma = new ResettableSliderWidget(0, 0, 150, WIDGET_H,
            10, 300, Options.materialAutoPBRHeightGamma[i], 100,
            v -> getGenericValueText(Text.literal("Gamma"), Text.literal(String.format("%.2f", v / 100.0))),
            v -> { Options.materialAutoPBRHeightGamma[i] = v; onSliderChanged(i); regeneratePreview(); LiveNormalReuploader.scheduleGeneratedReupload(i, false, true); });
        perHeightGamma.active = autoPBRActive;
        ButtonWidget invertHeightBtn = makeToggle("Invert", (Options.materialAutoPBRFlags[i] & 4) != 0, value -> {
            Options.materialAutoPBRFlags[i] = (Options.materialAutoPBRFlags[i] & ~4) | (value ? 4 : 0);
            onSliderChanged(i); regeneratePreview(); LiveNormalReuploader.scheduleGeneratedReupload(i, false, true);
        });
        ry = addPair(rightX, ry, colW, perHeightGamma, invertHeightBtn);

        // Compute preview strip Y — below the tallest column + gap
        previewStripY = Math.max(ly, Math.max(cy, ry)) + 8;
    }

    // ──────────────────────────────────────────────────────────────
    //  render()
    // ──────────────────────────────────────────────────────────────

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        if (RadianceTheme.peekActive) return;

        // Dark background
        context.fill(0, 0, width, height, 0xF0080808);

        // Column backgrounds with 1px border
        float fade = RadianceTheme.inactiveFadeFactor();
        for (int cx : new int[]{leftX, centerX, rightX}) {
            int x0 = cx - 4, y0 = bodyTop - 4, x1 = cx + colW + 4, y1 = bodyBot;
            context.fill(x0, y0, x1, y1, RadianceTheme.scaleAlpha(0x1A1A1A, fade * 0.7f));
            int border = RadianceTheme.scaleAlpha(0x333333, fade * 0.5f);
            context.fill(x0, y0, x1, y0 + 1, border);
            context.fill(x0, y1 - 1, x1, y1, border);
            context.fill(x0, y0, x0 + 1, y1, border);
            context.fill(x1 - 1, y0, x1, y1, border);
        }

        // Category headers with subtle background bar
        for (Header h : headers) {
            int hy = h.y + 2;
            context.fill(h.x, hy, h.x + h.w, hy + ROW_H - 4,
                RadianceTheme.scaleAlpha(0x2AB5A0, fade * 0.12f));
            context.fill(h.x, hy + ROW_H - 5, h.x + h.w, hy + ROW_H - 4,
                RadianceTheme.scaleAlpha(0x2AB5A0, fade * 0.4f));
            RadianceTheme.drawOutlinedText(context, textRenderer, Text.literal(h.text),
                h.x + 4, hy + (ROW_H - 12) / 2, 0x2AB5A0, fade);
        }

        // Warning texts
        for (Warning w : warnings) {
            if (fade > 0f) {
                String display = "\u26A0 " + w.text;
                int textW = textRenderer.getWidth(display);
                int textX = w.x + (w.w - textW) / 2;
                context.drawText(textRenderer, Text.literal(display), textX, w.y + 5,
                    RadianceTheme.scaleAlpha(0xFFFFAA00, fade), false);
            }
        }

        // Breadcrumb
        RadianceTheme.drawBreadcrumb(context, this.textRenderer, "Radiance > Surfaces > Materials", parentScreen);

        // Search match info
        if (searchField != null && !searchQuery.isEmpty()) {
            int infoX = searchField.getX() + searchField.getWidth() + 8;
            int infoY = searchField.getY() + 4;
            if (searchMatches.isEmpty()) {
                context.drawText(this.textRenderer, Text.literal("No matches"),
                    infoX, infoY, 0xFF808080, false);
            } else {
                if (searchMatchIndex >= searchMatches.size()) searchMatchIndex = 0;
                int matchIdx = searchMatches.get(searchMatchIndex);
                MaterialBlock matchBlock = MaterialBlock.values()[matchIdx];
                String matchName = Text.translatable("options.video.materials." + matchBlock.getId()).getString();
                String info = matchName + " (" + (searchMatchIndex + 1) + "/" + searchMatches.size() + ")";
                int color = matchIdx == currentBlockIndex ? 0xFF80FF80 : 0xFFFFFFFF;
                context.drawText(this.textRenderer, Text.literal(info), infoX, infoY, color, false);
            }
        }

        // Block icon between block selector and search field
        MaterialBlock[] blocks = MaterialBlock.values();
        if (currentBlockIndex < blocks.length) {
            Block iconBlock = blocks[currentBlockIndex].getPrimaryBlock();
            if (iconBlock != null) {
                int selectorEnd = leftX + (int)(width * 0.58) - MARGIN;
                RadianceBlockIcon.drawBlockIcon(context, iconBlock, selectorEnd - 52, -2, 48);
            }
        }

        // Preview thumbnails (right column top)
        renderPreviews(context, mouseX, mouseY);

        // Widgets (super.render handles drawable children)
        super.render(context, mouseX, mouseY, delta);

        // Noise dropdown overlay (must render above all widgets)
        if (noiseDropdown != null) {
            noiseDropdown.renderDropdownOverlay(context, mouseX, mouseY);
        }
    }

    /**
     * Render the bottom preview strip: 4 mask selectors + large selected mask + noise preview.
     */
    private void renderPreviews(DrawContext context, int mouseX, int mouseY) {
        if (!previewsRegistered) return;

        var tr = this.textRenderer;
        int sy = previewStripY;
        int availH = bodyBot - sy - 4;
        if (availH < 60) return; // not enough space

        // Preview sizes — use available height, max 140px
        int largeSize = Math.min(availH - 14, 140);
        int thumbSize = Math.min((largeSize - 12) / 4, 48); // 4 selectors stacked or in a row
        int thumbGap = 4;

        // Layout: [4 selectors column] [gap] [Large selected] [gap] [Noise]
        int selectorsW = thumbSize * 4 + thumbGap * 3;
        int totalUsed = selectorsW + 12 + largeSize + 12 + largeSize;
        int startX = (width - totalUsed) / 2;

        // Background panel
        int panelPad = 6;
        context.fill(startX - panelPad, sy - panelPad,
            startX + totalUsed + panelPad, sy + largeSize + 14 + panelPad, 0xCC0A0A0A);
        int border = RadianceTheme.scaleAlpha(0x333333, 0.5f);
        context.drawBorder(startX - panelPad, sy - panelPad,
            totalUsed + panelPad * 2, largeSize + 14 + panelPad * 2, border);

        // 4 mask selector thumbnails (horizontal row)
        Identifier[] texIds = {PREVIEW_ALBEDO_ID, PREVIEW_NORMAL_ID, PREVIEW_ROUGHNESS_ID, PREVIEW_HEIGHT_ID};
        String[] labels = {"Albedo", "Normal", "Rough", "Height"};
        int thumbY = sy + (largeSize - thumbSize) / 2; // vertically centered
        for (int mi = 0; mi < 4; mi++) {
            int mx = startX + mi * (thumbSize + thumbGap);
            context.drawTexture(RenderLayer::getGuiTextured, texIds[mi],
                mx, thumbY, 0, 0, thumbSize, thumbSize, thumbSize, thumbSize);
            // Selection outline
            if (mi == selectedPreviewMask) {
                int oc = 0xFF2AB5A0;
                context.fill(mx - 1, thumbY - 1, mx + thumbSize + 1, thumbY, oc);
                context.fill(mx - 1, thumbY + thumbSize, mx + thumbSize + 1, thumbY + thumbSize + 1, oc);
                context.fill(mx - 1, thumbY - 1, mx, thumbY + thumbSize + 1, oc);
                context.fill(mx + thumbSize, thumbY - 1, mx + thumbSize + 1, thumbY + thumbSize + 1, oc);
            }
            String label = labels[mi];
            int labelColor = mi == selectedPreviewMask ? 0xFF2AB5A0 : 0xFFCCCCCC;
            context.drawText(tr, Text.literal(label),
                mx + (thumbSize - tr.getWidth(label)) / 2, thumbY + thumbSize + 2, labelColor, false);
        }

        // Large selected mask preview
        int largeX = startX + selectorsW + 12;
        context.drawTexture(RenderLayer::getGuiTextured, texIds[selectedPreviewMask],
            largeX, sy, 0, 0, largeSize, largeSize, largeSize, largeSize);
        // Border
        context.drawBorder(largeX - 1, sy - 1, largeSize + 2, largeSize + 2, 0xFF2AB5A0);
        // Label
        String selLabel = labels[selectedPreviewMask];
        context.drawText(tr, Text.literal(selLabel),
            largeX + (largeSize - tr.getWidth(selLabel)) / 2, sy + largeSize + 3, 0xFF2AB5A0, false);

        // Noise preview
        int noiseX = largeX + largeSize + 12;
        context.drawTexture(RenderLayer::getGuiTextured, PREVIEW_NOISE_ID,
            noiseX, sy, 0, 0, largeSize, largeSize, largeSize, largeSize);
        context.drawBorder(noiseX - 1, sy - 1, largeSize + 2, largeSize + 2, 0xFF666666);
        String noiseLabel = "Noise";
        context.drawText(tr, Text.literal(noiseLabel),
            noiseX + (largeSize - tr.getWidth(noiseLabel)) / 2, sy + largeSize + 3, 0xFFCCCCCC, false);
    }

    // ──────────────────────────────────────────────────────────────
    //  Input handling
    // ──────────────────────────────────────────────────────────────

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (RadianceTheme.handleBreadcrumbClick(mouseX, mouseY, parentScreen)) return true;

        // Handle preview thumbnail clicks (bottom strip)
        if (previewsRegistered && previewStripY > 0) {
            int sy = previewStripY;
            int availH = bodyBot - sy - 4;
            int largeSize = Math.min(availH - 14, 140);
            int thumbSize = Math.min((largeSize - 12) / 4, 48);
            int thumbGap = 4;
            int selectorsW = thumbSize * 4 + thumbGap * 3;
            int totalUsed = selectorsW + 12 + largeSize + 12 + largeSize;
            int startX = (width - totalUsed) / 2;
            int thumbY = sy + (largeSize - thumbSize) / 2;
            for (int mi = 0; mi < 4; mi++) {
                int mx = startX + mi * (thumbSize + thumbGap);
                if (mouseX >= mx && mouseX < mx + thumbSize && mouseY >= thumbY && mouseY < thumbY + thumbSize) {
                    selectedPreviewMask = mi;
                    return true;
                }
            }
        }

        // Handle noise type dropdown clicks (overlay is above list widget)
        if (noiseDropdown != null && noiseDropdown.isOpen()) {
            if (noiseDropdown.mouseClicked(mouseX, mouseY, button)) return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY)) return true;
        // Propagate right-click drag for precision slider mode (button 1)
        if (button == 1) {
            Element focused = getFocused();
            if (focused != null) return focused.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
        }
        return false;
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (searchField != null) {
            searchField.setFocused(true);
            if (searchField.charTyped(chr, modifiers)) return true;
        }
        return super.charTyped(chr, modifiers);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // Search cycling must be checked before peek mode, since both use Tab
        if (searchField != null) {
            if (keyCode == GLFW.GLFW_KEY_ENTER) {
                commitSearch();
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_TAB && !searchMatches.isEmpty()) {
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
                    this.close();
                }
                return true;
            }
            // Forward backspace/delete to search field
            if (keyCode == GLFW.GLFW_KEY_BACKSPACE || keyCode == GLFW.GLFW_KEY_DELETE) {
                searchField.setFocused(true);
                if (searchField.keyPressed(keyCode, scanCode, modifiers)) return true;
            }
        }
        // Peek mode uses Tab — only activate when search didn't consume it
        if (RadianceTheme.handlePeekKeyPressed(keyCode)) return true;
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        if (RadianceTheme.handlePeekKeyReleased(keyCode)) return true;
        return super.keyReleased(keyCode, scanCode, modifiers);
    }

    @Override
    public void close() {
        RadianceTheme.endSliderFocus();
        NoiseTypeDropdownWidget.clearInstances();
        cleanupPreviews();
        cancelChanges();
    }

    /**
     * Rebuild this screen in place. If running as an overlay inside RadianceUnifiedScreen,
     * refreshes the overlay without calling setScreen(). Otherwise falls back to setScreen().
     */
    private void rebuildSelf() {
        if (parentScreen instanceof com.radiance.client.gui.unified.RadianceUnifiedScreen unified
                && unified.isOverlayShowing()) {
            unified.showOverlay(new MaterialsSettingsScreen(parentScreen));
        } else {
            MinecraftClient.getInstance().setScreen(new MaterialsSettingsScreen(parentScreen));
        }
    }

    /** Apply Radiance fixed scaling to the screen dimensions. */
    private void applyRadianceScale() {
        var window = this.client.getWindow();
        var accessor = (com.radiance.mixins.vulkan_render_integration.WindowAccessorMixin) (Object) window;
        int pixelWidth = window.getWidth();
        double radianceScale = Math.max(1.0, Math.round((double) pixelWidth / 1100.0));
        if (GuiScaleHelper.savedScaleFactor < 0) {
            GuiScaleHelper.savedScaleFactor = accessor.radiance$getScaleFactor();
            GuiScaleHelper.savedScaledWidth = accessor.radiance$getScaledWidth();
            GuiScaleHelper.savedScaledHeight = accessor.radiance$getScaledHeight();
        }
        accessor.radiance$setScaleFactor(radianceScale);
        accessor.radiance$setScaledWidth((int) Math.ceil((double) pixelWidth / radianceScale));
        accessor.radiance$setScaledHeight((int) Math.ceil((double) window.getHeight() / radianceScale));
        this.width = accessor.radiance$getScaledWidth();
        this.height = accessor.radiance$getScaledHeight();
    }

    @Override
    public void removed() {
        RadianceTheme.endSliderFocus();
        super.removed();
        // Restore scale when leaving to non-Radiance screen
        Screen next = this.client != null ? this.client.currentScreen : null;
        if (!(next instanceof MaterialsSettingsScreen)
                && !(next instanceof com.radiance.client.gui.unified.RadianceUnifiedScreen)) {
            GuiScaleHelper.restoreOriginalScale();
        }
    }

    // ──────────────────────────────────────────────────────────────
    //  Auto-PBR preview
    // ──────────────────────────────────────────────────────────────

    private void loadSourceAlbedo(String id) {
        if (sourceAlbedo != null) {
            sourceAlbedo.close();
            sourceAlbedo = null;
        }
        String[] candidates = {
            id, id + "_top", id + "_side", id + "_front", id + "_still",
            id + "_block", id + "_block_top", id + "_block_side",
            "white_" + id, "oak_" + id,
            id + "_planks", "brain_" + id,
            id + "_inner", id + "_block_side_inner"
        };
        var rm = MinecraftClient.getInstance().getResourceManager();
        for (String name : candidates) {
            try {
                Identifier texId = Identifier.of("minecraft", "textures/block/" + name + ".png");
                Optional<Resource> res = rm.getResource(texId);
                if (res.isPresent()) {
                    sourceAlbedo = NativeImage.read(res.get().getInputStream());
                    break;
                }
            } catch (IOException e) {
                // Try next candidate
            }
        }
    }

    private void regeneratePreview() {
        if (sourceAlbedo == null) {
            if (previewsRegistered) {
                NativeImage blank = new NativeImage(16, 16, false);
                for (int py = 0; py < 16; py++)
                    for (int px = 0; px < 16; px++)
                        blank.setColorArgb(px, py, 0xFF202020);
                previewAlbedoTex.setImage(blank.applyToCopy(img -> img));
                previewAlbedoTex.upload();
                previewNormalTex.setImage(blank.applyToCopy(img -> (128 << 24) | (128 << 16) | (128 << 8) | 255));
                previewNormalTex.upload();
                previewRoughTex.setImage(blank.applyToCopy(img -> img));
                previewRoughTex.upload();
                previewHeightTex.setImage(blank.applyToCopy(img -> img));
                previewHeightTex.upload();
                blank.close();
                // Still generate noise preview even without source albedo
                NativeImage noiseImg = NoisePreviewGenerator.generate(160, currentBlockIndex);
                if (previewNoiseTex != null) {
                    previewNoiseTex.setImage(noiseImg);
                    previewNoiseTex.upload();
                }
            }
            return;
        }

        int fullW = sourceAlbedo.getWidth();
        int fullH = Math.min(sourceAlbedo.getHeight(), fullW);
        int srcW = Math.min(160, Math.max(16, fullW));
        int srcH = Math.min(160, Math.max(16, fullH));
        NativeImage albedoCopy = new NativeImage(srcW, srcH, false);
        for (int y = 0; y < srcH; y++) {
            int sy = Math.min(fullH - 1, y * fullH / srcH);
            for (int x = 0; x < srcW; x++) {
                int sx = Math.min(fullW - 1, x * fullW / srcW);
                albedoCopy.setColorArgb(x, y, sourceAlbedo.getColorArgb(sx, sy));
            }
        }
        int ci = currentBlockIndex;
        int flags = Options.materialAutoPBRFlags[ci];
        boolean invertRough = (flags & 1) != 0;
        boolean invertNormal = (flags & 2) != 0;
        boolean invertHeight = (flags & 4) != 0;
        AutoPBRGenerator.HeightParams hp = AutoPBRGenerator.HeightParams.fromOptions(ci);
        NativeImage normalImg = AutoPBRGenerator.generateNormal(albedoCopy,
            Options.materialNormalStrength[ci], invertNormal,
            Options.materialAutoPBRHeightGamma[ci], invertHeight,
            hp, Options.materialPomAOStrength[ci]);
        NativeImage roughImg = AutoPBRGenerator.generateRoughnessPreviewPercentile(albedoCopy,
            Options.materialAutoPBRRoughnessMin[ci], Options.materialAutoPBRRoughnessMax[ci],
            Options.materialPercentileCenter[ci], Options.materialPercentileSpread[ci],
            invertRough);
        NativeImage heightImg = AutoPBRGenerator.generateHeightPreview(albedoCopy,
            Options.materialAutoPBRHeightGamma[ci], invertHeight, hp);
        NativeImage noiseImg = NoisePreviewGenerator.generate(160, ci);

        if (!previewsRegistered) {
            var texManager = MinecraftClient.getInstance().getTextureManager();
            previewAlbedoTex = new NativeImageBackedTexture(albedoCopy);
            previewAlbedoTex.upload();
            texManager.registerTexture(PREVIEW_ALBEDO_ID, previewAlbedoTex);

            previewNormalTex = new NativeImageBackedTexture(normalImg);
            previewNormalTex.upload();
            texManager.registerTexture(PREVIEW_NORMAL_ID, previewNormalTex);

            previewRoughTex = new NativeImageBackedTexture(roughImg);
            previewRoughTex.upload();
            texManager.registerTexture(PREVIEW_ROUGHNESS_ID, previewRoughTex);

            previewHeightTex = new NativeImageBackedTexture(heightImg);
            previewHeightTex.upload();
            texManager.registerTexture(PREVIEW_HEIGHT_ID, previewHeightTex);

            previewNoiseTex = new NativeImageBackedTexture(noiseImg);
            previewNoiseTex.upload();
            texManager.registerTexture(PREVIEW_NOISE_ID, previewNoiseTex);

            previewsRegistered = true;
        } else {
            var existingImg = previewAlbedoTex.getImage();
            if (existingImg == null || existingImg.getWidth() != albedoCopy.getWidth()
                    || existingImg.getHeight() != albedoCopy.getHeight()) {
                var texManager = MinecraftClient.getInstance().getTextureManager();
                texManager.destroyTexture(PREVIEW_ALBEDO_ID);
                texManager.destroyTexture(PREVIEW_NORMAL_ID);
                texManager.destroyTexture(PREVIEW_ROUGHNESS_ID);
                texManager.destroyTexture(PREVIEW_HEIGHT_ID);
                if (previewNoiseTex != null) texManager.destroyTexture(PREVIEW_NOISE_ID);
                previewsRegistered = false;
                regeneratePreview();
                return;
            }
            previewAlbedoTex.setImage(albedoCopy);
            previewAlbedoTex.upload();

            previewNormalTex.setImage(normalImg);
            previewNormalTex.upload();

            previewRoughTex.setImage(roughImg);
            previewRoughTex.upload();

            previewHeightTex.setImage(heightImg);
            previewHeightTex.upload();

            // Noise preview may change dimensions — recreate if needed
            var existingNoise = previewNoiseTex != null ? previewNoiseTex.getImage() : null;
            if (existingNoise == null || existingNoise.getWidth() != noiseImg.getWidth()) {
                if (previewNoiseTex != null) {
                    var texManager = MinecraftClient.getInstance().getTextureManager();
                    texManager.destroyTexture(PREVIEW_NOISE_ID);
                }
                previewNoiseTex = new NativeImageBackedTexture(noiseImg);
                previewNoiseTex.upload();
                MinecraftClient.getInstance().getTextureManager().registerTexture(PREVIEW_NOISE_ID, previewNoiseTex);
            } else {
                previewNoiseTex.setImage(noiseImg);
                previewNoiseTex.upload();
            }
        }
    }

    /** Regenerate only the noise preview texture (avoids redundant normal/roughness/height work). */
    private void regenerateNoisePreview() {
        NativeImage noiseImg = NoisePreviewGenerator.generate(160, currentBlockIndex);
        if (!previewsRegistered) {
            regeneratePreview(); // need full init first
            return;
        }
        var existingNoise = previewNoiseTex != null ? previewNoiseTex.getImage() : null;
        if (existingNoise == null || existingNoise.getWidth() != noiseImg.getWidth()) {
            if (previewNoiseTex != null) {
                MinecraftClient.getInstance().getTextureManager().destroyTexture(PREVIEW_NOISE_ID);
            }
            previewNoiseTex = new NativeImageBackedTexture(noiseImg);
            previewNoiseTex.upload();
            MinecraftClient.getInstance().getTextureManager().registerTexture(PREVIEW_NOISE_ID, previewNoiseTex);
        } else {
            previewNoiseTex.setImage(noiseImg);
            previewNoiseTex.upload();
        }
    }

    private void cleanupPreviews() {
        if (sourceAlbedo != null) {
            sourceAlbedo.close();
            sourceAlbedo = null;
        }
    }

    // ──────────────────────────────────────────────────────────────
    //  Search
    // ──────────────────────────────────────────────────────────────

    private void commitSearch() {
        if (searchMatches.isEmpty()) return;
        int match = searchMatches.get(searchMatchIndex);
        currentBlockIndex = match;
        searchQuery = "";
        searchMatches.clear();
        searchMatchIndex = 0;
        rebuildSelf();
    }

    private static void updateSearchMatches() {
        searchMatches.clear();
        if (searchQuery == null || searchQuery.isEmpty()) return;
        String lower = searchQuery.toLowerCase();
        java.util.List<Integer> ordinals = MaterialBlock.getUniqueOrdinals();
        for (int j = 0; j < ordinals.size(); j++) {
            int ord = ordinals.get(j);
            String name = MaterialBlock.getDisplayNameForOrdinal(ord).toLowerCase();
            String id = MaterialBlock.getIdForOrdinal(ord).toLowerCase();
            if (name.contains(lower) || id.contains(lower)) {
                searchMatches.add(j);
            }
        }
        if (searchMatchIndex >= searchMatches.size()) searchMatchIndex = 0;
    }
}
