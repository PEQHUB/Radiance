package com.radiance.client.gui;

import net.minecraft.client.MinecraftClient;
import com.radiance.mixins.vulkan_render_integration.WindowAccessorMixin;

public class GuiScaleHelper {
    public static double savedScaleFactor = -1;
    public static int savedScaledWidth = -1;
    public static int savedScaledHeight = -1;

    public static void restoreOriginalScale() {
        if (savedScaleFactor < 0) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null) return;
        var window = mc.getWindow();
        var accessor = (WindowAccessorMixin) (Object) window;
        accessor.radiance$setScaleFactor(savedScaleFactor);
        accessor.radiance$setScaledWidth(savedScaledWidth);
        accessor.radiance$setScaledHeight(savedScaledHeight);
        savedScaleFactor = -1;
    }
}
