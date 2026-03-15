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
 * Dropdown widget for selecting a procedural noise type (0-7).
 * Modeled on MaterialDropdownWidget with the same themed rendering.
 */
public class NoiseTypeDropdownWidget extends ClickableWidget {

    private static final List<NoiseTypeDropdownWidget> ALL_INSTANCES = new ArrayList<>();

    private static final String[] LABELS = {
        "Simplex", "Worley F1", "Worley F2-F1", "Voronoi",
        "Ridged", "Turbulence", "Marble", "Wood",
        "Checker", "Brick", "Hex", "Scratches",
        "Dots", "Gradient", "Rings", "Crackle"
    };
    private static final int ITEM_HEIGHT = 16;

    private int selected;
    private boolean open = false;
    private int hoveredIndex = -1;
    private final Consumer<Integer> onSelect;

    public NoiseTypeDropdownWidget(int x, int y, int width, int height, Consumer<Integer> onSelect) {
        super(x, y, width, height, Text.empty());
        this.onSelect = onSelect;
        this.selected = 0;
        updateMessage();
        ALL_INSTANCES.add(this);
    }

    private void updateMessage() {
        setMessage(Text.literal(LABELS[selected]));
    }

    public void setNoiseType(int type) {
        this.selected = Math.max(0, Math.min(type, LABELS.length - 1));
        updateMessage();
    }

    public int getSelected() {
        return selected;
    }

    public boolean isOpen() {
        return open;
    }

    public static String getLabel(int type) {
        if (type >= 0 && type < LABELS.length) return LABELS[type];
        return LABELS[0];
    }

    private static void closeAllExcept(NoiseTypeDropdownWidget keep) {
        for (NoiseTypeDropdownWidget w : ALL_INSTANCES) {
            if (w != keep) w.open = false;
        }
    }

    public static void clearInstances() {
        ALL_INSTANCES.clear();
    }

    @Override
    public void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
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

        String label = LABELS[selected];
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

    public void renderDropdownOverlay(DrawContext context, int mouseX, int mouseY) {
        if (!open) return;

        float fade = RadianceTheme.inactiveFadeFactor();
        boolean isActive = (RadianceTheme.activeSlider == this);
        float alphaMult = isActive ? 1f : fade;
        if (alphaMult <= 0f) return;

        context.getMatrices().push();
        context.getMatrices().translate(0, 0, 200);

        int x = getX();
        int y = getY() + getHeight();
        int w = getWidth();
        int totalHeight = LABELS.length * ITEM_HEIGHT + 2;

        context.fill(x - 1, y - 1, x + w + 1, y + totalHeight + 1,
                RadianceTheme.scaleAlpha(RadianceTheme.borderDefault, alphaMult));
        context.fill(x, y, x + w, y + totalHeight,
                RadianceTheme.scaleAlpha(RadianceTheme.dropdownBg, alphaMult));

        var tr = MinecraftClient.getInstance().textRenderer;
        hoveredIndex = -1;
        for (int i = 0; i < LABELS.length; i++) {
            int itemY = y + 1 + i * ITEM_HEIGHT;
            boolean hovered = mouseX >= x && mouseX < x + w && mouseY >= itemY && mouseY < itemY + ITEM_HEIGHT;
            if (hovered) {
                hoveredIndex = i;
                context.fill(x + 1, itemY, x + w - 1, itemY + ITEM_HEIGHT,
                        RadianceTheme.scaleAlpha(RadianceTheme.widgetBgHover, alphaMult));
            }
            if (i == selected) {
                context.fill(x + 1, itemY, x + 3, itemY + ITEM_HEIGHT,
                        RadianceTheme.scaleAlpha(RadianceTheme.SELECTED_BAR, alphaMult));
            }
            RadianceTheme.drawOutlinedText(context, tr, Text.literal(LABELS[i]),
                    x + 6, itemY + 4, RadianceTheme.textPrimary, alphaMult);
        }

        context.getMatrices().pop();
    }

    public boolean isInDropdownBounds(double mouseX, double mouseY) {
        if (!open) return false;
        int x = getX();
        int y = getY() + getHeight();
        int w = getWidth();
        int totalHeight = LABELS.length * ITEM_HEIGHT + 2;
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + totalHeight;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return false;

        if (open && isInDropdownBounds(mouseX, mouseY)) {
            if (hoveredIndex >= 0 && hoveredIndex < LABELS.length) {
                selected = hoveredIndex;
                updateMessage();
                onSelect.accept(selected);
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
