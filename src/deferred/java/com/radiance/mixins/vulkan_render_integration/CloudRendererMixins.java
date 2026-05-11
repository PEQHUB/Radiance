package com.radiance.mixins.vulkan_render_integration;

import com.radiance.client.UnsafeManager;
import com.radiance.client.cloud.CloudTileManager;
import com.radiance.client.proxy.world.EntityProxy;
import com.radiance.client.vertex.StorageVertexConsumerProvider;
import net.minecraft.client.gl.GlUsage;
import net.minecraft.client.gl.VertexBuffer;
import net.minecraft.client.option.CloudRenderMode;
import net.minecraft.client.render.BuiltBuffer;
import net.minecraft.client.render.CloudRenderer;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CloudRenderer.class)
public class CloudRendererMixins {

    @Shadow
    private CloudRenderer.CloudCells cells;

    @Unique
    private StorageVertexConsumerProvider storageVertexConsumerProvider = null;

    @Unique
    private EntityProxy.EntityRenderDataList entityRenderDataList = null;

    @Redirect(method = "<init>", at = @At(value = "NEW", target = "net/minecraft/client/gl/VertexBuffer"))
    private VertexBuffer cancelBufferInit(GlUsage usage) {
        return UnsafeManager.INSTANCE.allocateInstance(VertexBuffer.class);
    }

    @Inject(method =
        "renderClouds(ILnet/minecraft/client/option/CloudRenderMode;FLorg/joml/Matrix4f;Lorg/joml/Matrix4f;"
            +
            "Lnet/minecraft/util/math/Vec3d;F)V", at = @At(value = "HEAD"), cancellable = true)
    public void redirectCloudRendering(int color,
        CloudRenderMode cloudRenderMode,
        float cloudHeight,
        Matrix4f positionMatrix,
        Matrix4f projectionMatrix,
        Vec3d cameraPos,
        float ticks,
        CallbackInfo ci) {
        if (this.cells != null) {
            if (cloudRenderMode == CloudRenderMode.FANCY || cloudRenderMode == CloudRenderMode.FAST) {
                // Both Fast and Fancy use the volumetric slab path.
                // Fast = analytic flat slab (detailStrength forced to 0 by WorldRendererMixins).
                // Fancy = stepped volumetric with 3D FBM density (detailStrength from user setting).
                CloudTileManager.updateFancyTile(this.cells, cameraPos, ticks);

                // Ensure no lingering geometry buffers remain in the TLAS.
                if (storageVertexConsumerProvider != null) {
                    if (entityRenderDataList != null) {
                        for (EntityProxy.EntityRenderData entityRenderData : entityRenderDataList) {
                            for (EntityProxy.EntityRenderLayer entityRenderLayer : entityRenderData) {
                                BuiltBuffer vertexBuffer = entityRenderLayer.builtBuffer();
                                vertexBuffer.close();
                            }
                        }
                    }
                    storageVertexConsumerProvider.close();
                    storageVertexConsumerProvider = null;
                    entityRenderDataList = null;
                }
            } else {
                // OFF: disable all cloud rendering.
                CloudTileManager.invalidate();
                if (storageVertexConsumerProvider != null) {
                    if (entityRenderDataList != null) {
                        for (EntityProxy.EntityRenderData entityRenderData : entityRenderDataList) {
                            for (EntityProxy.EntityRenderLayer entityRenderLayer : entityRenderData) {
                                BuiltBuffer vertexBuffer = entityRenderLayer.builtBuffer();
                                vertexBuffer.close();
                            }
                        }
                    }
                    storageVertexConsumerProvider.close();
                    storageVertexConsumerProvider = null;
                    entityRenderDataList = null;
                }
            }
        } else {
            CloudTileManager.invalidate();
        }

        ci.cancel();
    }

    @Inject(method = "close()V", at = @At(value = "HEAD"), cancellable = true)
    public void cancelBufferClose(CallbackInfo ci) {
        ci.cancel();
    }
}
