package com.radiance.client;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RadianceStateTest {

    @AfterEach
    void reset() {
        RadianceState.set(RadianceState.UNINITIALIZED);
    }

    @Test
    void defaultStateIsUninitialized() {
        assertEquals(RadianceState.UNINITIALIZED, RadianceState.get());
    }

    @Test
    void resourceTrackingDisabledWhenUninitialized() {
        assertFalse(RadianceState.isResourceTrackingEnabled());
        assertFalse(RadianceState.isRendererActive());
    }

    @Test
    void resourceTrackingEnabledAfterBootOk() {
        RadianceState.set(RadianceState.BOOT_OK);
        assertTrue(RadianceState.isResourceTrackingEnabled());
        assertFalse(RadianceState.isRendererActive());
    }

    @Test
    void rendererActiveAfterRendererActive() {
        RadianceState.set(RadianceState.RENDERER_ACTIVE);
        assertTrue(RadianceState.isResourceTrackingEnabled());
        assertTrue(RadianceState.isRendererActive());
    }

    @Test
    void rendererInactiveAfterDisabled() {
        RadianceState.set(RadianceState.RENDERER_DISABLED);
        assertFalse(RadianceState.isResourceTrackingEnabled());
        assertFalse(RadianceState.isRendererActive());
    }

    @Test
    void rendererInactiveAfterInitFailed() {
        RadianceState.set(RadianceState.INIT_FAILED);
        assertFalse(RadianceState.isResourceTrackingEnabled());
        assertFalse(RadianceState.isRendererActive());
    }

    @Test
    void runIfActiveSkipsWhenInactive() {
        RadianceState.set(RadianceState.BOOT_OK);
        boolean[] ran = {false};
        RadianceState.runIfActive(() -> ran[0] = true);
        assertFalse(ran[0]);
    }

    @Test
    void runIfActiveExecutesWhenActive() {
        RadianceState.set(RadianceState.RENDERER_ACTIVE);
        boolean[] ran = {false};
        RadianceState.runIfActive(() -> ran[0] = true);
        assertTrue(ran[0]);
    }
}
