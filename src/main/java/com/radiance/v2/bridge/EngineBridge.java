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
    private static native void nativePostResize0(int width, int height);
    private static native void nativePostShutdown0();
    private static native String nativeGetDeviceName0();

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
