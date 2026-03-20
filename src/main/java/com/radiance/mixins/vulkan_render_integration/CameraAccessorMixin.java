package com.radiance.mixins.vulkan_render_integration;

import net.minecraft.client.render.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Camera.class)
public interface CameraAccessorMixin {
    @Accessor("cameraY")
    float radiance$getCameraY();

    @Accessor("lastCameraY")
    float radiance$getLastCameraY();
}
