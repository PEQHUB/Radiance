package com.radiance.client.proxy.vulkan;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.texture.MissingSprite;
import net.minecraft.util.Identifier;
import com.radiance.client.texture.TextureTracker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class TextureArrayBridge {
    private static final Logger LOGGER = LoggerFactory.getLogger("TextureArrayBridge");

    private static volatile long activeTextureGeneration = 0;

    public static List<Identifier> sortedSpriteIds = new ArrayList<>();
    private static Map<Identifier, Integer> spriteIdLookup = new HashMap<>();
    private static volatile int missingSpriteFallbackId = -1;
    private static volatile Identifier missingSpriteFallbackSprite = null;
    private static final List<Identifier> MISSING_SPRITE_FALLBACKS = List.of(
        Identifier.ofVanilla("block/dirt"),
        Identifier.ofVanilla("block/stone"),
        Identifier.ofVanilla("block/oak_planks"),
        Identifier.ofVanilla("block/debug"));
    private static final List<Identifier> MISSING_RENDER_TEXTURE_FALLBACKS = List.of(
        Identifier.ofVanilla("textures/atlas/blocks.png"),
        Identifier.ofVanilla("textures/atlas/gui.png"));

    private TextureArrayBridge() {
    }

    public static long getActiveTextureGeneration() {
        return activeTextureGeneration;
    }

    public static void incrementTextureGeneration() {
        activeTextureGeneration++;
    }

    public static void publishTextureGeneration() {
        try {
            nativeSetTextureGeneration(activeTextureGeneration);
        } catch (UnsatisfiedLinkError e) {
            LOGGER.debug("[TextureSystem] Native texture generation publish skipped", e);
        }
    }

    public static void setSortedSpriteIds(List<Identifier> spriteIds) {
        sortedSpriteIds = new ArrayList<>(spriteIds);
        Map<Identifier, Integer> nextLookup = new HashMap<>();
        for (int i = 0; i < sortedSpriteIds.size(); i++) {
            nextLookup.put(sortedSpriteIds.get(i), i);
        }
        spriteIdLookup = nextLookup;
        refreshMissingSpriteFallback(nextLookup);
        LOGGER.info("[TextureSystem] Sprite lookup refreshed: {} entries", spriteIdLookup.size());
    }

    private static void refreshMissingSpriteFallback(Map<Identifier, Integer> lookup) {
        missingSpriteFallbackId = -1;
        missingSpriteFallbackSprite = null;
        for (Identifier candidate : MISSING_SPRITE_FALLBACKS) {
            Integer id = lookup.get(candidate);
            if (id != null && id >= 0) {
                missingSpriteFallbackId = id;
                missingSpriteFallbackSprite = candidate;
                return;
            }
        }
        for (Map.Entry<Identifier, Integer> entry : lookup.entrySet()) {
            if (!MissingSprite.getMissingSpriteId().equals(entry.getKey())) {
                missingSpriteFallbackId = entry.getValue();
                missingSpriteFallbackSprite = entry.getKey();
                return;
            }
        }
    }

    public static int resolveRenderableSpriteId(Identifier id) {
        if (id == null) {
            return -1;
        }
        Integer spriteId = spriteIdLookup.get(id);
        if (spriteId != null && spriteId >= 0 && !MissingSprite.getMissingSpriteId().equals(id)) {
            return spriteId;
        }
        return missingSpriteFallbackId;
    }

    public static int resolveRenderableTextureGlId(Identifier id, int glId) {
        if (id != null && !MissingSprite.getMissingSpriteId().equals(id)
            && glId >= 0 && !isMissingTextureGlId(glId)) {
            return glId;
        }
        int fallback = renderTextureFallbackGlIdForTest();
        return fallback >= 0 ? fallback : glId;
    }

    public static int renderTextureFallbackGlIdForTest() {
        for (Identifier candidate : MISSING_RENDER_TEXTURE_FALLBACKS) {
            Integer glId = TextureTracker.textureID2GLID.get(candidate);
            if (glId != null && glId >= 0 && !isMissingTextureGlId(glId)) {
                return glId;
            }
        }
        return TextureTracker.textureID2GLID.entrySet().stream()
            .filter(entry -> entry.getValue() != null && entry.getValue() >= 0)
            .filter(entry -> !MissingSprite.getMissingSpriteId().equals(entry.getKey()))
            .filter(entry -> !isMissingTextureGlId(entry.getValue()))
            .sorted(Comparator.comparing(entry -> entry.getKey().toString()))
            .map(Map.Entry::getValue)
            .findFirst()
            .orElse(-1);
    }

    private static boolean isMissingTextureGlId(int glId) {
        Integer missingGlId = TextureTracker.textureID2GLID.get(MissingSprite.getMissingSpriteId());
        return missingGlId != null && missingGlId == glId;
    }

    public static int missingSpriteFallbackIdForTest() {
        return missingSpriteFallbackId;
    }

    public static String missingSpriteFallbackLabel() {
        return missingSpriteFallbackSprite == null ? "none" : missingSpriteFallbackSprite.toString();
    }

    public static int resolveSpriteId(String text) {
        if (text == null || text.isBlank()) {
            return -1;
        }
        String token = text.trim();
        try {
            int numeric = Integer.parseInt(token);
            return numeric >= 0 ? numeric : -1;
        } catch (NumberFormatException ignored) {
        }

        Identifier id = Identifier.tryParse(token);
        if (id == null && !token.contains(":")) {
            id = Identifier.ofVanilla(token);
        }
        if (id == null) {
            return -1;
        }
        return spriteIdLookup.getOrDefault(id, -1);
    }

    public static void reset() {
        sortedSpriteIds = new ArrayList<>();
        spriteIdLookup = new HashMap<>();
        missingSpriteFallbackId = -1;
        missingSpriteFallbackSprite = null;
    }

    public static native void nativeReceiveSpriteTable(long metaPtr, int count,
        int atlasWidth, int atlasHeight);
    public static native void nativeReceiveSpritePixels(long dataPtr, int totalBytes);
    public static native void nativeReceiveAnimationFrames(long dataPtr, int totalBytes);
    public static native void nativeTextureFinalize();
    public static native void nativeSetTextureGeneration(long generation);
    public static native void nativeTickAnimation(int gameTick, long generation);
    public static native void nativeSetTextureArrayAnimationEnabled(boolean enabled);
    public static native void nativeReceiveSpriteAuxPixels(
        long specularDataPtr, long normalDataPtr, long flagDataPtr, int totalBytesPerType);
    public static native boolean nativeUpdateAlbedoLayer(int spriteId, long pixelPtr, int sizeBytes,
        long generation);
    public static native boolean nativeUpdateSpecularLayer(int spriteId, long pixelPtr, int sizeBytes,
        long generation);
    public static native boolean nativeUpdateNormalLayer(int spriteId, long pixelPtr, int sizeBytes,
        long generation);
    public static native boolean nativeUpdateFlagLayer(int spriteId, long pixelPtr, int sizeBytes,
        long generation);
    public static native boolean nativeUpdateSpriteHeightMetadata(int spriteId, int flags,
        int heightRangePacked, long generation);
    public static native boolean nativeReceiveTextureRules(long dataPtr, int count, long generation);

    public static void updateAnimatedSprites(int animTick) {
        nativeTickAnimation(animTick, getActiveTextureGeneration());
    }
}
