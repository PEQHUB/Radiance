package com.radiance.v2.bridge;

import com.radiance.client.RadianceClient;
import com.radiance.client.option.Options;

/**
 * Phase 1 reversible-boot decision point. Owns the "is V2 active for THIS
 * session" flag, computed once during {@code Window.<init>} (from
 * {@code WindowMixins}) by running the dry-run Vulkan probe before any GLFW
 * window-hint or framebuffer state has been mutated.
 *
 * <p>Lives outside the Mixin layer because Mixin classes may only declare
 * {@code private static} methods — exposing a {@code public static} accessor
 * would be flagged as {@code InvalidMixinException} by sponge-mixin.
 *
 * <p>State is one-shot: {@link #ensureChecked()} runs once per process and
 * caches the result. {@link #isActive()} is then a cheap read for every
 * mixin redirect that needs to know whether to suppress GL behavior.
 */
public final class V2BootGate {

    private static volatile boolean checked = false;
    private static volatile boolean active = false;

    private V2BootGate() {}

    /**
     * Has the gate been computed yet?
     */
    public static boolean isChecked() { return checked; }

    /**
     * V2 active for this session? Returns false when:
     *   - Options.useV2Engine is false at probe time, or
     *   - The dry-run Vulkan probe rejected this hardware/driver, or
     *   - {@link #ensureChecked()} hasn't been called yet.
     */
    public static boolean isActive() { return active; }

    /**
     * Run the probe (idempotent). Safe to call from any mixin redirect that
     * fires during Window.&lt;init&gt;. The first caller wins; subsequent calls
     * are O(1) reads.
     */
    public static void ensureChecked() {
        if (checked) return;
        synchronized (V2BootGate.class) {
            if (checked) return;
            try {
                if (!Options.useV2Engine) {
                    active = false;
                    return;
                }
                if (RadianceClient.radianceDir == null) {
                    System.err.println("[Radiance] V2 gate: radianceDir not initialized — falling back to legacy for this session");
                    active = false;
                    return;
                }
                String configDir = RadianceClient.radianceDir.toAbsolutePath().toString();
                BootTrace.event("V2_PROBE_BEGIN", "configDir=" + configDir);
                EngineBridge.ProbeResult r = EngineBridge.probe(configDir, Options.validationLayers);
                if (r.ok) {
                    BootTrace.event("V2_PROBE_OK", "gpu=" + r.gpuName);
                    active = true;
                } else {
                    BootTrace.event("V2_PROBE_FAIL", r.reason
                            + (r.missingExtensions.isEmpty() ? "" : " missing=[" + r.missingExtensions + "]"));
                    System.err.println("[Radiance] V2 probe failed: " + r.reason);
                    System.err.println("[Radiance] See radiance/logs/v2_cannot_start.txt for details");
                    System.err.println("[Radiance] Falling back to legacy renderer for this session (Options.useV2Engine preserved)");
                    active = false;
                }
            } finally {
                checked = true;
            }
        }
    }
}
