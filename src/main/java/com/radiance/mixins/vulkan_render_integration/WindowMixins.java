package com.radiance.mixins.vulkan_render_integration;

import static org.lwjgl.glfw.GLFW.GLFW_CLIENT_API;
import static org.lwjgl.glfw.GLFW.GLFW_NO_API;

import com.radiance.client.proxy.vulkan.WindowProxy;
import com.radiance.v2.bridge.BootTrace;
import com.radiance.v2.bridge.V2BootGate;
import net.minecraft.client.WindowEventHandler;
import net.minecraft.client.WindowSettings;
import net.minecraft.client.util.MonitorTracker;
import net.minecraft.client.util.Window;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GLCapabilities;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Window.class)
public class WindowMixins {

    @Final
    @Shadow
    private long handle;

    // Phase 1 reversible-boot gate. The actual probe + decision lives in
    // {@link V2BootGate} (a regular helper class) because Mixin disallows
    // non-private static methods, which prevents exposing the result via
    // a static accessor on the mixin class itself.

    @Redirect(method =
        "<init>(Lnet/minecraft/client/WindowEventHandler;Lnet/minecraft/client/util/MonitorTracker;"
            +
            "Lnet/minecraft/client/WindowSettings;Ljava/lang/String;Ljava/lang/String;)V",
        at = @At(value = "INVOKE", target = "Lorg/lwjgl/glfw/GLFW;glfwWindowHint(II)V", ordinal = 0), remap = false)
    public void cancelGLFWWindowHint0(int hint, int value) {
        V2BootGate.ensureChecked();
        if (!V2BootGate.isActive()) GLFW.glfwWindowHint(hint, value);
    }

    @Redirect(method =
        "<init>(Lnet/minecraft/client/WindowEventHandler;Lnet/minecraft/client/util/MonitorTracker;"
            +
            "Lnet/minecraft/client/WindowSettings;Ljava/lang/String;Ljava/lang/String;)V",
        at = @At(value = "INVOKE", target = "Lorg/lwjgl/glfw/GLFW;glfwWindowHint(II)V", ordinal = 1, remap = false))
    public void cancelGLFWWindowHint1(int hint, int value) {
        if (!V2BootGate.isActive()) GLFW.glfwWindowHint(hint, value);
    }

    @Redirect(method =
        "<init>(Lnet/minecraft/client/WindowEventHandler;Lnet/minecraft/client/util/MonitorTracker;"
            +
            "Lnet/minecraft/client/WindowSettings;Ljava/lang/String;Ljava/lang/String;)V",
        at = @At(value = "INVOKE", target = "Lorg/lwjgl/glfw/GLFW;glfwWindowHint(II)V", ordinal = 2, remap = false))
    public void cancelGLFWWindowHint2(int hint, int value) {
        if (!V2BootGate.isActive()) GLFW.glfwWindowHint(hint, value);
    }

    @Redirect(method =
        "<init>(Lnet/minecraft/client/WindowEventHandler;Lnet/minecraft/client/util/MonitorTracker;"
            +
            "Lnet/minecraft/client/WindowSettings;Ljava/lang/String;Ljava/lang/String;)V",
        at = @At(value = "INVOKE", target = "Lorg/lwjgl/glfw/GLFW;glfwWindowHint(II)V", ordinal = 3, remap = false))
    public void cancelGLFWWindowHint3(int hint, int value) {
        if (!V2BootGate.isActive()) GLFW.glfwWindowHint(hint, value);
    }

    @Redirect(method =
        "<init>(Lnet/minecraft/client/WindowEventHandler;Lnet/minecraft/client/util/MonitorTracker;"
            +
            "Lnet/minecraft/client/WindowSettings;Ljava/lang/String;Ljava/lang/String;)V",
        at = @At(value = "INVOKE", target = "Lorg/lwjgl/glfw/GLFW;glfwWindowHint(II)V", ordinal = 4, remap = false))
    public void cancelGLFWWindowHint4(int hint, int value) {
        if (!V2BootGate.isActive()) GLFW.glfwWindowHint(hint, value);
    }

    @Redirect(method =
        "<init>(Lnet/minecraft/client/WindowEventHandler;Lnet/minecraft/client/util/MonitorTracker;"
            +
            "Lnet/minecraft/client/WindowSettings;Ljava/lang/String;Ljava/lang/String;)V",
        at = @At(value = "INVOKE", target = "Lorg/lwjgl/glfw/GLFW;glfwWindowHint(II)V", ordinal = 5, remap = false))
    public void cancelGLFWWindowHint5(int hint, int value) {
        if (!V2BootGate.isActive()) GLFW.glfwWindowHint(hint, value);
    }

    @Inject(method =
        "<init>(Lnet/minecraft/client/WindowEventHandler;Lnet/minecraft/client/util/MonitorTracker;"
            +
            "Lnet/minecraft/client/WindowSettings;Ljava/lang/String;Ljava/lang/String;)V",
        at = @At(value = "INVOKE",
            target = "Lorg/lwjgl/glfw/GLFW;glfwWindowHint(II)V",
            ordinal = 5,
            shift = At.Shift.AFTER,
            remap = false))
    public void addNewGLFWWindowHint(WindowEventHandler eventHandler,
        MonitorTracker monitorTracker,
        WindowSettings settings,
        String fullscreenVideoMode,
        String title,
        CallbackInfo ci) {
        // Phase 1 reversible-boot commit point: only set GLFW_NO_API after the
        // probe has confirmed Vulkan can come up. If isActive is false (probe
        // failed or V2 disabled), the original 6 hints have been preserved
        // above and we let GLFW build a normal GL-capable window.
        if (V2BootGate.isActive()) {
            BootTrace.event("V2_GLFW_NO_API_COMMITTED");
            GLFW.glfwWindowHint(GLFW_CLIENT_API, GLFW_NO_API);
        }
    }

    @Redirect(method =
        "<init>(Lnet/minecraft/client/WindowEventHandler;Lnet/minecraft/client/util/MonitorTracker;"
            +
            "Lnet/minecraft/client/WindowSettings;Ljava/lang/String;Ljava/lang/String;)V",
        at = @At(value = "INVOKE", target = "Lorg/lwjgl/glfw/GLFW;glfwMakeContextCurrent(J)V", remap = false))
    public void cancelGLFWMakeContextCurrent(long window) {
        if (!V2BootGate.isActive()) GLFW.glfwMakeContextCurrent(window);
    }

    @Redirect(method =
        "<init>(Lnet/minecraft/client/WindowEventHandler;Lnet/minecraft/client/util/MonitorTracker;"
            +
            "Lnet/minecraft/client/WindowSettings;Ljava/lang/String;Ljava/lang/String;)V",
        at = @At(value = "INVOKE",
            target = "Lorg/lwjgl/opengl/GL;createCapabilities()Lorg/lwjgl/opengl/GLCapabilities;",
            remap = false))
    public GLCapabilities cancelGLCreateCapabilities() {
        if (!V2BootGate.isActive()) return org.lwjgl.opengl.GL.createCapabilities();
        return null;
    }

    @Redirect(method =
        "<init>(Lnet/minecraft/client/WindowEventHandler;Lnet/minecraft/client/util/MonitorTracker;"
            +
            "Lnet/minecraft/client/WindowSettings;Ljava/lang/String;Ljava/lang/String;)V",
        at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/RenderSystem;maxSupportedTextureSize()I", remap = false))
    public int cancelGetMaxSupportedTextureSize() {
        if (!V2BootGate.isActive()) return com.mojang.blaze3d.systems.RenderSystem.maxSupportedTextureSize();
        return 0;
    }

    @Redirect(method =
        "<init>(Lnet/minecraft/client/WindowEventHandler;Lnet/minecraft/client/util/MonitorTracker;"
            +
            "Lnet/minecraft/client/WindowSettings;Ljava/lang/String;Ljava/lang/String;)V",
        at = @At(value = "INVOKE", target = "Lorg/lwjgl/glfw/GLFW;glfwSetWindowSizeLimits(JIIII)V", remap = false))
    public void cancelGetMaxSupportedTextureSize(long window, int minwidth, int minheight,
        int maxwidth, int maxheight) {
        // Don't allow user to set window size manually
    }

    @Redirect(method = "onFramebufferSizeChanged(JII)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/WindowEventHandler;onResolutionChanged()V"))
    public void guardAndNotifyOnResize(net.minecraft.client.WindowEventHandler handler) {
        // Always notify Vulkan about the resize
        WindowProxy.onFramebufferSizeChanged();
        // Skip Minecraft's onResolutionChanged during init (gameRenderer null → NPE)
        if (!com.radiance.client.option.Options.suppressResizeCallback) {
            handler.onResolutionChanged();
        }
    }

}
