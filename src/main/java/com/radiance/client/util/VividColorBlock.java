package com.radiance.client.util;

import net.minecraft.block.Block;
import net.minecraft.block.Blocks;

import java.util.HashSet;
import java.util.Set;

/**
 * Identifies blocks with rich, saturated colors that benefit from the
 * color expansion slider. When flagged, the GPU CHS applies an Oklab
 * chroma boost controlled by the colorExpansion push constant.
 *
 * The flag is encoded in bit 16 of the emissiveBlockType vertex attribute.
 */
public final class VividColorBlock {

    private static final Set<Block> VIVID_BLOCKS = new HashSet<>();

    static {
        // Flowers
        VIVID_BLOCKS.add(Blocks.POPPY);
        VIVID_BLOCKS.add(Blocks.BLUE_ORCHID);
        VIVID_BLOCKS.add(Blocks.ALLIUM);
        VIVID_BLOCKS.add(Blocks.AZURE_BLUET);
        VIVID_BLOCKS.add(Blocks.RED_TULIP);
        VIVID_BLOCKS.add(Blocks.ORANGE_TULIP);
        VIVID_BLOCKS.add(Blocks.WHITE_TULIP);
        VIVID_BLOCKS.add(Blocks.PINK_TULIP);
        VIVID_BLOCKS.add(Blocks.OXEYE_DAISY);
        VIVID_BLOCKS.add(Blocks.CORNFLOWER);
        VIVID_BLOCKS.add(Blocks.LILY_OF_THE_VALLEY);
        VIVID_BLOCKS.add(Blocks.TORCHFLOWER);
        VIVID_BLOCKS.add(Blocks.PITCHER_PLANT);
        VIVID_BLOCKS.add(Blocks.SUNFLOWER);
        VIVID_BLOCKS.add(Blocks.LILAC);
        VIVID_BLOCKS.add(Blocks.ROSE_BUSH);
        VIVID_BLOCKS.add(Blocks.PEONY);
        VIVID_BLOCKS.add(Blocks.WITHER_ROSE);
        VIVID_BLOCKS.add(Blocks.DANDELION);
        VIVID_BLOCKS.add(Blocks.PINK_PETALS);

        // Coral blocks
        VIVID_BLOCKS.add(Blocks.TUBE_CORAL_BLOCK);
        VIVID_BLOCKS.add(Blocks.BRAIN_CORAL_BLOCK);
        VIVID_BLOCKS.add(Blocks.BUBBLE_CORAL_BLOCK);
        VIVID_BLOCKS.add(Blocks.FIRE_CORAL_BLOCK);
        VIVID_BLOCKS.add(Blocks.HORN_CORAL_BLOCK);
        VIVID_BLOCKS.add(Blocks.TUBE_CORAL);
        VIVID_BLOCKS.add(Blocks.BRAIN_CORAL);
        VIVID_BLOCKS.add(Blocks.BUBBLE_CORAL);
        VIVID_BLOCKS.add(Blocks.FIRE_CORAL);
        VIVID_BLOCKS.add(Blocks.HORN_CORAL);
        VIVID_BLOCKS.add(Blocks.TUBE_CORAL_FAN);
        VIVID_BLOCKS.add(Blocks.BRAIN_CORAL_FAN);
        VIVID_BLOCKS.add(Blocks.BUBBLE_CORAL_FAN);
        VIVID_BLOCKS.add(Blocks.FIRE_CORAL_FAN);
        VIVID_BLOCKS.add(Blocks.HORN_CORAL_FAN);

        // Wool (16 colors)
        VIVID_BLOCKS.add(Blocks.WHITE_WOOL);
        VIVID_BLOCKS.add(Blocks.ORANGE_WOOL);
        VIVID_BLOCKS.add(Blocks.MAGENTA_WOOL);
        VIVID_BLOCKS.add(Blocks.LIGHT_BLUE_WOOL);
        VIVID_BLOCKS.add(Blocks.YELLOW_WOOL);
        VIVID_BLOCKS.add(Blocks.LIME_WOOL);
        VIVID_BLOCKS.add(Blocks.PINK_WOOL);
        VIVID_BLOCKS.add(Blocks.GRAY_WOOL);
        VIVID_BLOCKS.add(Blocks.LIGHT_GRAY_WOOL);
        VIVID_BLOCKS.add(Blocks.CYAN_WOOL);
        VIVID_BLOCKS.add(Blocks.PURPLE_WOOL);
        VIVID_BLOCKS.add(Blocks.BLUE_WOOL);
        VIVID_BLOCKS.add(Blocks.BROWN_WOOL);
        VIVID_BLOCKS.add(Blocks.GREEN_WOOL);
        VIVID_BLOCKS.add(Blocks.RED_WOOL);
        VIVID_BLOCKS.add(Blocks.BLACK_WOOL);

        // Concrete (16 colors)
        VIVID_BLOCKS.add(Blocks.WHITE_CONCRETE);
        VIVID_BLOCKS.add(Blocks.ORANGE_CONCRETE);
        VIVID_BLOCKS.add(Blocks.MAGENTA_CONCRETE);
        VIVID_BLOCKS.add(Blocks.LIGHT_BLUE_CONCRETE);
        VIVID_BLOCKS.add(Blocks.YELLOW_CONCRETE);
        VIVID_BLOCKS.add(Blocks.LIME_CONCRETE);
        VIVID_BLOCKS.add(Blocks.PINK_CONCRETE);
        VIVID_BLOCKS.add(Blocks.GRAY_CONCRETE);
        VIVID_BLOCKS.add(Blocks.LIGHT_GRAY_CONCRETE);
        VIVID_BLOCKS.add(Blocks.CYAN_CONCRETE);
        VIVID_BLOCKS.add(Blocks.PURPLE_CONCRETE);
        VIVID_BLOCKS.add(Blocks.BLUE_CONCRETE);
        VIVID_BLOCKS.add(Blocks.BROWN_CONCRETE);
        VIVID_BLOCKS.add(Blocks.GREEN_CONCRETE);
        VIVID_BLOCKS.add(Blocks.RED_CONCRETE);
        VIVID_BLOCKS.add(Blocks.BLACK_CONCRETE);

        // Stained glass (16 colors)
        VIVID_BLOCKS.add(Blocks.WHITE_STAINED_GLASS);
        VIVID_BLOCKS.add(Blocks.ORANGE_STAINED_GLASS);
        VIVID_BLOCKS.add(Blocks.MAGENTA_STAINED_GLASS);
        VIVID_BLOCKS.add(Blocks.LIGHT_BLUE_STAINED_GLASS);
        VIVID_BLOCKS.add(Blocks.YELLOW_STAINED_GLASS);
        VIVID_BLOCKS.add(Blocks.LIME_STAINED_GLASS);
        VIVID_BLOCKS.add(Blocks.PINK_STAINED_GLASS);
        VIVID_BLOCKS.add(Blocks.GRAY_STAINED_GLASS);
        VIVID_BLOCKS.add(Blocks.LIGHT_GRAY_STAINED_GLASS);
        VIVID_BLOCKS.add(Blocks.CYAN_STAINED_GLASS);
        VIVID_BLOCKS.add(Blocks.PURPLE_STAINED_GLASS);
        VIVID_BLOCKS.add(Blocks.BLUE_STAINED_GLASS);
        VIVID_BLOCKS.add(Blocks.BROWN_STAINED_GLASS);
        VIVID_BLOCKS.add(Blocks.GREEN_STAINED_GLASS);
        VIVID_BLOCKS.add(Blocks.RED_STAINED_GLASS);
        VIVID_BLOCKS.add(Blocks.BLACK_STAINED_GLASS);

        // Glazed terracotta (16 colors)
        VIVID_BLOCKS.add(Blocks.WHITE_GLAZED_TERRACOTTA);
        VIVID_BLOCKS.add(Blocks.ORANGE_GLAZED_TERRACOTTA);
        VIVID_BLOCKS.add(Blocks.MAGENTA_GLAZED_TERRACOTTA);
        VIVID_BLOCKS.add(Blocks.LIGHT_BLUE_GLAZED_TERRACOTTA);
        VIVID_BLOCKS.add(Blocks.YELLOW_GLAZED_TERRACOTTA);
        VIVID_BLOCKS.add(Blocks.LIME_GLAZED_TERRACOTTA);
        VIVID_BLOCKS.add(Blocks.PINK_GLAZED_TERRACOTTA);
        VIVID_BLOCKS.add(Blocks.GRAY_GLAZED_TERRACOTTA);
        VIVID_BLOCKS.add(Blocks.LIGHT_GRAY_GLAZED_TERRACOTTA);
        VIVID_BLOCKS.add(Blocks.CYAN_GLAZED_TERRACOTTA);
        VIVID_BLOCKS.add(Blocks.PURPLE_GLAZED_TERRACOTTA);
        VIVID_BLOCKS.add(Blocks.BLUE_GLAZED_TERRACOTTA);
        VIVID_BLOCKS.add(Blocks.BROWN_GLAZED_TERRACOTTA);
        VIVID_BLOCKS.add(Blocks.GREEN_GLAZED_TERRACOTTA);
        VIVID_BLOCKS.add(Blocks.RED_GLAZED_TERRACOTTA);
        VIVID_BLOCKS.add(Blocks.BLACK_GLAZED_TERRACOTTA);

        // Copper oxidation states (green patina)
        VIVID_BLOCKS.add(Blocks.OXIDIZED_COPPER);
        VIVID_BLOCKS.add(Blocks.OXIDIZED_CUT_COPPER);
        VIVID_BLOCKS.add(Blocks.WEATHERED_COPPER);
        VIVID_BLOCKS.add(Blocks.WEATHERED_CUT_COPPER);
        VIVID_BLOCKS.add(Blocks.EXPOSED_COPPER);
        VIVID_BLOCKS.add(Blocks.EXPOSED_CUT_COPPER);

        // Concrete powder (16 colors)
        VIVID_BLOCKS.add(Blocks.WHITE_CONCRETE_POWDER);
        VIVID_BLOCKS.add(Blocks.ORANGE_CONCRETE_POWDER);
        VIVID_BLOCKS.add(Blocks.MAGENTA_CONCRETE_POWDER);
        VIVID_BLOCKS.add(Blocks.LIGHT_BLUE_CONCRETE_POWDER);
        VIVID_BLOCKS.add(Blocks.YELLOW_CONCRETE_POWDER);
        VIVID_BLOCKS.add(Blocks.LIME_CONCRETE_POWDER);
        VIVID_BLOCKS.add(Blocks.PINK_CONCRETE_POWDER);
        VIVID_BLOCKS.add(Blocks.GRAY_CONCRETE_POWDER);
        VIVID_BLOCKS.add(Blocks.LIGHT_GRAY_CONCRETE_POWDER);
        VIVID_BLOCKS.add(Blocks.CYAN_CONCRETE_POWDER);
        VIVID_BLOCKS.add(Blocks.PURPLE_CONCRETE_POWDER);
        VIVID_BLOCKS.add(Blocks.BLUE_CONCRETE_POWDER);
        VIVID_BLOCKS.add(Blocks.BROWN_CONCRETE_POWDER);
        VIVID_BLOCKS.add(Blocks.GREEN_CONCRETE_POWDER);
        VIVID_BLOCKS.add(Blocks.RED_CONCRETE_POWDER);
        VIVID_BLOCKS.add(Blocks.BLACK_CONCRETE_POWDER);
    }

    private VividColorBlock() {}

    public static boolean isVivid(Block block) {
        return VIVID_BLOCKS.contains(block);
    }
}
