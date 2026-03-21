package com.radiance.client.ui;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;

/**
 * Minimal in-game HUD renderer that reads from UIStateSnapshot.
 * Avoids touching vanilla InGameHud (thread-safety concerns).
 */
public class DecoupledHudRenderer {

    public void render(DrawContext context, UIStateSnapshot snap, TextRenderer textRenderer) {
        if (textRenderer == null) return;

        int w = snap.scaledWidth();
        int h = snap.scaledHeight();

        // Health
        String healthText = String.format("%.0f/%.0f", snap.health(), snap.maxHealth());
        context.drawTextWithShadow(textRenderer, healthText, w / 2 - 60, h - 40, 0xFF5555);

        // Food
        String foodText = snap.food() + " Food";
        context.drawTextWithShadow(textRenderer, foodText, w / 2 + 20, h - 40, 0xAA8844);

        // XP
        if (snap.experienceLevel() > 0) {
            String xpText = "Lv " + snap.experienceLevel();
            context.drawTextWithShadow(textRenderer, xpText, w / 2 - 10, h - 52, 0x55FF55);
        }
    }
}
