package com.radiance.client.gui;

import static net.minecraft.client.option.GameOptions.getGenericValueText;

import com.radiance.client.option.Options;
import com.radiance.client.util.CategoryVideoOptionEntry;
import com.radiance.client.util.EmissiveBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.option.GameOptionsScreen;
import net.minecraft.text.Text;

public class EmissiveBlockSettingsScreen extends GameOptionsScreen {

    private final Screen parentScreen;

    public EmissiveBlockSettingsScreen(Screen parent) {
        super(parent, MinecraftClient.getInstance().options, Text.translatable("radiance.settings.emission.title"));
        this.parentScreen = parent;
    }

    @Override
    protected void addOptions() {
        this.body.addEntry(new CategoryVideoOptionEntry(Text.translatable(Options.CATEGORY_EMISSION), body));

        for (EmissiveBlock block : EmissiveBlock.values()) {
            addEmissionSlider(block);
        }
    }

    private void addEmissionSlider(EmissiveBlock block) {
        String key = "options.video.emission." + block.getId();
        int initialValue = (int) (block.getValue() * 100);
        
        ResettableSliderWidget slider = new ResettableSliderWidget(
            0, 0, 150, 20,
            0, 500, initialValue, (int)(block.getDefaultValue() * 100),
            v -> getGenericValueText(
                Text.translatable(key),
                Text.literal(String.format("%.2f", v / 100.0f))),
            v -> block.setValue(v / 100.0f, true));
            
        this.body.addEntry(new RadianceSettingsScreen.SliderEntry(slider, body));
    }
}
