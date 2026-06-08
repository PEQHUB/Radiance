package com.radiance.client.proxy.vulkan;

import com.radiance.client.constant.VulkanConstants;
import org.lwjgl.opengl.GL11;

public class PipelineStateProxy {

    public static class ViewportState {

        public static native void setScissorEnabled(boolean enabled);

        public static native void setScissor(int x, int y, int width, int height);

        public static native void setViewport(int x, int y, int width, int height);
    }

    public static class ColorBlendState {

        // region <common>
        public native static void setBlendEnable(boolean enable);

        public native static void setColorBlendConstants(float const1, float const2, float const3,
            float const4);

        public native static void setColorLogicOpEnable(boolean enable);
        // endregion

        // region <vulkan>
        public native static void vkSetBlendFuncSeparate(int srcColorBlendFactor,
            int srcAlphaBlendFactor,
            int dstColorBlendFactor,
            int dstAlphaBlendFactor);

        public static void vkSetBlendFuncCombined(int srcBlendFactor, int dstBlendFactor) {
            vkSetBlendFuncSeparate(srcBlendFactor, srcBlendFactor, dstBlendFactor, dstBlendFactor);
        }

        public native static void vkSetBlendOpSeparate(int colorBlendOp, int alphaBlendOp);

        public static void vkSetBlendOpCombined(int blendOp) {
            vkSetBlendOpSeparate(blendOp, blendOp);
        }

        public native static void vkSetColorWriteMask(int colorWriteMask);

        public native static void vkSetColorLogicOp(int colorLogicOp);
        // endregion

        // region <openGL>
        public static void glSetBlendFuncSeparate(int srcColorBlendFactor,
            int srcAlphaBlendFactor,
            int dstColorBlendFactor,
            int dstAlphaBlendFactor) {
            vkSetBlendFuncSeparate(VulkanConstants.VkBlendFactor.ofGL(srcColorBlendFactor),
                VulkanConstants.VkBlendFactor.ofGL(srcAlphaBlendFactor),
                VulkanConstants.VkBlendFactor.ofGL(dstColorBlendFactor),
                VulkanConstants.VkBlendFactor.ofGL(dstAlphaBlendFactor));
        }

        public static void glSetBlendFuncCombined(int srcBlendFactor, int dstBlendFactor) {
            vkSetBlendFuncCombined(VulkanConstants.VkBlendFactor.ofGL(srcBlendFactor),
                VulkanConstants.VkBlendFactor.ofGL(dstBlendFactor));
        }

        public static void glSetBlendOpSeparate(int colorBlendOp, int alphaBlendOp) {
            vkSetBlendOpSeparate(VulkanConstants.VkBlendOp.ofGL(colorBlendOp),
                VulkanConstants.VkBlendOp.ofGL(alphaBlendOp));
        }

        public static void glSetBlendOpCombined(int blendOp) {
            vkSetBlendOpCombined(VulkanConstants.VkBlendOp.ofGL(blendOp));
        }

        public static void glSetColorWriteMask(boolean r, boolean g, boolean b, boolean a) {
            int mask = 0;
            if (r) {
                mask |= VulkanConstants.VkColorComponentFlagBits.VK_COLOR_COMPONENT_R_BIT.getValue();
            }
            if (g) {
                mask |= VulkanConstants.VkColorComponentFlagBits.VK_COLOR_COMPONENT_G_BIT.getValue();
            }
            if (b) {
                mask |= VulkanConstants.VkColorComponentFlagBits.VK_COLOR_COMPONENT_B_BIT.getValue();
            }
            if (a) {
                mask |= VulkanConstants.VkColorComponentFlagBits.VK_COLOR_COMPONENT_A_BIT.getValue();
            }
            vkSetColorWriteMask(mask);
        }

        public static void glSetColorLogicOp(int colorLogicOp) {
            vkSetColorLogicOp(VulkanConstants.VkLogicOp.ofGL(colorLogicOp));
        }
        // endregion
    }

    public static class DepthStencilState {

        // region <common>
        public static native void setDepthTestEnable(boolean enable);

        public static native void setDepthWriteEnable(boolean enable);

        public static native void setStencilTestEnable(boolean enable);
        // endregion

        // region <vulkan>
        public static native void vkSetDepthCompareOp(int depthCompareOp);

        public static native void vkSetStencilFrontFunc(int compareOp, int reference,
            int compareMask);

        public static native void vkSetStencilBackFunc(int compareOp, int reference,
            int compareMask);

        public static void vkSetStencilFunc(int compareOp, int reference, int compareMask) {
            vkSetStencilFrontFunc(compareOp, reference, compareMask);
            vkSetStencilBackFunc(compareOp, reference, compareMask);
        }

        public static native void vkSetStencilFrontOp(int failOp, int depthFailOp, int passOp);

        public static native void vkSetStencilBackOp(int failOp, int depthFailOp, int passOp);

        public static void vkSetStencilOp(int failOp, int depthFailOp, int passOp) {
            vkSetStencilFrontOp(failOp, depthFailOp, passOp);
            vkSetStencilBackOp(failOp, depthFailOp, passOp);
        }

        public static native void vkSetStencilFrontWriteMask(int writeMask);

        public static native void vkSetStencilBackWriteMask(int writeMask);

        public static void vkSetStencilWriteMask(int writeMask) {
            vkSetStencilFrontWriteMask(writeMask);
            vkSetStencilBackWriteMask(writeMask);
        }
        // endregion

        // region <openGL>
        public static void glSetDepthCompareOp(int depthCompareOp) {
            vkSetDepthCompareOp(VulkanConstants.VkCompareOp.ofGL(depthCompareOp));
        }

        public static void glSetStencilFuncSeparate(int face, int func, int ref, int mask) {
            if (face == GL11.GL_FRONT) {
                vkSetStencilFrontFunc(func, ref, mask);
            } else {
                vkSetStencilBackFunc(func, ref, mask);
            }
        }

        public static void glSetStencilFunc(int func, int ref, int mask) {
            vkSetStencilFunc(VulkanConstants.VkStencilOp.ofGL(func), ref, mask);
        }

        public static void glSetStencilOpSeparate(int face, int failOp, int depthFailOp,
            int passOp) {
            if (face == GL11.GL_FRONT) {
                vkSetStencilFrontOp(VulkanConstants.VkStencilOp.ofGL(failOp),
                    VulkanConstants.VkStencilOp.ofGL(depthFailOp),
                    VulkanConstants.VkStencilOp.ofGL(passOp));
            } else {
                vkSetStencilBackOp(VulkanConstants.VkStencilOp.ofGL(failOp),
                    VulkanConstants.VkStencilOp.ofGL(depthFailOp),
                    VulkanConstants.VkStencilOp.ofGL(passOp));
            }
        }

        public static void glSetStencilOp(int failOp, int depthFailOp, int passOp) {
            vkSetStencilOp(VulkanConstants.VkStencilOp.ofGL(failOp),
                VulkanConstants.VkStencilOp.ofGL(depthFailOp),
                VulkanConstants.VkStencilOp.ofGL(passOp));
        }
        // endregion
    }

    public static class RasterizationState {

        // region <common>
        public static native void setLineWidth(float lineWidth);
        // endregion

        // region <vulkan>
        public static native void vkSetPolygonMode(int polygonMode);

        public static native void vkSetCullMode(int cullMode);

        public static native void vkSetFrontFace(int frontFace);

        public static native void vkSetDepthBiasEnable(int polygonMode, boolean enable);

        public static native void vkSetDepthBias(float depthBiasSlopeFactor,
            float depthBiasConstantFactor);
        // endregion

        // region <openGL>
        public static void glSetPolygonMode(int polygonMode) {
            vkSetPolygonMode(VulkanConstants.VkPolygonMode.ofGL(polygonMode));
        }

        public static void glSetCullMode(int cullMode) {
            vkSetCullMode(VulkanConstants.VkCullMode.ofGL(cullMode));
        }

        public static void glSetFrontFace(int frontFace) {
            vkSetFrontFace(VulkanConstants.VkFrontFace.ofGL(frontFace));
        }

        public static void glSetPolygonOffsetEnable(int polygonMode, boolean enable) {
            vkSetDepthBiasEnable(VulkanConstants.VkPolygonMode.ofGL(polygonMode), enable);
        }

        public static void glSetPolygonOffset(float factor, float units) {
            vkSetDepthBias(factor, units);
        }
        // region
    }

    public static class ClearState {

        public static native void setClearColor(float red, float green, float blue, float alpha);

        public static native void setClearDepth(double depth);

        public static native void setClearStencil(int stencil);
    }
}
