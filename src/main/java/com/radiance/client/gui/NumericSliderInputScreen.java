package com.radiance.client.gui;

import java.util.function.IntConsumer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;

public class NumericSliderInputScreen extends Screen {

    private final Screen parent;
    private final int min;
    private final int max;
    private final IntConsumer onSubmit;
    private TextFieldWidget valueField;

    public NumericSliderInputScreen(Screen parent, Text title, int currentValue, int min, int max, IntConsumer onSubmit) {
        super(title);
        this.parent = parent;
        this.min = min;
        this.max = max;
        this.onSubmit = onSubmit;
        this.valueField = null;
        this.initialValue = Integer.toString(currentValue);
    }

    private final String initialValue;

    @Override
    protected void init() {
        int w = 160;
        int x = this.width / 2 - w / 2;
        int y = this.height / 2 - 10;

        valueField = new TextFieldWidget(this.textRenderer, x, y, w, 20, Text.empty());
        valueField.setText(initialValue);
        valueField.setMaxLength(16);
        valueField.setFocused(true);
        this.addSelectableChild(valueField);
        this.addDrawableChild(valueField);

        this.addDrawableChild(ButtonWidget.builder(Text.translatable("gui.done"), b -> submit())
            .dimensions(this.width / 2 - 102, y + 28, 100, 20)
            .build());
        this.addDrawableChild(ButtonWidget.builder(Text.translatable("gui.cancel"), b -> close())
            .dimensions(this.width / 2 + 2, y + 28, 100, 20)
            .build());
    }

    private void submit() {
        try {
            int value = Integer.parseInt(valueField.getText().trim());
            value = MathHelper.clamp(value, min, max);
            onSubmit.accept(value);
            close();
        } catch (NumberFormatException ignored) {
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 257 || keyCode == 335) {
            submit();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void close() {
        MinecraftClient.getInstance().setScreen(parent);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, this.height / 2 - 34, 0xFFFFFF);
        context.drawCenteredTextWithShadow(this.textRenderer,
            Text.literal("Range: " + min + " - " + max),
            this.width / 2,
            this.height / 2 - 22,
            0xA0A0A0);
        super.render(context, mouseX, mouseY, delta);
    }
}
