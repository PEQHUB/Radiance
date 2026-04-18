package com.radiance.v2.bridge;

import net.minecraft.client.util.Window;
import org.lwjgl.glfw.GLFWNativeWin32;

/**
 * JNI bridge to the V2 engine (EngineApp).
 * All native methods are gated behind MCVR_ENABLE_ENGINE_V2 in core.dll.
 * When V2 is not compiled in, all methods gracefully return false/no-op.
 */
public class EngineBridge {

    // --- State model ---
    // Phase 2: classload no longer touches native code. The symbol-availability
    // probe is deferred to first use of probe()/initFromWindow(). This keeps
    // EngineBridge loadable even when core.dll is missing or stale, and makes
    // class-init failures impossible to silently mask the real V2 status.
    private static volatile Boolean v2Compiled = null;  // null = not yet probed
    private static boolean v2Requested = false;          // Options.useV2Engine at init time
    private static boolean v2Active = false;             // init succeeded, engine running
    private static String lastInitError = null;          // diagnostic message on failure

    private static boolean ensureV2Compiled() {
        Boolean cached = v2Compiled;
        if (cached != null) return cached;
        synchronized (EngineBridge.class) {
            if (v2Compiled != null) return v2Compiled;
            boolean compiled;
            try {
                nativeIsInitialized0();   // any native call resolves the symbol
                compiled = true;
            } catch (UnsatisfiedLinkError e) {
                compiled = false;
            }
            v2Compiled = compiled;
            return compiled;
        }
    }

    // --- Native declarations (private, wrapped below) ---

    private static native String nativeProbe0(String configDir, boolean enableValidation);
    private static native boolean nativeInitV2(String configDir, long nativeWindowHandle, boolean enableValidation);
    private static native boolean nativeTick0();
    private static native void nativeShutdown0();
    private static native boolean nativeIsInitialized0();
    private static native boolean nativeIsInWorld0();
    private static native void nativePostResize0(int width, int height);
    private static native void nativePostShutdown0();
    private static native String nativeGetDeviceName0();
    private static native void nativeSubmitChunk0(int chunkX, int sectionY, int chunkZ,
        int originX, int originY, int originZ,
        long vertexPtr, int vertexSize, long indexPtr, int indexCount, int triangleCount,
        long lightDataPtr, int lightCount);
    private static native void nativeRemoveChunk0(int chunkX, int sectionY, int chunkZ);
    private static native void nativeUpdateCamera0(float[] viewMatrix, float[] projMatrix,
        float posX, float posY, float posZ, float dirX, float dirY, float dirZ,
        float nearPlane, float farPlane);
    private static native void nativeUpdateSky0(
        float baseColorR, float baseColorG, float baseColorB,
        float horizonColorR, float horizonColorG, float horizonColorB, float horizonColorA,
        float sunDirX, float sunDirY, float sunDirZ,
        float moonDirX, float moonDirY, float moonDirZ,
        int skyType, boolean sunRisingOrSetting, boolean skyDark,
        boolean hasBlindnessOrDarkness, int submersionType, int moonPhase,
        float rainGradient, float thunderGradient,
        int sunTextureID, int moonTextureID);
    private static native void nativeUpdateTextureMapping0(long dataPtr, int dataSize);
    private static native void nativeSubmitSpriteTable0(long metaPtr, int count, int atlasWidth, int atlasHeight, int spriteSize);
    private static native void nativeSubmitSpritePixels0(long dataPtr, int totalBytes);
    private static native void nativeSubmitSpriteAuxPixels0(long specPtr, long normPtr, int bytesPerType);
    private static native void nativeSubmitAnimationFrames0(long dataPtr, int totalBytes);
    private static native void nativeTextureFinalize0();
    private static native void nativeSubmitAreaLights0(long dataPtr, int dataSize, int lightCount);
    private static native void nativeUploadEmissionData0(long dataPtr);
    private static native void nativeSubmitEntityBatch0(long entryPtr, int entryCount,
        long vertexPtr, int vertexBytes, long indexPtr, int indexCount);
    private static native void nativeSubmitPostEntities0(byte[] vertexData, int[] indexData, int[] drawCalls);
    private static native void nativeWorldUnload0();
    private static native void nativeWorldLoad0();
    private static native String nativeGetGpuProfile0();
    private static native void nativeResetAccumulation0();
    private static native String nativeGetV2Snapshot0();

    // --- State accessors ---

    public static boolean isV2Compiled() { return ensureV2Compiled(); }
    public static boolean isV2Requested() { return v2Requested; }
    public static boolean isV2Active() { return v2Active; }
    public static String getLastInitError() { return lastInitError; }

    /**
     * Check if V2 engine is initialized and ready.
     * Safe to call even when V2 is not compiled in.
     */
    public static boolean nativeIsInitialized() {
        return v2Active;
    }

    /**
     * Check if V2 engine is currently rendering a world (in-world).
     * When false, the GL pipeline should present normally for menus.
     */
    public static boolean nativeIsInWorld() {
        if (!v2Active) return false;
        try { return nativeIsInWorld0(); } catch (UnsatisfiedLinkError e) { return false; }
    }

    private static boolean firstTickLogged = false;

    /**
     * Run one frame (acquire, render, present). Returns false if shutdown was requested.
     */
    public static boolean nativeTick() {
        if (!v2Active) return false;
        try {
            boolean ok = nativeTick0();
            if (!firstTickLogged) {
                firstTickLogged = true;
                BootTrace.event(ok ? "V2_TICK_FIRST_OK" : "V2_TICK_FIRST_FAIL");
            }
            if (!ok) {
                BootTrace.event("V2_TICK_FAIL", "nativeTick0 returned false (device-lost or shutdown)");
            }
            return ok;
        } catch (UnsatisfiedLinkError e) {
            BootTrace.exception("V2_TICK_THREW_LINK", e);
            return false;
        } catch (RuntimeException e) {
            BootTrace.exception("V2_TICK_THREW", e);
            throw e;
        }
    }

    /**
     * Shut down the V2 engine and release all Vulkan resources.
     */
    public static void nativeShutdown() {
        if (!v2Active) return;
        try {
            nativeShutdown0();
        } catch (UnsatisfiedLinkError e) {
            // V2 not compiled in
        }
        v2Active = false;
    }

    public static void nativePostResize(int width, int height) {
        if (!v2Active) return;
        try { nativePostResize0(width, height); } catch (UnsatisfiedLinkError e) {}
    }

    public static void nativePostShutdown() {
        if (!v2Active) return;
        try { nativePostShutdown0(); } catch (UnsatisfiedLinkError e) {}
    }

    public static String nativeGetDeviceName() {
        if (!v2Active) return "N/A";
        try { return nativeGetDeviceName0(); } catch (UnsatisfiedLinkError e) { return "N/A"; }
    }

    /**
     * Returns per-pass GPU timing in V1-compatible format:
     * "RayTracing:5.234,DLSS:1.023,...,TOTAL:8.357"
     * Returns empty string when V2 is not active or GPU profiler is disabled.
     */
    public static String nativeGetGpuProfile() {
        if (!v2Active) return "";
        try { return nativeGetGpuProfile0(); } catch (UnsatisfiedLinkError e) { return ""; }
    }

    // --- Scene submission ---

    public static void submitChunk(int chunkX, int sectionY, int chunkZ,
                                   int originX, int originY, int originZ,
                                   long vertexPtr, int vertexSize,
                                   long indexPtr, int indexCount, int triangleCount,
                                   long lightDataPtr, int lightCount) {
        if (!v2Active) return;
        try {
            nativeSubmitChunk0(chunkX, sectionY, chunkZ, originX, originY, originZ,
                vertexPtr, vertexSize, indexPtr, indexCount, triangleCount,
                lightDataPtr, lightCount);
        } catch (UnsatisfiedLinkError e) {}
    }

    public static void removeChunk(int chunkX, int sectionY, int chunkZ) {
        if (!v2Active) return;
        try { nativeRemoveChunk0(chunkX, sectionY, chunkZ); } catch (UnsatisfiedLinkError e) {}
    }

    public static void updateCamera(float[] viewMatrix, float[] projMatrix,
                                    float posX, float posY, float posZ,
                                    float dirX, float dirY, float dirZ,
                                    float nearPlane, float farPlane) {
        if (!v2Active) return;
        try {
            nativeUpdateCamera0(viewMatrix, projMatrix, posX, posY, posZ,
                dirX, dirY, dirZ, nearPlane, farPlane);
        } catch (UnsatisfiedLinkError e) {}
    }

    public static void updateSky(
            float baseColorR, float baseColorG, float baseColorB,
            float horizonColorR, float horizonColorG, float horizonColorB, float horizonColorA,
            float sunDirX, float sunDirY, float sunDirZ,
            float moonDirX, float moonDirY, float moonDirZ,
            int skyType, boolean sunRisingOrSetting, boolean skyDark,
            boolean hasBlindnessOrDarkness, int submersionType, int moonPhase,
            float rainGradient, float thunderGradient,
            int sunTextureID, int moonTextureID) {
        if (!v2Active) return;
        try {
            nativeUpdateSky0(baseColorR, baseColorG, baseColorB,
                horizonColorR, horizonColorG, horizonColorB, horizonColorA,
                sunDirX, sunDirY, sunDirZ,
                moonDirX, moonDirY, moonDirZ,
                skyType, sunRisingOrSetting, skyDark,
                hasBlindnessOrDarkness, submersionType, moonPhase,
                rainGradient, thunderGradient,
                sunTextureID, moonTextureID);
        } catch (UnsatisfiedLinkError e) {}
    }

    /**
     * Upload the texture mapping SSBO bytes (block→texture lookup table).
     * Caller passes a raw memory address (e.g. from LWJGL memAddress()) and length.
     * The native side copies the data immediately, so the buffer is safe to free after return.
     */
    public static void updateTextureMapping(long dataPtr, int dataSize) {
        if (!v2Active) return;
        try { nativeUpdateTextureMapping0(dataPtr, dataSize); } catch (UnsatisfiedLinkError e) {}
    }

    // --- Texture upload ---

    public static void submitSpriteTable(long metaPtr, int count, int atlasWidth, int atlasHeight, int spriteSize) {
        if (!v2Active) return;
        try { nativeSubmitSpriteTable0(metaPtr, count, atlasWidth, atlasHeight, spriteSize); } catch (UnsatisfiedLinkError e) {}
    }

    public static void submitSpritePixels(long dataPtr, int totalBytes) {
        if (!v2Active) return;
        try { nativeSubmitSpritePixels0(dataPtr, totalBytes); } catch (UnsatisfiedLinkError e) {}
    }

    public static void submitSpriteAuxPixels(long specPtr, long normPtr, int bytesPerType) {
        if (!v2Active) return;
        try { nativeSubmitSpriteAuxPixels0(specPtr, normPtr, bytesPerType); } catch (UnsatisfiedLinkError e) {}
    }

    public static void submitAnimationFrames(long dataPtr, int totalBytes) {
        if (!v2Active) return;
        try { nativeSubmitAnimationFrames0(dataPtr, totalBytes); } catch (UnsatisfiedLinkError e) {}
    }

    public static void textureFinalize() {
        if (!v2Active) return;
        try { nativeTextureFinalize0(); } catch (UnsatisfiedLinkError e) {}
    }

    /**
     * Upload pre-gathered area light data to the V2 engine.
     * Each light entry is 48 bytes (AreaLight struct from shared.hpp).
     * The native side copies the data immediately, so the buffer is safe to free after return.
     *
     * @param dataPtr    native memory address of AreaLight[] array
     * @param dataSize   total bytes (lightCount * 48)
     * @param lightCount number of area lights
     */
    public static void submitAreaLights(long dataPtr, int dataSize, int lightCount) {
        if (!v2Active) return;
        try { nativeSubmitAreaLights0(dataPtr, dataSize, lightCount); } catch (UnsatisfiedLinkError e) {}
    }

    /**
     * Upload per-emissive-block emission data and gamut boosts to the V2 engine WorldUBO.
     * Layout: 50*4 floats (emissionData vec4[50]) followed by 13*4 floats (emissiveGamut vec4[13]).
     * Total: (50+13)*4*4 = 1008 bytes.
     * The native side copies immediately, so the buffer is safe to free after return.
     *
     * @param dataPtr native memory address of the packed float buffer
     */
    public static void uploadEmissionData(long dataPtr) {
        if (!v2Active) return;
        try { nativeUploadEmissionData0(dataPtr); } catch (UnsatisfiedLinkError e) {}
    }

    /**
     * Submit a batch of entity geometry to the V2 engine.
     * Each entry is 36 bytes packed: int hash, float x, float y, float z,
     * int rtFlag, int coordSystem, int vertexOffset, int indexOffset, int triangleCount.
     * Vertex data is pre-converted PBRTriangle format (96 bytes per vertex).
     * Index data is uint32 indices.
     *
     * @param entryPtr    native memory address of packed EntityEntry array
     * @param entryCount  number of entities
     * @param vertexPtr   native memory address of vertex data (PBRTriangle[])
     * @param vertexBytes total vertex data size in bytes
     * @param indexPtr    native memory address of uint32 index array
     * @param indexCount  number of indices
     */
    public static void submitEntityBatch(long entryPtr, int entryCount,
                                         long vertexPtr, int vertexBytes,
                                         long indexPtr, int indexCount) {
        if (!v2Active) return;
        try {
            nativeSubmitEntityBatch0(entryPtr, entryCount, vertexPtr, vertexBytes, indexPtr, indexCount);
        } catch (UnsatisfiedLinkError e) {}
    }

    /**
     * Submit post-entity draw data (particles, hand items, weather) to the V2 engine.
     * These entities are rasterized on top of the tone-mapped output, NOT included in RT.
     * Vertex data is PBRTriangle format (96 bytes per vertex).
     * Draw calls are packed as int triplets: {vertexByteOffset, indexElementOffset, indexCount}.
     *
     * @param vertexData  PBRTriangle vertex data as byte array
     * @param indexData   uint32 indices as int array
     * @param drawCalls   packed draw calls (3 ints per call)
     */
    public static void submitPostEntities(byte[] vertexData, int[] indexData, int[] drawCalls) {
        if (!v2Active) return;
        try {
            nativeSubmitPostEntities0(vertexData, indexData, drawCalls);
        } catch (UnsatisfiedLinkError e) {}
    }

    /**
     * Notify the V2 engine that a new world has been loaded and rendering should begin.
     * Sets inWorld_ = true so the next tick() renders rather than taking the menu early-out.
     */
    public static void worldLoad() {
        if (!v2Active) return;
        try {
            nativeWorldLoad0();
        } catch (UnsatisfiedLinkError e) {}
    }

    /**
     * Notify the V2 engine that the current world has been unloaded.
     * Clears the chunk registry, BLAS/TLAS, GPU uploads, entity state,
     * and resets inWorld_ so menus can render via OpenGL.
     */
    public static void worldUnload() {
        if (!v2Active) return;
        try {
            nativeWorldUnload0();
        } catch (UnsatisfiedLinkError e) {}
    }

    /**
     * Reset the offline accumulation counter (Welford N → 0).
     * Should be called when the user requests a fresh accumulation pass,
     * e.g. after changing any rendering setting while in offline-accum mode.
     * Safe to call when V2 is not active (no-op).
     */
    public static void nativeResetAccumulation() {
        if (!v2Active) return;
        try { nativeResetAccumulation0(); } catch (UnsatisfiedLinkError e) {}
    }

    /**
     * Return a multi-line diagnostic snapshot of the V2 engine state:
     * state, last frame index, last breadcrumb, last bridge command, mode, in-world.
     * Safe to call from any thread and in any engine state — callbacks from
     * Runtime shutdown hooks are the primary caller, so this must not touch
     * locks that could be held by a dying render thread.
     *
     * <p>Returns a header-only string when V2 is not compiled in, so the
     * caller (e.g. CrashContext) can always embed the output unconditionally.
     */
    public static String getV2Snapshot() {
        if (!ensureV2Compiled()) {
            return "state=NotCompiled\n";
        }
        try {
            String s = nativeGetV2Snapshot0();
            return s != null ? s : "state=NullSnapshot\n";
        } catch (UnsatisfiedLinkError e) {
            return "state=UnsatisfiedLinkError\n";
        } catch (Throwable t) {
            return "state=SnapshotThrew(" + t.getClass().getSimpleName() + ")\n";
        }
    }

    // --- Java helpers ---

    /**
     * Result of the dry-run Vulkan probe. {@code ok=false} means V2 cannot start
     * on this hardware/driver combination — no GLFW/window mutation has occurred,
     * so the legacy renderer is still safe to bring up.
     */
    public static final class ProbeResult {
        public final boolean ok;
        public final String gpuName;       // populated on success
        public final String reason;        // populated on failure
        public final String missingExtensions;  // comma-joined, may be empty
        public ProbeResult(boolean ok, String gpuName, String reason, String missing) {
            this.ok = ok;
            this.gpuName = gpuName;
            this.reason = reason;
            this.missingExtensions = missing;
        }
    }

    /**
     * Run the dry-run Vulkan probe. Creates and tears down a temporary
     * VkInstance + queries devices/extensions. Does NOT touch GLFW or the
     * window. Always safe to call before deciding to commit to V2 init.
     *
     * <p>Writes {@code v2_cannot_start.txt} (on failure) or {@code v2_probe_ok.txt}
     * (on success) into {@code radiance/logs/}.
     */
    public static ProbeResult probe(String configDir, boolean enableValidation) {
        if (!ensureV2Compiled()) {
            return new ProbeResult(false, "", "V2 JNI symbols not compiled into core.dll", "");
        }
        try {
            String payload = nativeProbe0(configDir, enableValidation);
            if (payload == null) payload = "FAIL|nativeProbe0 returned null|";
            String[] parts = payload.split("\\|", 3);
            if (parts.length >= 1 && "OK".equals(parts[0])) {
                String gpu = parts.length >= 2 ? parts[1] : "";
                return new ProbeResult(true, gpu, "", "");
            }
            String reason  = parts.length >= 2 ? parts[1] : "<unknown>";
            String missing = parts.length >= 3 ? parts[2] : "";
            return new ProbeResult(false, "", reason, missing);
        } catch (UnsatisfiedLinkError e) {
            return new ProbeResult(false, "", "UnsatisfiedLinkError: " + e.getMessage(), "");
        } catch (Throwable t) {
            return new ProbeResult(false, "", t.getClass().getSimpleName() + ": " + t.getMessage(), "");
        }
    }

    /**
     * Initialize V2 from a Minecraft Window (extracts HWND via LWJGL).
     */
    public static boolean initFromWindow(Window window, String configDir, boolean enableValidation) {
        v2Requested = true;
        lastInitError = null;

        boolean compiled = ensureV2Compiled();
        BootTrace.event("V2_INIT_CALLED",
                "configDir=" + configDir + " validation=" + enableValidation
                        + " v2Compiled=" + compiled);

        if (!compiled) {
            lastInitError = "V2 JNI symbols not compiled into core.dll";
            BootTrace.event("V2_INIT_FAIL", lastInitError);
            return false;
        }

        try {
            long glfwHandle = window.getHandle();
            long hwnd = GLFWNativeWin32.glfwGetWin32Window(glfwHandle);
            BootTrace.event("V2_INIT_HWND_OK", "glfw=" + Long.toHexString(glfwHandle)
                    + " hwnd=" + Long.toHexString(hwnd));
            boolean ok = nativeInitV2(configDir, hwnd, enableValidation);
            v2Active = ok;
            if (!ok) {
                lastInitError = "nativeInitV2 returned false (check engine logs)";
                BootTrace.event("V2_INIT_FAIL", lastInitError);
            } else {
                BootTrace.event("V2_INIT_OK");
            }
            return ok;
        } catch (UnsatisfiedLinkError e) {
            v2Active = false;
            lastInitError = "UnsatisfiedLinkError: " + e.getMessage();
            BootTrace.exception("V2_INIT_THREW_LINK", e);
            return false;
        } catch (Exception e) {
            v2Active = false;
            lastInitError = "Exception: " + e.getMessage();
            BootTrace.exception("V2_INIT_THREW", e);
            return false;
        }
    }
}
