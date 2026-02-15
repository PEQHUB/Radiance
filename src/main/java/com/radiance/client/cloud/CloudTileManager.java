package com.radiance.client.cloud;

import com.radiance.client.constant.VulkanConstants;
import com.radiance.client.proxy.vulkan.TextureProxy;
import net.minecraft.client.render.CloudRenderer;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;

public final class CloudTileManager {

    public static final int TILE_HALF_EXTENT = 32;
    public static final int TILE_SIZE = TILE_HALF_EXTENT * 2 + 1; // 65
    public static final float CELL_SIZE = 12.0f;

    private static int cloudMaskTextureId = -1;
    private static ByteBuffer tileMask;

    // Track when the mask contents actually change so we can avoid re-uploading every frame.
    private static long[] lastCellsRef;
    private static int lastMaskCenterCellX = Integer.MIN_VALUE;
    private static int lastMaskCenterCellZ = Integer.MIN_VALUE;
    private static int lastMaskCellsWidth;
    private static int lastMaskCellsHeight;

    private static int centerCellX;
    private static int centerCellZ;
    private static int cellsWidth;
    private static int cellsHeight;
    private static float ticks;

    // Vanilla intra-cell offsets (in blocks, [0,12))
    private static float offsetX;
    private static float offsetZ;

    private static boolean valid;

    private CloudTileManager() {}

    public static void invalidate() {
        valid = false;
        centerCellX = 0;
        centerCellZ = 0;
        cellsWidth = 0;
        cellsHeight = 0;
        ticks = 0.0f;
        offsetX = 0.0f;
        offsetZ = 0.0f;

        lastCellsRef = null;
        lastMaskCenterCellX = Integer.MIN_VALUE;
        lastMaskCenterCellZ = Integer.MIN_VALUE;
        lastMaskCellsWidth = 0;
        lastMaskCellsHeight = 0;
    }

    public static void shutdown() {
        invalidate();
        if (tileMask != null) {
            MemoryUtil.memFree(tileMask);
            tileMask = null;
        }
        // No explicit texture destroy API exists; drop the id so it can't be used after shutdown.
        cloudMaskTextureId = -1;
    }

    public static boolean isValid() {
        return valid && cloudMaskTextureId > 0 && cellsWidth > 0 && cellsHeight > 0;
    }

    public static int getCloudMaskTextureId() {
        return cloudMaskTextureId;
    }

    public static int getCenterCellX() {
        return centerCellX;
    }

    public static int getCenterCellZ() {
        return centerCellZ;
    }

    public static float getPeriodX() {
        return cellsWidth * CELL_SIZE;
    }

    public static float getPeriodZ() {
        return cellsHeight * CELL_SIZE;
    }

    public static float getTicks() {
        return ticks;
    }

    public static float getOffsetX() {
        return offsetX;
    }

    public static float getOffsetZ() {
        return offsetZ;
    }

    private static void ensureTexture() {
        if (cloudMaskTextureId > 0 && tileMask != null) {
            return;
        }

        cloudMaskTextureId = TextureProxy.generateTextureId();
        // RGBA8 mask: R=occupied, G=borderN, B=borderE, A=borderS. West border is read from neighbor's east.
        TextureProxy.prepareImage(cloudMaskTextureId, 1, TILE_SIZE, TILE_SIZE,
            VulkanConstants.VkFormat.VK_FORMAT_R8G8B8A8_UNORM);
        TextureProxy.setFilter(cloudMaskTextureId,
            VulkanConstants.VkFilter.VK_FILTER_NEAREST.getValue(),
            VulkanConstants.VkSamplerMipmapMode.VK_SAMPLER_MIPMAP_MODE_NEAREST.getValue());
        TextureProxy.setClamp(cloudMaskTextureId,
            VulkanConstants.VkSamplerAddressMode.VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE.getValue());

        tileMask = MemoryUtil.memAlloc(TILE_SIZE * TILE_SIZE * 4);
    }

    public static void updateFancyTile(CloudRenderer.CloudCells cells, Vec3d cameraPos, float ticksIn) {
        if (cells == null) {
            invalidate();
            return;
        }

        ensureTexture();

        cellsWidth = cells.width();
        cellsHeight = cells.height();
        ticks = ticksIn;

        // Vanilla mapping (copied from CloudRenderer.renderClouds)
        double d = cameraPos.x + ticksIn * 0.030000001f;
        double e = cameraPos.z + 3.96f;
        double periodX = cellsWidth * CELL_SIZE;
        double periodZ = cellsHeight * CELL_SIZE;
        d -= MathHelper.floor(d / periodX) * periodX;
        e -= MathHelper.floor(e / periodZ) * periodZ;
        int newCenterCellX = MathHelper.floor(d / CELL_SIZE);
        int newCenterCellZ = MathHelper.floor(e / CELL_SIZE);

        centerCellX = newCenterCellX;
        centerCellZ = newCenterCellZ;

        offsetX = (float) (d - centerCellX * CELL_SIZE);
        offsetZ = (float) (e - centerCellZ * CELL_SIZE);

        long[] ls = cells.cells();

        boolean needsUpload = !valid
            || ls != lastCellsRef
            || newCenterCellX != lastMaskCenterCellX
            || newCenterCellZ != lastMaskCenterCellZ
            || cellsWidth != lastMaskCellsWidth
            || cellsHeight != lastMaskCellsHeight;

        if (needsUpload) {
            // Fill 65x65 mask around center (Fancy uses +/-32)
            for (int dz = -TILE_HALF_EXTENT; dz <= TILE_HALF_EXTENT; dz++) {
                int tileZ = dz + TILE_HALF_EXTENT;
                int o = Math.floorMod(centerCellZ + dz, cellsHeight);
                for (int dx = -TILE_HALF_EXTENT; dx <= TILE_HALF_EXTENT; dx++) {
                    int tileX = dx + TILE_HALF_EXTENT;
                    int n = Math.floorMod(centerCellX + dx, cellsWidth);

                    long packed = ls[n + o * cellsWidth];
                    int base = (tileZ * TILE_SIZE + tileX) * 4;
                    if (packed == 0L) {
                        tileMask.put(base, (byte) 0);
                        tileMask.put(base + 1, (byte) 0);
                        tileMask.put(base + 2, (byte) 0);
                        tileMask.put(base + 3, (byte) 0);
                    } else {
                        // Border bits are already computed by vanilla CloudCells.
                        boolean borderN = (packed >> 3 & 1L) != 0L;
                        boolean borderE = (packed >> 2 & 1L) != 0L;
                        boolean borderS = (packed >> 1 & 1L) != 0L;

                        tileMask.put(base, (byte) 255);
                        tileMask.put(base + 1, borderN ? (byte) 255 : (byte) 0);
                        tileMask.put(base + 2, borderE ? (byte) 255 : (byte) 0);
                        tileMask.put(base + 3, borderS ? (byte) 255 : (byte) 0);
                    }
                }
            }

            // Upload mask to Vulkan texture system.
            long ptr = MemoryUtil.memAddress(tileMask);
            int sizeBytes = tileMask.capacity();
            TextureProxy.queueUpload(ptr, sizeBytes, TILE_SIZE, cloudMaskTextureId,
                0, 0, 0, 0, TILE_SIZE, TILE_SIZE, 0);

            lastCellsRef = ls;
            lastMaskCenterCellX = newCenterCellX;
            lastMaskCenterCellZ = newCenterCellZ;
            lastMaskCellsWidth = cellsWidth;
            lastMaskCellsHeight = cellsHeight;
        }

        valid = true;
    }
}
