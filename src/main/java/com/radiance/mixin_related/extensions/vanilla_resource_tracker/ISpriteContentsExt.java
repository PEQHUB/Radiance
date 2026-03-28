package com.radiance.mixin_related.extensions.vanilla_resource_tracker;

import net.minecraft.client.texture.NativeImage;

public interface ISpriteContentsExt {

    int neoVoxelRT$getTargetID();

    void neoVoxelRT$setTargetID(int targetID);

    NativeImage neoVoxelRT$getImage();
}
