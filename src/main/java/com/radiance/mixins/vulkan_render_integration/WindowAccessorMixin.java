package com.radiance.mixins.vulkan_render_integration;

import net.minecraft.client.util.Window;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Window.class)
public interface WindowAccessorMixin {
    @Accessor("scaleFactor") double radiance$getScaleFactor();
    @Accessor("scaleFactor") void radiance$setScaleFactor(double factor);
    @Accessor("scaledWidth") int radiance$getScaledWidth();
    @Accessor("scaledWidth") void radiance$setScaledWidth(int w);
    @Accessor("scaledHeight") int radiance$getScaledHeight();
    @Accessor("scaledHeight") void radiance$setScaledHeight(int h);
}
