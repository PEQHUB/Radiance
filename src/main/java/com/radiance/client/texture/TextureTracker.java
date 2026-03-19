package com.radiance.client.texture;

import com.radiance.client.constant.VulkanConstants;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.util.Identifier;

public class TextureTracker {

    public static Map<Identifier, Integer> textureID2GLID = new ConcurrentHashMap<>();
    public static Map<Integer, Texture> GLID2Texture = new ConcurrentHashMap<>();
    public static Map<Integer, Integer> GLID2SpecularGLID = new ConcurrentHashMap<>();
    public static Map<Integer, Integer> GLID2NormalGLID = new ConcurrentHashMap<>();
    public static Map<Integer, Integer> GLID2FlagGLID = new ConcurrentHashMap<>();
    // Material class mask: albedo GLID → R8_UNORM mask texture ID (per-texel class index)
    public static Map<Integer, Integer> GLID2MaskGLID = new ConcurrentHashMap<>();
    // Pending mask registrations: flushed after performQueuedUpload() to ensure texture data
    // is uploaded before the descriptor is referenced. Without this, the mask texture is in
    // UNDEFINED layout when the shader first reads it → garbage material index → flickering.
    public static Map<Integer, Integer> pendingMaskGLID = new ConcurrentHashMap<>();

    /**
     * Flush pending mask texture registrations into the active GLID2MaskGLID map.
     * Called from BufferProxy after performQueuedUpload() completes, ensuring the
     * texture data is resident before the mask ID appears in the TextureMapping SSBO.
     */
    public static void flushPendingMasks() {
        if (!pendingMaskGLID.isEmpty()) {
            GLID2MaskGLID.putAll(pendingMaskGLID);
            pendingMaskGLID.clear();
        }
    }
    // Albedo GLIDs whose normal map contains valid height data (for POM)
    public static Set<Integer> hasHeightMap = ConcurrentHashMap.newKeySet();

    // Albedo NativeImage copies keyed by albedo GLID — for live auto-PBR re-generation
    public static Map<Integer, NativeImage> materialBlockAlbedoCache = new ConcurrentHashMap<>();
    // Albedo GLID → MaterialBlock ordinal (for per-block Auto-PBR toggle check)
    public static Map<Integer, Integer> albedoGLID2BlockOrdinal = new ConcurrentHashMap<>();
    // Albedo GLIDs whose normal slot exists and can be auto-PBR re-uploaded
    public static Set<Integer> autoPBRNormalGLIDs = ConcurrentHashMap.newKeySet();
    // Albedo GLIDs whose specular slot exists and can be auto-PBR re-uploaded
    public static Set<Integer> autoPBRSpecularGLIDs = ConcurrentHashMap.newKeySet();

    public record Texture(int width, int height, int channel, VulkanConstants.VkFormat format,
                          int maxLayer) {

        public Texture {
            if (width <= 0 || height <= 0 || channel <= 0 || maxLayer < 0) {
                throw new IllegalArgumentException(
                    "Invalid texture width, height, channel, or maxLayer: " + width + ", " + height
                        + ", " + channel + ", " + maxLayer);
            }
        }

        public Texture(int width, int height, NativeImage.InternalFormat format, int maxLayer) {
            this(width, height, getChannel(format), getFormat(format), maxLayer);
        }

        private static int getChannel(NativeImage.InternalFormat internalFormat) {
            return switch (internalFormat) {
                case RGBA -> 4;
                case RGB -> 3;
                case RG -> 2;
                case RED -> 1;
                default -> throw new IllegalArgumentException(
                    "Unknown internal format: " + internalFormat);
            };
        }

        private static VulkanConstants.VkFormat getFormat(
            NativeImage.InternalFormat internalFormat) {
            return switch (internalFormat) {
                case RGBA -> VulkanConstants.VkFormat.VK_FORMAT_R8G8B8A8_SRGB;
                case RGB -> VulkanConstants.VkFormat.VK_FORMAT_R8G8B8_SRGB;
                case RG -> VulkanConstants.VkFormat.VK_FORMAT_R8G8_SRGB;
                case RED -> VulkanConstants.VkFormat.VK_FORMAT_R8_SRGB;
            };
        }
    }
}
