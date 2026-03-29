package com.radiance.client.texture;

import com.radiance.client.option.Options;
import com.radiance.client.proxy.vulkan.TextureProxy;
import com.radiance.client.proxy.world.BlockModelBridge;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Re-uploads auto-PBR normal and specular maps into their existing Vulkan texture slots
 * when auto-PBR parameters change. No destroy/recreate — overwrites the same GLID.
 *
 * Bypasses NativeImage.upload() entirely to avoid render thread assertions and
 * AuxiliaryTextures' uploadedLevelsMask. Calls TextureProxy.queueUpload() directly.
 *
 * Uses debounced scheduling so rapid slider adjustments coalesce into a single re-upload.
 */
public final class LiveNormalReuploader {

    private static final ScheduledExecutorService scheduler =
        Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "LiveNormalReuploader");
            t.setDaemon(true);
            return t;
        });

    private static ScheduledFuture<?> pendingReupload;

    private LiveNormalReuploader() {}

    /**
     * Schedule a debounced re-upload of all auto-PBR maps.
     * Coalesces calls within 100ms into a single re-upload on the render thread.
     */
    public static synchronized void scheduleReupload() {
        if (pendingReupload != null && !pendingReupload.isDone()) {
            pendingReupload.cancel(false);
        }
        pendingReupload = scheduler.schedule(() -> {
            MinecraftClient.getInstance().execute(LiveNormalReuploader::reuploadAllAutoPBR);
        }, 100, TimeUnit.MILLISECONDS);
    }

    /**
     * Immediately re-generate and re-upload all auto-PBR normals and speculars.
     * Must be called from the render thread (or via scheduleReupload which posts to it).
     */
    public static void reuploadAllAutoPBR() {
        specUpdateCount = 0;
        normUpdateCount = 0;
        reuploadNormals();
        reuploadSpeculars();
        System.err.println("[LiveReuploader] Reupload complete: " + specUpdateCount
            + " spec layers, " + normUpdateCount + " norm layers");
        // Don't call performQueuedUpload() here — the queued uploads will be
        // flushed by the regular submitCommand() path on the next frame.
        // Calling it here risks double-processing (upload queue isn't cleared
        // after processing) and command buffer state corruption if this runs
        // before acquireContext() has begun the upload command buffer.
    }

    private static boolean isAutoPBRActive(int albedoGLID) {
        Integer ordinal = TextureTracker.albedoGLID2BlockOrdinal.get(albedoGLID);
        if (ordinal == null) return false;
        return Options.autoPBREnabled && Options.materialAutoPBR[ordinal];
    }

    private static void reuploadNormals() {
        for (int albedoGLID : TextureTracker.autoPBRNormalGLIDs) {
            NativeImage cachedAlbedo = TextureTracker.materialBlockAlbedoCache.get(albedoGLID);
            if (cachedAlbedo == null) continue;

            if (albedoGLID < 0 || albedoGLID >= TextureTracker.MAX_TEXTURES) continue;
            int normalGLID = TextureTracker.GLID2NormalGLID[albedoGLID];
            if (normalGLID == -1) continue;

            try {
                NativeImage newNormal;
                if (isAutoPBRActive(albedoGLID)) {
                    Integer ordN = TextureTracker.albedoGLID2BlockOrdinal.get(albedoGLID);
                    if (ordN != null) {
                        newNormal = AutoPBRGenerator.generateNormal(cachedAlbedo,
                            Options.materialNormalStrength[ordN],
                            (Options.materialAutoPBRFlags[ordN] & 2) != 0,
                            Options.materialAutoPBRHeightGamma[ordN],
                            (Options.materialAutoPBRFlags[ordN] & 4) != 0);
                    } else {
                        newNormal = AutoPBRGenerator.generateNormal(cachedAlbedo);
                    }
                } else {
                    // Flat neutral normal (128,128,255 = straight up, no bump)
                    newNormal = cachedAlbedo.applyToCopy(i -> (128 << 24) | (128 << 16) | (128 << 8) | 255);
                }
                NativeImage aligned = ((com.radiance.mixin_related.extensions.vulkan_render_integration.INativeImageExt) (Object) newNormal)
                    .neoVoxelRT$alignTo(cachedAlbedo);

                directUpload(aligned, normalGLID);

                // Update texture array layers with neutral-strength normals
                // (shader applies runtime normal strength from pack5.w)
                Integer ordN2 = TextureTracker.albedoGLID2BlockOrdinal.get(albedoGLID);
                if (ordN2 != null && isAutoPBRActive(albedoGLID)) {
                    NativeImage neutralNormal = AutoPBRGenerator.generateNormal(cachedAlbedo,
                        100, // neutral strength — shader applies pack5.w
                        (Options.materialAutoPBRFlags[ordN2] & 2) != 0,
                        Options.materialAutoPBRHeightGamma[ordN2],
                        (Options.materialAutoPBRFlags[ordN2] & 4) != 0);
                    NativeImage neutralAligned = ((com.radiance.mixin_related.extensions.vulkan_render_integration.INativeImageExt)
                        (Object) neutralNormal).neoVoxelRT$alignTo(cachedAlbedo);
                    updateTextureArrayLayers(albedoGLID, neutralAligned, false);
                    if (neutralAligned != neutralNormal) neutralNormal.close();
                    neutralAligned.close();
                } else {
                    updateTextureArrayLayers(albedoGLID, aligned, false);
                }

                if (aligned != newNormal) newNormal.close();
                aligned.close();
            } catch (Exception e) {
                System.err.println("[LiveReuploader] Normal re-upload failed for GLID "
                    + albedoGLID + ": " + e.getMessage());
            }
        }
    }

    private static void reuploadSpeculars() {
        for (int albedoGLID : TextureTracker.autoPBRSpecularGLIDs) {
            NativeImage cachedAlbedo = TextureTracker.materialBlockAlbedoCache.get(albedoGLID);
            if (cachedAlbedo == null) continue;

            if (albedoGLID < 0 || albedoGLID >= TextureTracker.MAX_TEXTURES) continue;
            int specularGLID = TextureTracker.GLID2SpecularGLID[albedoGLID];
            if (specularGLID == -1) continue;

            try {
                NativeImage newSpec;
                Integer ordinal = TextureTracker.albedoGLID2BlockOrdinal.get(albedoGLID);
                if (isAutoPBRActive(albedoGLID) && ordinal != null) {
                    newSpec = AutoPBRGenerator.generateSpecularPercentile(cachedAlbedo,
                        Options.materialAutoPBRRoughnessMin[ordinal],
                        Options.materialAutoPBRRoughnessMax[ordinal],
                        Options.materialPercentileCenter[ordinal],
                        Options.materialPercentileSpread[ordinal],
                        (Options.materialAutoPBRFlags[ordinal] & 1) != 0);
                } else {
                    // Flat zero specular (no roughness/F0/emission data)
                    newSpec = cachedAlbedo.applyToCopy(i -> 0);
                }
                NativeImage aligned = ((com.radiance.mixin_related.extensions.vulkan_render_integration.INativeImageExt) (Object) newSpec)
                    .neoVoxelRT$alignTo(cachedAlbedo);

                directUpload(aligned, specularGLID);

                // Also update texture array layers for block rendering path
                updateTextureArrayLayers(albedoGLID, aligned, true);

                if (aligned != newSpec) newSpec.close();
                aligned.close();
            } catch (Exception e) {
                System.err.println("[LiveReuploader] Specular re-upload failed for GLID "
                    + albedoGLID + ": " + e.getMessage());
            }
        }
    }

    /**
     * Push a NativeImage to all texture array layers for sprites belonging to the same material block.
     * @param isSpecular true for specular array, false for normal array
     */
    private static int specUpdateCount = 0;
    private static int normUpdateCount = 0;

    private static void updateTextureArrayLayers(int albedoGLID, NativeImage image, boolean isSpecular) {
        // Guard: skip during texture system reset/reload
        if (BlockModelBridge.sortedSpriteIds.isEmpty()) return;

        Integer ordinal = TextureTracker.albedoGLID2BlockOrdinal.get(albedoGLID);
        if (ordinal == null) return;
        Set<Integer> spriteIds = BlockModelBridge.materialOrdinal2SpriteIds.get(ordinal);
        if (spriteIds == null || spriteIds.isEmpty()) return;

        if (isSpecular) specUpdateCount += spriteIds.size();
        else normUpdateCount += spriteIds.size();

        long ptr = ((com.radiance.mixin_related.extensions.vulkan_render_integration.INativeImageExt)
            (Object) image).neoVoxelRT$getPointer();
        int sizeBytes = image.getWidth() * image.getHeight() * 4;
        int maxSpriteId = BlockModelBridge.sortedSpriteIds.size();
        for (int sid : spriteIds) {
            if (sid < 0 || sid >= maxSpriteId) continue; // bounds guard
            if (isSpecular) {
                BlockModelBridge.nativeUpdateSpecularLayer(sid, ptr, sizeBytes);
            } else {
                BlockModelBridge.nativeUpdateNormalLayer(sid, ptr, sizeBytes);
            }
        }
    }

    /**
     * Upload pixel data directly to a Vulkan texture slot, bypassing the NativeImage mixin pipeline.
     * The staging buffer copy happens inside the JNI call, so the NativeImage can be closed after this returns.
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
