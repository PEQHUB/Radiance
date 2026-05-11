package com.radiance.client.proxy.buffer;

/**
 * Single source of truth for converting MC's BuiltBuffer to a RadianceBufferHandle.
 * The real implementation lands in Implementation Checkpoint C against 1.20.1's
 * BufferBuilder.BuiltBuffer.getParameters() shape. Stubbed here so callers can compile
 * before that work begins.
 */
public final class RadianceBufferAdapter {

    private RadianceBufferAdapter() {
    }

    // Real signature lands in Checkpoint C:
    // public static RadianceBufferHandle from(net.minecraft.client.render.BufferBuilder.BuiltBuffer buf)
    // For now, only the empty class exists so other code can reference the package.
}
