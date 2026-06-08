package com.radiance.client.gui.unified.rows;

import com.radiance.client.gui.MaterialDropdownWidget;
import com.radiance.client.gui.ResettableSliderWidget;
import com.radiance.client.gui.unified.SettingsRow;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.widget.ClickableWidget;

/**
 * A 5-column row for emission controls: [Emission | Temp | Material | Wavelength | Purity].
 * Columns are equally spaced at 20% width each with 4px gaps.
 */
public class FiveColumnEmissionRow extends SettingsRow {

    private static final int GAP = 4;

    private final ResettableSliderWidget emission;
    private final ResettableSliderWidget temperature;
    private final MaterialDropdownWidget material;
    private final ResettableSliderWidget wavelength;
    private final ResettableSliderWidget purity;

    public FiveColumnEmissionRow(ResettableSliderWidget emission,
                                  ResettableSliderWidget temperature,
                                  MaterialDropdownWidget material,
                                  ResettableSliderWidget wavelength,
                                  ResettableSliderWidget purity) {
        this.emission = emission;
        this.temperature = temperature;
        this.material = material;
        this.wavelength = wavelength;
        this.purity = purity;
    }

    @Override
    public void render(DrawContext context, int x, int y, int width,
                       int mouseX, int mouseY, float delta, float alphaMult) {
        if (alphaMult <= 0f) return;

        int colW = (width - GAP * 4) / 5;

        // Column 1: Emission (position at 10% center)
        renderSlider(context, emission, x, y, width, colW, 1, mouseX, mouseY, delta);

        // Column 2: Temperature (position at 30% center)
        renderSlider(context, temperature, x, y, width, colW, 3, mouseX, mouseY, delta);

        // Column 3: Material dropdown (position at 50% center)
        if (material != null) {
            material.setX(x + width * 5 / 10 - colW / 2);
            material.setY(y);
            material.setWidth(colW);
            material.renderWidget(context, mouseX, mouseY, delta);
        }

        // Column 4: Wavelength (position at 70% center)
        renderSlider(context, wavelength, x, y, width, colW, 7, mouseX, mouseY, delta);

        // Column 5: Purity (position at 90% center)
        renderSlider(context, purity, x, y, width, colW, 9, mouseX, mouseY, delta);
    }

    private void renderSlider(DrawContext context, ResettableSliderWidget slider,
                               int x, int y, int width, int colW, int colIdx,
                               int mouseX, int mouseY, float delta) {
        if (slider == null) return;
        slider.setX(x + width * colIdx / 10 - colW / 2);
        slider.setY(y);
        slider.setWidth(colW);
        slider.render(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean isGridFullSpan() {
        return true;
    }

    /**
     * Render the material dropdown overlay AFTER all rows have rendered.
     * Call this from the populator's post-render pass.
     */
    public void renderDropdownOverlay(DrawContext context, int mouseX, int mouseY) {
        if (material != null) {
            material.renderDropdownOverlay(context, mouseX, mouseY);
        }
    }

    public MaterialDropdownWidget getMaterial() {
        return material;
    }

    @Override
    public List<? extends Element> children() {
        List<Element> list = new ArrayList<>();
        if (emission != null) list.add(emission);
        if (temperature != null) list.add(temperature);
        if (material != null) list.add(material);
        if (wavelength != null) list.add(wavelength);
        if (purity != null) list.add(purity);
        return list;
    }

    @Override
    public List<? extends ClickableWidget> clickableWidgets() {
        List<ClickableWidget> list = new ArrayList<>();
        if (emission != null) list.add(emission);
        if (temperature != null) list.add(temperature);
        if (material != null) list.add(material);
        if (wavelength != null) list.add(wavelength);
        if (purity != null) list.add(purity);
        return list;
    }
}
