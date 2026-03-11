package com.radiance.client.util;

import net.minecraft.util.math.BlockPos;

import java.util.HashMap;
import java.util.Map;

/**
 * Thread-local collector for light source positions during chunk building.
 * Each chunk build thread calls begin() before SectionBuilder.build() and
 * end() after, to collect all light-emitting block positions.
 *
 * Deduplicates by BlockPos (multiple quads per block produce only one light).
 */
public class ChunkLightCollector {

    public static class LightEntry {
        public final float worldX, worldY, worldZ;
        public final int typeId;

        public LightEntry(float worldX, float worldY, float worldZ, int typeId) {
            this.worldX = worldX;
            this.worldY = worldY;
            this.worldZ = worldZ;
            this.typeId = typeId;
        }
    }

    private static final ThreadLocal<Map<BlockPos, LightEntry>> LIGHTS =
        ThreadLocal.withInitial(HashMap::new);

    private static final ThreadLocal<Boolean> ACTIVE = ThreadLocal.withInitial(() -> false);

    public static void begin() {
        LIGHTS.get().clear();
        ACTIVE.set(true);
    }

    public static void addLight(BlockPos pos, LightSourceDef def) {
        if (!ACTIVE.get()) return;
        Map<BlockPos, LightEntry> map = LIGHTS.get();
        // Deduplicate: first quad for this block wins
        if (!map.containsKey(pos)) {
            map.put(pos.toImmutable(), new LightEntry(
                def.getWorldX(pos),
                def.getWorldY(pos),
                def.getWorldZ(pos),
                def.typeId
            ));
        }
    }

    public static Map<BlockPos, LightEntry> end() {
        ACTIVE.set(false);
        Map<BlockPos, LightEntry> result = new HashMap<>(LIGHTS.get());
        LIGHTS.get().clear();
        return result;
    }

    public static boolean isActive() {
        return ACTIVE.get();
    }
}
