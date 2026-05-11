package com.radiance.mixins.vulkan_render_integration;

import com.radiance.mixin_related.extensions.vulkan_render_integration.IParticleExt;
import net.minecraft.client.particle.BillboardParticle;
import net.minecraft.client.particle.WhiteAshParticle;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.util.math.MathHelper;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BillboardParticle.class)
public abstract class BillboardParticleMixins {

    @Unique
    private static final ThreadLocal<Quaternionf> QUATERNION = ThreadLocal.withInitial(
        Quaternionf::new);

    @Unique
    private static final ThreadLocal<Vector3f> VERTEX = ThreadLocal.withInitial(Vector3f::new);

    @Inject(method = "render(Lnet/minecraft/client/render/VertexConsumer;Lnet/minecraft/client/render/Camera;F)V",
        at = @At(value = "HEAD"),
        cancellable = true)
    private void renderNoAlloc(VertexConsumer vertexConsumer, Camera camera, float tickDelta,
        CallbackInfo ci) {
        IParticleExt particle = (IParticleExt) this;
        Quaternionf quaternion = QUATERNION.get().identity();
        this.getRotator().setRotation(quaternion, camera, tickDelta);
        float angle = particle.neoVoxelRT$getAngle();
        if (angle != 0.0F) {
            quaternion.rotateZ(MathHelper.lerp(tickDelta, particle.neoVoxelRT$getPrevAngle(), angle));
        }

        this.method_60373(vertexConsumer, camera, quaternion, tickDelta);
        ci.cancel();
    }

    @Inject(method = "method_60375(Lnet/minecraft/client/render/VertexConsumer;Lorg/joml/Quaternionf;FFFFFFFFI)V",
        at = @At(value = "HEAD"),
        cancellable = true)
    private void writeVertexNoAlloc(VertexConsumer vertexConsumer,
        Quaternionf quaternion,
        float x,
        float y,
        float z,
        float xCorner,
        float yCorner,
        float size,
        float u,
        float v,
        int light,
        CallbackInfo ci) {
        Vector3f vertex = VERTEX.get()
            .set(xCorner, yCorner, 0.0F)
            .rotate(quaternion)
            .mul(size)
            .add(x, y, z);

        IParticleExt particle = (IParticleExt) this;
        vertexConsumer.vertex(vertex.x(), vertex.y(), vertex.z())
            .texture(u, v)
            .color(particle.neoVoxelRT$getRed(),
                particle.neoVoxelRT$getGreen(),
                particle.neoVoxelRT$getBlue(),
                particle.neoVoxelRT$getAlpha())
            .light(light);
        ci.cancel();
    }

    @Inject(method = "method_60374(Lnet/minecraft/client/render/VertexConsumer;Lorg/joml/Quaternionf;FFFF)V",
        at = @At(value = "HEAD"),
        cancellable = true)
    public void resizeParticle(VertexConsumer vertexConsumer,
        Quaternionf quaternionf,
        float f,
        float g,
        float h,
        float i,
        CallbackInfo ci) {
        if (((BillboardParticle) (Object) this) instanceof WhiteAshParticle) {
            float j = this.getSize(i);
            float k = this.getMinU();
            float l = this.getMaxU();
            float m = this.getMinV();
            float n = this.getMaxV();
            int o = 0;
            this.method_60375(vertexConsumer, quaternionf, f, g, h, 1.0F / 8.0F, -1.0F / 8.0F, j, l,
                n, o);
            this.method_60375(vertexConsumer, quaternionf, f, g, h, 1.0F / 8.0F, 1.0F / 8.0F, j, l,
                m, o);
            this.method_60375(vertexConsumer, quaternionf, f, g, h, -1.0F / 8.0F, 1.0F / 8.0F, j, k,
                m, o);
            this.method_60375(vertexConsumer, quaternionf, f, g, h, -1.0F / 8.0F, -1.0F / 8.0F, j,
                k, n, o);

            ci.cancel();
        }
    }

    @Shadow
    public abstract float getSize(float i);

    @Shadow
    public abstract BillboardParticle.Rotator getRotator();

    @Shadow
    protected abstract void method_60373(VertexConsumer vertexConsumer,
        Camera camera,
        Quaternionf quaternion,
        float tickDelta);

    @Shadow
    protected abstract float getMinU();

    @Shadow
    protected abstract float getMaxU();

    @Shadow
    protected abstract float getMinV();

    @Shadow
    protected abstract float getMaxV();

    @Shadow
    protected abstract void method_60375(VertexConsumer vertexConsumer,
        Quaternionf quaternionf,
        float f,
        float g,
        float h,
        float i,
        float j,
        float k,
        float l,
        float m,
        int n);
}
