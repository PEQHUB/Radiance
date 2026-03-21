package com.radiance.client.proxy.vulkan;

import com.radiance.client.constant.Constants;
import com.radiance.client.constant.VulkanConstants;
import net.minecraft.client.render.VertexFormat;
import org.lwjgl.opengl.GL11;

/**
 * JNI bridge for decoupled UI rendering on a dedicated UI thread.
 * All methods target UIRenderContext (secondary queue) instead of the
 * render thread's UIModuleContext. Thread-safe: designed to be called
 * exclusively from the UI thread.
 */
public class UIThreadProxy {

    public static volatile int uiPipelineType = -1;

    // Thread-local matrix state — prevents race with render thread's RenderSystem globals
    public static final org.joml.Matrix4f uiProjectionMatrix = new org.joml.Matrix4f();
    public static final org.joml.Matrix4fStack uiModelViewStack = new org.joml.Matrix4fStack(16);

    // ── Lifecycle ──

    /** Create UIRenderContext on-demand. Returns true if ready. */
    public static native boolean createUIRenderContext();

    /** Destroy UIRenderContext. */
    public static native void destroyUIRenderContext();

    /** Begin a new UI frame (wait fence, reset cmd, begin render pass). */
    public static native void beginUIFrame();

    /** End the current render pass and finalize command buffer. */
    public static native void endUIFrame();

    /** Submit to GPU, wait, present via DComp, pace to display rate. */
    public static native void submitUIFrame();

    /** Whether decoupled UI is currently active (UIRenderContext exists). */
    public static native boolean isDecoupledUIActive();

    /** Whether UI thread has presented at least one frame (safe to suppress render-thread overlay). */
    public static native boolean isUIThreadRenderingOverlay();

    /** Whether C++ has requested UIThread to pause (for swapchain recreate). */
    public static native boolean isPauseRequested();

    // ── Draw commands ──

    public static native void drawUIOverlay(int vertexId, int indexId, int pipelineType,
        int indexCount, int indexType);

    public static void drawUIOverlay(BufferProxy.VertexIndexBufferHandle handle, int pipelineType,
        int indexCount, VertexFormat.IndexType indexType) {
        drawUIOverlay(handle.vertexId, handle.indexId, pipelineType, indexCount,
            Constants.IndexTypes.getValue(indexType));
    }

    // ── Clear commands ──

    public static native void clearUIColor();

    public static native void clearUIDepthStencil(int aspectMask);

    public static void glClear(int mask) {
        if ((mask & GL11.GL_COLOR_BUFFER_BIT) > 0) {
            clearUIColor();
        }
        int vkMask = 0;
        if ((mask & GL11.GL_DEPTH_BUFFER_BIT) > 0) {
            vkMask |= VulkanConstants.VkImageAspectFlagBits.ofGL(GL11.GL_DEPTH_BUFFER_BIT);
        }
        if ((mask & GL11.GL_STENCIL_BUFFER_BIT) > 0) {
            vkMask |= VulkanConstants.VkImageAspectFlagBits.ofGL(GL11.GL_STENCIL_BUFFER_BIT);
        }
        if (vkMask > 0) {
            clearUIDepthStencil(vkMask);
        }
    }

    // ── Viewport / Scissor ──

    public static native void setUIViewport(int x, int y, int width, int height);

    public static native void setUIScissor(int x, int y, int width, int height);

    public static native void setUIScissorEnabled(boolean enabled);

    // ── Color blend state ──

    public static native void setUIBlendEnable(boolean enable);

    public static native void setUIBlendFuncSeparate(int srcColor, int srcAlpha,
        int dstColor, int dstAlpha);

    public static native void setUIBlendOpSeparate(int colorOp, int alphaOp);

    public static native void setUIColorWriteMask(int mask);

    public static native void setUIColorLogicOpEnable(boolean enable);

    public static native void setUIColorLogicOp(int op);

    public static native void setUIBlendConstants(float c0, float c1, float c2, float c3);

    // GL convenience wrappers
    public static void glSetBlendFuncSeparate(int srcColor, int srcAlpha, int dstColor, int dstAlpha) {
        setUIBlendFuncSeparate(
            VulkanConstants.VkBlendFactor.ofGL(srcColor),
            VulkanConstants.VkBlendFactor.ofGL(srcAlpha),
            VulkanConstants.VkBlendFactor.ofGL(dstColor),
            VulkanConstants.VkBlendFactor.ofGL(dstAlpha));
    }

    public static void glSetBlendOpSeparate(int colorOp, int alphaOp) {
        setUIBlendOpSeparate(
            VulkanConstants.VkBlendOp.ofGL(colorOp),
            VulkanConstants.VkBlendOp.ofGL(alphaOp));
    }

    public static void glSetColorWriteMask(boolean r, boolean g, boolean b, boolean a) {
        int mask = 0;
        if (r) mask |= VulkanConstants.VkColorComponentFlagBits.VK_COLOR_COMPONENT_R_BIT.getValue();
        if (g) mask |= VulkanConstants.VkColorComponentFlagBits.VK_COLOR_COMPONENT_G_BIT.getValue();
        if (b) mask |= VulkanConstants.VkColorComponentFlagBits.VK_COLOR_COMPONENT_B_BIT.getValue();
        if (a) mask |= VulkanConstants.VkColorComponentFlagBits.VK_COLOR_COMPONENT_A_BIT.getValue();
        setUIColorWriteMask(mask);
    }

    public static void glSetColorLogicOp(int op) {
        setUIColorLogicOp(VulkanConstants.VkLogicOp.ofGL(op));
    }

    // ── Depth / Stencil state ──

    public static native void setUIDepthTestEnable(boolean enable);

    public static native void setUIDepthWriteEnable(boolean enable);

    public static native void setUIDepthCompareOp(int op);

    public static native void setUIStencilTestEnable(boolean enable);

    public static native void setUIStencilFrontFunc(int compareOp, int reference, int compareMask);

    public static native void setUIStencilBackFunc(int compareOp, int reference, int compareMask);

    public static native void setUIStencilFrontOp(int failOp, int depthFailOp, int passOp);

    public static native void setUIStencilBackOp(int failOp, int depthFailOp, int passOp);

    public static native void setUIStencilFrontWriteMask(int writeMask);

    public static native void setUIStencilBackWriteMask(int writeMask);

    // GL convenience
    public static void glSetDepthCompareOp(int op) {
        setUIDepthCompareOp(VulkanConstants.VkCompareOp.ofGL(op));
    }

    public static void glSetStencilFunc(int func, int ref, int mask) {
        int vkFunc = VulkanConstants.VkStencilOp.ofGL(func);
        setUIStencilFrontFunc(vkFunc, ref, mask);
        setUIStencilBackFunc(vkFunc, ref, mask);
    }

    // ── Rasterization state ──

    public static native void setUILineWidth(float lineWidth);

    public static native void setUICullMode(int cullMode);

    public static native void setUIFrontFace(int frontFace);

    public static native void setUIPolygonMode(int polygonMode);

    public static native void setUIDepthBiasEnable(int polygonMode, boolean enable);

    public static native void setUIDepthBias(float slopeFactor, float constantFactor);

    // GL convenience
    public static void glSetCullMode(int cullMode) {
        setUICullMode(VulkanConstants.VkCullMode.ofGL(cullMode));
    }

    public static void glSetFrontFace(int frontFace) {
        setUIFrontFace(VulkanConstants.VkFrontFace.ofGL(frontFace));
    }

    public static void glSetPolygonMode(int polygonMode) {
        setUIPolygonMode(VulkanConstants.VkPolygonMode.ofGL(polygonMode));
    }

    // ── Clear state ──

    public static native void setUIClearColor(float r, float g, float b, float a);

    // ── Sync ──

    /** Push all accumulated dynamic state to the command buffer. */
    public static native void syncUIDynamicState();
}
