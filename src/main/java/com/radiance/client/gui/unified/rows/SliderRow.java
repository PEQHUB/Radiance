package com.radiance.client.gui.unified.rows;

import com.radiance.client.gui.RadianceTheme;
import com.radiance.client.gui.ResettableSliderWidget;
import com.radiance.client.gui.unified.SettingsRow;
import java.util.List;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.widget.ClickableWidget;

/**
 * A full-width slider row wrapping a ResettableSliderWidget.
 * Supports all existing slider features: Ctrl+Click numeric, Shift+Click reset, precision drag.
 */
public class SliderRow extends SettingsRow {

    private final ResettableSliderWidget slider;

    public SliderRow(ResettableSliderWidget slider) {
        this.slider = slider;
    }

    @Override
    public void render(DrawContext context, int x, int y, int width,
                       int mouseX, int mouseY, float delta, float alphaMult) {
        if (alphaMult <= 0f) return;
        slider.setX(x);
        slider.setY(y);
        slider.setWidth(width);
        slider.render(context, mouseX, mouseY, delta);
        RadianceTheme.drawModifiedDot(context, x, y, getHeight(), !slider.isDefault());
    }

    @Override
    public List<? extends Element> children() {
        return List.of(slider);
    }

    @Override
    public List<? extends ClickableWidget> clickableWidgets() {
        return List.of(slider);
    }

    public ResettableSliderWidget getSlider() {
        return slider;
    }
}
