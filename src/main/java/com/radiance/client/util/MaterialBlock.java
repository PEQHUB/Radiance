package com.radiance.client.util;

import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.registry.Registries;

import java.util.HashMap;
import java.util.Map;

/**
 * Physically accurate metal/gem/mineral block definitions.
 * Each entry stores default F0 (RGB reflectance) and roughness.
 * Metals (isMetal=true): F0 is wavelength-dependent RGB.
 * Dielectrics (isMetal=false): F0 derived from IOR, all channels equal.
 *
 * Values flow: MaterialBlock defaults → Options arrays → WorldUBO.materialData[] → CHS shader.
 * The vertex materialBlockType (packed into upper bits of emissiveBlockType) selects the UBO entry.
 */
public enum MaterialBlock {
    // Category 1: Pure Metals — F0 from Johnson & Christy / Lagarde spectral integration
    // Roughness is perceptual (squared in shader for GGX alpha)
    IRON_BLOCK      ("iron_block",       true,  560, 570, 580, 15, 0),  // J&C 1974, achromatic grey
    GOLD_BLOCK      ("gold_block",       true, 1000, 766, 336, 10, 0),  // J&C 1972, saturated warm
    COPPER_BLOCK    ("copper_block",     true,  955, 637, 538, 10, 0),  // J&C 1972, reddish-orange
    NETHERITE_BLOCK ("netherite_block",  true,  504, 479, 429, 35, 0),  // tungsten (W) — Lagarde/Blender reference, refractory metal
    RAW_IRON_BLOCK  ("raw_iron_block",   true,  560, 570, 580, 80, 0),  // Fe F0, very rough ore matrix
    RAW_GOLD_BLOCK  ("raw_gold_block",   true, 1000, 766, 336, 75, 0),  // Au F0, rough nuggets in stone
    RAW_COPPER_BLOCK("raw_copper_block", true,  955, 637, 538, 80, 0),  // Cu F0, rough ore matrix

    // Category 2: Metal Constructs — inherit parent metal F0
    IRON_BARS       ("iron_bars",        true,  560, 570, 580, 45, 0),  // wrought iron
    CHAIN           ("chain",            true,  560, 570, 580, 40, 0),  // forged iron
    ANVIL           ("anvil",            true,  520, 520, 535, 55, 0),  // cast iron — Fe with ~3% C, achromatic near-iron
    CAULDRON        ("cauldron",         true,  520, 530, 550, 60, 0),  // cast iron pot
    HOPPER          ("hopper",           true,  560, 570, 580, 35, 0),  // sheet iron
    HEAVY_WEIGHTED_PRESSURE_PLATE("heavy_pressure_plate", true, 560, 570, 580, 15, 0),  // polished iron
    LIGHT_WEIGHTED_PRESSURE_PLATE("light_pressure_plate", true, 1000, 766, 336, 10, 0), // polished gold
    BELL            ("bell",             true,  820, 660, 480, 25, 0),  // bronze Cu-Sn (80/20 by weight) — weighted blend of Cu/Sn F0
    LIGHTNING_ROD   ("lightning_rod",    true,  700, 650, 550, 45, 0),  // oxidized copper, patina reduces F0
    IRON_DOOR       ("iron_door",        true,  500, 500, 500, 40, 0),  // coated iron
    RAIL            ("rail",             true,  580, 580, 580, 50, 0),  // steel (Fe-C), achromatic

    // Category 3: Gems & Crystals — dielectric, F0 from IOR
    DIAMOND_BLOCK   ("diamond",          false, 0, 0, 0,  5, 2417),  // well-established IOR
    EMERALD_BLOCK   ("emerald",          false, 0, 0, 0, 15, 1580),  // beryl family
    AMETHYST_BLOCK  ("amethyst",         false, 0, 0, 0, 20, 1544),  // quartz variety
    AMETHYST_CLUSTER("amethyst_cluster", false, 0, 0, 0, 10, 1544),  // cleaner crystal facets
    LAPIS_BLOCK     ("lapis",            false, 0, 0, 0, 50, 1500),  // opaque mineral aggregate

    // Category 4: Minerals
    QUARTZ_BLOCK    ("quartz",           false, 0, 0, 0, 30, 1544),
    OBSIDIAN        ("obsidian",         false, 0, 0, 0, 10, 1500),  // volcanic glass, vitreous
    CRYING_OBSIDIAN ("crying_obsidian",  false, 0, 0, 0, 15, 1500),
    PRISMARINE      ("prismarine",       false, 0, 0, 0, 35, 1577),  // aquamarine analog
    CALCITE         ("calcite",          false, 0, 0, 0, 30, 1486),  // calcium carbonate — ordinary ray IOR, CRC Handbook

    // Category 4b: Glass & Ice
    GLASS           ("glass",            false, 0, 0, 0,  5, 1520),  // soda-lime glass
    ICE             ("ice",              false, 0, 0, 0, 10, 1309),  // water ice Ih

    // Category 5: Special / Mixed
    REDSTONE_BLOCK  ("redstone",         true,  286, 250, 255, 45, 0),  // hematite (α-Fe2O3) — R=((n-1)²+k²)/((n+1)²+k²) at 650/550/450nm
    ANCIENT_DEBRIS  ("ancient_debris",   true,  450, 410, 380, 60, 0),  // dark refractory alloy — molybdenum/iridium-like dark metal
    GILDED_BLACKSTONE("gilded_blackstone", true, 600, 430, 180, 70, 0), // stone with gold veins
    LODESTONE       ("lodestone",        true,  207, 191, 182, 55, 0),  // magnetite Fe3O4, Querry 1985
    SMITHING_TABLE  ("smithing_table",   true,  480, 480, 480, 50, 0);  // dark iron surface

    public static final int COUNT = values().length; // 35

    private final String id;
    private final boolean metal;
    /** Default F0 in permille (0-1000). For dielectrics, computed from IOR. */
    private final int defaultF0R, defaultF0G, defaultF0B;
    /** Default roughness in percent (0-100). */
    private final int defaultRoughness;
    /** Default IOR × 1000 (e.g., 2420 = 2.420). 0 for metals. */
    private final int defaultIOR;

    MaterialBlock(String id, boolean metal, int f0r, int f0g, int f0b, int roughness, int ior) {
        this.id = id;
        this.metal = metal;
        this.defaultRoughness = roughness;
        this.defaultIOR = ior;
        if (!metal && ior > 0) {
            // Compute F0 from IOR: F0 = ((n-1)/(n+1))^2
            float n = ior / 1000.0f;
            float f0 = ((n - 1.0f) / (n + 1.0f));
            f0 = f0 * f0;
            int f0pm = Math.round(f0 * 1000.0f);
            this.defaultF0R = f0pm;
            this.defaultF0G = f0pm;
            this.defaultF0B = f0pm;
        } else {
            this.defaultF0R = f0r;
            this.defaultF0G = f0g;
            this.defaultF0B = f0b;
        }
    }

    public String getId() { return id; }
    public boolean isMetal() { return metal; }
    public int getDefaultF0R() { return defaultF0R; }
    public int getDefaultF0G() { return defaultF0G; }
    public int getDefaultF0B() { return defaultF0B; }
    public int getDefaultRoughness() { return defaultRoughness; }
    public int getDefaultIOR() { return defaultIOR; }

    // Principled BSDF defaults — derived from existing block properties
    public int getDefaultMetallic() { return metal ? 1000 : 0; }
    public int getDefaultTransmission() { return (this == GLASS || this == ICE) ? 1000 : 0; }
    public int getDefaultSubsurface() { return 0; }
    public int getDefaultAnisotropic() { return 0; }
    public int getDefaultSheenWeight() { return 0; }
    public int getDefaultSheenTint() { return 500; }
    public int getDefaultCoatWeight() { return 0; }
    public int getDefaultCoatRoughness() { return 3; }

    /** Convert IOR (×1000) to F0 permille. */
    public static int iorToF0Permille(int iorTimes1000) {
        float n = iorTimes1000 / 1000.0f;
        float r = (n - 1.0f) / (n + 1.0f);
        return Math.round(r * r * 1000.0f);
    }

    // ====== Block Registration ======

    private static final Map<Block, MaterialBlock> BLOCK_MAP = new HashMap<>();

    static {
        // Pure Metals
        register(Blocks.IRON_BLOCK, IRON_BLOCK);
        register(Blocks.GOLD_BLOCK, GOLD_BLOCK);
        // Copper: all oxidation/waxed/cut/door/trapdoor/grate/chiseled variants
        register(Blocks.COPPER_BLOCK, COPPER_BLOCK);
        register(Blocks.EXPOSED_COPPER, COPPER_BLOCK);
        register(Blocks.WEATHERED_COPPER, COPPER_BLOCK);
        register(Blocks.OXIDIZED_COPPER, COPPER_BLOCK);
        register(Blocks.WAXED_COPPER_BLOCK, COPPER_BLOCK);
        register(Blocks.WAXED_EXPOSED_COPPER, COPPER_BLOCK);
        register(Blocks.WAXED_WEATHERED_COPPER, COPPER_BLOCK);
        register(Blocks.WAXED_OXIDIZED_COPPER, COPPER_BLOCK);
        register(Blocks.CUT_COPPER, COPPER_BLOCK);
        register(Blocks.EXPOSED_CUT_COPPER, COPPER_BLOCK);
        register(Blocks.WEATHERED_CUT_COPPER, COPPER_BLOCK);
        register(Blocks.OXIDIZED_CUT_COPPER, COPPER_BLOCK);
        register(Blocks.WAXED_CUT_COPPER, COPPER_BLOCK);
        register(Blocks.WAXED_EXPOSED_CUT_COPPER, COPPER_BLOCK);
        register(Blocks.WAXED_WEATHERED_CUT_COPPER, COPPER_BLOCK);
        register(Blocks.WAXED_OXIDIZED_CUT_COPPER, COPPER_BLOCK);
        register(Blocks.CUT_COPPER_STAIRS, COPPER_BLOCK);
        register(Blocks.EXPOSED_CUT_COPPER_STAIRS, COPPER_BLOCK);
        register(Blocks.WEATHERED_CUT_COPPER_STAIRS, COPPER_BLOCK);
        register(Blocks.OXIDIZED_CUT_COPPER_STAIRS, COPPER_BLOCK);
        register(Blocks.WAXED_CUT_COPPER_STAIRS, COPPER_BLOCK);
        register(Blocks.WAXED_EXPOSED_CUT_COPPER_STAIRS, COPPER_BLOCK);
        register(Blocks.WAXED_WEATHERED_CUT_COPPER_STAIRS, COPPER_BLOCK);
        register(Blocks.WAXED_OXIDIZED_CUT_COPPER_STAIRS, COPPER_BLOCK);
        register(Blocks.CUT_COPPER_SLAB, COPPER_BLOCK);
        register(Blocks.EXPOSED_CUT_COPPER_SLAB, COPPER_BLOCK);
        register(Blocks.WEATHERED_CUT_COPPER_SLAB, COPPER_BLOCK);
        register(Blocks.OXIDIZED_CUT_COPPER_SLAB, COPPER_BLOCK);
        register(Blocks.WAXED_CUT_COPPER_SLAB, COPPER_BLOCK);
        register(Blocks.WAXED_EXPOSED_CUT_COPPER_SLAB, COPPER_BLOCK);
        register(Blocks.WAXED_WEATHERED_CUT_COPPER_SLAB, COPPER_BLOCK);
        register(Blocks.WAXED_OXIDIZED_CUT_COPPER_SLAB, COPPER_BLOCK);
        register(Blocks.COPPER_DOOR, COPPER_BLOCK);
        register(Blocks.EXPOSED_COPPER_DOOR, COPPER_BLOCK);
        register(Blocks.WEATHERED_COPPER_DOOR, COPPER_BLOCK);
        register(Blocks.OXIDIZED_COPPER_DOOR, COPPER_BLOCK);
        register(Blocks.WAXED_COPPER_DOOR, COPPER_BLOCK);
        register(Blocks.WAXED_EXPOSED_COPPER_DOOR, COPPER_BLOCK);
        register(Blocks.WAXED_WEATHERED_COPPER_DOOR, COPPER_BLOCK);
        register(Blocks.WAXED_OXIDIZED_COPPER_DOOR, COPPER_BLOCK);
        register(Blocks.COPPER_TRAPDOOR, COPPER_BLOCK);
        register(Blocks.EXPOSED_COPPER_TRAPDOOR, COPPER_BLOCK);
        register(Blocks.WEATHERED_COPPER_TRAPDOOR, COPPER_BLOCK);
        register(Blocks.OXIDIZED_COPPER_TRAPDOOR, COPPER_BLOCK);
        register(Blocks.WAXED_COPPER_TRAPDOOR, COPPER_BLOCK);
        register(Blocks.WAXED_EXPOSED_COPPER_TRAPDOOR, COPPER_BLOCK);
        register(Blocks.WAXED_WEATHERED_COPPER_TRAPDOOR, COPPER_BLOCK);
        register(Blocks.WAXED_OXIDIZED_COPPER_TRAPDOOR, COPPER_BLOCK);
        register(Blocks.COPPER_GRATE, COPPER_BLOCK);
        register(Blocks.EXPOSED_COPPER_GRATE, COPPER_BLOCK);
        register(Blocks.WEATHERED_COPPER_GRATE, COPPER_BLOCK);
        register(Blocks.OXIDIZED_COPPER_GRATE, COPPER_BLOCK);
        register(Blocks.WAXED_COPPER_GRATE, COPPER_BLOCK);
        register(Blocks.WAXED_EXPOSED_COPPER_GRATE, COPPER_BLOCK);
        register(Blocks.WAXED_WEATHERED_COPPER_GRATE, COPPER_BLOCK);
        register(Blocks.WAXED_OXIDIZED_COPPER_GRATE, COPPER_BLOCK);
        register(Blocks.CHISELED_COPPER, COPPER_BLOCK);
        register(Blocks.EXPOSED_CHISELED_COPPER, COPPER_BLOCK);
        register(Blocks.WEATHERED_CHISELED_COPPER, COPPER_BLOCK);
        register(Blocks.OXIDIZED_CHISELED_COPPER, COPPER_BLOCK);
        register(Blocks.WAXED_CHISELED_COPPER, COPPER_BLOCK);
        register(Blocks.WAXED_EXPOSED_CHISELED_COPPER, COPPER_BLOCK);
        register(Blocks.WAXED_WEATHERED_CHISELED_COPPER, COPPER_BLOCK);
        register(Blocks.WAXED_OXIDIZED_CHISELED_COPPER, COPPER_BLOCK);
        // Copper Bulb variants (MC 1.21)
        register(Blocks.COPPER_BULB, COPPER_BLOCK);
        register(Blocks.EXPOSED_COPPER_BULB, COPPER_BLOCK);
        register(Blocks.WEATHERED_COPPER_BULB, COPPER_BLOCK);
        register(Blocks.OXIDIZED_COPPER_BULB, COPPER_BLOCK);
        register(Blocks.WAXED_COPPER_BULB, COPPER_BLOCK);
        register(Blocks.WAXED_EXPOSED_COPPER_BULB, COPPER_BLOCK);
        register(Blocks.WAXED_WEATHERED_COPPER_BULB, COPPER_BLOCK);
        register(Blocks.WAXED_OXIDIZED_COPPER_BULB, COPPER_BLOCK);
        register(Blocks.NETHERITE_BLOCK, NETHERITE_BLOCK);
        register(Blocks.RAW_IRON_BLOCK, RAW_IRON_BLOCK);
        register(Blocks.RAW_GOLD_BLOCK, RAW_GOLD_BLOCK);
        register(Blocks.RAW_COPPER_BLOCK, RAW_COPPER_BLOCK);

        // Metal Constructs
        register(Blocks.IRON_BARS, IRON_BARS);
        register(Blocks.CHAIN, CHAIN);
        register(Blocks.ANVIL, ANVIL);
        register(Blocks.CHIPPED_ANVIL, ANVIL);
        register(Blocks.DAMAGED_ANVIL, ANVIL);
        register(Blocks.CAULDRON, CAULDRON);
        register(Blocks.WATER_CAULDRON, CAULDRON);
        register(Blocks.LAVA_CAULDRON, CAULDRON);
        register(Blocks.POWDER_SNOW_CAULDRON, CAULDRON);
        register(Blocks.HOPPER, HOPPER);
        register(Blocks.HEAVY_WEIGHTED_PRESSURE_PLATE, HEAVY_WEIGHTED_PRESSURE_PLATE);
        register(Blocks.LIGHT_WEIGHTED_PRESSURE_PLATE, LIGHT_WEIGHTED_PRESSURE_PLATE);
        register(Blocks.BELL, BELL);
        register(Blocks.LIGHTNING_ROD, LIGHTNING_ROD);
        register(Blocks.IRON_DOOR, IRON_DOOR);
        register(Blocks.IRON_TRAPDOOR, IRON_DOOR);
        register(Blocks.RAIL, RAIL);
        register(Blocks.POWERED_RAIL, RAIL);
        register(Blocks.DETECTOR_RAIL, RAIL);
        register(Blocks.ACTIVATOR_RAIL, RAIL);

        // Gems & Crystals
        register(Blocks.DIAMOND_BLOCK, DIAMOND_BLOCK);
        register(Blocks.EMERALD_BLOCK, EMERALD_BLOCK);
        register(Blocks.AMETHYST_BLOCK, AMETHYST_BLOCK);
        register(Blocks.BUDDING_AMETHYST, AMETHYST_BLOCK);
        register(Blocks.AMETHYST_CLUSTER, AMETHYST_CLUSTER);
        register(Blocks.LARGE_AMETHYST_BUD, AMETHYST_CLUSTER);
        register(Blocks.MEDIUM_AMETHYST_BUD, AMETHYST_CLUSTER);
        register(Blocks.SMALL_AMETHYST_BUD, AMETHYST_CLUSTER);
        register(Blocks.LAPIS_BLOCK, LAPIS_BLOCK);

        // Minerals
        register(Blocks.QUARTZ_BLOCK, QUARTZ_BLOCK);
        register(Blocks.QUARTZ_PILLAR, QUARTZ_BLOCK);
        register(Blocks.QUARTZ_BRICKS, QUARTZ_BLOCK);
        register(Blocks.SMOOTH_QUARTZ, QUARTZ_BLOCK);
        register(Blocks.CHISELED_QUARTZ_BLOCK, QUARTZ_BLOCK);
        register(Blocks.QUARTZ_STAIRS, QUARTZ_BLOCK);
        register(Blocks.QUARTZ_SLAB, QUARTZ_BLOCK);
        register(Blocks.SMOOTH_QUARTZ_STAIRS, QUARTZ_BLOCK);
        register(Blocks.SMOOTH_QUARTZ_SLAB, QUARTZ_BLOCK);
        register(Blocks.OBSIDIAN, OBSIDIAN);
        register(Blocks.CRYING_OBSIDIAN, CRYING_OBSIDIAN);
        register(Blocks.PRISMARINE, PRISMARINE);
        register(Blocks.DARK_PRISMARINE, PRISMARINE);
        register(Blocks.PRISMARINE_BRICKS, PRISMARINE);
        register(Blocks.PRISMARINE_STAIRS, PRISMARINE);
        register(Blocks.PRISMARINE_SLAB, PRISMARINE);
        register(Blocks.DARK_PRISMARINE_STAIRS, PRISMARINE);
        register(Blocks.DARK_PRISMARINE_SLAB, PRISMARINE);
        register(Blocks.PRISMARINE_BRICK_STAIRS, PRISMARINE);
        register(Blocks.PRISMARINE_BRICK_SLAB, PRISMARINE);

        // Glass (dielectric IOR 1.52)
        register(Blocks.GLASS, GLASS);
        register(Blocks.GLASS_PANE, GLASS);
        register(Blocks.TINTED_GLASS, GLASS);
        register(Blocks.WHITE_STAINED_GLASS, GLASS);
        register(Blocks.ORANGE_STAINED_GLASS, GLASS);
        register(Blocks.MAGENTA_STAINED_GLASS, GLASS);
        register(Blocks.LIGHT_BLUE_STAINED_GLASS, GLASS);
        register(Blocks.YELLOW_STAINED_GLASS, GLASS);
        register(Blocks.LIME_STAINED_GLASS, GLASS);
        register(Blocks.PINK_STAINED_GLASS, GLASS);
        register(Blocks.GRAY_STAINED_GLASS, GLASS);
        register(Blocks.LIGHT_GRAY_STAINED_GLASS, GLASS);
        register(Blocks.CYAN_STAINED_GLASS, GLASS);
        register(Blocks.PURPLE_STAINED_GLASS, GLASS);
        register(Blocks.BLUE_STAINED_GLASS, GLASS);
        register(Blocks.BROWN_STAINED_GLASS, GLASS);
        register(Blocks.GREEN_STAINED_GLASS, GLASS);
        register(Blocks.RED_STAINED_GLASS, GLASS);
        register(Blocks.BLACK_STAINED_GLASS, GLASS);
        register(Blocks.WHITE_STAINED_GLASS_PANE, GLASS);
        register(Blocks.ORANGE_STAINED_GLASS_PANE, GLASS);
        register(Blocks.MAGENTA_STAINED_GLASS_PANE, GLASS);
        register(Blocks.LIGHT_BLUE_STAINED_GLASS_PANE, GLASS);
        register(Blocks.YELLOW_STAINED_GLASS_PANE, GLASS);
        register(Blocks.LIME_STAINED_GLASS_PANE, GLASS);
        register(Blocks.PINK_STAINED_GLASS_PANE, GLASS);
        register(Blocks.GRAY_STAINED_GLASS_PANE, GLASS);
        register(Blocks.LIGHT_GRAY_STAINED_GLASS_PANE, GLASS);
        register(Blocks.CYAN_STAINED_GLASS_PANE, GLASS);
        register(Blocks.PURPLE_STAINED_GLASS_PANE, GLASS);
        register(Blocks.BLUE_STAINED_GLASS_PANE, GLASS);
        register(Blocks.BROWN_STAINED_GLASS_PANE, GLASS);
        register(Blocks.GREEN_STAINED_GLASS_PANE, GLASS);
        register(Blocks.RED_STAINED_GLASS_PANE, GLASS);
        register(Blocks.BLACK_STAINED_GLASS_PANE, GLASS);

        // Ice (dielectric IOR 1.31)
        register(Blocks.ICE, ICE);
        register(Blocks.PACKED_ICE, ICE);
        register(Blocks.BLUE_ICE, ICE);
        register(Blocks.FROSTED_ICE, ICE);

        // Minerals
        register(Blocks.CALCITE, CALCITE);

        // Special / Mixed
        register(Blocks.REDSTONE_BLOCK, REDSTONE_BLOCK);
        register(Blocks.ANCIENT_DEBRIS, ANCIENT_DEBRIS);
        register(Blocks.GILDED_BLACKSTONE, GILDED_BLACKSTONE);
        register(Blocks.LODESTONE, LODESTONE);
        register(Blocks.SMITHING_TABLE, SMITHING_TABLE);
        // MC 1.21 iron-surface blocks
        register(Blocks.VAULT, IRON_BLOCK);
        register(Blocks.CRAFTER, IRON_BLOCK);

        // Extra prismarine
        register(Blocks.PRISMARINE_WALL, PRISMARINE);
    }

    private static void register(Block block, MaterialBlock materialBlock) {
        BLOCK_MAP.put(block, materialBlock);
    }

    public static MaterialBlock fromBlock(Block block) {
        return BLOCK_MAP.get(block);
    }

    // ====== Texture-to-MaterialBlock lookup (for auto-PBR) ======

    /** Maps block registry name → MaterialBlock ordinal. Built lazily from BLOCK_MAP. */
    private static volatile Map<String, Integer> textureMap;

    private static Map<String, Integer> getTextureMap() {
        if (textureMap == null) {
            Map<String, Integer> map = new HashMap<>();
            for (Map.Entry<Block, MaterialBlock> entry : BLOCK_MAP.entrySet()) {
                String name = Registries.BLOCK.getId(entry.getKey()).getPath();
                map.put(name, entry.getValue().ordinal());
            }
            textureMap = map;
        }
        return textureMap;
    }

    /**
     * Given a texture path (e.g. "textures/block/iron_block.png"), returns the MaterialBlock
     * ordinal if it matches a registered block, or -1 if not.
     */
    public static int getOrdinalForTexture(String texturePath) {
        // Extract filename without extension
        int lastSlash = texturePath.lastIndexOf('/');
        String filename = (lastSlash >= 0) ? texturePath.substring(lastSlash + 1) : texturePath;
        int lastDot = filename.lastIndexOf('.');
        if (lastDot >= 0) filename = filename.substring(0, lastDot);

        Map<String, Integer> map = getTextureMap();

        // Exact match (e.g., "iron_block")
        Integer ordinal = map.get(filename);
        if (ordinal != null) return ordinal;

        // Prefix match for texture variants (e.g., "iron_block_top", "anvil_front")
        for (Map.Entry<String, Integer> entry : map.entrySet()) {
            String prefix = entry.getKey();
            if (filename.startsWith(prefix) && filename.length() > prefix.length()
                && filename.charAt(prefix.length()) == '_') {
                return entry.getValue();
            }
        }
        return -1;
    }
}
