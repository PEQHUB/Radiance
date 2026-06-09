package com.radiance.client.texture.v4;

import java.nio.Buffer;
import java.nio.ByteBuffer;

/**
 * Guards against JVM crashes from invalid native upload buffers.
 *
 * Every code path that passes a ByteBuffer address and byte count to JNI
 * must call assertDirectCapacity before the native call. This prevents
 * EXCEPTION_ACCESS_VIOLATION from non-direct buffers, null buffers,
 * or capacity mismatches.
 *
 * Phase 1 requirement: no fatal JVM/native crash on bad Java/JNI input.
 */
public final class NativeUploadGuards {

    private NativeUploadGuards() {}

    /**
     * Assert that a ByteBuffer is direct and has at least the required capacity.
     * Throws IllegalArgumentException (not a native crash) on any violation.
     */
    public static void assertDirectCapacity(ByteBuffer buffer, long requiredBytes, String label) {
        if (buffer == null) {
            throw new IllegalArgumentException(label + ": null buffer");
        }
        if (!buffer.isDirect()) {
            throw new IllegalArgumentException(label + ": buffer is not direct");
        }
        if (requiredBytes < 0 || requiredBytes > Integer.MAX_VALUE) {
            throw new IllegalArgumentException(label + ": invalid required bytes " + requiredBytes);
        }
        if (buffer.capacity() < requiredBytes) {
            throw new IllegalArgumentException(
                label + ": capacity=" + buffer.capacity() + " required=" + requiredBytes);
        }
    }

    /**
     * Assert that a range [offset, offset+bytes) fits within a buffer's capacity.
     * Checks for overflow and negative values.
     */
    public static void assertRange(Buffer buffer, long offset, long bytes, String label) {
        if (buffer == null) {
            throw new IllegalArgumentException(label + ": null buffer");
        }
        if (offset < 0 || bytes < 0) {
            throw new IllegalArgumentException(label + ": negative range offset=" + offset + " bytes=" + bytes);
        }
        long end = offset + bytes;
        if (end < offset) {
            throw new IllegalArgumentException(label + ": overflow range offset=" + offset + " bytes=" + bytes);
        }
        if (end > buffer.capacity()) {
            throw new IllegalArgumentException(
                label + ": range end=" + end + " capacity=" + buffer.capacity());
        }
    }

    /**
     * Compute total bytes for a tiered layer upload, with overflow checking.
     * Returns the checked int value or throws on overflow.
     */
    public static int checkedLayerBytes(TextureTier tier, int layerCount, String label) {
        if (tier == null) {
            throw new IllegalArgumentException(label + ": null tier");
        }
        if (layerCount <= 0) {
            throw new IllegalArgumentException(label + ": layerCount <= 0");
        }
        long total = (long) tier.bytesPerLayerRgba8() * layerCount;
        if (total > Integer.MAX_VALUE) {
            throw new IllegalArgumentException(label + ": byte count overflow " + total);
        }
        return (int) total;
    }

    /**
     * Validate a raw pointer + byte count before passing to JNI.
     * Returns false (not crash) on any invalid combination.
     */
    public static boolean validNativePointer(long ptr, int bytes) {
        return ptr != 0L && bytes > 0;
    }

    /**
     * Validate generation is active before any JNI upload call.
     * Returns false if stale or zero.
     */
    public static boolean validGeneration(long generation) {
        return generation > 0L && TextureLoadGeneration.isActive(generation);
    }
}
