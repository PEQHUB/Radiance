package com.radiance.client.texture.v4;

import java.nio.ByteBuffer;

/**
 * A single texture payload ready for GPU upload.
 *
 * Each payload carries its generation, material/sprite identity, tier,
 * and direct byte buffers for RGBA8 pixel data. All buffers must be
 * native-order direct ByteBuffers with capacity >= tier.bytesPerLayerRgba8().
 */
public record TexturePayload(
    long generation,
    int materialId,
    int spriteId,
    TextureTier tier,
    int frameIndex,
    ByteBuffer albedoRgba,
    ByteBuffer specularRgba,
    ByteBuffer normalRgba,
    ByteBuffer flagRgba,
    boolean hasAuthoredSpecular,
    boolean hasAuthoredNormal,
    int heightRangePacked
) {
    /** Bytes per layer for this payload's tier (RGBA8). */
    public int bytesPerLayer() {
        return tier.bytesPerLayerRgba8();
    }

    /** Validate all non-null buffers meet direct-capacity requirements. */
    public void validate() {
        NativeUploadGuards.assertDirectCapacity(albedoRgba, bytesPerLayer(), "albedo");
        if (specularRgba != null) {
            NativeUploadGuards.assertDirectCapacity(specularRgba, bytesPerLayer(), "specular");
        }
        if (normalRgba != null) {
            NativeUploadGuards.assertDirectCapacity(normalRgba, bytesPerLayer(), "normal");
        }
        if (flagRgba != null) {
            NativeUploadGuards.assertDirectCapacity(flagRgba, bytesPerLayer(), "flag");
        }
    }
}
