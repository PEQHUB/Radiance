package com.radiance.client;

public final class RadianceState {

    public enum State {
        UNINITIALIZED, INIT_FAILED, BOOT_OK, RENDERER_ACTIVE, RENDERER_DISABLED
    }

    public static final State UNINITIALIZED = State.UNINITIALIZED;
    public static final State INIT_FAILED = State.INIT_FAILED;
    public static final State BOOT_OK = State.BOOT_OK;
    public static final State RENDERER_ACTIVE = State.RENDERER_ACTIVE;
    public static final State RENDERER_DISABLED = State.RENDERER_DISABLED;

    private static volatile State current = State.UNINITIALIZED;

    private RadianceState() {
    }

    public static synchronized void set(State next) {
        current = next;
    }

    public static State get() {
        return current;
    }

    public static boolean isResourceTrackingEnabled() {
        State s = current;
        return s == State.BOOT_OK || s == State.RENDERER_ACTIVE;
    }

    public static boolean isRendererActive() {
        return current == State.RENDERER_ACTIVE;
    }

    public static void runIfActive(Runnable r) {
        if (isRendererActive()) {
            r.run();
        }
    }
}
