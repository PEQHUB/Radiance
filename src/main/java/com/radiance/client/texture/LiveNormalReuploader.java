package com.radiance.client.texture;

import com.radiance.client.proxy.vulkan.BufferProxy;
import com.radiance.client.proxy.vulkan.TextureProxy;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
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
        reuploadNormals();
        reuploadSpeculars();
        // Don't call performQueuedUpload() here — the queued uploads will be
        // flushed by the regular submitCommand() path on the next frame.
        // Calling it here risks double-processing (upload queue isn't cleared
        // after processing) and command buffer state corruption if this runs
        // before acquireContext() has begun the upload command buffer.
    }

    private static void reuploadNormals() {
        for (int albedoGLID : TextureTracker.autoPBRNormalGLIDs) {
            NativeImage cachedAlbedo = TextureTracker.materialBlockAlbedoCache.get(albedoGLID);
            if (cachedAlbedo == null) continue;

            Integer normalGLID = TextureTracker.GLID2NormalGLID.get(albedoGLID);
            if (normalGLID == null) continue;

            try {
                NativeImage newNormal = AutoPBRGenerator.generateNormal(cachedAlbedo);
                NativeImage aligned = ((com.radiance.mixin_related.extensions.vulkan_render_integration.INativeImageExt) (Object) newNormal)
                    .neoVoxelRT$alignTo(cachedAlbedo);

                directUpload(aligned, normalGLID);

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

            Integer specularGLID = TextureTracker.GLID2SpecularGLID.get(albedoGLID);
            if (specularGLID == null) continue;

            try {
                NativeImage newSpec = AutoPBRGenerator.generateSpecular(cachedAlbedo);
                NativeImage aligned = ((com.radiance.mixin_related.extensions.vulkan_render_integration.INativeImageExt) (Object) newSpec)
                    .neoVoxelRT$alignTo(cachedAlbedo);

                directUpload(aligned, specularGLID);

                if (aligned != newSpec) newSpec.close();
                aligned.close();
            } catch (Exception e) {
                System.err.println("[LiveReuploader] Specular re-upload failed for GLID "
                    + albedoGLID + ": " + e.getMessage());
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
