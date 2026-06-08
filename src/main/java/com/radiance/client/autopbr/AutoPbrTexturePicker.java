package com.radiance.client.autopbr;

import com.radiance.client.proxy.vulkan.TextureArrayBridge;
import java.util.List;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.render.model.BakedQuad;
import net.minecraft.fluid.FluidState;
import net.minecraft.fluid.Fluids;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;

public final class AutoPbrTexturePicker {
    private AutoPbrTexturePicker() {
    }

    public static Identifier pick(MinecraftClient client) {
        if (client == null || client.world == null) return AutoPbrTextureCatalog.fallbackSprite();
        if (!(client.crosshairTarget instanceof BlockHitResult hit) || hit.getType() == HitResult.Type.MISS) {
            return AutoPbrTextureCatalog.fallbackSprite();
        }

        BlockState state = client.world.getBlockState(hit.getBlockPos());
        Identifier fluid = fluidSprite(state.getFluidState(), hit.getSide());
        if (fluid != null
            && AutoPbrTextureCatalog.isEditableSprite(fluid)
            && TextureArrayBridge.sortedSpriteIds.contains(fluid)) {
            return fluid;
        }

        if (state.getRenderType() == BlockRenderType.MODEL) {
            Identifier sprite = modelSprite(client, state, hit.getSide());
            if (sprite != null) return sprite;
        }
        return AutoPbrTextureCatalog.fallbackSprite();
    }

    private static Identifier modelSprite(MinecraftClient client, BlockState state, Direction side) {
        try {
            BakedModel model = client.getBakedModelManager().getBlockModels().getModel(state);
            if (model == null) return null;
            List<BakedQuad> sideQuads = model.getQuads(state, side, Random.create(42));
            Identifier sprite = firstSprite(sideQuads);
            if (sprite != null) return sprite;
            return firstSprite(model.getQuads(state, null, Random.create(42)));
        } catch (Exception ignored) {
            return null;
        }
    }

    private static Identifier firstSprite(List<BakedQuad> quads) {
        for (BakedQuad quad : quads) {
            if (quad.getSprite() == null) continue;
            Identifier sprite = quad.getSprite().getContents().getId();
            if (AutoPbrTextureCatalog.isEditableSprite(sprite)
                && TextureArrayBridge.sortedSpriteIds.contains(sprite)) {
                return sprite;
            }
        }
        return null;
    }

    private static Identifier fluidSprite(FluidState fluid, Direction side) {
        if (fluid == null || fluid.isEmpty()) return null;
        boolean still = side == Direction.UP || side == Direction.DOWN;
        if (fluid.getFluid() == Fluids.WATER || fluid.getFluid() == Fluids.FLOWING_WATER) {
            return null;
        }
        if (fluid.getFluid() == Fluids.LAVA || fluid.getFluid() == Fluids.FLOWING_LAVA) {
            return Identifier.ofVanilla(still ? "block/lava_still" : "block/lava_flow");
        }
        return null;
    }
}
