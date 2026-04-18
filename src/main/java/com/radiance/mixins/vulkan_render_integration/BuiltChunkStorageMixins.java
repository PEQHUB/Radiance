package com.radiance.mixins.vulkan_render_integration;

import com.radiance.client.proxy.world.ChunkProxy;
import com.radiance.v2.bridge.EngineBridge;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.BuiltChunkStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BuiltChunkStorage.class)
public class BuiltChunkStorageMixins {

    @Inject(method = "clear()V", at = @At(value = "HEAD"))
    public void clearChunkProxy(CallbackInfo ci) {
        ChunkProxy.clear();
        // Only send worldUnload when we are doing a real world teardown (world == null).
        // BuiltChunkStorage.clear() is also called internally during createChunks() when
        // setting up a new world — in that case world is non-null and we must NOT send
        // worldUnload, which would reset inWorld_=false immediately after worldLoad() set it.
        if (EngineBridge.isV2Active()) {
            MinecraftClient mc = MinecraftClient.getInstance();
            boolean realTeardown = (mc == null || mc.world == null);
            if (realTeardown) {
                EngineBridge.worldUnload();
            }
        }
    }

    @ModifyVariable(method = "createChunks(Lnet/minecraft/client/render/chunk/ChunkBuilder;)V", at = @At(value = "STORE"), ordinal = 0)
    private int initChunkRebuildGrid(int i) {
        ChunkProxy.init(i);
        return i;
    }
}
