package com.radiance.client.gui;

import static net.minecraft.client.option.GameOptions.getGenericValueText;

import com.radiance.client.option.Options;
import com.radiance.client.util.CategoryVideoOptionEntry;
import com.radiance.client.util.EmissiveBlock;
import com.radiance.client.util.FlameColorant;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.option.GameOptionsScreen;
import net.minecraft.text.Text;

public class EmissiveBlockSettingsScreen extends GameOptionsScreen {

    private final Screen parentScreen;
    private final List<MaterialDropdownWidget> dropdowns = new ArrayList<>();

    public EmissiveBlockSettingsScreen(Screen parent) {
        super(parent, MinecraftClient.getInstance().options, Text.translatable("radiance.settings.emission.title"));
        this.parentScreen = parent;
    }

    @Override
    protected void initBody() {
        MaterialDropdownWidget.clearInstances();
        dropdowns.clear();
        this.body = this.layout.addBody(
            new WideOptionListWidget(this.client, this.width, this));
        addOptions();
    }

    @Override
    protected void addOptions() {
        this.body.addEntry(new CategoryVideoOptionEntry(Text.translatable(Options.CATEGORY_EMISSION), body));

        // 5-column layout: [Emission | Temperature | Material | Wavelength | Purity]
        // Non-thermal blocks: [Emission | blank | blank | blank | blank]
        for (EmissiveBlock block : EmissiveBlock.values()) {
            ResettableSliderWidget em = makeEmissionSlider(block);
            ResettableSliderWidget temp = block.isThermal() ? makeTemperatureSlider(block) : null;
            ResettableSliderWidget wave = block.isThermal() ? makeWavelengthSlider(block) : null;
            ResettableSliderWidget pur  = block.isThermal() ? makePuritySlider(block) : null;
            MaterialDropdownWidget mat  = block.isThermal() ? makeMaterialDropdown(block, wave) : null;

            if (mat != null) dropdowns.add(mat);

            // Wire wavelength slider to update material dropdown on change
            if (wave != null && mat != null) {
                final MaterialDropdownWidget dropdown = mat;
                ResettableSliderWidget waveWithSync = makeWavelengthSliderWithSync(block, dropdown);
                this.body.addEntry(new RadianceSettingsScreen.FiveColumnEmissionEntry(
                    em, temp, dropdown, waveWithSync, pur, body));
            } else {
                this.body.addEntry(new RadianceSettingsScreen.FiveColumnEmissionEntry(
                    em, temp, mat, wave, pur, body));
            }
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        RadianceTheme.drawOutlinedText(context, this.textRenderer,
            Text.literal("Radiance > Lighting > Emission"), 20, 26, RadianceTheme.textSecondary);
        // Render dropdown overlays AFTER the body list, so they appear on top
        for (MaterialDropdownWidget dd : dropdowns) {
            dd.renderDropdownOverlay(context, mouseX, mouseY);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // Check dropdown overlays first (they render on top, should get clicks first)
        for (MaterialDropdownWidget dd : dropdowns) {
            if (dd.isOpen() && dd.isInDropdownBounds(mouseX, mouseY)) {
                return dd.mouseClicked(mouseX, mouseY, button);
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void removed() {
        super.removed();
        MaterialDropdownWidget.clearInstances();
    }

    private ResettableSliderWidget makeEmissionSlider(EmissiveBlock block) {
        String key = "options.video.emission." + block.getId();
        int initialValue = (int) (block.getValue() * 100);

        return new ResettableSliderWidget(
            0, 0, 100, 20,
            0, 500, initialValue, (int)(block.getDefaultValue() * 100),
            v -> getGenericValueText(
                Text.translatable(key),
                Text.literal(String.format("%.2f", v / 100.0f))),
            v -> block.setValue(v / 100.0f, true));
    }

    private ResettableSliderWidget makeTemperatureSlider(EmissiveBlock block) {
        String key = "options.video.emission.temperature." + block.getId();
        int currentTemp = Options.getBlockTemperature(block);
        int defaultTemp = block.getDefaultTemperatureCelsius();

        return new ResettableSliderWidget(
            0, 0, 100, 20,
            500, 4000, currentTemp, defaultTemp,
            v -> getGenericValueText(
                Text.translatable(key),
                Text.literal(v + "\u00B0C")),
            v -> Options.setBlockTemperature(block, v, true));
    }

    private ResettableSliderWidget makeWavelengthSlider(EmissiveBlock block) {
        String key = "options.video.emission.wavelength." + block.getId();
        int current = Options.getBlockWavelength(block);
        int defaultVal = block.getDefaultWavelengthNm();

        return new ResettableSliderWidget(
            0, 0, 100, 20,
            0, 780, current, defaultVal,
            v -> getGenericValueText(
                Text.translatable(key),
                Text.literal(v == 0 ? "Off" : v + "nm")),
            v -> Options.setBlockWavelength(block, v, true));
    }

    /** Wavelength slider that also updates the material dropdown on change. */
    private ResettableSliderWidget makeWavelengthSliderWithSync(EmissiveBlock block, MaterialDropdownWidget dropdown) {
        String key = "options.video.emission.wavelength." + block.getId();
        int current = Options.getBlockWavelength(block);
        int defaultVal = block.getDefaultWavelengthNm();

        return new ResettableSliderWidget(
            0, 0, 100, 20,
            0, 780, current, defaultVal,
            v -> getGenericValueText(
                Text.translatable(key),
                Text.literal(v == 0 ? "Off" : v + "nm")),
            v -> {
                Options.setBlockWavelength(block, v, true);
                dropdown.updateFromWavelength(v);
            });
    }

    private ResettableSliderWidget makePuritySlider(EmissiveBlock block) {
        String key = "options.video.emission.purity." + block.getId();
        int current = Options.getBlockPurity(block);
        int defaultVal = block.getDefaultPurityPercent();

        return new ResettableSliderWidget(
            0, 0, 100, 20,
            0, 100, current, defaultVal,
            v -> getGenericValueText(
                Text.translatable(key),
                Text.literal(v + "%")),
            v -> Options.setBlockPurity(block, v, true));
    }

    private MaterialDropdownWidget makeMaterialDropdown(EmissiveBlock block, ResettableSliderWidget waveSlider) {
        int currentWl = Options.getBlockWavelength(block);
        MaterialDropdownWidget dropdown = new MaterialDropdownWidget(0, 0, 100, 20, colorant -> {
            if (colorant != FlameColorant.CUSTOM && colorant != FlameColorant.NONE) {
                int wl = colorant.getWavelengthNm();
                Options.setBlockWavelength(block, wl, true);
                if (waveSlider != null) {
                    waveSlider.setCurrentValue(wl);
                }
            } else if (colorant == FlameColorant.NONE) {
                Options.setBlockWavelength(block, 0, true);
                if (waveSlider != null) {
                    waveSlider.setCurrentValue(0);
                }
            }
        });
        dropdown.updateFromWavelength(currentWl);
        return dropdown;
    }
}
