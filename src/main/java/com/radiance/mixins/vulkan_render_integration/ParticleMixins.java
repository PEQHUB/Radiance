package com.radiance.mixins.vulkan_render_integration;

import com.radiance.mixin_related.extensions.vulkan_render_integration.IParticleExt;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Particle.class)
public class ParticleMixins implements IParticleExt {

    @Unique
    private static final ThreadLocal<BlockPos.Mutable> LIGHT_POS = ThreadLocal.withInitial(
        BlockPos.Mutable::new);

    @Shadow
    protected ClientWorld world;

    @Shadow
    protected double x;

    @Shadow
    protected double y;

    @Shadow
    protected double z;

    @Shadow
    protected float red;

    @Shadow
    protected float green;

    @Shadow
    protected float blue;

    @Shadow
    protected float alpha;

    @Shadow
    protected float angle;

    @Shadow
    protected float prevAngle;

    @Override
    public double neoVoxelRT$getX() {
        return x;
    }

    @Override
    public double neoVoxelRT$getY() {
        return y;
    }

    @Override
    public double neoVoxelRT$getZ() {
        return z;
    }

    @Override
    public float neoVoxelRT$getRed() {
        return red;
    }

    @Override
    public float neoVoxelRT$getGreen() {
        return green;
    }

    @Override
    public float neoVoxelRT$getBlue() {
        return blue;
    }

    @Override
    public float neoVoxelRT$getAlpha() {
        return alpha;
    }

    @Override
    public float neoVoxelRT$getAngle() {
        return angle;
    }

    @Override
    public float neoVoxelRT$getPrevAngle() {
        return prevAngle;
    }

    @Inject(method = "getBrightness(F)I", at = @At(value = "HEAD"), cancellable = true)
    private void optimizeGetBrightness(float tickDelta, CallbackInfoReturnable<Integer> cir) {
        BlockPos.Mutable pos = LIGHT_POS.get().set(this.x, this.y, this.z);
        if (this.world.isChunkLoaded(pos)) {
            cir.setReturnValue(WorldRenderer.getLightmapCoordinates(this.world, pos));
        } else {
            cir.setReturnValue(0);
        }
    }
}
