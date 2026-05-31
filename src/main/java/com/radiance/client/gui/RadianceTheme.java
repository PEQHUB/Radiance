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
    private static final int BASE_WIDGET      = 0x1E1E24;
    private static final int BASE_HOVER       = 0x2E2E36;
    private static final int BASE_ACTIVE      = 0x3E3E48;
    private static final int BASE_DROPDOWN    = 0x101014;
    private static final int BASE_HEADER      = 0x0A0A0A;
    private static final int BASE_BORDER      = 0x808080;
    private static final int BASE_BORDER_FOCUS = 0xD0C0B0;
    private static final int BASE_TEXT_PRIMARY = 0xFFFFFF;
    private static final int BASE_TEXT_SECONDARY = 0xB0B0B0;
    private static final int BASE_TEXT_ACCENT  = 0xE8712A;

    // ── Custom widget rendering palette ──
    private static final int BASE_SLIDER_TRACK = 0x252528;
    private static final int BASE_SLIDER_FILL  = 0xE8712A;
    private static final int BASE_SLIDER_THUMB = 0xE0E0E0;
    private static final int BASE_TEAL         = 0x2AB5A0;
    private static final int BASE_TOGGLE_ON    = 0x2AB5A0; // teal for ON state
    private static final int BASE_TOGGLE_OFF   = 0x404046;
    private static final int BASE_BUTTON_BG    = 0x1E1E24;
    private static final int BASE_BUTTON_HOVER = 0x2A2A32;
    private static final int BASE_BUTTON_BORDER= 0x404048;
    private static final int BASE_DISABLED     = 0x55555A;

    // ── Semantic colors (special purpose, fixed alpha) ──
    public static final int TEXT_ERROR    = 0xFFFF5555;
    public static final int TEXT_SUCCESS  = 0xFF55FF55;
    public static final int TEXT_LINK     = 0xFF55FFFF;
    public static final int TEXT_PATH     = 0xFFFFAA00;
    public static final int SELECTED_BAR  = 0xFFE8712A;
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
    public static int textCategory;   // teal for category headers
    public static int dividerLine;    // teal divider
    public static int modifiedDot;    // teal modified indicator

    // ── Custom widget derived colors ──
    public static int sliderTrack;
    public static int sliderFill;
    public static int sliderThumb;
    public static int toggleOn;
    public static int toggleOff;
    public static int buttonBg;
    public static int buttonHover;
    public static int buttonBorder;
    public static int disabledBg;
    public static int disabledBorder;
    public static int disabledText;
    public static int disabledTrack;

    // ── Unified screen panel backgrounds (alpha-aware with readability floor) ──
    public static int unifiedContentBg;
    public static int unifiedTreeBg;
    public static int unifiedHeaderBg;

    // ── Alpha state ──
    private static float globalAlpha = 0.85f;
    private static float effectiveAlpha = 0.85f; // after adaptive dimming

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

        panelBg       = withAlpha(BASE_PANEL, effectiveAlpha * 0.95f);
        widgetBg      = withAlpha(BASE_WIDGET, effectiveAlpha);
        widgetBgHover = withAlpha(BASE_HOVER, effectiveAlpha);
        widgetBgActive= withAlpha(BASE_ACTIVE, effectiveAlpha);
        dropdownBg    = withAlpha(BASE_DROPDOWN, Math.min(1f, effectiveAlpha + 0.15f));
        headerBg      = withAlpha(BASE_HEADER, effectiveAlpha * 0.5f);
        borderDefault = withAlpha(BASE_BORDER, effectiveAlpha * 0.6f);
        borderFocused = withAlpha(BASE_BORDER_FOCUS, effectiveAlpha);
        textPrimary   = withAlpha(BASE_TEXT_PRIMARY, 1.0f);
        textSecondary = withAlpha(BASE_TEXT_SECONDARY, 0.9f);
        textAccent    = withAlpha(BASE_TEXT_ACCENT, 1.0f);
        textCategory  = withAlpha(BASE_TEAL, 1.0f);
        dividerLine   = withAlpha(BASE_TEAL, 0.4f);
        modifiedDot   = withAlpha(BASE_TEAL, 0.8f);

        // Custom widget colors — minimum 0.3 alpha so the opacity slider never fades itself out
        float sliderAlpha = Math.max(effectiveAlpha, 0.3f);
        sliderTrack   = withAlpha(BASE_SLIDER_TRACK, sliderAlpha);
        sliderFill    = withAlpha(BASE_SLIDER_FILL, sliderAlpha);
        sliderThumb   = withAlpha(BASE_SLIDER_THUMB, 1.0f);
        toggleOn      = withAlpha(BASE_TOGGLE_ON, effectiveAlpha);
        toggleOff     = withAlpha(BASE_TOGGLE_OFF, effectiveAlpha);
        buttonBg      = withAlpha(BASE_BUTTON_BG, effectiveAlpha);
        buttonHover   = withAlpha(BASE_BUTTON_HOVER, effectiveAlpha);
        buttonBorder  = withAlpha(BASE_BUTTON_BORDER, effectiveAlpha * 0.6f);
        disabledBg    = withAlpha(0x141416, Math.max(0.28f, effectiveAlpha * 0.55f));
        disabledBorder= withAlpha(BASE_DISABLED, Math.max(0.20f, effectiveAlpha * 0.35f));
        disabledText  = withAlpha(0x8A8A8F, 0.78f);
        disabledTrack = withAlpha(0x303034, Math.max(0.25f, effectiveAlpha * 0.45f));

        // Unified panel backgrounds — track globalAlpha fully
        float panelAlpha = Math.min(0.92f, effectiveAlpha * 1.3f);
        unifiedContentBg = withAlpha(0x0C0C0E, panelAlpha);
        unifiedTreeBg    = withAlpha(0x0A0A0C, Math.min(0.92f, panelAlpha + 0.04f * effectiveAlpha));
        unifiedHeaderBg  = withAlpha(0x080808, Math.min(0.95f, panelAlpha + 0.08f * effectiveAlpha));
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
            // Fade out to a floor (never fully invisible so user retains context)
            float t = Math.min(1f, elapsed / (float) FADE_OUT_MS);
            return 0.15f + (1f - 0.15f) * (1f - t);
        } else {
            // Fading back in from floor
            float t = Math.min(1f, elapsed / (float) FADE_IN_MS);
            if (t >= 1f) fadeStartMs = 0; // Animation complete
            return 0.15f + (1f - 0.15f) * t;
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
        int outlineColor = withAlpha(0x000000, 0.9f * alphaMult);
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
     * Draw a tiny in-bounds dot if the setting is non-default.
     */
    public static void drawModifiedDot(DrawContext ctx, int x, int y, int entryHeight, boolean modified) {
        if (!modified) return;
        int dotX = x + 2;
        int dotY = y + 2;
        ctx.fill(dotX, dotY, dotX + 2, dotY + 2, modifiedDot);
    }

    // ── Breadcrumb navigation ──

    private static final int BREADCRUMB_Y = 26;
    private static int breadcrumbTextEndX = 0;

    /**
     * Draw a clickable breadcrumb at the standard position (20, 26).
     * The breadcrumb text is rendered with link-style coloring.
     * Call {@link #handleBreadcrumbClick} in mouseClicked to handle navigation.
     */
    public static void drawBreadcrumb(DrawContext ctx, TextRenderer renderer,
            String breadcrumbText, Screen parentScreen) {
        // Split into segments by " > "
        String[] parts = breadcrumbText.split(" > ");
        int x = 20;
        for (int i = 0; i < parts.length; i++) {
            boolean isLast = (i == parts.length - 1);
            int color = isLast ? textSecondary : TEXT_LINK;
            Text seg = Text.literal(parts[i]);
            drawOutlinedText(ctx, renderer, seg, x, BREADCRUMB_Y, color);
            x += renderer.getWidth(seg);
            if (!isLast) {
                Text sep = Text.literal(" > ");
                drawOutlinedText(ctx, renderer, sep, x, BREADCRUMB_Y, textSecondary);
                x += renderer.getWidth(sep);
            }
        }
        breadcrumbTextEndX = x;
    }

    /**
     * Check if a mouse click hit the breadcrumb area and navigate to parent if so.
     * @return true if the click was consumed (navigated to parent)
     */
    public static boolean handleBreadcrumbClick(double mouseX, double mouseY, Screen parent) {
        if (parent != null
                && mouseY >= BREADCRUMB_Y - 6 && mouseY <= BREADCRUMB_Y + 16
                && mouseX >= 20 && mouseX <= breadcrumbTextEndX) {
            net.minecraft.client.MinecraftClient.getInstance().setScreen(parent);
            return true;
        }
        return false;
    }

    /**
     * Handle Tab key for peek mode. Call from sub-screen keyPressed/keyReleased.
     * @return true if the key event was consumed
     */
    public static boolean handlePeekKeyPressed(int keyCode) {
        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_TAB) {
            peekActive = true;
            return true;
        }
        return false;
    }

    public static boolean handlePeekKeyReleased(int keyCode) {
        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_TAB) {
            peekActive = false;
            return true;
        }
        return false;
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

        int textW = renderer.getWidth(text);
        int textX = x + (width - textW) / 2;
        int textY = y + (entryHeight - 9) / 2; // vertically centered text
        int lineY = textY + 4; // line through text midline
        int lineColor = withAlpha(BASE_TEAL, 0.4f * alphaMult);

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
                textCategory & 0x00FFFFFF | 0xFF000000, alphaMult);
    }

    // ── Modern section header (left-aligned, thin underline) ──

    /**
     * Draw a modern section header: left-aligned label in accent color with
     * a thin horizontal rule underneath. Replaces the centered category header
     * for the unified settings UI.
     */
    public static void drawSectionHeader(DrawContext ctx, TextRenderer renderer,
            Text text, int x, int y, int width, int headerHeight, float alphaMult) {
        if (alphaMult <= 0f) return;

        int textY = y + (headerHeight - 8) / 2;
        drawOutlinedText(ctx, renderer, text, x + 4, textY,
                textAccent & 0x00FFFFFF | 0xFF000000, alphaMult);

        // Thin horizontal rule below text
        int lineY = y + headerHeight - 2;
        int lineColor = withAlpha(BASE_TEXT_ACCENT, 0.25f * alphaMult);
        ctx.fill(x, lineY, x + width, lineY + 1, lineColor);
    }

    // ── Custom widget rendering ──

    /**
     * Render a custom flat slider: thin track, orange fill, white thumb, centered label.
     * Replaces Minecraft's vanilla sprite-based slider rendering.
     */
    public static void drawCustomSlider(DrawContext ctx, int x, int y, int w, int h,
            double value, boolean hovered, boolean active,
            TextRenderer renderer, Text message) {
        drawCustomSlider(ctx, x, y, w, h, value, hovered, active, renderer, message, false);
    }

    public static void drawCustomSlider(DrawContext ctx, int x, int y, int w, int h,
            double value, boolean hovered, boolean active,
            TextRenderer renderer, Text message, boolean modified) {
        drawCustomSlider(ctx, x, y, w, h, value, hovered, active, renderer, message, modified, true);
    }

    public static void drawCustomSlider(DrawContext ctx, int x, int y, int w, int h,
            double value, boolean hovered, boolean active,
            TextRenderer renderer, Text message, boolean modified, boolean enabled) {
        // Dark backdrop behind slider so it remains visible over the game scene.
        // Always drawn (not just when active) so sliders are readable in the menu.
        float bgAlpha = enabled ? (active ? 0.75f : 0.55f) : 0.42f;
        ctx.fill(x, y, x + w, y + h, enabled ? withAlpha(0x000000, bgAlpha) : disabledBg);

        // Track background — use higher alpha when active for visibility
        int trackH = 4;
        int trackY = y + (h - trackH) / 2;
        int trackColor = !enabled ? disabledTrack : (active ? withAlpha(BASE_SLIDER_TRACK, 0.9f) : sliderTrack);
        ctx.fill(x, trackY, x + w, trackY + trackH, trackColor);

        // Filled portion (accent color)
        int fillW = Math.max(0, Math.min(w, (int) Math.round(w * value)));
        int fillColor = !enabled ? withAlpha(BASE_DISABLED, 0.45f) : (active ? withAlpha(BASE_SLIDER_FILL, 0.95f) : sliderFill);
        if (fillW > 0) {
            ctx.fill(x, trackY, x + fillW, trackY + trackH, fillColor);
        }

        // Thumb
        int thumbW = 8, thumbH = 14;
        int thumbX = Math.max(x, Math.min(x + fillW - thumbW / 2, x + w - thumbW));
        int thumbY = y + (h - thumbH) / 2;
        int thumbColor = !enabled ? withAlpha(BASE_DISABLED, 0.72f)
            : (active ? sliderFill : (hovered ? scaleAlpha(sliderThumb, 0.9f) : sliderThumb));
        ctx.fill(thumbX, thumbY, thumbX + thumbW, thumbY + thumbH, thumbColor);

        // Label centered on widget
        int maxTextW = Math.max(24, w - 10);
        Text drawMessage = trimText(renderer, message, maxTextW);
        int textW = renderer.getWidth(drawMessage);
        int labelColor = !enabled ? disabledText : (modified ? modifiedDot : textPrimary);
        drawOutlinedText(ctx, renderer, drawMessage,
                x + (w - textW) / 2, y + (h - 8) / 2, labelColor);
    }

    /**
     * Render a compact toggle row: label text plus a small switch, without a
     * full-width button slab.
     */
    public static void drawCompactToggle(DrawContext ctx, int x, int y, int w, int h,
            boolean value, boolean hovered, TextRenderer renderer, Text message) {
        drawCompactToggle(ctx, x, y, w, h, value, hovered, renderer, message, true);
    }

    public static void drawCompactToggle(DrawContext ctx, int x, int y, int w, int h,
            boolean value, boolean hovered, TextRenderer renderer, Text message, boolean enabled) {
        if (hovered) {
            ctx.fill(x, y, x + w, y + h, enabled ? scaleAlpha(widgetBgHover, 0.45f) : disabledBg);
        }

        int swW = 28, swH = 14;
        int swX = x + w - swW - 16;
        int swY = y + (h - swH) / 2;
        int swBg = !enabled ? disabledTrack : (value ? toggleOn : toggleOff);
        ctx.fill(swX, swY, swX + swW, swY + swH, swBg);

        int knobW = 10, knobH = swH - 2;
        int knobX = value ? swX + swW - knobW - 1 : swX + 1;
        ctx.fill(knobX, swY + 1, knobX + knobW, swY + 1 + knobH,
            enabled ? sliderThumb : withAlpha(BASE_DISABLED, 0.72f));

        String label = compactToggleLabel(message.getString());
        int maxTextW = Math.max(24, swX - x - 10);
        Text labelText = trimText(renderer, Text.literal(label), maxTextW);
        drawOutlinedText(ctx, renderer, labelText, x + 6, y + (h - 8) / 2,
            enabled ? (hovered ? textPrimary : textSecondary) : disabledText);
    }

    /**
     * Render a custom flat button: dark bg, subtle border, centered label.
     * Replaces Minecraft's vanilla button textures.
     */
    public static void drawCustomButton(DrawContext ctx, int x, int y, int w, int h,
            boolean hovered, TextRenderer renderer, Text message) {
        drawCustomButton(ctx, x, y, w, h, hovered, renderer, message, true);
    }

    public static void drawCustomButton(DrawContext ctx, int x, int y, int w, int h,
            boolean hovered, TextRenderer renderer, Text message, boolean enabled) {
        int bg = !enabled ? disabledBg : (hovered ? buttonHover : buttonBg);
        ctx.fill(x, y, x + w, y + h, bg);
        ctx.drawBorder(x, y, w, h, enabled ? buttonBorder : disabledBorder);
        Text drawMessage = trimText(renderer, message, Math.max(16, w - 10));
        int textW = renderer.getWidth(drawMessage);
        drawOutlinedText(ctx, renderer, drawMessage,
                x + (w - textW) / 2, y + (h - 8) / 2,
                enabled ? (hovered ? textPrimary : textSecondary) : disabledText);
    }

    /**
     * Render a custom toggle widget: label on left, ON/OFF switch on right.
     * Replaces Minecraft's vanilla CyclingButtonWidget rendering.
     */
    public static void drawCustomToggle(DrawContext ctx, int x, int y, int w, int h,
            boolean value, boolean hovered, TextRenderer renderer, Text message) {
        drawCustomToggle(ctx, x, y, w, h, value, hovered, renderer, message, true);
    }

    public static void drawCustomToggle(DrawContext ctx, int x, int y, int w, int h,
            boolean value, boolean hovered, TextRenderer renderer, Text message, boolean enabled) {
        // Background
        int bg = !enabled ? disabledBg : (hovered ? buttonHover : buttonBg);
        ctx.fill(x, y, x + w, y + h, bg);
        ctx.drawBorder(x, y, w, h, enabled ? buttonBorder : disabledBorder);

        // Toggle switch on right side
        int swW = 28, swH = 14;
        int swX = x + w - swW - 6;
        int swY = y + (h - swH) / 2;
        int swBg = !enabled ? disabledTrack : (value ? toggleOn : toggleOff);
        ctx.fill(swX, swY, swX + swW, swY + swH, swBg);
        // Thumb knob
        int knobW = 10, knobH = swH - 2;
        int knobX = value ? swX + swW - knobW - 1 : swX + 1;
        ctx.fill(knobX, swY + 1, knobX + knobW, swY + 1 + knobH,
            enabled ? sliderThumb : withAlpha(BASE_DISABLED, 0.72f));

        // Label on left
        Text drawMessage = trimText(renderer, message, Math.max(24, swX - x - 10));
        drawOutlinedText(ctx, renderer, drawMessage, x + 6, y + (h - 8) / 2,
            enabled ? textPrimary : disabledText);
    }

    public static Text trimText(TextRenderer renderer, Text text, int maxWidth) {
        if (renderer.getWidth(text) <= maxWidth) {
            return text;
        }
        int ellipsisWidth = renderer.getWidth("...");
        String trimmed = renderer.trimToWidth(text.getString(), Math.max(0, maxWidth - ellipsisWidth));
        return Text.literal(trimmed + "...");
    }

    private static String compactToggleLabel(String message) {
        int colon = message.lastIndexOf(':');
        if (colon > 0) {
            String suffix = message.substring(colon + 1).trim();
            if (suffix.equalsIgnoreCase("ON") || suffix.equalsIgnoreCase("OFF")) {
                return message.substring(0, colon).trim();
            }
        }
        return message;
    }
}
