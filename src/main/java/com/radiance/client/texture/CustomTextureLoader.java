package com.radiance.client.texture;

import com.radiance.client.constant.VulkanConstants;
import com.radiance.client.proxy.vulkan.TextureProxy;
import com.radiance.mixin_related.extensions.vanilla_resource_tracker.INativeImageExt;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.texture.NativeImage;

/**
 * Loads custom PNG textures from disk, allocates Vulkan texture IDs, and uploads.
 * Maintains a cache keyed by file path to avoid redundant allocations.
 * On removal, calls TextureProxy.destroyTexture() to free the Vulkan resource.
 */
public final class CustomTextureLoader {

    private static final Map<String, CachedTexture> cache = new ConcurrentHashMap<>();

    private CustomTextureLoader() {}

    public record CachedTexture(int glid, int width, int height) {}

    /**
     * Load a custom texture from an absolute file path.
     * Returns the allocated GLID, or -1 on failure.
     * Caches by path — repeated calls with the same path return the cached GLID.
     */
    public static int loadAndUpload(String absolutePath, NativeImage alignTo) {
        if (absolutePath == null || absolutePath.isEmpty()) return -1;

        CachedTexture existing = cache.get(absolutePath);
        if (existing != null) return existing.glid;

        Path path = Path.of(absolutePath);
        if (!Files.exists(path)) {
            System.err.println("[CustomTextureLoader] File not found: " + absolutePath);
            return -1;
        }

        try (InputStream is = new FileInputStream(path.toFile());
             NativeImage loaded = NativeImage.read(is)) {

            // Align to source texture dimensions
            NativeImage aligned = ((com.radiance.mixin_related.extensions.vulkan_render_integration.INativeImageExt) (Object) loaded)
                .neoVoxelRT$alignTo(alignTo);

            // Allocate Vulkan texture
            int glid = TextureProxy.generateTextureId();
            TextureProxy.prepareImage(glid, 1, aligned.getWidth(), aligned.getHeight(),
                VulkanConstants.VkFormat.VK_FORMAT_R8G8B8A8_UNORM);
            TextureTracker.GLID2Texture.put(glid,
                new TextureTracker.Texture(aligned.getWidth(), aligned.getHeight(), 4,
                    VulkanConstants.VkFormat.VK_FORMAT_R8G8B8A8_UNORM, 0));

            // Set target and upload
            ((INativeImageExt) (Object) aligned).neoVoxelRT$setTargetID(glid);
            aligned.upload(0, 0, 0, 0, 0, aligned.getWidth(), aligned.getHeight(), false);

            if (aligned != loaded) {
                // loaded already closed by try-with-resources, aligned is the upload image
            }

            cache.put(absolutePath, new CachedTexture(glid, aligned.getWidth(), aligned.getHeight()));
            return glid;

        } catch (IOException e) {
            System.err.println("[CustomTextureLoader] Failed to load: " + absolutePath + ": " + e.getMessage());
            return -1;
        }
    }

    /**
     * Remove a cached texture by path and destroy its Vulkan resource.
     */
    public static void unload(String absolutePath) {
        CachedTexture removed = cache.remove(absolutePath);
        if (removed != null) {
            TextureProxy.destroyTexture(removed.glid);
        }
    }

    /**
     * Unload all cached custom textures.
     */
    public static void unloadAll() {
        for (Map.Entry<String, CachedTexture> entry : cache.entrySet()) {
            TextureProxy.destroyTexture(entry.getValue().glid);
        }
        cache.clear();
    }

    /**
     * Check if a path is already cached.
     */
    public static boolean isCached(String absolutePath) {
        return cache.containsKey(absolutePath);
    }

    /**
     * Get the GLID for a cached path, or -1 if not cached.
     */
    public static int getCachedGlid(String absolutePath) {
        CachedTexture ct = cache.get(absolutePath);
        return ct != null ? ct.glid : -1;
    }
}
