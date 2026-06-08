package com.radiance.client.util;

import com.google.common.collect.ImmutableList;
import com.radiance.client.gui.RadianceTheme;
import java.util.List;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.Selectable;
import net.minecraft.client.gui.widget.OptionListWidget;
import net.minecraft.text.Text;

public class CategoryVideoOptionEntry extends OptionListWidget.WidgetEntry {

    private final Text text;
    private final MinecraftClient client;
    private final OptionListWidget parent;

    public CategoryVideoOptionEntry(Text text, OptionListWidget parent) {
        super(ImmutableList.of(), null);
        this.client = MinecraftClient.getInstance();
        this.parent = parent;
        this.text = text;
    }

    @Override
    public void render(DrawContext context, int index, int y, int x, int entryWidth,
        int entryHeight, int mouseX, int mouseY, boolean hovered, float tickDelta) {
        // Category headers never contain the active slider, so they always fade during drag
        float fadeFactor = RadianceTheme.inactiveFadeFactor();
        if (fadeFactor <= 0f) return;

        RadianceTheme.drawCategoryHeader(
            context, this.client.textRenderer, this.text,
            x, y, entryWidth, entryHeight, fadeFactor
        );
    }

    @Override
    public List<? extends Element> children() {
        return ImmutableList.of();
    }

    @Override
    public List<? extends Selectable> selectableChildren() {
        return ImmutableList.of();
    }
}
