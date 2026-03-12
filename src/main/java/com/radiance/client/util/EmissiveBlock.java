package com.radiance.client.util;

import com.radiance.client.option.Options;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Emissive block definitions with physical surface luminance in nits (cd/m²).
 * The surfaceNits field is the physical brightness of the block's emitting surface.
 * The user slider (0-1, default 1.0) scales this value.
 * Effective nits = surfaceNits * userSlider.
 *
 * Thermal blocks have colorTemperature > 0 (Kelvin) and get a temperature slider.
 * When temperature changes: surfaceNits = blackbodyLuminance(T) × emissivity.
 * lightTypeId maps to C++ LightTypeId enum for area light color override.
 */
public enum EmissiveBlock {
    // Thermal emitters: Planck(λ,T) × CIE V(λ) integration × emissivity
    // L = 683 × ∫ B(λ,T)·V(λ)dλ × ε [cd/m²]
    // Constructor: (id, defaultValue, surfaceNits, lightTypeId, defaultTempCelsius, wavelengthNm, purityPct, ...)
    LAVA("lava", 1.0f, 2300.0f, 38, 1050, 0, 0, () -> Options.emissionLava, v -> Options.emissionLava = v),           // 1323K basalt, ε=0.65
    FIRE("fire", 1.0f, 14000.0f, 39, 1227, 0, 0, () -> Options.emissionFire, v -> Options.emissionFire = v),           // 1500K soot flame, ε=0.35
    SOUL_FIRE("soul_fire", 1.0f, 6000.0f, 40, 1727, 460, 80, () -> Options.emissionSoulFire, v -> Options.emissionSoulFire = v), // 2000K sulfur blue
    TORCH("torch", 1.0f, 110000.0f, 0, 1527, 0, 0, () -> Options.emissionTorch, v -> Options.emissionTorch = v),     // 1800K soot flame, ε=0.35
    SOUL_TORCH("soul_torch", 1.0f, 6000.0f, 1, 1727, 460, 80, () -> Options.emissionSoulTorch, v -> Options.emissionSoulTorch = v), // 2000K sulfur blue

    // Enclosed flames: flame × glass/housing transmission
    LANTERN("lantern", 1.0f, 30000.0f, 2, 1927, 0, 0, () -> Options.emissionLantern, v -> Options.emissionLantern = v),       // 2200K oil lamp
    SOUL_LANTERN("soul_lantern", 1.0f, 3000.0f, 3, 1727, 460, 80, () -> Options.emissionSoulLantern, v -> Options.emissionSoulLantern = v), // 2000K sulfur blue
    CAMPFIRE("campfire", 1.0f, 14000.0f, 4, 1227, 0, 0, () -> Options.emissionCampfire, v -> Options.emissionCampfire = v),   // 1500K soot flame, ε=0.35
    SOUL_CAMPFIRE("soul_campfire", 1.0f, 6000.0f, 5, 1727, 460, 80, () -> Options.emissionSoulCampfire, v -> Options.emissionSoulCampfire = v), // 2000K sulfur blue

    // Mineral/bioluminescent glow: 20-150 nits (non-thermal)
    GLOWSTONE("glowstone", 1.0f, 150.0f, -1, 0, 0, 0, () -> Options.emissionGlowstone, v -> Options.emissionGlowstone = v),
    SHROOMLIGHT("shroomlight", 1.0f, 40.0f, -1, 0, 0, 0, () -> Options.emissionShroomlight, v -> Options.emissionShroomlight = v),
    SEA_LANTERN("sea_lantern", 1.0f, 60.0f, -1, 0, 0, 0, () -> Options.emissionSeaLantern, v -> Options.emissionSeaLantern = v),
    FROGLIGHT("froglight", 1.0f, 80.0f, -1, 0, 0, 0, () -> Options.emissionFroglight, v -> Options.emissionFroglight = v),

    // Hot surface: cooling lava crust, blackbody 1200K, ε=0.90
    MAGMA_BLOCK("magma_block", 1.0f, 40.0f, 41, 927, 0, 0, () -> Options.emissionMagmaBlock, v -> Options.emissionMagmaBlock = v),

    // Constructed lights
    BEACON("beacon", 1.0f, 80000.0f, -1, 0, 0, 0, () -> Options.emissionBeacon, v -> Options.emissionBeacon = v),           // non-thermal focused beam
    END_ROD("end_rod", 1.0f, 3000.0f, -1, 0, 0, 0, () -> Options.emissionEndRod, v -> Options.emissionEndRod = v),         // non-thermal
    JACK_O_LANTERN("jack_o_lantern", 1.0f, 15000.0f, 9, 1627, 0, 0, () -> Options.emissionJackOLantern, v -> Options.emissionJackOLantern = v), // 1900K candle in pumpkin

    // Magical/portal glow: 15-200 nits (non-thermal)
    NETHER_PORTAL("nether_portal", 1.0f, 150.0f, -1, 0, 0, 0, () -> Options.emissionNetherPortal, v -> Options.emissionNetherPortal = v),
    CRYING_OBSIDIAN("crying_obsidian", 1.0f, 60.0f, -1, 0, 0, 0, () -> Options.emissionCryingObsidian, v -> Options.emissionCryingObsidian = v),
    RESPAWN_ANCHOR("respawn_anchor", 1.0f, 80.0f, -1, 0, 0, 0, () -> Options.emissionRespawnAnchor, v -> Options.emissionRespawnAnchor = v),
    CONDUIT("conduit", 1.0f, 200.0f, -1, 0, 0, 0, () -> Options.emissionConduit, v -> Options.emissionConduit = v),

    // Crystal: subtle glow (non-thermal)
    AMETHYST_CLUSTER("amethyst_cluster", 1.0f, 8.0f, -1, 0, 0, 0, () -> Options.emissionAmethystCluster, v -> Options.emissionAmethystCluster = v),

    // Deep dark sculk: 0.3-2 nits (non-thermal bioluminescence)
    SCULK_SENSOR("sculk_sensor", 1.0f, 2.0f, -1, 0, 0, 0, () -> Options.emissionSculkSensor, v -> Options.emissionSculkSensor = v),
    SCULK_CATALYST("sculk_catalyst", 1.0f, 2.0f, -1, 0, 0, 0, () -> Options.emissionSculkCatalyst, v -> Options.emissionSculkCatalyst = v),
    SCULK_VEIN("sculk_vein", 1.0f, 0.3f, -1, 0, 0, 0, () -> Options.emissionSculkVein, v -> Options.emissionSculkVein = v),
    SCULK("sculk", 1.0f, 0.5f, -1, 0, 0, 0, () -> Options.emissionSculk, v -> Options.emissionSculk = v),
    SCULK_SHRIEKER("sculk_shrieker", 1.0f, 2.0f, -1, 0, 0, 0, () -> Options.emissionSculkShrieker, v -> Options.emissionSculkShrieker = v),

    // Utility blocks
    BREWING_STAND("brewing_stand", 1.0f, 1500.0f, 47, 1727, 0, 0, () -> Options.emissionBrewingStand, v -> Options.emissionBrewingStand = v), // 2000K pilot flame
    END_PORTAL("end_portal", 1.0f, 20.0f, -1, 0, 0, 0, () -> Options.emissionEndPortal, v -> Options.emissionEndPortal = v),

    // Redstone / misc (non-thermal)
    REDSTONE_TORCH("redstone_torch", 1.0f, 300.0f, -1, 0, 0, 0, () -> Options.emissionRedstoneTorch, v -> Options.emissionRedstoneTorch = v),
    REDSTONE_LAMP("redstone_lamp", 1.0f, 5000.0f, -1, 0, 0, 0, () -> Options.emissionRedstoneLamp, v -> Options.emissionRedstoneLamp = v),
    CANDLE("candle", 1.0f, 78000.0f, 17, 1527, 0, 0, () -> Options.emissionCandle, v -> Options.emissionCandle = v),           // 1800K small flame, ε=0.25
    CAVE_VINES("cave_vines", 1.0f, 8.0f, -1, 0, 0, 0, () -> Options.emissionCaveVines, v -> Options.emissionCaveVines = v),
    GLOW_LICHEN("glow_lichen", 1.0f, 0.5f, -1, 0, 0, 0, () -> Options.emissionGlowLichen, v -> Options.emissionGlowLichen = v),
    FURNACE("furnace", 1.0f, 5500.0f, 23, 1127, 0, 0, () -> Options.emissionFurnace, v -> Options.emissionFurnace = v),       // 1400K, ε=0.70
    BLAST_FURNACE("blast_furnace", 1.0f, 58000.0f, 24, 1327, 0, 0, () -> Options.emissionBlastFurnace, v -> Options.emissionBlastFurnace = v), // 1600K, ε=0.65
    SMOKER("smoker", 1.0f, 3200.0f, 25, 1127, 0, 0, () -> Options.emissionSmoker, v -> Options.emissionSmoker = v),           // 1400K, ε=0.40
    ENDER_CHEST("ender_chest", 1.0f, 10.0f, -1, 0, 0, 0, () -> Options.emissionEnderChest, v -> Options.emissionEnderChest = v),
    COPPER_BULB("copper_bulb", 1.0f, 15000.0f, 36, 2427, 0, 0, () -> Options.emissionCopperBulb, v -> Options.emissionCopperBulb = v), // 2700K incandescent
    ENCHANTING_TABLE("enchanting_table", 1.0f, 3.0f, -1, 0, 0, 0, () -> Options.emissionEnchantingTable, v -> Options.emissionEnchantingTable = v),

    // 1.21 additions
    CALIBRATED_SCULK_SENSOR("calibrated_sculk_sensor", 1.0f, 3.0f, -1, 0, 0, 0, () -> Options.emissionCalibratedSculkSensor, v -> Options.emissionCalibratedSculkSensor = v),
    SEA_PICKLE("sea_pickle", 1.0f, 15.0f, -1, 0, 0, 0, () -> Options.emissionSeaPickle, v -> Options.emissionSeaPickle = v),
    END_GATEWAY("end_gateway", 1.0f, 30.0f, -1, 0, 0, 0, () -> Options.emissionEndGateway, v -> Options.emissionEndGateway = v),
    TRIAL_SPAWNER("trial_spawner", 1.0f, 5.0f, -1, 0, 0, 0, () -> Options.emissionTrialSpawner, v -> Options.emissionTrialSpawner = v),
    VAULT("vault", 1.0f, 5.0f, -1, 0, 0, 0, () -> Options.emissionVault, v -> Options.emissionVault = v);

    private final String id;
    private final float defaultValue;
    private final float defaultSurfaceNits; // Original physical luminance at default temperature
    private float surfaceNits; // Mutable: updated by temperature slider
    private final int lightTypeId; // C++ LightTypeId for area light override (-1 = none)
    private final int defaultTemperatureCelsius; // 0 = non-thermal
    private final int defaultWavelengthNm; // 0 = pure blackbody, 380-780 = spectral line
    private final int defaultPurityPercent; // 0-100, blend factor between blackbody and spectral
    private float emissivity; // Derived: surfaceNits / BB(defaultTemp). Set in static init.
    private final Supplier<Float> valueSupplier;
    private final Consumer<Float> valueSetter;

    EmissiveBlock(String id, float defaultValue, float surfaceNits, int lightTypeId, int defaultTemperatureCelsius,
                  int defaultWavelengthNm, int defaultPurityPercent,
                  Supplier<Float> valueSupplier, Consumer<Float> valueSetter) {
        this.id = id;
        this.defaultValue = defaultValue;
        this.defaultSurfaceNits = surfaceNits;
        this.surfaceNits = surfaceNits;
        this.lightTypeId = lightTypeId;
        this.defaultTemperatureCelsius = defaultTemperatureCelsius;
        this.defaultWavelengthNm = defaultWavelengthNm;
        this.defaultPurityPercent = defaultPurityPercent;
        this.valueSupplier = valueSupplier;
        this.valueSetter = valueSetter;
    }

    public void setValue(float value, boolean write) {
        valueSetter.accept(value);
        if (write) {
            Options.overwriteConfig();
            // No chunk rebuild needed — UBO multiplier updated each frame in updateWorldUniform()
        }
    }

    /** Update the physical surface luminance (used by temperature slider). */
    public void setSurfaceNits(float nits) {
        this.surfaceNits = nits;
    }

    public float getValue() {
        return valueSupplier.get();
    }

    public float getDefaultValue() {
        return defaultValue;
    }

    public float getSurfaceNits() {
        return surfaceNits;
    }

    /**
     * Returns effective emission in nits: surfaceNits * userScale.
     */
    public float getEffectiveNits() {
        return surfaceNits * valueSupplier.get();
    }

    public String getId() {
        return id;
    }

    public int getLightTypeId() {
        return lightTypeId;
    }

    public int getDefaultTemperatureCelsius() {
        return defaultTemperatureCelsius;
    }

    public float getDefaultSurfaceNits() {
        return defaultSurfaceNits;
    }

    public float getEmissivity() {
        return emissivity;
    }

    public int getDefaultWavelengthNm() {
        return defaultWavelengthNm;
    }

    public int getDefaultPurityPercent() {
        return defaultPurityPercent;
    }

    /** Returns true if this block has a physical blackbody temperature (gets a temperature slider). */
    public boolean isThermal() {
        return defaultTemperatureCelsius > 0;
    }

    // ========================================================================
    // Blackbody luminance from Planck's law
    // L = 683 × ∫ B(λ,T) × V(λ) dλ  [cd/m²]
    // Uses CIE 1931 2-degree observer Y (photopic luminous efficiency)
    // ========================================================================

    /** CIE 1931 Y color matching function (380-780nm, 5nm intervals, 81 entries) */
    private static final float[] CIE_Y = {
        0.000039f, 0.000064f, 0.000120f, 0.000217f, 0.000396f,
        0.000640f, 0.001210f, 0.002180f, 0.004000f, 0.007300f,
        0.011600f, 0.016840f, 0.023000f, 0.029800f, 0.038000f,
        0.048000f, 0.060000f, 0.073900f, 0.090980f, 0.112600f,
        0.139020f, 0.169300f, 0.208020f, 0.258600f, 0.323000f,
        0.407300f, 0.503000f, 0.608200f, 0.710000f, 0.793200f,
        0.862000f, 0.914850f, 0.954000f, 0.980300f, 0.994950f,
        1.000000f, 0.995000f, 0.978600f, 0.952000f, 0.915400f,
        0.870000f, 0.816300f, 0.757000f, 0.694900f, 0.631000f,
        0.566800f, 0.503000f, 0.441200f, 0.381000f, 0.321000f,
        0.265000f, 0.217000f, 0.175000f, 0.138200f, 0.107000f,
        0.081600f, 0.061000f, 0.044580f, 0.032000f, 0.023200f,
        0.017000f, 0.011920f, 0.008210f, 0.005723f, 0.004102f,
        0.002929f, 0.002091f, 0.001484f, 0.001047f, 0.000740f,
        0.000520f, 0.000361f, 0.000249f, 0.000172f, 0.000120f,
        0.000085f, 0.000060f, 0.000042f, 0.000030f, 0.000021f,
        0.000015f
    };

    /** c2 = hc/k = 14388 μm·K (second radiation constant, CODATA 2018) */
    private static final float C2 = 14388.0f;
    /** 2hc² = 1.191e-16 W·m² (first radiation constant for spectral radiance) */
    private static final double TWO_HC2 = 1.191042952e-16;

    /**
     * Compute absolute blackbody luminance in cd/m² (nits) at temperature T.
     * Integrates Planck spectral radiance × CIE photopic response V(λ) = CIE_Y.
     * L = 683 × Δλ × Σ [ 2hc²/λ⁵ / (exp(c2/(λT)) - 1) × V(λ) ]
     * @param tempK Temperature in Kelvin (valid range ~500-5000K)
     * @return Luminance in cd/m²
     */
    public static float blackbodyLuminance(float tempK) {
        double sum = 0.0;
        for (int i = 0; i < 81; i++) {
            double lambda_m = (380.0 + i * 5.0) * 1e-9; // wavelength in meters
            double lambda_um = lambda_m * 1e6; // wavelength in micrometers
            double x = C2 / (lambda_um * tempK);
            if (x > 40.0) continue; // negligible contribution
            double planck = TWO_HC2 / (Math.pow(lambda_m, 5) * Math.expm1(x));
            sum += planck * CIE_Y[i];
        }
        // 683 lm/W × 5nm step × sum (W/(m² sr m) × dimensionless)
        // 5nm = 5e-9 m
        return (float)(683.0 * 5e-9 * sum);
    }

    // Derive emissivity for thermal blocks: ε = surfaceNits / BB(defaultTemp)
    // Must come after CIE_Y is initialized (static fields init in declaration order).
    static {
        for (EmissiveBlock b : values()) {
            if (b.defaultTemperatureCelsius > 0) {
                float defaultK = b.defaultTemperatureCelsius + 273.15f;
                float bb = blackbodyLuminance(defaultK);
                b.emissivity = (bb > 0) ? b.surfaceNits / bb : 0;
            }
        }
    }

    private static final Map<Block, EmissiveBlock> BLOCK_MAP = new HashMap<>();

    static {
        register(Blocks.LAVA, LAVA);
        register(Blocks.FIRE, FIRE);
        register(Blocks.SOUL_FIRE, SOUL_FIRE);
        register(Blocks.TORCH, TORCH);
        register(Blocks.WALL_TORCH, TORCH);
        register(Blocks.SOUL_TORCH, SOUL_TORCH);
        register(Blocks.SOUL_WALL_TORCH, SOUL_TORCH);
        register(Blocks.LANTERN, LANTERN);
        register(Blocks.SOUL_LANTERN, SOUL_LANTERN);
        register(Blocks.CAMPFIRE, CAMPFIRE);
        register(Blocks.SOUL_CAMPFIRE, SOUL_CAMPFIRE);
        register(Blocks.GLOWSTONE, GLOWSTONE);
        register(Blocks.SHROOMLIGHT, SHROOMLIGHT);
        register(Blocks.SEA_LANTERN, SEA_LANTERN);
        register(Blocks.OCHRE_FROGLIGHT, FROGLIGHT);
        register(Blocks.VERDANT_FROGLIGHT, FROGLIGHT);
        register(Blocks.PEARLESCENT_FROGLIGHT, FROGLIGHT);
        register(Blocks.MAGMA_BLOCK, MAGMA_BLOCK);
        register(Blocks.BEACON, BEACON);
        register(Blocks.END_ROD, END_ROD);
        register(Blocks.JACK_O_LANTERN, JACK_O_LANTERN);
        register(Blocks.NETHER_PORTAL, NETHER_PORTAL);
        register(Blocks.CRYING_OBSIDIAN, CRYING_OBSIDIAN);
        register(Blocks.RESPAWN_ANCHOR, RESPAWN_ANCHOR);
        register(Blocks.CONDUIT, CONDUIT);
        register(Blocks.AMETHYST_CLUSTER, AMETHYST_CLUSTER);
        register(Blocks.LARGE_AMETHYST_BUD, AMETHYST_CLUSTER);
        register(Blocks.MEDIUM_AMETHYST_BUD, AMETHYST_CLUSTER);
        register(Blocks.SMALL_AMETHYST_BUD, AMETHYST_CLUSTER);
        register(Blocks.SCULK_SENSOR, SCULK_SENSOR);
        register(Blocks.SCULK_CATALYST, SCULK_CATALYST);
        register(Blocks.SCULK_VEIN, SCULK_VEIN);
        register(Blocks.SCULK, SCULK);
        register(Blocks.SCULK_SHRIEKER, SCULK_SHRIEKER);
        register(Blocks.BREWING_STAND, BREWING_STAND);
        register(Blocks.END_PORTAL, END_PORTAL);
        register(Blocks.END_PORTAL_FRAME, END_PORTAL);
        // New: formerly area-light-only blocks
        register(Blocks.REDSTONE_TORCH, REDSTONE_TORCH);
        register(Blocks.REDSTONE_WALL_TORCH, REDSTONE_TORCH);
        register(Blocks.REDSTONE_LAMP, REDSTONE_LAMP);
        register(Blocks.CANDLE, CANDLE);
        register(Blocks.WHITE_CANDLE, CANDLE);
        register(Blocks.ORANGE_CANDLE, CANDLE);
        register(Blocks.MAGENTA_CANDLE, CANDLE);
        register(Blocks.LIGHT_BLUE_CANDLE, CANDLE);
        register(Blocks.YELLOW_CANDLE, CANDLE);
        register(Blocks.LIME_CANDLE, CANDLE);
        register(Blocks.PINK_CANDLE, CANDLE);
        register(Blocks.GRAY_CANDLE, CANDLE);
        register(Blocks.LIGHT_GRAY_CANDLE, CANDLE);
        register(Blocks.CYAN_CANDLE, CANDLE);
        register(Blocks.PURPLE_CANDLE, CANDLE);
        register(Blocks.BLUE_CANDLE, CANDLE);
        register(Blocks.BROWN_CANDLE, CANDLE);
        register(Blocks.GREEN_CANDLE, CANDLE);
        register(Blocks.RED_CANDLE, CANDLE);
        register(Blocks.BLACK_CANDLE, CANDLE);
        register(Blocks.CAVE_VINES, CAVE_VINES);
        register(Blocks.CAVE_VINES_PLANT, CAVE_VINES);
        register(Blocks.GLOW_LICHEN, GLOW_LICHEN);
        register(Blocks.FURNACE, FURNACE);
        register(Blocks.BLAST_FURNACE, BLAST_FURNACE);
        register(Blocks.SMOKER, SMOKER);
        register(Blocks.ENDER_CHEST, ENDER_CHEST);
        register(Blocks.COPPER_BULB, COPPER_BULB);
        register(Blocks.EXPOSED_COPPER_BULB, COPPER_BULB);
        register(Blocks.WEATHERED_COPPER_BULB, COPPER_BULB);
        register(Blocks.OXIDIZED_COPPER_BULB, COPPER_BULB);
        register(Blocks.WAXED_COPPER_BULB, COPPER_BULB);
        register(Blocks.WAXED_EXPOSED_COPPER_BULB, COPPER_BULB);
        register(Blocks.WAXED_WEATHERED_COPPER_BULB, COPPER_BULB);
        register(Blocks.WAXED_OXIDIZED_COPPER_BULB, COPPER_BULB);
        register(Blocks.ENCHANTING_TABLE, ENCHANTING_TABLE);
        register(Blocks.CALIBRATED_SCULK_SENSOR, CALIBRATED_SCULK_SENSOR);
        register(Blocks.SEA_PICKLE, SEA_PICKLE);
        register(Blocks.END_GATEWAY, END_GATEWAY);
        register(Blocks.TRIAL_SPAWNER, TRIAL_SPAWNER);
        register(Blocks.VAULT, VAULT);
    }

    private static void register(Block block, EmissiveBlock emissiveBlock) {
        BLOCK_MAP.put(block, emissiveBlock);
    }

    /**
     * Returns the EmissiveBlock for a given block, or null if not registered.
     */
    public static EmissiveBlock fromBlock(Block block) {
        return BLOCK_MAP.get(block);
    }

    /**
     * Returns effective emission in nits for the block, or 0 if not registered.
     */
    public static float getEmissionNits(Block block) {
        EmissiveBlock eb = BLOCK_MAP.get(block);
        return eb != null ? eb.getEffectiveNits() : 0.0f;
    }

    /**
     * @deprecated Use getEmissionNits() for physical nit values.
     */
    public static float getEmission(Block block) {
        EmissiveBlock eb = BLOCK_MAP.get(block);
        return eb != null ? eb.getValue() : 0.0f;
    }

    public static boolean isEmissive(Block block) {
        return BLOCK_MAP.containsKey(block);
    }
}
