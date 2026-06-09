package com.radiance.client.texture.v4;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.radiance.client.proxy.vulkan.TextureArrayBridgeV4;

import net.minecraft.client.MinecraftClient;

/**
 * Strict first-frame texture readiness — no timeout success path.
 *
 * Replaces the old FirstFrameTextureReadiness which could return
 * ready=true via timeout_release. In v4, timeout can only produce
 * ready=false with reason="timeout_failure", never ready=true.
 *
 * Readiness requires ALL of:
 *   - world present
 *   - generation > 0
 *   - visible material set known
 *   - zero visible fallback materials
 *   - zero failed visible materials
 *   - zero pending visible upload bytes
 *   - zero pending native mip pages
 *   - zero unready allocated native pages
 *   - zero pending visible material table updates
 */
public final class FirstFrameTextureReadiness {

    private FirstFrameTextureReadiness() {}

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
        TextureResidencySnapshot residency = TextureResidencySnapshot.current(generation);

        JsonObject nativeJson = nativeReadiness(generation);

        boolean visibleKnown = residency.visibleMaterialSetKnown();
        int visibleFallbacks = residency.visibleFallbackMaterialCount();
        int failedVisible = residency.failedVisibleMaterialCount();
        long pendingVisibleBytes = longProp(nativeJson, "pendingVisibleUploadBytes",
            residency.pendingVisibleUploadBytes());
        int pendingMipPages = intProp(nativeJson, "nativePendingMipPageCount", 0);
        int unreadyPages = intProp(nativeJson, "nativeUnreadyAllocatedPageCount", 0);
        int pendingTableUpdates = intProp(nativeJson, "pendingVisibleMaterialTableUpdates", 0);

        boolean nativeIdle = boolProp(nativeJson, "generationIdle", false);

        boolean ready = worldPresent
            && generation > 0L
            && visibleKnown
            && visibleFallbacks == 0
            && failedVisible == 0
            && pendingVisibleBytes == 0L
            && nativeIdle
            && pendingMipPages == 0
            && unreadyPages == 0
            && pendingTableUpdates == 0;

        JsonObject json = new JsonObject();
        json.addProperty("schema", "radser_first_frame_texture_readiness_v4");
        json.addProperty("worldPresent", worldPresent);
        json.addProperty("generation", generation);
        json.addProperty("ready", ready);
        json.addProperty("timedOut", false);
        json.addProperty("readinessBackend", "v4");
        json.addProperty("legacyFallbackUsed", false);
        json.addProperty("reason", ready ? "texture_material_ready"
            : reason(worldPresent, generation, visibleKnown, visibleFallbacks,
                     failedVisible, pendingVisibleBytes, nativeIdle, pendingMipPages,
                     unreadyPages, pendingTableUpdates));
        json.addProperty("visibleMaterialSetKnown", visibleKnown);
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
        if (failedVisible > 0) return "failed_visible_materials";
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
