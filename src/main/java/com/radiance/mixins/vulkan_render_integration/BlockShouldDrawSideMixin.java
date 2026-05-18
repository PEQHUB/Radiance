package com.radiance.mixins.vulkan_render_integration;

import com.radiance.client.option.Options;
import com.radiance.client.util.MaterialBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Forces face rendering when an adjacent block has PBR transmission set via the material system.
 * Without this, Minecraft's face culling hides faces between adjacent opaque blocks,
 * making blocks below transmissive blocks invisible.
 *
 * Ice lake handling: same-type transmissive blocks cull normally (no internal faces).
 */
@Mixin(Block.class)
public abstract class BlockShouldDrawSideMixin {

    @Inject(method = "shouldDrawSide", at = @At("HEAD"), cancellable = true)
    private static void onShouldDrawSide(BlockState state, BlockState adjacentState,
            Direction side, CallbackInfoReturnable<Boolean> cir) {
        // Check if the adjacent block (the one potentially occluding) has transmission
        Block adjacentBlock = adjacentState.getBlock();
        MaterialBlock adjacentMb = MaterialBlock.fromBlock(adjacentBlock);
        if (adjacentMb != null) {
            int ordinal = adjacentMb.ordinal();
            if (Options.materialTransmission[ordinal] > 0) {
                // Adjacent block is transmissive — draw this face
                // Exception: same MaterialBlock type (ice lake) — let vanilla culling decide
                MaterialBlock selfMb = MaterialBlock.fromBlock(state.getBlock());
                if (selfMb != adjacentMb) {
                    cir.setReturnValue(true);
                }
            }
        }
    }
}
