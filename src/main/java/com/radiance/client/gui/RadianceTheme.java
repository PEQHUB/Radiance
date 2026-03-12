package com.radiance.client.gui;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.text.Text;

/**
 * Central theme authority for all Radiance GUI screens.
 * All colors are alpha-aware and recomputed when globalAlpha changes.
 * No GUI file should use hardcoded color literals — draw through this class.
 */
public final class RadianceTheme {

    private RadianceTheme() {}

    // ── Base palette (RGB only, alpha applied dynamically) ──
    private static final int BASE_PANEL       = 0x0A0A0A;
    private static final int BASE_WIDGET      = 0x1A1A2A;
    private static final int BASE_HOVER       = 0x2A2A4A;
    private static final int BASE_ACTIVE      = 0x3A3A5A;
    private static final int BASE_DROPDOWN    = 0x101018;
    private static final int BASE_HEADER      = 0x0A0A0A;
    private static final int BASE_BORDER      = 0x808080;
    private static final int BASE_BORDER_FOCUS = 0xC0C0E0;
    private static final int BASE_TEXT_PRIMARY = 0xE0E0E0;
    private static final int BASE_TEXT_SECONDARY = 0x909090;
    private static final int BASE_TEXT_ACCENT  = 0x8888CC;

    // ── Semantic colors (special purpose, fixed alpha) ──
    public static final int TEXT_ERROR    = 0xFFFF5555;
    public static final int TEXT_SUCCESS  = 0xFF55FF55;
    public static final int TEXT_LINK     = 0xFF55FFFF;
    public static final int TEXT_PATH     = 0xFFFFAA00;
    public static final int SELECTED_BAR  = 0xFF6060FF;
    public static final int GPU_TAG       = 0xFF707090;

    // ── Derived colors (recomputed by recompute()) ──
    public static int panelBg;
    public static int widgetBg;
    public static int widgetBgHover;
    public static int widgetBgActive;
    public static int dropdownBg;
    public static int headerBg;
    public static int borderDefault;
    public static int borderFocused;
    public static int textPrimary;
    public static int textSecondary;
    public static int textAccent;

    // ── Alpha state ──
    private static float globalAlpha = 0.55f;
    private static float effectiveAlpha = 0.55f; // after adaptive dimming

    // ── Per-screen alpha overrides (screen class simple name → alpha, -1 = use global) ──
    private static final java.util.Map<String, Float> screenAlphaOverrides = new java.util.HashMap<>();

    // ── Slider focus mode (vanish-on-drag) ──
    public static ClickableWidget activeSlider = null;
    private static long fadeStartMs = 0;
    private static boolean fadingOut = false;
    public static final long FADE_OUT_MS = 100;
    public static final long FADE_IN_MS = 150;

    // ── Peek mode (Tab key full hide) ──
    public static boolean peekActive = false;

    // ── Adaptive dimming ──
    private static boolean adaptiveDimmingEnabled = false;
    private static float sceneBrightness = 0.5f; // 0=dark, 1=bright, from preExposure

    // ── Hover description state ──
    public static ClickableWidget hoveredWidget = null;
    public static long hoverStartMs = 0;
    public static final long HOVER_DESCRIPTION_DELAY_MS = 800;

    // ── Initialize on first use ──
    static {
        recompute();
    }

    // ── Public API ──

    public static float getGlobalAlpha() {
        return globalAlpha;
    }

    public static void setGlobalAlpha(float alpha) {
        globalAlpha = Math.max(0f, Math.min(1f, alpha));
        recompute();
    }

    public static void setScreenAlpha(String screenName, float alpha) {
        if (alpha < 0) {
            screenAlphaOverrides.remove(screenName);
        } else {
            screenAlphaOverrides.put(screenName, Math.max(0f, Math.min(1f, alpha)));
        }
    }

    public static float getScreenAlpha(String screenName) {
        Float override = screenAlphaOverrides.get(screenName);
        return override != null ? override : -1f;
    }

    public static void setAdaptiveDimmingEnabled(boolean enabled) {
        adaptiveDimmingEnabled = enabled;
        recompute();
    }

    public static boolean isAdaptiveDimmingEnabled() {
        return adaptiveDimmingEnabled;
    }

    public static void setSceneBrightness(float brightness) {
        sceneBrightness = Math.max(0f, Math.min(1f, brightness));
        if (adaptiveDimmingEnabled) {
            recompute();
        }
    }

    /**
     * Recompute all derived colors from the current global alpha.
     * Call after globalAlpha, adaptive dimming, or screen alpha changes.
     */
    public static void recompute() {
        effectiveAlpha = globalAlpha;
        if (adaptiveDimmingEnabled) {
            // Bright scenes: bump alpha up slightly, dark scenes: reduce
            float adjustment = (sceneBrightness - 0.5f) * 0.2f;
            effectiveAlpha = Math.max(0f, Math.min(1f, globalAlpha + adjustment));
        }

        panelBg       = withAlpha(BASE_PANEL, effectiveAlpha * 0.7f);
        widgetBg      = withAlpha(BASE_WIDGET, effectiveAlpha);
        widgetBgHover = withAlpha(BASE_HOVER, effectiveAlpha);
        widgetBgActive= withAlpha(BASE_ACTIVE, effectiveAlpha);
        dropdownBg    = withAlpha(BASE_DROPDOWN, Math.min(1f, effectiveAlpha + 0.15f));
        headerBg      = withAlpha(BASE_HEADER, effectiveAlpha * 0.5f);
        borderDefault = withAlpha(BASE_BORDER, effectiveAlpha * 0.6f);
        borderFocused = withAlpha(BASE_BORDER_FOCUS, effectiveAlpha);
        textPrimary   = withAlpha(BASE_TEXT_PRIMARY, 1.0f);
        textSecondary = withAlpha(BASE_TEXT_SECONDARY, 0.8f);
        textAccent    = withAlpha(BASE_TEXT_ACCENT, 1.0f);
    }

    /**
     * Resolve the effective panel background for a given screen,
     * respecting per-screen alpha overrides.
     */
    public static int currentPanelBg(Screen screen) {
        if (screen == null) return panelBg;
        Float override = screenAlphaOverrides.get(screen.getClass().getSimpleName());
        if (override == null) return panelBg;
        return withAlpha(BASE_PANEL, override * 0.7f);
    }

    // ── Slider focus mode ──

    /**
     * Called when a slider drag begins.
     */
    public static void beginSliderFocus(ClickableWidget slider) {
        activeSlider = slider;
        fadeStartMs = System.currentTimeMillis();
        fadingOut = true;
    }

    /**
     * Called when a slider drag ends.
     */
    public static void endSliderFocus() {
        activeSlider = null;
        fadeStartMs = System.currentTimeMillis();
        fadingOut = false;
    }

    /**
     * Returns the alpha multiplier for inactive entries during slider focus.
     * 1.0 = fully visible, 0.0 = fully hidden.
     */
    public static float inactiveFadeFactor() {
        if (peekActive) return 0f;
        if (activeSlider == null && fadeStartMs == 0) return 1f;

        long elapsed = System.currentTimeMillis() - fadeStartMs;
        if (fadingOut || activeSlider != null) {
            // Fading out to 0
            return Math.max(0f, 1f - (elapsed / (float) FADE_OUT_MS));
        } else {
            // Fading back in from 0
            float factor = Math.min(1f, elapsed / (float) FADE_IN_MS);
            if (factor >= 1f) fadeStartMs = 0; // Animation complete
            return factor;
        }
    }

    /**
     * Whether a widget entry should render at full opacity (it contains the active slider).
     */
    public static boolean isActiveEntry(java.util.List<? extends net.minecraft.client.gui.Element> children) {
        if (activeSlider == null) return false;
        for (var child : children) {
            if (child == activeSlider) return true;
        }
        return false;
    }

    // ── Color utilities ──

    /**
     * Combine an RGB color (0xRRGGBB) with an alpha value (0.0-1.0) into ARGB.
     */
    public static int withAlpha(int rgb, float alpha) {
        int a = Math.max(0, Math.min(255, (int) (alpha * 255)));
        return (a << 24) | (rgb & 0x00FFFFFF);
    }

    /**
     * Scale the alpha of an existing ARGB color by a multiplier.
     */
    public static int scaleAlpha(int argb, float multiplier) {
        int a = (argb >>> 24) & 0xFF;
        a = Math.max(0, Math.min(255, (int) (a * multiplier)));
        return (a << 24) | (argb & 0x00FFFFFF);
    }

    // ── Text rendering with outline for contrast ──

    /**
     * Draw text with a dark outline for readability over any game scene.
     * Draws 4 shadow passes (±1px) in dark color, then the main text on top.
     */
    public static void drawOutlinedText(DrawContext ctx, TextRenderer renderer,
            Text text, int x, int y, int color) {
        drawOutlinedText(ctx, renderer, text, x, y, color, 1f);
    }

    /**
     * Draw outlined text with an additional alpha multiplier (for fade effects).
     */
    public static void drawOutlinedText(DrawContext ctx, TextRenderer renderer,
            Text text, int x, int y, int color, float alphaMult) {
        int outlineColor = withAlpha(0x000000, 0.4f * alphaMult);
        int mainColor = scaleAlpha(color, alphaMult);

        // 4-direction outline
        ctx.drawText(renderer, text, x - 1, y, outlineColor, false);
        ctx.drawText(renderer, text, x + 1, y, outlineColor, false);
        ctx.drawText(renderer, text, x, y - 1, outlineColor, false);
        ctx.drawText(renderer, text, x, y + 1, outlineColor, false);
        // Main text
        ctx.drawText(renderer, text, x, y, mainColor, false);
    }

    /**
     * Draw centered outlined text.
     */
    public static void drawCenteredOutlinedText(DrawContext ctx, TextRenderer renderer,
            Text text, int centerX, int y, int color) {
        int w = renderer.getWidth(text);
        drawOutlinedText(ctx, renderer, text, centerX - w / 2, y, color);
    }

    /**
     * Draw centered outlined text with alpha multiplier.
     */
    public static void drawCenteredOutlinedText(DrawContext ctx, TextRenderer renderer,
            Text text, int centerX, int y, int color, float alphaMult) {
        int w = renderer.getWidth(text);
        drawOutlinedText(ctx, renderer, text, centerX - w / 2, y, color, alphaMult);
    }

    // ── Scroll effects ──

    /**
     * Compute the alpha multiplier for a given entry Y position within the viewport.
     * Combines scroll-edge fade and spotlight scrolling.
     *
     * @param entryY       the Y position of the entry
     * @param viewportTop  the top of the visible scroll area
     * @param viewportBot  the bottom of the visible scroll area
     * @return alpha multiplier 0.0-1.0
     */
    public static float scrollAlpha(int entryY, int viewportTop, int viewportBot) {
        int edgeFade = 40;
        float alpha = 1f;

        // Edge fade at top
        if (entryY < viewportTop + edgeFade) {
            alpha = Math.max(0f, (entryY - viewportTop) / (float) edgeFade);
        }
        // Edge fade at bottom
        if (entryY > viewportBot - edgeFade) {
            alpha = Math.min(alpha, Math.max(0f, (viewportBot - entryY) / (float) edgeFade));
        }

        // Spotlight: center of viewport is brightest
        float viewportCenter = (viewportTop + viewportBot) / 2f;
        float viewportHalf = (viewportBot - viewportTop) / 2f;
        if (viewportHalf > 0) {
            float distFromCenter = Math.abs(entryY - viewportCenter) / viewportHalf;
            float spotlight = 1f - distFromCenter * 0.35f; // 65% at edges, 100% at center
            alpha *= Math.max(0.6f, spotlight);
        }

        return alpha;
    }

    // ── Modified setting indicator ──

    /**
     * Draw a small accent dot to the left of a widget if the setting is non-default.
     */
    public static void drawModifiedDot(DrawContext ctx, int x, int y, int entryHeight, boolean modified) {
        if (!modified) return;
        int dotSize = 4;
        int dotX = x - dotSize - 3;
        int dotY = y + (entryHeight - dotSize) / 2;
        ctx.fill(dotX, dotY, dotX + dotSize, dotY + dotSize, textAccent);
    }

    // ── Category accent line ──

    /**
     * Draw a thin accent line with centered category text.
     */
    public static void drawCategoryHeader(DrawContext ctx, TextRenderer renderer,
            Text text, int x, int y, int width, int entryHeight) {
        drawCategoryHeader(ctx, renderer, text, x, y, width, entryHeight, 1f);
    }

    /**
     * Draw a thin accent line with centered category text, with an alpha multiplier
     * for fade effects (e.g., slider focus fade).
     */
    public static void drawCategoryHeader(DrawContext ctx, TextRenderer renderer,
            Text text, int x, int y, int width, int entryHeight, float alphaMult) {
        if (alphaMult <= 0f) return;

        int lineY = y + entryHeight / 2;
        int textW = renderer.getWidth(text);
        int textX = x + (width - textW) / 2;
        int textY = y + entryHeight - 9 - 1;
        int lineColor = withAlpha(BASE_TEXT_ACCENT, 0.3f * alphaMult);

        // Accent line left of text
        if (textX > x + 4) {
            ctx.fill(x, lineY, textX - 4, lineY + 1, lineColor);
        }
        // Accent line right of text
        if (textX + textW + 4 < x + width) {
            ctx.fill(textX + textW + 4, lineY, x + width, lineY + 1, lineColor);
        }
        // Text
        drawOutlinedText(ctx, renderer, text, textX, textY,
                textAccent & 0x00FFFFFF | 0xFF000000, alphaMult);
    }
}
