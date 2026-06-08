package com.radiance.client.gui;

import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.Selectable;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.OptionListWidget;

public class LegacyFourColumnButtonEntry extends OptionListWidget.WidgetEntry {
    private final List<ClickableWidget> widgets;

    public LegacyFourColumnButtonEntry(ClickableWidget w1, ClickableWidget w2,
                                        ClickableWidget w3, ClickableWidget w4,
                                        OptionListWidget parent) {
        super(buildList(w1, w2, w3, w4), null);
        this.widgets = buildList(w1, w2, w3, w4);
    }

    private static List<ClickableWidget> buildList(ClickableWidget... ws) {
        List<ClickableWidget> list = new ArrayList<>();
        for (ClickableWidget w : ws) { if (w != null) list.add(w); }
        return list;
    }

    @Override
    public void render(DrawContext context, int index, int y, int x, int entryWidth,
                       int entryHeight, int mouseX, int mouseY, boolean hovered, float tickDelta) {
        int count = widgets.size();
        if (count == 0) return;
        int gap = 4;
        int colWidth = (entryWidth - gap * (count - 1)) / count;
        for (int i = 0; i < count; i++) {
            ClickableWidget w = widgets.get(i);
            w.setX(x + i * (colWidth + gap));
            w.setY(y);
            w.setWidth(colWidth);
            w.render(context, mouseX, mouseY, tickDelta);
        }
    }

    @Override
    public List<? extends Element> children() { return widgets; }

    @Override
    public List<? extends Selectable> selectableChildren() { return widgets; }
}
