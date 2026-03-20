package com.radiance.client.gui;

import com.google.common.collect.ImmutableList;
import com.radiance.client.gui.RadianceTheme;
import java.util.List;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.Selectable;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.OptionListWidget;

public class LegacySliderEntry extends OptionListWidget.WidgetEntry {
    private final ClickableWidget widget;

    public LegacySliderEntry(ClickableWidget widget, OptionListWidget parent) {
        super(ImmutableList.of(widget), null);
        this.widget = widget;
    }

    @Override
    public void render(DrawContext context, int index, int y, int x, int entryWidth,
                       int entryHeight, int mouseX, int mouseY, boolean hovered, float tickDelta) {
        widget.setX(x + (entryWidth - widget.getWidth()) / 2);
        widget.setY(y);
        widget.setWidth(Math.min(310, entryWidth));
        widget.render(context, mouseX, mouseY, tickDelta);
    }

    @Override
    public List<? extends Element> children() {
        return ImmutableList.of(widget);
    }

    @Override
    public List<? extends Selectable> selectableChildren() {
        return ImmutableList.of(widget);
    }
}
