package com.radiance.client.gui.unified.rows;

import com.radiance.client.gui.KeyBindButton;
import com.radiance.client.gui.RadianceTheme;
import com.radiance.client.gui.unified.SettingsRow;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

/**
 * A row with a small keybind button on the left and an optional widget on the right.
 * If no right widget is provided, shows just the keybind button + label.
 */
public class KeyBindRow extends SettingsRow {

    private static final int MIN_KEYBIND_WIDTH = 28;
    private static final int MAX_KEYBIND_WIDTH = 84;
    private static final int KEYBIND_PAD = 12;
    private static final int GAP = 4;
    private static final String ON_TEXT = Text.translatable("options.on").getString();

    private final KeyBindButton keyBindButton;
    private final ClickableWidget rightWidget; // nullable — if null, just label text
    private final String label; // only used when rightWidget is null

    /** Keybind + toggle/button widget on the right. */
    public KeyBindRow(KeyBindButton keyBindButton, ClickableWidget rightWidget) {
        this.keyBindButton = keyBindButton;
        this.rightWidget = rightWidget;
        this.label = null;
    }

    /** Keybind + label only (for action-only bindings like F7/F5). */
    public KeyBindRow(KeyBindButton keyBindButton, String label) {
        this.keyBindButton = keyBindButton;
        this.rightWidget = null;
        this.label = label;
    }

    @Override
    public void render(DrawContext context, int x, int y, int width,
                       int mouseX, int mouseY, float delta, float alphaMult) {
        if (alphaMult <= 0f) return;
        TextRenderer renderer = MinecraftClient.getInstance().textRenderer;
        int keyWidth = keyBindWidth(renderer, width);

        // Keybind button on the left
        keyBindButton.setX(x);
        keyBindButton.setY(y);
        keyBindButton.setWidth(keyWidth);
        keyBindButton.render(context, mouseX, mouseY, delta);

        int rightX = x + keyWidth + GAP;
        int rightW = Math.max(0, width - keyWidth - GAP);

        if (rightWidget != null) {
            rightWidget.setX(rightX);
            rightWidget.setY(y);
            rightWidget.setWidth(rightW);

            // Detect toggle vs button
            String msg = rightWidget.getMessage().getString();
            boolean looksLikeToggle = msg.contains(ON_TEXT)
                || msg.contains(Text.translatable("options.off").getString());
            boolean hovered = mouseX >= rightX && mouseX < rightX + rightW
                && mouseY >= y && mouseY < y + getHeight();

            if (looksLikeToggle) {
                boolean isOn = msg.contains(ON_TEXT);
                RadianceTheme.drawCustomToggle(context, rightX, y, rightW, getHeight(),
                    isOn, hovered, renderer, rightWidget.getMessage());
            } else {
                RadianceTheme.drawCustomButton(context, rightX, y, rightW, getHeight(),
                    hovered, renderer, rightWidget.getMessage());
            }
        } else if (label != null) {
            // Just draw a label
            int textY = y + (getHeight() - 8) / 2;
            Text drawLabel = RadianceTheme.trimText(renderer, Text.literal(label), Math.max(0, rightW - 8));
            context.drawTextWithShadow(renderer, drawLabel, rightX + 4, textY, RadianceTheme.textPrimary);
        }
    }

    private int keyBindWidth(TextRenderer renderer, int availableWidth) {
        int wanted = renderer.getWidth(keyBindButton.getMessage()) + KEYBIND_PAD;
        int clamped = Math.max(MIN_KEYBIND_WIDTH, Math.min(MAX_KEYBIND_WIDTH, wanted));
        return Math.min(Math.max(MIN_KEYBIND_WIDTH, availableWidth), clamped);
    }

    @Override
    public List<? extends Element> children() {
        List<Element> list = new ArrayList<>();
        list.add(keyBindButton);
        if (rightWidget != null) list.add(rightWidget);
        return list;
    }

    @Override
    public List<? extends ClickableWidget> clickableWidgets() {
        List<ClickableWidget> list = new ArrayList<>();
        list.add(keyBindButton);
        if (rightWidget != null) list.add(rightWidget);
        return list;
    }

    @Override
    public boolean isGridFullSpan() {
        return true;
    }

}
