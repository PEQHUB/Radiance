package com.radiance.mixin_related.extensions.vulkan_render_integration;

import net.minecraft.client.texture.NativeImage;

public interface INativeImageExt {

    void neoVoxelRT$loadFromTextureImageWithoutUI(int level, boolean removeAlpha);

    NativeImage neoVoxelRT$alignTo(NativeImage template);

    long neoVoxelRT$getPointer();
}
