package com.radiance.client.gui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.option.GameOptionsScreen;
import net.minecraft.client.gui.widget.OptionListWidget;

/**
 * OptionListWidget subclass that uses the full screen width for entries.
 * Vanilla OptionListWidget.getRowWidth() returns hardcoded 310px.
 */
public class WideOptionListWidget extends OptionListWidget {

    public WideOptionListWidget(MinecraftClient client, int width, GameOptionsScreen screen) {
        super(client, width, screen);
    }

    @Override
    public int getRowWidth() {
        return getWidth() - 40;
    }

    @Override
    protected void drawMenuListBackground(DrawContext context) {
        // Skip the tinted in-world list background
    }
}
