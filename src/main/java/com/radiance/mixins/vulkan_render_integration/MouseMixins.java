package com.radiance.mixins.vulkan_render_integration;

import com.radiance.client.option.Options;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.Mouse;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Mouse.class)
public class MouseMixins {

    @Shadow private double x;
    @Shadow private double y;
    private static double lastFreecamX = Double.NaN;
    private static double lastFreecamY = Double.NaN;

    // Handle mouse look: suppress in accumulating, route to freecam in FREE+freecam
    @Inject(method = "onCursorPos", at = @At("HEAD"), cancellable = true)
    private void handleMouseLook(long window, double mouseX, double mouseY, CallbackInfo ci) {
        if (Options.offlineState == 2 && MinecraftClient.getInstance().currentScreen == null) {
            // Accumulating: suppress all mouse look
            ci.cancel();
            return;
        }
        if (Options.offlineState == 1 && Options.freecamEnabled
            && MinecraftClient.getInstance().currentScreen == null) {
            // FREE mode + freecam: route mouse to freecam rotation, suppress player rotation
            if (!Double.isNaN(lastFreecamX)) {
                double dx = mouseX - lastFreecamX;
                double dy = mouseY - lastFreecamY;
                double sensitivity = MinecraftClient.getInstance().options.getMouseSensitivity().getValue() * 0.6 + 0.2;
                double factor = sensitivity * sensitivity * sensitivity * 8.0;
                Options.freecam.applyMouseDelta(dx * factor * 0.15, dy * factor * 0.15, 1.0);
            }
            lastFreecamX = mouseX;
            lastFreecamY = mouseY;
            ci.cancel();
            return;
        }
        // Reset tracking when not in freecam
        lastFreecamX = Double.NaN;
        lastFreecamY = Double.NaN;
    }

    // Scroll wheel: plain=focus distance, Shift=time dial (offline mode only)
    @Inject(method = "onMouseScroll", at = @At("HEAD"), cancellable = true)
    private void handleOfflineScroll(long window, double horizontal, double vertical, CallbackInfo ci) {
        if (Options.offlineState != 0 && MinecraftClient.getInstance().currentScreen == null) {
            boolean shift = org.lwjgl.glfw.GLFW.glfwGetKey(window, org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_SHIFT)
                == org.lwjgl.glfw.GLFW.GLFW_PRESS;

            if (shift) {
                // Shift+scroll: time dial (existing behavior)
                if (Options.frozenDayTimeTicks >= 0) {
                    boolean ctrl = org.lwjgl.glfw.GLFW.glfwGetKey(window, org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_CONTROL)
                        == org.lwjgl.glfw.GLFW.GLFW_PRESS;
                    long delta = ctrl ? 10 : 100;
                    Options.frozenDayTimeTicks = ((Options.frozenDayTimeTicks + (long)(vertical * delta)) % 24000L + 24000L) % 24000L;
                    if (Options.offlineState == 2) {
                        Options.nativeResetAccumulation();
                    }
                }
            } else {
                // Plain scroll or Ctrl+scroll: adjust focus distance
                // If in AF-C, scroll cancels to MF (manual override)
                if (Options.focusMode == 2) {
                    Options.focusMode = 0;
                }
                boolean ctrl = org.lwjgl.glfw.GLFW.glfwGetKey(window, org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_CONTROL)
                    == org.lwjgl.glfw.GLFW.GLFW_PRESS;
                // Ctrl+scroll: ±0.5 block (round to nearest int for JNI), plain: ±1 block
                int delta = ctrl ? (vertical > 0 ? 1 : -1) : (vertical > 0 ? 1 : -1);
                // For Ctrl fine mode, alternate between +0 and +1 based on current value parity
                // Since offlineFocalDistance is int, Ctrl just uses ±1 as minimum step
                int newDist = Options.offlineFocalDistance + delta;
                newDist = Math.max(1, Math.min(256, newDist));
                if (newDist != Options.offlineFocalDistance) {
                    Options.offlineFocalDistance = newDist;
                    Options.nativeSetOfflineFocalDistance(newDist, true);
                    if (Options.offlineState == 2) {
                        Options.nativeResetAccumulation();
                    }
                }
            }
            ci.cancel();
        }
    }
}
