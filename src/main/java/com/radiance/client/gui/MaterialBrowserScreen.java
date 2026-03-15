package com.radiance.client.gui;

import static net.minecraft.client.option.GameOptions.getGenericValueText;

import com.radiance.client.option.Options;
import com.radiance.client.util.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Cinema 4D-inspired material browser with grid layout, sphere previews,
 * category tabs, search, and import/export functionality.
 */
public class MaterialBrowserScreen extends Screen {

    private final Screen parent;

    // Layout constants
    private static final int HEADER_H = 22;
    private static final int TABS_H = 22;
    private static final int SEARCH_H = 24;
    private static final int SIDEBAR_W = 220;
    private static final int CELL_SIZE = 80;
    private static final int CELL_PAD = 6;
    private static final int ICON_SIZE = 64;
    private static final int LARGE_ICON = 180;
    private static final int MARGIN = 10;
    private static final int SIDE_BTN_H = 16;
    private static final int SIDE_BTN_PAD = 3;

    // Category tabs
    private static final String[] CATEGORIES = {
        "All", "Metals", "Gems", "Minerals", "Glass", "Stone", "Wood",
        "Earth", "Ceramics", "Organic", "Liquids", "Misc",
        "Presets", "Saved", "Packs"
    };
    public static final int TAB_PACKS = 14;
    private int selectedCategory = 0;

    // Sort modes
    private static final String[] SORT_LABELS = {"A-Z", "Rough", "Metal", "Modified"};
    private int sortMode = 0;

    // State
    private String searchQuery = "";
    private TextFieldWidget searchField;
    private double scrollOffset = 0;
    private int hoveredIndex = -1;
    private int selectedIndex = -1;
    private final List<MaterialEntry> allEntries = new ArrayList<>();
    private List<MaterialEntry> filteredEntries = new ArrayList<>();

    // Double-click tracking
    private long lastClickTime = 0;
    private int lastClickedIndex = -1;

    // Sidebar button Y (set during render, used for click hit-testing)
    private int sidebarBtnY = -1;

    // Context menu
    private boolean contextMenuOpen = false;
    private int contextMenuX, contextMenuY;
    private int contextMenuTarget = -1;
    private boolean clipboardHasData = false; // cached when context menu opens

    // Toast message
    private String toastMessage = null;
    private long toastExpiry = 0;

    // Sidebar child variant tracking (for click hit-testing)
    private final List<Integer> sidebarChildOrdinals = new ArrayList<>();
    private int sidebarChildrenTopY = -1;

    record MaterialEntry(
        String blockId,
        String displayName,
        String category,
        MaterialData data,
        int blockIndex  // -1 for saved/imported/preset materials
    ) {}

    public MaterialBrowserScreen(Screen parent) {
        super(Text.translatable("radiance.texture_editor.title"));
        this.parent = parent;
    }

    /** Open the browser with a specific category tab pre-selected. */
    public MaterialBrowserScreen(Screen parent, int categoryIndex) {
        this(parent);
        if (categoryIndex >= 0 && categoryIndex < CATEGORIES.length) {
            this.selectedCategory = categoryIndex;
        }
    }

    @Override
    protected void init() {
        // Validate sphere disk cache on first open
        MaterialSphereRenderer.validateDiskCache();

        // Search field
        int searchX = MARGIN;
        int searchY = HEADER_H + TABS_H + 4;
        int searchW = this.width - SIDEBAR_W - MARGIN * 3 - 340;
        searchField = new TextFieldWidget(this.textRenderer, searchX, searchY, Math.max(searchW, 100), SEARCH_H - 4,
            Text.translatable("radiance.texture_editor.search"));
        searchField.setMaxLength(50);
        searchField.setText(searchQuery);
        searchField.setChangedListener(q -> { searchQuery = q; rebuildFilteredList(); });
        addDrawableChild(searchField);

        // Import button
        int btnX = searchField.getX() + searchField.getWidth() + 6;
        addDrawableChild(ButtonWidget.builder(
            Text.translatable("radiance.materials.import"), btn -> onImportMaterial())
            .dimensions(btnX, searchY, 64, SEARCH_H - 4).build());

        // Export All button
        addDrawableChild(ButtonWidget.builder(
            Text.translatable("radiance.materials.exportAll"), btn -> onExportAll())
            .dimensions(btnX + 70, searchY, 64, SEARCH_H - 4).build());

        // Sort cycle button
        addDrawableChild(ButtonWidget.builder(
            Text.literal("Sort: " + SORT_LABELS[sortMode]), btn -> {
                sortMode = (sortMode + 1) % SORT_LABELS.length;
                btn.setMessage(Text.literal("Sort: " + SORT_LABELS[sortMode]));
                rebuildFilteredList();
            }).dimensions(btnX + 140, searchY, 64, SEARCH_H - 4).build());

        // Opacity slider (top-right, before sidebar)
        int opSliderW = 120;
        int opSliderH = SEARCH_H - 4;
        int opSliderX = this.width - SIDEBAR_W - MARGIN - opSliderW - 4;
        int opSliderY = searchY;
        ResettableSliderWidget opacitySlider = new ResettableSliderWidget(
            opSliderX, opSliderY, opSliderW, opSliderH,
            0, 100, Options.uiGlobalAlphaPercent, 55,
            v -> getGenericValueText(
                Text.translatable("radiance.settings.menu_transparency"),
                Text.literal(v + "%")),
            v -> Options.setUiGlobalAlphaPercent(v, false));
        opacitySlider.setOnRelease(() -> Options.overwriteConfig());
        opacitySlider.settingKey = "uiGlobalAlphaPercent";
        addDrawableChild(opacitySlider);

        // Build entries
        buildEntryList();
        rebuildFilteredList();
    }

    private void buildEntryList() {
        allEntries.clear();
        MaterialBlock[] blocks = MaterialBlock.values();
        for (int i = 0; i < blocks.length; i++) {
            MaterialBlock mb = blocks[i];
            if (!mb.isParent()) continue; // Only show parent materials in grid
            MaterialData data = MaterialData.fromOptions(i);
            String cat = mb.getCategory().getDisplayName();
            String name = Text.translatable("options.video.materials." + mb.getId()).getString();
            allEntries.add(new MaterialEntry(mb.getId(), name, cat, data, i));
        }

        // Metal presets (physically accurate F0 reference data)
        for (MetalPreset preset : MetalPreset.values()) {
            MaterialData d = new MaterialData();
            d.blockId = "preset_" + preset.name().toLowerCase();
            d.displayName = preset.getDisplayName();
            d.f0R = preset.getF0R();
            d.f0G = preset.getF0G();
            d.f0B = preset.getF0B();
            d.roughness = preset.getRoughness();
            d.metallic = 1000;
            allEntries.add(new MaterialEntry(d.blockId, d.displayName, "Presets", d, -1));
        }

        // Load saved materials
        List<MaterialData> saved = MaterialFileManager.listSavedMaterials();
        for (MaterialData d : saved) {
            allEntries.add(new MaterialEntry(d.blockId, d.displayName, "Saved", d, -1));
        }

        // Load packs (flatten into entries)
        List<MaterialsPack> packs = MaterialFileManager.listSavedPacks();
        for (MaterialsPack pack : packs) {
            for (MaterialData d : pack.materials.values()) {
                String packLabel = pack.name.isEmpty() ? "Pack" : pack.name;
                allEntries.add(new MaterialEntry(d.blockId,
                    d.displayName + " (" + packLabel + ")", "Packs", d, -1));
            }
        }
    }

    private void rebuildFilteredList() {
        filteredEntries = new ArrayList<>();
        String cat = selectedCategory > 0 ? CATEGORIES[selectedCategory] : null;
        String query = searchQuery.toLowerCase().trim();

        for (MaterialEntry e : allEntries) {
            if (cat != null && !e.category.equals(cat)) continue;
            if (!query.isEmpty()) {
                if (!e.displayName.toLowerCase().contains(query)
                    && !e.blockId.toLowerCase().contains(query)) continue;
            }
            filteredEntries.add(e);
        }

        // Sort
        switch (sortMode) {
            case 1 -> filteredEntries.sort(Comparator.comparingInt(e -> e.data.roughness));
            case 2 -> filteredEntries.sort(Comparator.comparingInt(e -> -e.data.metallic));
            case 3 -> {
                MaterialBlock[] blocks = MaterialBlock.values();
                filteredEntries.sort((a, b) -> {
                    boolean amod = a.blockIndex >= 0 && !a.data.isDefault(blocks[a.blockIndex]);
                    boolean bmod = b.blockIndex >= 0 && !b.data.isDefault(blocks[b.blockIndex]);
                    return Boolean.compare(bmod, amod);
                });
            }
            default -> filteredEntries.sort(Comparator.comparing(e -> e.displayName));
        }

        // Reset scroll and selection if out of range
        if (selectedIndex >= filteredEntries.size()) selectedIndex = -1;
        scrollOffset = Math.max(0, Math.min(scrollOffset, maxScroll()));
    }

    // ── Rendering ──

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        // Drain completed async sphere renders → register textures on GL thread
        MaterialSphereRenderer.drainCompleted();

        // Background
        ctx.fill(0, 0, this.width, this.height, RadianceTheme.panelBg);

        // Header / breadcrumb (clickable)
        RadianceTheme.drawBreadcrumb(ctx, this.textRenderer,
            "Radiance > Lighting > Texture Editor", parent);

        // Category tabs
        renderCategoryTabs(ctx, mouseX, mouseY);

        // Search field and buttons rendered by super
        super.render(ctx, mouseX, mouseY, delta);

        // Grid area
        int gridTop = HEADER_H + TABS_H + SEARCH_H + 8;
        int gridLeft = MARGIN;
        int gridWidth = this.width - SIDEBAR_W - MARGIN * 2;
        int gridBottom = this.height - 4;

        renderGrid(ctx, mouseX, mouseY, gridLeft, gridTop, gridWidth, gridBottom);

        // Preview sidebar
        renderPreviewSidebar(ctx, mouseX, mouseY);

        // Hover tooltip (when not already showing in sidebar)
        if (hoveredIndex >= 0 && hoveredIndex != selectedIndex && !contextMenuOpen) {
            renderHoverTooltip(ctx, mouseX, mouseY);
        }

        // Context menu
        if (contextMenuOpen) {
            renderContextMenu(ctx, mouseX, mouseY);
        }

        // Toast message with fade-out
        if (toastMessage != null && System.currentTimeMillis() < toastExpiry) {
            long remaining = toastExpiry - System.currentTimeMillis();
            float fadeAlpha = remaining < 500 ? remaining / 500f : 1f;
            int bgColor = RadianceTheme.scaleAlpha(RadianceTheme.dropdownBg, fadeAlpha);
            int textColor = RadianceTheme.scaleAlpha(RadianceTheme.textAccent, fadeAlpha);
            int tw = this.textRenderer.getWidth(toastMessage) + 12;
            int tx = (this.width - tw) / 2;
            int ty = this.height - 30;
            ctx.fill(tx, ty, tx + tw, ty + 18, bgColor);
            RadianceTheme.drawOutlinedText(ctx, this.textRenderer,
                Text.literal(toastMessage), tx + 6, ty + 5, textColor);
        }
    }

    private static String getTabLabel(String category) {
        return Text.translatable("radiance.materials.browser.tab." + category.toLowerCase()).getString();
    }

    private void renderCategoryTabs(DrawContext ctx, int mouseX, int mouseY) {
        int tabY = HEADER_H;
        int tabX = MARGIN;
        for (int i = 0; i < CATEGORIES.length; i++) {
            String label = getTabLabel(CATEGORIES[i]);
            int tw = this.textRenderer.getWidth(label) + 12;
            boolean hovered = mouseX >= tabX && mouseX < tabX + tw && mouseY >= tabY && mouseY < tabY + TABS_H;
            boolean selected = i == selectedCategory;

            int bg = selected ? RadianceTheme.widgetBgActive : (hovered ? RadianceTheme.widgetBgHover : RadianceTheme.widgetBg);
            ctx.fill(tabX, tabY, tabX + tw, tabY + TABS_H, bg);
            if (selected) {
                ctx.fill(tabX, tabY + TABS_H - 2, tabX + tw, tabY + TABS_H, RadianceTheme.textAccent);
            }
            RadianceTheme.drawOutlinedText(ctx, this.textRenderer,
                Text.literal(label), tabX + 6, tabY + 6,
                selected ? RadianceTheme.textAccent : RadianceTheme.textPrimary);
            tabX += tw + 2;
        }
    }

    private void renderGrid(DrawContext ctx, int mouseX, int mouseY,
                             int gridLeft, int gridTop, int gridWidth, int gridBottom) {
        // Enable scissor to clip grid
        ctx.enableScissor(gridLeft, gridTop, gridLeft + gridWidth, gridBottom);

        int cols = Math.max(1, gridWidth / (CELL_SIZE + CELL_PAD));
        int rows = (filteredEntries.size() + cols - 1) / cols;
        hoveredIndex = -1;

        for (int idx = 0; idx < filteredEntries.size(); idx++) {
            int col = idx % cols;
            int row = idx / cols;
            int cellX = gridLeft + col * (CELL_SIZE + CELL_PAD);
            int cellY = gridTop + row * (CELL_SIZE + CELL_PAD + 14) - (int) scrollOffset;

            if (cellY + CELL_SIZE + 14 < gridTop || cellY > gridBottom) continue;

            MaterialEntry entry = filteredEntries.get(idx);
            boolean hovered = mouseX >= cellX && mouseX < cellX + CELL_SIZE
                && mouseY >= cellY && mouseY < cellY + CELL_SIZE + 14
                && mouseY >= gridTop && mouseY < gridBottom;
            boolean selected = idx == selectedIndex;

            if (hovered) hoveredIndex = idx;

            // Cell background
            int bg = selected ? RadianceTheme.widgetBgActive
                : (hovered ? RadianceTheme.widgetBgHover : RadianceTheme.widgetBg);
            ctx.fill(cellX, cellY, cellX + CELL_SIZE, cellY + CELL_SIZE + 14, bg);

            // Animated pulsing border for selected
            if (selected) {
                double pulse = 0.55 + 0.45 * Math.sin(System.currentTimeMillis() / 400.0);
                int accentRgb = RadianceTheme.textAccent & 0x00FFFFFF;
                int borderAlpha = (int) (pulse * 255) << 24;
                drawBorder(ctx, cellX, cellY, CELL_SIZE, CELL_SIZE + 14, borderAlpha | accentRgb);
            }

            // Block icon (centered in cell)
            int iconX = cellX + (CELL_SIZE - ICON_SIZE) / 2;
            int iconY = cellY + 2;
            boolean drewBlock = false;
            if (entry.blockIndex >= 0 && entry.blockIndex < MaterialBlock.values().length) {
                net.minecraft.block.Block block = MaterialBlock.values()[entry.blockIndex].getPrimaryBlock();
                if (block != null) {
                    RadianceBlockIcon.drawBlockIcon(ctx, block, iconX, iconY, ICON_SIZE);
                    drewBlock = true;
                }
            }
            if (!drewBlock) {
                MaterialSphereRenderer.drawSphere(ctx, entry.data, iconX, iconY,
                    MaterialSphereRenderer.GRID_RENDER_SIZE, ICON_SIZE);
            }

            // Modified indicator (accent bottom edge)
            if (entry.blockIndex >= 0) {
                MaterialBlock mb = MaterialBlock.values()[entry.blockIndex];
                if (!entry.data.isDefault(mb)) {
                    ctx.fill(cellX + 2, cellY + CELL_SIZE + 12,
                        cellX + CELL_SIZE - 2, cellY + CELL_SIZE + 14, RadianceTheme.textAccent);
                }
            }

            // Name label (truncated)
            String name = entry.displayName;
            if (this.textRenderer.getWidth(name) > CELL_SIZE - 2) {
                int ellipsisW = this.textRenderer.getWidth("..");
                name = this.textRenderer.trimToWidth(name, CELL_SIZE - 4 - ellipsisW) + "..";
            }
            int nameX = cellX + (CELL_SIZE - this.textRenderer.getWidth(name)) / 2;
            RadianceTheme.drawOutlinedText(ctx, this.textRenderer,
                Text.literal(name), nameX, cellY + ICON_SIZE + 4, RadianceTheme.textSecondary);
        }

        ctx.disableScissor();

        // Scrollbar
        int totalHeight = rows * (CELL_SIZE + CELL_PAD + 14);
        int visibleHeight = gridBottom - gridTop;
        if (totalHeight > visibleHeight) {
            int trackX = gridLeft + gridWidth - 3;
            double ratio = (double) visibleHeight / totalHeight;
            int thumbH = Math.max(20, (int) (visibleHeight * ratio));
            int thumbY = gridTop + (int) ((scrollOffset / totalHeight) * visibleHeight);
            thumbY = Math.min(thumbY, gridBottom - thumbH);
            ctx.fill(trackX, gridTop, trackX + 3, gridBottom, RadianceTheme.widgetBg);
            ctx.fill(trackX, thumbY, trackX + 3, thumbY + thumbH, RadianceTheme.borderFocused);
        }
    }

    private void renderPreviewSidebar(DrawContext ctx, int mouseX, int mouseY) {
        int sideX = this.width - SIDEBAR_W - MARGIN;
        int sideY = HEADER_H + TABS_H + SEARCH_H + 8;
        int sideBottom = this.height - 4;

        // Sidebar background
        ctx.fill(sideX, sideY, sideX + SIDEBAR_W, sideBottom, RadianceTheme.headerBg);

        sidebarBtnY = -1; // reset until we have a selection

        if (selectedIndex < 0 || selectedIndex >= filteredEntries.size()) {
            RadianceTheme.drawOutlinedText(ctx, this.textRenderer,
                Text.literal("Select a material"), sideX + 10, sideY + 40, RadianceTheme.textSecondary);
            return;
        }

        MaterialEntry entry = filteredEntries.get(selectedIndex);
        MaterialData d = entry.data;

        // Large sphere preview
        int sphereX = sideX + (SIDEBAR_W - LARGE_ICON) / 2;
        MaterialSphereRenderer.drawSphere(ctx, d, sphereX, sideY + 8,
            MaterialSphereRenderer.SIDEBAR_RENDER_SIZE, LARGE_ICON);

        // Material name
        int textY = sideY + LARGE_ICON + 16;
        RadianceTheme.drawOutlinedText(ctx, this.textRenderer,
            Text.literal(entry.displayName), sideX + 8, textY, RadianceTheme.textPrimary);
        textY += 12;
        RadianceTheme.drawOutlinedText(ctx, this.textRenderer,
            Text.literal(entry.blockId), sideX + 8, textY, RadianceTheme.textSecondary);
        textY += 14;

        // F0 color swatch
        int swR = Math.max(0, Math.min(255, (int) (d.f0R / 1000.0 * 255)));
        int swG = Math.max(0, Math.min(255, (int) (d.f0G / 1000.0 * 255)));
        int swB = Math.max(0, Math.min(255, (int) (d.f0B / 1000.0 * 255)));
        int swatchColor = 0xFF000000 | (swR << 16) | (swG << 8) | swB;
        ctx.fill(sideX + 8, textY, sideX + SIDEBAR_W - 8, textY + 8, swatchColor);
        drawBorder(ctx, sideX + 8, textY, SIDEBAR_W - 16, 8, RadianceTheme.borderDefault);
        textY += 14;

        // Separator
        ctx.fill(sideX + 8, textY - 3, sideX + SIDEBAR_W - 8, textY - 2, RadianceTheme.borderDefault);

        // Properties list
        textY = drawProperty(ctx, sideX + 8, textY, "F0 R", String.format("%.1f%%", d.f0R / 10.0));
        textY = drawProperty(ctx, sideX + 8, textY, "F0 G", String.format("%.1f%%", d.f0G / 10.0));
        textY = drawProperty(ctx, sideX + 8, textY, "F0 B", String.format("%.1f%%", d.f0B / 10.0));
        textY = drawProperty(ctx, sideX + 8, textY, "Rough", d.roughness + "%");
        textY = drawProperty(ctx, sideX + 8, textY, "Metal", String.format("%.1f%%", d.metallic / 10.0));
        textY = drawProperty(ctx, sideX + 8, textY, "Trans", String.format("%.1f%%", d.transmission / 10.0));
        textY = drawProperty(ctx, sideX + 8, textY, "IOR", String.format("%.3f", d.ior / 1000.0));
        textY = drawProperty(ctx, sideX + 8, textY, "Subsrf", String.format("%.1f%%", d.subsurface / 10.0));
        textY = drawProperty(ctx, sideX + 8, textY, "Aniso", String.format("%.1f%%", d.anisotropic / 10.0));
        textY = drawProperty(ctx, sideX + 8, textY, "Sheen", String.format("%.1f%%", d.sheenWeight / 10.0));
        textY = drawProperty(ctx, sideX + 8, textY, "S.Tint", String.format("%.1f%%", d.sheenTint / 10.0));
        textY = drawProperty(ctx, sideX + 8, textY, "Coat", String.format("%.1f%%", d.coatWeight / 10.0));
        textY = drawProperty(ctx, sideX + 8, textY, "C.Rough", d.coatRoughness + "%");
        if (d.noiseStrength > 0) {
            textY = drawProperty(ctx, sideX + 8, textY, "Noise", NoiseTypeDropdownWidget.getLabel(d.noiseType));
            textY = drawProperty(ctx, sideX + 8, textY, "N.Str", d.noiseStrength + "%");
            textY = drawProperty(ctx, sideX + 8, textY, "N.Scale", String.format("%.1f", d.noiseScale / 10.0));
            textY = drawProperty(ctx, sideX + 8, textY, "N.Oct", String.valueOf(d.noiseOctaves));
            if (d.noiseSeed > 0) textY = drawProperty(ctx, sideX + 8, textY, "N.Seed", String.valueOf(d.noiseSeed));
        }
        textY += 4;

        // Separator before buttons
        ctx.fill(sideX + 8, textY, sideX + SIDEBAR_W - 8, textY + 1, RadianceTheme.borderDefault);
        textY += 5;

        // Action buttons — store Y for click hit-testing
        sidebarBtnY = textY;
        int btnW = SIDEBAR_W - 16;
        renderSidebarButton(ctx, sideX + 8, textY, btnW, "Apply", mouseX, mouseY);
        textY += SIDE_BTN_H + SIDE_BTN_PAD;
        renderSidebarButton(ctx, sideX + 8, textY, btnW, "Copy", mouseX, mouseY);
        textY += SIDE_BTN_H + SIDE_BTN_PAD;
        renderSidebarButton(ctx, sideX + 8, textY, btnW, "Export", mouseX, mouseY);
        textY += SIDE_BTN_H + SIDE_BTN_PAD;

        // Variants section (children of parent material)
        sidebarChildOrdinals.clear();
        sidebarChildrenTopY = -1;
        if (entry.blockIndex >= 0 && entry.blockIndex < MaterialBlock.values().length) {
            MaterialBlock mb = MaterialBlock.values()[entry.blockIndex];
            List<MaterialBlock> children = mb.getChildren();
            if (!children.isEmpty()) {
                textY += 4;
                ctx.fill(sideX + 8, textY, sideX + SIDEBAR_W - 8, textY + 1, RadianceTheme.borderDefault);
                textY += 5;
                RadianceTheme.drawOutlinedText(ctx, this.textRenderer,
                    Text.translatable("radiance.texture_editor.variants"), sideX + 8, textY, RadianceTheme.textAccent);
                textY += 14;

                sidebarChildrenTopY = textY;
                for (MaterialBlock child : children) {
                    sidebarChildOrdinals.add(child.ordinal());
                    boolean childOverride = Options.materialChildOverride[child.ordinal()];
                    String childName = Text.translatable("options.video.materials." + child.getId()).getString();
                    boolean hovered = mouseX >= sideX + 8 && mouseX < sideX + SIDEBAR_W - 8
                        && mouseY >= textY && mouseY < textY + 13;

                    if (hovered) ctx.fill(sideX + 8, textY, sideX + SIDEBAR_W - 8, textY + 13, RadianceTheme.widgetBgHover);

                    // Override indicator dot
                    int dotColor = childOverride ? RadianceTheme.textAccent : RadianceTheme.textSecondary;
                    ctx.fill(sideX + 10, textY + 3, sideX + 14, textY + 7, dotColor);

                    RadianceTheme.drawOutlinedText(ctx, this.textRenderer,
                        Text.literal(childName), sideX + 18, textY + 2,
                        childOverride ? RadianceTheme.textPrimary : RadianceTheme.textSecondary);
                    textY += 14;
                }
            }
        }
    }

    private void renderSidebarButton(DrawContext ctx, int x, int y, int w, String label, int mouseX, int mouseY) {
        boolean hovered = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + SIDE_BTN_H;
        int bg = hovered ? RadianceTheme.widgetBgHover : RadianceTheme.widgetBg;
        ctx.fill(x, y, x + w, y + SIDE_BTN_H, bg);
        drawBorder(ctx, x, y, w, SIDE_BTN_H, RadianceTheme.borderDefault);
        int textX = x + (w - this.textRenderer.getWidth(label)) / 2;
        RadianceTheme.drawOutlinedText(ctx, this.textRenderer,
            Text.literal(label), textX, y + 4, RadianceTheme.textPrimary);
    }

    /** Returns the sidebar button index (0=Apply, 1=Copy, 2=Export) or -1. */
    private int getSidebarButtonAt(int mouseX, int mouseY) {
        if (sidebarBtnY < 0) return -1;
        int sideX = this.width - SIDEBAR_W - MARGIN;
        int btnX = sideX + 8;
        int btnW = SIDEBAR_W - 16;
        if (mouseX < btnX || mouseX >= btnX + btnW) return -1;

        for (int i = 0; i < 3; i++) {
            int btnY = sidebarBtnY + i * (SIDE_BTN_H + SIDE_BTN_PAD);
            if (mouseY >= btnY && mouseY < btnY + SIDE_BTN_H) return i;
        }
        return -1;
    }

    private void renderHoverTooltip(DrawContext ctx, int mouseX, int mouseY) {
        if (hoveredIndex < 0 || hoveredIndex >= filteredEntries.size()) return;
        MaterialEntry entry = filteredEntries.get(hoveredIndex);
        MaterialData d = entry.data;

        String line1 = entry.displayName;
        String line2 = String.format("F0R:%.0f%% Met:%.0f%% Rgh:%d%%",
            d.f0R / 10.0, d.metallic / 10.0, d.roughness);

        int tw = Math.max(this.textRenderer.getWidth(line1), this.textRenderer.getWidth(line2)) + 8;
        int th = 24;
        int tx = mouseX + 12;
        int ty = mouseY - 28;

        // Clamp to screen
        if (tx + tw > this.width) tx = mouseX - tw - 4;
        if (ty < 0) ty = mouseY + 16;

        ctx.fill(tx, ty, tx + tw, ty + th, RadianceTheme.dropdownBg);
        drawBorder(ctx, tx, ty, tw, th, RadianceTheme.borderDefault);
        RadianceTheme.drawOutlinedText(ctx, this.textRenderer,
            Text.literal(line1), tx + 4, ty + 2, RadianceTheme.textPrimary);
        RadianceTheme.drawOutlinedText(ctx, this.textRenderer,
            Text.literal(line2), tx + 4, ty + 12, RadianceTheme.textSecondary);
    }

    private int drawProperty(DrawContext ctx, int x, int y, String label, String value) {
        RadianceTheme.drawOutlinedText(ctx, this.textRenderer,
            Text.literal(label), x, y, RadianceTheme.textSecondary);
        RadianceTheme.drawOutlinedText(ctx, this.textRenderer,
            Text.literal(value), x + 50, y, RadianceTheme.textPrimary);
        return y + 10;
    }

    private static final String[] CONTEXT_KEYS = {
        "radiance.materials.context.copy",
        "radiance.materials.context.paste",
        "radiance.materials.context.exportJson",
        "radiance.materials.context.exportTxt",
        "radiance.materials.context.reset"
    };

    private boolean isContextItemEnabled(int actionIndex, MaterialEntry entry) {
        return switch (actionIndex) {
            case 1 -> clipboardHasData; // Paste requires clipboard data (cached at menu open)
            case 4 -> entry.blockIndex >= 0; // Reset only for real blocks
            default -> true;
        };
    }

    private void renderContextMenu(DrawContext ctx, int mouseX, int mouseY) {
        int menuW = 110;
        int menuH = CONTEXT_KEYS.length * 16 + 4;
        int mx = contextMenuX;
        int my = contextMenuY;

        // Clamp to screen
        if (mx + menuW > this.width) mx = this.width - menuW;
        if (my + menuH > this.height) my = this.height - menuH;

        ctx.fill(mx, my, mx + menuW, my + menuH, RadianceTheme.dropdownBg);
        drawBorder(ctx, mx, my, menuW, menuH, RadianceTheme.borderDefault);

        MaterialEntry targetEntry = (contextMenuTarget >= 0 && contextMenuTarget < filteredEntries.size())
            ? filteredEntries.get(contextMenuTarget) : null;

        for (int i = 0; i < CONTEXT_KEYS.length; i++) {
            int iy = my + 2 + i * 16;
            boolean enabled = targetEntry != null && isContextItemEnabled(i, targetEntry);
            boolean hovered = enabled && mouseX >= mx && mouseX < mx + menuW && mouseY >= iy && mouseY < iy + 16;
            if (hovered) {
                ctx.fill(mx + 1, iy, mx + menuW - 1, iy + 16, RadianceTheme.widgetBgHover);
            }
            if (enabled) {
                RadianceTheme.drawOutlinedText(ctx, this.textRenderer,
                    Text.translatable(CONTEXT_KEYS[i]), mx + 6, iy + 4, RadianceTheme.textPrimary);
            } else {
                ctx.drawText(this.textRenderer, Text.translatable(CONTEXT_KEYS[i]),
                    mx + 6, iy + 4, 0x66909090, false);
            }
        }
    }

    private void drawBorder(DrawContext ctx, int x, int y, int w, int h, int color) {
        ctx.fill(x, y, x + w, y + 1, color);        // top
        ctx.fill(x, y + h - 1, x + w, y + h, color); // bottom
        ctx.fill(x, y, x + 1, y + h, color);         // left
        ctx.fill(x + w - 1, y, x + w, y + h, color); // right
    }

    // ── Input handling ──

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // Breadcrumb navigation
        if (RadianceTheme.handleBreadcrumbClick(mouseX, mouseY, parent)) return true;
        // Close context menu on any click outside it
        if (contextMenuOpen) {
            if (button == 0) {
                int idx = getContextMenuItem((int) mouseX, (int) mouseY);
                if (idx >= 0) {
                    MaterialEntry targetEntry = (contextMenuTarget >= 0 && contextMenuTarget < filteredEntries.size())
                        ? filteredEntries.get(contextMenuTarget) : null;
                    if (targetEntry != null && isContextItemEnabled(idx, targetEntry)) {
                        executeContextAction(idx);
                    }
                }
            }
            contextMenuOpen = false;
            return true;
        }

        // Category tabs
        int tabY = HEADER_H;
        int tabX = MARGIN;
        if (mouseY >= tabY && mouseY < tabY + TABS_H) {
            for (int i = 0; i < CATEGORIES.length; i++) {
                int tw = this.textRenderer.getWidth(getTabLabel(CATEGORIES[i])) + 12;
                if (mouseX >= tabX && mouseX < tabX + tw) {
                    selectedCategory = i;
                    rebuildFilteredList();
                    return true;
                }
                tabX += tw + 2;
            }
        }

        // Sidebar child variant clicks
        if (button == 0 && sidebarChildrenTopY >= 0 && !sidebarChildOrdinals.isEmpty()) {
            int sideX = this.width - SIDEBAR_W - MARGIN;
            if (mouseX >= sideX + 8 && mouseX < sideX + SIDEBAR_W - 8) {
                int childIdx = ((int) mouseY - sidebarChildrenTopY) / 14;
                if (childIdx >= 0 && childIdx < sidebarChildOrdinals.size()
                        && mouseY >= sidebarChildrenTopY) {
                    int childOrdinal = sidebarChildOrdinals.get(childIdx);
                    MaterialsSettingsScreen.setCurrentBlockIndex(childOrdinal);
                    this.client.setScreen(new MaterialsSettingsScreen(this));
                    return true;
                }
            }
        }

        // Sidebar action buttons
        if (button == 0) {
            int sideBtn = getSidebarButtonAt((int) mouseX, (int) mouseY);
            if (sideBtn >= 0) {
                executeSidebarAction(sideBtn);
                return true;
            }
        }

        // Grid clicks with proper double-click timing
        if (hoveredIndex >= 0 && button == 0) {
            long now = System.currentTimeMillis();
            if (hoveredIndex == lastClickedIndex && now - lastClickTime < 400) {
                onApplyMaterial(hoveredIndex);
                lastClickTime = 0;
            } else {
                lastClickTime = now;
                lastClickedIndex = hoveredIndex;
            }
            selectedIndex = hoveredIndex;
            return true;
        }

        // Right-click on grid: context menu
        if (hoveredIndex >= 0 && button == 1) {
            selectedIndex = hoveredIndex;
            contextMenuOpen = true;
            contextMenuX = (int) mouseX;
            contextMenuY = (int) mouseY;
            contextMenuTarget = hoveredIndex;
            clipboardHasData = MaterialClipboard.hasData();
            return true;
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double hAmount, double vAmount) {
        int gridLeft = MARGIN;
        int gridRight = this.width - SIDEBAR_W - MARGIN;
        int gridTop = HEADER_H + TABS_H + SEARCH_H + 8;
        int gridBottom = this.height - 4;
        if (mouseX >= gridLeft && mouseX < gridRight && mouseY >= gridTop && mouseY < gridBottom) {
            scrollOffset -= vAmount * 24;
            scrollOffset = Math.max(0, Math.min(scrollOffset, maxScroll()));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, hAmount, vAmount);
    }

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        if (RadianceTheme.handlePeekKeyReleased(keyCode)) return true;
        return super.keyReleased(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (RadianceTheme.handlePeekKeyPressed(keyCode)) return true;
        if (keyCode == 256) { // Escape
            if (contextMenuOpen) {
                contextMenuOpen = false;
                return true;
            }
            close();
            return true;
        }

        // Ctrl+C / Ctrl+V
        if ((modifiers & 2) != 0) {
            if (keyCode == 67) { // C
                if (selectedIndex >= 0 && selectedIndex < filteredEntries.size()) {
                    MaterialClipboard.copy(filteredEntries.get(selectedIndex).data);
                    showToast("Copied: " + filteredEntries.get(selectedIndex).displayName);
                }
                return true;
            }
            if (keyCode == 86) { // V
                if (selectedIndex >= 0 && selectedIndex < filteredEntries.size()) {
                    MaterialEntry entry = filteredEntries.get(selectedIndex);
                    int targetIdx = entry.blockIndex >= 0 ? entry.blockIndex : entry.data.findBlockIndex();
                    if (targetIdx >= 0 && MaterialClipboard.paste(targetIdx)) {
                        Options.overwriteConfig();
                        String restoreId = entry.blockId;
                        buildEntryList();
                        rebuildFilteredList();
                        restoreSelection(restoreId);
                        showToast("Pasted material");
                    }
                }
                return true;
            }
        }

        // Arrow key navigation in grid
        if (selectedIndex >= 0) {
            int gridWidth = this.width - SIDEBAR_W - MARGIN * 2;
            int cols = Math.max(1, gridWidth / (CELL_SIZE + CELL_PAD));

            int newIdx = selectedIndex;
            boolean isNav = true;
            switch (keyCode) {
                case 262 -> newIdx++;        // Right
                case 263 -> newIdx--;        // Left
                case 264 -> newIdx += cols;  // Down
                case 265 -> newIdx -= cols;  // Up
                case 257 -> { onApplyMaterial(selectedIndex); return true; } // Enter
                default -> isNav = false;
            }
            if (isNav) {
                if (newIdx >= 0 && newIdx < filteredEntries.size()) {
                    selectedIndex = newIdx;
                    ensureVisible(selectedIndex);
                }
                return true; // consume arrow keys even at boundary
            }
        }

        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void close() {
        MaterialSphereRenderer.clearCache();
        this.client.setScreen(parent);
    }

    @Override
    public void removed() {
        // Safety net: if screen is removed externally (not through close()),
        // ensure native textures are cleaned up to prevent memory leaks
        MaterialSphereRenderer.clearCache();
        super.removed();
    }

    // ── Actions ──

    private void onApplyMaterial(int index) {
        if (index < 0 || index >= filteredEntries.size()) return;
        MaterialEntry entry = filteredEntries.get(index);

        // Find which block to apply to — use the entry's own blockIndex if available
        int targetIdx = entry.blockIndex;
        if (targetIdx < 0) {
            // For saved/pack/preset materials, find matching block by ID
            targetIdx = entry.data.findBlockIndex();
        }
        if (targetIdx >= 0) {
            entry.data.applyToOptions(targetIdx);
            Options.overwriteConfig();
            showToast("Applied: " + entry.displayName);
        }
    }

    private void executeSidebarAction(int actionIndex) {
        if (selectedIndex < 0 || selectedIndex >= filteredEntries.size()) return;
        MaterialEntry entry = filteredEntries.get(selectedIndex);

        switch (actionIndex) {
            case 0 -> onApplyMaterial(selectedIndex);
            case 1 -> {
                MaterialClipboard.copy(entry.data);
                showToast("Copied: " + entry.displayName);
            }
            case 2 -> {
                Path path = MaterialFileManager.saveMaterial(entry.data, entry.data.blockId);
                if (path != null) showToast("Saved: " + path.getFileName());
            }
        }
    }

    private void onImportMaterial() {
        // Reload saved materials list
        buildEntryList();
        rebuildFilteredList();
        showToast("Refreshed material library");
    }

    private void onExportAll() {
        MaterialsPack pack = MaterialsPack.fromCurrentOptions();
        pack.name = "All Materials";
        Path path = MaterialFileManager.savePack(pack, "all-materials");
        if (path != null) {
            showToast("Exported to: " + path.getFileName());
        }
    }

    private void executeContextAction(int actionIndex) {
        if (contextMenuTarget < 0 || contextMenuTarget >= filteredEntries.size()) return;
        MaterialEntry entry = filteredEntries.get(contextMenuTarget);

        switch (actionIndex) {
            case 0 -> { // Copy
                MaterialClipboard.copy(entry.data);
                showToast("Copied: " + entry.displayName);
            }
            case 1 -> { // Paste
                int targetIdx = entry.blockIndex >= 0 ? entry.blockIndex : entry.data.findBlockIndex();
                if (targetIdx >= 0 && MaterialClipboard.paste(targetIdx)) {
                    Options.overwriteConfig();
                    String restoreId = entry.blockId;
                    buildEntryList();
                    rebuildFilteredList();
                    restoreSelection(restoreId);
                    showToast("Pasted material");
                }
            }
            case 2 -> { // Export .radmat
                Path path = MaterialFileManager.saveMaterial(entry.data, entry.data.blockId);
                if (path != null) showToast("Saved: " + path.getFileName());
            }
            case 3 -> { // Export .txt
                Path path = MaterialFileManager.exportMaterialAsText(entry.data, entry.data.blockId);
                if (path != null) showToast("Exported: " + path.getFileName());
            }
            case 4 -> { // Reset Default
                if (entry.blockIndex >= 0) {
                    MaterialBlock mb = MaterialBlock.values()[entry.blockIndex];
                    MaterialData defaults = MaterialData.fromBlock(mb);
                    defaults.applyToOptions(entry.blockIndex);
                    Options.overwriteConfig();
                    String restoreId = entry.blockId;
                    buildEntryList();
                    rebuildFilteredList();
                    restoreSelection(restoreId);
                    showToast("Reset: " + entry.displayName);
                }
            }
        }
    }

    private int getContextMenuItem(int mouseX, int mouseY) {
        int mx = contextMenuX;
        int my = contextMenuY;
        int menuW = 110;
        if (mx + menuW > this.width) mx = this.width - menuW;
        int menuH = CONTEXT_KEYS.length * 16 + 4;
        if (my + menuH > this.height) my = this.height - menuH;

        if (mouseX < mx || mouseX >= mx + menuW) return -1;
        if (mouseY < my + 2 || mouseY >= my + menuH) return -1;
        int idx = (mouseY - my - 2) / 16;
        return (idx >= 0 && idx < CONTEXT_KEYS.length) ? idx : -1;
    }

    // ── Helpers ──

    private double maxScroll() {
        int gridWidth = this.width - SIDEBAR_W - MARGIN * 2;
        int cols = Math.max(1, gridWidth / (CELL_SIZE + CELL_PAD));
        int rows = (filteredEntries.size() + cols - 1) / cols;
        int totalHeight = rows * (CELL_SIZE + CELL_PAD + 14);
        int visibleHeight = this.height - (HEADER_H + TABS_H + SEARCH_H + 8) - 4;
        return Math.max(0, totalHeight - visibleHeight);
    }

    private void ensureVisible(int index) {
        int gridWidth = this.width - SIDEBAR_W - MARGIN * 2;
        int cols = Math.max(1, gridWidth / (CELL_SIZE + CELL_PAD));
        int row = index / cols;
        int cellTop = row * (CELL_SIZE + CELL_PAD + 14);
        int cellBottom = cellTop + CELL_SIZE + 14;
        int visibleTop = (int) scrollOffset;
        int visibleHeight = this.height - (HEADER_H + TABS_H + SEARCH_H + 8) - 4;
        int visibleBottom = visibleTop + visibleHeight;

        if (cellTop < visibleTop) scrollOffset = cellTop;
        else if (cellBottom > visibleBottom) scrollOffset = cellBottom - visibleHeight;
        scrollOffset = Math.max(0, Math.min(scrollOffset, maxScroll()));
    }

    private void restoreSelection(String blockId) {
        selectedIndex = -1;
        for (int i = 0; i < filteredEntries.size(); i++) {
            if (filteredEntries.get(i).blockId.equals(blockId)) {
                selectedIndex = i;
                break;
            }
        }
    }

    private void showToast(String message) {
        toastMessage = message;
        toastExpiry = System.currentTimeMillis() + 2000;
    }
}
