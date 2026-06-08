package com.radiance.client.gui.unified.rows;

import com.radiance.client.gui.ResettableSliderWidget;
import com.radiance.client.gui.unified.SettingsRow;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.widget.ClickableWidget;

/**
 * A row with two side-by-side sliders. If the right slider is null,
 * the left slider keeps a compact preferred width.
 */
public class TwoSliderRow extends SettingsRow {

    private static final int GAP = 8;

    private final ResettableSliderWidget left;
    private final ResettableSliderWidget right; // nullable

    public TwoSliderRow(ResettableSliderWidget left, ResettableSliderWidget right) {
        this.left = left;
        this.right = right;
    }

    @Override
    public void render(DrawContext context, int x, int y, int width,
                       int mouseX, int mouseY, float delta, float alphaMult) {
        if (alphaMult <= 0f) return;
        int colW = (width - GAP) / 2;

        left.setX(x);
        left.setY(y);
        left.setWidth(right != null ? colW : width);
        left.render(context, mouseX, mouseY, delta);

        if (right != null) {
            right.setX(x + colW + GAP);
            right.setY(y);
            right.setWidth(colW);
            right.render(context, mouseX, mouseY, delta);
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

    public ResettableSliderWidget getLeft() { return left; }
    public ResettableSliderWidget getRight() { return right; }
}
