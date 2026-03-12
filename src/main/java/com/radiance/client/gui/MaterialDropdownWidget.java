package com.radiance.client.gui;

import com.radiance.client.util.FlameColorant;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * A dropdown widget that lets users select a flame test material.
 * When clicked, shows a floating list of FlameColorant options.
 * Dropdown overlay is rendered via renderDropdownOverlay() called from the screen,
 * not from the entry, to avoid list widget clipping.
 */
public class MaterialDropdownWidget extends ClickableWidget {

    /** Global tracker to ensure only one dropdown is open at a time. */
    private static final List<MaterialDropdownWidget> ALL_INSTANCES = new ArrayList<>();

    private static final FlameColorant[] OPTIONS = FlameColorant.values();
    private static final int ITEM_HEIGHT = 16;

    private FlameColorant selected;
    private boolean open = false;
    private int hoveredIndex = -1;
    private final Consumer<FlameColorant> onSelect;

    public MaterialDropdownWidget(int x, int y, int width, int height, Consumer<FlameColorant> onSelect) {
        super(x, y, width, height, Text.empty());
        this.onSelect = onSelect;
        this.selected = FlameColorant.NONE;
        updateMessage();
        ALL_INSTANCES.add(this);
    }

    private void updateMessage() {
        setMessage(Text.literal(selected.getLabel()));
    }

    public void setMaterial(FlameColorant colorant) {
        this.selected = colorant;
        updateMessage();
    }

    public void updateFromWavelength(int nm) {
        FlameColorant match = FlameColorant.fromWavelength(nm);
        this.selected = match;
        updateMessage();
    }

    public FlameColorant getSelected() {
        return selected;
    }

    public boolean isOpen() {
        return open;
    }

    private static void closeAllExcept(MaterialDropdownWidget keep) {
        for (MaterialDropdownWidget w : ALL_INSTANCES) {
            if (w != keep) w.open = false;
        }
    }

    /** Call from screen to clean up static references when screen closes. */
    public static void clearInstances() {
        ALL_INSTANCES.clear();
    }

    @Override
    public void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
        // Apply inactive fade if another widget is the active slider
        float fade = RadianceTheme.inactiveFadeFactor();
        boolean isActive = (RadianceTheme.activeSlider == this);
        float alphaMult = isActive ? 1f : fade;
        if (alphaMult <= 0f) return;

        int x = getX();
        int y = getY();
        int w = getWidth();
        int h = getHeight();

        context.fill(x, y, x + w, y + h, RadianceTheme.scaleAlpha(RadianceTheme.dropdownBg, alphaMult));
        int borderColor = (this.isFocused() || open)
                ? RadianceTheme.scaleAlpha(RadianceTheme.borderFocused, alphaMult)
                : RadianceTheme.scaleAlpha(RadianceTheme.borderDefault, alphaMult);
        context.drawBorder(x, y, w, h, borderColor);

        String label = selected.getLabel();
        var tr = MinecraftClient.getInstance().textRenderer;
        int textWidth = tr.getWidth(label);
        int textX = x + (w - textWidth) / 2;
        int textY = y + (h - 8) / 2;
        RadianceTheme.drawOutlinedText(context, tr, Text.literal(label), textX, textY,
                RadianceTheme.textPrimary, alphaMult);

        String arrow = open ? "\u25B2" : "\u25BC";
        RadianceTheme.drawOutlinedText(context, tr, Text.literal(arrow), x + w - 10, textY,
                RadianceTheme.textSecondary, alphaMult);
    }

    /**
     * Render the dropdown overlay. Called from the SCREEN's render method
     * (after the body list widget finishes) so it draws above all entries.
     */
    public void renderDropdownOverlay(DrawContext context, int mouseX, int mouseY) {
        if (!open) return;

        // Apply inactive fade if another widget is the active slider
        float fade = RadianceTheme.inactiveFadeFactor();
        boolean isActive = (RadianceTheme.activeSlider == this);
        float alphaMult = isActive ? 1f : fade;
        if (alphaMult <= 0f) return;

        // Push z forward so dropdown renders above list entries below
        context.getMatrices().push();
        context.getMatrices().translate(0, 0, 200);

        int x = getX();
        int y = getY() + getHeight();
        int w = getWidth();
        int totalHeight = OPTIONS.length * ITEM_HEIGHT + 2;

        // Solid opaque background with border
        context.fill(x - 1, y - 1, x + w + 1, y + totalHeight + 1,
                RadianceTheme.scaleAlpha(RadianceTheme.borderDefault, alphaMult));
        context.fill(x, y, x + w, y + totalHeight,
                RadianceTheme.scaleAlpha(RadianceTheme.dropdownBg, alphaMult));

        var tr = MinecraftClient.getInstance().textRenderer;
        hoveredIndex = -1;
        for (int i = 0; i < OPTIONS.length; i++) {
            int itemY = y + 1 + i * ITEM_HEIGHT;
            boolean hovered = mouseX >= x && mouseX < x + w && mouseY >= itemY && mouseY < itemY + ITEM_HEIGHT;
            if (hovered) {
                hoveredIndex = i;
                context.fill(x + 1, itemY, x + w - 1, itemY + ITEM_HEIGHT,
                        RadianceTheme.scaleAlpha(RadianceTheme.widgetBgHover, alphaMult));
            }
            if (OPTIONS[i] == selected) {
                context.fill(x + 1, itemY, x + 3, itemY + ITEM_HEIGHT,
                        RadianceTheme.scaleAlpha(RadianceTheme.SELECTED_BAR, alphaMult));
            }
            RadianceTheme.drawOutlinedText(context, tr, Text.literal(OPTIONS[i].getLabel()),
                    x + 6, itemY + 4, RadianceTheme.textPrimary, alphaMult);
        }

        context.getMatrices().pop();
    }

    /**
     * Check if a point is within the dropdown overlay bounds.
     */
    public boolean isInDropdownBounds(double mouseX, double mouseY) {
        if (!open) return false;
        int x = getX();
        int y = getY() + getHeight();
        int w = getWidth();
        int totalHeight = OPTIONS.length * ITEM_HEIGHT + 2;
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + totalHeight;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return false;

        // Click on dropdown list item
        if (open && isInDropdownBounds(mouseX, mouseY)) {
            if (hoveredIndex >= 0 && hoveredIndex < OPTIONS.length) {
                FlameColorant choice = OPTIONS[hoveredIndex];
                if (choice != FlameColorant.CUSTOM) {
                    selected = choice;
                    updateMessage();
                    onSelect.accept(choice);
                }
            }
            open = false;
            return true;
        }

        // Click on button itself
        if (this.isMouseOver(mouseX, mouseY)) {
            if (!open) {
                closeAllExcept(this);
                open = true;
            } else {
                open = false;
            }
            return true;
        }

        // Click outside — close
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
