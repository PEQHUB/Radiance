package com.radiance.client.proxy.vulkan;

import com.radiance.client.constant.VulkanConstants;
import net.minecraft.client.texture.NativeImage;

public class TextureProxy {

    public static native int generateTextureId();

    public static native void prepareImage(int id, int mipLevels, int width,
        int height, int format);

    public static void prepareImage(int id, int mipLevels, int width, int height,
        VulkanConstants.VkFormat format) {
        prepareImage(id, mipLevels, width, height, format.getValue());
    }

    public static native void setFilter(int id, int samplingMode, int mipmapMode);

    public static native void setClamp(int id, int addressMode);

    public static native void queueUpload(long srcPointer,
        int srcSizeInBytes,
        int srcRowPixels,
        int dstId,
        int srcOffsetX,
        int srcOffsetY,
        int dstOffsetX,
        int dstOffsetY,
        int width,
        int height,
        int level);

    // OMM: 0 = FULLY_OPAQUE, 1 = FULLY_TRANSPARENT, 2 = MIXED
    public static native void setTextureAlphaClass(int id, int alphaClass);

    // Safely destroy a Vulkan texture — GC-defers image/sampler, frees ID for reuse
    public static native void destroyTexture(int id);

    public static void prepareImage(NativeImage.InternalFormat internalFormat, int id,
        int mipLevels, int width, int height) {
        switch (internalFormat) {
            case RGBA:
                prepareImage(id, mipLevels, width, height,
                    VulkanConstants.VkFormat.VK_FORMAT_R8G8B8A8_SRGB);
                break;
            case RGB:
                prepareImage(id, mipLevels, width, height,
                    VulkanConstants.VkFormat.VK_FORMAT_R8G8B8_SRGB);
                break;
            case RG:
                prepareImage(id, mipLevels, width, height,
                    VulkanConstants.VkFormat.VK_FORMAT_R8G8_SRGB);
                break;
            case RED:
                prepareImage(id, mipLevels, width, height,
                    VulkanConstants.VkFormat.VK_FORMAT_R8_SRGB);
                break;
        }
    }
}
