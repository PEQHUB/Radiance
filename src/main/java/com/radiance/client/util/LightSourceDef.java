package com.radiance.client.util;

import net.minecraft.util.math.BlockPos;

/**
 * Defines light source properties for a block type or state variant.
 * The typeId must match the corresponding entry in C++ lights.hpp,
 * which stores rendering properties (halfExtent, intensity, radius, tint).
 * The offset positions the light relative to the block center (0.5, 0.5, 0.5).
 */
public class LightSourceDef {
    public final int typeId;
    public final float offsetX, offsetY, offsetZ;

    public LightSourceDef(int typeId, float offsetX, float offsetY, float offsetZ) {
        this.typeId = typeId;
        this.offsetX = offsetX;
        this.offsetY = offsetY;
        this.offsetZ = offsetZ;
    }

    public float getWorldX(BlockPos pos) {
        return pos.getX() + 0.5f + offsetX;
    }

    public float getWorldY(BlockPos pos) {
        return pos.getY() + 0.5f + offsetY;
    }

    public float getWorldZ(BlockPos pos) {
        return pos.getZ() + 0.5f + offsetZ;
    }
}
