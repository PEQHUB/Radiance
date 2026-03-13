package com.radiance.client.gui.unified;

import com.radiance.client.gui.RadianceTheme;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;

/**
 * Left-side tree navigation panel with collapsible categories and selectable leaves.
 * Renders a hierarchical list of settings categories.
 */
public class TreeNavigationWidget extends ClickableWidget {

    private static final int NODE_HEIGHT = 18;
    private static final int INDENT = 14;
    private static final int LEFT_PAD = 6;
    private static final int SCROLL_STEP = 24;

    private final List<TreeNode> rootNodes = new ArrayList<>();
    private Consumer<TreeNode> onSelect;

    // ── Scroll state ──
    private double scrollTarget = 0;
    private double scrollDisplay = 0;
    private boolean scrollInitialized = false;
    private static final float SCROLL_LERP_SPEED = 0.35f;

    // ── Keyboard navigation ──
    private int focusIndex = -1; // flattened index of focused node

    public TreeNavigationWidget(int x, int y, int width, int height) {
        super(x, y, width, height, Text.empty());
    }

    // ── Tree building ──

    public TreeNode addRoot(TreeNode node) {
        rootNodes.add(node);
        return node;
    }

    public List<TreeNode> getRootNodes() {
        return rootNodes;
    }

    public void setOnSelect(Consumer<TreeNode> onSelect) {
        this.onSelect = onSelect;
    }

    /** Select a node by ID — deselects all others, expands parents, triggers callback. */
    public void selectById(String id) {
        for (TreeNode root : rootNodes) {
            root.deselectAll();
        }
        for (TreeNode root : rootNodes) {
            TreeNode found = root.findById(id);
            if (found != null) {
                found.selected = true;
                // Expand all ancestor categories
                expandParentsOf(id);
                if (onSelect != null) onSelect.accept(found);
                return;
            }
        }
    }

    /** Select the first root node (category or leaf). */
    public void selectFirst() {
        if (!rootNodes.isEmpty()) {
            selectById(rootNodes.get(0).id);
        }
    }

    private void expandParentsOf(String id) {
        for (TreeNode root : rootNodes) {
            if (expandParentsRecursive(root, id)) return;
        }
    }

    private boolean expandParentsRecursive(TreeNode node, String id) {
        if (node.id.equals(id)) return true;
        for (TreeNode child : node.children) {
            if (expandParentsRecursive(child, id)) {
                node.expanded = true;
                return true;
            }
        }
        return false;
    }

    // ── Flatten visible nodes ──

    private record FlatNode(TreeNode node, int depth) {}

    private List<FlatNode> flattenVisible() {
        List<FlatNode> list = new ArrayList<>();
        flattenRecursive(rootNodes, 0, list);
        return list;
    }

    private void flattenRecursive(List<TreeNode> nodes, int depth, List<FlatNode> out) {
        for (TreeNode node : nodes) {
            out.add(new FlatNode(node, depth));
            if (node.isCategory() && node.expanded) {
                flattenRecursive(node.children, depth + 1, out);
            }
        }
    }

    // ── Layout ──

    private int totalContentHeight() {
        return flattenVisible().size() * NODE_HEIGHT + 4;
    }

    private double maxScroll() {
        return Math.max(0, totalContentHeight() - getHeight());
    }

    // ── Rendering ──

    @Override
    protected void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
        if (RadianceTheme.peekActive) return;

        // Initialize scroll
        if (!scrollInitialized) {
            scrollDisplay = scrollTarget;
            scrollInitialized = true;
        }

        // Smooth scroll
        double diff = scrollTarget - scrollDisplay;
        if (Math.abs(diff) < 0.5) {
            scrollDisplay = scrollTarget;
        } else {
            scrollDisplay += diff * SCROLL_LERP_SPEED;
        }

        int panelX = getX();
        int panelY = getY();
        int panelW = getWidth();
        int panelH = getHeight();

        // Background (responds to global transparency slider)
        context.fill(panelX, panelY, panelX + panelW, panelY + panelH, RadianceTheme.unifiedTreeBg);

        // Right border
        context.fill(panelX + panelW - 1, panelY, panelX + panelW, panelY + panelH,
            RadianceTheme.borderDefault);

        // Enable scissor
        context.enableScissor(panelX, panelY, panelX + panelW, panelY + panelH);

        TextRenderer textRenderer = MinecraftClient.getInstance().textRenderer;
        List<FlatNode> visible = flattenVisible();

        int drawY = panelY + 2 - (int) scrollDisplay;
        for (int i = 0; i < visible.size(); i++) {
            FlatNode flat = visible.get(i);
            TreeNode node = flat.node;
            int depth = flat.depth;

            if (drawY + NODE_HEIGHT < panelY) {
                drawY += NODE_HEIGHT;
                continue;
            }
            if (drawY > panelY + panelH) break;

            int textX = panelX + LEFT_PAD + depth * INDENT;
            int textY = drawY + (NODE_HEIGHT - 8) / 2;

            // Selected highlight
            if (node.selected) {
                context.fill(panelX + 1, drawY, panelX + panelW - 2, drawY + NODE_HEIGHT,
                    RadianceTheme.scaleAlpha(RadianceTheme.widgetBgActive, 0.9f));
                // Left accent bar
                context.fill(panelX + 1, drawY, panelX + 3, drawY + NODE_HEIGHT,
                    RadianceTheme.SELECTED_BAR);
            }

            // Hover highlight
            boolean hovered = mouseX >= panelX && mouseX < panelX + panelW
                && mouseY >= drawY && mouseY < drawY + NODE_HEIGHT;
            if (hovered && !node.selected) {
                context.fill(panelX + 1, drawY, panelX + panelW - 2, drawY + NODE_HEIGHT,
                    RadianceTheme.scaleAlpha(RadianceTheme.widgetBgHover, 0.5f));
            }

            // Category expand/collapse indicator
            if (node.isCategory()) {
                String indicator = node.expanded ? "\u25BC" : "\u25B6"; // ▼ or ▶
                RadianceTheme.drawOutlinedText(context, textRenderer,
                    Text.literal(indicator), textX - 2, textY,
                    RadianceTheme.textSecondary);
                textX += 10;
            }

            // Label
            int labelColor = node.isCategory() ? RadianceTheme.textAccent :
                             node.selected ? RadianceTheme.textPrimary :
                             RadianceTheme.textSecondary;
            RadianceTheme.drawOutlinedText(context, textRenderer,
                Text.literal(node.label), textX, textY, labelColor);

            drawY += NODE_HEIGHT;
        }

        context.disableScissor();
    }

    // ── Input handling ──

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!isMouseOver(mouseX, mouseY)) return false;
        if (button != 0) return false;

        List<FlatNode> visible = flattenVisible();
        int drawY = getY() + 2 - (int) scrollDisplay;

        for (FlatNode flat : visible) {
            if (mouseY >= drawY && mouseY < drawY + NODE_HEIGHT) {
                TreeNode node = flat.node;
                if (node.isCategory()) {
                    node.expanded = !node.expanded;
                    // Also select category and fire callback to populate all children
                    for (TreeNode root : rootNodes) root.deselectAll();
                    node.selected = true;
                    if (onSelect != null) onSelect.accept(node);
                } else if (node.isLeaf()) {
                    // Deselect all, select this one
                    for (TreeNode root : rootNodes) root.deselectAll();
                    node.selected = true;
                    if (onSelect != null) onSelect.accept(node);
                }
                return true;
            }
            drawY += NODE_HEIGHT;
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY,
                                 double horizontalAmount, double verticalAmount) {
        if (!isMouseOver(mouseX, mouseY)) return false;
        scrollTarget = MathHelper.clamp(scrollTarget - verticalAmount * SCROLL_STEP, 0, maxScroll());
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // Arrow key navigation
        List<FlatNode> visible = flattenVisible();
        if (visible.isEmpty()) return false;

        int currentIndex = -1;
        for (int i = 0; i < visible.size(); i++) {
            if (visible.get(i).node.selected) {
                currentIndex = i;
                break;
            }
        }

        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_UP) {
            int newIndex = currentIndex <= 0 ? visible.size() - 1 : currentIndex - 1;
            selectFlatNode(visible, newIndex);
            return true;
        }
        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_DOWN) {
            int newIndex = currentIndex >= visible.size() - 1 ? 0 : currentIndex + 1;
            selectFlatNode(visible, newIndex);
            return true;
        }
        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT && currentIndex >= 0) {
            TreeNode node = visible.get(currentIndex).node;
            if (node.isCategory() && node.expanded) {
                node.expanded = false;
                return true;
            }
        }
        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT && currentIndex >= 0) {
            TreeNode node = visible.get(currentIndex).node;
            if (node.isCategory() && !node.expanded) {
                node.expanded = true;
                return true;
            }
        }
        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER && currentIndex >= 0) {
            TreeNode node = visible.get(currentIndex).node;
            if (node.isLeaf() && onSelect != null) {
                onSelect.accept(node);
                return true;
            }
            if (node.isCategory()) {
                node.expanded = !node.expanded;
                return true;
            }
        }

        return false;
    }

    private void selectFlatNode(List<FlatNode> visible, int index) {
        if (index < 0 || index >= visible.size()) return;
        TreeNode node = visible.get(index).node;
        for (TreeNode root : rootNodes) root.deselectAll();
        node.selected = true;
        // Fire callback for any node that has a populator (categories + leaves)
        if (node.populator != null && onSelect != null) {
            onSelect.accept(node);
        }
    }

    @Override
    public boolean isMouseOver(double mouseX, double mouseY) {
        return mouseX >= getX() && mouseX < getX() + getWidth()
            && mouseY >= getY() && mouseY < getY() + getHeight();
    }

    @Override
    protected void appendClickableNarrations(NarrationMessageBuilder builder) {
        // Minimal narration
    }
}
