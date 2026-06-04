package com.radiance.client.proxy.vulkan;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class TextureArrayBridge {
    private static final Logger LOGGER = LoggerFactory.getLogger("TextureArrayBridge");

    private static volatile long activeTextureGeneration = 0;

    public static List<Identifier> sortedSpriteIds = new ArrayList<>();
    private static Map<Identifier, Integer> spriteIdLookup = new HashMap<>();

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
        LOGGER.info("[TextureSystem] Sprite lookup refreshed: {} entries", spriteIdLookup.size());
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
    public static native void nativeUpdateAlbedoLayer(int spriteId, long pixelPtr, int sizeBytes,
        long generation);
    public static native void nativeUpdateSpecularLayer(int spriteId, long pixelPtr, int sizeBytes,
        long generation);
    public static native void nativeUpdateNormalLayer(int spriteId, long pixelPtr, int sizeBytes,
        long generation);
    public static native void nativeUpdateFlagLayer(int spriteId, long pixelPtr, int sizeBytes,
        long generation);
    public static native void nativeUpdateSpriteHeightMetadata(int spriteId, int flags,
        int heightRangePacked, long generation);
    public static native void nativeReceiveTextureRules(long dataPtr, int count, long generation);

    public static void updateAnimatedSprites(int animTick) {
        nativeTickAnimation(animTick, getActiveTextureGeneration());
    }
}
