package com.radiance.mixins.vulkan_render_integration;

import com.mojang.blaze3d.systems.RenderSystem;
import com.radiance.client.ui.UIThread;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Bypasses RenderSystem thread assertions for the UI thread.
 * The UI thread renders overlay via UIRenderContext (secondary queue),
 * which is fully independent of the render thread's command buffers.
 */
@Mixin(RenderSystem.class)
public class RenderSystemAssertMixin {

    @Inject(method = "assertOnRenderThread()V", at = @At("HEAD"), cancellable = true, remap = false)
    private static void allowUIThread(CallbackInfo ci) {
        if (UIThread.isUIThread()) ci.cancel();
    }

    @Inject(method = "assertOnRenderThreadOrInit()V", at = @At("HEAD"), cancellable = true, remap = false)
    private static void allowUIThreadOrInit(CallbackInfo ci) {
        if (UIThread.isUIThread()) ci.cancel();
    }
}
