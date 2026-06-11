package com.radiance.client.texture.v4;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.radiance.client.proxy.vulkan.TextureArrayBridgeV4;

import net.minecraft.client.MinecraftClient;

/**
 * Bounded first-frame texture readiness with a release latch.
 *
 * Strict readiness requires ALL of:
 *   - world present
 *   - generation > 0
 *   - visible material set known
 *   - zero visible fallback materials (failed materials do NOT block:
 *     failure is a terminal state that renders the permanent fallback,
 *     so waiting on it can never succeed)
 *   - zero pending visible upload bytes
 *   - zero pending native mip pages
 *   - zero unready allocated native pages
 *   - zero pending visible material table updates
 *
 * The gate is bounded: once a world is present for the active generation,
 * strict readiness has {@code radser.textureLoader.firstFrameDeadlineMs}
 * (default 3000 ms) to converge. At the deadline the gate releases with
 * fallback materials visible instead of withholding the world pass.
 * Either form of release latches for the generation — after the first
 * release the world pass is never withheld again, even if new chunks add
 * fallback materials later. A texture-generation mismatch must never make
 * valid geometry invisible; stale/fallback materials are preferred over
 * dropped frames.
 */
public final class FirstFrameTextureReadiness {

    private static final long DEADLINE_DEFAULT_MS = 3000L;
    private static final long DEADLINE_MIN_MS = 250L;
    private static final long DEADLINE_MAX_MS = 30000L;
    private static final org.slf4j.Logger LOGGER =
        org.slf4j.LoggerFactory.getLogger("RadSER Texture Readiness");

    private static final Object GATE_LOCK = new Object();
    private static long gateGeneration = -1L;
    private static long firstWorldEvalNanos = 0L;
    private static boolean released = false;
    private static boolean releasedByDeadline = false;

    private FirstFrameTextureReadiness() {}

    private static long deadlineMs() {
        long value = DEADLINE_DEFAULT_MS;
        String property = System.getProperty("radser.textureLoader.firstFrameDeadlineMs", "");
        if (!property.isBlank()) {
            try {
                value = Long.parseLong(property.trim());
            } catch (NumberFormatException ignored) {
                value = DEADLINE_DEFAULT_MS;
            }
        }
        return Math.max(DEADLINE_MIN_MS, Math.min(DEADLINE_MAX_MS, value));
    }

    /**
     * Apply the bounded-release latch to a strict readiness result.
     * Returns the effective readiness and records release transitions.
     */
    private static GateDecision applyReleaseLatch(boolean worldPresent, long generation,
                                                  boolean strictReady, JsonObject strictStatus) {
        synchronized (GATE_LOCK) {
            if (generation != gateGeneration) {
                gateGeneration = generation;
                firstWorldEvalNanos = 0L;
                released = false;
                releasedByDeadline = false;
            }
            if (!worldPresent || generation <= 0L) {
                // No world: the deadline clock does not run, but a previous
                // release for this generation stays latched.
                return new GateDecision(released, releasedByDeadline, 0L);
            }
            if (strictReady && !released) {
                released = true;
                releasedByDeadline = false;
            }
            long elapsedMs = 0L;
            if (!released) {
                if (firstWorldEvalNanos == 0L) {
                    firstWorldEvalNanos = System.nanoTime();
                }
                elapsedMs = (System.nanoTime() - firstWorldEvalNanos) / 1_000_000L;
                if (elapsedMs >= deadlineMs()) {
                    released = true;
                    releasedByDeadline = true;
                    LOGGER.warn("[TextureReadiness] First-frame gate released at deadline ({} ms) for generation {} "
                            + "with fallback materials visible; blocking status: {}",
                        deadlineMs(), generation, strictStatus);
                }
            } else if (firstWorldEvalNanos != 0L) {
                elapsedMs = (System.nanoTime() - firstWorldEvalNanos) / 1_000_000L;
            }
            return new GateDecision(released, releasedByDeadline, elapsedMs);
        }
    }

    private record GateDecision(boolean released, boolean byDeadline, long elapsedMs) {}

    /** Quick boolean check. */
    public static boolean ready(boolean worldPresent) {
        return statusJson(worldPresent).get("ready").getAsBoolean();
    }

    /** Full status JSON using current MinecraftClient world state. */
    public static JsonObject statusJson() {
        MinecraftClient client = MinecraftClient.getInstance();
        return statusJson(client != null && client.world != null);
    }

    /** Full status JSON with explicit world-present flag. */
    public static JsonObject statusJson(boolean worldPresent) {
        long generation = TextureLoadGeneration.active();
        if (worldPresent && generation > 0L) {
            FirstFrameMaterialPlanner.plan(generation);
        }
        TextureResidencySnapshot residency = TextureResidencySnapshot.current(generation);

        JsonObject nativeJson = nativeReadiness(generation);

        boolean visibleKnown = residency.visibleMaterialSetKnown();
        boolean emptyPlanAllowed = FirstFrameMaterialPlanner.isEmptyPlanAllowed();
        String emptyPlanReason = FirstFrameMaterialPlanner.emptyPlanReason();
        int visibleMaterialCount = residency.visibleMaterialCount();
        int visibleFallbacks = residency.visibleFallbackMaterialCount();
        int failedVisible = residency.failedVisibleMaterialCount();
        long pendingVisibleBytes = longProp(nativeJson, "pendingVisibleUploadBytes",
            residency.pendingVisibleUploadBytes());
        int pendingMipPages = intProp(nativeJson, "nativePendingMipPageCount", 0);
        int unreadyPages = intProp(nativeJson, "nativeUnreadyAllocatedPageCount", 0);
        int pendingTableUpdates = intProp(nativeJson, "pendingVisibleMaterialTableUpdates", 0);

        boolean nativeIdle = boolProp(nativeJson, "generationIdle", false);

        boolean strictReady = worldPresent
            && generation > 0L
            && visibleKnown
            && (visibleMaterialCount > 0 || emptyPlanAllowed)
            && visibleFallbacks == 0
            && pendingVisibleBytes == 0L
            && nativeIdle
            && pendingMipPages == 0
            && unreadyPages == 0
            && pendingTableUpdates == 0;

        JsonObject json = new JsonObject();
        json.addProperty("schema", "radser_first_frame_texture_readiness_v4");
        json.addProperty("worldPresent", worldPresent);
        json.addProperty("generation", generation);
        json.addProperty("strictReady", strictReady);
        json.addProperty("readinessBackend", "v4");
        json.addProperty("legacyFallbackUsed", false);
        String strictReason = strictReady ? "texture_material_ready"
            : reason(worldPresent, generation, visibleKnown, visibleFallbacks,
                     failedVisible, pendingVisibleBytes, nativeIdle, pendingMipPages,
                     unreadyPages, pendingTableUpdates);
        json.addProperty("ready", strictReady);
        json.addProperty("reason", strictReason);

        GateDecision decision = applyReleaseLatch(worldPresent, generation, strictReady, json);
        boolean ready = strictReady || decision.released();
        json.addProperty("ready", ready);
        json.addProperty("reason", ready && !strictReady
            ? "deadline_release_fallback_visible"
            : strictReason);
        json.addProperty("timedOut", decision.byDeadline());
        json.addProperty("releaseLatched", decision.released());
        json.addProperty("gateDeadlineMs", deadlineMs());
        json.addProperty("gateElapsedMs", decision.elapsedMs());
        json.addProperty("visibleMaterialSetKnown", visibleKnown);
        json.addProperty("visibleMaterialCount", visibleMaterialCount);
        json.addProperty("emptyPlanAllowed", emptyPlanAllowed);
        json.addProperty("emptyPlanReason", emptyPlanReason);
        json.addProperty("visibleFallbackMaterialCount", visibleFallbacks);
        json.addProperty("failedVisibleMaterialCount", failedVisible);
        json.addProperty("pendingVisibleUploadBytes", pendingVisibleBytes);
        json.addProperty("nativePendingMipPageCount", pendingMipPages);
        json.addProperty("nativeUnreadyAllocatedPageCount", unreadyPages);
        json.addProperty("pendingVisibleMaterialTableUpdates", pendingTableUpdates);
        json.add("native", nativeJson);
        json.add("residency", residency.toJson());

        return json;
    }

    private static String reason(boolean worldPresent, long generation, boolean visibleKnown,
                                  int visibleFallbacks, int failedVisible, long pendingVisibleBytes,
                                  boolean nativeIdle, int pendingMipPages, int unreadyPages,
                                  int pendingTableUpdates) {
        if (!worldPresent) return "waiting_for_world";
        if (generation <= 0L) return "waiting_for_texture_generation";
        if (!visibleKnown) return "waiting_for_visible_material_plan";
        TextureResidencySnapshot residency = TextureResidencySnapshot.current(generation);
        if (residency.visibleMaterialCount() == 0 && !FirstFrameMaterialPlanner.isEmptyPlanAllowed()) {
            return FirstFrameMaterialPlanner.emptyPlanReason();
        }
        // failedVisible is intentionally not a blocking reason: failed materials
        // are terminal and render the permanent fallback.
        if (visibleFallbacks > 0) return "waiting_for_visible_material_residency";
        if (pendingVisibleBytes > 0L) return "waiting_for_visible_gpu_uploads";
        if (!nativeIdle) return "waiting_for_native_generation_idle";
        if (pendingMipPages > 0) return "waiting_for_visible_mips";
        if (unreadyPages > 0) return "waiting_for_native_page_ready";
        if (pendingTableUpdates > 0) return "waiting_for_material_table";
        return "texture_material_ready";
    }

    private static JsonObject nativeReadiness(long generation) {
        try {
            String raw = TextureArrayBridgeV4.nativeFirstFrameNativeReadinessJsonV4(generation);
            if (raw == null || raw.isBlank()) return new JsonObject();
            return JsonParser.parseString(raw).getAsJsonObject();
        } catch (Throwable t) {
            JsonObject json = new JsonObject();
            json.addProperty("available", false);
            json.addProperty("error", t.getClass().getSimpleName());
            return json;
        }
    }

    private static int intProp(JsonObject obj, String key, int fallback) {
        try {
            return obj != null && obj.has(key) ? obj.get(key).getAsInt() : fallback;
        } catch (Throwable ignored) { return fallback; }
    }

    private static long longProp(JsonObject obj, String key, long fallback) {
        try {
            return obj != null && obj.has(key) ? obj.get(key).getAsLong() : fallback;
        } catch (Throwable ignored) { return fallback; }
    }

    private static boolean boolProp(JsonObject obj, String key, boolean fallback) {
        try {
            return obj != null && obj.has(key) ? obj.get(key).getAsBoolean() : fallback;
        } catch (Throwable ignored) { return fallback; }
    }
}
