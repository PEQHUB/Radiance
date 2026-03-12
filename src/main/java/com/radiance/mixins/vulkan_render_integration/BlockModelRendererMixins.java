package com.radiance.mixins.vulkan_render_integration;

import com.radiance.client.option.Options;
import com.radiance.client.util.ChunkLightCollector;
import com.radiance.client.util.EmissiveBlock;
import com.radiance.client.util.MaterialBlock;
import com.radiance.client.util.LightSourceDef;
import com.radiance.client.util.LightSourceRegistry;
import com.radiance.client.vertex.PBRVertexConsumer;

import com.radiance.mixin_related.extensions.vulkan_render_integration.IBlockColorsExt;
import net.minecraft.block.BlockState;
import net.minecraft.client.color.block.BlockColors;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.block.BlockModelRenderer;
import net.minecraft.client.render.model.BakedQuad;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockRenderView;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BlockModelRenderer.class)
public class BlockModelRendererMixins {

    @Final
    @Shadow
    private BlockColors colors;

    private static final ThreadLocal<float[]> BRIGHTNESS_BUFFER = ThreadLocal.withInitial(() -> new float[4]);
    private static final ThreadLocal<int[]> LIGHT_BUFFER = ThreadLocal.withInitial(() -> new int[4]);

    @Inject(method =
        "renderQuad(Lnet/minecraft/world/BlockRenderView;Lnet/minecraft/block/BlockState;Lnet/minecraft/util/math/BlockPos;"
            +
            "Lnet/minecraft/client/render/VertexConsumer;Lnet/minecraft/client/util/math/MatrixStack$Entry;"
            +
            "Lnet/minecraft/client/render/model/BakedQuad;FFFFIIIII)V",
        at = @At(value = "HEAD"),
        cancellable = true)
    public void redirectRenderQuad(BlockRenderView world,
        BlockState state,
        BlockPos pos,
        VertexConsumer vertexConsumer,
        MatrixStack.Entry matrixEntry,
        BakedQuad quad,
        float brightness0,
        float brightness1,
        float brightness2,
        float brightness3,
        int light0,
        int light1,
        int light2,
        int light3,
        int overlay,
        CallbackInfo ci) {
        float f;
        float g;
        float h;
        float emission;
        if (quad.hasTint()) {
            int i = this.colors.getColor(state, world, pos, quad.getTintIndex());
            f = (i >> 16 & 0xFF) / 255.0F;
            g = (i >> 8 & 0xFF) / 255.0F;
            h = (i & 0xFF) / 255.0F;

            emission = ((IBlockColorsExt) this.colors).neoVoxelRT$getEmission(state, world, pos,
                quad.getTintIndex());
        } else {
            f = 1.0F;
            g = 1.0F;
            h = 1.0F;

            emission = 0.0F;
        }

        // Convert emission to physical nits (cd/m²)
        // For registered blocks: bake DEFAULT nits (immutable). User scale applied via UBO multiplier.
        float emissionNits;
        EmissiveBlock eb = EmissiveBlock.fromBlock(state.getBlock());
        if (eb != null) {
            // Registered emissive block: DEFAULT surfaceNits (scale applied by shader via UBO)
            emissionNits = eb.getDefaultSurfaceNits();
        } else if (emission > 0.0f) {
            // Non-registered block with tint emission: scale 0-1 to nits (200 nits reference)
            emissionNits = emission * 200.0f;
        } else {
            emissionNits = 0.0f;
        }

        // Look up light source definition for this block (used for area light mode below)
        LightSourceDef lightDef = LightSourceRegistry.getLightSource(state);

        PBRVertexConsumer pbrVertexConsumer = null;
        if (vertexConsumer instanceof PBRVertexConsumer pbr) {
            pbrVertexConsumer = pbr;

            // --- Mutual exclusion: resolve effective light mode ---
            int effectiveMode;

            if (lightDef != null && lightDef.typeId >= 0 && lightDef.typeId < Options.AREA_LIGHT_TYPE_COUNT) {
                int configuredMode = Options.blockLightMode[lightDef.typeId];
                if (configuredMode == Options.LIGHT_MODE_FORCE_AREA) {
                    effectiveMode = Options.LIGHT_MODE_FORCE_AREA;
                } else if (configuredMode == Options.LIGHT_MODE_FORCE_EMISSIVE) {
                    effectiveMode = Options.LIGHT_MODE_FORCE_EMISSIVE;
                } else {
                    // Auto: always prefer area light for registered blocks (ReSTIR handles lighting)
                    effectiveMode = Options.LIGHT_MODE_FORCE_AREA;
                }
            } else {
                // No area light available — emissive only
                effectiveMode = Options.LIGHT_MODE_FORCE_EMISSIVE;
            }

            // Write emissive block type ordinal for UBO multiplier lookup
            if (eb != null) {
                pbrVertexConsumer.setPendingEmissiveBlockType(eb.ordinal());
            }

            // Tag material blocks for physically accurate material override
            MaterialBlock mb = MaterialBlock.fromBlock(state.getBlock());
            if (mb != null) {
                pbrVertexConsumer.setPendingMaterialBlockType(mb.ordinal());
            }

            // Apply based on effective mode
            if (effectiveMode == Options.LIGHT_MODE_FORCE_AREA && lightDef != null) {
                // AREA LIGHT MODE: negative emission = signal to shader to suppress bounce
                // abs(value) used for primary-hit self-glow and bloom
                pbrVertexConsumer.setPendingEmission(-Math.max(emissionNits, 0.001f));
                if (ChunkLightCollector.isActive()) {
                    ChunkLightCollector.addLight(pos, lightDef);
                }
            } else {
                // EMISSIVE MODE: positive emission in nits, no area light collection
                pbrVertexConsumer.setPendingEmission(emissionNits);
                // Area light NOT added to collector — emissive path handles illumination
            }
        }

        float[] brightness = BRIGHTNESS_BUFFER.get();
        brightness[0] = brightness0;
        brightness[1] = brightness1;
        brightness[2] = brightness2;
        brightness[3] = brightness3;

        int[] lights = LIGHT_BUFFER.get();
        lights[0] = light0;
        lights[1] = light1;
        lights[2] = light2;
        lights[3] = light3;

        try {
            vertexConsumer.quad(matrixEntry,
                quad,
                brightness,
                f,
                g,
                h,
                1.0F,
                lights,
                overlay,
                true);
        } finally {
            if (pbrVertexConsumer != null) {
                pbrVertexConsumer.setPendingEmission(0.0F);
                pbrVertexConsumer.setPendingEmissiveBlockType(255);
                pbrVertexConsumer.setPendingMaterialBlockType(255);
            }
        }

        ci.cancel();
    }
}
