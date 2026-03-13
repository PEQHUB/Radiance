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
    // Albedo GLIDs whose normal map contains valid height data (for POM)
    public static Set<Integer> hasHeightMap = ConcurrentHashMap.newKeySet();

    // Blender PBR per-channel texture IDs, keyed by albedo GLID
    public static Map<Integer, Map<BlenderChannel, Integer>> blenderPBRTextures = new ConcurrentHashMap<>();
    // All Blender PBR texture IDs (for cleanup on world unload / resource pack reload)
    public static Set<Integer> blenderTextureIDs = ConcurrentHashMap.newKeySet();

    /**
     * Blender PBR channel types. ssboOffset is the int index within TextureMapEntry
     * (shared.hpp) where this channel's texture ID is stored.
     */
    public enum BlenderChannel {
        ROUGHNESS(4),
        METALLIC(5),
        EMISSION(6),
        NORMAL(7),
        HEIGHT(8),
        AO(9),
        EXTRA(10);

        public final int ssboOffset;
        BlenderChannel(int ssboOffset) { this.ssboOffset = ssboOffset; }
    }

    // Albedo NativeImage copies keyed by albedo GLID — for live auto-PBR re-generation
    public static Map<Integer, NativeImage> materialBlockAlbedoCache = new ConcurrentHashMap<>();
    // Albedo GLIDs whose normal was auto-PBR generated (not from resource pack)
    public static Set<Integer> autoPBRNormalGLIDs = ConcurrentHashMap.newKeySet();
    // Albedo GLIDs whose specular was auto-PBR generated
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
