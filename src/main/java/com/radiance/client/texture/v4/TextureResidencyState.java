package com.radiance.client.texture.v4;

/**
 * Residency state for a single material or sprite.
 *
 * Tracks whether the texture data is on the GPU, pending upload,
 * or failed. Used by the first-frame planner and DebugBridge.
 */
public final class TextureResidencyState {

    /** Texture data is not on GPU and no upload is in progress. */
    public static final int NOT_RESIDENT = 0;
    /** Texture data is queued for upload but not yet on GPU. */
    public static final int PENDING_UPLOAD = 1;
    /** Texture data is being uploaded to GPU. */
    public static final int UPLOADING = 2;
    /** Texture data is on GPU and ready for sampling. */
    public static final int GPU_RESIDENT = 3;
    /** Upload failed — material will use fallback. */
    public static final int FAILED = 4;

    private TextureResidencyState() {}

    public static boolean isReady(int state) {
        return state == GPU_RESIDENT;
    }

    public static boolean isPending(int state) {
        return state == PENDING_UPLOAD || state == UPLOADING;
    }

    public static String name(int state) {
        return switch (state) {
            case NOT_RESIDENT -> "not_resident";
            case PENDING_UPLOAD -> "pending_upload";
            case UPLOADING -> "uploading";
            case GPU_RESIDENT -> "gpu_resident";
            case FAILED -> "failed";
            default -> "unknown(" + state + ")";
        };
    }
}
