package com.radiance.client.texture;

import com.radiance.client.constant.VulkanConstants;
import java.util.Arrays;
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
    public static final int SPRITE_FLAG_SPEC_SOURCE_SHIFT = 3;
    public static final int SPRITE_FLAG_NORMAL_SOURCE_SHIFT = 5;
    public static final int SPRITE_FLAG_SOURCE_MASK = 0x3;
    public static final int MAX_SPRITES = 2048;

    public static Map<Identifier, Integer> textureID2GLID = new ConcurrentHashMap<>();
    public static Map<Integer, Texture> GLID2Texture = new ConcurrentHashMap<>();

    public static final int MAX_TEXTURES = 4096;
    public static int[] GLID2SpecularGLID = new int[MAX_TEXTURES];
    public static int[] GLID2NormalGLID = new int[MAX_TEXTURES];
    public static int[] GLID2FlagGLID = new int[MAX_TEXTURES];
    // Material class mask: albedo GLID → R8_UNORM mask texture ID (per-texel class index)
    public static int[] GLID2MaskGLID = new int[MAX_TEXTURES];
    // Pending mask registrations: flushed after performQueuedUpload() to ensure texture data
    // is uploaded before the descriptor is referenced. Without this, the mask texture is in
    // UNDEFINED layout when the shader first reads it → garbage material index → flickering.
    public static Map<Integer, Integer> pendingMaskGLID = new ConcurrentHashMap<>();

    static {
        Arrays.fill(GLID2SpecularGLID, -1);
        Arrays.fill(GLID2NormalGLID, -1);
        Arrays.fill(GLID2FlagGLID, -1);
        Arrays.fill(GLID2MaskGLID, -1);
    }

    /**
     * Flush pending mask texture registrations into the active GLID2MaskGLID map.
     * Called from BufferProxy after performQueuedUpload() completes, ensuring the
     * texture data is resident before the mask ID appears in the TextureMapping SSBO.
     */
    public static void flushPendingMasks() {
        if (!pendingMaskGLID.isEmpty()) {
            for (var entry : pendingMaskGLID.entrySet()) {
                int key = entry.getKey();
                if (key >= 0 && key < MAX_TEXTURES) GLID2MaskGLID[key] = entry.getValue();
            }
            pendingMaskGLID.clear();
        }
    }
    // Albedo GLIDs whose normal map contains valid height data (for POM)
    public static Set<Integer> hasHeightMap = ConcurrentHashMap.newKeySet();

    // Shared singleton fallback mask texture (1x1 R8_UNORM, value=255).
    // Reused for ALL unregistered block textures — zero descriptor bloat.
    public static volatile int fallbackMaskGLID = -1;

    // Fallback luminance range for unregistered textures (mbOrdinal == -1)
    public static Map<Integer, float[]> fallbackLumRange = new ConcurrentHashMap<>();

    // Albedo NativeImage copies keyed by albedo GLID — for legacy atlas live re-upload
    public static Map<Integer, NativeImage> materialBlockAlbedoCache = new ConcurrentHashMap<>();
    // Per-sprite albedo NativeImage copies keyed by sprite ID — for texture array live re-upload
    // Populated in SpriteAtlasTextureMixins with sprite-sized (not atlas-sized) images
    public static Map<Integer, NativeImage> spriteAlbedoCache = new ConcurrentHashMap<>();
    public static volatile int currentSpriteLayerSize = 0;
    // Albedo GLID → MaterialBlock ordinal (for per-block Auto-PBR toggle check)
    public static Map<Integer, Integer> albedoGLID2BlockOrdinal = new ConcurrentHashMap<>();
    // Albedo GLIDs whose normal slot exists and can be auto-PBR re-uploaded
    public static Set<Integer> autoPBRNormalGLIDs = ConcurrentHashMap.newKeySet();
    // Albedo GLIDs whose specular slot exists and can be auto-PBR re-uploaded
    public static Set<Integer> autoPBRSpecularGLIDs = ConcurrentHashMap.newKeySet();

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

    public static void resetSpriteAuxSources(int spriteCount) {
        int count = Math.min(spriteCount, MAX_TEXTURES);
        Arrays.fill(spriteSpecularSource, 0, count, SOURCE_GENERATED);
        Arrays.fill(spriteNormalSource, 0, count, SOURCE_GENERATED);
        packProvidedSpecularSpriteIds.clear();
        packProvidedNormalSpriteIds.clear();
    }

    public static void beginBlockAtlasReload() {
        Arrays.fill(GLID2SpecularGLID, -1);
        Arrays.fill(GLID2NormalGLID, -1);
        Arrays.fill(GLID2FlagGLID, -1);
        Arrays.fill(GLID2MaskGLID, -1);
        pendingMaskGLID.clear();
        hasHeightMap.clear();
        fallbackLumRange.clear();
        materialBlockAlbedoCache.clear();
        spriteAlbedoCache.clear();
        currentSpriteLayerSize = 0;
        albedoGLID2BlockOrdinal.clear();
        autoPBRNormalGLIDs.clear();
        autoPBRSpecularGLIDs.clear();
        packProvidedSpecularGLIDs.clear();
        packProvidedNormalGLIDs.clear();
        customSpecularGLIDs.clear();
        customNormalGLIDs.clear();
        packProvidedSpecularSpriteIds.clear();
        packProvidedNormalSpriteIds.clear();
        Arrays.fill(spriteSpecularSource, SOURCE_GENERATED);
        Arrays.fill(spriteNormalSource, SOURCE_GENERATED);
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
