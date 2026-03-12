package com.radiance.client.gui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * Fullscreen search overlay toggled by spacebar in RadianceSettingsScreen.
 * Displays results in file-tree style grouped by Category > Sub-Screen > Setting.
 * Clicking a result navigates to the appropriate sub-screen.
 */
public class RadianceSearchOverlay {

    private final Screen parent;
    private boolean visible = false;
    private String query = "";
    private List<SettingsRegistry.SettingEntry> results = List.of();
    private int selectedIndex = -1;

    /** Y positions of rendered result items (parallel to filteredResultIndices). */
    private final List<int[]> resultHitBoxes = new ArrayList<>(); // {y, height, resultListIndex}

    private static final int MAX_VISIBLE = 15;
    private static final int BOX_WIDTH = 380;
    private static final int BOX_Y = 50;
    private static final int RESULT_LINE_H = 14;
    private static final int CATEGORY_LINE_H = 16;

    public RadianceSearchOverlay(Screen parent) {
        this.parent = parent;
    }

    public void toggle() {
        visible = !visible;
        if (!visible) {
            query = "";
            results = List.of();
            selectedIndex = -1;
            resultHitBoxes.clear();
        } else {
            SettingsRegistry.initialize();
        }
    }

    public boolean isVisible() {
        return visible;
    }

    // ── Input handling ──

    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!visible) return false;

        if (keyCode == GLFW.GLFW_KEY_ESCAPE || keyCode == GLFW.GLFW_KEY_SPACE) {
            toggle();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
            if (!query.isEmpty()) {
                query = query.substring(0, query.length() - 1);
                updateResults();
            }
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_UP) {
            if (selectedIndex > 0) selectedIndex--;
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_DOWN) {
            if (selectedIndex < results.size() - 1) selectedIndex++;
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ENTER && selectedIndex >= 0 && selectedIndex < results.size()) {
            navigateToResult(results.get(selectedIndex));
            return true;
        }
        return true; // consume all keys while overlay is open
    }

    public boolean charTyped(char chr, int modifiers) {
        if (!visible) return false;
        if (chr >= 32 && chr < 127) {
            query += chr;
            updateResults();
            return true;
        }
        return false;
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!visible) return false;

        if (button == 0) {
            for (int[] box : resultHitBoxes) {
                int y = box[0];
                int h = box[1];
                int idx = box[2];
                if (mouseY >= y && mouseY < y + h && idx >= 0 && idx < results.size()) {
                    navigateToResult(results.get(idx));
                    return true;
                }
            }
        }
        // Click anywhere else closes overlay
        toggle();
        return true;
    }

    // ── Render ──

    public void render(DrawContext ctx, TextRenderer renderer, int screenWidth, int screenHeight) {
        if (!visible) return;

        resultHitBoxes.clear();

        // Dim overlay
        ctx.fill(0, 0, screenWidth, screenHeight, RadianceTheme.withAlpha(0x000000, 0.75f));

        int boxW = Math.min(BOX_WIDTH, screenWidth - 40);
        int boxX = (screenWidth - boxW) / 2;

        // ── Search box ──
        ctx.fill(boxX - 1, BOX_Y - 1, boxX + boxW + 1, BOX_Y + 21, RadianceTheme.borderFocused);
        ctx.fill(boxX, BOX_Y, boxX + boxW, BOX_Y + 20, RadianceTheme.widgetBg);

        String cursor = (System.currentTimeMillis() / 500 % 2 == 0) ? "|" : "";
        String displayText = query.isEmpty() ? "Search settings..." : query + cursor;
        int textColor = query.isEmpty() ? RadianceTheme.textSecondary : RadianceTheme.textPrimary;
        ctx.drawText(renderer, Text.literal(displayText), boxX + 6, BOX_Y + 6, textColor, false);

        // ── Hint ──
        RadianceTheme.drawOutlinedText(ctx, renderer,
                Text.literal("Space to close  |  Arrow keys + Enter to navigate"),
                boxX + 6, BOX_Y + 24, RadianceTheme.textSecondary, 0.6f);

        // ── Results ──
        if (query.isEmpty()) {
            // Show all categories as a directory tree
            renderCategoryTree(ctx, renderer, boxX, BOX_Y + 40, boxW);
            return;
        }

        int y = BOX_Y + 42;
        if (results.isEmpty()) {
            RadianceTheme.drawOutlinedText(ctx, renderer,
                    Text.literal("No matching settings"), boxX + 6, y, RadianceTheme.textSecondary);
            return;
        }

        String lastCategoryPath = null;
        int rendered = 0;

        for (int i = 0; i < results.size() && rendered < MAX_VISIBLE; i++) {
            SettingsRegistry.SettingEntry entry = results.get(i);

            // Build category path
            String path = entry.category();
            if (entry.subScreen() != null) path += " > " + entry.subScreen();

            // Category header
            if (!path.equals(lastCategoryPath)) {
                RadianceTheme.drawOutlinedText(ctx, renderer,
                        Text.literal(path), boxX + 4, y, RadianceTheme.textAccent);
                y += CATEGORY_LINE_H;
                lastCategoryPath = path;
            }

            // Result item
            boolean selected = (i == selectedIndex);
            if (selected) {
                ctx.fill(boxX + 2, y - 1, boxX + boxW - 2, y + RESULT_LINE_H - 1,
                        RadianceTheme.widgetBgHover);
            }

            String prefix = selected ? "> " : "  ";
            int itemColor = selected ? 0xFFFFFFFF : RadianceTheme.textPrimary;
            RadianceTheme.drawOutlinedText(ctx, renderer,
                    Text.literal(prefix + entry.displayName()), boxX + 14, y + 1, itemColor);

            // GPU tag
            if (entry.gpuIntensive()) {
                int nameW = renderer.getWidth(prefix + entry.displayName());
                ctx.drawText(renderer, Text.literal(" [GPU]"),
                        boxX + 14 + nameW, y + 1, RadianceTheme.GPU_TAG, false);
            }

            resultHitBoxes.add(new int[]{y - 1, RESULT_LINE_H, i});
            y += RESULT_LINE_H;
            rendered++;
        }

        if (results.size() > MAX_VISIBLE) {
            RadianceTheme.drawOutlinedText(ctx, renderer,
                    Text.literal("... and " + (results.size() - MAX_VISIBLE) + " more"),
                    boxX + 14, y + 2, RadianceTheme.textSecondary);
        }
    }

    /**
     * Render a directory tree of all setting categories when no query is typed.
     */
    private void renderCategoryTree(DrawContext ctx, TextRenderer renderer, int x, int y, int width) {
        String lastCategory = null;
        String lastSubScreen = null;
        int count = 0;

        for (SettingsRegistry.SettingEntry entry : SettingsRegistry.ALL) {
            if (count >= 25) break; // limit visible

            if (!entry.category().equals(lastCategory)) {
                lastCategory = entry.category();
                lastSubScreen = null;
                RadianceTheme.drawOutlinedText(ctx, renderer,
                        Text.literal(lastCategory), x + 4, y, RadianceTheme.textAccent);
                y += CATEGORY_LINE_H;
                count++;
            }

            String sub = entry.subScreen();
            if (sub != null && !sub.equals(lastSubScreen)) {
                lastSubScreen = sub;
                RadianceTheme.drawOutlinedText(ctx, renderer,
                        Text.literal("  " + sub), x + 14, y, RadianceTheme.textSecondary);
                y += RESULT_LINE_H;
                count++;
            }
        }
    }

    // ── Internal ──

    private void updateResults() {
        results = SettingsRegistry.search(query);
        if (results.isEmpty()) {
            selectedIndex = -1;
        } else {
            selectedIndex = Math.min(selectedIndex, results.size() - 1);
            if (selectedIndex < 0) selectedIndex = 0;
        }
    }

    private void navigateToResult(SettingsRegistry.SettingEntry entry) {
        toggle();

        if (entry.subScreen() == null) return; // main screen setting — just close

        MinecraftClient mc = MinecraftClient.getInstance();
        Screen target = resolveSubScreen(entry.subScreen());
        if (target != null) {
            mc.setScreen(target);
        }
    }

    private Screen resolveSubScreen(String subScreen) {
        return switch (subScreen) {
            case "Exposure" -> new ExposureSettingsScreen(parent);
            case "Area Lights" -> new AreaLightSettingsScreen(parent);
            case "PsychoV" -> new PsychoVSettingsScreen(parent);
            case "Post Processing" -> new PostProcessingSettingsScreen(parent);
            default -> null;
        };
    }
}
