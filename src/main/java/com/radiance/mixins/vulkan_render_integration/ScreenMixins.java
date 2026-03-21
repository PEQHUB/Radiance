package com.radiance.mixins.vulkan_render_integration;

import com.radiance.client.ui.UIThread;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Screen.class)
public class ScreenMixins {

    @Redirect(method = "applyBlur()V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gl/Framebuffer;beginWrite(Z)V"))
    public void cancelFrameBufferInApplyBlur(Framebuffer instance, boolean setViewport) {

    }

    /**
     * Skip the dark overlay (renderDarkening) for Radiance settings screens
     * so the game view is visible behind the menu without a vignette/tint.
     */
    @Inject(method = "renderDarkening(Lnet/minecraft/client/gui/DrawContext;)V", at = @At("HEAD"), cancellable = true)
    private void skipDarkeningForRadianceScreens(DrawContext context, CallbackInfo ci) {
        if (((Object) this).getClass().getPackageName().startsWith("com.radiance.client.gui")) {
            ci.cancel();
        }
    }

    /**
     * Also cancel the 4-arg variant called directly by some code paths.
     */
    @Inject(method = "renderDarkening(Lnet/minecraft/client/gui/DrawContext;IIII)V", at = @At("HEAD"), cancellable = true)
    private void skipDarkening4ArgForRadianceScreens(DrawContext context, int x, int y, int w, int h, CallbackInfo ci) {
        if (((Object) this).getClass().getPackageName().startsWith("com.radiance.client.gui")) {
            ci.cancel();
        }
    }

    /**
     * Also cancel renderInGameBackground which draws a separate dark gradient.
     */
    @Inject(method = "renderInGameBackground", at = @At("HEAD"), cancellable = true)
    private void skipInGameBgForRadianceScreens(DrawContext context, CallbackInfo ci) {
        if (((Object) this).getClass().getPackageName().startsWith("com.radiance.client.gui")) {
            ci.cancel();
        }
    }

    // ── Decoupled UI: gate screen input to only accept from UI thread ──
    // When UIThread is running, the main thread must not dispatch input to screens
    // (the UI thread handles it via InputEventQueue). Prevents double-dispatch.
    // Only gate methods that Screen actually declares — parent interface methods
    // (mouseClicked, keyReleased, etc.) are handled by InputEventQueue consumption.

    // Input gating disabled — needs MainThreadTaskQueue for close-screen callback.
    // Without it, blocking render-thread keyPressed prevents escape from closing screens.
    // @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    // private void gateKeyPressed(int keyCode, int scanCode, int modifiers, CallbackInfoReturnable<Boolean> cir) {
    //     if (UIThread.isRunning() && !UIThread.isUIThread()) cir.setReturnValue(false);
    // }
}
