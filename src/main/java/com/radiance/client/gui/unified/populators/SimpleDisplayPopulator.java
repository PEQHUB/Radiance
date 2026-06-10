package com.radiance.client.gui.unified.populators;

import static net.minecraft.client.option.GameOptions.getGenericValueText;

import com.mojang.serialization.Codec;
import com.radiance.client.gui.unified.*;
import com.radiance.client.option.Options;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.SimpleOption;
import net.minecraft.text.Text;

public class SimpleDisplayPopulator implements ContentPopulator {
    @Override
    public void populate(ContentPanelWidget panel, RadianceUnifiedScreen screen) {
        var mc = MinecraftClient.getInstance();
        var gameOptions = mc.options;

        SettingsSection section = panel.addSection("options.video.category.display");

        // VSync + FPS Limit
        SimpleOption<Boolean> vsync = SimpleOption.ofBoolean(
            Options.VSYNC_KEY, Options.vsync,
            value -> Options.setVsync(value, true));

        SimpleOption<Integer> maxFps = new SimpleOption<>(
            "options.framerateLimit",
            SimpleOption.emptyTooltip(),
            (optionText, value) -> value == 260
                ? getGenericValueText(optionText, Text.translatable("options.framerateLimit.max"))
                : getGenericValueText(optionText, Text.translatable("options.framerate", value)),
            new SimpleOption.ValidatingIntSliderCallbacks(1, 26).withModifier(
                value -> value * 10, value -> value / 10),
            Codec.intRange(10, 260),
            Options.maxFps,
            value -> {
                mc.getInactivityFpsLimiter().setMaxFps(value);
                Options.setMaxFps(value, true);
            });

        section.addTwoWidgets(vsync.createWidget(gameOptions), maxFps.createWidget(gameOptions))
              .tooltip("VSync synchronizes presentation to display refresh. Can reduce tearing but may increase latency. FPS Limit caps rendered framerate.");

        // Fullscreen
        SimpleOption<Boolean> fullscreen = SimpleOption.ofBoolean("options.fullscreen",
            mc.getWindow().isFullscreen(),
            value -> mc.getWindow().toggleFullscreen());
        section.addToggle(fullscreen.createWidget(gameOptions));
        section.tooltip("Toggles fullscreen mode using Minecraft's current window.");
    }

    @Override
    public java.util.List<UnifiedSearchOverlay.SearchEntry> getSearchEntries(String nodeId, String category) {
        return java.util.List.of(
            new UnifiedSearchOverlay.SearchEntry("VSync", category, nodeId, false),
            new UnifiedSearchOverlay.SearchEntry("FPS Limit", category, nodeId, false),
            new UnifiedSearchOverlay.SearchEntry("Fullscreen", category, nodeId, false)
        );
    }
}
