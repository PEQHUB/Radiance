package com.radiance.mixins.vulkan_render_integration;

import com.radiance.client.option.Options;
import com.radiance.client.texture.TextureTracker;
import net.minecraft.client.texture.SpriteAtlasTexture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SpriteAtlasTexture.class)
public class SpriteAnimationMixin {

    // Freeze animated textures (fire, water flow, lava, prismarine) during:
    // 1. Offline accumulation mode (deterministic rendering)
    // 2. When texture-array animation updates are disabled (default)
    //    This prevents vanilla from re-uploading animated atlas regions to the GL
    //    texture every client tick (~20 TPS), which causes menu stutter with
    //    high-res texture packs. C++ texture arrays handle animation independently.
    @Inject(method = "tickAnimatedSprites", at = @At("HEAD"), cancellable = true)
    private void freezeAnimatedSprites(CallbackInfo ci) {
        if (Options.offlineState != 0) {
            ci.cancel();
            return;
        }
        // When texture-array animation is disabled (default), skip vanilla atlas
        // animation ticks entirely. C++ texture arrays are authoritative for block
        // texture animation. Vanilla GL atlas animation is redundant and causes
        // per-tick NativeImage pixel copies + GL upload calls that stall the
        // render thread at ~51ms cadence (matching 20 TPS client ticks).
        if (!TextureTracker.textureArrayAnimationUpdatesEnabled) {
            ci.cancel();
        }
    }
}
