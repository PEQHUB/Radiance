package com.radiance.mixins.vulkan_render_integration;

import com.radiance.client.option.Options;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.Mouse;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Mouse.class)
public class MouseMixins {

    // Suppress mouse look when camera is locked (accumulating) — but allow cursor on screens
    @Inject(method = "onCursorPos", at = @At("HEAD"), cancellable = true)
    private void suppressMouseLook(long window, double x, double y, CallbackInfo ci) {
        if (Options.offlineState == 2 && MinecraftClient.getInstance().currentScreen == null) {
            ci.cancel();
        }
    }

    // Scroll wheel: adjust time of day in offline FREE mode
    @Inject(method = "onMouseScroll", at = @At("HEAD"), cancellable = true)
    private void handleOfflineScroll(long window, double horizontal, double vertical, CallbackInfo ci) {
        if (Options.offlineState != 0 && Options.frozenDayTimeTicks >= 0) {
            net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
            if (client.currentScreen == null) {
                // Scroll adjusts frozen time: ±100 ticks per notch (Shift: ±10)
                boolean shift = org.lwjgl.glfw.GLFW.glfwGetKey(window, org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_SHIFT)
                    == org.lwjgl.glfw.GLFW.GLFW_PRESS;
                long delta = shift ? 10 : 100;
                Options.frozenDayTimeTicks = ((Options.frozenDayTimeTicks + (long)(vertical * delta)) % 24000L + 24000L) % 24000L;
                // Reset accumulation if time changed while accumulating
                if (Options.offlineState == 2) {
                    Options.nativeResetAccumulation();
                }
                ci.cancel();
            }
        }
    }
}
