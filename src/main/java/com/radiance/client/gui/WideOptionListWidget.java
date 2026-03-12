package com.radiance.client.gui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.option.GameOptionsScreen;
import net.minecraft.client.gui.widget.OptionListWidget;

/**
 * OptionListWidget subclass that uses the full screen width for entries,
 * draws themed backdrops behind each row, applies scroll-edge fade,
 * slider-focus fade, peek-mode hiding, and smooth scrolling.
 */
public class WideOptionListWidget extends OptionListWidget {

    /** Smooth-scroll state: the visually displayed scroll position. */
    private double displayScrollY = 0.0;
    /** Whether displayScrollY has been initialized from the actual scroll position. */
    private boolean scrollInitialized = false;
    /** Lerp speed for smooth scrolling (0-1, higher = snappier). */
    private static final float SCROLL_LERP_SPEED = 0.35f;

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

    // ── Smooth scrolling ──

    @Override
    public void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
        // Initialize displayScrollY on first frame
        if (!scrollInitialized) {
            displayScrollY = getScrollY();
            scrollInitialized = true;
        }

        // Lerp toward the target scroll position
        double target = getScrollY();
        double diff = target - displayScrollY;
        if (Math.abs(diff) < 0.5) {
            displayScrollY = target;
        } else {
            displayScrollY += diff * SCROLL_LERP_SPEED;
        }

        // Temporarily apply the smoothed scroll for rendering, then restore
        double originalScroll = getScrollY();
        setScrollY(displayScrollY);
        super.renderWidget(context, mouseX, mouseY, delta);
        setScrollY(originalScroll);
    }

    // ── Per-entry backdrop, fade, and peek mode ──

    @Override
    protected void renderEntry(DrawContext context, int mouseX, int mouseY, float delta,
                               int index, int rowLeft, int rowTop, int rowWidth, int rowHeight) {
        // Peek mode: skip rendering entirely
        if (RadianceTheme.peekActive) {
            return;
        }

        // Compute scroll-edge alpha for this entry's Y position
        int viewportTop = getY();
        int viewportBot = getBottom();
        float scrollAlpha = RadianceTheme.scrollAlpha(rowTop, viewportTop, viewportBot);

        // Compute slider-focus fade (inactive entries fade out during drag)
        float fadeFactor = RadianceTheme.inactiveFadeFactor();

        // Check if this entry contains the active slider (should stay fully visible)
        OptionListWidget.WidgetEntry entry = getEntry(index);
        boolean isActive = RadianceTheme.isActiveEntry(entry.children());
        float entryFade = isActive ? 1.0f : fadeFactor;

        // Combined alpha for this entry
        float combinedAlpha = scrollAlpha * entryFade;

        // Skip if fully transparent
        if (combinedAlpha <= 0.005f) {
            return;
        }

        // Draw subtle panel backdrop behind the entry row
        int backdropColor = RadianceTheme.scaleAlpha(RadianceTheme.panelBg, combinedAlpha);
        int pad = 2;
        context.fill(rowLeft - pad, rowTop - pad,
                     rowLeft + rowWidth + pad, rowTop + rowHeight + pad,
                     backdropColor);

        // If partially transparent, push alpha state via color modulation
        // (we render the entry normally — individual widgets handle their own colors,
        //  but the backdrop provides the visual fade cue)
        super.renderEntry(context, mouseX, mouseY, delta,
                          index, rowLeft, rowTop, rowWidth, rowHeight);
    }
}
