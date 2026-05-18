package com.radiance.client.material;

/**
 * Material class enum for the unified SSBO material system.
 * Each value maps to a MaterialClassEntry in the GPU-side MaterialClassMapping buffer.
 * Stub — full implementation was lost during branch cleanup. Recreate from session handoff.
 */
public enum MaterialClass {
    GENERIC,
    IRON, GOLD, COPPER, NETHERITE, CHAIN, REDSTONE,
    DIAMOND, EMERALD, AMETHYST, LAPIS, QUARTZ, PRISMARINE,
    STONE, WOOD, GLASS, ICE, CERAMIC, TERRACOTTA,
    ORGANIC, BONE, WOOL, SOIL, SAND,
    WATER, LAVA, HONEY, SLIME;
}
