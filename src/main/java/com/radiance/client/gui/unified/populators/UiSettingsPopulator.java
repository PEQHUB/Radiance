package com.radiance.client.gui.unified.populators;

import static net.minecraft.client.option.GameOptions.getGenericValueText;

import com.radiance.client.gui.ResettableSliderWidget;
import com.radiance.client.gui.unified.*;
import com.radiance.client.option.Options;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.option.SimpleOption;
import net.minecraft.text.Text;

public class UiSettingsPopulator implements ContentPopulator {
    @Override
    public void populate(ContentPanelWidget panel, RadianceUnifiedScreen screen) {
        SettingsSection section = panel.addSection(Text.literal("UI Settings"));

        // Menu Transparency slider
        ResettableSliderWidget alphaSlider = new ResettableSliderWidget(
            0, 0, 150, 20,
            0, 100, Options.uiGlobalAlphaPercent, 55,
            v -> getGenericValueText(
                Text.translatable("radiance.settings.menu_transparency"),
                Text.literal(v + "%")),
            v -> Options.setUiGlobalAlphaPercent(v, false));
        alphaSlider.setOnRelease(() -> Options.overwriteConfig());
        alphaSlider.settingKey = "uiGlobalAlphaPercent";
        section.addSlider(alphaSlider);

        // Welcome Message toggle
        section.addButton(ButtonWidget.builder(
            Text.translatable(Options.showWelcomeMessage
                ? "radiance.settings.welcome_message.on"
                : "radiance.settings.welcome_message.off"),
            btn -> {
                Options.showWelcomeMessage = !Options.showWelcomeMessage;
                Options.overwriteConfig();
                btn.setMessage(Text.translatable(Options.showWelcomeMessage
                    ? "radiance.settings.welcome_message.on"
                    : "radiance.settings.welcome_message.off"));
            })
            .width(150).build());

        // Reset to Defaults button
        section.addButton(ButtonWidget.builder(
            Text.translatable("radiance.settings.reset_defaults"),
            btn -> {
                Options.resetAllToDefaults();
                screen.refreshContent();
            })
            .width(150).build());
    }
}
