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
        var gameOptions = MinecraftClient.getInstance().options;

        SettingsSection section = panel.addSection(Text.literal("UI Settings"));

        // Transparency
        section.addSlider(new ResettableSliderWidget(0, 0, 150, 20,
            0, 100, Options.uiGlobalAlphaPercent, 55,
            v -> getGenericValueText(Text.literal("Menu Opacity"), Text.literal(v + "%")),
            v -> Options.setUiGlobalAlphaPercent(v, true)));
        section.tooltip("Controls how transparent the settings menu background is.");

        // Adaptive Dimming
        SimpleOption<Boolean> adaptiveDim = SimpleOption.ofBoolean(
            "options.video.ui_adaptive_dimming", Options.uiAdaptiveDimming,
            value -> Options.setUiAdaptiveDimming(value, true));
        section.addToggle(adaptiveDim.createWidget(gameOptions));
        section.tooltip("Auto-adjusts menu opacity based on scene brightness behind the UI.");

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

    @Override
    public java.util.List<UnifiedSearchOverlay.SearchEntry> getSearchEntries(String nodeId, String category) {
        return java.util.List.of(
            new UnifiedSearchOverlay.SearchEntry("Menu Opacity", category, nodeId, false),
            new UnifiedSearchOverlay.SearchEntry("Adaptive Dimming", category, nodeId, false),
            new UnifiedSearchOverlay.SearchEntry("Welcome Message", category, nodeId, false)
        );
    }
}
