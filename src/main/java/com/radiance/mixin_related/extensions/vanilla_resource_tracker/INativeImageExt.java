package com.radiance.mixin_related.extensions.vanilla_resource_tracker;

import net.minecraft.client.texture.NativeImage;
import net.minecraft.util.Identifier;

public interface INativeImageExt {

    int neoVoxelRT$getTargetID();

    void neoVoxelRT$setTargetID(int id);

    Identifier neoVoxelRT$getIdentifier();

    void neoVoxelRT$setIdentifier(Identifier id);

    NativeImage neoVoxelRT$getSpecularNativeImage();

    void neoVoxelRT$setSpecularNativeImage(NativeImage image);

    NativeImage neoVoxelRT$getNormalNativeImage();

    void neoVoxelRT$setNormalNativeImage(NativeImage image);

    NativeImage neoVoxelRT$getFlagNativeImage();

    void neoVoxelRT$setFlagNativeImage(NativeImage image);

    int neoVoxelRT$getSpecularUploadedLevelsMask();

    void neoVoxelRT$setSpecularUploadedLevelsMask(int uploadedLevelsMask);

    int neoVoxelRT$getNormalUploadedLevelsMask();

    void neoVoxelRT$setNormalUploadedLevelsMask(int uploadedLevelsMask);

    int neoVoxelRT$getFlagUploadedLevelsMask();

    void neoVoxelRT$setFlagUploadedLevelsMask(int uploadedLevelsMask);
}
