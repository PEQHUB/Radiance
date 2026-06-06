package com.radiance.client.texture;

import com.radiance.client.constant.VulkanConstants;
import java.util.Arrays;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.util.Identifier;

public class TextureTracker {

    public static final byte SOURCE_GENERATED = 0;
    public static final byte SOURCE_PACK_AUTHORED = 1;
    public static final byte SOURCE_USER_CUSTOM = 2;
    public static final byte SOURCE_FLAT = 3;

    public static final int SPRITE_FLAG_HAS_SPECULAR = 1 << 0;
    public static final int SPRITE_FLAG_HAS_NORMAL = 1 << 1;
    public static final int SPRITE_FLAG_HAS_HEIGHT = 1 << 2;
    public static final int SPRITE_FLAG_EMISSIVE_OVERLAY = 1 << 7;
    public static final int SPRITE_FLAG_SPEC_SOURCE_SHIFT = 3;
    public static final int SPRITE_FLAG_NORMAL_SOURCE_SHIFT = 5;
    public static final int SPRITE_FLAG_SOURCE_MASK = 0x3;
    public static final int MAX_SPRITES = 4096;
    public static volatile boolean textureArrayAnimationUpdatesEnabled = false;

    /**
     * Set texture array animation enabled state and sync to C++.
     * When disabled (default), vanilla atlas animation ticks are frozen and
     * C++ texture arrays do not receive animation updates. This prevents
     * per-tick NativeImage pixel copies + GL upload calls that cause menu
     * stutter with high-res texture packs.
     */
    public static void setTextureArrayAnimationUpdatesEnabled(boolean enabled) {
        textureArrayAnimationUpdatesEnabled = enabled;
        try {
            com.radiance.client.proxy.vulkan.TextureArrayBridge.nativeSetTextureArrayAnimationEnabled(enabled);
        } catch (UnsatisfiedLinkError ignored) {
            // Native not loaded yet
        }
    }

    public static Map<Identifier, Integer> textureID2GLID = new ConcurrentHashMap<>();
    public static Map<Integer, Texture> GLID2Texture = new ConcurrentHashMap<>();

    public static final int MAX_TEXTURES = 4096;
    public static int[] GLID2SpecularGLID = new int[MAX_TEXTURES];
    public static int[] GLID2NormalGLID = new int[MAX_TEXTURES];
    public static int[] GLID2FlagGLID = new int[MAX_TEXTURES];
    static {
        Arrays.fill(GLID2SpecularGLID, -1);
        Arrays.fill(GLID2NormalGLID, -1);
        Arrays.fill(GLID2FlagGLID, -1);
    }

    // Albedo GLIDs whose normal map contains valid height data for height-field displacement.
    public static Set<Integer> hasHeightMap = ConcurrentHashMap.newKeySet();

    // Albedo NativeImage copies keyed by albedo GLID — for legacy atlas live re-upload
    // Per-sprite albedo NativeImage copies keyed by sprite ID — for texture array live re-upload
    // Populated in SpriteAtlasTextureMixins with sprite-sized (not atlas-sized) images
    public static Map<Integer, NativeImage> spriteAlbedoCache = new ConcurrentHashMap<>();
    public static Map<Integer, NativeImage> spriteSpecularCache = new ConcurrentHashMap<>();
    public static Map<Integer, NativeImage> spriteNormalCache = new ConcurrentHashMap<>();
    public static Map<Integer, NativeImage> spriteFlagCache = new ConcurrentHashMap<>();
    public static Map<Integer, NativeImage> spriteBaselineSpecularCache = new ConcurrentHashMap<>();
    public static Map<Integer, NativeImage> spriteBaselineNormalCache = new ConcurrentHashMap<>();
    public static volatile int currentSpriteLayerSize = 0;
    // Auxiliary texture provenance keyed by auxiliary GLID. This survives the legacy upload
    // path and lets the texture-array stitch preserve authored LabPBR channels.
    public static Set<Integer> packProvidedSpecularGLIDs = ConcurrentHashMap.newKeySet();
    public static Set<Integer> packProvidedNormalGLIDs = ConcurrentHashMap.newKeySet();
    public static Set<Integer> customSpecularGLIDs = ConcurrentHashMap.newKeySet();
    public static Set<Integer> customNormalGLIDs = ConcurrentHashMap.newKeySet();

    // Per-sprite provenance keyed by texture-array spriteId.
    public static Set<Integer> packProvidedSpecularSpriteIds = ConcurrentHashMap.newKeySet();
    public static Set<Integer> packProvidedNormalSpriteIds = ConcurrentHashMap.newKeySet();
    public static byte[] spriteSpecularSource = new byte[MAX_TEXTURES];
    public static byte[] spriteNormalSource = new byte[MAX_TEXTURES];
    public static byte[] spriteBaselineSpecularSource = new byte[MAX_TEXTURES];
    public static byte[] spriteBaselineNormalSource = new byte[MAX_TEXTURES];

    public static void resetSpriteAuxSources(int spriteCount) {
        int count = Math.min(spriteCount, MAX_TEXTURES);
        Arrays.fill(spriteSpecularSource, 0, count, SOURCE_GENERATED);
        Arrays.fill(spriteNormalSource, 0, count, SOURCE_GENERATED);
        Arrays.fill(spriteBaselineSpecularSource, 0, count, SOURCE_GENERATED);
        Arrays.fill(spriteBaselineNormalSource, 0, count, SOURCE_GENERATED);
        packProvidedSpecularSpriteIds.clear();
        packProvidedNormalSpriteIds.clear();
    }

    public static void beginBlockAtlasReload() {
        Arrays.fill(GLID2SpecularGLID, -1);
        Arrays.fill(GLID2NormalGLID, -1);
        Arrays.fill(GLID2FlagGLID, -1);
        hasHeightMap.clear();
        closeAndClearImageCache(spriteAlbedoCache);
        closeAndClearImageCache(spriteSpecularCache);
        closeAndClearImageCache(spriteNormalCache);
        closeAndClearImageCache(spriteFlagCache);
        closeAndClearImageCache(spriteBaselineSpecularCache);
        closeAndClearImageCache(spriteBaselineNormalCache);
        currentSpriteLayerSize = 0;
        packProvidedSpecularGLIDs.clear();
        packProvidedNormalGLIDs.clear();
        customSpecularGLIDs.clear();
        customNormalGLIDs.clear();
        packProvidedSpecularSpriteIds.clear();
        packProvidedNormalSpriteIds.clear();
        Arrays.fill(spriteSpecularSource, SOURCE_GENERATED);
        Arrays.fill(spriteNormalSource, SOURCE_GENERATED);
        Arrays.fill(spriteBaselineSpecularSource, SOURCE_GENERATED);
        Arrays.fill(spriteBaselineNormalSource, SOURCE_GENERATED);
    }

    public static void clearSpriteAlbedoCache() {
        closeAndClearImageCache(spriteAlbedoCache);
        closeAndClearImageCache(spriteSpecularCache);
        closeAndClearImageCache(spriteNormalCache);
        closeAndClearImageCache(spriteFlagCache);
        closeAndClearImageCache(spriteBaselineSpecularCache);
        closeAndClearImageCache(spriteBaselineNormalCache);
    }

    public static void closeAndClearImageCache(Map<Integer, NativeImage> cache) {
        Set<NativeImage> closed = Collections.newSetFromMap(new IdentityHashMap<>());
        for (NativeImage image : cache.values()) {
            if (image == null) continue;
            if (!closed.add(image)) continue;
            try {
                image.close();
            } catch (Exception ignored) {
            }
        }
        cache.clear();
    }

    public static int encodeSpriteSourceFlags(byte specularSource, byte normalSource) {
        int spec = (specularSource & SPRITE_FLAG_SOURCE_MASK) << SPRITE_FLAG_SPEC_SOURCE_SHIFT;
        int norm = (normalSource & SPRITE_FLAG_SOURCE_MASK) << SPRITE_FLAG_NORMAL_SOURCE_SHIFT;
        return spec | norm;
    }

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
