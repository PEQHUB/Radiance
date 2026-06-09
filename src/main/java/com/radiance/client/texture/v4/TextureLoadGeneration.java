package com.radiance.client.texture.v4;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Monotonic generation counter for texture load operations.
 *
 * Each resource reload begins a new generation. Stale uploads from
 * cancelled or superseded generations must be rejected at the JNI boundary.
 * Timeout can never produce ready=true; only strict material readiness does.
 */
public final class TextureLoadGeneration {

    private static final AtomicLong ACTIVE = new AtomicLong(0L);
    private static final AtomicLong CANCELLED_THROUGH = new AtomicLong(0L);

    private TextureLoadGeneration() {}

    /** Begin a new texture load generation. Returns the new generation number. */
    public static long begin() {
        long next = ACTIVE.incrementAndGet();
        return next;
    }

    /** Current active generation. */
    public static long active() {
        return ACTIVE.get();
    }

    /** True if the given generation is the current active one and has not been cancelled. */
    public static boolean isActive(long generation) {
        return generation > 0L
            && generation == ACTIVE.get()
            && generation > CANCELLED_THROUGH.get();
    }

    /** Cancel the current active generation. Notifies native side. */
    public static void cancelActive() {
        long active = ACTIVE.get();
        CANCELLED_THROUGH.accumulateAndGet(active, Math::max);
        try {
            com.radiance.client.proxy.vulkan.TextureArrayBridgeV4.nativeCancelTextureLoaderV4(active, 1);
        } catch (Throwable ignored) {
            // Native may not be loaded yet
        }
    }

    /**
     * Throw if the requested generation is no longer active.
     * Used to prevent stale uploads from proceeding after disconnect/title/resource reload.
     */
    public static void requireActive(long generation, String label) {
        if (!isActive(generation)) {
            throw new StaleTextureGenerationException(generation, active(), label);
        }
    }

    /** Reset state for testing. */
    public static void resetForTest() {
        ACTIVE.set(0L);
        CANCELLED_THROUGH.set(0L);
    }

    public static final class StaleTextureGenerationException extends RuntimeException {
        public final long requested;
        public final long currentActive;

        public StaleTextureGenerationException(long requested, long active, String label) {
            super("stale texture generation requested=" + requested + " active=" + active + " at " + label);
            this.requested = requested;
            this.currentActive = active;
        }
    }
}
