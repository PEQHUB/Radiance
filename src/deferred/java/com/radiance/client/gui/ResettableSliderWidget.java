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
 */
public class ResettableSliderWidget extends SliderWidget {

    private final int min;
    private final int max;
    private int stockDefault;
    private final Consumer<Integer> onChange;
    private final IntFunction<Text> displayFormatter;

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
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && Screen.hasControlDown() && this.isMouseOver(mouseX, mouseY)) {
            MinecraftClient mc = MinecraftClient.getInstance();
            Screen parent = mc.currentScreen;
            mc.setScreen(new NumericSliderInputScreen(parent, this.getMessage(), current(), min, max, value -> {
                setCurrentValue(value);
                onChange.accept(value);
            }));
            return true;
        }

        if (button == 0 && Screen.hasShiftDown() && this.isMouseOver(mouseX, mouseY)) {
            // Reset to stock default
            this.value = (max == min) ? 0.0
                : (stockDefault - (double) min) / (double) (max - min);
            this.value = MathHelper.clamp(this.value, 0.0, 1.0);
            updateMessage();
            applyValue();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
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
}
