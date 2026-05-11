package com.radiance.mixins.vulkan_render_integration;

import com.radiance.client.proxy.vulkan.BufferProxy;
import com.radiance.client.proxy.vulkan.RendererProxy;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.BuiltBuffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BufferRenderer.class)
public class BufferRendererMixins {

    @Inject(method = "drawWithGlobalProgram(Lnet/minecraft/client/render/BuiltBuffer;)V",
        at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/RenderSystem;assertOnRenderThread()V", shift = At.Shift.AFTER, remap = false),
        cancellable = true)
    private static void rewriteDrawWithGlobalProgram(BuiltBuffer buffer, CallbackInfo ci) {
        BufferProxy.VertexIndexBufferHandle handle = BufferProxy.createAndUploadVertexIndexBuffer(
            buffer);

        BufferProxy.updateOverlayDrawUniform();

        RendererProxy.drawOverlay(handle,
            buffer.getDrawParameters()
                .indexCount(),
            buffer.getDrawParameters()
                .indexType());

        buffer.close();

        ci.cancel();
    }
}
