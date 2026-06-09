package com.radiance.client.texture.v4;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

import com.google.gson.JsonObject;

/**
 * Snapshot of texture residency state for first-frame readiness evaluation.
 *
 * Tracks which materials are visible, resident, and failed.
 * This is the Java-side mirror of native page readiness state.
 * The first-frame readiness gate uses this to determine strict readiness
 * without timeout-based success.
 */
public record TextureResidencySnapshot(
    long generation,
    boolean visibleMaterialSetKnown,
    int visibleMaterialCount,
    int residentVisibleMaterialCount,
    int failedVisibleMaterialCount,
    long pendingVisibleUploadBytes
) {

    private static final AtomicReference<TextureResidencySnapshot> CURRENT =
        new AtomicReference<>(new TextureResidencySnapshot(0, false, 0, 0, 0, 0));

    private static final Set<Integer> VISIBLE = ConcurrentHashMap.newKeySet();
    private static final Set<Integer> RESIDENT = ConcurrentHashMap.newKeySet();
    private static final Set<Integer> FAILED = ConcurrentHashMap.newKeySet();

    /** Get the current snapshot for a generation. */
    public static TextureResidencySnapshot current(long generation) {
        TextureResidencySnapshot snapshot = CURRENT.get();
        return snapshot.generation == generation ? snapshot
            : new TextureResidencySnapshot(generation, false, 0, 0, 0, 0);
    }

    /** Publish the visible material plan for a generation. */
    public static void publishVisiblePlan(long generation, Set<Integer> materialIds) {
        VISIBLE.clear();
        RESIDENT.clear();
        FAILED.clear();
        if (materialIds != null) VISIBLE.addAll(materialIds);
        publish(generation, 0L);
    }

    /** Mark materials as resident. */
    public static void markResident(long generation, Set<Integer> materialIds, long pendingBytes) {
        if (materialIds != null) RESIDENT.addAll(materialIds);
        publish(generation, pendingBytes);
    }

    /** Mark materials as failed. */
    public static void markFailed(long generation, Set<Integer> materialIds, long pendingBytes) {
        if (materialIds != null) FAILED.addAll(materialIds);
        publish(generation, pendingBytes);
    }

    /** Reset for a new generation. */
    public static void resetForGeneration(long generation) {
        VISIBLE.clear();
        RESIDENT.clear();
        FAILED.clear();
        CURRENT.set(new TextureResidencySnapshot(generation, false, 0, 0, 0, 0));
    }

    /** Count of visible materials that are neither resident nor failed (fallback). */
    public int visibleFallbackMaterialCount() {
        return Math.max(0, visibleMaterialCount - residentVisibleMaterialCount - failedVisibleMaterialCount);
    }

    /** Serialize to JSON for DebugBridge. */
    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("generation", generation);
        json.addProperty("visibleMaterialSetKnown", visibleMaterialSetKnown);
        json.addProperty("visibleMaterialCount", visibleMaterialCount);
        json.addProperty("residentVisibleMaterialCount", residentVisibleMaterialCount);
        json.addProperty("failedVisibleMaterialCount", failedVisibleMaterialCount);
        json.addProperty("visibleFallbackMaterialCount", visibleFallbackMaterialCount());
        json.addProperty("pendingVisibleUploadBytes", pendingVisibleUploadBytes);
        return json;
    }

    private static void publish(long generation, long pendingBytes) {
        CURRENT.set(new TextureResidencySnapshot(
            generation,
            true,
            VISIBLE.size(),
            countIntersection(VISIBLE, RESIDENT),
            countIntersection(VISIBLE, FAILED),
            Math.max(0L, pendingBytes)
        ));
    }

    private static int countIntersection(Set<Integer> a, Set<Integer> b) {
        int count = 0;
        for (Integer item : a) {
            if (b.contains(item)) count++;
        }
        return count;
    }
}
