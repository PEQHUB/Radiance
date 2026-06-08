package com.radiance.mixin_related.extensions.vulkan_render_integration;

import org.joml.Vector3f;

public interface ILightMapManagerExt {

    float neoVoxelRT$getAmbientLightFactor();

    float neoVoxelRT$getSkyFactor();

    float neoVoxelRT$getBlockFactor();

    boolean neoVoxelRT$isUseBrightLightmap();

    Vector3f neoVoxelRT$getSkyLightColor();

    float neoVoxelRT$getNightVisionFactor();

    float neoVoxelRT$getDarknessScale();

    float neoVoxelRT$getDarkenWorldFactor();

    float neoVoxelRT$getBrightnessFactor();
}
