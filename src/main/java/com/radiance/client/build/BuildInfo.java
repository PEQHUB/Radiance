package com.radiance.client.build;

/**
 * Build identity for Radiance mod.
 *
 * Generated at build time by gradle task generateBuildInfo.
 * Every runtime log and DebugBridge buildInfo command must print these fields.
 */
public final class BuildInfo {

    /** Git commit hash (full 40-char hex). */
    public static final String REPO_COMMIT = "db15f0491d4bd3d8532fbd9490b180aef9b8efa8";

    /** Git branch name. */
    public static final String BRANCH = "rehab/texture-loader-v4-finish-single-path";

    /** Whether the working tree had uncommitted changes at build time. */
    public static final boolean DIRTY = true;

    /** ISO-8601 timestamp of the build. */
    public static final String BUILD_TIMESTAMP = "2026-06-10T12:00:00Z";

    /** SHA-256 of the deployed JAR, or "unknown" if not yet computed. */
    public static final String JAR_SHA256 = "unknown";

    /** Texture loader ABI version — must match native side. */
    public static final int TEXTURE_LOADER_ABI_VERSION = 5;

    /** Cache schema version — must match native side. */
    public static final int CACHE_SCHEMA_VERSION = 5;

    private BuildInfo() {}

    public static String summary() {
        return "Radiance commit=" + REPO_COMMIT
            + " branch=" + BRANCH
            + " dirty=" + DIRTY
            + " abi=" + TEXTURE_LOADER_ABI_VERSION
            + " cache=" + CACHE_SCHEMA_VERSION
            + " built=" + BUILD_TIMESTAMP;
    }

    /** True if the deployed JAR hash matches the expected repo head. */
    public static boolean jarHashVerified() {
        return !"unknown".equals(JAR_SHA256) && !JAR_SHA256.isBlank();
    }
}
