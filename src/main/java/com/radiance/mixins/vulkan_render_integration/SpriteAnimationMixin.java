package com.radiance.mixins.vulkan_render_integration;

import com.radiance.client.option.Options;
import net.minecraft.client.texture.SpriteAtlasTexture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SpriteAtlasTexture.class)
public class SpriteAnimationMixin {

    // Freeze animated textures (fire, water flow, lava, prismarine) during offline mode
    @Inject(method = "tickAnimatedSprites", at = @At("HEAD"), cancellable = true)
    private void freezeAnimatedSprites(CallbackInfo ci) {
        if (Options.offlineState != 0) {
            ci.cancel();
        }
    }
}
