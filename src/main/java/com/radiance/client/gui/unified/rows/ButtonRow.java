package com.radiance.client.gui.unified.rows;

import com.radiance.client.gui.RadianceTheme;
import com.radiance.client.gui.unified.SettingsRow;
import java.util.List;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ClickableWidget;

/**
 * A row containing a single compact button.
 * Uses custom flat rendering instead of Minecraft's vanilla button textures.
 */
public class ButtonRow extends SettingsRow {

    private final ButtonWidget button;

    public ButtonRow(ButtonWidget button) {
        this.button = button;
    }

    @Override
    public void render(DrawContext context, int x, int y, int width,
                       int mouseX, int mouseY, float delta, float alphaMult) {
        if (alphaMult <= 0f) return;

        // Position the underlying widget (for click handling)
        button.setX(x);
        button.setY(y);
        button.setWidth(width);

        // Custom rendering instead of button.render()
        boolean hovered = mouseX >= x && mouseX < x + width
            && mouseY >= y && mouseY < y + getHeight();
        RadianceTheme.drawCustomButton(context, x, y, width, getHeight(),
            hovered, MinecraftClient.getInstance().textRenderer, button.getMessage(), button.active);
    }

    @Override
    public List<? extends Element> children() {
        return List.of(button);
    }

    @Override
    public List<? extends ClickableWidget> clickableWidgets() {
        return List.of(button);
    }
}
