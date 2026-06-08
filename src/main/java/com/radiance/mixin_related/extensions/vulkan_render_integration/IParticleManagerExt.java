package com.radiance.mixin_related.extensions.vulkan_render_integration;

import java.util.List;
import java.util.Map;
import java.util.Queue;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleTextureSheet;

public interface IParticleManagerExt {

    List<ParticleTextureSheet> neoVoxelRT$getTextureSheets();

    Map<ParticleTextureSheet, Queue<Particle>> neoVoxelRT$getParticles();
}
