package com.radiance.client.gui.unified;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;

/**
 * Search overlay for the unified settings screen.
 * Allows searching settings by name across all populators.
 *
 * Minimal recovery — full implementation was lost during branch cleanup.
 * Provides the API surface needed for compilation + basic functionality.
 */
public class UnifiedSearchOverlay {

    public record SearchEntry(String label, String category, String nodeId, boolean isLeaf) {}

    private final RadianceUnifiedScreen screen;
    private boolean visible = false;
    private String query = "";

    public UnifiedSearchOverlay(RadianceUnifiedScreen screen) {
        this.screen = screen;
    }

    public boolean isVisible() {
        return visible;
    }

    public void toggle() {
        visible = !visible;
        if (!visible) {
            query = "";
        }
    }

    public void render(DrawContext context, TextRenderer textRenderer, int screenWidth, int screenHeight) {
        if (!visible) return;

        // Draw semi-transparent backdrop
        int overlayW = Math.min(400, screenWidth - 40);
        int overlayH = 30;
        int ox = (screenWidth - overlayW) / 2;
        int oy = 40;
        context.fill(ox - 4, oy - 4, ox + overlayW + 4, oy + overlayH + 4, 0xCC000000);

        // Draw search text
        String displayText = query.isEmpty() ? "Type to search settings... (Ctrl+F)" : query + "_";
        context.drawText(textRenderer, displayText, ox + 4, oy + 8, 0xFFCCCCCC, false);
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!visible) return false;
        // Close on click outside
        visible = false;
        query = "";
        return true;
    }

    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!visible) return false;
        // Escape closes
        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) {
            visible = false;
            query = "";
            return true;
        }
        // Backspace
        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_BACKSPACE && !query.isEmpty()) {
            query = query.substring(0, query.length() - 1);
            return true;
        }
        return true;
    }

    public boolean charTyped(char chr, int modifiers) {
        if (!visible) return false;
        if (Character.isISOControl(chr)) return false;
        query += chr;
        return true;
    }
}
