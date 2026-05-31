package com.radiance.client.material;

import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import java.util.HashMap;
import java.util.Map;

/**
 * Assigns a unique sequential ID to each distinct Block type.
 * Used by the greedy mesher (via bits 17-31 of emissiveBlockType) to prevent
 * merging across block types that share the same atlas texture
 * (e.g., oak_stairs and oak_planks).
 *
 * IDs are 1-based (0 = untagged). Packed IDs use bits 0-13 for identity and
 * bit 14 for exact Minecraft thin plant cards; C++ shifts that bit into
 * emissiveBlockType bit 31.
 */
public class BlockTypeIdRegistry {
    public static final int BLOCK_TYPE_ID_MASK = 0x3FFF;
    public static final int THIN_CUTOUT_PLANT_FLAG = 0x4000;

    private static final Map<Block, Integer> BLOCK_TO_ID = new HashMap<>();
    private static int nextId = 1;

    public static int getBlockTypeId(Block block) {
        return BLOCK_TO_ID.computeIfAbsent(block, b -> nextId++);
    }

    public static int getPackedBlockTypeId(Block block) {
        int id = getBlockTypeId(block) & BLOCK_TYPE_ID_MASK;
        if (isThinCutoutPlant(block)) {
            id |= THIN_CUTOUT_PLANT_FLAG;
        }
        return id;
    }

    public static boolean isThinCutoutPlant(Block block) {
        return block == Blocks.SHORT_GRASS
            || block == Blocks.TALL_GRASS
            || block == Blocks.FERN
            || block == Blocks.LARGE_FERN
            || block == Blocks.DEAD_BUSH;
    }

    public static int getRegisteredCount() {
        return nextId - 1;
    }
}
