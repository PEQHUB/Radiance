package com.radiance.client.gui.unified.rows;

import com.radiance.client.gui.RadianceTheme;
import com.radiance.client.gui.unified.SettingsRow;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.text.Text;

/**
 * A row of N small buttons, all visible at once, with the selected one highlighted.
 * Used for preset selectors (DLSS quality, etc.) where cycling is poor UX.
 */
public class ButtonGroupRow extends SettingsRow {

    private static final int GAP = 4;
    private final String label;
    private final ButtonWidget[] buttons;
    private int selectedIndex;

    public ButtonGroupRow(String label, String[] names, int initialIndex, IntConsumer onSelect) {
        this.label = label;
        this.selectedIndex = initialIndex;
        this.buttons = new ButtonWidget[names.length];
        for (int i = 0; i < names.length; i++) {
            final int idx = i;
            buttons[i] = ButtonWidget.builder(Text.literal(names[i]), btn -> {
                selectedIndex = idx;
                onSelect.accept(idx);
            }).width(50).build();
        }
    }

    @Override
    public void render(DrawContext context, int x, int y, int width,
                       int mouseX, int mouseY, float delta, float alphaMult) {
        if (alphaMult <= 0f) return;
        TextRenderer tr = MinecraftClient.getInstance().textRenderer;

        // Label on the left
        int labelWidth = tr.getWidth(label) + 8;
        RadianceTheme.drawOutlinedText(context, tr, Text.literal(label),
            x + 4, y + (getHeight() - 8) / 2, RadianceTheme.textSecondary, alphaMult);

        // Buttons fill remaining width
        int btnArea = width - labelWidth;
        int btnCount = buttons.length;
        int btnW = (btnArea - GAP * (btnCount - 1)) / btnCount;

        for (int i = 0; i < btnCount; i++) {
            int bx = x + labelWidth + i * (btnW + GAP);
            int by = y;
            int bh = getHeight();
            buttons[i].setX(bx);
            buttons[i].setY(by);
            buttons[i].setWidth(btnW);

            boolean hovered = mouseX >= bx && mouseX < bx + btnW
                && mouseY >= by && mouseY < by + bh;
            boolean selected = (i == selectedIndex);

            if (selected) {
                RadianceTheme.drawCustomToggle(context, bx, by, btnW, bh,
                    true, hovered, tr, buttons[i].getMessage());
            } else {
                RadianceTheme.drawCustomButton(context, bx, by, btnW, bh,
                    hovered, tr, buttons[i].getMessage());
            }
        }
    }

    @Override
    public List<? extends Element> children() {
        return List.of(buttons);
    }

    @Override
    public List<? extends ClickableWidget> clickableWidgets() {
        List<ClickableWidget> list = new ArrayList<>();
        for (ButtonWidget b : buttons) list.add(b);
        return list;
    }

    @Override
    public boolean isGridFullSpan() {
        return true;
    }
}
