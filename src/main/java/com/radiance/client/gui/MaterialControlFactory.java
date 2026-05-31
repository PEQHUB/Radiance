package com.radiance.client.gui;

import java.util.function.Consumer;
import java.util.function.IntFunction;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

public final class MaterialControlFactory {
    private MaterialControlFactory() {}

    public static ResettableSliderWidget slider(
            int min,
            int max,
            int currentValue,
            int stockDefault,
            IntFunction<Text> displayFormatter,
            Consumer<Integer> onChange,
            MaterialControlState state,
            Runnable onRelease) {
        ResettableSliderWidget slider = new ResettableSliderWidget(
            0, 0, 150, 20, min, max, currentValue, stockDefault, displayFormatter, onChange);
        slider.active = state == null || state.enabled();
        if (onRelease != null) {
            slider.setOnRelease(onRelease);
        }
        return slider;
    }

    public static ButtonWidget button(String label, Runnable action, MaterialControlState state) {
        ButtonWidget button = ButtonWidget.builder(Text.literal(label), b -> action.run())
            .dimensions(0, 0, 150, 20)
            .build();
        button.active = state == null || state.enabled();
        return button;
    }
}
