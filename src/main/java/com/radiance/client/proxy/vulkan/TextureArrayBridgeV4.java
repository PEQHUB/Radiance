package com.radiance.client.proxy.vulkan;

/**
 * V4 texture loader JNI bridge.
 *
 * Replaces the old fixed-layer TextureArrayBridge methods with
 * generation-scoped, tiered, async upload APIs.
 *
 * ABI version 4: no fixed-layer bulk sprite upload, no timeout readiness,
 * no vkDeviceWaitIdle during normal load.
 */
public final class TextureArrayBridgeV4 {

    private TextureArrayBridgeV4() {}

    /** ABI version — must match native side build_info::kTextureLoaderAbiVersion. */
    public static final int ABI_VERSION = 4;

    /** Cache schema version — must match native side build_info::kCacheSchemaVersion. */
    public static final int CACHE_SCHEMA_VERSION = 4;

    /** Channel mask: albedo plane present. */
    public static final int CHANNEL_ALBEDO   = 1 << 0;
    /** Channel mask: specular plane present. */
    public static final int CHANNEL_SPECULAR = 1 << 1;
    /** Channel mask: normal plane present. */
    public static final int CHANNEL_NORMAL   = 1 << 2;
    /** Channel mask: flag/emissive plane present. */
    public static final int CHANNEL_FLAG     = 1 << 3;

    /** Vulkan VK_FORMAT_R8G8B8A8_UNORM. */
    public static final int VK_FORMAT_R8G8B8A8_UNORM = 37;

    // ---- V4 lifecycle ----

    /** Begin a v4 texture load generation. Native allocates page pools. */
    public static native boolean nativeBeginTextureLoaderV4(long generation, long manifestPtr, int manifestBytes);

    /**
     * Upload a tiered texture page with explicit per-field arguments.
     * Albedo is mandatory; specular/normal/flag may be absent (pass 0).
     */
    public static native boolean nativeUploadTexturePageV4(
        long generation,
        int namespaceId,
        int tier,
        int page,
        int startLayer,
        int layerCount,
        int width,
        int height,
        int vkFormat,
        long albedoPtr,
        long specularPtr,
        long normalPtr,
        long flagPtr,
        long bytesPerLayer,
        int channelMask,
        boolean visible);

    /** Commit a v4 generation. Native finalizes page pools and publishes. */
    public static native boolean nativeCommitTextureLoaderV4(long generation);

    /** Cancel a v4 generation. Native discards all pending uploads. */
    public static native boolean nativeCancelTextureLoaderV4(long generation, int reasonCode);

    // ---- Sparse registry updates ----

    /** Sparse material table update — only changed entries. */
    public static native boolean nativeUpdateMaterialTableSparseV4(long generation, long entriesPtr, int entryCount);

    /** Sparse sprite registry update — only changed entries. */
    public static native boolean nativeUpdateSpriteRegistrySparseV4(long generation, long entriesPtr, int entryCount);

    // ---- Status JSON (for DebugBridge validation) ----

    /** Full texture loader v4 status. */
    public static native String nativeTextureLoaderV4StatusJson();

    /** Per-tier status. */
    public static native String nativeTextureTierStatusJsonV4();

    /** GPU upload queue status. */
    public static native String nativeGpuUploadQueueStatusJsonV4();

    /** Material page pool status. */
    public static native String nativeMaterialPagePoolStatusJsonV4();

    /** First-frame native readiness for a generation. */
    public static native String nativeFirstFrameNativeReadinessJsonV4(long generation);
}
