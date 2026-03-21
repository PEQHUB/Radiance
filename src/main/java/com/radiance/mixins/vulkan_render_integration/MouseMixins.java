package com.radiance.mixins.vulkan_render_integration;

import com.radiance.client.option.Options;
import com.radiance.client.ui.InputEvent;
import com.radiance.client.ui.InputEventQueue;
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
    private static boolean rawMouseEnabled = false;

    // Handle mouse look: suppress in accumulating, route to freecam in FREE+freecam
    @Inject(method = "onCursorPos", at = @At("HEAD"), cancellable = true)
    private void handleMouseLook(long window, double mouseX, double mouseY, CallbackInfo ci) {
        // Enable raw mouse input on first call (bypasses OS acceleration)
        if (!rawMouseEnabled) {
            if (org.lwjgl.glfw.GLFW.glfwRawMouseMotionSupported()) {
                org.lwjgl.glfw.GLFW.glfwSetInputMode(window,
                    org.lwjgl.glfw.GLFW.GLFW_RAW_MOUSE_MOTION,
                    org.lwjgl.glfw.GLFW.GLFW_TRUE);
            }
            rawMouseEnabled = true;
        }

        // Forward mouse position to decoupled UI thread
        if (InputEventQueue.isActive()) {
            InputEventQueue.enqueue(new InputEvent.MouseMove(System.nanoTime(), mouseX, mouseY));
        }

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

    // Forward mouse button events to decoupled UI thread
    @Inject(method = "onMouseButton", at = @At("HEAD"))
    private void forwardMouseButton(long window, int button, int action, int mods, CallbackInfo ci) {
        if (InputEventQueue.isActive()) {
            InputEventQueue.enqueue(new InputEvent.MouseButton(System.nanoTime(), button, action, mods));
        }
    }

    // Focus scroll acceleration state
    private static long lastFocusScrollNanos = 0;

    // Scroll wheel: plain=focus distance, Shift=time dial (offline mode only)
    // Focus distance: 1/16th block base step with velocity acceleration (cinema lens feel)
    @Inject(method = "onMouseScroll", at = @At("HEAD"), cancellable = true)
    private void handleOfflineScroll(long window, double horizontal, double vertical, CallbackInfo ci) {
        // Forward scroll to decoupled UI thread
        if (InputEventQueue.isActive()) {
            InputEventQueue.enqueue(new InputEvent.MouseScroll(System.nanoTime(), horizontal, vertical));
        }

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
                boolean ctrl = org.lwjgl.glfw.GLFW.glfwGetKey(window, org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_CONTROL)
                    == org.lwjgl.glfw.GLFW.GLFW_PRESS;
                float direction = vertical > 0 ? 1.0f : -1.0f;

                // Velocity computation (shared by focus distance and focal length)
                long now = System.nanoTime();
                long dtNanos = now - lastFocusScrollNanos;
                lastFocusScrollNanos = now;
                float dtMs = dtNanos / 1_000_000.0f;
                float velocity = Math.min(8.0f, Math.max(1.0f, 200.0f / Math.max(dtMs, 25.0f)));

                if (ctrl) {
                    // Ctrl+scroll: adjust focal length (14-200mm)
                    int step = velocity > 2.0f ? 5 : 1;
                    int newFL = Options.focalLengthMM + (int)(direction * step);
                    newFL = Math.max(14, Math.min(200, newFL));
                    if (newFL != Options.focalLengthMM) {
                        Options.focalLengthMM = newFL;
                        Options.syncApertureToNative();
                        if (Options.offlineState == 2) Options.nativeResetAccumulation();
                    }
                } else {
                    // Plain scroll: adjust focus distance with acceleration
                    // If in AF-C, scroll cancels to MF (manual override)
                    if (Options.focusMode == 2) {
                        Options.focusMode = 0;
                    }

                    float dist = Options.offlineFocalDistance;

                    // Base step: 1/16 block (0.0625)
                    float baseStep = 1.0f / 16.0f;

                    // Distance-proportional scaling: above 16 blocks, step grows with distance
                    // Matches cinema lens hyperbolic focus ring behavior
                    float proportionalStep = dist * 0.04f;
                    float step = Math.max(baseStep, proportionalStep);
                    step *= velocity;

                    float newDist = dist + direction * step;
                    newDist = Math.max(0.5f, Math.min(256.0f, newDist));
                    newDist = Math.round(newDist * 16.0f) / 16.0f;

                    if (Math.abs(newDist - Options.offlineFocalDistance) > 0.001f) {
                        Options.offlineFocalDistance = newDist;
                        Options.nativeSetOfflineFocalDistance(newDist, true);
                        if (Options.offlineState == 2) {
                            Options.nativeResetAccumulation();
                        }
                    }
                }
            }
            ci.cancel();
        }
    }
}
