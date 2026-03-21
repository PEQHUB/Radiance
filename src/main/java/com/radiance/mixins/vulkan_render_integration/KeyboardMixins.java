package com.radiance.mixins.vulkan_render_integration;

import com.radiance.client.ui.InputEvent;
import com.radiance.client.ui.InputEventQueue;
import net.minecraft.client.Keyboard;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Keyboard.class)
public class KeyboardMixins {

    @Inject(method = "onKey", at = @At("HEAD"))
    private void forwardKeyEvent(long window, int key, int scancode, int action, int mods,
        CallbackInfo ci) {
        if (InputEventQueue.isActive()) {
            InputEventQueue.enqueue(
                new InputEvent.KeyPress(System.nanoTime(), key, scancode, action, mods));
        }
    }

    @Inject(method = "onChar", at = @At("HEAD"))
    private void forwardCharEvent(long window, int codepoint, int mods, CallbackInfo ci) {
        if (InputEventQueue.isActive()) {
            InputEventQueue.enqueue(
                new InputEvent.CharTyped(System.nanoTime(), codepoint, mods));
        }
    }
}
