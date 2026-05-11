package com.radiance.mixins.vulkan_render_integration;

import com.mojang.blaze3d.systems.RenderSystem;
import com.radiance.client.proxy.vulkan.RendererProxy;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.gl.ShaderProgramKey;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(RenderSystem.class)
public abstract class RenderSystemMixins {

    @Shadow(remap = false)
    private static Matrix4f projectionMatrix;

    @Shadow(remap = false)
    private static Matrix4f savedProjectionMatrix;

    @Final
    @Shadow(remap = false)
    private static Matrix4fStack modelViewStack;

    @Shadow(remap = false)
    private static Matrix4f textureMatrix;

    @Inject(method = "maxSupportedTextureSize()I", at = @At(value = "HEAD"), cancellable = true, remap = false)
    private static void redirectMaxSupportedTextureSize(CallbackInfoReturnable<Integer> cir) {
        int maxImageSize = RendererProxy.maxSupportedTextureSize();
        cir.setReturnValue(maxImageSize);
    }

    @Inject(method = "setShader(Lnet/minecraft/client/gl/ShaderProgramKey;)Lnet/minecraft/client/gl/ShaderProgram;",
        at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/RenderSystem;assertOnRenderThread()V", shift = At.Shift.AFTER, remap = false),
        cancellable = true)
    private static void redirectSetShaderProgram(ShaderProgramKey shaderProgramKey,
        CallbackInfoReturnable<ShaderProgram> cir) {
        // need formalize
        int
            type =
            switch (shaderProgramKey.configId()
                .toString()) {
                case "minecraft:core/rendertype_glint",
                     "minecraft:core/rendertype_glint_translucent",
                     "minecraft:core/rendertype_entity_glint",
                     "minecraft:core/rendertype_armor_entity_glint" -> 0;
                case "minecraft:core/position_color", "minecraft:core/rendertype_gui",
                     "minecraft:core/rendertype_gui_overlay",
                     "minecraft:core/rendertype_gui_text_highlight" -> 1;
                case "minecraft:core/position_tex_color" -> 2;
                case "minecraft:core/rendertype_text" -> 3;
                case "minecraft:core/rendertype_entity_cutout",
                     "minecraft:core/rendertype_entity_cutout_no_cull",
                     "minecraft:core/rendertype_entity_cutout_no_cull_z_offset",
                     "minecraft:core/rendertype_entity_translucent",
                     "minecraft:core/rendertype_item_entity_translucent_cull",
                     "minecraft:core/rendertype_entity_solid" -> 4;
                case "minecraft:core/rendertype_entity_no_outline",
                     "minecraft:core/rendertype_armor_cutout_no_cull",
                     "minecraft:core/rendertype_armor_translucent" -> 5;
                case "minecraft:core/rendertype_end_portal" -> 6;
                case "minecraft:core/position" -> 7;
                default -> throw new IllegalStateException(
                    "Unexpected value: " + shaderProgramKey.configId());
            };

        RendererProxy.bindOverlayPipeline(type);

        cir.setReturnValue(null);
    }

    @Redirect(method = "flipFrame(JLnet/minecraft/client/util/tracy/TracyFrameCapturer;)V",
        at = @At(value = "INVOKE", target = "Lorg/lwjgl/glfw/GLFW;glfwSwapBuffers(J)V", remap = false))
    private static void cancelSwapBuffers(long window) {

    }

    @Redirect(method = "renderCrosshair(I)V", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/platform/GLX;_renderCrosshair(IZZZ)V"))
    private static void cancelDrawCrossAirForNow(int size, boolean drawX, boolean drawY,
        boolean drawZ) {

    }
}
