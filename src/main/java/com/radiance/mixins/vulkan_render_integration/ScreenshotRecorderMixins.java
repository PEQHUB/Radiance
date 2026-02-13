package com.radiance.mixins.vulkan_render_integration;

import com.radiance.client.option.Options;
import com.radiance.client.proxy.vulkan.RendererProxy;
import com.radiance.client.util.HdrPngScreenshotWriter;
import java.io.File;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ScreenshotRecorder.class)
public class ScreenshotRecorderMixins {

    private static final AtomicReference<RendererProxy.HdrPackedScreenshot>
        PENDING_HDR_FRAME =
        new AtomicReference<>();

    @Inject(method = "takeScreenshot(Lnet/minecraft/client/gl/Framebuffer;)Lnet/minecraft/client/texture/NativeImage;",
        at = @At(value = "HEAD"),
        cancellable = true)
    private static void redirectTakeScreenshot(Framebuffer framebuffer,
        CallbackInfoReturnable<NativeImage> cir) {
        if (Options.hdrEnabled && Options.isHdrActive()) {
            PENDING_HDR_FRAME.set(RendererProxy.takeScreenshotHdrPacked(true));
        } else {
            PENDING_HDR_FRAME.set(null);
        }

        int width;
        int height;
        if (framebuffer != null) {
            width = framebuffer.textureWidth;
            height = framebuffer.textureHeight;
        } else {
            MinecraftClient mc = MinecraftClient.getInstance();
            width = mc.getWindow().getWidth();
            height = mc.getWindow().getHeight();
        }

        NativeImage nativeImage = new NativeImage(width, height, false);
        nativeImage.loadFromTextureImage(0, true);
        cir.setReturnValue(nativeImage);
    }

    @Inject(method = "method_1661(Lnet/minecraft/client/texture/NativeImage;Ljava/io/File;Ljava/util/function/Consumer;)V",
        at = @At("TAIL"))
    private static void writeHdrScreenshotCopy(NativeImage nativeImage, File file,
        Consumer<Text> consumer, CallbackInfo ci) {
        RendererProxy.HdrPackedScreenshot frame = PENDING_HDR_FRAME.getAndSet(null);
        if (frame == null) {
            return;
        }

        try {
            if (frame.width() != nativeImage.getWidth() || frame.height() != nativeImage.getHeight()) {
                return;
            }
            HdrPngScreenshotWriter.writeHdrCopy(file.toPath(), frame.width(), frame.height(),
                frame.vkFormat(), frame.packedPixels());
        } catch (Throwable ignored) {
        }
    }
}
