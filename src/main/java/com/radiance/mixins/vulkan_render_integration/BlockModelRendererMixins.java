package com.radiance.mixins.vulkan_render_integration;

import com.radiance.client.util.EmissiveBlock;
import com.radiance.client.texture.compat.ResourcePackColorPropertiesResolver;
import com.radiance.client.texture.compat.ResourcePackTextureVariantResolver;
import com.radiance.client.vertex.PBRVertexConsumer;

import com.radiance.mixin_related.extensions.vulkan_render_integration.IBlockColorsExt;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.state.property.Properties;
import net.minecraft.client.color.block.BlockColors;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.block.BlockModelRenderer;
import net.minecraft.client.render.model.BakedQuad;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
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

    private static final Identifier GRASS_BLOCK_SIDE =
        Identifier.ofVanilla("block/grass_block_side");
    private static final Identifier GRASS_BLOCK_SIDE_OVERLAY =
        Identifier.ofVanilla("block/grass_block_side_overlay");

    @Final
    @Shadow
    private BlockColors colors;

    private static final ThreadLocal<float[]> BRIGHTNESS_BUFFER = ThreadLocal.withInitial(() -> new float[4]);
    private static final ThreadLocal<int[]> LIGHT_BUFFER = ThreadLocal.withInitial(() -> new int[4]);

    private static boolean isGrassBlockSideBaseQuad(BlockState state, BakedQuad quad) {
        if (state.getBlock() != Blocks.GRASS_BLOCK || quad == null) {
            return false;
        }
        Sprite sprite = quad.getSprite();
        return sprite != null && sprite.getContents() != null &&
            GRASS_BLOCK_SIDE.equals(sprite.getContents().getId());
    }

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
            i = ResourcePackColorPropertiesResolver.resolveBlockColor(state, world, pos,
                quad.getTintIndex(), i);
            f = (i >> 16 & 0xFF) / 255.0F;
            g = (i >> 8 & 0xFF) / 255.0F;
            h = (i & 0xFF) / 255.0F;

            emission = ((IBlockColorsExt) this.colors).neoVoxelRT$getEmission(state, world, pos,
                quad.getTintIndex());
        } else {
            int i = ResourcePackColorPropertiesResolver.resolveBlockColor(state, world, pos,
                -1, 0xFFFFFF);
            f = (i >> 16 & 0xFF) / 255.0F;
            g = (i >> 8 & 0xFF) / 255.0F;
            h = (i & 0xFF) / 255.0F;

            emission = 0.0F;
        }

        boolean foldGrassSideOverlay = isGrassBlockSideBaseQuad(state, quad)
            && !ResourcePackTextureVariantResolver.hasBlockSpriteRule(
                GRASS_BLOCK_SIDE_OVERLAY,
                world,
                state,
                pos,
                quad.getFace());
        if (foldGrassSideOverlay) {
            int i = this.colors.getColor(state, world, pos, 0);
            i = ResourcePackColorPropertiesResolver.resolveBlockColor(state, world, pos, 0, i);
            f = (i >> 16 & 0xFF) / 255.0F;
            g = (i >> 8 & 0xFF) / 255.0F;
            h = (i & 0xFF) / 255.0F;
        }

        // Convert emission to physical nits (cd/m²)
        // For registered blocks: bake DEFAULT nits (immutable). User scale applied via UBO multiplier.
        float emissionNits;
        EmissiveBlock eb = EmissiveBlock.fromBlock(state.getBlock());
        if (eb != null) {
            // Skip emission for blocks with a LIT property when unlit (furnace, blast_furnace, smoker)
            boolean isLit = !state.contains(Properties.LIT) || state.get(Properties.LIT);
            emissionNits = isLit ? eb.getDefaultSurfaceNits() : 0.0f;
        } else if (emission > 0.0f) {
            // Non-registered block with tint emission: scale 0-1 to nits (200 nits reference)
            emissionNits = emission * 200.0f;
        } else {
            emissionNits = 0.0f;
        }

        PBRVertexConsumer pbrVertexConsumer = null;
        if (vertexConsumer instanceof PBRVertexConsumer pbr) {
            pbrVertexConsumer = pbr;
            pbrVertexConsumer.setBase(pos.getX(), pos.getY(), pos.getZ());
            pbrVertexConsumer.setPendingBlockContext(world, state, pos);
            pbrVertexConsumer.pushBlockGeometryContext();
            if (eb != null) {
                pbrVertexConsumer.setPendingEmissiveBlockType(eb.ordinal());
            }
            pbrVertexConsumer.setPendingEmission(emissionNits);
            pbrVertexConsumer.setPendingOverlayAlphaMask(foldGrassSideOverlay);
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
                pbrVertexConsumer.popBlockGeometryContext();
                pbrVertexConsumer.clearPendingBlockContext();
                pbrVertexConsumer.setBase(0.0F, 0.0F, 0.0F);
                pbrVertexConsumer.setPendingEmission(0.0F);
                pbrVertexConsumer.setPendingEmissiveBlockType(255);
                pbrVertexConsumer.setPendingOverlayAlphaMask(false);
            }
        }

        ci.cancel();
    }
}
