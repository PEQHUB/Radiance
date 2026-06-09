package com.radiance.client.gui.unified.rows;

import com.radiance.client.gui.RadianceTheme;
import com.radiance.client.gui.unified.SettingsRow;
import java.util.List;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Element;
import net.minecraft.text.Text;

/**
 * Read-only settings row for diagnostics and status text.
 */
public class InfoRow extends SettingsRow {
    private final Text label;
    private final Text value;

    public InfoRow(String label, String value) {
        this(Text.literal(label), Text.literal(value));
    }

    public InfoRow(Text label, Text value) {
        this.label = label;
        this.value = value;
    }

    @Override
    public void render(DrawContext context, int x, int y, int width,
                       int mouseX, int mouseY, float delta, float alphaMult) {
        if (alphaMult <= 0f) return;
        TextRenderer renderer = MinecraftClient.getInstance().textRenderer;
        int textY = y + (getHeight() - 8) / 2;
        int labelColor = RadianceTheme.scaleAlpha(RadianceTheme.textSecondary, alphaMult);
        int valueColor = RadianceTheme.scaleAlpha(RadianceTheme.textPrimary, alphaMult);
        context.drawTextWithShadow(renderer, label, x + 4, textY, labelColor);

        int labelWidth = Math.min(width / 2, renderer.getWidth(label) + 10);
        String valueText = value.getString();
        int maxValueWidth = Math.max(24, width - labelWidth - 8);
        while (renderer.getWidth(valueText) > maxValueWidth && valueText.length() > 4) {
            valueText = valueText.substring(0, valueText.length() - 4) + "...";
        }
        context.drawTextWithShadow(renderer, valueText, x + labelWidth, textY, valueColor);
    }

    @Override
    public List<? extends Element> children() {
        return List.of();
    }

    @Override
    public boolean isGridFullSpan() {
        return true;
    }
}
