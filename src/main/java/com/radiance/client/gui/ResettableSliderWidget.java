package com.radiance.client.gui;

import java.util.function.Consumer;
import java.util.function.IntFunction;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;

/**
 * A slider that resets to its stock default value when Shift+Clicked.
 *
 * <p>Features:
 * <ul>
 *   <li>Ctrl+Click: opens numeric input screen for exact value entry</li>
 *   <li>Shift+Click: resets to stock default</li>
 *   <li>Left-drag: normal slider behavior + vanish-on-drag focus mode</li>
 *   <li>Right-click-drag: precision mode (1/10th speed)</li>
 *   <li>isDefault(): reports whether the current value matches stockDefault</li>
 * </ul>
 */
public class ResettableSliderWidget extends SliderWidget {

    private final int min;
    private final int max;
    private int stockDefault;
    private final Consumer<Integer> onChange;
    private final IntFunction<Text> displayFormatter;
    private Runnable onRelease;

    /** Optional setting key for recent-tweaks tracking. */
    public String settingKey;

    // ── Drag / precision state ──
    private boolean dragging = false;
    private boolean precisionDragging = false;
    private double dragAnchorX;
    private double valueAtDragStart;

    /** Precision mode slows the drag rate by this factor. */
    private static final double PRECISION_FACTOR = 0.1;

    public ResettableSliderWidget(int x, int y, int width, int height,
                                  int min, int max, int currentValue, int stockDefault,
                                  IntFunction<Text> displayFormatter,
                                  Consumer<Integer> onChange) {
        super(x, y, width, height, Text.empty(),
            (max == min) ? 0.0 : (currentValue - (double) min) / (double) (max - min));
        this.min = min;
        this.max = max;
        this.stockDefault = stockDefault;
        this.onChange = onChange;
        this.displayFormatter = displayFormatter;
        updateMessage();
    }

    private int current() {
        if (max == min) return min;
        return min + (int) Math.round(this.value * (max - min));
    }

    @Override
    protected void updateMessage() {
        setMessage(displayFormatter.apply(current()));
    }

    @Override
    protected void applyValue() {
        int v = MathHelper.clamp(current(), min, max);
        onChange.accept(v);
        // Track recent tweak if setting key is set
        if (settingKey != null) {
            RecentTweaksManager.recordTweak(settingKey, getMessage().getString());
        }
    }

    // ── Mouse handling ──

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // Ctrl+Click: numeric input
        if (button == 0 && Screen.hasControlDown() && this.isMouseOver(mouseX, mouseY)) {
            MinecraftClient mc = MinecraftClient.getInstance();
            Screen parent = mc.currentScreen;
            mc.setScreen(new NumericSliderInputScreen(parent, this.getMessage(), current(), min, max, value -> {
                setCurrentValue(value);
                onChange.accept(value);
            }));
            return true;
        }

        // Shift+Click: reset to stock default
        if (button == 0 && Screen.hasShiftDown() && this.isMouseOver(mouseX, mouseY)) {
            this.value = (max == min) ? 0.0
                : (stockDefault - (double) min) / (double) (max - min);
            this.value = MathHelper.clamp(this.value, 0.0, 1.0);
            updateMessage();
            applyValue();
            return true;
        }

        // Right-click: begin precision drag
        if (button == 1 && this.isMouseOver(mouseX, mouseY)) {
            beginDrag(mouseX, true);
            return true;
        }

        // Left-click: normal drag (handled by super, but we hook the focus mode)
        if (button == 0 && this.isMouseOver(mouseX, mouseY)) {
            beginDrag(mouseX, false);
            return super.mouseClicked(mouseX, mouseY, button);
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (precisionDragging && button == 1) {
            // Precision drag: compute delta from anchor and apply at 1/10th scale
            double rawDelta = (mouseX - dragAnchorX) / (double) (this.width - 8);
            this.value = MathHelper.clamp(valueAtDragStart + rawDelta * PRECISION_FACTOR, 0.0, 1.0);
            updateMessage();
            applyValue();
            return true;
        }
        if (dragging && button == 0) {
            return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 1 && precisionDragging) {
            endDrag();
            return true;
        }
        if (button == 0 && dragging) {
            // endDrag already fires onRelease, so skip the super.onRelease path
            endDrag();
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public void onRelease(double mouseX, double mouseY) {
        super.onRelease(mouseX, mouseY);
        // Only fire if not already fired by endDrag (non-drag clicks)
        if (!dragging && this.onRelease != null) this.onRelease.run();
    }

    // ── Drag helpers ──

    private void beginDrag(double mouseX, boolean precision) {
        this.dragging = true;
        this.precisionDragging = precision;
        this.dragAnchorX = mouseX;
        this.valueAtDragStart = this.value;
        RadianceTheme.beginSliderFocus(this);
    }

    private void endDrag() {
        this.dragging = false;
        this.precisionDragging = false;
        RadianceTheme.endSliderFocus();
        if (this.onRelease != null) this.onRelease.run();
    }

    // ── Public API ──

    /** Set a callback to run when the mouse is released after dragging. */
    public void setOnRelease(Runnable onRelease) {
        this.onRelease = onRelease;
    }

    /** Update the slider position externally (e.g. when tonemapper changes). */
    public void setCurrentValue(int newValue) {
        this.value = (max == min) ? 0.0
            : (newValue - (double) min) / (double) (max - min);
        this.value = MathHelper.clamp(this.value, 0.0, 1.0);
        updateMessage();
    }

    /** Update the stock default (e.g. when tonemapper changes). */
    public void setStockDefault(int newDefault) {
        this.stockDefault = newDefault;
    }

    /** Whether the current value matches the stock default. */
    public boolean isDefault() {
        return current() == stockDefault;
    }
}
