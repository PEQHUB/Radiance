package com.radiance.client.gui.unified.rows;

import com.radiance.client.gui.RadianceTheme;
import com.radiance.client.gui.unified.RadianceUnifiedScreen;
import com.radiance.client.gui.unified.SettingsRow;
import java.util.List;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.text.Text;

/**
 * A button row that opens a separate Screen when clicked.
 * Uses custom flat rendering. Opens sub-screens as a modal overlay within the
 * unified screen instead of replacing it via setScreen().
 */
public class LauncherRow extends SettingsRow {

    private final ButtonWidget button;
    private final Text label;

    /** Create a launcher that opens a sub-screen via the unified screen's overlay. */
    public LauncherRow(String translationKey, Screen target, RadianceUnifiedScreen screen) {
        this.label = Text.translatable(translationKey);
        this.button = ButtonWidget.builder(label,
            btn -> screen.showOverlay(target))
            .width(150).build();
    }

    /** Create a launcher with explicit label text. */
    public LauncherRow(Text label, Screen target, RadianceUnifiedScreen screen) {
        this.label = label;
        this.button = ButtonWidget.builder(label,
            btn -> screen.showOverlay(target))
            .width(150).build();
    }

    /** Legacy constructor (opens via setScreen — use overlay constructors instead). */
    public LauncherRow(String translationKey, Screen target) {
        this.label = Text.translatable(translationKey);
        this.button = ButtonWidget.builder(label,
            btn -> MinecraftClient.getInstance().setScreen(target))
            .width(150).build();
    }

    /** Legacy constructor with Text label. */
    public LauncherRow(Text label, Screen target) {
        this.label = label;
        this.button = ButtonWidget.builder(label,
            btn -> MinecraftClient.getInstance().setScreen(target))
            .width(150).build();
    }

    @Override
    public void render(DrawContext context, int x, int y, int width,
                       int mouseX, int mouseY, float delta, float alphaMult) {
        if (alphaMult <= 0f) return;
        button.setX(x);
        button.setY(y);
        button.setWidth(width);

        // Custom rendering with "›" indicator
        boolean hovered = mouseX >= x && mouseX < x + width
            && mouseY >= y && mouseY < y + getHeight();
        Text displayLabel = Text.literal(label.getString() + "  \u203A");
        RadianceTheme.drawCustomButton(context, x, y, width, getHeight(),
            hovered, MinecraftClient.getInstance().textRenderer, displayLabel);
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
