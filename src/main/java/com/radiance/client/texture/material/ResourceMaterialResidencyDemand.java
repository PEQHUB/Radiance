package com.radiance.client.texture.material;

import com.google.gson.JsonObject;
import com.radiance.client.texture.compat.ResourcePackRuntimeMaterialBootstrap;
import com.radiance.client.texture.v4.TextureResidencySnapshot;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Visible-material demand tracker for progressive material residency.
 *
 * <p>Chunk building already resolves OptiFine CTM/random/repeat choices into
 * global material ids. This tracker records those ids so the residency uploader
 * can promote visible materials ahead of background full-pack upload order.</p>
 */
public final class ResourceMaterialResidencyDemand {
    private static final Object GENERATION_LOCK = new Object();
    private static volatile long activeGeneration = 0L;
    private static Set<Integer> plannedFirstFrameMaterials = ConcurrentHashMap.newKeySet();
    private static Set<Integer> prewarmMaterials = ConcurrentHashMap.newKeySet();
    private static Set<Integer> visibleMaterials = ConcurrentHashMap.newKeySet();
    private static Set<Integer> residentVisibleMaterials = ConcurrentHashMap.newKeySet();
    private static Set<Integer> requestedMaterials = ConcurrentHashMap.newKeySet();
    private static Set<Integer> retryableFailedMaterials = ConcurrentHashMap.newKeySet();
    private static Set<Integer> permanentFailedMaterials = ConcurrentHashMap.newKeySet();
    private static final AtomicLong visibleRequestEvents = new AtomicLong();
    private static final AtomicLong visibleUniqueRequests = new AtomicLong();
    private static final AtomicLong plannedFirstFrameRequestEvents = new AtomicLong();
    private static final AtomicLong plannedFirstFrameUniqueRequests = new AtomicLong();
    private static final AtomicLong prewarmRequestEvents = new AtomicLong();
    private static final AtomicLong prewarmUniqueRequests = new AtomicLong();
    private static final AtomicLong visiblePriorityCandidates = new AtomicLong();
    private static final AtomicLong visibleResidentEvents = new AtomicLong();

    private ResourceMaterialResidencyDemand() {
    }

    public static void resetForGeneration(long generation) {
        synchronized (GENERATION_LOCK) {
            if (generation < activeGeneration) {
                return;
            }
            activeGeneration = generation;
            plannedFirstFrameMaterials = ConcurrentHashMap.newKeySet();
            prewarmMaterials = ConcurrentHashMap.newKeySet();
            visibleMaterials = ConcurrentHashMap.newKeySet();
            residentVisibleMaterials = ConcurrentHashMap.newKeySet();
            requestedMaterials = ConcurrentHashMap.newKeySet();
            retryableFailedMaterials = ConcurrentHashMap.newKeySet();
            permanentFailedMaterials = ConcurrentHashMap.newKeySet();
            visibleRequestEvents.set(0L);
            visibleUniqueRequests.set(0L);
            plannedFirstFrameRequestEvents.set(0L);
            plannedFirstFrameUniqueRequests.set(0L);
            prewarmRequestEvents.set(0L);
            prewarmUniqueRequests.set(0L);
            visiblePriorityCandidates.set(0L);
            visibleResidentEvents.set(0L);
            TextureResidencySnapshot.resetForGeneration(generation);
        }
    }

    public static void enqueuePlannedFirstFrame(long generation, int materialId) {
        if (generation <= 0L || materialId < 0) {
            return;
        }
        ensureGeneration(generation);
        if (generation != activeGeneration) {
            return;
        }
        plannedFirstFrameRequestEvents.incrementAndGet();
        if (plannedFirstFrameMaterials.add(materialId)) {
            plannedFirstFrameUniqueRequests.incrementAndGet();
            prewarmMaterials.add(materialId);
            requestedMaterials.add(materialId);
            ResourcePackRuntimeMaterialBootstrap.onPlannedFirstFrameMaterialDemand(generation);
        }
    }

    public static void enqueuePrewarm(long generation, int materialId) {
        if (generation <= 0L || materialId < 0) {
            return;
        }
        ensureGeneration(generation);
        if (generation != activeGeneration) {
            return;
        }
        prewarmRequestEvents.incrementAndGet();
        if (prewarmMaterials.add(materialId)) {
            prewarmUniqueRequests.incrementAndGet();
            requestedMaterials.add(materialId);
            ResourcePackRuntimeMaterialBootstrap.onPrewarmMaterialDemand(generation);
        }
    }

    public static void enqueueVisible(long generation, int materialId) {
        if (generation <= 0L || materialId < 0) {
            return;
        }
        ensureGeneration(generation);
        if (generation != activeGeneration) {
            return;
        }
        visibleRequestEvents.incrementAndGet();
        if (visibleMaterials.add(materialId)) {
            visibleUniqueRequests.incrementAndGet();
            requestedMaterials.add(materialId);
            ResourcePackRuntimeMaterialBootstrap.onVisibleMaterialDemand(generation);
        }
    }

    public static Set<Integer> visibleMaterialIds(long generation) {
        if (generation != activeGeneration || visibleMaterials.isEmpty()) {
            return Set.of();
        }
        return Set.copyOf(visibleMaterials);
    }

    public static Set<Integer> residencyMaterialIds(long generation) {
        if (generation != activeGeneration || requestedMaterials.isEmpty()) {
            return Set.of();
        }
        return Set.copyOf(requestedMaterials);
    }

    public static void recordPriorityCandidates(long generation, int count) {
        if (generation == activeGeneration && count > 0) {
            visiblePriorityCandidates.addAndGet(count);
        }
    }

    public static void recordResident(long generation, Collection<Integer> materialIds) {
        if (generation != activeGeneration || materialIds == null || materialIds.isEmpty()) {
            return;
        }
        int added = 0;
        Set<Integer> snapshotIds = new HashSet<>();
        for (Integer materialId : materialIds) {
            if (materialId != null) {
                snapshotIds.add(materialId);
                requestedMaterials.remove(materialId);
                plannedFirstFrameMaterials.remove(materialId);
                prewarmMaterials.remove(materialId);
                retryableFailedMaterials.remove(materialId);
                permanentFailedMaterials.remove(materialId);
                if (visibleMaterials.contains(materialId)
                    && residentVisibleMaterials.add(materialId)) {
                    added++;
                }
            }
        }
        if (added > 0) {
            visibleResidentEvents.addAndGet(added);
        }
        if (!snapshotIds.isEmpty()) {
            TextureResidencySnapshot.markResident(generation, snapshotIds, 0L);
        }
    }

    public static void recordFailed(long generation, Collection<Integer> materialIds) {
        recordPermanentFailed(generation, materialIds);
    }

    public static void recordPermanentFailed(long generation, Collection<Integer> materialIds) {
        if (generation != activeGeneration || materialIds == null || materialIds.isEmpty()) {
            return;
        }
        Set<Integer> snapshotIds = new HashSet<>();
        for (Integer materialId : materialIds) {
            if (materialId != null && materialId >= 0) {
                snapshotIds.add(materialId);
                requestedMaterials.remove(materialId);
                plannedFirstFrameMaterials.remove(materialId);
                prewarmMaterials.remove(materialId);
                retryableFailedMaterials.remove(materialId);
                permanentFailedMaterials.add(materialId);
            }
        }
        if (!snapshotIds.isEmpty()) {
            TextureResidencySnapshot.markFailed(generation, snapshotIds, 0L);
        }
    }

    public static void recordRetryableFailed(long generation, Collection<Integer> materialIds) {
        if (generation != activeGeneration || materialIds == null || materialIds.isEmpty()) {
            return;
        }
        Set<Integer> snapshotIds = new HashSet<>();
        for (Integer materialId : materialIds) {
            if (materialId != null && materialId >= 0 && !permanentFailedMaterials.contains(materialId)) {
                snapshotIds.add(materialId);
                requestedMaterials.add(materialId);
                retryableFailedMaterials.add(materialId);
            }
        }
        if (!snapshotIds.isEmpty()) {
            TextureResidencySnapshot.markFailed(generation, snapshotIds, 0L);
        }
    }

    public static boolean isVisibleResident(long generation, int materialId) {
        return generation == activeGeneration && residentVisibleMaterials.contains(materialId);
    }

    public static int residentVisibleMaterialCount(long generation) {
        return generation == activeGeneration ? residentVisibleMaterials.size() : 0;
    }

    public static JsonObject summaryJson(long generation) {
        JsonObject json = new JsonObject();
        json.addProperty("generation", activeGeneration);
        json.addProperty("requestedGeneration", generation);
        json.addProperty("generationMatches", generation == activeGeneration);
        json.addProperty("visibleRequestEvents", visibleRequestEvents.get());
        json.addProperty("visibleUniqueMaterialCount", visibleMaterials.size());
        json.addProperty("plannedFirstFrameRequestEvents", plannedFirstFrameRequestEvents.get());
        json.addProperty("plannedFirstFrameUniqueMaterialCount", plannedFirstFrameMaterials.size());
        json.addProperty("prewarmRequestEvents", prewarmRequestEvents.get());
        json.addProperty("prewarmUniqueMaterialCount", prewarmMaterials.size());
        json.addProperty("requestedUniqueMaterialCount", requestedMaterials.size());
        json.addProperty("failedUniqueMaterialCount",
            retryableFailedMaterials.size() + permanentFailedMaterials.size());
        json.addProperty("retryableFailedUniqueMaterialCount", retryableFailedMaterials.size());
        json.addProperty("permanentFailedUniqueMaterialCount", permanentFailedMaterials.size());
        json.addProperty("retryableFailedVisibleMaterialCount",
            intersectionSize(retryableFailedMaterials, visibleMaterials));
        json.addProperty("permanentFailedVisibleMaterialCount",
            intersectionSize(permanentFailedMaterials, visibleMaterials));
        json.addProperty("visiblePriorityCandidateEvents", visiblePriorityCandidates.get());
        json.addProperty("visibleResidentMaterialCount", residentVisibleMaterials.size());
        json.addProperty("visibleFallbackMaterialCount",
            Math.max(0, visibleMaterials.size() - residentVisibleMaterials.size()));
        json.add("scheduler", ResourcePackRuntimeMaterialBootstrap.schedulerStatusJson(generation));
        return json;
    }

    private static int intersectionSize(Set<Integer> left, Set<Integer> right) {
        if (left.isEmpty() || right.isEmpty()) {
            return 0;
        }
        int count = 0;
        for (Integer value : left) {
            if (right.contains(value)) {
                count++;
            }
        }
        return count;
    }

    private static void ensureGeneration(long generation) {
        if (generation == activeGeneration) {
            return;
        }
        synchronized (GENERATION_LOCK) {
            if (generation > activeGeneration) {
                resetForGeneration(generation);
            }
        }
    }
}
