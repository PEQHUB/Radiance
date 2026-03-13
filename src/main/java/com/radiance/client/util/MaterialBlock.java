package com.radiance.client.util;

import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.registry.Registries;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
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
    SMITHING_TABLE  ("smithing_table",   true,  480, 480, 480, 50, 0),  // dark iron surface

    // Category 6: Stone Types — dielectric minerals, IOR ~1.54 (quartz/feldspar aggregate)
    STONE              ("stone",              false, 0, 0, 0, 80, 1540),
    GRANITE            ("granite",            false, 0, 0, 0, 75, 1540),
    POLISHED_GRANITE   ("polished_granite",   false, 0, 0, 0, 40, 1540),
    DIORITE            ("diorite",            false, 0, 0, 0, 70, 1540),
    POLISHED_DIORITE   ("polished_diorite",   false, 0, 0, 0, 40, 1540),
    ANDESITE           ("andesite",           false, 0, 0, 0, 78, 1540),
    POLISHED_ANDESITE  ("polished_andesite",  false, 0, 0, 0, 40, 1540),
    DEEPSLATE          ("deepslate",          false, 0, 0, 0, 70, 1540),
    TUFF               ("tuff",              false, 0, 0, 0, 85, 1540),
    COBBLESTONE_MAT    ("cobblestone",        false, 0, 0, 0, 90, 1540),
    MOSSY_COBBLESTONE_MAT ("mossy_cobblestone", false, 0, 0, 0, 88, 1540),
    STONE_BRICKS_MAT   ("stone_bricks",       false, 0, 0, 0, 75, 1540),
    MOSSY_STONE_BRICKS_MAT ("mossy_stone_bricks", false, 0, 0, 0, 78, 1540),
    SANDSTONE_MAT      ("sandstone",          false, 0, 0, 0, 80, 1540),
    RED_SANDSTONE_MAT  ("red_sandstone",      false, 0, 0, 0, 80, 1540),
    SMOOTH_STONE       ("smooth_stone",       false, 0, 0, 0, 50, 1540),

    // Category 7: Wood Types — cellulose IOR ~1.50-1.53
    OAK_PLANKS         ("oak_planks",         false, 0, 0, 0, 75, 1500),
    SPRUCE_PLANKS      ("spruce_planks",      false, 0, 0, 0, 75, 1510),
    BIRCH_PLANKS       ("birch_planks",       false, 0, 0, 0, 70, 1500),
    JUNGLE_PLANKS      ("jungle_planks",      false, 0, 0, 0, 70, 1520),
    ACACIA_PLANKS      ("acacia_planks",      false, 0, 0, 0, 70, 1510),
    DARK_OAK_PLANKS    ("dark_oak_planks",    false, 0, 0, 0, 75, 1530),
    MANGROVE_PLANKS    ("mangrove_planks",    false, 0, 0, 0, 75, 1510),
    CHERRY_PLANKS      ("cherry_planks",      false, 0, 0, 0, 65, 1500),

    // Category 8: Dirt, Sand, Gravel — rough dielectrics
    DIRT_MAT           ("dirt",               false, 0, 0, 0, 90, 1500),
    SAND_MAT           ("sand",               false, 0, 0, 0, 85, 1500),
    RED_SAND_MAT       ("red_sand",           false, 0, 0, 0, 85, 1500),
    GRAVEL_MAT         ("gravel",             false, 0, 0, 0, 92, 1500),
    CLAY_MAT           ("clay",               false, 0, 0, 0, 80, 1530),
    MUD_MAT            ("mud",                false, 0, 0, 0, 88, 1500),

    // Category 9: Bricks — fired clay, IOR ~1.55
    BRICKS_MAT         ("bricks",             false, 0, 0, 0, 80, 1550),
    NETHER_BRICKS_MAT  ("nether_bricks",      false, 0, 0, 0, 75, 1550),
    MUD_BRICKS_MAT     ("mud_bricks",         false, 0, 0, 0, 82, 1540),

    // Category 10: Concrete — cementite, IOR ~1.54
    CONCRETE           ("concrete",           false, 0, 0, 0, 80, 1540),

    // Category 11: Wool — soft fiber, very rough, slight subsurface
    WOOL_MAT           ("wool",               false, 0, 0, 0, 95, 1500, 100),

    // Category 12: Terracotta — fired clay ceramic
    TERRACOTTA_MAT     ("terracotta",         false, 0, 0, 0, 70, 1560),
    GLAZED_TERRACOTTA  ("glazed_terracotta",  false, 0, 0, 0, 30, 1580),

    // Category 13: Leaves — plant matter with subsurface scattering
    LEAVES_MAT         ("leaves",             false, 0, 0, 0, 80, 1500, 300),

    // Category 14: Nether Blocks
    NETHERRACK         ("netherrack",         false, 0, 0, 0, 85, 1540),
    BASALT_MAT         ("basalt",             false, 0, 0, 0, 60, 1550),
    BLACKSTONE_MAT     ("blackstone",         false, 0, 0, 0, 50, 1550),
    SOUL_SAND          ("soul_sand",          false, 0, 0, 0, 92, 1500),
    SOUL_SOIL          ("soul_soil",          false, 0, 0, 0, 90, 1500),
    WARPED_NYLIUM      ("warped_nylium",      false, 0, 0, 0, 75, 1530),
    CRIMSON_NYLIUM     ("crimson_nylium",     false, 0, 0, 0, 75, 1530),
    NETHER_WART_BLOCK_MAT ("nether_wart_block", false, 0, 0, 0, 85, 1500, 200),

    // Category 15: End Blocks
    END_STONE_MAT      ("end_stone",          false, 0, 0, 0, 70, 1550),
    END_STONE_BRICKS_MAT ("end_stone_bricks", false, 0, 0, 0, 65, 1550),
    PURPUR             ("purpur_block",       false, 0, 0, 0, 55, 1550),

    // Category 16: Organic / Special
    CORAL_MAT          ("coral_block",        false, 0, 0, 0, 70, 1500, 250),
    MUSHROOM_BLOCK     ("mushroom_block",     false, 0, 0, 0, 88, 1500, 350),
    SPONGE_MAT         ("sponge",             false, 0, 0, 0, 95, 1500, 150),
    HAY_BLOCK_MAT      ("hay_block",          false, 0, 0, 0, 85, 1500, 100),
    BONE_BLOCK_MAT     ("bone_block",         false, 0, 0, 0, 65, 1540),
    PACKED_MUD         ("packed_mud",         false, 0, 0, 0, 85, 1530),
    MOSS_BLOCK         ("moss_block",         false, 0, 0, 0, 90, 1500, 200),
    SCULK_MAT          ("sculk",              false, 0, 0, 0, 85, 1520, 100),
    DRIPSTONE_MAT      ("dripstone_block",    false, 0, 0, 0, 65, 1540),
    SNOW_BLOCK_MAT     ("snow_block",         false, 0, 0, 0, 90, 1310),

    // Category 17: Concrete Powder — loose powder, very rough
    CONCRETE_POWDER    ("concrete_powder",    false, 0, 0, 0, 92, 1500),

    // Category 18: Miscellaneous
    BOOKSHELF_MAT      ("bookshelf",          false, 0, 0, 0, 70, 1500),
    CRAFTING_TABLE_MAT ("crafting_table",     false, 0, 0, 0, 75, 1500),
    TNT_MAT            ("tnt",                false, 0, 0, 0, 80, 1500),
    MELON_MAT          ("melon",              false, 0, 0, 0, 70, 1500, 200),
    PUMPKIN_MAT        ("pumpkin",            false, 0, 0, 0, 75, 1500, 150),
    DRIED_KELP_BLOCK_MAT ("dried_kelp_block", false, 0, 0, 0, 85, 1500),
    HONEYCOMB_BLOCK_MAT ("honeycomb_block",   false, 0, 0, 0, 60, 1520, 100),
    SHROOMLIGHT_MAT    ("shroomlight",        false, 0, 0, 0, 75, 1500, 250),

    // Category 19: Liquids
    WATER_MAT          ("water",              false, 0, 0, 0,  5, 1333),   // water IOR 1.333
    LAVA_MAT           ("lava",               false, 0, 0, 0, 90, 1500, 500),  // opaque, high subsurface
    HONEY_MAT          ("honey",              false, 0, 0, 0, 15, 1504, 300),  // viscous, IOR ~1.50
    SLIME_MAT          ("slime_block",        false, 0, 0, 0, 30, 1500, 400);  // translucent gel

    public static final int COUNT = values().length;

    /** Material category for Texture Editor tab organization. */
    public enum MaterialCategory {
        METALS("Metals"), GEMS("Gems"), MINERALS("Minerals"), GLASS("Glass"),
        STONE("Stone"), WOOD("Wood"), EARTH("Earth"), CERAMICS("Ceramics"),
        ORGANIC("Organic"), LIQUIDS("Liquids"), MISC("Misc");

        private final String displayName;
        MaterialCategory(String d) { this.displayName = d; }
        public String getDisplayName() { return displayName; }
    }

    private final String id;
    private final boolean metal;
    /** Default F0 in permille (0-1000). For dielectrics, computed from IOR. */
    private final int defaultF0R, defaultF0G, defaultF0B;
    /** Default roughness in percent (0-100). */
    private final int defaultRoughness;
    /** Default IOR × 1000 (e.g., 2420 = 2.420). 0 for metals. */
    private final int defaultIOR;
    /** Default subsurface scattering in permille (0-1000). */
    private final int defaultSubsurface;
    /** Primary Minecraft block for icon rendering (set on first register() call). */
    private Block primaryBlock;
    /** Category for Texture Editor tab assignment. Set in static init. */
    private MaterialCategory category = MaterialCategory.MISC;
    /** Parent material for child variants (null = this is a parent). Set in static init. */
    private MaterialBlock parentMaterial = null;
    /** Cached children list, built lazily. */
    private List<MaterialBlock> childrenCache = null;

    MaterialBlock(String id, boolean metal, int f0r, int f0g, int f0b, int roughness, int ior) {
        this(id, metal, f0r, f0g, f0b, roughness, ior, 0);
    }

    MaterialBlock(String id, boolean metal, int f0r, int f0g, int f0b, int roughness, int ior, int subsurface) {
        this.id = id;
        this.metal = metal;
        this.defaultRoughness = roughness;
        this.defaultIOR = ior;
        this.defaultSubsurface = subsurface;
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
    public int getDefaultSubsurface() { return defaultSubsurface; }
    public int getDefaultAnisotropic() { return 0; }
    public int getDefaultSheenWeight() { return 0; }
    public int getDefaultSheenTint() { return 500; }
    public int getDefaultCoatWeight() { return 0; }
    public int getDefaultCoatRoughness() { return 3; }

    // ====== Category & Parent/Child ======

    public MaterialCategory getCategory() { return category; }
    public MaterialBlock getParentMaterial() { return parentMaterial; }
    /** True if this is a top-level material (not a child variant). */
    public boolean isParent() { return parentMaterial == null; }
    /** Returns all child variants of this material (empty if none). */
    public List<MaterialBlock> getChildren() {
        if (childrenCache == null) {
            List<MaterialBlock> list = new ArrayList<>();
            for (MaterialBlock mb : values()) {
                if (mb.parentMaterial == this) list.add(mb);
            }
            childrenCache = Collections.unmodifiableList(list);
        }
        return childrenCache;
    }

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

        // Category 6: Stone Types
        register(Blocks.STONE, STONE);
        register(Blocks.STONE_STAIRS, STONE);
        register(Blocks.STONE_SLAB, STONE);
        register(Blocks.STONE_PRESSURE_PLATE, STONE);
        register(Blocks.STONE_BUTTON, STONE);
        register(Blocks.GRANITE, GRANITE);
        register(Blocks.GRANITE_STAIRS, GRANITE);
        register(Blocks.GRANITE_SLAB, GRANITE);
        register(Blocks.GRANITE_WALL, GRANITE);
        register(Blocks.POLISHED_GRANITE, POLISHED_GRANITE);
        register(Blocks.POLISHED_GRANITE_STAIRS, POLISHED_GRANITE);
        register(Blocks.POLISHED_GRANITE_SLAB, POLISHED_GRANITE);
        register(Blocks.DIORITE, DIORITE);
        register(Blocks.DIORITE_STAIRS, DIORITE);
        register(Blocks.DIORITE_SLAB, DIORITE);
        register(Blocks.DIORITE_WALL, DIORITE);
        register(Blocks.POLISHED_DIORITE, POLISHED_DIORITE);
        register(Blocks.POLISHED_DIORITE_STAIRS, POLISHED_DIORITE);
        register(Blocks.POLISHED_DIORITE_SLAB, POLISHED_DIORITE);
        register(Blocks.ANDESITE, ANDESITE);
        register(Blocks.ANDESITE_STAIRS, ANDESITE);
        register(Blocks.ANDESITE_SLAB, ANDESITE);
        register(Blocks.ANDESITE_WALL, ANDESITE);
        register(Blocks.POLISHED_ANDESITE, POLISHED_ANDESITE);
        register(Blocks.POLISHED_ANDESITE_STAIRS, POLISHED_ANDESITE);
        register(Blocks.POLISHED_ANDESITE_SLAB, POLISHED_ANDESITE);
        register(Blocks.DEEPSLATE, DEEPSLATE);
        register(Blocks.COBBLED_DEEPSLATE, DEEPSLATE);
        register(Blocks.COBBLED_DEEPSLATE_STAIRS, DEEPSLATE);
        register(Blocks.COBBLED_DEEPSLATE_SLAB, DEEPSLATE);
        register(Blocks.COBBLED_DEEPSLATE_WALL, DEEPSLATE);
        register(Blocks.POLISHED_DEEPSLATE, DEEPSLATE);
        register(Blocks.POLISHED_DEEPSLATE_STAIRS, DEEPSLATE);
        register(Blocks.POLISHED_DEEPSLATE_SLAB, DEEPSLATE);
        register(Blocks.POLISHED_DEEPSLATE_WALL, DEEPSLATE);
        register(Blocks.DEEPSLATE_BRICKS, DEEPSLATE);
        register(Blocks.DEEPSLATE_BRICK_STAIRS, DEEPSLATE);
        register(Blocks.DEEPSLATE_BRICK_SLAB, DEEPSLATE);
        register(Blocks.DEEPSLATE_BRICK_WALL, DEEPSLATE);
        register(Blocks.DEEPSLATE_TILES, DEEPSLATE);
        register(Blocks.DEEPSLATE_TILE_STAIRS, DEEPSLATE);
        register(Blocks.DEEPSLATE_TILE_SLAB, DEEPSLATE);
        register(Blocks.DEEPSLATE_TILE_WALL, DEEPSLATE);
        register(Blocks.CHISELED_DEEPSLATE, DEEPSLATE);
        register(Blocks.TUFF, TUFF);
        register(Blocks.TUFF_STAIRS, TUFF);
        register(Blocks.TUFF_SLAB, TUFF);
        register(Blocks.TUFF_WALL, TUFF);
        register(Blocks.POLISHED_TUFF, TUFF);
        register(Blocks.POLISHED_TUFF_STAIRS, TUFF);
        register(Blocks.POLISHED_TUFF_SLAB, TUFF);
        register(Blocks.POLISHED_TUFF_WALL, TUFF);
        register(Blocks.TUFF_BRICKS, TUFF);
        register(Blocks.TUFF_BRICK_STAIRS, TUFF);
        register(Blocks.TUFF_BRICK_SLAB, TUFF);
        register(Blocks.TUFF_BRICK_WALL, TUFF);
        register(Blocks.CHISELED_TUFF, TUFF);
        register(Blocks.CHISELED_TUFF_BRICKS, TUFF);
        register(Blocks.COBBLESTONE, COBBLESTONE_MAT);
        register(Blocks.COBBLESTONE_STAIRS, COBBLESTONE_MAT);
        register(Blocks.COBBLESTONE_SLAB, COBBLESTONE_MAT);
        register(Blocks.COBBLESTONE_WALL, COBBLESTONE_MAT);
        register(Blocks.MOSSY_COBBLESTONE, MOSSY_COBBLESTONE_MAT);
        register(Blocks.MOSSY_COBBLESTONE_STAIRS, MOSSY_COBBLESTONE_MAT);
        register(Blocks.MOSSY_COBBLESTONE_SLAB, MOSSY_COBBLESTONE_MAT);
        register(Blocks.MOSSY_COBBLESTONE_WALL, MOSSY_COBBLESTONE_MAT);
        register(Blocks.STONE_BRICKS, STONE_BRICKS_MAT);
        register(Blocks.STONE_BRICK_STAIRS, STONE_BRICKS_MAT);
        register(Blocks.STONE_BRICK_SLAB, STONE_BRICKS_MAT);
        register(Blocks.STONE_BRICK_WALL, STONE_BRICKS_MAT);
        register(Blocks.CRACKED_STONE_BRICKS, STONE_BRICKS_MAT);
        register(Blocks.CHISELED_STONE_BRICKS, STONE_BRICKS_MAT);
        register(Blocks.MOSSY_STONE_BRICKS, MOSSY_STONE_BRICKS_MAT);
        register(Blocks.MOSSY_STONE_BRICK_STAIRS, MOSSY_STONE_BRICKS_MAT);
        register(Blocks.MOSSY_STONE_BRICK_SLAB, MOSSY_STONE_BRICKS_MAT);
        register(Blocks.MOSSY_STONE_BRICK_WALL, MOSSY_STONE_BRICKS_MAT);
        register(Blocks.SANDSTONE, SANDSTONE_MAT);
        register(Blocks.SANDSTONE_STAIRS, SANDSTONE_MAT);
        register(Blocks.SANDSTONE_SLAB, SANDSTONE_MAT);
        register(Blocks.SANDSTONE_WALL, SANDSTONE_MAT);
        register(Blocks.CUT_SANDSTONE, SANDSTONE_MAT);
        register(Blocks.CUT_SANDSTONE_SLAB, SANDSTONE_MAT);
        register(Blocks.CHISELED_SANDSTONE, SANDSTONE_MAT);
        register(Blocks.SMOOTH_SANDSTONE, SANDSTONE_MAT);
        register(Blocks.SMOOTH_SANDSTONE_STAIRS, SANDSTONE_MAT);
        register(Blocks.SMOOTH_SANDSTONE_SLAB, SANDSTONE_MAT);
        register(Blocks.RED_SANDSTONE, RED_SANDSTONE_MAT);
        register(Blocks.RED_SANDSTONE_STAIRS, RED_SANDSTONE_MAT);
        register(Blocks.RED_SANDSTONE_SLAB, RED_SANDSTONE_MAT);
        register(Blocks.RED_SANDSTONE_WALL, RED_SANDSTONE_MAT);
        register(Blocks.CUT_RED_SANDSTONE, RED_SANDSTONE_MAT);
        register(Blocks.CUT_RED_SANDSTONE_SLAB, RED_SANDSTONE_MAT);
        register(Blocks.CHISELED_RED_SANDSTONE, RED_SANDSTONE_MAT);
        register(Blocks.SMOOTH_RED_SANDSTONE, RED_SANDSTONE_MAT);
        register(Blocks.SMOOTH_RED_SANDSTONE_STAIRS, RED_SANDSTONE_MAT);
        register(Blocks.SMOOTH_RED_SANDSTONE_SLAB, RED_SANDSTONE_MAT);
        register(Blocks.SMOOTH_STONE, SMOOTH_STONE);
        register(Blocks.SMOOTH_STONE_SLAB, SMOOTH_STONE);

        // Category 7: Wood Types
        register(Blocks.OAK_PLANKS, OAK_PLANKS);
        register(Blocks.OAK_STAIRS, OAK_PLANKS);
        register(Blocks.OAK_SLAB, OAK_PLANKS);
        register(Blocks.OAK_FENCE, OAK_PLANKS);
        register(Blocks.OAK_FENCE_GATE, OAK_PLANKS);
        register(Blocks.OAK_DOOR, OAK_PLANKS);
        register(Blocks.OAK_TRAPDOOR, OAK_PLANKS);
        register(Blocks.OAK_PRESSURE_PLATE, OAK_PLANKS);
        register(Blocks.OAK_BUTTON, OAK_PLANKS);
        register(Blocks.OAK_LOG, OAK_PLANKS);
        register(Blocks.STRIPPED_OAK_LOG, OAK_PLANKS);
        register(Blocks.OAK_WOOD, OAK_PLANKS);
        register(Blocks.STRIPPED_OAK_WOOD, OAK_PLANKS);
        register(Blocks.SPRUCE_PLANKS, SPRUCE_PLANKS);
        register(Blocks.SPRUCE_STAIRS, SPRUCE_PLANKS);
        register(Blocks.SPRUCE_SLAB, SPRUCE_PLANKS);
        register(Blocks.SPRUCE_FENCE, SPRUCE_PLANKS);
        register(Blocks.SPRUCE_FENCE_GATE, SPRUCE_PLANKS);
        register(Blocks.SPRUCE_DOOR, SPRUCE_PLANKS);
        register(Blocks.SPRUCE_TRAPDOOR, SPRUCE_PLANKS);
        register(Blocks.SPRUCE_PRESSURE_PLATE, SPRUCE_PLANKS);
        register(Blocks.SPRUCE_BUTTON, SPRUCE_PLANKS);
        register(Blocks.SPRUCE_LOG, SPRUCE_PLANKS);
        register(Blocks.STRIPPED_SPRUCE_LOG, SPRUCE_PLANKS);
        register(Blocks.SPRUCE_WOOD, SPRUCE_PLANKS);
        register(Blocks.STRIPPED_SPRUCE_WOOD, SPRUCE_PLANKS);
        register(Blocks.BIRCH_PLANKS, BIRCH_PLANKS);
        register(Blocks.BIRCH_STAIRS, BIRCH_PLANKS);
        register(Blocks.BIRCH_SLAB, BIRCH_PLANKS);
        register(Blocks.BIRCH_FENCE, BIRCH_PLANKS);
        register(Blocks.BIRCH_FENCE_GATE, BIRCH_PLANKS);
        register(Blocks.BIRCH_DOOR, BIRCH_PLANKS);
        register(Blocks.BIRCH_TRAPDOOR, BIRCH_PLANKS);
        register(Blocks.BIRCH_PRESSURE_PLATE, BIRCH_PLANKS);
        register(Blocks.BIRCH_BUTTON, BIRCH_PLANKS);
        register(Blocks.BIRCH_LOG, BIRCH_PLANKS);
        register(Blocks.STRIPPED_BIRCH_LOG, BIRCH_PLANKS);
        register(Blocks.BIRCH_WOOD, BIRCH_PLANKS);
        register(Blocks.STRIPPED_BIRCH_WOOD, BIRCH_PLANKS);
        register(Blocks.JUNGLE_PLANKS, JUNGLE_PLANKS);
        register(Blocks.JUNGLE_STAIRS, JUNGLE_PLANKS);
        register(Blocks.JUNGLE_SLAB, JUNGLE_PLANKS);
        register(Blocks.JUNGLE_FENCE, JUNGLE_PLANKS);
        register(Blocks.JUNGLE_FENCE_GATE, JUNGLE_PLANKS);
        register(Blocks.JUNGLE_DOOR, JUNGLE_PLANKS);
        register(Blocks.JUNGLE_TRAPDOOR, JUNGLE_PLANKS);
        register(Blocks.JUNGLE_PRESSURE_PLATE, JUNGLE_PLANKS);
        register(Blocks.JUNGLE_BUTTON, JUNGLE_PLANKS);
        register(Blocks.JUNGLE_LOG, JUNGLE_PLANKS);
        register(Blocks.STRIPPED_JUNGLE_LOG, JUNGLE_PLANKS);
        register(Blocks.JUNGLE_WOOD, JUNGLE_PLANKS);
        register(Blocks.STRIPPED_JUNGLE_WOOD, JUNGLE_PLANKS);
        register(Blocks.ACACIA_PLANKS, ACACIA_PLANKS);
        register(Blocks.ACACIA_STAIRS, ACACIA_PLANKS);
        register(Blocks.ACACIA_SLAB, ACACIA_PLANKS);
        register(Blocks.ACACIA_FENCE, ACACIA_PLANKS);
        register(Blocks.ACACIA_FENCE_GATE, ACACIA_PLANKS);
        register(Blocks.ACACIA_DOOR, ACACIA_PLANKS);
        register(Blocks.ACACIA_TRAPDOOR, ACACIA_PLANKS);
        register(Blocks.ACACIA_PRESSURE_PLATE, ACACIA_PLANKS);
        register(Blocks.ACACIA_BUTTON, ACACIA_PLANKS);
        register(Blocks.ACACIA_LOG, ACACIA_PLANKS);
        register(Blocks.STRIPPED_ACACIA_LOG, ACACIA_PLANKS);
        register(Blocks.ACACIA_WOOD, ACACIA_PLANKS);
        register(Blocks.STRIPPED_ACACIA_WOOD, ACACIA_PLANKS);
        register(Blocks.DARK_OAK_PLANKS, DARK_OAK_PLANKS);
        register(Blocks.DARK_OAK_STAIRS, DARK_OAK_PLANKS);
        register(Blocks.DARK_OAK_SLAB, DARK_OAK_PLANKS);
        register(Blocks.DARK_OAK_FENCE, DARK_OAK_PLANKS);
        register(Blocks.DARK_OAK_FENCE_GATE, DARK_OAK_PLANKS);
        register(Blocks.DARK_OAK_DOOR, DARK_OAK_PLANKS);
        register(Blocks.DARK_OAK_TRAPDOOR, DARK_OAK_PLANKS);
        register(Blocks.DARK_OAK_PRESSURE_PLATE, DARK_OAK_PLANKS);
        register(Blocks.DARK_OAK_BUTTON, DARK_OAK_PLANKS);
        register(Blocks.DARK_OAK_LOG, DARK_OAK_PLANKS);
        register(Blocks.STRIPPED_DARK_OAK_LOG, DARK_OAK_PLANKS);
        register(Blocks.DARK_OAK_WOOD, DARK_OAK_PLANKS);
        register(Blocks.STRIPPED_DARK_OAK_WOOD, DARK_OAK_PLANKS);
        register(Blocks.MANGROVE_PLANKS, MANGROVE_PLANKS);
        register(Blocks.MANGROVE_STAIRS, MANGROVE_PLANKS);
        register(Blocks.MANGROVE_SLAB, MANGROVE_PLANKS);
        register(Blocks.MANGROVE_FENCE, MANGROVE_PLANKS);
        register(Blocks.MANGROVE_FENCE_GATE, MANGROVE_PLANKS);
        register(Blocks.MANGROVE_DOOR, MANGROVE_PLANKS);
        register(Blocks.MANGROVE_TRAPDOOR, MANGROVE_PLANKS);
        register(Blocks.MANGROVE_PRESSURE_PLATE, MANGROVE_PLANKS);
        register(Blocks.MANGROVE_BUTTON, MANGROVE_PLANKS);
        register(Blocks.MANGROVE_LOG, MANGROVE_PLANKS);
        register(Blocks.STRIPPED_MANGROVE_LOG, MANGROVE_PLANKS);
        register(Blocks.MANGROVE_WOOD, MANGROVE_PLANKS);
        register(Blocks.STRIPPED_MANGROVE_WOOD, MANGROVE_PLANKS);
        register(Blocks.CHERRY_PLANKS, CHERRY_PLANKS);
        register(Blocks.CHERRY_STAIRS, CHERRY_PLANKS);
        register(Blocks.CHERRY_SLAB, CHERRY_PLANKS);
        register(Blocks.CHERRY_FENCE, CHERRY_PLANKS);
        register(Blocks.CHERRY_FENCE_GATE, CHERRY_PLANKS);
        register(Blocks.CHERRY_DOOR, CHERRY_PLANKS);
        register(Blocks.CHERRY_TRAPDOOR, CHERRY_PLANKS);
        register(Blocks.CHERRY_PRESSURE_PLATE, CHERRY_PLANKS);
        register(Blocks.CHERRY_BUTTON, CHERRY_PLANKS);
        register(Blocks.CHERRY_LOG, CHERRY_PLANKS);
        register(Blocks.STRIPPED_CHERRY_LOG, CHERRY_PLANKS);
        register(Blocks.CHERRY_WOOD, CHERRY_PLANKS);
        register(Blocks.STRIPPED_CHERRY_WOOD, CHERRY_PLANKS);
        // Bamboo + Crimson + Warped → use closest wood type
        register(Blocks.BAMBOO_PLANKS, CHERRY_PLANKS);
        register(Blocks.BAMBOO_MOSAIC, CHERRY_PLANKS);
        register(Blocks.BAMBOO_MOSAIC_STAIRS, CHERRY_PLANKS);
        register(Blocks.BAMBOO_MOSAIC_SLAB, CHERRY_PLANKS);
        register(Blocks.BAMBOO_STAIRS, CHERRY_PLANKS);
        register(Blocks.BAMBOO_SLAB, CHERRY_PLANKS);
        register(Blocks.BAMBOO_FENCE, CHERRY_PLANKS);
        register(Blocks.BAMBOO_FENCE_GATE, CHERRY_PLANKS);
        register(Blocks.BAMBOO_DOOR, CHERRY_PLANKS);
        register(Blocks.BAMBOO_TRAPDOOR, CHERRY_PLANKS);
        register(Blocks.BAMBOO_PRESSURE_PLATE, CHERRY_PLANKS);
        register(Blocks.BAMBOO_BUTTON, CHERRY_PLANKS);
        register(Blocks.BAMBOO_BLOCK, CHERRY_PLANKS);
        register(Blocks.STRIPPED_BAMBOO_BLOCK, CHERRY_PLANKS);
        register(Blocks.CRIMSON_PLANKS, DARK_OAK_PLANKS);
        register(Blocks.CRIMSON_STAIRS, DARK_OAK_PLANKS);
        register(Blocks.CRIMSON_SLAB, DARK_OAK_PLANKS);
        register(Blocks.CRIMSON_FENCE, DARK_OAK_PLANKS);
        register(Blocks.CRIMSON_FENCE_GATE, DARK_OAK_PLANKS);
        register(Blocks.CRIMSON_DOOR, DARK_OAK_PLANKS);
        register(Blocks.CRIMSON_TRAPDOOR, DARK_OAK_PLANKS);
        register(Blocks.CRIMSON_PRESSURE_PLATE, DARK_OAK_PLANKS);
        register(Blocks.CRIMSON_BUTTON, DARK_OAK_PLANKS);
        register(Blocks.CRIMSON_STEM, DARK_OAK_PLANKS);
        register(Blocks.STRIPPED_CRIMSON_STEM, DARK_OAK_PLANKS);
        register(Blocks.CRIMSON_HYPHAE, DARK_OAK_PLANKS);
        register(Blocks.STRIPPED_CRIMSON_HYPHAE, DARK_OAK_PLANKS);
        register(Blocks.WARPED_PLANKS, DARK_OAK_PLANKS);
        register(Blocks.WARPED_STAIRS, DARK_OAK_PLANKS);
        register(Blocks.WARPED_SLAB, DARK_OAK_PLANKS);
        register(Blocks.WARPED_FENCE, DARK_OAK_PLANKS);
        register(Blocks.WARPED_FENCE_GATE, DARK_OAK_PLANKS);
        register(Blocks.WARPED_DOOR, DARK_OAK_PLANKS);
        register(Blocks.WARPED_TRAPDOOR, DARK_OAK_PLANKS);
        register(Blocks.WARPED_PRESSURE_PLATE, DARK_OAK_PLANKS);
        register(Blocks.WARPED_BUTTON, DARK_OAK_PLANKS);
        register(Blocks.WARPED_STEM, DARK_OAK_PLANKS);
        register(Blocks.STRIPPED_WARPED_STEM, DARK_OAK_PLANKS);
        register(Blocks.WARPED_HYPHAE, DARK_OAK_PLANKS);
        register(Blocks.STRIPPED_WARPED_HYPHAE, DARK_OAK_PLANKS);

        // Category 8: Dirt, Sand, Gravel
        register(Blocks.DIRT, DIRT_MAT);
        register(Blocks.COARSE_DIRT, DIRT_MAT);
        register(Blocks.ROOTED_DIRT, DIRT_MAT);
        register(Blocks.DIRT_PATH, DIRT_MAT);
        register(Blocks.FARMLAND, DIRT_MAT);
        register(Blocks.GRASS_BLOCK, DIRT_MAT);
        register(Blocks.PODZOL, DIRT_MAT);
        register(Blocks.MYCELIUM, DIRT_MAT);
        register(Blocks.SAND, SAND_MAT);
        register(Blocks.RED_SAND, RED_SAND_MAT);
        register(Blocks.GRAVEL, GRAVEL_MAT);
        register(Blocks.CLAY, CLAY_MAT);
        register(Blocks.MUD, MUD_MAT);
        register(Blocks.MUDDY_MANGROVE_ROOTS, MUD_MAT);

        // Category 9: Bricks
        register(Blocks.BRICKS, BRICKS_MAT);
        register(Blocks.BRICK_STAIRS, BRICKS_MAT);
        register(Blocks.BRICK_SLAB, BRICKS_MAT);
        register(Blocks.BRICK_WALL, BRICKS_MAT);
        register(Blocks.NETHER_BRICKS, NETHER_BRICKS_MAT);
        register(Blocks.NETHER_BRICK_STAIRS, NETHER_BRICKS_MAT);
        register(Blocks.NETHER_BRICK_SLAB, NETHER_BRICKS_MAT);
        register(Blocks.NETHER_BRICK_WALL, NETHER_BRICKS_MAT);
        register(Blocks.NETHER_BRICK_FENCE, NETHER_BRICKS_MAT);
        register(Blocks.RED_NETHER_BRICKS, NETHER_BRICKS_MAT);
        register(Blocks.RED_NETHER_BRICK_STAIRS, NETHER_BRICKS_MAT);
        register(Blocks.RED_NETHER_BRICK_SLAB, NETHER_BRICKS_MAT);
        register(Blocks.RED_NETHER_BRICK_WALL, NETHER_BRICKS_MAT);
        register(Blocks.CRACKED_NETHER_BRICKS, NETHER_BRICKS_MAT);
        register(Blocks.CHISELED_NETHER_BRICKS, NETHER_BRICKS_MAT);
        register(Blocks.MUD_BRICKS, MUD_BRICKS_MAT);
        register(Blocks.MUD_BRICK_STAIRS, MUD_BRICKS_MAT);
        register(Blocks.MUD_BRICK_SLAB, MUD_BRICKS_MAT);
        register(Blocks.MUD_BRICK_WALL, MUD_BRICKS_MAT);

        // Category 10: Concrete (all 16 colors → shared entry)
        register(Blocks.WHITE_CONCRETE, CONCRETE);
        register(Blocks.ORANGE_CONCRETE, CONCRETE);
        register(Blocks.MAGENTA_CONCRETE, CONCRETE);
        register(Blocks.LIGHT_BLUE_CONCRETE, CONCRETE);
        register(Blocks.YELLOW_CONCRETE, CONCRETE);
        register(Blocks.LIME_CONCRETE, CONCRETE);
        register(Blocks.PINK_CONCRETE, CONCRETE);
        register(Blocks.GRAY_CONCRETE, CONCRETE);
        register(Blocks.LIGHT_GRAY_CONCRETE, CONCRETE);
        register(Blocks.CYAN_CONCRETE, CONCRETE);
        register(Blocks.PURPLE_CONCRETE, CONCRETE);
        register(Blocks.BLUE_CONCRETE, CONCRETE);
        register(Blocks.BROWN_CONCRETE, CONCRETE);
        register(Blocks.GREEN_CONCRETE, CONCRETE);
        register(Blocks.RED_CONCRETE, CONCRETE);
        register(Blocks.BLACK_CONCRETE, CONCRETE);

        // Category 11: Wool (all 16 colors → shared entry)
        register(Blocks.WHITE_WOOL, WOOL_MAT);
        register(Blocks.ORANGE_WOOL, WOOL_MAT);
        register(Blocks.MAGENTA_WOOL, WOOL_MAT);
        register(Blocks.LIGHT_BLUE_WOOL, WOOL_MAT);
        register(Blocks.YELLOW_WOOL, WOOL_MAT);
        register(Blocks.LIME_WOOL, WOOL_MAT);
        register(Blocks.PINK_WOOL, WOOL_MAT);
        register(Blocks.GRAY_WOOL, WOOL_MAT);
        register(Blocks.LIGHT_GRAY_WOOL, WOOL_MAT);
        register(Blocks.CYAN_WOOL, WOOL_MAT);
        register(Blocks.PURPLE_WOOL, WOOL_MAT);
        register(Blocks.BLUE_WOOL, WOOL_MAT);
        register(Blocks.BROWN_WOOL, WOOL_MAT);
        register(Blocks.GREEN_WOOL, WOOL_MAT);
        register(Blocks.RED_WOOL, WOOL_MAT);
        register(Blocks.BLACK_WOOL, WOOL_MAT);
        register(Blocks.WHITE_CARPET, WOOL_MAT);
        register(Blocks.ORANGE_CARPET, WOOL_MAT);
        register(Blocks.MAGENTA_CARPET, WOOL_MAT);
        register(Blocks.LIGHT_BLUE_CARPET, WOOL_MAT);
        register(Blocks.YELLOW_CARPET, WOOL_MAT);
        register(Blocks.LIME_CARPET, WOOL_MAT);
        register(Blocks.PINK_CARPET, WOOL_MAT);
        register(Blocks.GRAY_CARPET, WOOL_MAT);
        register(Blocks.LIGHT_GRAY_CARPET, WOOL_MAT);
        register(Blocks.CYAN_CARPET, WOOL_MAT);
        register(Blocks.PURPLE_CARPET, WOOL_MAT);
        register(Blocks.BLUE_CARPET, WOOL_MAT);
        register(Blocks.BROWN_CARPET, WOOL_MAT);
        register(Blocks.GREEN_CARPET, WOOL_MAT);
        register(Blocks.RED_CARPET, WOOL_MAT);
        register(Blocks.BLACK_CARPET, WOOL_MAT);

        // Category 12: Terracotta
        register(Blocks.TERRACOTTA, TERRACOTTA_MAT);
        register(Blocks.WHITE_TERRACOTTA, TERRACOTTA_MAT);
        register(Blocks.ORANGE_TERRACOTTA, TERRACOTTA_MAT);
        register(Blocks.MAGENTA_TERRACOTTA, TERRACOTTA_MAT);
        register(Blocks.LIGHT_BLUE_TERRACOTTA, TERRACOTTA_MAT);
        register(Blocks.YELLOW_TERRACOTTA, TERRACOTTA_MAT);
        register(Blocks.LIME_TERRACOTTA, TERRACOTTA_MAT);
        register(Blocks.PINK_TERRACOTTA, TERRACOTTA_MAT);
        register(Blocks.GRAY_TERRACOTTA, TERRACOTTA_MAT);
        register(Blocks.LIGHT_GRAY_TERRACOTTA, TERRACOTTA_MAT);
        register(Blocks.CYAN_TERRACOTTA, TERRACOTTA_MAT);
        register(Blocks.PURPLE_TERRACOTTA, TERRACOTTA_MAT);
        register(Blocks.BLUE_TERRACOTTA, TERRACOTTA_MAT);
        register(Blocks.BROWN_TERRACOTTA, TERRACOTTA_MAT);
        register(Blocks.GREEN_TERRACOTTA, TERRACOTTA_MAT);
        register(Blocks.RED_TERRACOTTA, TERRACOTTA_MAT);
        register(Blocks.BLACK_TERRACOTTA, TERRACOTTA_MAT);
        register(Blocks.WHITE_GLAZED_TERRACOTTA, GLAZED_TERRACOTTA);
        register(Blocks.ORANGE_GLAZED_TERRACOTTA, GLAZED_TERRACOTTA);
        register(Blocks.MAGENTA_GLAZED_TERRACOTTA, GLAZED_TERRACOTTA);
        register(Blocks.LIGHT_BLUE_GLAZED_TERRACOTTA, GLAZED_TERRACOTTA);
        register(Blocks.YELLOW_GLAZED_TERRACOTTA, GLAZED_TERRACOTTA);
        register(Blocks.LIME_GLAZED_TERRACOTTA, GLAZED_TERRACOTTA);
        register(Blocks.PINK_GLAZED_TERRACOTTA, GLAZED_TERRACOTTA);
        register(Blocks.GRAY_GLAZED_TERRACOTTA, GLAZED_TERRACOTTA);
        register(Blocks.LIGHT_GRAY_GLAZED_TERRACOTTA, GLAZED_TERRACOTTA);
        register(Blocks.CYAN_GLAZED_TERRACOTTA, GLAZED_TERRACOTTA);
        register(Blocks.PURPLE_GLAZED_TERRACOTTA, GLAZED_TERRACOTTA);
        register(Blocks.BLUE_GLAZED_TERRACOTTA, GLAZED_TERRACOTTA);
        register(Blocks.BROWN_GLAZED_TERRACOTTA, GLAZED_TERRACOTTA);
        register(Blocks.GREEN_GLAZED_TERRACOTTA, GLAZED_TERRACOTTA);
        register(Blocks.RED_GLAZED_TERRACOTTA, GLAZED_TERRACOTTA);
        register(Blocks.BLACK_GLAZED_TERRACOTTA, GLAZED_TERRACOTTA);

        // Category 13: Leaves
        register(Blocks.OAK_LEAVES, LEAVES_MAT);
        register(Blocks.SPRUCE_LEAVES, LEAVES_MAT);
        register(Blocks.BIRCH_LEAVES, LEAVES_MAT);
        register(Blocks.JUNGLE_LEAVES, LEAVES_MAT);
        register(Blocks.ACACIA_LEAVES, LEAVES_MAT);
        register(Blocks.DARK_OAK_LEAVES, LEAVES_MAT);
        register(Blocks.MANGROVE_LEAVES, LEAVES_MAT);
        register(Blocks.CHERRY_LEAVES, LEAVES_MAT);
        register(Blocks.AZALEA_LEAVES, LEAVES_MAT);
        register(Blocks.FLOWERING_AZALEA_LEAVES, LEAVES_MAT);

        // Category 14: Nether Blocks
        register(Blocks.NETHERRACK, NETHERRACK);
        register(Blocks.BASALT, BASALT_MAT);
        register(Blocks.POLISHED_BASALT, BASALT_MAT);
        register(Blocks.SMOOTH_BASALT, BASALT_MAT);
        register(Blocks.BLACKSTONE, BLACKSTONE_MAT);
        register(Blocks.BLACKSTONE_STAIRS, BLACKSTONE_MAT);
        register(Blocks.BLACKSTONE_SLAB, BLACKSTONE_MAT);
        register(Blocks.BLACKSTONE_WALL, BLACKSTONE_MAT);
        register(Blocks.POLISHED_BLACKSTONE, BLACKSTONE_MAT);
        register(Blocks.POLISHED_BLACKSTONE_STAIRS, BLACKSTONE_MAT);
        register(Blocks.POLISHED_BLACKSTONE_SLAB, BLACKSTONE_MAT);
        register(Blocks.POLISHED_BLACKSTONE_WALL, BLACKSTONE_MAT);
        register(Blocks.POLISHED_BLACKSTONE_BRICKS, BLACKSTONE_MAT);
        register(Blocks.POLISHED_BLACKSTONE_BRICK_STAIRS, BLACKSTONE_MAT);
        register(Blocks.POLISHED_BLACKSTONE_BRICK_SLAB, BLACKSTONE_MAT);
        register(Blocks.POLISHED_BLACKSTONE_BRICK_WALL, BLACKSTONE_MAT);
        register(Blocks.POLISHED_BLACKSTONE_PRESSURE_PLATE, BLACKSTONE_MAT);
        register(Blocks.POLISHED_BLACKSTONE_BUTTON, BLACKSTONE_MAT);
        register(Blocks.CRACKED_POLISHED_BLACKSTONE_BRICKS, BLACKSTONE_MAT);
        register(Blocks.CHISELED_POLISHED_BLACKSTONE, BLACKSTONE_MAT);
        register(Blocks.SOUL_SAND, SOUL_SAND);
        register(Blocks.SOUL_SOIL, SOUL_SOIL);
        register(Blocks.WARPED_NYLIUM, WARPED_NYLIUM);
        register(Blocks.CRIMSON_NYLIUM, CRIMSON_NYLIUM);
        register(Blocks.NETHER_WART_BLOCK, NETHER_WART_BLOCK_MAT);
        register(Blocks.WARPED_WART_BLOCK, NETHER_WART_BLOCK_MAT);

        // Category 15: End Blocks
        register(Blocks.END_STONE, END_STONE_MAT);
        register(Blocks.END_STONE_BRICKS, END_STONE_BRICKS_MAT);
        register(Blocks.END_STONE_BRICK_STAIRS, END_STONE_BRICKS_MAT);
        register(Blocks.END_STONE_BRICK_SLAB, END_STONE_BRICKS_MAT);
        register(Blocks.END_STONE_BRICK_WALL, END_STONE_BRICKS_MAT);
        register(Blocks.PURPUR_BLOCK, PURPUR);
        register(Blocks.PURPUR_STAIRS, PURPUR);
        register(Blocks.PURPUR_SLAB, PURPUR);
        register(Blocks.PURPUR_PILLAR, PURPUR);

        // Category 16: Organic / Special
        register(Blocks.TUBE_CORAL_BLOCK, CORAL_MAT);
        register(Blocks.BRAIN_CORAL_BLOCK, CORAL_MAT);
        register(Blocks.BUBBLE_CORAL_BLOCK, CORAL_MAT);
        register(Blocks.FIRE_CORAL_BLOCK, CORAL_MAT);
        register(Blocks.HORN_CORAL_BLOCK, CORAL_MAT);
        register(Blocks.DEAD_TUBE_CORAL_BLOCK, CORAL_MAT);
        register(Blocks.DEAD_BRAIN_CORAL_BLOCK, CORAL_MAT);
        register(Blocks.DEAD_BUBBLE_CORAL_BLOCK, CORAL_MAT);
        register(Blocks.DEAD_FIRE_CORAL_BLOCK, CORAL_MAT);
        register(Blocks.DEAD_HORN_CORAL_BLOCK, CORAL_MAT);
        register(Blocks.BROWN_MUSHROOM_BLOCK, MUSHROOM_BLOCK);
        register(Blocks.RED_MUSHROOM_BLOCK, MUSHROOM_BLOCK);
        register(Blocks.MUSHROOM_STEM, MUSHROOM_BLOCK);
        register(Blocks.SPONGE, SPONGE_MAT);
        register(Blocks.WET_SPONGE, SPONGE_MAT);
        register(Blocks.HAY_BLOCK, HAY_BLOCK_MAT);
        register(Blocks.BONE_BLOCK, BONE_BLOCK_MAT);
        register(Blocks.PACKED_MUD, PACKED_MUD);
        register(Blocks.MOSS_BLOCK, MOSS_BLOCK);
        register(Blocks.MOSS_CARPET, MOSS_BLOCK);
        register(Blocks.SCULK, SCULK_MAT);
        register(Blocks.POINTED_DRIPSTONE, DRIPSTONE_MAT);
        register(Blocks.DRIPSTONE_BLOCK, DRIPSTONE_MAT);
        register(Blocks.SNOW_BLOCK, SNOW_BLOCK_MAT);
        register(Blocks.SNOW, SNOW_BLOCK_MAT);
        register(Blocks.POWDER_SNOW, SNOW_BLOCK_MAT);

        // Category 17: Concrete Powder (all 16 colors → shared entry)
        register(Blocks.WHITE_CONCRETE_POWDER, CONCRETE_POWDER);
        register(Blocks.ORANGE_CONCRETE_POWDER, CONCRETE_POWDER);
        register(Blocks.MAGENTA_CONCRETE_POWDER, CONCRETE_POWDER);
        register(Blocks.LIGHT_BLUE_CONCRETE_POWDER, CONCRETE_POWDER);
        register(Blocks.YELLOW_CONCRETE_POWDER, CONCRETE_POWDER);
        register(Blocks.LIME_CONCRETE_POWDER, CONCRETE_POWDER);
        register(Blocks.PINK_CONCRETE_POWDER, CONCRETE_POWDER);
        register(Blocks.GRAY_CONCRETE_POWDER, CONCRETE_POWDER);
        register(Blocks.LIGHT_GRAY_CONCRETE_POWDER, CONCRETE_POWDER);
        register(Blocks.CYAN_CONCRETE_POWDER, CONCRETE_POWDER);
        register(Blocks.PURPLE_CONCRETE_POWDER, CONCRETE_POWDER);
        register(Blocks.BLUE_CONCRETE_POWDER, CONCRETE_POWDER);
        register(Blocks.BROWN_CONCRETE_POWDER, CONCRETE_POWDER);
        register(Blocks.GREEN_CONCRETE_POWDER, CONCRETE_POWDER);
        register(Blocks.RED_CONCRETE_POWDER, CONCRETE_POWDER);
        register(Blocks.BLACK_CONCRETE_POWDER, CONCRETE_POWDER);

        // Category 18: Miscellaneous
        register(Blocks.BOOKSHELF, BOOKSHELF_MAT);
        register(Blocks.CHISELED_BOOKSHELF, BOOKSHELF_MAT);
        register(Blocks.CRAFTING_TABLE, CRAFTING_TABLE_MAT);
        register(Blocks.TNT, TNT_MAT);
        register(Blocks.MELON, MELON_MAT);
        register(Blocks.PUMPKIN, PUMPKIN_MAT);
        register(Blocks.CARVED_PUMPKIN, PUMPKIN_MAT);
        register(Blocks.DRIED_KELP_BLOCK, DRIED_KELP_BLOCK_MAT);
        register(Blocks.HONEYCOMB_BLOCK, HONEYCOMB_BLOCK_MAT);
        register(Blocks.SHROOMLIGHT, SHROOMLIGHT_MAT);

        // Category 19: Liquids
        register(Blocks.WATER, WATER_MAT);
        register(Blocks.LAVA, LAVA_MAT);
        register(Blocks.HONEY_BLOCK, HONEY_MAT);
        register(Blocks.SLIME_BLOCK, SLIME_MAT);

        // ====== Category assignments ======
        for (MaterialBlock mb : new MaterialBlock[]{IRON_BLOCK, GOLD_BLOCK, COPPER_BLOCK, NETHERITE_BLOCK,
                REDSTONE_BLOCK, ANCIENT_DEBRIS, GILDED_BLACKSTONE, LODESTONE, SMITHING_TABLE,
                IRON_BARS, CHAIN, ANVIL, CAULDRON, HOPPER, HEAVY_WEIGHTED_PRESSURE_PLATE,
                LIGHT_WEIGHTED_PRESSURE_PLATE, BELL, LIGHTNING_ROD, IRON_DOOR, RAIL,
                RAW_IRON_BLOCK, RAW_GOLD_BLOCK, RAW_COPPER_BLOCK})
            mb.category = MaterialCategory.METALS;

        for (MaterialBlock mb : new MaterialBlock[]{DIAMOND_BLOCK, EMERALD_BLOCK, AMETHYST_BLOCK,
                AMETHYST_CLUSTER, LAPIS_BLOCK})
            mb.category = MaterialCategory.GEMS;

        for (MaterialBlock mb : new MaterialBlock[]{QUARTZ_BLOCK, OBSIDIAN, CRYING_OBSIDIAN, PRISMARINE, CALCITE})
            mb.category = MaterialCategory.MINERALS;

        for (MaterialBlock mb : new MaterialBlock[]{GLASS, ICE})
            mb.category = MaterialCategory.GLASS;

        for (MaterialBlock mb : new MaterialBlock[]{STONE, GRANITE, POLISHED_GRANITE, DIORITE, POLISHED_DIORITE,
                ANDESITE, POLISHED_ANDESITE, DEEPSLATE, TUFF, SANDSTONE_MAT, RED_SANDSTONE_MAT,
                COBBLESTONE_MAT, MOSSY_COBBLESTONE_MAT, STONE_BRICKS_MAT, MOSSY_STONE_BRICKS_MAT,
                SMOOTH_STONE, BASALT_MAT, BLACKSTONE_MAT, END_STONE_MAT, END_STONE_BRICKS_MAT, PURPUR})
            mb.category = MaterialCategory.STONE;

        for (MaterialBlock mb : new MaterialBlock[]{OAK_PLANKS, SPRUCE_PLANKS, BIRCH_PLANKS, JUNGLE_PLANKS,
                ACACIA_PLANKS, DARK_OAK_PLANKS, MANGROVE_PLANKS, CHERRY_PLANKS})
            mb.category = MaterialCategory.WOOD;

        for (MaterialBlock mb : new MaterialBlock[]{DIRT_MAT, SAND_MAT, RED_SAND_MAT, GRAVEL_MAT, CLAY_MAT,
                MUD_MAT, SNOW_BLOCK_MAT, SOUL_SAND, SOUL_SOIL, NETHERRACK, DRIPSTONE_MAT, PACKED_MUD,
                CONCRETE, CONCRETE_POWDER})
            mb.category = MaterialCategory.EARTH;

        for (MaterialBlock mb : new MaterialBlock[]{BRICKS_MAT, NETHER_BRICKS_MAT, MUD_BRICKS_MAT,
                TERRACOTTA_MAT, GLAZED_TERRACOTTA})
            mb.category = MaterialCategory.CERAMICS;

        for (MaterialBlock mb : new MaterialBlock[]{WOOL_MAT, LEAVES_MAT, CORAL_MAT, MUSHROOM_BLOCK,
                SPONGE_MAT, HAY_BLOCK_MAT, BONE_BLOCK_MAT, MOSS_BLOCK, SCULK_MAT,
                NETHER_WART_BLOCK_MAT, WARPED_NYLIUM, CRIMSON_NYLIUM, SHROOMLIGHT_MAT})
            mb.category = MaterialCategory.ORGANIC;

        for (MaterialBlock mb : new MaterialBlock[]{WATER_MAT, LAVA_MAT, HONEY_MAT, SLIME_MAT})
            mb.category = MaterialCategory.LIQUIDS;

        // ====== Parent/child relationships ======
        // Iron family
        IRON_BARS.parentMaterial = IRON_BLOCK;
        CHAIN.parentMaterial = IRON_BLOCK;
        ANVIL.parentMaterial = IRON_BLOCK;
        CAULDRON.parentMaterial = IRON_BLOCK;
        HOPPER.parentMaterial = IRON_BLOCK;
        HEAVY_WEIGHTED_PRESSURE_PLATE.parentMaterial = IRON_BLOCK;
        IRON_DOOR.parentMaterial = IRON_BLOCK;
        RAIL.parentMaterial = IRON_BLOCK;
        RAW_IRON_BLOCK.parentMaterial = IRON_BLOCK;

        // Gold family
        LIGHT_WEIGHTED_PRESSURE_PLATE.parentMaterial = GOLD_BLOCK;
        BELL.parentMaterial = GOLD_BLOCK;
        RAW_GOLD_BLOCK.parentMaterial = GOLD_BLOCK;

        // Copper family
        LIGHTNING_ROD.parentMaterial = COPPER_BLOCK;
        RAW_COPPER_BLOCK.parentMaterial = COPPER_BLOCK;

        // Gem/mineral variants
        AMETHYST_CLUSTER.parentMaterial = AMETHYST_BLOCK;
        CRYING_OBSIDIAN.parentMaterial = OBSIDIAN;

        // Stone variants
        POLISHED_GRANITE.parentMaterial = GRANITE;
        POLISHED_DIORITE.parentMaterial = DIORITE;
        POLISHED_ANDESITE.parentMaterial = ANDESITE;
        COBBLESTONE_MAT.parentMaterial = STONE;
        MOSSY_COBBLESTONE_MAT.parentMaterial = STONE;
        STONE_BRICKS_MAT.parentMaterial = STONE;
        MOSSY_STONE_BRICKS_MAT.parentMaterial = STONE;
        SMOOTH_STONE.parentMaterial = STONE;
    }

    private static void register(Block block, MaterialBlock materialBlock) {
        BLOCK_MAP.put(block, materialBlock);
        if (materialBlock.primaryBlock == null) {
            materialBlock.primaryBlock = block;
        }
    }

    /** Returns the primary Minecraft block for this material (for icon rendering). May be null for unregistered entries. */
    public Block getPrimaryBlock() { return primaryBlock; }

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

        // Longest prefix match for texture variants (e.g., "stone_bricks_top" matches "stone_bricks", not "stone")
        String bestKey = null;
        int bestOrd = -1;
        for (Map.Entry<String, Integer> entry : map.entrySet()) {
            String prefix = entry.getKey();
            if (filename.startsWith(prefix) && filename.length() > prefix.length()
                && filename.charAt(prefix.length()) == '_') {
                if (bestKey == null || prefix.length() > bestKey.length()) {
                    bestKey = prefix;
                    bestOrd = entry.getValue();
                }
            }
        }
        return bestOrd;
    }
}
