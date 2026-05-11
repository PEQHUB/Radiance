package com.radiance.mixin_related.extensions.vulkan_render_integration;

import net.minecraft.client.render.chunk.BlockBufferAllocatorStorage;
import net.minecraft.client.render.chunk.SectionBuilder;
import net.minecraft.client.world.ClientWorld;

public interface IChunkBuilderExt {

    SectionBuilder neoVoxelRT$getSectionBuilder();

    ClientWorld neoVoxelRT$getWorld();

    BlockBufferAllocatorStorage neoVoxelRT$getBuffers();
}
