package com.radiance.client.gui.unified;

import java.util.List;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.widget.ClickableWidget;

/**
 * Abstract base for a single row within a SettingsSection.
 * Each row has a fixed height, can contain interactive widgets,
 * and handles mouse/keyboard input delegation.
 *
 * <p>IMPORTANT: Minecraft's {@code ClickableWidget.mouseDragged()} returns {@code true}
 * for ANY valid click button unconditionally (it does NOT check if the widget was
 * actually clicked). This means naively iterating children in mouseDragged would
 * always let the FIRST child steal all drag events. We fix this by tracking which
 * child received the original mouseClicked and routing drags only to that child.
 */
public abstract class SettingsRow {

    /** Standard row height for most rows. */
    public static final int ROW_HEIGHT = 24;

    /** Whether this row is visible (for conditional rows). */
    protected boolean visible = true;

    /** Tooltip text shown when hovering the "?" icon. Null = no icon. */
    String tooltip;

    /** The child that consumed the last mouseClicked — drags/releases route here. */
    private Element clickedChild;

    /**
     * Render this row at the given position.
     *
     * @param context   draw context
     * @param x         left edge X
     * @param y         top edge Y
     * @param width     available width
     * @param mouseX    cursor X
     * @param mouseY    cursor Y
     * @param delta     partial tick
     * @param alphaMult alpha multiplier (for fade effects)
     */
    public abstract void render(DrawContext context, int x, int y, int width,
                                int mouseX, int mouseY, float delta, float alphaMult);

    /** Return the height of this row in pixels. */
    public int getHeight() {
        return ROW_HEIGHT;
    }

    /**
     * Whether this row should occupy the full section content width when the
     * parent section is using grid layout. Dense tables and long diagnostic rows
     * use this to stay readable inside otherwise grid-based pages.
     */
    public boolean isGridFullSpan() {
        return false;
    }

    /** Return all interactive child widgets (for focus/click delegation). */
    public abstract List<? extends Element> children();

    /** Return all clickable widgets (for slider-focus detection). */
    public List<? extends ClickableWidget> clickableWidgets() {
        return List.of();
    }

    /** Handle mouse click. Tracks which child consumed the click for drag routing. */
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        clickedChild = null;
        for (Element child : children()) {
            if (child.mouseClicked(mouseX, mouseY, button)) {
                clickedChild = child;
                return true;
            }
        }
        return false;
    }

    /**
     * Handle mouse drag. Routes ONLY to the child that received the original click.
     * This prevents ClickableWidget.mouseDragged's unconditional {@code return true}
     * from letting the first child steal all drag events.
     */
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (clickedChild != null) {
            return clickedChild.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
        }
        return false;
    }

    /** Handle mouse release. Routes to the clicked child, then clears tracking. */
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (clickedChild != null) {
            boolean result = clickedChild.mouseReleased(mouseX, mouseY, button);
            clickedChild = null;
            return result;
        }
        // Fallback: iterate all children (e.g. if mouseClicked wasn't tracked)
        for (Element child : children()) {
            if (child.mouseReleased(mouseX, mouseY, button)) return true;
        }
        return false;
    }

    /** Handle mouse scroll. Return true if consumed. */
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        return false;
    }

    public boolean isVisible() {
        return visible;
    }

    public void setVisible(boolean visible) {
        this.visible = visible;
    }
}
