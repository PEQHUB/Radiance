package com.radiance.client.gui.unified.rows;

import com.radiance.client.gui.RadianceTheme;
import com.radiance.client.gui.MaterialDropdownWidget;
import com.radiance.client.gui.SelectionDropdownWidget;
import com.radiance.client.gui.unified.SettingsRow;
import java.util.List;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.text.Text;

/**
 * A row with a label on the left and an ON/OFF toggle switch on the right.
 * Uses custom rendering instead of Minecraft's vanilla CyclingButtonWidget textures.
 * The underlying widget handles click logic; we just override the visual.
 */
public class ToggleRow extends SettingsRow {

    private final ClickableWidget widget;

    /** Cached "ON" translation for locale-safe toggle state detection. */
    private static final String ON_TEXT = Text.translatable("options.on").getString();

    public ToggleRow(ClickableWidget widget) {
        this.widget = widget;
    }

    @Override
    public void render(DrawContext context, int x, int y, int width,
                       int mouseX, int mouseY, float delta, float alphaMult) {
        if (alphaMult <= 0f) return;
        // Position the underlying widget (for click handling)
        widget.setX(x);
        widget.setY(y);
        widget.setWidth(width);
        if (widget instanceof SelectionDropdownWidget || widget instanceof MaterialDropdownWidget) {
            widget.render(context, mouseX, mouseY, delta);
            return;
        }

        // Detect toggle state from message text
        String msg = widget.getMessage().getString();
        boolean isOn = msg.contains(ON_TEXT);

        boolean hovered = mouseX >= x && mouseX < x + width
            && mouseY >= y && mouseY < y + getHeight();

        RadianceTheme.drawCompactToggle(context, x, y, width, getHeight(),
            isOn, hovered, MinecraftClient.getInstance().textRenderer, widget.getMessage());
    }

    @Override
    public List<? extends Element> children() {
        return List.of(widget);
    }

    @Override
    public List<? extends ClickableWidget> clickableWidgets() {
        return List.of(widget);
    }

    public ClickableWidget getWidget() {
        return widget;
    }
}
