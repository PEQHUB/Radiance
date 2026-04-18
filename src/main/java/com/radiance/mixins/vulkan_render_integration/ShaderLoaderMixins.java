package com.radiance.mixins.vulkan_render_integration;

import com.radiance.v2.bridge.BootTrace;
import com.radiance.v2.bridge.V2BootGate;
import net.minecraft.client.gl.ShaderLoader;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.profiler.Profiler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * In Minecraft 1.21.4 the {@link ShaderLoader} reloader compiles GL shaders
 * lazily during its {@code apply(Definitions, ResourceManager, Profiler)}
 * pass — which runs as a queued task on the render thread, AFTER V2 has
 * committed {@code GLFW_NO_API}. The downstream
 * {@code GlStateManager.glCreateShader} dispatches through a null GL function
 * pointer and the JVM dies with EXCEPTION_ACCESS_VIOLATION.
 *
 * <p>The pre-existing {@code GameRendererMixins.cancelPreloadShader} only
 * handles the static {@code preload} entry point used at boot; the lazy
 * compile path that fires from the resource-reload chain has no analogue.
 *
 * <p>This mixin cancels the {@code apply} method outright when V2 is the
 * active renderer. V2 owns its own RT/raster shader compilation pipeline
 * (Vulkan SPIR-V), so the vanilla GL shader cache is dead weight in V2 mode
 * and can be safely skipped without breaking anything that V2 needs.
 */
@Mixin(ShaderLoader.class)
public class ShaderLoaderMixins {

    @Inject(
            method = "apply(Ljava/lang/Object;Lnet/minecraft/resource/ResourceManager;Lnet/minecraft/util/profiler/Profiler;)V",
            at = @At("HEAD"),
            cancellable = true)
    private void radiance$cancelApplyInV2(Object definitions, ResourceManager resourceManager,
                                          Profiler profiler, CallbackInfo ci) {
        if (V2BootGate.isActive()) {
            BootTrace.event("V2_SHADERLOADER_APPLY_SKIPPED");
            ci.cancel();
        }
    }
}
