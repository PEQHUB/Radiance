package com.radiance.client.gui.unified.rows;

import com.radiance.client.gui.RadianceTheme;
import com.radiance.client.gui.unified.SettingsRow;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.text.Text;

/**
 * A row with two generic ClickableWidgets side by side.
 * Uses custom rendering: detects widget type (slider vs button/toggle)
 * and renders appropriately. If the right widget is null, the left takes full width.
 */
public class TwoWidgetRow extends SettingsRow {

    private static final int GAP = 8;

    /** Cached "ON" translation for locale-safe toggle state detection. */
    private static final String ON_TEXT = Text.translatable("options.on").getString();

    private final ClickableWidget left;
    private final ClickableWidget right; // nullable

    public TwoWidgetRow(ClickableWidget left, ClickableWidget right) {
        this.left = left;
        this.right = right;
    }

    @Override
    public void render(DrawContext context, int x, int y, int width,
                       int mouseX, int mouseY, float delta, float alphaMult) {
        if (alphaMult <= 0f) return;
        int colW = (width - GAP) / 2;

        int leftW = right != null ? colW : width;
        left.setX(x);
        left.setY(y);
        left.setWidth(leftW);
        renderCustomWidget(context, left, x, y, leftW, getHeight(), mouseX, mouseY);

        if (right != null) {
            int rx = x + colW + GAP;
            right.setX(rx);
            right.setY(y);
            right.setWidth(colW);
            renderCustomWidget(context, right, rx, y, colW, getHeight(), mouseX, mouseY);
        }
    }

    /**
     * Render a widget with custom styling based on its type.
     * SliderWidgets use drawCustomSlider, others use drawCustomButton/Toggle.
     */
    private void renderCustomWidget(DrawContext ctx, ClickableWidget widget,
                                     int x, int y, int w, int h,
                                     int mouseX, int mouseY) {
        TextRenderer renderer = MinecraftClient.getInstance().textRenderer;
        boolean hovered = mouseX >= x && mouseX < x + w
            && mouseY >= y && mouseY < y + h;

        if (widget instanceof SliderWidget slider) {
            // For SliderWidget subclasses (ResettableSliderWidget overrides renderWidget,
            // but plain SliderWidget from SimpleOption doesn't). Delegate to the widget's
            // own render which may be custom.
            widget.render(ctx, mouseX, mouseY, 0);
        } else {
            // Check if this is a boolean toggle (message contains ON/OFF)
            String msg = widget.getMessage().getString();
            boolean looksLikeToggle = msg.contains(ON_TEXT)
                || msg.contains(Text.translatable("options.off").getString());

            if (looksLikeToggle) {
                boolean isOn = msg.contains(ON_TEXT);
                RadianceTheme.drawCustomToggle(ctx, x, y, w, h,
                    isOn, hovered, renderer, widget.getMessage());
            } else {
                RadianceTheme.drawCustomButton(ctx, x, y, w, h,
                    hovered, renderer, widget.getMessage());
            }
        }
    }

    @Override
    public List<? extends Element> children() {
        if (right != null) return List.of(left, right);
        return List.of(left);
    }

    @Override
    public List<? extends ClickableWidget> clickableWidgets() {
        List<ClickableWidget> list = new ArrayList<>();
        list.add(left);
        if (right != null) list.add(right);
        return list;
    }
}
