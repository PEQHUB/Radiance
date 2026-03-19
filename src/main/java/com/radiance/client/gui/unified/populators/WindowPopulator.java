package com.radiance.client.gui.unified.populators;

import static net.minecraft.client.option.GameOptions.getGenericValueText;

import com.mojang.serialization.Codec;
import com.radiance.client.gui.ResettableSliderWidget;
import com.radiance.client.gui.unified.*;
import com.radiance.client.option.Options;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.option.SimpleOption;
import net.minecraft.text.Text;

public class WindowPopulator implements ContentPopulator {
    @Override
    public void populate(ContentPanelWidget panel, RadianceUnifiedScreen screen) {
        var mc = MinecraftClient.getInstance();
        var gameOptions = mc.options;

        SettingsSection section = panel.addSection(Options.CATEGORY_WINDOW);

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

        SimpleOption<Boolean> enableVsync = SimpleOption.ofBoolean("options.vsync", Options.vsync,
            value -> Options.setVsync(value, true));
        section.addTwoWidgets(maxFps.createWidget(gameOptions), enableVsync.createWidget(gameOptions));

        // Window dimensions — width and height sliders
        var window = mc.getWindow();
        int currentW = window.getWidth();
        int currentH = window.getHeight();

        ResettableSliderWidget widthSlider = new ResettableSliderWidget(0, 0, 150, 20,
            640, 3840, currentW, 1920,
            v -> getGenericValueText(Text.literal("Width"), Text.literal(v + "px")),
            v -> applyWindowSize(v, -1));

        ResettableSliderWidget heightSlider = new ResettableSliderWidget(0, 0, 150, 20,
            360, 2160, currentH, 1080,
            v -> getGenericValueText(Text.literal("Height"), Text.literal(v + "px")),
            v -> applyWindowSize(-1, v));

        section.addTwoSliders(widthSlider, heightSlider);

        // Fullscreen toggle + Center Window button
        SimpleOption<Boolean> fullscreen = SimpleOption.ofBoolean("options.fullscreen",
            mc.getWindow().isFullscreen(),
            value -> mc.getWindow().toggleFullscreen());
        ButtonWidget centerBtn = ButtonWidget.builder(Text.literal("Center Window"), btn -> {
            centerWindow();
        }).width(150).build();
        section.addTwoWidgets(fullscreen.createWidget(gameOptions), centerBtn);
    }

    private static void centerWindow() {
        var mc = MinecraftClient.getInstance();
        var window = mc.getWindow();
        if (window.isFullscreen()) return;

        long handle = window.getHandle();
        long monitor = org.lwjgl.glfw.GLFW.glfwGetPrimaryMonitor();
        if (monitor == 0) return;
        var vidMode = org.lwjgl.glfw.GLFW.glfwGetVideoMode(monitor);
        if (vidMode == null) return;

        int x = (vidMode.width() - window.getWidth()) / 2;
        int y = (vidMode.height() - window.getHeight()) / 2;
        org.lwjgl.glfw.GLFW.glfwSetWindowPos(handle, x, y);
        Options.windowPosX = x;
        Options.windowPosY = y;
        Options.overwriteConfig();
    }

    private static void applyWindowSize(int width, int height) {
        var mc = MinecraftClient.getInstance();
        var window = mc.getWindow();
        if (window.isFullscreen()) return;

        int w = width > 0 ? width : window.getWidth();
        int h = height > 0 ? height : window.getHeight();

        long handle = window.getHandle();
        org.lwjgl.glfw.GLFW.glfwSetWindowSize(handle, w, h);
        Options.windowWidth = w;
        Options.windowHeight = h;
        Options.overwriteConfig();
    }

    @Override
    public java.util.List<UnifiedSearchOverlay.SearchEntry> getSearchEntries(String nodeId, String category) {
        return java.util.List.of(
            new UnifiedSearchOverlay.SearchEntry("FPS Limit", category, nodeId, false),
            new UnifiedSearchOverlay.SearchEntry("VSync", category, nodeId, false),
            new UnifiedSearchOverlay.SearchEntry("Window Size", category, nodeId, false),
            new UnifiedSearchOverlay.SearchEntry("Fullscreen", category, nodeId, false)
        );
    }
}
