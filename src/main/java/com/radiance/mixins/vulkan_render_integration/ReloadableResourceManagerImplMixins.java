package com.radiance.mixins.vulkan_render_integration;

import net.minecraft.resource.ReloadableResourceManagerImpl;
import net.minecraft.resource.ResourceReloader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ReloadableResourceManagerImpl.class)
public class ReloadableResourceManagerImplMixins {

    @Inject(method = "registerReloader(Lnet/minecraft/resource/ResourceReloader;)V", at = @At(value = "HEAD"))
    public void addInfo(ResourceReloader reloader, CallbackInfo ci) {
//        if (reloader == null) {
//            System.out.println("Reloader: null");
//        } else {
//            System.out.println("Reloader: " + reloader.getClass()
//                .getName());
//        }
    }
}
