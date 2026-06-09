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
    private static boolean v2Compiled = false;   // JNI symbols present in core.dll
    private static boolean v2Requested = false;  // Options.useV2Engine was true at init time
    private static boolean v2Active = false;     // init succeeded, engine running
    private static String lastInitError = null;  // diagnostic message on failure

    static {
        try {
            nativeIsInitialized0();  // probe for V2 JNI symbol
            v2Compiled = true;
        } catch (UnsatisfiedLinkError e) {
            v2Compiled = false;
        }
    }

    // --- Native declarations (private, wrapped below) ---

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
        long vertexPtr, int vertexSize, long indexPtr, int indexCount, int triangleCount);
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

    // --- State accessors ---

    public static boolean isV2Compiled() { return v2Compiled; }
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

    /**
     * Run one frame (acquire, render, present). Returns false if shutdown was requested.
     */
    public static boolean nativeTick() {
        if (!v2Active) return false;
        try {
            return nativeTick0();
        } catch (UnsatisfiedLinkError e) {
            return false;
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

    // --- Scene submission ---

    public static void submitChunk(int chunkX, int sectionY, int chunkZ,
                                   int originX, int originY, int originZ,
                                   long vertexPtr, int vertexSize,
                                   long indexPtr, int indexCount, int triangleCount) {
        if (!v2Active) return;
        try {
            nativeSubmitChunk0(chunkX, sectionY, chunkZ, originX, originY, originZ,
                vertexPtr, vertexSize, indexPtr, indexCount, triangleCount);
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

    // --- Java helpers ---

    /**
     * Initialize V2 from a Minecraft Window (extracts HWND via LWJGL).
     */
    public static boolean initFromWindow(Window window, String configDir, boolean enableValidation) {
        v2Requested = true;
        lastInitError = null;

        if (!v2Compiled) {
            lastInitError = "V2 JNI symbols not compiled into core.dll";
            return false;
        }

        try {
            long glfwHandle = window.getHandle();
            long hwnd = GLFWNativeWin32.glfwGetWin32Window(glfwHandle);
            boolean ok = nativeInitV2(configDir, hwnd, enableValidation);
            v2Active = ok;
            if (!ok) {
                lastInitError = "nativeInitV2 returned false (check engine logs)";
            }
            return ok;
        } catch (UnsatisfiedLinkError e) {
            v2Active = false;
            lastInitError = "UnsatisfiedLinkError: " + e.getMessage();
            return false;
        } catch (Exception e) {
            v2Active = false;
            lastInitError = "Exception: " + e.getMessage();
            return false;
        }
    }
}
