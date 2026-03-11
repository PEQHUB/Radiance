package com.radiance.client.util;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.Direction;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * Maps block states to area light definitions for the RT pipeline.
 * Type IDs must match C++ lights.hpp definitions table.
 * All emissive blocks are registered here so they can participate in the
 * per-block light mode system (Auto / Force Area Light / Force Emissive).
 */
public class LightSourceRegistry {

    // Light type IDs — must match C++ lights.hpp
    public static final int TYPE_TORCH = 0;
    public static final int TYPE_SOUL_TORCH = 1;
    public static final int TYPE_LANTERN = 2;
    public static final int TYPE_SOUL_LANTERN = 3;
    public static final int TYPE_CAMPFIRE = 4;
    public static final int TYPE_SOUL_CAMPFIRE = 5;
    public static final int TYPE_GLOWSTONE = 6;
    public static final int TYPE_SEA_LANTERN = 7;
    public static final int TYPE_SHROOMLIGHT = 8;
    public static final int TYPE_JACK_O_LANTERN = 9;
    public static final int TYPE_END_ROD = 10;
    public static final int TYPE_BEACON = 11;
    public static final int TYPE_OCHRE_FROGLIGHT = 12;
    public static final int TYPE_VERDANT_FROGLIGHT = 13;
    public static final int TYPE_PEARL_FROGLIGHT = 14;
    public static final int TYPE_REDSTONE_TORCH = 15;
    public static final int TYPE_REDSTONE_LAMP = 16;
    public static final int TYPE_CANDLE = 17;
    // IDs 18-20 were formerly CANDLE_2/3/4 (by count) — now consolidated into TYPE_CANDLE
    public static final int TYPE_CAVE_VINES = 21;
    public static final int TYPE_GLOW_LICHEN = 22;
    public static final int TYPE_FURNACE = 23;
    public static final int TYPE_BLAST_FURNACE = 24;
    public static final int TYPE_SMOKER = 25;
    public static final int TYPE_ENDER_CHEST = 26;
    public static final int TYPE_CRYING_OBSIDIAN = 27;
    public static final int TYPE_NETHER_PORTAL = 28;
    public static final int TYPE_CONDUIT = 29;
    public static final int TYPE_RESPAWN_ANCHOR_1 = 30;
    public static final int TYPE_RESPAWN_ANCHOR_2 = 31;
    public static final int TYPE_RESPAWN_ANCHOR_3 = 32;
    public static final int TYPE_RESPAWN_ANCHOR_4 = 33;
    public static final int TYPE_AMETHYST_CLUSTER = 34;
    public static final int TYPE_LARGE_AMETHYST_BUD = 35;
    public static final int TYPE_COPPER_BULB = 36;
    public static final int TYPE_ENCHANTING_TABLE = 37;
    // --- New: formerly emissive-only blocks ---
    public static final int TYPE_LAVA = 38;
    public static final int TYPE_FIRE = 39;
    public static final int TYPE_SOUL_FIRE = 40;
    public static final int TYPE_MAGMA_BLOCK = 41;
    public static final int TYPE_SCULK_SENSOR = 42;
    public static final int TYPE_SCULK_CATALYST = 43;
    public static final int TYPE_SCULK_VEIN = 44;
    public static final int TYPE_SCULK = 45;
    public static final int TYPE_SCULK_SHRIEKER = 46;
    public static final int TYPE_BREWING_STAND = 47;
    public static final int TYPE_END_PORTAL = 48;
    public static final int TYPE_END_PORTAL_FRAME = 49;
    public static final int TYPE_COUNT = 50;

    private static final Map<Block, Function<BlockState, LightSourceDef>> REGISTRY = new HashMap<>();

    // Simple blocks (no state-dependent variants)
    private static final LightSourceDef DEF_TORCH = new LightSourceDef(TYPE_TORCH, 0, 0.12f, 0);
    private static final LightSourceDef DEF_SOUL_TORCH = new LightSourceDef(TYPE_SOUL_TORCH, 0, 0.12f, 0);
    private static final LightSourceDef DEF_LANTERN = new LightSourceDef(TYPE_LANTERN, 0, 0, 0);
    private static final LightSourceDef DEF_SOUL_LANTERN = new LightSourceDef(TYPE_SOUL_LANTERN, 0, 0, 0);
    private static final LightSourceDef DEF_CAMPFIRE = new LightSourceDef(TYPE_CAMPFIRE, 0, 0.1f, 0);
    private static final LightSourceDef DEF_SOUL_CAMPFIRE = new LightSourceDef(TYPE_SOUL_CAMPFIRE, 0, 0.1f, 0);
    private static final LightSourceDef DEF_GLOWSTONE = new LightSourceDef(TYPE_GLOWSTONE, 0, 0, 0);
    private static final LightSourceDef DEF_SEA_LANTERN = new LightSourceDef(TYPE_SEA_LANTERN, 0, 0, 0);
    private static final LightSourceDef DEF_SHROOMLIGHT = new LightSourceDef(TYPE_SHROOMLIGHT, 0, 0, 0);
    private static final LightSourceDef DEF_JACK_O_LANTERN = new LightSourceDef(TYPE_JACK_O_LANTERN, 0, 0, 0);
    private static final LightSourceDef DEF_END_ROD = new LightSourceDef(TYPE_END_ROD, 0, 0, 0);
    private static final LightSourceDef DEF_BEACON = new LightSourceDef(TYPE_BEACON, 0, 0, 0);
    private static final LightSourceDef DEF_OCHRE_FROGLIGHT = new LightSourceDef(TYPE_OCHRE_FROGLIGHT, 0, 0, 0);
    private static final LightSourceDef DEF_VERDANT_FROGLIGHT = new LightSourceDef(TYPE_VERDANT_FROGLIGHT, 0, 0, 0);
    private static final LightSourceDef DEF_PEARL_FROGLIGHT = new LightSourceDef(TYPE_PEARL_FROGLIGHT, 0, 0, 0);
    private static final LightSourceDef DEF_REDSTONE_TORCH = new LightSourceDef(TYPE_REDSTONE_TORCH, 0, 0.12f, 0);
    private static final LightSourceDef DEF_REDSTONE_LAMP = new LightSourceDef(TYPE_REDSTONE_LAMP, 0, 0, 0);
    private static final LightSourceDef DEF_CANDLE = new LightSourceDef(TYPE_CANDLE, 0, 0.15f, 0);
    private static final LightSourceDef DEF_CAVE_VINES = new LightSourceDef(TYPE_CAVE_VINES, 0, 0, 0);
    private static final LightSourceDef DEF_GLOW_LICHEN = new LightSourceDef(TYPE_GLOW_LICHEN, 0, 0, 0);
    private static final LightSourceDef DEF_FURNACE = new LightSourceDef(TYPE_FURNACE, 0, 0, 0);
    private static final LightSourceDef DEF_BLAST_FURNACE = new LightSourceDef(TYPE_BLAST_FURNACE, 0, 0, 0);
    private static final LightSourceDef DEF_SMOKER = new LightSourceDef(TYPE_SMOKER, 0, 0, 0);
    private static final LightSourceDef DEF_ENDER_CHEST = new LightSourceDef(TYPE_ENDER_CHEST, 0, 0, 0);
    private static final LightSourceDef DEF_CRYING_OBSIDIAN = new LightSourceDef(TYPE_CRYING_OBSIDIAN, 0, 0, 0);
    private static final LightSourceDef DEF_NETHER_PORTAL = new LightSourceDef(TYPE_NETHER_PORTAL, 0, 0, 0);
    private static final LightSourceDef DEF_CONDUIT = new LightSourceDef(TYPE_CONDUIT, 0, 0, 0);
    private static final LightSourceDef DEF_RESPAWN_ANCHOR_1 = new LightSourceDef(TYPE_RESPAWN_ANCHOR_1, 0, 0, 0);
    private static final LightSourceDef DEF_RESPAWN_ANCHOR_2 = new LightSourceDef(TYPE_RESPAWN_ANCHOR_2, 0, 0, 0);
    private static final LightSourceDef DEF_RESPAWN_ANCHOR_3 = new LightSourceDef(TYPE_RESPAWN_ANCHOR_3, 0, 0, 0);
    private static final LightSourceDef DEF_RESPAWN_ANCHOR_4 = new LightSourceDef(TYPE_RESPAWN_ANCHOR_4, 0, 0, 0);
    private static final LightSourceDef DEF_AMETHYST_CLUSTER = new LightSourceDef(TYPE_AMETHYST_CLUSTER, 0, 0, 0);
    private static final LightSourceDef DEF_LARGE_AMETHYST_BUD = new LightSourceDef(TYPE_LARGE_AMETHYST_BUD, 0, 0, 0);
    private static final LightSourceDef DEF_COPPER_BULB = new LightSourceDef(TYPE_COPPER_BULB, 0, 0, 0);
    private static final LightSourceDef DEF_ENCHANTING_TABLE = new LightSourceDef(TYPE_ENCHANTING_TABLE, 0, 0.2f, 0);
    // --- New: formerly emissive-only blocks ---
    private static final LightSourceDef DEF_LAVA = new LightSourceDef(TYPE_LAVA, 0, 0, 0);
    private static final LightSourceDef DEF_FIRE = new LightSourceDef(TYPE_FIRE, 0, 0.15f, 0);
    private static final LightSourceDef DEF_SOUL_FIRE = new LightSourceDef(TYPE_SOUL_FIRE, 0, 0.15f, 0);
    private static final LightSourceDef DEF_MAGMA_BLOCK = new LightSourceDef(TYPE_MAGMA_BLOCK, 0, 0, 0);
    private static final LightSourceDef DEF_SCULK_SENSOR = new LightSourceDef(TYPE_SCULK_SENSOR, 0, 0, 0);
    private static final LightSourceDef DEF_SCULK_CATALYST = new LightSourceDef(TYPE_SCULK_CATALYST, 0, 0, 0);
    private static final LightSourceDef DEF_SCULK_VEIN = new LightSourceDef(TYPE_SCULK_VEIN, 0, 0, 0);
    private static final LightSourceDef DEF_SCULK = new LightSourceDef(TYPE_SCULK, 0, 0, 0);
    private static final LightSourceDef DEF_SCULK_SHRIEKER = new LightSourceDef(TYPE_SCULK_SHRIEKER, 0, 0, 0);
    private static final LightSourceDef DEF_BREWING_STAND = new LightSourceDef(TYPE_BREWING_STAND, 0, 0, 0);
    private static final LightSourceDef DEF_END_PORTAL = new LightSourceDef(TYPE_END_PORTAL, 0, 0, 0);
    private static final LightSourceDef DEF_END_PORTAL_FRAME = new LightSourceDef(TYPE_END_PORTAL_FRAME, 0, 0, 0);

    // Wall torch definitions per facing direction (flame ~0.28 blocks from center along facing axis)
    private static final LightSourceDef WALL_TORCH_NORTH = new LightSourceDef(TYPE_TORCH, 0, 0.15f, -0.28f);
    private static final LightSourceDef WALL_TORCH_SOUTH = new LightSourceDef(TYPE_TORCH, 0, 0.15f, 0.28f);
    private static final LightSourceDef WALL_TORCH_EAST  = new LightSourceDef(TYPE_TORCH, 0.28f, 0.15f, 0);
    private static final LightSourceDef WALL_TORCH_WEST  = new LightSourceDef(TYPE_TORCH, -0.28f, 0.15f, 0);

    private static final LightSourceDef SOUL_WALL_TORCH_NORTH = new LightSourceDef(TYPE_SOUL_TORCH, 0, 0.15f, -0.28f);
    private static final LightSourceDef SOUL_WALL_TORCH_SOUTH = new LightSourceDef(TYPE_SOUL_TORCH, 0, 0.15f, 0.28f);
    private static final LightSourceDef SOUL_WALL_TORCH_EAST  = new LightSourceDef(TYPE_SOUL_TORCH, 0.28f, 0.15f, 0);
    private static final LightSourceDef SOUL_WALL_TORCH_WEST  = new LightSourceDef(TYPE_SOUL_TORCH, -0.28f, 0.15f, 0);

    private static final LightSourceDef RS_WALL_TORCH_NORTH = new LightSourceDef(TYPE_REDSTONE_TORCH, 0, 0.15f, -0.28f);
    private static final LightSourceDef RS_WALL_TORCH_SOUTH = new LightSourceDef(TYPE_REDSTONE_TORCH, 0, 0.15f, 0.28f);
    private static final LightSourceDef RS_WALL_TORCH_EAST  = new LightSourceDef(TYPE_REDSTONE_TORCH, 0.28f, 0.15f, 0);
    private static final LightSourceDef RS_WALL_TORCH_WEST  = new LightSourceDef(TYPE_REDSTONE_TORCH, -0.28f, 0.15f, 0);

    static {
        // Torches (always emit)
        register(Blocks.TORCH, state -> DEF_TORCH);
        register(Blocks.WALL_TORCH, LightSourceRegistry::resolveWallTorch);
        register(Blocks.SOUL_TORCH, state -> DEF_SOUL_TORCH);
        register(Blocks.SOUL_WALL_TORCH, LightSourceRegistry::resolveSoulWallTorch);

        // Lanterns (always emit)
        register(Blocks.LANTERN, state -> DEF_LANTERN);
        register(Blocks.SOUL_LANTERN, state -> DEF_SOUL_LANTERN);

        // Campfires (only when lit)
        register(Blocks.CAMPFIRE, s -> s.get(Properties.LIT) ? DEF_CAMPFIRE : null);
        register(Blocks.SOUL_CAMPFIRE, s -> s.get(Properties.LIT) ? DEF_SOUL_CAMPFIRE : null);

        // Full-block emitters (always emit)
        register(Blocks.GLOWSTONE, state -> DEF_GLOWSTONE);
        register(Blocks.SEA_LANTERN, state -> DEF_SEA_LANTERN);
        register(Blocks.SHROOMLIGHT, state -> DEF_SHROOMLIGHT);
        register(Blocks.JACK_O_LANTERN, state -> DEF_JACK_O_LANTERN);
        register(Blocks.OCHRE_FROGLIGHT, state -> DEF_OCHRE_FROGLIGHT);
        register(Blocks.VERDANT_FROGLIGHT, state -> DEF_VERDANT_FROGLIGHT);
        register(Blocks.PEARLESCENT_FROGLIGHT, state -> DEF_PEARL_FROGLIGHT);

        // Point/medium emitters (always emit)
        register(Blocks.END_ROD, state -> DEF_END_ROD);
        register(Blocks.BEACON, state -> DEF_BEACON);
        register(Blocks.ENDER_CHEST, state -> DEF_ENDER_CHEST);
        register(Blocks.CRYING_OBSIDIAN, state -> DEF_CRYING_OBSIDIAN);
        register(Blocks.NETHER_PORTAL, state -> DEF_NETHER_PORTAL);
        register(Blocks.CONDUIT, state -> DEF_CONDUIT);
        register(Blocks.ENCHANTING_TABLE, state -> DEF_ENCHANTING_TABLE);
        register(Blocks.GLOW_LICHEN, state -> DEF_GLOW_LICHEN);
        register(Blocks.AMETHYST_CLUSTER, state -> DEF_AMETHYST_CLUSTER);
        register(Blocks.LARGE_AMETHYST_BUD, state -> DEF_LARGE_AMETHYST_BUD);

        // Redstone torch (only when lit — unlit redstone torches exist)
        register(Blocks.REDSTONE_TORCH, s -> s.get(Properties.LIT) ? DEF_REDSTONE_TORCH : null);
        register(Blocks.REDSTONE_WALL_TORCH, LightSourceRegistry::resolveRedstoneWallTorch);

        // Redstone lamp (only when lit)
        register(Blocks.REDSTONE_LAMP, s -> s.get(Properties.LIT) ? DEF_REDSTONE_LAMP : null);

        // Furnaces (only when lit)
        register(Blocks.FURNACE, s -> s.get(Properties.LIT) ? DEF_FURNACE : null);
        register(Blocks.BLAST_FURNACE, s -> s.get(Properties.LIT) ? DEF_BLAST_FURNACE : null);
        register(Blocks.SMOKER, s -> s.get(Properties.LIT) ? DEF_SMOKER : null);

        // Cave vines (only with berries)
        register(Blocks.CAVE_VINES, s -> s.get(Properties.BERRIES) ? DEF_CAVE_VINES : null);
        register(Blocks.CAVE_VINES_PLANT, s -> s.get(Properties.BERRIES) ? DEF_CAVE_VINES : null);

        // Respawn anchor (by charge count, 0 = no light)
        register(Blocks.RESPAWN_ANCHOR, LightSourceRegistry::resolveRespawnAnchor);

        // Candles — all 17 color variants (only when lit, intensity by count)
        registerCandles();

        // Copper bulbs — all 8 oxidation/waxed variants (only when lit)
        registerCopperBulbs();

        // --- New: formerly emissive-only blocks ---
        register(Blocks.LAVA, state -> DEF_LAVA);
        register(Blocks.FIRE, state -> DEF_FIRE);
        register(Blocks.SOUL_FIRE, state -> DEF_SOUL_FIRE);
        register(Blocks.MAGMA_BLOCK, state -> DEF_MAGMA_BLOCK);
        register(Blocks.SCULK_SENSOR, state -> DEF_SCULK_SENSOR);
        register(Blocks.SCULK_CATALYST, state -> DEF_SCULK_CATALYST);
        register(Blocks.SCULK_VEIN, state -> DEF_SCULK_VEIN);
        register(Blocks.SCULK, state -> DEF_SCULK);
        register(Blocks.SCULK_SHRIEKER, state -> DEF_SCULK_SHRIEKER);
        register(Blocks.BREWING_STAND, state -> DEF_BREWING_STAND);
        register(Blocks.END_PORTAL, state -> DEF_END_PORTAL);
        register(Blocks.END_PORTAL_FRAME, state -> DEF_END_PORTAL_FRAME);
    }

    public static LightSourceDef getLightSource(BlockState state) {
        Function<BlockState, LightSourceDef> resolver = REGISTRY.get(state.getBlock());
        if (resolver == null) return null;
        return resolver.apply(state);
    }

    public static boolean isLightSource(BlockState state) {
        return REGISTRY.containsKey(state.getBlock());
    }

    // --- Registration helper ---

    private static void register(Block block, Function<BlockState, LightSourceDef> resolver) {
        REGISTRY.put(block, resolver);
    }

    // --- State-dependent resolvers ---

    private static LightSourceDef resolveWallTorch(BlockState state) {
        Direction facing = state.get(Properties.HORIZONTAL_FACING);
        return switch (facing) {
            case NORTH -> WALL_TORCH_NORTH;
            case SOUTH -> WALL_TORCH_SOUTH;
            case EAST -> WALL_TORCH_EAST;
            case WEST -> WALL_TORCH_WEST;
            default -> DEF_TORCH;
        };
    }

    private static LightSourceDef resolveSoulWallTorch(BlockState state) {
        Direction facing = state.get(Properties.HORIZONTAL_FACING);
        return switch (facing) {
            case NORTH -> SOUL_WALL_TORCH_NORTH;
            case SOUTH -> SOUL_WALL_TORCH_SOUTH;
            case EAST -> SOUL_WALL_TORCH_EAST;
            case WEST -> SOUL_WALL_TORCH_WEST;
            default -> DEF_SOUL_TORCH;
        };
    }

    private static LightSourceDef resolveRedstoneWallTorch(BlockState state) {
        if (!state.get(Properties.LIT)) return null;
        Direction facing = state.get(Properties.HORIZONTAL_FACING);
        return switch (facing) {
            case NORTH -> RS_WALL_TORCH_NORTH;
            case SOUTH -> RS_WALL_TORCH_SOUTH;
            case EAST -> RS_WALL_TORCH_EAST;
            case WEST -> RS_WALL_TORCH_WEST;
            default -> DEF_REDSTONE_TORCH;
        };
    }

    private static LightSourceDef resolveCandle(BlockState state) {
        if (!state.get(Properties.LIT)) return null;
        return DEF_CANDLE;
    }

    private static LightSourceDef resolveRespawnAnchor(BlockState state) {
        int charges = state.get(Properties.CHARGES);
        return switch (charges) {
            case 1 -> DEF_RESPAWN_ANCHOR_1;
            case 2 -> DEF_RESPAWN_ANCHOR_2;
            case 3 -> DEF_RESPAWN_ANCHOR_3;
            case 4 -> DEF_RESPAWN_ANCHOR_4;
            default -> null; // 0 charges = no light
        };
    }

    private static void registerCandles() {
        Block[] candleBlocks = {
            Blocks.CANDLE,
            Blocks.WHITE_CANDLE, Blocks.ORANGE_CANDLE, Blocks.MAGENTA_CANDLE,
            Blocks.LIGHT_BLUE_CANDLE, Blocks.YELLOW_CANDLE, Blocks.LIME_CANDLE,
            Blocks.PINK_CANDLE, Blocks.GRAY_CANDLE, Blocks.LIGHT_GRAY_CANDLE,
            Blocks.CYAN_CANDLE, Blocks.PURPLE_CANDLE, Blocks.BLUE_CANDLE,
            Blocks.BROWN_CANDLE, Blocks.GREEN_CANDLE, Blocks.RED_CANDLE,
            Blocks.BLACK_CANDLE
        };
        for (Block block : candleBlocks) {
            register(block, LightSourceRegistry::resolveCandle);
        }
    }

    private static void registerCopperBulbs() {
        Block[] copperBulbs = {
            Blocks.COPPER_BULB, Blocks.EXPOSED_COPPER_BULB,
            Blocks.WEATHERED_COPPER_BULB, Blocks.OXIDIZED_COPPER_BULB,
            Blocks.WAXED_COPPER_BULB, Blocks.WAXED_EXPOSED_COPPER_BULB,
            Blocks.WAXED_WEATHERED_COPPER_BULB, Blocks.WAXED_OXIDIZED_COPPER_BULB
        };
        for (Block block : copperBulbs) {
            register(block, s -> s.get(Properties.LIT) ? DEF_COPPER_BULB : null);
        }
    }
}
