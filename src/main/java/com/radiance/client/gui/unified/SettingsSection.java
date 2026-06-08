package com.radiance.client.gui.unified;

import static net.minecraft.client.option.GameOptions.getGenericValueText;

import com.radiance.client.gui.RadianceTheme;
import com.radiance.client.gui.ResettableSliderWidget;
import com.radiance.client.gui.unified.rows.*;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.IntFunction;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.text.Text;

/**
 * A collapsible section within the content panel.
 * Contains a header (category name + collapse toggle) and a list of SettingsRow entries.
 * Supports smooth collapse/expand animation.
 */
public class SettingsSection {

    private static final int HEADER_HEIGHT = 22;
    private static final long COLLAPSE_ANIM_MS = 150;

    private static final int DESCRIPTION_PAD = 4;
    private static final int GRID_CELL_WIDTH = 300;
    private static final int GRID_GAP = 14;
    private static final int GRID_MAX_COLUMNS = 4;

    public enum LayoutMode {
        GRID,
        LINEAR
    }

    private final Text title;
    private Text description;
    private final List<SettingsRow> rows = new ArrayList<>();
    private List<SettingsRow> lastAddedRows = List.of();
    private LayoutMode layoutMode = LayoutMode.GRID;

    private boolean collapsed = false;
    private boolean collapsible = false;
    private long collapseAnimStartMs = 0;
    private boolean animating = false;
    private boolean animTargetCollapsed = false;

    /** Height of content rows when fully expanded. */
    private int expandedContentHeight = 0;

    // ── Tooltip state (shared across all sections, read by ContentPanelWidget) ──
    static String hoveredTooltip;
    static int hoveredTooltipX;
    static int hoveredTooltipY;

    private static final int ICON_SIZE = 7;

    /** The row that consumed the last mouseClicked — drags/releases route here. */
    private SettingsRow clickedRow;

    public SettingsSection(Text title) {
        this.title = title;
    }

    public SettingsSection(String translationKey) {
        this(Text.translatable(translationKey));
    }

    // ── Builder API ──

    /** Use fixed-cell responsive grid layout for this section. */
    public SettingsSection setGrid() {
        this.layoutMode = LayoutMode.GRID;
        return this;
    }

    /** Use legacy vertical row layout for dense/status sections. */
    public SettingsSection setLinear() {
        this.layoutMode = LayoutMode.LINEAR;
        return this;
    }

    public SettingsSection setLayoutMode(LayoutMode layoutMode) {
        this.layoutMode = layoutMode != null ? layoutMode : LayoutMode.GRID;
        return this;
    }

    /** Set a brief description shown below the section header in dim text. */
    public SettingsSection setDescription(String desc) {
        this.description = Text.literal(desc);
        return this;
    }

    /** Make this section collapsible (header click toggles expand/collapse). Default: false. */
    public SettingsSection setCollapsible(boolean collapsible) {
        this.collapsible = collapsible;
        return this;
    }

    /** Attach a tooltip to the most recently added row. Shows a tiny "?" icon on hover. */
    public SettingsSection tooltip(String text) {
        for (SettingsRow row : lastAddedRows) {
            row.tooltip = text;
        }
        return this;
    }

    /** Add a raw row. */
    public SettingsSection addRow(SettingsRow row) {
        rows.add(row);
        lastAddedRows = List.of(row);
        return this;
    }

    /** Add a read-only diagnostic/status row. */
    public SettingsSection addInfo(String label, String value) {
        SettingsRow row = new InfoRow(label, value);
        rows.add(row);
        lastAddedRows = List.of(row);
        return this;
    }

    /** Add a slider row. */
    public SettingsSection addSlider(ResettableSliderWidget slider) {
        SettingsRow row = new SliderRow(slider);
        rows.add(row);
        lastAddedRows = List.of(row);
        return this;
    }

    /** Add a two-column slider row. */
    public SettingsSection addTwoSliders(ResettableSliderWidget left, ResettableSliderWidget right) {
        if (layoutMode == LayoutMode.GRID) {
            List<SettingsRow> added = new ArrayList<>();
            SettingsRow leftRow = new SliderRow(left);
            rows.add(leftRow);
            added.add(leftRow);
            if (right != null) {
                SettingsRow rightRow = new SliderRow(right);
                rows.add(rightRow);
                added.add(rightRow);
            }
            lastAddedRows = added;
        } else {
            SettingsRow row = new TwoSliderRow(left, right);
            rows.add(row);
            lastAddedRows = List.of(row);
        }
        return this;
    }

    /** Add a toggle widget row (wraps a SimpleOption-created widget). */
    public SettingsSection addToggle(ClickableWidget toggleWidget) {
        SettingsRow row = new ToggleRow(toggleWidget);
        rows.add(row);
        lastAddedRows = List.of(row);
        return this;
    }

    /** Add a two-widget row (any pair of ClickableWidgets). */
    public SettingsSection addTwoWidgets(ClickableWidget left, ClickableWidget right) {
        if (layoutMode == LayoutMode.GRID) {
            List<SettingsRow> added = new ArrayList<>();
            SettingsRow leftRow = new TwoWidgetRow(left, null);
            rows.add(leftRow);
            added.add(leftRow);
            if (right != null) {
                SettingsRow rightRow = new TwoWidgetRow(right, null);
                rows.add(rightRow);
                added.add(rightRow);
            }
            lastAddedRows = added;
        } else {
            SettingsRow row = new TwoWidgetRow(left, right);
            rows.add(row);
            lastAddedRows = List.of(row);
        }
        return this;
    }

    /** Add a button row. */
    public SettingsSection addButton(ButtonWidget button) {
        SettingsRow row = new ButtonRow(button);
        rows.add(row);
        lastAddedRows = List.of(row);
        return this;
    }

    /** Add a launcher row that opens a sub-screen as a modal overlay. */
    public SettingsSection addLauncher(String translationKey, Screen target, RadianceUnifiedScreen screen) {
        SettingsRow row = new LauncherRow(translationKey, target, screen);
        rows.add(row);
        lastAddedRows = List.of(row);
        return this;
    }

    /** Add a launcher row (legacy — opens via setScreen). */
    public SettingsSection addLauncher(String translationKey, Screen target) {
        SettingsRow row = new LauncherRow(translationKey, target);
        rows.add(row);
        lastAddedRows = List.of(row);
        return this;
    }

    // ── Layout ──

    /** Recalculate expandedContentHeight from row heights. */
    public void recalculate(int width) {
        expandedContentHeight = contentHeight(width);
    }

    private int descriptionHeight() {
        return description != null ? 12 : 0;
    }

    /** Total height of this section including header and optional description. */
    public int getHeight() {
        return getHeight(GRID_CELL_WIDTH);
    }

    /** Total height of this section for a specific available content width. */
    public int getHeight(int width) {
        recalculate(width);
        float contentHeight = getAnimatedContentHeight();
        return HEADER_HEIGHT + descriptionHeight() + (int) contentHeight;
    }

    /** Width occupied by this section's content and header rule. */
    public int getVisualWidth(int width) {
        if (layoutMode == LayoutMode.LINEAR) {
            return Math.max(0, width);
        }
        int occupiedWidth = occupiedWidth(buildGridPlacements(width));
        int titleWidth = titleVisualWidth(width);
        return Math.min(Math.max(0, width), Math.max(occupiedWidth, titleWidth));
    }

    private int contentHeight(int width) {
        if (layoutMode == LayoutMode.LINEAR) {
            int total = 0;
            for (SettingsRow row : rows) {
                if (row.isVisible()) total += row.getHeight();
            }
            return total;
        }

        List<RowPlacement> placements = buildGridPlacements(width);
        int height = 0;
        for (RowPlacement placement : placements) {
            height = Math.max(height, placement.y() + placement.height());
        }
        return height;
    }

    private int gridColumns(int width) {
        if (width <= 0) return 1;
        int byTarget = (width + GRID_GAP) / (GRID_CELL_WIDTH + GRID_GAP);
        return Math.max(1, Math.min(GRID_MAX_COLUMNS, byTarget));
    }

    private int gridCellWidth(int width) {
        int columns = gridColumns(width);
        int availableForCells = Math.max(0, width - GRID_GAP * (columns - 1));
        return Math.max(1, Math.min(GRID_CELL_WIDTH, availableForCells / columns));
    }

    private int gridVisualWidth(int width) {
        int columns = gridColumns(width);
        int cellW = gridCellWidth(width);
        return Math.min(Math.max(0, width), cellW * columns + GRID_GAP * (columns - 1));
    }

    private int occupiedWidth(List<RowPlacement> placements) {
        int occupied = 0;
        for (RowPlacement placement : placements) {
            occupied = Math.max(occupied, placement.x() + placement.width());
        }
        return occupied;
    }

    private int titleVisualWidth(int width) {
        var textRenderer = MinecraftClient.getInstance().textRenderer;
        int titleOffset = collapsible ? 28 : 16;
        int titleWidth = textRenderer.getWidth(title) + titleOffset;
        return Math.min(Math.max(0, width), Math.max(80, titleWidth));
    }

    private List<RowPlacement> buildPlacements(int width) {
        if (layoutMode == LayoutMode.LINEAR) {
            List<RowPlacement> placements = new ArrayList<>();
            int y = 0;
            for (SettingsRow row : rows) {
                if (!row.isVisible()) continue;
                int rowHeight = row.getHeight();
                placements.add(new RowPlacement(row, 0, y, Math.max(0, width), rowHeight));
                y += rowHeight;
            }
            return placements;
        }
        return buildGridPlacements(width);
    }

    private List<RowPlacement> buildGridPlacements(int width) {
        List<RowPlacement> placements = new ArrayList<>();
        int columns = gridColumns(width);
        int cellW = gridCellWidth(width);
        int visualW = gridVisualWidth(width);
        int col = 0;
        int y = 0;
        int rowHeight = 0;

        for (SettingsRow row : rows) {
            if (!row.isVisible()) continue;
            int h = row.getHeight();
            if (row.isGridFullSpan()) {
                if (col > 0) {
                    y += rowHeight;
                    col = 0;
                    rowHeight = 0;
                }
                placements.add(new RowPlacement(row, 0, y, visualW, h));
                y += h;
                continue;
            }

            int x = col * (cellW + GRID_GAP);
            placements.add(new RowPlacement(row, x, y, cellW, h));
            rowHeight = Math.max(rowHeight, h);
            col++;
            if (col >= columns) {
                y += rowHeight;
                col = 0;
                rowHeight = 0;
            }
        }

        if (col > 0) {
            y += rowHeight;
        }
        return placements;
    }

    /** Returns the animated content height (interpolates during collapse/expand). */
    private float getAnimatedContentHeight() {
        if (!animating) {
            return collapsed ? 0 : expandedContentHeight;
        }
        long elapsed = System.currentTimeMillis() - collapseAnimStartMs;
        float t = Math.min(1f, elapsed / (float) COLLAPSE_ANIM_MS);
        // Ease out
        t = 1f - (1f - t) * (1f - t);
        if (t >= 1f) {
            animating = false;
            collapsed = animTargetCollapsed;
            return collapsed ? 0 : expandedContentHeight;
        }
        if (animTargetCollapsed) {
            return expandedContentHeight * (1f - t);
        } else {
            return expandedContentHeight * t;
        }
    }

    // ── Rendering ──

    /**
     * Render this section at the given position.
     *
     * @return the total height consumed
     */
    public int render(DrawContext context, int x, int y, int width,
                      int mouseX, int mouseY, float delta, float alphaMult) {
        recalculate(width);

        // Draw header
        renderHeader(context, x, y, width, mouseX, mouseY, alphaMult);

        // Draw description (if set)
        int descH = descriptionHeight();
        if (descH > 0 && alphaMult > 0.01f) {
            var textRenderer = MinecraftClient.getInstance().textRenderer;
            RadianceTheme.drawOutlinedText(context, textRenderer,
                description, x + 14, y + HEADER_HEIGHT + 1,
                RadianceTheme.textSecondary, alphaMult * 0.6f);
        }

        float contentH = getAnimatedContentHeight();
        if (contentH <= 0.5f) {
            return HEADER_HEIGHT + descH;
        }

        // Enable scissor for smooth collapse animation
        int contentTop = y + HEADER_HEIGHT + descH;
        int contentBottom = contentTop + (int) contentH;

        // Render rows + tooltip icons
        List<RowPlacement> placements = buildPlacements(width);
        var textRenderer = MinecraftClient.getInstance().textRenderer;
        for (RowPlacement placement : placements) {
            SettingsRow row = placement.row();
            int rowY = contentTop + placement.y();
            int rowHeight = placement.height();
            if (rowY + rowHeight > contentBottom) break;
            if (rowY >= contentBottom) break;
            row.render(context, x + placement.x(), rowY, placement.width(), mouseX, mouseY, delta, alphaMult);

            // Draw tiny "?" icon if this row has a tooltip
            if (row.tooltip != null && alphaMult > 0.01f) {
                int ix = x + placement.x() + placement.width() - ICON_SIZE - 3;
                int iy = rowY + (rowHeight - ICON_SIZE) / 2;
                boolean iconHovered = mouseX >= ix && mouseX < ix + ICON_SIZE
                    && mouseY >= iy && mouseY < iy + ICON_SIZE;

                // Circle background
                int bg = iconHovered
                    ? RadianceTheme.scaleAlpha(RadianceTheme.textAccent, 0.9f * alphaMult)
                    : RadianceTheme.scaleAlpha(RadianceTheme.textSecondary, 0.35f * alphaMult);
                context.fill(ix, iy, ix + ICON_SIZE, iy + ICON_SIZE, bg);

                // "?" glyph
                int glyphColor = iconHovered ? 0xFFFFFFFF
                    : RadianceTheme.scaleAlpha(RadianceTheme.textSecondary, 0.7f * alphaMult);
                context.drawText(textRenderer, Text.literal("?"),
                    ix + 1, iy - 1, glyphColor, false);

                if (iconHovered) {
                    hoveredTooltip = row.tooltip;
                    hoveredTooltipX = mouseX;
                    hoveredTooltipY = mouseY;
                }
            }
        }

        return HEADER_HEIGHT + descH + (int) contentH;
    }

    private void renderHeader(DrawContext context, int x, int y, int width,
                              int mouseX, int mouseY, float alphaMult) {
        var textRenderer = MinecraftClient.getInstance().textRenderer;
        int textY = y + (HEADER_HEIGHT - 8) / 2;

        if (collapsible) {
            // Collapse indicator: ▶ (collapsed) or ▼ (expanded)
            String indicator = (collapsed && !animating) || (animating && animTargetCollapsed && getAnimatedContentHeight() < expandedContentHeight * 0.5f)
                ? "\u25B6" : "\u25BC";
            RadianceTheme.drawOutlinedText(context, textRenderer,
                Text.literal(indicator), x + 2, textY,
                RadianceTheme.textSecondary, alphaMult);
            // Title offset past indicator
            RadianceTheme.drawOutlinedText(context, textRenderer,
                title, x + 14, textY,
                RadianceTheme.textAccent & 0x00FFFFFF | 0xFF000000, alphaMult);
        } else {
            // No arrow — title flush left
            RadianceTheme.drawOutlinedText(context, textRenderer,
                title, x + 2, textY,
                RadianceTheme.textAccent & 0x00FFFFFF | 0xFF000000, alphaMult);
        }

        // Thin horizontal rule below
        int lineY = y + HEADER_HEIGHT - 2;
        int lineColor = RadianceTheme.withAlpha(0xE8712A, 0.2f * alphaMult);
        context.fill(x, lineY, x + getVisualWidth(width), lineY + 1, lineColor);
    }

    // ── Input handling ──

    /**
     * Handle mouse click. Clicking the header toggles collapse.
     *
     * @return true if consumed
     */
    public boolean mouseClicked(double mouseX, double mouseY, int button,
                                int sectionX, int sectionY, int width) {
        clickedRow = null;

        // Header click toggles collapse (only if collapsible)
        int visualWidth = getVisualWidth(width);
        if (collapsible && button == 0 && mouseY >= sectionY && mouseY < sectionY + HEADER_HEIGHT
                && mouseX >= sectionX && mouseX < sectionX + visualWidth) {
            toggleCollapse();
            return true;
        }

        // Delegate to rows if not collapsed
        if (collapsed && !animating) return false;

        int contentTop = sectionY + HEADER_HEIGHT + descriptionHeight();
        for (RowPlacement placement : buildPlacements(width)) {
            SettingsRow row = placement.row();
            int rowX = sectionX + placement.x();
            int rowY = contentTop + placement.y();
            int rowHeight = placement.height();
            if (mouseY >= rowY && mouseY < rowY + rowHeight
                    && mouseX >= rowX && mouseX < rowX + placement.width()) {
                if (row.mouseClicked(mouseX, mouseY, button)) {
                    clickedRow = row;
                    return true;
                }
                return false;
            }
        }
        return false;
    }

    /**
     * Handle mouse drag. Routes ONLY to the row that received the original click.
     * This prevents rows with vanilla ClickableWidgets from stealing drag events
     * (ClickableWidget.mouseDragged returns true unconditionally for valid buttons).
     */
    public boolean mouseDragged(double mouseX, double mouseY, int button,
                                double deltaX, double deltaY) {
        if (collapsed && !animating) return false;
        if (clickedRow != null) {
            return clickedRow.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
        }
        return false;
    }

    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (collapsed && !animating) return false;
        if (clickedRow != null) {
            boolean result = clickedRow.mouseReleased(mouseX, mouseY, button);
            clickedRow = null;
            return result;
        }
        // Fallback
        for (SettingsRow row : rows) {
            if (!row.isVisible()) continue;
            if (row.mouseReleased(mouseX, mouseY, button)) return true;
        }
        return false;
    }

    // ── Collapse ──

    public void toggleCollapse() {
        if (!collapsible) return;
        animTargetCollapsed = !collapsed;
        collapseAnimStartMs = System.currentTimeMillis();
        animating = true;
    }

    public boolean isCollapsed() {
        return collapsed && !animating;
    }

    public void setCollapsed(boolean collapsed) {
        if (!collapsible) return;
        this.collapsed = collapsed;
        this.animating = false;
    }

    // ── Accessors ──

    public Text getTitle() { return title; }
    public List<SettingsRow> getRows() { return rows; }

    /**
     * Check if any widget in this section is the active slider (for focus mode).
     */
    public boolean containsActiveSlider() {
        if (RadianceTheme.activeSlider == null) return false;
        for (SettingsRow row : rows) {
            for (ClickableWidget w : row.clickableWidgets()) {
                if (w == RadianceTheme.activeSlider) return true;
            }
        }
        return false;
    }

    private record RowPlacement(SettingsRow row, int x, int y, int width, int height) {}
}
