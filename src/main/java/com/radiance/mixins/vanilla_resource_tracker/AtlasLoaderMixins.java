package com.radiance.mixins.vanilla_resource_tracker;

import com.radiance.client.texture.compat.ResourcePackCompatAtlasSource;
import java.util.List;
import net.minecraft.client.texture.atlas.AtlasLoader;
import net.minecraft.client.texture.atlas.AtlasSource;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AtlasLoader.class)
public abstract class AtlasLoaderMixins {
    @Shadow
    @Final
    private List<AtlasSource> sources;

    @Inject(method = "of", at = @At("RETURN"))
    private static void radiance$appendMaterialCompatAtlasSource(ResourceManager resourceManager,
        Identifier atlasId, CallbackInfoReturnable<AtlasLoader> cir) {
        AtlasLoader loader = cir.getReturnValue();
        if (loader != null) {
            ((AtlasLoaderMixins) (Object) loader).radiance$appendMaterialCompatAtlasSource(atlasId);
        }
    }

    @Unique
    private void radiance$appendMaterialCompatAtlasSource(Identifier atlasId) {
        if (ResourcePackCompatAtlasSource.shouldInjectForAtlas(atlasId)) {
            sources.add(ResourcePackCompatAtlasSource.INSTANCE);
        }
    }
}
