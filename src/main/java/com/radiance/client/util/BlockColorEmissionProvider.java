package com.radiance.client.util;

import net.minecraft.block.BlockState;
import net.minecraft.client.color.block.BlockColorProvider;
import net.minecraft.util.Pair;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockRenderView;
import org.jetbrains.annotations.Nullable;

public interface BlockColorEmissionProvider extends BlockColorProvider {

    Pair<Integer, Float> getColorEmission(BlockState state, @Nullable BlockRenderView world,
        @Nullable BlockPos pos, int tintIndex);

    default int getColor(BlockState state, @Nullable BlockRenderView world, @Nullable BlockPos pos,
        int tintIndex) {
        return getColorEmission(state, world, pos, tintIndex).getLeft();
    }

    default float getEmission(BlockState state, @Nullable BlockRenderView world,
        @Nullable BlockPos pos, int tintIndex) {
        return getColorEmission(state, world, pos, tintIndex).getRight();
    }
}
