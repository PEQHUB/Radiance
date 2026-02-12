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

public enum EmissiveBlock {
    LAVA("lava", 1.0f, () -> Options.emissionLava, v -> Options.emissionLava = v),
    FIRE("fire", 1.0f, () -> Options.emissionFire, v -> Options.emissionFire = v),
    SOUL_FIRE("soul_fire", 1.0f, () -> Options.emissionSoulFire, v -> Options.emissionSoulFire = v),
    TORCH("torch", 1.0f, () -> Options.emissionTorch, v -> Options.emissionTorch = v),
    SOUL_TORCH("soul_torch", 1.0f, () -> Options.emissionSoulTorch, v -> Options.emissionSoulTorch = v),
    LANTERN("lantern", 1.0f, () -> Options.emissionLantern, v -> Options.emissionLantern = v),
    SOUL_LANTERN("soul_lantern", 1.0f, () -> Options.emissionSoulLantern, v -> Options.emissionSoulLantern = v),
    CAMPFIRE("campfire", 1.0f, () -> Options.emissionCampfire, v -> Options.emissionCampfire = v),
    SOUL_CAMPFIRE("soul_campfire", 1.0f, () -> Options.emissionSoulCampfire, v -> Options.emissionSoulCampfire = v),
    GLOWSTONE("glowstone", 1.0f, () -> Options.emissionGlowstone, v -> Options.emissionGlowstone = v),
    SHROOMLIGHT("shroomlight", 1.0f, () -> Options.emissionShroomlight, v -> Options.emissionShroomlight = v),
    SEA_LANTERN("sea_lantern", 1.0f, () -> Options.emissionSeaLantern, v -> Options.emissionSeaLantern = v),
    FROGLIGHT("froglight", 1.0f, () -> Options.emissionFroglight, v -> Options.emissionFroglight = v),
    MAGMA_BLOCK("magma_block", 1.0f, () -> Options.emissionMagmaBlock, v -> Options.emissionMagmaBlock = v),
    BEACON("beacon", 1.0f, () -> Options.emissionBeacon, v -> Options.emissionBeacon = v),
    END_ROD("end_rod", 1.0f, () -> Options.emissionEndRod, v -> Options.emissionEndRod = v),
    JACK_O_LANTERN("jack_o_lantern", 1.0f, () -> Options.emissionJackOLantern, v -> Options.emissionJackOLantern = v),
    NETHER_PORTAL("nether_portal", 1.0f, () -> Options.emissionNetherPortal, v -> Options.emissionNetherPortal = v),
    CRYING_OBSIDIAN("crying_obsidian", 0.8f, () -> Options.emissionCryingObsidian, v -> Options.emissionCryingObsidian = v),
    RESPAWN_ANCHOR("respawn_anchor", 1.0f, () -> Options.emissionRespawnAnchor, v -> Options.emissionRespawnAnchor = v),
    CONDUIT("conduit", 1.0f, () -> Options.emissionConduit, v -> Options.emissionConduit = v),
    AMETHYST_CLUSTER("amethyst_cluster", 0.5f, () -> Options.emissionAmethystCluster, v -> Options.emissionAmethystCluster = v),
    SCULK_SENSOR("sculk_sensor", 0.5f, () -> Options.emissionSculkSensor, v -> Options.emissionSculkSensor = v),
    SCULK_CATALYST("sculk_catalyst", 0.5f, () -> Options.emissionSculkCatalyst, v -> Options.emissionSculkCatalyst = v),
    SCULK_VEIN("sculk_vein", 0.3f, () -> Options.emissionSculkVein, v -> Options.emissionSculkVein = v),
    SCULK("sculk", 0.2f, () -> Options.emissionSculk, v -> Options.emissionSculk = v),
    SCULK_SHRIEKER("sculk_shrieker", 0.5f, () -> Options.emissionSculkShrieker, v -> Options.emissionSculkShrieker = v),
    BREWING_STAND("brewing_stand", 0.5f, () -> Options.emissionBrewingStand, v -> Options.emissionBrewingStand = v),
    END_PORTAL("end_portal", 1.0f, () -> Options.emissionEndPortal, v -> Options.emissionEndPortal = v);

    private final String id;
    private final float defaultValue;
    private final Supplier<Float> valueSupplier;
    private final Consumer<Float> valueSetter;

    EmissiveBlock(String id, float defaultValue, Supplier<Float> valueSupplier, Consumer<Float> valueSetter) {
        this.id = id;
        this.defaultValue = defaultValue;
        this.valueSupplier = valueSupplier;
        this.valueSetter = valueSetter;
    }

    public void setValue(float value, boolean write) {
        valueSetter.accept(value);
        if (write) {
            Options.overwriteConfig();
            Options.nativeRebuildChunks();
        }
    }


    public float getValue() {
        return valueSupplier.get();
    }

    public float getDefaultValue() {
        return defaultValue;
    }

    public String getId() {
        return id;
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
    }

    private static void register(Block block, EmissiveBlock emissiveBlock) {
        BLOCK_MAP.put(block, emissiveBlock);
    }

    public static float getEmission(Block block) {
        EmissiveBlock eb = BLOCK_MAP.get(block);
        return eb != null ? eb.getValue() : 0.0f;
    }
    
    public static boolean isEmissive(Block block) {
        return BLOCK_MAP.containsKey(block);
    }
}
