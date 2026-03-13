package com.radiance.client.util;

import java.awt.Toolkit;
import java.awt.datatransfer.*;

/**
 * Copy/paste system for materials. Uses both an internal buffer (always works)
 * and the system clipboard (JSON text, best-effort).
 */
public final class MaterialClipboard {

    private MaterialClipboard() {}

    private static MaterialData internalBuffer = null;

    /** Copy the material at blockIndex to both internal buffer and system clipboard. */
    public static void copy(int blockIndex) {
        internalBuffer = MaterialData.fromOptions(blockIndex);
        if (internalBuffer != null) {
            copyToSystemClipboard(internalBuffer.toCompactJson());
        }
    }

    /** Copy a MaterialData directly (e.g. from browser). */
    public static void copy(MaterialData data) {
        internalBuffer = data;
        if (data != null) {
            copyToSystemClipboard(data.toCompactJson());
        }
    }

    /**
     * Paste onto the block at blockIndex.
     * Tries internal buffer first, then system clipboard JSON.
     * Returns true if paste succeeded.
     */
    public static boolean paste(int blockIndex) {
        MaterialData data = internalBuffer;
        if (data == null) {
            String clip = readFromSystemClipboard();
            if (clip != null) data = MaterialData.fromJson(clip);
        }
        if (data == null) return false;
        data.applyToOptions(blockIndex);
        return true;
    }

    /** Whether there is material data available to paste. */
    public static boolean hasData() {
        if (internalBuffer != null) return true;
        String clip = readFromSystemClipboard();
        return clip != null && MaterialData.fromJson(clip) != null;
    }

    /** Short preview of the buffered material for tooltips. */
    public static String getPreviewText() {
        if (internalBuffer != null) return internalBuffer.displayName;
        String clip = readFromSystemClipboard();
        if (clip != null) {
            MaterialData d = MaterialData.fromJson(clip);
            if (d != null) return d.displayName;
        }
        return "";
    }

    // ── System clipboard helpers (best-effort, silent fail) ──

    private static void copyToSystemClipboard(String text) {
        try {
            Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
            clipboard.setContents(new StringSelection(text), null);
        } catch (Exception ignored) {}
    }

    private static String readFromSystemClipboard() {
        try {
            Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
            Transferable content = clipboard.getContents(null);
            if (content != null && content.isDataFlavorSupported(DataFlavor.stringFlavor)) {
                String text = (String) content.getTransferData(DataFlavor.stringFlavor);
                // Quick sanity check: must look like JSON with blockId
                if (text != null && text.contains("\"blockId\"")) return text;
            }
        } catch (Exception ignored) {}
        return null;
    }
}
