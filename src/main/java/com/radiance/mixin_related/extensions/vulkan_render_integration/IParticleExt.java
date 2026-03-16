package com.radiance.mixin_related.extensions.vulkan_render_integration;

public interface IParticleExt {

    double neoVoxelRT$getX();

    double neoVoxelRT$getY();

    double neoVoxelRT$getZ();

    float neoVoxelRT$getRed();

    float neoVoxelRT$getGreen();

    float neoVoxelRT$getBlue();

    float neoVoxelRT$getAlpha();

    float neoVoxelRT$getAngle();

    float neoVoxelRT$getPrevAngle();

    void neoVoxelRT$setRed(float r);

    void neoVoxelRT$setGreen(float g);

    void neoVoxelRT$setBlue(float b);
}
