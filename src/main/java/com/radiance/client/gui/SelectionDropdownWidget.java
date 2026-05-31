package com.radiance.client.gui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;

/**
 * Generic dropdown widget for selecting from a list of string options.
 * Shares the same overlay rendering and click interception infrastructure
 * as MaterialDropdownWidget via the static ALL_DROPDOWNS list.
 */
public class SelectionDropdownWidget extends ClickableWidget {

    private static final List<SelectionDropdownWidget> ALL_DROPDOWNS = new ArrayList<>();
    private static final int ITEM_HEIGHT = 14;

    private final String label;
    private final String[] options;
    private int selectedIndex;
    private boolean open = false;
    private int hoveredIndex = -1;
    private final IntConsumer onSelect;

    public SelectionDropdownWidget(int x, int y, int width, int height,
                                    String label, String[] options, int initialIndex,
                                    IntConsumer onSelect) {
        super(x, y, width, height, Text.empty());
        this.label = label;
        this.options = options;
        this.selectedIndex = Math.max(0, Math.min(initialIndex, options.length - 1));
        this.onSelect = onSelect;
        updateMessage();
        ALL_DROPDOWNS.add(this);
    }

    private void updateMessage() {
        setMessage(Text.literal(label + ": " + options[selectedIndex]));
    }

    public int getSelectedIndex() { return selectedIndex; }

    public boolean isOpen() { return open; }

    private static void closeAllExcept(SelectionDropdownWidget keep) {
        for (SelectionDropdownWidget w : ALL_DROPDOWNS) {
            if (w != keep) w.open = false;
        }
        // Also close any MaterialDropdownWidgets
        MaterialDropdownWidget.closeAll();
    }

    public static void clearInstances() {
        ALL_DROPDOWNS.clear();
    }

    /** Render all open dropdown overlays. Call after scissor is disabled. */
    public static void renderAllOverlays(DrawContext context, int mouseX, int mouseY) {
        for (SelectionDropdownWidget w : ALL_DROPDOWNS) {
            if (w.isOpen()) {
                w.renderDropdownOverlay(context, mouseX, mouseY);
            }
        }
    }

    /** Check if any open dropdown wants to handle a click. Returns true if consumed. */
    public static boolean handleOverlayClick(double mouseX, double mouseY, int button) {
        for (SelectionDropdownWidget w : ALL_DROPDOWNS) {
            if (w.isOpen()) {
                if (w.isInDropdownBounds(mouseX, mouseY)) {
                    return w.mouseClicked(mouseX, mouseY, button);
                }
                w.open = false;
                return true;
            }
        }
        return false;
    }

    private int getDropdownY() {
        int totalHeight = options.length * ITEM_HEIGHT + 2;
        int screenHeight = MinecraftClient.getInstance().getWindow().getScaledHeight();
        int downY = getY() + getHeight();
        if (downY + totalHeight > screenHeight - 4) {
            return getY() - totalHeight;
        }
        return downY;
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
        int textX = x + 6;
        int textY = y + (h - 8) / 2;
        String arrow = open ? "\u25B2" : "\u25BC";
        int arrowW = tr.getWidth(arrow);
        int arrowX = x + w - arrowW - 6;
        Text display = RadianceTheme.trimText(tr, Text.literal(label + ": " + options[selectedIndex]),
            Math.max(24, arrowX - textX - 4));
        RadianceTheme.drawOutlinedText(context, tr, display, textX, textY,
                RadianceTheme.textPrimary, alphaMult);

        RadianceTheme.drawOutlinedText(context, tr, Text.literal(arrow), arrowX, textY,
                RadianceTheme.textSecondary, alphaMult);
    }

    public void renderDropdownOverlay(DrawContext context, int mouseX, int mouseY) {
        if (!open) return;

        float fade = RadianceTheme.inactiveFadeFactor();
        float alphaMult = (RadianceTheme.activeSlider == this) ? 1f : fade;
        if (alphaMult <= 0f) return;

        context.getMatrices().push();
        context.getMatrices().translate(0, 0, 200);

        int x = getX();
        int y = getDropdownY();
        int w = getWidth();
        int totalHeight = options.length * ITEM_HEIGHT + 2;

        context.fill(x - 1, y - 1, x + w + 1, y + totalHeight + 1,
                RadianceTheme.scaleAlpha(RadianceTheme.borderDefault, alphaMult));
        context.fill(x, y, x + w, y + totalHeight,
                RadianceTheme.scaleAlpha(RadianceTheme.dropdownBg, alphaMult));

        var tr = MinecraftClient.getInstance().textRenderer;
        hoveredIndex = -1;
        for (int i = 0; i < options.length; i++) {
            int itemY = y + 1 + i * ITEM_HEIGHT;
            boolean hovered = mouseX >= x && mouseX < x + w && mouseY >= itemY && mouseY < itemY + ITEM_HEIGHT;
            if (hovered) {
                hoveredIndex = i;
                context.fill(x + 1, itemY, x + w - 1, itemY + ITEM_HEIGHT,
                        RadianceTheme.scaleAlpha(RadianceTheme.widgetBgHover, alphaMult));
            }
            if (i == selectedIndex) {
                context.fill(x + 1, itemY, x + 3, itemY + ITEM_HEIGHT,
                        RadianceTheme.scaleAlpha(RadianceTheme.SELECTED_BAR, alphaMult));
            }
            RadianceTheme.drawOutlinedText(context, tr, Text.literal(options[i]),
                    x + 6, itemY + 3, RadianceTheme.textPrimary, alphaMult);
        }

        context.getMatrices().pop();
    }

    public boolean isInDropdownBounds(double mouseX, double mouseY) {
        if (!open) return false;
        int x = getX();
        int y = getDropdownY();
        int w = getWidth();
        int totalHeight = options.length * ITEM_HEIGHT + 2;
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + totalHeight;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return false;

        if (open && isInDropdownBounds(mouseX, mouseY)) {
            if (hoveredIndex >= 0 && hoveredIndex < options.length) {
                selectedIndex = hoveredIndex;
                updateMessage();
                onSelect.accept(selectedIndex);
            }
            open = false;
            return true;
        }

        if (this.isMouseOver(mouseX, mouseY)) {
            if (!open) {
                closeAllExcept(this);
                open = true;
            } else {
                open = false;
            }
            return true;
        }

        if (open) {
            open = false;
            return true;
        }

        return false;
    }

    @Override
    protected void appendClickableNarrations(NarrationMessageBuilder builder) {
        this.appendDefaultNarrations(builder);
    }
}
