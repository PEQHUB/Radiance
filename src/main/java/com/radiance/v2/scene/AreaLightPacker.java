package com.radiance.v2.scene;

import com.radiance.client.util.ChunkLightCollector;
import com.radiance.v2.bridge.EngineBridge;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Aggregates per-chunk area lights and packs them into the 48-byte AreaLight
 * struct layout expected by the V2 engine (shared.hpp Data::AreaLight).
 *
 * Thread-safe: chunk build threads call {@link #setChunkLights} concurrently.
 * The render thread calls {@link #flush()} once per frame before EngineBridge.nativeTick()
 * to upload the full light set.
 *
 * <pre>
 * AreaLight layout (48 bytes, 3 x vec4, std430):
 *   vec3  position     (0..11)  camera-relative world position
 *   float halfExtent   (12..15) cube half-size
 *   vec3  color        (16..27) pre-computed emissive RGB (BT.709 for now)
 *   float intensity    (28..31) brightness scale
 *   vec3  _unused      (32..43) .x = stableId, .y = flickerStrength
 *   float radius       (44..47) max range in blocks
 * </pre>
 */
public class AreaLightPacker {

    /** Maximum area lights the GPU SSBO supports (set 1, binding 8). */
    private static final int MAX_AREA_LIGHTS = 512;

    /** Matches C++ LUMENS_TO_INTENSITY = 500.0f / 150.0f */
    private static final float LUMENS_TO_INTENSITY = 500.0f / 150.0f;

    /** Total light type count — must match C++ LIGHT_TYPE_COUNT and Java LightSourceRegistry.TYPE_COUNT. */
    private static final int LIGHT_TYPE_COUNT = 50;

    // --- Per-chunk light storage (chunk index -> array of raw light entries) ---
    // Mirrors V1's setChunkLights: each chunk reports its lights during rebuild.
    private static final ConcurrentHashMap<Long, LightEntry[]> chunkLights = new ConcurrentHashMap<>();

    // --- Light type definition table (mirrors C++ lights.hpp LIGHT_DEFS) ---
    // Each entry: halfExtent, lumens, radius, colorR, colorG, colorB, yOffset, flickerStrength
    private static final float[][] LIGHT_DEFS = new float[LIGHT_TYPE_COUNT][];

    static {
        // Format: { halfExtent, lumens, radius, R, G, B, yOffset, flickerStrength }
        // Must match MCVR/src/core/render/lights.hpp LIGHT_DEFS exactly.
        LIGHT_DEFS[ 0] = new float[]{ 0.10f, 3000.0f, 48.0f, 1.0f, 0.7f,  0.3f,   0.07f, 0.08f }; // TORCH
        LIGHT_DEFS[ 1] = new float[]{ 0.10f, 1600.0f, 40.0f, 0.3f, 0.8f,  0.9f,   0.12f, 0.04f }; // SOUL_TORCH
        LIGHT_DEFS[ 2] = new float[]{ 0.15f,  200.0f, 48.0f, 1.0f, 0.7f,  0.3f,  -0.1f,  0.03f }; // LANTERN
        LIGHT_DEFS[ 3] = new float[]{ 0.15f,  100.0f, 40.0f, 0.3f, 0.8f,  0.9f,  -0.1f,  0.02f }; // SOUL_LANTERN
        LIGHT_DEFS[ 4] = new float[]{ 0.15f,  800.0f, 48.0f, 1.0f, 0.7f,  0.3f,   0.15f, 0.05f }; // CAMPFIRE
        LIGHT_DEFS[ 5] = new float[]{ 0.15f,  400.0f, 40.0f, 0.3f, 0.8f,  0.9f,   0.15f, 0.04f }; // SOUL_CAMPFIRE
        LIGHT_DEFS[ 6] = new float[]{ 0.50f,  300.0f, 48.0f, 1.0f, 0.85f, 0.5f,   0.0f,  0.0f  }; // GLOWSTONE
        LIGHT_DEFS[ 7] = new float[]{ 0.50f,  250.0f, 48.0f, 0.7f, 0.85f, 1.0f,   0.0f,  0.0f  }; // SEA_LANTERN
        LIGHT_DEFS[ 8] = new float[]{ 0.50f,  250.0f, 48.0f, 1.0f, 0.6f,  0.3f,   0.0f,  0.0f  }; // SHROOMLIGHT
        LIGHT_DEFS[ 9] = new float[]{ 0.50f,   50.0f, 48.0f, 1.0f, 0.7f,  0.3f,   0.0f,  0.0f  }; // JACK_O_LANTERN
        LIGHT_DEFS[10] = new float[]{ 0.05f,  400.0f, 48.0f, 0.95f,0.9f,  1.0f,   0.0f,  0.0f  }; // END_ROD
        LIGHT_DEFS[11] = new float[]{ 0.30f, 2000.0f, 64.0f, 0.9f, 0.95f, 1.0f,   0.0f,  0.0f  }; // BEACON
        LIGHT_DEFS[12] = new float[]{ 0.50f,  200.0f, 48.0f, 1.0f, 0.9f,  0.5f,   0.0f,  0.0f  }; // OCHRE_FROGLIGHT
        LIGHT_DEFS[13] = new float[]{ 0.50f,  200.0f, 48.0f, 0.4f, 1.0f,  0.5f,   0.0f,  0.0f  }; // VERDANT_FROGLIGHT
        LIGHT_DEFS[14] = new float[]{ 0.50f,  200.0f, 48.0f, 0.9f, 0.6f,  0.8f,   0.0f,  0.0f  }; // PEARL_FROGLIGHT
        LIGHT_DEFS[15] = new float[]{ 0.05f,   20.0f, 32.0f, 1.0f, 0.2f,  0.1f,   0.35f, 0.06f }; // REDSTONE_TORCH
        LIGHT_DEFS[16] = new float[]{ 0.50f,  800.0f, 48.0f, 1.0f, 0.2f,  0.1f,   0.0f,  0.0f  }; // REDSTONE_LAMP
        LIGHT_DEFS[17] = new float[]{ 0.05f,   13.0f, 40.0f, 1.0f, 0.75f, 0.35f,  0.3f,  0.12f }; // CANDLE
        LIGHT_DEFS[18] = new float[]{ 0.05f,   13.0f, 40.0f, 1.0f, 0.75f, 0.35f,  0.3f,  0.12f }; // UNUSED (CANDLE_2)
        LIGHT_DEFS[19] = new float[]{ 0.05f,   13.0f, 40.0f, 1.0f, 0.75f, 0.35f,  0.3f,  0.12f }; // UNUSED (CANDLE_3)
        LIGHT_DEFS[20] = new float[]{ 0.05f,   13.0f, 40.0f, 1.0f, 0.75f, 0.35f,  0.3f,  0.12f }; // UNUSED (CANDLE_4)
        LIGHT_DEFS[21] = new float[]{ 0.05f,   10.0f, 48.0f, 1.0f, 0.75f, 0.35f,  0.35f, 0.03f }; // CAVE_VINES
        LIGHT_DEFS[22] = new float[]{ 0.05f,    5.0f, 32.0f, 0.4f, 0.8f,  0.6f,   0.0f,  0.0f  }; // GLOW_LICHEN
        LIGHT_DEFS[23] = new float[]{ 0.30f,  300.0f, 40.0f, 1.0f, 0.5f,  0.2f,   0.0f,  0.04f }; // FURNACE
        LIGHT_DEFS[24] = new float[]{ 0.30f,  500.0f, 40.0f, 1.0f, 0.5f,  0.2f,   0.0f,  0.04f }; // BLAST_FURNACE
        LIGHT_DEFS[25] = new float[]{ 0.30f,  200.0f, 40.0f, 1.0f, 0.5f,  0.2f,   0.0f,  0.04f }; // SMOKER
        LIGHT_DEFS[26] = new float[]{ 0.15f,   15.0f, 32.0f, 0.3f, 0.7f,  0.5f,   0.0f,  0.02f }; // ENDER_CHEST
        LIGHT_DEFS[27] = new float[]{ 0.30f,   40.0f, 40.0f, 0.6f, 0.2f,  0.9f,   0.0f,  0.03f }; // CRYING_OBSIDIAN
        LIGHT_DEFS[28] = new float[]{ 0.30f,   80.0f, 40.0f, 0.5f, 0.2f,  0.8f,   0.0f,  0.02f }; // NETHER_PORTAL
        LIGHT_DEFS[29] = new float[]{ 0.30f,  500.0f, 48.0f, 0.9f, 0.95f, 1.0f,   0.0f,  0.0f  }; // CONDUIT
        LIGHT_DEFS[30] = new float[]{ 0.30f,   20.0f, 28.0f, 1.0f, 0.6f,  0.2f,   0.0f,  0.0f  }; // RESPAWN_ANCHOR_1
        LIGHT_DEFS[31] = new float[]{ 0.30f,   50.0f, 32.0f, 1.0f, 0.6f,  0.2f,   0.0f,  0.0f  }; // RESPAWN_ANCHOR_2
        LIGHT_DEFS[32] = new float[]{ 0.30f,  100.0f, 40.0f, 1.0f, 0.6f,  0.2f,   0.0f,  0.0f  }; // RESPAWN_ANCHOR_3
        LIGHT_DEFS[33] = new float[]{ 0.30f,  200.0f, 48.0f, 1.0f, 0.6f,  0.2f,   0.0f,  0.0f  }; // RESPAWN_ANCHOR_4
        LIGHT_DEFS[34] = new float[]{ 0.05f,    8.0f, 24.0f, 0.7f, 0.5f,  0.9f,   0.0f,  0.0f  }; // AMETHYST_CLUSTER
        LIGHT_DEFS[35] = new float[]{ 0.05f,    5.0f, 24.0f, 0.7f, 0.5f,  0.9f,   0.0f,  0.0f  }; // LARGE_AMETHYST_BUD
        LIGHT_DEFS[36] = new float[]{ 0.50f,  600.0f, 48.0f, 1.0f, 0.7f,  0.4f,   0.0f,  0.0f  }; // COPPER_BULB
        LIGHT_DEFS[37] = new float[]{ 0.05f,    3.0f, 16.0f, 0.5f, 0.8f,  0.5f,   0.0f,  0.02f }; // ENCHANTING_TABLE
        LIGHT_DEFS[38] = new float[]{ 0.50f, 1500.0f, 48.0f, 1.0f, 0.4f,  0.1f,   0.0f,  0.0f  }; // LAVA
        LIGHT_DEFS[39] = new float[]{ 0.15f,  500.0f, 48.0f, 1.0f, 0.6f,  0.2f,   0.15f, 0.12f }; // FIRE
        LIGHT_DEFS[40] = new float[]{ 0.15f,  250.0f, 40.0f, 0.3f, 0.8f,  0.9f,   0.15f, 0.08f }; // SOUL_FIRE
        LIGHT_DEFS[41] = new float[]{ 0.50f,   80.0f, 24.0f, 1.0f, 0.3f,  0.1f,   0.0f,  0.0f  }; // MAGMA_BLOCK
        LIGHT_DEFS[42] = new float[]{ 0.15f,    2.0f, 16.0f, 0.2f, 0.5f,  0.5f,   0.0f,  0.02f }; // SCULK_SENSOR
        LIGHT_DEFS[43] = new float[]{ 0.30f,    3.0f, 16.0f, 0.2f, 0.5f,  0.5f,   0.0f,  0.02f }; // SCULK_CATALYST
        LIGHT_DEFS[44] = new float[]{ 0.05f,    1.0f, 16.0f, 0.2f, 0.5f,  0.5f,   0.0f,  0.0f  }; // SCULK_VEIN
        LIGHT_DEFS[45] = new float[]{ 0.50f,    1.0f, 16.0f, 0.15f,0.4f,  0.4f,   0.0f,  0.0f  }; // SCULK
        LIGHT_DEFS[46] = new float[]{ 0.30f,    3.0f, 16.0f, 0.2f, 0.5f,  0.5f,   0.0f,  0.0f  }; // SCULK_SHRIEKER
        LIGHT_DEFS[47] = new float[]{ 0.15f,    5.0f, 16.0f, 1.0f, 0.6f,  0.2f,   0.0f,  0.0f  }; // BREWING_STAND
        LIGHT_DEFS[48] = new float[]{ 0.50f,   30.0f, 48.0f, 0.3f, 0.1f,  0.5f,   0.0f,  0.0f  }; // END_PORTAL
        LIGHT_DEFS[49] = new float[]{ 0.15f,    3.0f, 16.0f, 0.4f, 0.7f,  0.4f,   0.0f,  0.02f }; // END_PORTAL_FRAME
    }

    // Indices into the LIGHT_DEFS sub-arrays
    private static final int DEF_HALF_EXTENT = 0;
    private static final int DEF_LUMENS      = 1;
    private static final int DEF_RADIUS      = 2;
    private static final int DEF_COLOR_R     = 3;
    private static final int DEF_COLOR_G     = 4;
    private static final int DEF_COLOR_B     = 5;
    private static final int DEF_Y_OFFSET    = 6;
    private static final int DEF_FLICKER     = 7;

    /** Compact per-chunk light entry. Packed from ChunkLightCollector data. */
    public static final class LightEntry {
        public final float worldX, worldY, worldZ;
        public final int typeId;

        public LightEntry(float worldX, float worldY, float worldZ, int typeId) {
            this.worldX = worldX;
            this.worldY = worldY;
            this.worldZ = worldZ;
            this.typeId = typeId;
        }
    }

    /**
     * Called from chunk build threads after lights are collected.
     * Stores the light entries for a given chunk index, replacing any previous data.
     * Pass an empty array or null to clear lights for a chunk.
     */
    public static void setChunkLights(long chunkIndex, LightEntry[] lights) {
        if (lights == null || lights.length == 0) {
            chunkLights.remove(chunkIndex);
        } else {
            chunkLights.put(chunkIndex, lights);
        }
    }

    /**
     * Convenience: convert from ChunkLightCollector output to our internal format
     * and store for a given chunk index.
     */
    public static void setChunkLightsFromCollector(long chunkIndex,
            Map<net.minecraft.util.math.BlockPos, ChunkLightCollector.LightEntry> collectedLights) {
        if (collectedLights == null || collectedLights.isEmpty()) {
            chunkLights.remove(chunkIndex);
            return;
        }
        LightEntry[] entries = new LightEntry[collectedLights.size()];
        int i = 0;
        for (ChunkLightCollector.LightEntry cl : collectedLights.values()) {
            entries[i++] = new LightEntry(cl.worldX, cl.worldY, cl.worldZ, cl.typeId);
        }
        chunkLights.put(chunkIndex, entries);
    }

    /**
     * Remove all lights for a given chunk (e.g. when chunk is invalidated).
     */
    public static void removeChunkLights(long chunkIndex) {
        chunkLights.remove(chunkIndex);
    }

    /**
     * Clear all stored lights (e.g. on world unload).
     */
    public static void clear() {
        chunkLights.clear();
    }

    /**
     * Pack all stored chunk lights into the 48-byte AreaLight format and upload
     * to the V2 engine via EngineBridge.submitAreaLights().
     *
     * Must be called on the render thread, once per frame, before EngineBridge.nativeTick().
     * Positions are stored as absolute world coordinates; the C++ side converts to
     * camera-relative in the scene resource service.
     */
    public static void flush() {
        if (!EngineBridge.isV2Active()) return;

        // Count total lights across all chunks
        int totalLights = 0;
        for (LightEntry[] entries : chunkLights.values()) {
            totalLights += entries.length;
        }

        if (totalLights == 0) {
            // Upload empty set to clear any stale lights on the GPU
            EngineBridge.submitAreaLights(0, 0, 0);
            return;
        }

        // Clamp to GPU max
        int lightCount = Math.min(totalLights, MAX_AREA_LIGHTS);

        // Allocate native buffer for lightCount * 48 bytes
        int dataSize = lightCount * 48;
        ByteBuffer buf = MemoryUtil.memAlloc(dataSize);
        try {
            int written = 0;
            outer:
            for (LightEntry[] entries : chunkLights.values()) {
                for (LightEntry entry : entries) {
                    if (written >= lightCount) break outer;
                    if (entry.typeId < 0 || entry.typeId >= LIGHT_TYPE_COUNT) continue;

                    float[] def = LIGHT_DEFS[entry.typeId];
                    int offset = written * 48;

                    // vec3 position (world-space; C++ converts to camera-relative)
                    buf.putFloat(offset,      entry.worldX);
                    buf.putFloat(offset +  4, entry.worldY + def[DEF_Y_OFFSET]);
                    buf.putFloat(offset +  8, entry.worldZ);
                    // float halfExtent
                    buf.putFloat(offset + 12, def[DEF_HALF_EXTENT]);
                    // vec3 color (BT.709 fallback; C++ can apply BT.2020 conversion)
                    buf.putFloat(offset + 16, def[DEF_COLOR_R]);
                    buf.putFloat(offset + 20, def[DEF_COLOR_G]);
                    buf.putFloat(offset + 24, def[DEF_COLOR_B]);
                    // float intensity = lumens * LUMENS_TO_INTENSITY
                    buf.putFloat(offset + 28, def[DEF_LUMENS] * LUMENS_TO_INTENSITY);
                    // vec3 _unused (.x = stableId hash, .y = flickerStrength, .z = 0)
                    // Use a simple hash of world position for stable ReSTIR DI tracking
                    float stableId = Float.intBitsToFloat(
                        positionHash(entry.worldX, entry.worldY, entry.worldZ));
                    buf.putFloat(offset + 32, stableId);
                    buf.putFloat(offset + 36, def[DEF_FLICKER]);
                    buf.putFloat(offset + 40, 0.0f);
                    // float radius
                    buf.putFloat(offset + 44, def[DEF_RADIUS]);

                    written++;
                }
            }

            long ptr = MemoryUtil.memAddress(buf);
            EngineBridge.submitAreaLights(ptr, written * 48, written);
        } finally {
            MemoryUtil.memFree(buf);
        }
    }

    /**
     * Deterministic hash for stable light identification across frames.
     * Uses the same approach as V1 world_prepare.cpp for ReSTIR DI.
     */
    private static int positionHash(float x, float y, float z) {
        int ix = Float.floatToRawIntBits(x);
        int iy = Float.floatToRawIntBits(y);
        int iz = Float.floatToRawIntBits(z);
        // Simple mixing (same as C++ world_prepare stable ID)
        int h = ix;
        h = h * 31 + iy;
        h = h * 31 + iz;
        h ^= (h >>> 16);
        return h;
    }
}
