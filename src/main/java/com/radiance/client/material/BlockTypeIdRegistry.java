package com.radiance.client.material;

import net.minecraft.block.Block;
import java.util.HashMap;
import java.util.Map;

/**
 * Assigns a unique sequential ID to each distinct Block type.
 * Used by the greedy mesher (via bits 17-31 of emissiveBlockType) to prevent
 * merging across block types that share the same atlas texture
 * (e.g., oak_stairs and oak_planks).
 *
 * IDs are 1-based (0 = untagged). 15 bits supports up to 32767 unique blocks.
 */
public class BlockTypeIdRegistry {
    private static final Map<Block, Integer> BLOCK_TO_ID = new HashMap<>();
    private static int nextId = 1;

    public static int getBlockTypeId(Block block) {
        return BLOCK_TO_ID.computeIfAbsent(block, b -> nextId++);
    }

    public static int getRegisteredCount() {
        return nextId - 1;
    }
}
