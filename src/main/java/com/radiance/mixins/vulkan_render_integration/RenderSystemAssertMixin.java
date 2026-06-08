package com.radiance.mixins.vulkan_render_integration;

import com.mojang.blaze3d.systems.RenderSystem;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Placeholder — UIThread assertion bypass removed.
 * Retained as empty mixin to avoid stale references; can be deleted
 * once confirmed no other code depends on this class name.
 */
@Mixin(RenderSystem.class)
public class RenderSystemAssertMixin {
}
