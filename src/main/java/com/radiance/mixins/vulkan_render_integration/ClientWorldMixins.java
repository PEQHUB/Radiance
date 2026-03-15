package com.radiance.mixins.vulkan_render_integration;

import com.radiance.client.option.Options;
import net.minecraft.client.world.ClientWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientWorld.class)
public class ClientWorldMixins {

    @Inject(method = "tickEntities", at = @At("HEAD"), cancellable = true)
    private void cancelEntityTicks(CallbackInfo ci) {
        if (Options.offlineState != 0) {
            ci.cancel();
        }
    }
}
