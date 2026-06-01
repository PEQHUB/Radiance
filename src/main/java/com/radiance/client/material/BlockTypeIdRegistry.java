package com.radiance.client.material;

import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

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
    private static final Set<Block> THIN_CUTOUT_PLANTS = Set.of(
        Blocks.SHORT_GRASS,
        Blocks.TALL_GRASS,
        Blocks.FERN,
        Blocks.LARGE_FERN,
        Blocks.DEAD_BUSH,
        Blocks.SUGAR_CANE,
        Blocks.OAK_SAPLING,
        Blocks.SPRUCE_SAPLING,
        Blocks.BIRCH_SAPLING,
        Blocks.JUNGLE_SAPLING,
        Blocks.ACACIA_SAPLING,
        Blocks.CHERRY_SAPLING,
        Blocks.DARK_OAK_SAPLING,
        Blocks.PALE_OAK_SAPLING,
        Blocks.MANGROVE_PROPAGULE,
        Blocks.BAMBOO_SAPLING,
        Blocks.DANDELION,
        Blocks.POPPY,
        Blocks.BLUE_ORCHID,
        Blocks.ALLIUM,
        Blocks.AZURE_BLUET,
        Blocks.RED_TULIP,
        Blocks.ORANGE_TULIP,
        Blocks.WHITE_TULIP,
        Blocks.PINK_TULIP,
        Blocks.OXEYE_DAISY,
        Blocks.CORNFLOWER,
        Blocks.LILY_OF_THE_VALLEY,
        Blocks.WITHER_ROSE,
        Blocks.TORCHFLOWER,
        Blocks.OPEN_EYEBLOSSOM,
        Blocks.CLOSED_EYEBLOSSOM,
        Blocks.SUNFLOWER,
        Blocks.LILAC,
        Blocks.ROSE_BUSH,
        Blocks.PEONY,
        Blocks.PITCHER_PLANT,
        Blocks.SPORE_BLOSSOM,
        Blocks.BROWN_MUSHROOM,
        Blocks.RED_MUSHROOM,
        Blocks.AZALEA,
        Blocks.FLOWERING_AZALEA,
        Blocks.CRIMSON_FUNGUS,
        Blocks.WARPED_FUNGUS,
        Blocks.WHEAT,
        Blocks.CARROTS,
        Blocks.POTATOES,
        Blocks.BEETROOTS,
        Blocks.NETHER_WART,
        Blocks.PUMPKIN_STEM,
        Blocks.MELON_STEM,
        Blocks.ATTACHED_PUMPKIN_STEM,
        Blocks.ATTACHED_MELON_STEM,
        Blocks.TORCHFLOWER_CROP,
        Blocks.PITCHER_CROP,
        Blocks.SWEET_BERRY_BUSH,
        Blocks.VINE,
        Blocks.CAVE_VINES,
        Blocks.CAVE_VINES_PLANT,
        Blocks.WEEPING_VINES,
        Blocks.WEEPING_VINES_PLANT,
        Blocks.TWISTING_VINES,
        Blocks.TWISTING_VINES_PLANT,
        Blocks.KELP,
        Blocks.KELP_PLANT,
        Blocks.SEAGRASS,
        Blocks.TALL_SEAGRASS,
        Blocks.HANGING_ROOTS,
        Blocks.CRIMSON_ROOTS,
        Blocks.WARPED_ROOTS,
        Blocks.NETHER_SPROUTS,
        Blocks.PINK_PETALS,
        Blocks.SMALL_DRIPLEAF,
        Blocks.BIG_DRIPLEAF,
        Blocks.BIG_DRIPLEAF_STEM
    );
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
        return THIN_CUTOUT_PLANTS.contains(block);
    }

    public static int getRegisteredCount() {
        return nextId - 1;
    }
}
