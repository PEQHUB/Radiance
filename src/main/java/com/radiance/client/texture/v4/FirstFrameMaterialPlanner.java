package com.radiance.client.texture.v4;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.client.MinecraftClient;

/**
 * Plans the visible material set before the first world render frame.
 *
 * Phase 6 requirement: first-frame visible material set must be planned
 * before render. This planner collects visible material IDs from the
 * chunk build system and publishes them to TextureResidencySnapshot.
 *
 * CTM neighbor dependencies are included in the plan.
 */
public final class FirstFrameMaterialPlanner {

    private static volatile boolean planned = false;
    private static volatile long plannedGeneration = 0L;
    private static volatile int plannedVisibleCount = -1;
    private static volatile boolean emptyPlanAllowed = false;
    private static volatile String emptyPlanReason = "none";
    private static final Set<Integer> VISIBLE_MATERIALS = ConcurrentHashMap.newKeySet();

    private FirstFrameMaterialPlanner() {}

    /**
     * Plan visible materials for the current generation.
     * Called when the world is first loaded or after a resource reload.
     */
    public static void plan(long generation) {
        int visibleCount = VISIBLE_MATERIALS.size();
        if (planned && plannedGeneration == generation && plannedVisibleCount == visibleCount) return;

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.world == null) return;

        int visibleChunkCount = mc.worldRenderer != null ? (int) mc.worldRenderer.getChunkCount() : 0;

        Set<Integer> visible = Collections.unmodifiableSet(Set.copyOf(VISIBLE_MATERIALS));

        if (visibleChunkCount > 0 && visible.isEmpty()) {
            emptyPlanAllowed = false;
            emptyPlanReason = "visible_material_plan_empty_error";
        } else {
            emptyPlanAllowed = true;
            emptyPlanReason = "none";
        }

        TextureResidencySnapshot.publishVisiblePlan(generation, visible);

        planned = true;
        plannedGeneration = generation;
        plannedVisibleCount = visible.size();
    }

    /** Add a material to the visible set. */
    public static void addVisibleMaterial(int materialId) {
        if (VISIBLE_MATERIALS.add(materialId)) {
            planned = false;
        }
    }

    /** Reset for a new generation. */
    public static void reset(long generation) {
        planned = false;
        plannedGeneration = 0L;
        plannedVisibleCount = -1;
        emptyPlanAllowed = false;
        emptyPlanReason = "none";
        VISIBLE_MATERIALS.clear();
        TextureResidencySnapshot.resetForGeneration(generation);
    }

    public static boolean isPlanned() { return planned; }
    public static long plannedGeneration() { return plannedGeneration; }
    public static boolean isEmptyPlanAllowed() { return emptyPlanAllowed; }
    public static String emptyPlanReason() { return emptyPlanReason; }
    public static int visibleMaterialCount() { return VISIBLE_MATERIALS.size(); }
}
