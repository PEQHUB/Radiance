package com.radiance.mixins.vulkan_render_integration;

import com.radiance.client.proxy.vulkan.BufferProxy;
import com.radiance.client.proxy.vulkan.RendererProxy;
import com.radiance.client.proxy.vulkan.UIThreadProxy;
import com.radiance.client.ui.UIThread;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.BuiltBuffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BufferRenderer.class)
public class BufferRendererMixins {

    @Inject(method = "drawWithGlobalProgram(Lnet/minecraft/client/render/BuiltBuffer;)V",
        at = @At("HEAD"),
        cancellable = true)
    private static void rewriteDrawWithGlobalProgram(BuiltBuffer buffer, CallbackInfo ci) {
        // Suppress render-thread overlay draws only after UI thread has presented its first frame.
        // Prevents doubled UI (render thread + UI thread both drawing).
        if (UIThreadProxy.isUIThreadRenderingOverlay() && !UIThread.isUIThread()) {
            buffer.close();
            ci.cancel();
            return;
        }

        BufferProxy.VertexIndexBufferHandle handle = BufferProxy.createAndUploadVertexIndexBuffer(
            buffer);

        if (UIThread.isUIThread()) {
            BufferProxy.updateOverlayDrawUniform();
            UIThreadProxy.drawUIOverlay(handle.vertexId, handle.indexId, UIThreadProxy.uiPipelineType,
                buffer.getDrawParameters().indexCount(),
                com.radiance.client.constant.Constants.IndexTypes.getValue(buffer.getDrawParameters().indexType()));
        } else {
            BufferProxy.updateOverlayDrawUniform();
            RendererProxy.drawOverlay(handle,
                buffer.getDrawParameters().indexCount(),
                buffer.getDrawParameters().indexType());
        }

        buffer.close();

        ci.cancel();
    }
}
