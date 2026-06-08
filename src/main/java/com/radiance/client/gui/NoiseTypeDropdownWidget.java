package com.radiance.client.gui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Noise type selector: grid popup showing all types at once, grouped by category.
 * No scrolling — everything visible in a compact grid.
 */
public class NoiseTypeDropdownWidget extends ClickableWidget {

    private static final List<NoiseTypeDropdownWidget> ALL_INSTANCES = new ArrayList<>();

    // Categories with their noise type IDs
    private static final int[][] CATEGORIES = {
        {0, 20, 4, 5},           // Smooth: Simplex, Value, Ridged, Turbulence
        {1, 2, 3, 17},           // Cellular: Worley F1, F2-F1, Voronoi, Cellular
        {8, 9, 10, 12, 23, 19},  // Geometric: Checker, Brick, Hex, Dots, Diamond, Fabric
        {6, 7, 15, 18, 11},      // Organic: Marble, Wood, Crackle, Erosion, Scratches
        {16, 14, 13, 22, 21},    // Simple: Waves, Rings, Gradient, Sine Lines, Hash Grid
    };
    private static final String[] CAT_NAMES = {"Smooth", "Cellular", "Geometric", "Organic", "Simple"};

    private static final String[] TYPE_LABELS = new String[24];
    private static final int[] TYPE_COST = new int[24]; // 0=light 1=med 2=heavy
    static {
        String[] labels = {
            "Simplex",  "Worley F1", "Worley F2-F1", "Voronoi",
            "Ridged",   "Turbulence","Marble",        "Wood",
            "Checker",  "Brick",     "Hex",           "Scratches",
            "Dots",     "Gradient",  "Rings",         "Crackle",
            "Waves",    "Cellular",  "Erosion",       "Fabric",
            "Value",    "Hash Grid", "Sine Lines",    "Diamond"
        };
        int[] costs = {1,2,2,2, 1,1,1,1, 0,0,0,1, 0,0,0,2, 0,2,1,0, 0,0,0,0};
        for (int i = 0; i < labels.length; i++) { TYPE_LABELS[i] = labels[i]; TYPE_COST[i] = costs[i]; }
    }
    private static final int[] COST_COLORS = {0xFF55FF55, 0xFFFFFF55, 0xFFFF5555};

    private static final int CELL_W = 72;
    private static final int CELL_H = 14;
    private static final int CAT_HEADER_H = 11;
    private static final int COLS = 6; // max items per row

    private int selected;
    private boolean open = false;
    private int hoveredTypeId = -1;
    private final Consumer<Integer> onSelect;

    public NoiseTypeDropdownWidget(int x, int y, int width, int height, Consumer<Integer> onSelect) {
        super(x, y, width, height, Text.empty());
        this.onSelect = onSelect;
        this.selected = 0;
        updateMessage();
        ALL_INSTANCES.add(this);
    }

    private void updateMessage() {
        String label = (selected >= 0 && selected < TYPE_LABELS.length && TYPE_LABELS[selected] != null)
                ? TYPE_LABELS[selected] : "Simplex";
        setMessage(Text.literal(label));
    }

    public void setNoiseType(int type) { this.selected = Math.max(0, Math.min(type, 23)); updateMessage(); }
    public int getSelected() { return selected; }
    public boolean isOpen() { return open; }
    public static String getLabel(int type) {
        return (type >= 0 && type < TYPE_LABELS.length && TYPE_LABELS[type] != null) ? TYPE_LABELS[type] : "Simplex";
    }
    private static void closeAllExcept(NoiseTypeDropdownWidget keep) {
        for (NoiseTypeDropdownWidget w : ALL_INSTANCES) { if (w != keep) w.open = false; }
    }
    public static void clearInstances() { ALL_INSTANCES.clear(); }

    private int gridWidth() {
        int maxCols = 0;
        for (int[] cat : CATEGORIES) maxCols = Math.max(maxCols, cat.length);
        return Math.min(maxCols, COLS) * CELL_W + 8;
    }

    private int gridHeight() {
        int h = 4;
        for (int[] cat : CATEGORIES) {
            h += CAT_HEADER_H; // category label
            int rows = (cat.length + COLS - 1) / COLS;
            h += rows * CELL_H;
        }
        return h;
    }

    @Override
    public void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
        float fade = RadianceTheme.inactiveFadeFactor();
        boolean isActive = (RadianceTheme.activeSlider == this);
        float alphaMult = isActive ? 1f : fade;
        if (alphaMult <= 0f) return;

        int x = getX(), y = getY(), w = getWidth(), h = getHeight();
        context.fill(x, y, x + w, y + h, RadianceTheme.scaleAlpha(RadianceTheme.dropdownBg, alphaMult));
        int borderColor = (this.isFocused() || open)
                ? RadianceTheme.scaleAlpha(RadianceTheme.borderFocused, alphaMult)
                : RadianceTheme.scaleAlpha(RadianceTheme.borderDefault, alphaMult);
        context.drawBorder(x, y, w, h, borderColor);

        var tr = MinecraftClient.getInstance().textRenderer;
        String label = getMessage().getString();
        RadianceTheme.drawOutlinedText(context, tr, Text.literal(label),
                x + (w - tr.getWidth(label)) / 2, y + (h - 8) / 2,
                RadianceTheme.textPrimary, alphaMult);
        RadianceTheme.drawOutlinedText(context, tr, Text.literal(open ? "\u25B2" : "\u25BC"),
                x + w - 10, y + (h - 8) / 2, RadianceTheme.textSecondary, alphaMult);
    }

    public void renderDropdownOverlay(DrawContext context, int mouseX, int mouseY) {
        if (!open) return;

        context.getMatrices().push();
        context.getMatrices().translate(0, 0, 300);

        int gx = getX(), gy = getY() + getHeight();
        int gw = gridWidth(), gh = gridHeight();

        // Background
        context.fill(gx - 1, gy - 1, gx + gw + 1, gy + gh + 1,
                RadianceTheme.scaleAlpha(RadianceTheme.borderDefault, 1f));
        context.fill(gx, gy, gx + gw, gy + gh,
                RadianceTheme.scaleAlpha(0xFF1a1a24, 1f));

        var tr = MinecraftClient.getInstance().textRenderer;
        hoveredTypeId = -1;
        int drawY = gy + 2;

        for (int ci = 0; ci < CATEGORIES.length; ci++) {
            // Category header
            int headerColor = 0xFF333344;
            context.fill(gx + 2, drawY, gx + gw - 2, drawY + CAT_HEADER_H, headerColor);
            String catName = CAT_NAMES[ci];
            context.drawText(tr, catName, gx + (gw - tr.getWidth(catName)) / 2, drawY + 2, 0xFF7777AA, false);
            drawY += CAT_HEADER_H;

            // Grid items
            int[] ids = CATEGORIES[ci];
            for (int j = 0; j < ids.length; j++) {
                int col = j % COLS;
                int ix = gx + 4 + col * CELL_W;
                int iy = drawY + (j / COLS) * CELL_H;
                int id = ids[j];

                boolean hovered = mouseX >= ix && mouseX < ix + CELL_W - 2
                        && mouseY >= iy && mouseY < iy + CELL_H;
                if (hovered) {
                    hoveredTypeId = id;
                    context.fill(ix, iy, ix + CELL_W - 2, iy + CELL_H, 0xFF3a3a50);
                }
                if (id == selected) {
                    context.fill(ix, iy, ix + 2, iy + CELL_H, RadianceTheme.SELECTED_BAR);
                }

                // Name
                String name = TYPE_LABELS[id];
                int textY = iy + (CELL_H - 8) / 2;
                context.drawText(tr, name, ix + 4, textY, hovered ? 0xFFFFFFFF : 0xFFCCCCCC, false);

                // Cost dot (small colored circle indicator)
                int costColor = COST_COLORS[TYPE_COST[id]];
                context.fill(ix + CELL_W - 8, textY + 2, ix + CELL_W - 4, textY + 6, costColor);
            }
            int rows = (ids.length + COLS - 1) / COLS;
            drawY += rows * CELL_H;
        }

        context.getMatrices().pop();
    }

    public boolean isInDropdownBounds(double mouseX, double mouseY) {
        if (!open) return false;
        int gx = getX(), gy = getY() + getHeight();
        return mouseX >= gx && mouseX < gx + gridWidth() && mouseY >= gy && mouseY < gy + gridHeight();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return false;
        if (open && isInDropdownBounds(mouseX, mouseY)) {
            if (hoveredTypeId >= 0) {
                selected = hoveredTypeId;
                updateMessage();
                onSelect.accept(selected);
            }
            open = false;
            return true;
        }
        if (this.isMouseOver(mouseX, mouseY)) {
            if (!open) { closeAllExcept(this); open = true; }
            else { open = false; }
            return true;
        }
        if (open) { open = false; return true; }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (!open) return false;
        if (this.isMouseOver(mouseX, mouseY) || (open && isInDropdownBounds(mouseX, mouseY))) {
            int dir = verticalAmount > 0 ? -1 : 1;
            selected = Math.floorMod(selected + dir, 24);
            updateMessage();
            onSelect.accept(selected);
            return true;
        }
        return false;
    }

    @Override
    protected void appendClickableNarrations(NarrationMessageBuilder builder) {
        this.appendDefaultNarrations(builder);
    }
}
