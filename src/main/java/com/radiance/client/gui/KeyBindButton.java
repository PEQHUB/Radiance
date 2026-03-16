package com.radiance.client.gui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.PressableWidget;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

/**
 * A small square button that displays the current key binding.
 * Click to enter listening mode, then press a key to rebind.
 */
public class KeyBindButton extends PressableWidget {

    private final KeyBinding binding;
    private boolean listening = false;

    public KeyBindButton(int x, int y, KeyBinding binding) {
        super(x, y, 24, 20, Text.literal(getKeyName(binding)));
        this.binding = binding;
    }

    private static String getKeyName(KeyBinding binding) {
        String name = binding.getBoundKeyLocalizedText().getString();
        // Shorten common prefixes
        if (name.startsWith("key.keyboard.")) {
            name = name.substring("key.keyboard.".length());
        }
        return name;
    }

    @Override
    public void onPress() {
        listening = true;
        setMessage(Text.literal("..."));
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (listening) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                // Cancel rebinding
                listening = false;
                setMessage(Text.literal(getKeyName(binding)));
                return true;
            }
            binding.setBoundKey(InputUtil.fromKeyCode(keyCode, scanCode));
            KeyBinding.updateKeysByCode();
            MinecraftClient.getInstance().options.write();
            listening = false;
            setMessage(Text.literal(getKeyName(binding)));
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    protected void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
        TextRenderer textRenderer = MinecraftClient.getInstance().textRenderer;
        boolean hovered = isHovered();

        int bgColor = listening ? 0xFF4488FF : (hovered ? 0xFF555555 : 0xFF333333);
        int borderColor = listening ? 0xFF6699FF : (hovered ? 0xFF888888 : 0xFF555555);

        // Draw background
        context.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), bgColor);
        // Draw border
        context.drawBorder(getX(), getY(), getWidth(), getHeight(), borderColor);

        // Draw key text centered
        String text = getMessage().getString();
        int textWidth = textRenderer.getWidth(text);
        int textX = getX() + (getWidth() - textWidth) / 2;
        int textY = getY() + (getHeight() - 8) / 2;
        context.drawTextWithShadow(textRenderer, text, textX, textY, 0xFFFFFF);
    }

    @Override
    protected void appendClickableNarrations(net.minecraft.client.gui.screen.narration.NarrationMessageBuilder builder) {
        this.appendDefaultNarrations(builder);
    }
}
