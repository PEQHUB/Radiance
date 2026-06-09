package com.radiance.client.gui.unified;

import com.radiance.client.gui.RadianceTheme;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import org.lwjgl.glfw.GLFW;

/**
 * Search overlay for the unified settings screen.
 * Indexes tree nodes and each populator's explicit search entries.
 */
public class UnifiedSearchOverlay {

    public record SearchEntry(String label, String category, String nodeId, boolean isLeaf) {}

    private static final int MAX_RESULTS = 12;
    private static final int ROW_HEIGHT = 18;

    private final RadianceUnifiedScreen screen;
    private final List<SearchEntry> allEntries = new ArrayList<>();
    private final List<SearchEntry> filtered = new ArrayList<>();

    private boolean visible = false;
    private String query = "";
    private int selectedIndex = 0;
    private int lastX;
    private int lastY;
    private int lastWidth;
    private int lastHeight;
    private int lastResultTop;

    public UnifiedSearchOverlay(RadianceUnifiedScreen screen) {
        this.screen = screen;
    }

    public boolean isVisible() {
        return visible;
    }

    public void toggle() {
        visible = !visible;
        if (visible) {
            rebuildIndex();
            updateFiltered();
        } else {
            query = "";
            filtered.clear();
            selectedIndex = 0;
        }
    }

    public void render(DrawContext context, TextRenderer textRenderer, int screenWidth, int screenHeight) {
        if (!visible) return;

        rebuildIndex();
        updateFiltered();

        int overlayW = Math.min(460, Math.max(240, screenWidth - 48));
        int resultCount = filtered.size();
        int overlayH = 34 + resultCount * ROW_HEIGHT + 8;
        int ox = (screenWidth - overlayW) / 2;
        int oy = 40;
        lastX = ox;
        lastY = oy;
        lastWidth = overlayW;
        lastHeight = overlayH;
        lastResultTop = oy + 34;

        context.fill(ox - 4, oy - 4, ox + overlayW + 4, oy + overlayH + 4, 0xD9000000);
        context.fill(ox, oy, ox + overlayW, oy + 26, RadianceTheme.unifiedContentBg);
        context.drawBorder(ox, oy, overlayW, 26, RadianceTheme.borderDefault);

        String displayText = query.isEmpty() ? "Search settings..." : query + "_";
        context.drawText(textRenderer, displayText, ox + 8, oy + 9, 0xFFE8E8E8, false);

        if (resultCount == 0) {
            String empty = query.isEmpty() ? "Type to search. Enter opens the selected result." : "No matching settings";
            context.drawText(textRenderer, empty, ox + 8, lastResultTop + 4, 0xFF888888, false);
            return;
        }

        for (int i = 0; i < resultCount; i++) {
            SearchEntry entry = filtered.get(i);
            int rowY = lastResultTop + i * ROW_HEIGHT;
            boolean selected = i == selectedIndex;
            if (selected) {
                context.fill(ox, rowY, ox + overlayW, rowY + ROW_HEIGHT, RadianceTheme.widgetBgActive);
                context.fill(ox, rowY, ox + 2, rowY + ROW_HEIGHT, RadianceTheme.SELECTED_BAR);
            }
            String label = trimToWidth(textRenderer, entry.label(), overlayW - 24);
            String category = trimToWidth(textRenderer, entry.category(), overlayW - 24);
            int labelColor = selected ? 0xFFFFFFFF : 0xFFE0E0E0;
            int categoryColor = selected ? 0xFFBFDAD7 : 0xFF8FA8A5;
            context.drawText(textRenderer, label, ox + 8, rowY + 2, labelColor, false);
            context.drawText(textRenderer, category, ox + 8, rowY + 11, categoryColor, false);
        }
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!visible) return false;
        if (button != 0) return true;

        boolean inside = mouseX >= lastX - 4 && mouseX < lastX + lastWidth + 4
            && mouseY >= lastY - 4 && mouseY < lastY + lastHeight + 4;
        if (!inside) {
            close();
            return true;
        }

        if (mouseY >= lastResultTop && mouseY < lastResultTop + filtered.size() * ROW_HEIGHT) {
            int index = (int) ((mouseY - lastResultTop) / ROW_HEIGHT);
            if (index >= 0 && index < filtered.size()) {
                selectedIndex = index;
                navigateSelected();
            }
        }
        return true;
    }

    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!visible) return false;

        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            close();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
            if (!query.isEmpty()) {
                query = query.substring(0, query.length() - 1);
                selectedIndex = 0;
            }
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_DELETE) {
            query = "";
            selectedIndex = 0;
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_DOWN) {
            if (!filtered.isEmpty()) selectedIndex = (selectedIndex + 1) % filtered.size();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_UP) {
            if (!filtered.isEmpty()) selectedIndex = (selectedIndex + filtered.size() - 1) % filtered.size();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            navigateSelected();
            return true;
        }
        return true;
    }

    public boolean charTyped(char chr, int modifiers) {
        if (!visible) return false;
        if (Character.isISOControl(chr)) return true;
        query += chr;
        selectedIndex = 0;
        return true;
    }

    private void close() {
        visible = false;
        query = "";
        filtered.clear();
        selectedIndex = 0;
    }

    private void navigateSelected() {
        if (filtered.isEmpty()) return;
        SearchEntry entry = filtered.get(Math.max(0, Math.min(selectedIndex, filtered.size() - 1)));
        close();
        screen.navigateTo(entry.nodeId());
    }

    private void rebuildIndex() {
        allEntries.clear();
        Map<String, SearchEntry> unique = new LinkedHashMap<>();
        if (screen.getTree() == null) return;
        for (TreeNode root : screen.getTree().getRootNodes()) {
            collectNode(root, "", unique);
        }
        allEntries.addAll(unique.values());
    }

    private void collectNode(TreeNode node, String parentPath, Map<String, SearchEntry> unique) {
        String path = parentPath.isEmpty() ? node.label : parentPath + " > " + node.label;
        String category = parentPath.isEmpty() ? "Root" : parentPath;
        put(unique, new SearchEntry(node.label, category, node.id, node.isLeaf()));

        if (node.populator != null) {
            for (SearchEntry entry : node.populator.getSearchEntries(node.id, path)) {
                put(unique, entry);
            }
        }
        for (TreeNode child : node.children) {
            collectNode(child, path, unique);
        }
    }

    private void put(Map<String, SearchEntry> unique, SearchEntry entry) {
        String key = entry.label() + "\n" + entry.category() + "\n" + entry.nodeId();
        unique.putIfAbsent(key, entry);
    }

    private void updateFiltered() {
        filtered.clear();
        String q = query.trim().toLowerCase(Locale.ROOT);
        for (SearchEntry entry : allEntries) {
            if (q.isEmpty() || matches(entry, q)) {
                filtered.add(entry);
                if (filtered.size() >= MAX_RESULTS) break;
            }
        }
        if (selectedIndex >= filtered.size()) selectedIndex = Math.max(0, filtered.size() - 1);
    }

    private boolean matches(SearchEntry entry, String q) {
        return entry.label().toLowerCase(Locale.ROOT).contains(q)
            || entry.category().toLowerCase(Locale.ROOT).contains(q)
            || entry.nodeId().toLowerCase(Locale.ROOT).contains(q);
    }

    private String trimToWidth(TextRenderer textRenderer, String text, int width) {
        if (textRenderer.getWidth(text) <= width) return text;
        String ellipsis = "...";
        int max = Math.max(0, width - textRenderer.getWidth(ellipsis));
        return textRenderer.trimToWidth(text, max) + ellipsis;
    }
}
