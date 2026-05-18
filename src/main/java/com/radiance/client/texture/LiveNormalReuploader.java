package com.radiance.client.texture;

import com.radiance.client.option.Options;
import com.radiance.client.proxy.vulkan.TextureProxy;
import com.radiance.client.proxy.world.BlockModelBridge;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Re-uploads auto-PBR normal and specular maps into their existing Vulkan texture slots
 * when auto-PBR parameters change. No destroy/recreate — overwrites the same GLID.
 *
 * Two upload paths:
 *   1. Legacy atlas: directUpload() to the specular/normal GLID (entities, fallback)
 *   2. Texture array: per-sprite updates via nativeUpdateSpecularLayer/nativeUpdateNormalLayer
 *
 * The texture array path uses per-sprite albedo images from TextureTracker.spriteAlbedoCache
 * (sprite-sized, keyed by spriteId). The legacy path uses the atlas-sized cache in
 * materialBlockAlbedoCache (keyed by atlas GLID).
 */
public final class LiveNormalReuploader {

    private static final ScheduledExecutorService scheduler =
        Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "LiveNormalReuploader");
            t.setDaemon(true);
            return t;
        });

    private static ScheduledFuture<?> pendingReupload;
    private static volatile int pendingOrdinal = -1; // -1 = all

    private LiveNormalReuploader() {}

    /**
     * Schedule a debounced re-upload for a single material ordinal's auto-PBR maps.
     * Coalesces calls within 100ms into a single re-upload on the render thread.
     * Pass -1 to re-upload all materials (e.g. global toggle).
     */
    public static synchronized void scheduleReupload(int ordinal) {
        // If already pending for all, keep all; otherwise widen to all if different ordinal
        if (pendingReupload != null && !pendingReupload.isDone()) {
            if (pendingOrdinal != -1 && ordinal != -1 && pendingOrdinal != ordinal) {
                pendingOrdinal = -1; // multiple ordinals changed — do all
            } else if (ordinal == -1) {
                pendingOrdinal = -1;
            }
            pendingReupload.cancel(false);
        } else {
            pendingOrdinal = ordinal;
        }
        final int ord = pendingOrdinal;
        pendingReupload = scheduler.schedule(() -> {
            MinecraftClient.getInstance().execute(() -> reuploadAutoPBR(ord));
        }, 30, TimeUnit.MILLISECONDS);
    }

    /** Convenience overload — re-upload all materials. */
    public static void scheduleReupload() {
        scheduleReupload(-1);
    }

    public static void scheduleGeneratedReupload(int ordinal, boolean specular, boolean normal) {
        if (needsGeneratedReupload(ordinal, specular, normal)) {
            scheduleReupload(ordinal);
        }
    }

    public static void scheduleGeneratedReupload(boolean specular, boolean normal) {
        scheduleGeneratedReupload(-1, specular, normal);
    }

    /**
     * Re-generate and re-upload auto-PBR normals and speculars.
     * @param ordinal material ordinal to update, or -1 for all
     */
    public static void reuploadAutoPBR(int ordinal) {
        specUpdateCount = 0;
        normUpdateCount = 0;
        reuploadLegacyAtlas(ordinal);
        reuploadTextureArrays(ordinal);
        System.err.println("[LiveReuploader] Reupload complete: " + specUpdateCount
            + " spec layers, " + normUpdateCount + " norm layers"
            + (ordinal >= 0 ? " (ordinal " + ordinal + ")" : " (all)"));
    }

    /** Re-upload all materials (called from non-UI paths). */
    public static void reuploadAllAutoPBR() {
        reuploadAutoPBR(-1);
    }

    // ── Legacy atlas path (directUpload to GLID — entities/fallback) ──────────

    private static void reuploadLegacyAtlas(int targetOrdinal) {
        // Normals
        for (int albedoGLID : TextureTracker.autoPBRNormalGLIDs) {
            if (targetOrdinal >= 0) {
                Integer ord = TextureTracker.albedoGLID2BlockOrdinal.get(albedoGLID);
                if (ord == null || ord != targetOrdinal) continue;
            }
            NativeImage cachedAlbedo = TextureTracker.materialBlockAlbedoCache.get(albedoGLID);
            if (cachedAlbedo == null) continue;
            if (albedoGLID < 0 || albedoGLID >= TextureTracker.MAX_TEXTURES) continue;
            int normalGLID = TextureTracker.GLID2NormalGLID[albedoGLID];
            if (normalGLID == -1) continue;
            if (TextureTracker.packProvidedNormalGLIDs.contains(normalGLID)
                || TextureTracker.customNormalGLIDs.contains(normalGLID)) continue;

            try {
                Integer ordN = TextureTracker.albedoGLID2BlockOrdinal.get(albedoGLID);
                NativeImage newNormal;
                if (isAutoPBRActiveForGLID(albedoGLID) && ordN != null) {
                    newNormal = AutoPBRGenerator.generateNormal(cachedAlbedo,
                        Options.materialNormalStrength[ordN],
                        (Options.materialAutoPBRFlags[ordN] & 2) != 0,
                        Options.materialAutoPBRHeightGamma[ordN],
                        (Options.materialAutoPBRFlags[ordN] & 4) != 0,
                        AutoPBRGenerator.HeightParams.fromOptions(ordN),
                        Options.materialPomAOStrength[ordN]);
                } else {
                    newNormal = cachedAlbedo.applyToCopy(i -> (128 << 24) | (128 << 16) | (128 << 8) | 255);
                }
                NativeImage aligned = ((com.radiance.mixin_related.extensions.vulkan_render_integration.INativeImageExt)
                    (Object) newNormal).neoVoxelRT$alignTo(cachedAlbedo);
                directUpload(aligned, normalGLID);
                if (aligned != newNormal) newNormal.close();
                aligned.close();
            } catch (Exception e) {
                System.err.println("[LiveReuploader] Legacy normal re-upload failed for GLID "
                    + albedoGLID + ": " + e.getMessage());
            }
        }

        // Speculars
        for (int albedoGLID : TextureTracker.autoPBRSpecularGLIDs) {
            if (targetOrdinal >= 0) {
                Integer ord = TextureTracker.albedoGLID2BlockOrdinal.get(albedoGLID);
                if (ord == null || ord != targetOrdinal) continue;
            }
            NativeImage cachedAlbedo = TextureTracker.materialBlockAlbedoCache.get(albedoGLID);
            if (cachedAlbedo == null) continue;
            if (albedoGLID < 0 || albedoGLID >= TextureTracker.MAX_TEXTURES) continue;
            int specularGLID = TextureTracker.GLID2SpecularGLID[albedoGLID];
            if (specularGLID == -1) continue;
            if (TextureTracker.packProvidedSpecularGLIDs.contains(specularGLID)
                || TextureTracker.customSpecularGLIDs.contains(specularGLID)) continue;

            try {
                Integer ordinal = TextureTracker.albedoGLID2BlockOrdinal.get(albedoGLID);
                NativeImage newSpec;
                if (isAutoPBRActiveForGLID(albedoGLID) && ordinal != null) {
                    newSpec = AutoPBRGenerator.generateSpecularPercentile(cachedAlbedo,
                        Options.materialAutoPBRRoughnessMin[ordinal],
                        Options.materialAutoPBRRoughnessMax[ordinal],
                        Options.materialPercentileCenter[ordinal],
                        Options.materialPercentileSpread[ordinal],
                        (Options.materialAutoPBRFlags[ordinal] & 1) != 0);
                } else {
                    newSpec = cachedAlbedo.applyToCopy(i -> 0);
                }
                NativeImage aligned = ((com.radiance.mixin_related.extensions.vulkan_render_integration.INativeImageExt)
                    (Object) newSpec).neoVoxelRT$alignTo(cachedAlbedo);
                directUpload(aligned, specularGLID);
                if (aligned != newSpec) newSpec.close();
                aligned.close();
            } catch (Exception e) {
                System.err.println("[LiveReuploader] Legacy specular re-upload failed for GLID "
                    + albedoGLID + ": " + e.getMessage());
            }
        }
    }

    // ── Texture array path (per-sprite updates — blocks) ─────────────────────

    private static int specUpdateCount = 0;
    private static int normUpdateCount = 0;

    private static void reuploadTextureArrays(int targetOrdinal) {
        if (BlockModelBridge.sortedSpriteIds.isEmpty()) return;
        int maxSpriteId = BlockModelBridge.sortedSpriteIds.size();

        for (Map.Entry<Integer, Set<Integer>> entry : BlockModelBridge.materialOrdinal2SpriteIds.entrySet()) {
            int ordinal = entry.getKey();
            if (targetOrdinal >= 0 && ordinal != targetOrdinal) continue;
            Set<Integer> spriteIds = entry.getValue();
            if (spriteIds == null || spriteIds.isEmpty()) continue;

            if (ordinal < 0 || ordinal >= Options.materialAutoPBR.length) continue;
            boolean autoPBR = Options.autoPBREnabled && Options.materialAutoPBR[ordinal];

            for (int spriteId : spriteIds) {
                if (spriteId < 0 || spriteId >= maxSpriteId) continue;
                NativeImage spriteAlbedo = TextureTracker.spriteAlbedoCache.get(spriteId);
                if (spriteAlbedo == null) continue;

                try {
                    // ── Specular ──
                    // Skip transmissive blocks — roughness from material class slider, not CPU bake
                    boolean transmissive = Options.materialTransmission[ordinal] > 0;
                    boolean generatedSpecular = spriteId < TextureTracker.spriteSpecularSource.length
                        && TextureTracker.spriteSpecularSource[spriteId] != TextureTracker.SOURCE_PACK_AUTHORED
                        && TextureTracker.spriteSpecularSource[spriteId] != TextureTracker.SOURCE_USER_CUSTOM;
                    if (Options.materialSpecularInputType[ordinal] == 0 && !transmissive && generatedSpecular) {
                        NativeImage specImg;
                        if (autoPBR) {
                            specImg = AutoPBRGenerator.generateSpecularPercentile(spriteAlbedo,
                                Options.materialAutoPBRRoughnessMin[ordinal],
                                Options.materialAutoPBRRoughnessMax[ordinal],
                                Options.materialPercentileCenter[ordinal],
                                Options.materialPercentileSpread[ordinal],
                                (Options.materialAutoPBRFlags[ordinal] & 1) != 0);
                        } else {
                            specImg = spriteAlbedo.applyToCopy(i -> 0);
                        }
                        long specPtr = ((com.radiance.mixin_related.extensions.vulkan_render_integration.INativeImageExt)
                            (Object) specImg).neoVoxelRT$getPointer();
                        int specBytes = specImg.getWidth() * specImg.getHeight() * 4;
                        BlockModelBridge.nativeUpdateSpecularLayer(spriteId, specPtr, specBytes);
                        specUpdateCount++;
                        specImg.close();
                    }

                    // ── Normal ──
                    boolean generatedNormal = spriteId < TextureTracker.spriteNormalSource.length
                        && TextureTracker.spriteNormalSource[spriteId] != TextureTracker.SOURCE_PACK_AUTHORED
                        && TextureTracker.spriteNormalSource[spriteId] != TextureTracker.SOURCE_USER_CUSTOM;
                    if (Options.materialNormalInputType[ordinal] == 0 && generatedNormal) {
                        NativeImage normImg;
                        if (autoPBR) {
                            // Neutral strength for texture array — shader applies pack5.w at runtime
                            normImg = AutoPBRGenerator.generateNormal(spriteAlbedo,
                                100,
                                (Options.materialAutoPBRFlags[ordinal] & 2) != 0,
                                Options.materialAutoPBRHeightGamma[ordinal],
                                (Options.materialAutoPBRFlags[ordinal] & 4) != 0,
                                AutoPBRGenerator.HeightParams.fromOptions(ordinal),
                                Options.materialPomAOStrength[ordinal]);
                        } else {
                            normImg = spriteAlbedo.applyToCopy(i -> (128 << 24) | (128 << 16) | (128 << 8) | 255);
                        }
                        long normPtr = ((com.radiance.mixin_related.extensions.vulkan_render_integration.INativeImageExt)
                            (Object) normImg).neoVoxelRT$getPointer();
                        int normBytes = normImg.getWidth() * normImg.getHeight() * 4;
                        BlockModelBridge.nativeUpdateNormalLayer(spriteId, normPtr, normBytes);
                        normUpdateCount++;
                        normImg.close();
                    }
                } catch (Exception e) {
                    System.err.println("[LiveReuploader] Texture array re-upload failed for sprite "
                        + spriteId + " ordinal " + ordinal + ": " + e.getMessage());
                }
            }
        }
    }

    private static boolean isAutoPBRActiveForGLID(int albedoGLID) {
        Integer ordinal = TextureTracker.albedoGLID2BlockOrdinal.get(albedoGLID);
        if (ordinal == null) return false;
        return Options.autoPBREnabled && Options.materialAutoPBR[ordinal];
    }

    private static boolean needsGeneratedReupload(int ordinal, boolean specular, boolean normal) {
        if ((!specular && !normal) || BlockModelBridge.materialOrdinal2SpriteIds.isEmpty()) {
            return true;
        }

        if (ordinal < 0) {
            for (int candidate : BlockModelBridge.materialOrdinal2SpriteIds.keySet()) {
                if (needsGeneratedReupload(candidate, specular, normal)) return true;
            }
            return false;
        }
        if (ordinal >= Options.materialAutoPBR.length) return false;

        Set<Integer> spriteIds = BlockModelBridge.materialOrdinal2SpriteIds.get(ordinal);
        if (spriteIds == null || spriteIds.isEmpty()) return true;

        for (int spriteId : spriteIds) {
            if (spriteId < 0 || spriteId >= TextureTracker.MAX_TEXTURES) continue;
            if (specular && Options.materialSpecularInputType[ordinal] == 0
                && TextureTracker.spriteSpecularSource[spriteId] != TextureTracker.SOURCE_PACK_AUTHORED
                && TextureTracker.spriteSpecularSource[spriteId] != TextureTracker.SOURCE_USER_CUSTOM) {
                return true;
            }
            if (normal && Options.materialNormalInputType[ordinal] == 0
                && TextureTracker.spriteNormalSource[spriteId] != TextureTracker.SOURCE_PACK_AUTHORED
                && TextureTracker.spriteNormalSource[spriteId] != TextureTracker.SOURCE_USER_CUSTOM) {
                return true;
            }
        }
        return false;
    }

    /**
     * Upload pixel data directly to a Vulkan texture slot, bypassing the NativeImage mixin pipeline.
     */
    private static void directUpload(NativeImage image, int targetGLID) {
        long pointer = ((com.radiance.mixin_related.extensions.vulkan_render_integration.INativeImageExt) (Object) image)
            .neoVoxelRT$getPointer();
        int w = image.getWidth();
        int h = image.getHeight();
        int sizeBytes = w * h * image.getFormat().getChannelCount();
        TextureProxy.queueUpload(pointer, sizeBytes, w, targetGLID, 0, 0, 0, 0, w, h, 0);
    }
}
