package com.radiance.client.autopbr;

import com.radiance.client.proxy.vulkan.TextureArrayBridge;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.block.Block;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.render.model.BakedQuad;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;

public final class AutoPbrUsageIndex {
    private static long builtGeneration = -1;
    private static final Map<Identifier, Integer> USAGE_COUNTS = new HashMap<>();

    private AutoPbrUsageIndex() {
    }

    public static int usageCount(MinecraftClient client, Identifier sprite) {
        ensureBuilt(client);
        return USAGE_COUNTS.getOrDefault(sprite, 0);
    }

    public static void ensureBuilt(MinecraftClient client) {
        long generation = com.radiance.client.proxy.vulkan.TextureArrayBridge.getActiveTextureGeneration();
        if (generation == builtGeneration) return;
        USAGE_COUNTS.clear();
        builtGeneration = generation;
        if (client == null || client.getBakedModelManager() == null
            || TextureArrayBridge.sortedSpriteIds.isEmpty()) {
            return;
        }

        Map<Identifier, Set<Identifier>> users = new HashMap<>();
        Direction[] faces = Direction.values();
        for (Block block : Registries.BLOCK) {
            Identifier blockId = Registries.BLOCK.getId(block);
            for (BlockState state : block.getStateManager().getStates()) {
                if (state.getRenderType() != BlockRenderType.MODEL) continue;
                try {
                    BakedModel model = client.getBakedModelManager().getBlockModels().getModel(state);
                    if (model == null) continue;
                    for (int i = 0; i < 7; i++) {
                        Direction face = i < 6 ? faces[i] : null;
                        List<BakedQuad> quads = model.getQuads(state, face, Random.create(42));
                        for (BakedQuad quad : quads) {
                            if (quad.getSprite() == null) continue;
                            Identifier sprite = quad.getSprite().getContents().getId();
                            users.computeIfAbsent(sprite, ignored -> new HashSet<>()).add(blockId);
                        }
                    }
                } catch (Exception ignored) {
                    // Some dynamic models are not safe to query outside their render path.
                }
            }
        }

        users.forEach((sprite, set) -> USAGE_COUNTS.put(sprite, set.size()));
        addFluidFallback("minecraft:block/water_still", 1);
        addFluidFallback("minecraft:block/water_flow", 1);
        addFluidFallback("minecraft:block/lava_still", 1);
        addFluidFallback("minecraft:block/lava_flow", 1);
    }

    private static void addFluidFallback(String spriteText, int count) {
        Identifier id = Identifier.tryParse(spriteText);
        if (id != null && TextureArrayBridge.sortedSpriteIds.contains(id)) {
            USAGE_COUNTS.putIfAbsent(id, count);
        }
    }
}
