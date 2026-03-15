package com.radiance.mixins.vulkan_render_integration;

import com.radiance.client.option.Options;
import net.minecraft.client.input.KeyboardInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardInput.class)
public class KeyboardInputMixins {

    // Suppress WASD movement when camera is locked (accumulating)
    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void suppressMovement(boolean slowDown, float slowDownFactor, CallbackInfo ci) {
        if (Options.offlineState == 2) {
            ci.cancel();
        }
    }
}
