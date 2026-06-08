package com.radiance.client.texture.material;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.radiance.client.proxy.vulkan.TextureArrayBridge;
import com.radiance.client.texture.compat.ResourcePackRuntimeMaterialBootstrap;
import net.minecraft.client.MinecraftClient;

/**
 * Bounded first-frame material readiness gate for visible CTM residency.
 */
public final class FirstFrameTextureReadiness {
    private static final long DEFAULT_TIMEOUT_MS = 5_000L;
    private static final long DEFAULT_COLLECT_VISIBLE_DEMAND_MS = 250L;
    private static final long MIN_TIMEOUT_MS = 250L;
    private static final long MAX_TIMEOUT_MS = 30_000L;
    private static final long MAX_COLLECT_VISIBLE_DEMAND_MS = 2_000L;
    private static final Object LOCK = new Object();

    private static long gateGeneration = Long.MIN_VALUE;
    private static long gateStartedNanos = 0L;
    private static boolean previousWorldPresent = false;

    private FirstFrameTextureReadiness() {
    }

    public static boolean readyOrTimedOut(boolean worldPresent) {
        return statusJson(worldPresent).get("ready").getAsBoolean();
    }

    public static JsonObject statusJson() {
        MinecraftClient client = MinecraftClient.getInstance();
        return statusJson(client != null && client.world != null);
    }

    public static JsonObject statusJson(boolean worldPresent) {
        JsonObject registry = ResourceMaterialRegistry.activeSummaryJson();
        JsonObject visibleResidency = asObject(registry.get("visibleResidency"));
        JsonObject scheduler = visibleResidency == null ? null : asObject(visibleResidency.get("scheduler"));
        long generation = longValue(registry, "generation", 0L);
        int recordedMaterialCount = intValue(registry, "recordedMaterialCount", 0);
        int compatVirtualMaterialCount = intValue(registry, "compatVirtualMaterialCount", 0);
        int visibleUniqueMaterialCount = intValue(visibleResidency, "visibleUniqueMaterialCount", 0);
        int visibleFallbackMaterialCount = intValue(visibleResidency, "visibleFallbackMaterialCount", 0);
        int requestedUniqueMaterialCount = intValue(visibleResidency, "requestedUniqueMaterialCount", 0);
        int failedUniqueMaterialCount = intValue(visibleResidency, "failedUniqueMaterialCount", 0);
        int pendingQueueSize = intValue(scheduler, "pendingQueueSize", requestedUniqueMaterialCount);
        boolean uploadInFlight = boolValue(scheduler, "uploadInFlight");
        boolean uploadScheduled = boolValue(scheduler, "uploadScheduled");
        JsonObject nativePagePool = nativeMaterialPagePoolStatus();
        boolean nativePagePoolBusy = boolValue(nativePagePool, "busy");
        int pendingMipPageCount = intValue(nativePagePool, "pendingMipPageCount", 0);
        int unreadyAllocatedPageCount = intValue(nativePagePool, "unreadyAllocatedPageCount", 0);
        int readyAllocatedPageCount = intValue(nativePagePool, "readyAllocatedPageCount", 0);
        int ctmUnaddressableMaterials = intValue(nativePagePool, "ctmUnaddressableMaterials", 0);
        boolean nativePagesExhausted = boolValue(nativePagePool, "pagesExhausted");
        long timeoutMs = configuredLong("radser.firstFrameTextureGateTimeoutMs",
            DEFAULT_TIMEOUT_MS, MIN_TIMEOUT_MS, MAX_TIMEOUT_MS);
        long collectVisibleDemandMs = configuredLong("radser.firstFrameTextureGateCollectMs",
            DEFAULT_COLLECT_VISIBLE_DEMAND_MS, 0L, MAX_COLLECT_VISIBLE_DEMAND_MS);

        long now = System.nanoTime();
        long started;
        synchronized (LOCK) {
            if (!worldPresent) {
                previousWorldPresent = false;
                gateGeneration = Long.MIN_VALUE;
                gateStartedNanos = 0L;
            } else if (!previousWorldPresent || generation != gateGeneration || gateStartedNanos <= 0L) {
                previousWorldPresent = true;
                gateGeneration = generation;
                gateStartedNanos = now;
            }
            started = gateStartedNanos;
        }

        long elapsedMs = worldPresent && started > 0L
            ? Math.max(0L, (now - started) / 1_000_000L)
            : 0L;
        boolean collectingMaterialRegistry = worldPresent
            && (generation <= 0L || recordedMaterialCount <= 0)
            && elapsedMs < collectVisibleDemandMs;
        boolean hasCompatResidency = compatVirtualMaterialCount > 0 && generation > 0L;
        boolean collectingVisibleDemand = worldPresent
            && hasCompatResidency
            && visibleUniqueMaterialCount == 0
            && elapsedMs < collectVisibleDemandMs;
        boolean residencyWorkPending = pendingQueueSize > 0 || uploadInFlight || uploadScheduled;
        boolean nativePageProgressPending = visibleFallbackMaterialCount > 0
            && (nativePagePoolBusy || pendingMipPageCount > 0 || unreadyAllocatedPageCount > 0);
        boolean materialProgressPending = visibleFallbackMaterialCount > 0
            && (residencyWorkPending || nativePageProgressPending);
        boolean firstFrameDrainRequested = worldPresent
            && hasCompatResidency
            && visibleFallbackMaterialCount > 0
            && requestedUniqueMaterialCount > 0;
        if (firstFrameDrainRequested) {
            ResourcePackRuntimeMaterialBootstrap.requestFirstFrameVisibleResidency(generation);
        }
        boolean materialReady = !collectingMaterialRegistry
            && (!hasCompatResidency
            || (!collectingVisibleDemand && !materialProgressPending));
        boolean timedOut = worldPresent && !materialReady && elapsedMs >= timeoutMs;
        boolean ready = !worldPresent || materialReady || timedOut;

        JsonObject json = new JsonObject();
        json.addProperty("schema", "radser_first_frame_texture_readiness_v1");
        json.addProperty("worldPresent", worldPresent);
        json.addProperty("generation", generation);
        json.addProperty("gateGeneration", gateGeneration == Long.MIN_VALUE ? 0L : gateGeneration);
        json.addProperty("elapsedMs", elapsedMs);
        json.addProperty("timeoutMs", timeoutMs);
        json.addProperty("collectVisibleDemandMs", collectVisibleDemandMs);
        json.addProperty("recordedMaterialCount", recordedMaterialCount);
        json.addProperty("compatVirtualMaterialCount", compatVirtualMaterialCount);
        json.addProperty("visibleUniqueMaterialCount", visibleUniqueMaterialCount);
        json.addProperty("visibleFallbackMaterialCount", visibleFallbackMaterialCount);
        json.addProperty("requestedUniqueMaterialCount", requestedUniqueMaterialCount);
        json.addProperty("failedUniqueMaterialCount", failedUniqueMaterialCount);
        json.addProperty("pendingQueueSize", pendingQueueSize);
        json.addProperty("uploadInFlight", uploadInFlight);
        json.addProperty("uploadScheduled", uploadScheduled);
        json.addProperty("nativePagePoolBusy", nativePagePoolBusy);
        json.addProperty("nativePendingMipPageCount", pendingMipPageCount);
        json.addProperty("nativeUnreadyAllocatedPageCount", unreadyAllocatedPageCount);
        json.addProperty("nativeReadyAllocatedPageCount", readyAllocatedPageCount);
        json.addProperty("nativePagesExhausted", nativePagesExhausted);
        json.addProperty("nativeCtmUnaddressableMaterials", ctmUnaddressableMaterials);
        json.addProperty("collectingMaterialRegistry", collectingMaterialRegistry);
        json.addProperty("collectingVisibleDemand", collectingVisibleDemand);
        json.addProperty("residencyWorkPending", residencyWorkPending);
        json.addProperty("nativePageProgressPending", nativePageProgressPending);
        json.addProperty("materialProgressPending", materialProgressPending);
        json.addProperty("firstFrameDrainRequested", firstFrameDrainRequested);
        json.addProperty("materialReady", materialReady);
        json.addProperty("timedOut", timedOut);
        json.addProperty("ready", ready);
        json.addProperty("reason", reason(worldPresent, materialReady, timedOut,
            collectingMaterialRegistry, collectingVisibleDemand, materialProgressPending,
            nativePageProgressPending, nativePagesExhausted, ctmUnaddressableMaterials,
            failedUniqueMaterialCount));
        json.add("materialRegistry", registry);
        json.add("nativeMaterialPagePool", nativePagePool);
        return json;
    }

    private static String reason(boolean worldPresent, boolean materialReady, boolean timedOut,
                                 boolean collectingMaterialRegistry,
                                 boolean collectingVisibleDemand, boolean materialProgressPending,
                                 boolean nativePageProgressPending, boolean nativePagesExhausted,
                                 int ctmUnaddressableMaterials,
                                 int failedUniqueMaterialCount) {
        if (!worldPresent) {
            return "waiting_for_world";
        }
        if (timedOut) {
            return "timeout_release";
        }
        if (collectingMaterialRegistry) {
            return "collecting_material_registry";
        }
        if (collectingVisibleDemand) {
            return "collecting_visible_material_demand";
        }
        if (ctmUnaddressableMaterials > 0 || nativePagesExhausted) {
            return "material_page_capacity_exhausted";
        }
        if (nativePageProgressPending) {
            return "waiting_for_native_material_page_mips";
        }
        if (materialProgressPending) {
            return "waiting_for_visible_material_residency";
        }
        if (materialReady && failedUniqueMaterialCount > 0) {
            return "ready_with_failed_material_fallbacks";
        }
        return "texture_material_ready";
    }

    private static JsonObject asObject(com.google.gson.JsonElement element) {
        return element != null && element.isJsonObject() ? element.getAsJsonObject() : null;
    }

    private static JsonObject nativeMaterialPagePoolStatus() {
        try {
            String raw = TextureArrayBridge.nativeMaterialPagePoolStatusJson();
            if (raw == null || raw.isBlank()) {
                return nativeUnavailable("empty");
            }
            JsonElement parsed = JsonParser.parseString(raw);
            return parsed.isJsonObject() ? parsed.getAsJsonObject() : nativeUnavailable("non_object");
        } catch (Throwable e) {
            return nativeUnavailable(e.getClass().getSimpleName());
        }
    }

    private static JsonObject nativeUnavailable(String reason) {
        JsonObject json = new JsonObject();
        json.addProperty("schema", "radser_material_page_pool_status_v1");
        json.addProperty("available", false);
        json.addProperty("reason", reason == null || reason.isBlank() ? "unknown" : reason);
        return json;
    }

    private static boolean boolValue(JsonObject object, String key) {
        return object != null && object.has(key) && object.get(key).isJsonPrimitive()
            && object.get(key).getAsBoolean();
    }

    private static int intValue(JsonObject object, String key, int fallback) {
        if (object == null || !object.has(key) || !object.get(key).isJsonPrimitive()) {
            return fallback;
        }
        try {
            return object.get(key).getAsInt();
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static long longValue(JsonObject object, String key, long fallback) {
        if (object == null || !object.has(key) || !object.get(key).isJsonPrimitive()) {
            return fallback;
        }
        try {
            return object.get(key).getAsLong();
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static long configuredLong(String name, long fallback, long min, long max) {
        String value = System.getProperty(name, "");
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            long parsed = Long.parseLong(value.trim());
            return Math.max(min, Math.min(max, parsed));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }
}
