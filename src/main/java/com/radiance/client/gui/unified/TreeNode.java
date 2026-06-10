package com.radiance.client.gui.unified;

import java.util.ArrayList;
import java.util.List;

/**
 * A node in the settings tree navigation.
 * Categories have children and can be expanded/collapsed.
 * Leaf nodes have a content populator that fills the right panel.
 */
public class TreeNode {

    public final String id;
    public final String label;
    public final List<TreeNode> children = new ArrayList<>();
    public ContentPopulator populator; // null initially for category nodes, set via composite

    public boolean expanded = true;
    public boolean selected = false;

    /** Create a leaf node (selectable, has content populator). */
    public TreeNode(String id, String label, ContentPopulator populator) {
        this.id = id;
        this.label = label;
        this.populator = populator;
    }

    /** Create a category node (expandable parent, no content). */
    public TreeNode(String id, String label) {
        this.id = id;
        this.label = label;
        this.populator = null;
    }

    public boolean isCategory() {
        return !children.isEmpty();
    }

    public boolean isLeaf() {
        return children.isEmpty() && populator != null;
    }

    public TreeNode addChild(TreeNode child) {
        children.add(child);
        return this;
    }

    /** Recursively deselect this node and all children. */
    public void deselectAll() {
        selected = false;
        for (TreeNode child : children) {
            child.deselectAll();
        }
    }

    /** Find a node by ID recursively. */
    public TreeNode findById(String searchId) {
        if (this.id.equals(searchId)) return this;
        for (TreeNode child : children) {
            TreeNode found = child.findById(searchId);
            if (found != null) return found;
        }
        return null;
    }
}
