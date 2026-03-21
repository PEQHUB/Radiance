package com.radiance.mixins.vulkan_render_integration;

import com.mojang.blaze3d.platform.GlStateManager;
import com.radiance.client.constant.VulkanConstants;
import com.radiance.client.proxy.vulkan.DrawCommandProxy;
import com.radiance.client.proxy.vulkan.PipelineStateProxy;
import com.radiance.client.proxy.vulkan.UIThreadProxy;
import com.radiance.client.ui.UIThread;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GlStateManager.class)
public class GlStateManagerMixins {

    @Inject(method = "_activeTexture(I)V",
        at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/RenderSystem;assertOnRenderThread()V", shift = At.Shift.AFTER),
        cancellable = true,
        remap = false)
    private static void redirectActiveTexture(int texture, CallbackInfo ci) {
        ci.cancel();
    }

    // region <PipelineStateProxy.ViewportState>
    @Inject(method = "_disableScissorTest()V",
        at = @At(value = "INVOKE",
            target = "Lcom/mojang/blaze3d/systems/RenderSystem;assertOnRenderThreadOrInit()V",
            shift = At.Shift.AFTER),
        cancellable = true,
        remap = false)
    private static void redirectDisableScissorTest(CallbackInfo ci) {
        if (UIThread.isUIThread()) {
            UIThreadProxy.setUIScissorEnabled(false);
        } else {
            PipelineStateProxy.ViewportState.setScissorEnabled(false);
        }
        ci.cancel();
    }

    @Inject(method = "_enableScissorTest()V",
        at = @At(value = "INVOKE",
            target = "Lcom/mojang/blaze3d/systems/RenderSystem;assertOnRenderThreadOrInit()V",
            shift = At.Shift.AFTER),
        cancellable = true,
        remap = false)
    private static void redirectEnableScissorTest(CallbackInfo ci) {
        if (UIThread.isUIThread()) {
            UIThreadProxy.setUIScissorEnabled(true);
        } else {
            PipelineStateProxy.ViewportState.setScissorEnabled(true);
        }
        ci.cancel();
    }

    @Inject(method = "_scissorBox(IIII)V",
        at = @At(value = "INVOKE",
            target = "Lcom/mojang/blaze3d/systems/RenderSystem;assertOnRenderThreadOrInit()V",
            shift = At.Shift.AFTER),
        cancellable = true,
        remap = false)
    private static void redirectScissorBox(int x, int y, int width, int height, CallbackInfo ci) {
        if (UIThread.isUIThread()) {
            UIThreadProxy.setUIScissor(x, y, width, height);
        } else {
            PipelineStateProxy.ViewportState.setScissor(x, y, width, height);
        }
        ci.cancel();
    }

    @Inject(method = "_viewport(IIII)V",
        at = @At(value = "INVOKE",
            target = "Lcom/mojang/blaze3d/systems/RenderSystem;assertOnRenderThreadOrInit()V",
            shift = At.Shift.AFTER),
        cancellable = true,
        remap = false)
    private static void redirectViewport(int x, int y, int width, int height, CallbackInfo ci) {
        if (UIThread.isUIThread()) {
            UIThreadProxy.setUIViewport(x, y, width, height);
        } else {
            PipelineStateProxy.ViewportState.setViewport(x, y, width, height);
        }
        ci.cancel();
    }
    // endregion

    // region <PipelineStateProxy.ColorBlendState>
    @Inject(method = "_disableBlend()V",
        at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/RenderSystem;assertOnRenderThread()V", shift = At.Shift.AFTER),
        cancellable = true,
        remap = false)
    private static void redirectDisableBlend(CallbackInfo ci) {
        if (UIThread.isUIThread()) {
            UIThreadProxy.setUIBlendEnable(false);
        } else {
            PipelineStateProxy.ColorBlendState.setBlendEnable(false);
        }
        ci.cancel();
    }

    @Inject(method = "_enableBlend()V",
        at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/RenderSystem;assertOnRenderThread()V", shift = At.Shift.AFTER),
        cancellable = true,
        remap = false)
    private static void redirectEnableBlend(CallbackInfo ci) {
        if (UIThread.isUIThread()) {
            UIThreadProxy.setUIBlendEnable(true);
        } else {
            PipelineStateProxy.ColorBlendState.setBlendEnable(true);
        }
        ci.cancel();
    }

    @Inject(method = "_blendFunc(II)V",
        at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/RenderSystem;assertOnRenderThread()V", shift = At.Shift.AFTER),
        cancellable = true,
        remap = false)
    private static void redirectBlendFunc(int srcFactor, int dstFactor, CallbackInfo ci) {
        if (UIThread.isUIThread()) {
            UIThreadProxy.setUIBlendFuncSeparate(
                VulkanConstants.VkBlendFactor.ofGL(srcFactor),
                VulkanConstants.VkBlendFactor.ofGL(srcFactor),
                VulkanConstants.VkBlendFactor.ofGL(dstFactor),
                VulkanConstants.VkBlendFactor.ofGL(dstFactor));
        } else {
            PipelineStateProxy.ColorBlendState.glSetBlendFuncCombined(srcFactor, dstFactor);
        }
        ci.cancel();
    }

    @Inject(method = "_blendFuncSeparate(IIII)V",
        at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/RenderSystem;assertOnRenderThread()V", shift = At.Shift.AFTER),
        cancellable = true,
        remap = false)
    private static void redirectBlendFuncSeparate(int srcFactorRGB,
        int dstFactorRGB,
        int srcFactorAlpha,
        int dstFactorAlpha,
        CallbackInfo ci) {
        if (UIThread.isUIThread()) {
            UIThreadProxy.glSetBlendFuncSeparate(srcFactorRGB, srcFactorAlpha,
                dstFactorRGB, dstFactorAlpha);
        } else {
            PipelineStateProxy.ColorBlendState.glSetBlendFuncSeparate(srcFactorRGB, srcFactorAlpha,
                dstFactorRGB, dstFactorAlpha);
        }
        ci.cancel();
    }

    @Inject(method = "_blendEquation(I)V",
        at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/RenderSystem;assertOnRenderThread()V", shift = At.Shift.AFTER),
        cancellable = true,
        remap = false)
    private static void redirectBlendEquation(int mode, CallbackInfo ci) {
        if (UIThread.isUIThread()) {
            UIThreadProxy.setUIBlendOpSeparate(
                VulkanConstants.VkBlendOp.ofGL(mode),
                VulkanConstants.VkBlendOp.ofGL(mode));
        } else {
            PipelineStateProxy.ColorBlendState.glSetBlendOpCombined(mode);
        }
        ci.cancel();
    }

    @Inject(method = "_colorMask(ZZZZ)V",
        at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/RenderSystem;assertOnRenderThread()V", shift = At.Shift.AFTER),
        cancellable = true,
        remap = false)
    private static void redirectBlendEquation(boolean red, boolean green, boolean blue,
        boolean alpha, CallbackInfo ci) {
        if (UIThread.isUIThread()) {
            UIThreadProxy.glSetColorWriteMask(red, green, blue, alpha);
        } else {
            PipelineStateProxy.ColorBlendState.glSetColorWriteMask(red, green, blue, alpha);
        }
        ci.cancel();
    }

    @Inject(method = "_enableColorLogicOp()V",
        at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/RenderSystem;assertOnRenderThread()V", shift = At.Shift.AFTER),
        cancellable = true,
        remap = false)
    private static void redirectEnableColorLogicOp(CallbackInfo ci) {
        if (UIThread.isUIThread()) {
            UIThreadProxy.setUIColorLogicOpEnable(true);
        } else {
            PipelineStateProxy.ColorBlendState.setColorLogicOpEnable(true);
        }
        ci.cancel();
    }

    @Inject(method = "_disableColorLogicOp()V",
        at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/RenderSystem;assertOnRenderThread()V", shift = At.Shift.AFTER),
        cancellable = true,
        remap = false)
    private static void redirectDisableColorLogicOp(CallbackInfo ci) {
        if (UIThread.isUIThread()) {
            UIThreadProxy.setUIColorLogicOpEnable(false);
        } else {
            PipelineStateProxy.ColorBlendState.setColorLogicOpEnable(false);
        }
        ci.cancel();
    }

    @Inject(method = "_logicOp(I)V",
        at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/RenderSystem;assertOnRenderThread()V", shift = At.Shift.AFTER),
        cancellable = true,
        remap = false)
    private static void redirectLogicOp(int op, CallbackInfo ci) {
        if (UIThread.isUIThread()) {
            UIThreadProxy.glSetColorLogicOp(op);
        } else {
            PipelineStateProxy.ColorBlendState.glSetColorLogicOp(op);
        }
        ci.cancel();
    }
    // endregion

    // region <PipelineStateProxy.DepthStencilState>
    @Inject(method = "_disableDepthTest()V",
        at = @At(value = "INVOKE",
            target = "Lcom/mojang/blaze3d/systems/RenderSystem;assertOnRenderThreadOrInit()V",
            shift = At.Shift.AFTER),
        cancellable = true,
        remap = false)
    private static void redirectDisableDepthTest(CallbackInfo ci) {
        if (UIThread.isUIThread()) {
            UIThreadProxy.setUIDepthTestEnable(false);
        } else {
            PipelineStateProxy.DepthStencilState.setDepthTestEnable(false);
        }
        ci.cancel();
    }

    @Inject(method = "_enableDepthTest()V",
        at = @At(value = "INVOKE",
            target = "Lcom/mojang/blaze3d/systems/RenderSystem;assertOnRenderThreadOrInit()V",
            shift = At.Shift.AFTER),
        cancellable = true,
        remap = false)
    private static void redirectEnableDepthTest(CallbackInfo ci) {
        if (UIThread.isUIThread()) {
            UIThreadProxy.setUIDepthTestEnable(true);
        } else {
            PipelineStateProxy.DepthStencilState.setDepthTestEnable(true);
        }
        ci.cancel();
    }

    @Inject(method = "_depthFunc(I)V",
        at = @At(value = "INVOKE",
            target = "Lcom/mojang/blaze3d/systems/RenderSystem;assertOnRenderThreadOrInit()V",
            shift = At.Shift.AFTER),
        cancellable = true,
        remap = false)
    private static void redirectDepthFunc(int func, CallbackInfo ci) {
        if (UIThread.isUIThread()) {
            UIThreadProxy.glSetDepthCompareOp(func);
        } else {
            PipelineStateProxy.DepthStencilState.glSetDepthCompareOp(func);
        }
        ci.cancel();
    }

    @Inject(method = "_depthMask(Z)V",
        at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/RenderSystem;assertOnRenderThread()V", shift = At.Shift.AFTER),
        cancellable = true,
        remap = false)
    private static void redirectDepthMask(boolean mask, CallbackInfo ci) {
        if (UIThread.isUIThread()) {
            UIThreadProxy.setUIDepthWriteEnable(mask);
        } else {
            PipelineStateProxy.DepthStencilState.setDepthWriteEnable(mask);
        }
        ci.cancel();
    }

    @Inject(method = "_stencilFunc(III)V",
        at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/RenderSystem;assertOnRenderThread()V", shift = At.Shift.AFTER),
        cancellable = true,
        remap = false)
    private static void redirectStencilFunc(int func, int ref, int mask, CallbackInfo ci) {
        if (UIThread.isUIThread()) {
            UIThreadProxy.glSetStencilFunc(func, ref, mask);
        } else {
            PipelineStateProxy.DepthStencilState.glSetStencilFunc(func, ref, mask);
        }
        ci.cancel();
    }

    @Inject(method = "_stencilMask(I)V",
        at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/RenderSystem;assertOnRenderThread()V", shift = At.Shift.AFTER),
        cancellable = true,
        remap = false)
    private static void redirectStencilMask(int mask, CallbackInfo ci) {
        if (UIThread.isUIThread()) {
            UIThreadProxy.setUIStencilFrontWriteMask(mask);
            UIThreadProxy.setUIStencilBackWriteMask(mask);
        } else {
            PipelineStateProxy.DepthStencilState.vkSetStencilWriteMask(mask);
        }
        ci.cancel();
    }

    @Inject(method = "_stencilOp(III)V",
        at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/RenderSystem;assertOnRenderThread()V", shift = At.Shift.AFTER),
        cancellable = true,
        remap = false)
    private static void redirectStencilMask(int sfail, int dpfail, int dppass, CallbackInfo ci) {
        if (UIThread.isUIThread()) {
            int vkSfail = VulkanConstants.VkStencilOp.ofGL(sfail);
            int vkDpfail = VulkanConstants.VkStencilOp.ofGL(dpfail);
            int vkDppass = VulkanConstants.VkStencilOp.ofGL(dppass);
            UIThreadProxy.setUIStencilFrontOp(vkSfail, vkDpfail, vkDppass);
            UIThreadProxy.setUIStencilBackOp(vkSfail, vkDpfail, vkDppass);
        } else {
            PipelineStateProxy.DepthStencilState.glSetStencilOp(sfail, dpfail, dppass);
        }
        ci.cancel();
    }
    // endregion

    // region <PipelineStateProxy.RasterizationState>
    @Inject(method = "_enableCull()V",
        at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/RenderSystem;assertOnRenderThread()V", shift = At.Shift.AFTER),
        cancellable = true,
        remap = false)
    private static void redirectEnableCull(CallbackInfo ci) {
        if (UIThread.isUIThread()) {
            UIThreadProxy.glSetCullMode(GL11.GL_BACK);
        } else {
            PipelineStateProxy.RasterizationState.glSetCullMode(GL11.GL_BACK);
        }
        ci.cancel();
    }

    @Inject(method = "_disableCull()V",
        at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/RenderSystem;assertOnRenderThread()V", shift = At.Shift.AFTER),
        cancellable = true,
        remap = false)
    private static void redirectDisableCull(CallbackInfo ci) {
        if (UIThread.isUIThread()) {
            UIThreadProxy.setUICullMode(VulkanConstants.VkCullMode.VK_CULL_MODE_NONE.getValue());
        } else {
            PipelineStateProxy.RasterizationState.vkSetCullMode(
                VulkanConstants.VkCullMode.VK_CULL_MODE_NONE.getValue());
        }
        ci.cancel();
    }

    @Inject(method = "_polygonMode(II)V",
        at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/RenderSystem;assertOnRenderThread()V", shift = At.Shift.AFTER),
        cancellable = true,
        remap = false)
    private static void redirectPolygonMode(int face, int mode, CallbackInfo ci) {
        /*
          @Warning no vulkan equivalent implementation
         */
        if (UIThread.isUIThread()) {
            UIThreadProxy.glSetPolygonMode(mode);
        } else {
            PipelineStateProxy.RasterizationState.glSetPolygonMode(mode);
        }
        ci.cancel();
    }

    @Inject(method = "_enablePolygonOffset()V",
        at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/RenderSystem;assertOnRenderThread()V", shift = At.Shift.AFTER),
        cancellable = true,
        remap = false)
    private static void redirectEnablePolygonOffset(CallbackInfo ci) {
        if (UIThread.isUIThread()) {
            UIThreadProxy.setUIDepthBiasEnable(0, true);
        } else {
            PipelineStateProxy.RasterizationState.glSetPolygonOffsetEnable(GL11.GL_FILL, true);
        }
        ci.cancel();
    }

    @Inject(method = "_disablePolygonOffset()V",
        at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/RenderSystem;assertOnRenderThread()V", shift = At.Shift.AFTER),
        cancellable = true,
        remap = false)
    private static void redirectDisablePolygonOffset(CallbackInfo ci) {
        if (UIThread.isUIThread()) {
            UIThreadProxy.setUIDepthBiasEnable(0, false);
        } else {
            PipelineStateProxy.RasterizationState.glSetPolygonOffsetEnable(GL11.GL_FILL, false);
        }
        ci.cancel();
    }

    @Inject(method = "_polygonOffset(FF)V",
        at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/RenderSystem;assertOnRenderThread()V", shift = At.Shift.AFTER),
        cancellable = true,
        remap = false)
    private static void redirectPolygonOffset(float factor, float units, CallbackInfo ci) {
        if (UIThread.isUIThread()) {
            UIThreadProxy.setUIDepthBias(factor, units);
        } else {
            PipelineStateProxy.RasterizationState.glSetPolygonOffset(factor, units);
        }
        ci.cancel();
    }
    // endregion

    // region <PipelineStateProxy.ClearState>
    @Inject(method = "_clearColor(FFFF)V",
        at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/RenderSystem;assertOnRenderThreadOrInit()V", shift = At.Shift.AFTER),
        cancellable = true,
        remap = false)
    private static void redirectClearColor(float red, float green, float blue, float alpha,
        CallbackInfo ci) {
        if (UIThread.isUIThread()) {
            UIThreadProxy.setUIClearColor(red, green, blue, alpha);
        } else {
            PipelineStateProxy.ClearState.setClearColor(red, green, blue, alpha);
        }
        ci.cancel();
    }

    @Inject(method = "_clearDepth(D)V",
        at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/RenderSystem;assertOnRenderThreadOrInit()V", shift = At.Shift.AFTER),
        cancellable = true,
        remap = false)
    private static void redirectClearDepth(double depth, CallbackInfo ci) {
        if (UIThread.isUIThread()) {
            // no-op for UI thread (depth clear handled by render pass)
        } else {
            PipelineStateProxy.ClearState.setClearDepth(depth);
        }
        ci.cancel();
    }

    @Inject(method = "_clearStencil(I)V",
        at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/RenderSystem;assertOnRenderThread()V", shift = At.Shift.AFTER),
        cancellable = true,
        remap = false)
    private static void redirectClearStencil(int stencil, CallbackInfo ci) {
        if (UIThread.isUIThread()) {
            // no-op for UI thread (stencil clear handled by render pass)
        } else {
            PipelineStateProxy.ClearState.setClearStencil(stencil);
        }
        ci.cancel();
    }
    // endregion

    // region <DrawCommandProxy.Overlay>
    @Inject(method = "_clear(I)V",
        at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/RenderSystem;assertOnRenderThreadOrInit()V", shift = At.Shift.AFTER),
        cancellable = true,
        remap = false)
    private static void redirectClear(int mask, CallbackInfo ci) {
        if (UIThread.isUIThread()) {
            UIThreadProxy.glClear(mask);
        } else {
            DrawCommandProxy.Overlay.glClear(mask);
        }
        ci.cancel();
    }
    // endregion

    @Redirect(method = "_getString(I)Ljava/lang/String;", at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glGetString(I)Ljava/lang/String;", remap = false))
    private static String redirectGetString(int name) {
        return "Vulkan 1.4";
    }
}
