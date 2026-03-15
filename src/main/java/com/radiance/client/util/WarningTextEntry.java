package com.radiance.client.util;

import com.google.common.collect.ImmutableList;
import java.util.List;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.Selectable;
import net.minecraft.client.gui.widget.OptionListWidget;
import net.minecraft.text.Text;

public class WarningTextEntry extends OptionListWidget.WidgetEntry {

    private final String warningText;
    private final MinecraftClient client;

    public WarningTextEntry(String warningText, OptionListWidget parent) {
        super(ImmutableList.of(), null);
        this.client = MinecraftClient.getInstance();
        this.warningText = warningText;
    }

    @Override
    public void render(DrawContext context, int index, int y, int x, int entryWidth,
        int entryHeight, int mouseX, int mouseY, boolean hovered, float tickDelta) {
        // Amber/gold warning text, centered
        String display = "\u26A0 " + warningText;
        int textWidth = this.client.textRenderer.getWidth(display);
        int textX = x + (entryWidth - textWidth) / 2;
        context.drawText(this.client.textRenderer, Text.literal(display), textX, y + 5, 0xFFFFAA00, false);
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
