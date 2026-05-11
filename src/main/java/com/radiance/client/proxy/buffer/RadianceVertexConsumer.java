package com.radiance.client.proxy.buffer;

/**
 * Radiance-owned vertex consumer surface. Replaces every JNI reference to MC's VertexConsumer
 * so the JNI contract does not depend on MC version. Methods are added in Implementation
 * Checkpoint C as PBRVertexConsumer is wired to this interface.
 */
public interface RadianceVertexConsumer {
    // Methods land in Checkpoint C with PBRVertexConsumer integration.
}
