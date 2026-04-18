package com.radiance.mixins.vulkan_render_integration;

import com.radiance.client.RadianceClient;
import com.radiance.client.UnsafeManager;
import com.radiance.client.cloud.CloudTileManager;
import com.radiance.client.option.Options;
import com.radiance.client.pipeline.Pipeline;
import com.radiance.client.proxy.vulkan.RendererProxy;
import com.radiance.client.proxy.vulkan.TextureProxy;
import com.radiance.client.proxy.world.ChunkProxy;
import com.radiance.v2.bridge.BootTrace;
import com.radiance.v2.bridge.ConfigBridge;
import com.radiance.v2.bridge.EngineBridge;
import com.radiance.v2.scene.AreaLightPacker;
import java.util.Optional;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.GlTimer;
import net.minecraft.client.gl.ShaderLoader;
import net.minecraft.client.gl.WindowFramebuffer;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.util.Window;
import net.minecraft.resource.ReloadableResourceManagerImpl;
import net.minecraft.resource.ResourceReloader;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MinecraftClient.class)
public class MinecraftClientMixins {

    @Shadow
    @Final
    private Window window;

    @Shadow
    public net.minecraft.client.render.GameRenderer gameRenderer;

    //region <isAmbientOcclusionEnabled>
    @Inject(method = "isAmbientOcclusionEnabled()Z", at = @At(value = "HEAD"), cancellable = true)
    private static void disableAmbientOcclusion(CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(false);
    }
    // endregion

    // region <init>
    @Redirect(method = "<init>(Lnet/minecraft/client/RunArgs;)V",
        at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/RenderSystem;initRenderer(IZ)V"))
    public void initRenderer(int debugVerbosity, boolean debugSync) {
        // Restore window size+position BEFORE Vulkan init so swapchain picks up correct dimensions.
        // Suppress the framebuffer callback to avoid NPE (gameRenderer null during constructor).
        com.radiance.client.option.Options.suppressResizeCallback = true;
        com.radiance.client.option.Options.restoreWindow();
        com.radiance.client.option.Options.suppressResizeCallback = false;

        boolean v2Started = false;
        // Phase 1 reversible boot: WindowMixins runs the dry-run Vulkan probe
        // earlier in the boot sequence (via V2BootGate) and decides whether
        // the window was brought up GL-less. Only attempt V2 init if BOTH the
        // user wants V2 AND the probe accepted this hardware. Otherwise the
        // window has a GL context and we must fall through to the legacy renderer.
        boolean v2WantedThisSession = Options.useV2Engine
                && com.radiance.v2.bridge.V2BootGate.isActive();
        if (v2WantedThisSession) {
            System.out.println("[Radiance] V2 engine requested — compiled=" + EngineBridge.isV2Compiled());
            BootTrace.event("V2_REQUESTED", "compiled=" + EngineBridge.isV2Compiled());
            String configDir = RadianceClient.radianceDir.toAbsolutePath().toString();
            v2Started = EngineBridge.initFromWindow(window, configDir, Options.validationLayers);
            if (!v2Started) {
                System.err.println("[Radiance] V2 init failed: " + EngineBridge.getLastInitError());
                System.err.println("[Radiance] Falling back to legacy renderer for this session");
                BootTrace.event("V2_FALLBACK_LEGACY", EngineBridge.getLastInitError());
                // Do NOT clear Options.useV2Engine — keep the user's intent so
                // the next launch retries. The probe runs earlier and determines
                // whether GL was preserved, so falling back here without window
                // mutation is safe only when the probe gate already agreed.
            } else {
                System.out.println("[Radiance] V2 engine active — GPU: " + EngineBridge.nativeGetDeviceName());
                BootTrace.event("V2_ACTIVE", "gpu=" + EngineBridge.nativeGetDeviceName());
                ConfigBridge.syncAllSettings();
                BootTrace.event("V2_CONFIG_SYNCED");
            }
        } else if (Options.useV2Engine) {
            // V2 was requested but the probe rejected this hardware/driver
            // earlier. Skip V2 init entirely; the legacy path runs below with
            // a GL-capable window that WindowMixins preserved.
            BootTrace.event("V2_SKIPPED_PROBE_FAILED");
        }

        if (!v2Started) {
            // Legacy path: RendererProxy + Pipeline
            long stackSize = 512 * 1024 * 1024; // 32MB
            Runnable myRunnable = () -> {
                RendererProxy.initRenderer(window);
                Pipeline.collectNativeModules();
            };

            Thread myThread = new Thread(null, myRunnable, "", stackSize);
            myThread.start();
            try {
                myThread.join();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }

            Pipeline.loadPipeline();
            Pipeline.build();

            // Apply settings that require renderer to be initialized
            com.radiance.client.option.Options.applyDeferredSettings();

            // Auto-enable Reflex + VRR if hardware supports it and user hasn't explicitly configured
            com.radiance.client.option.Options.autoDetectReflexVrr();
        }
    }

    @Redirect(method = "<init>(Lnet/minecraft/client/RunArgs;)V",
        at = @At(value = "NEW", target = "net/minecraft/client/gl/WindowFramebuffer"))
    public WindowFramebuffer cancelNewFramebuffer(int width, int height) {
        // In V2 mode, no GL context is initialized, so calling new WindowFramebuffer()
        // would crash in glGetError() with a null LWJGL function pointer. Allocate the
        // instance without invoking its constructor (no GL calls). The framebuffer field
        // remains null in V2 mode — all framebuffer methods are suppressed by the
        // redirects below and the V2 engine handles its own presentation.
        return UnsafeManager.INSTANCE.allocateInstance(WindowFramebuffer.class);
    }

    @Redirect(method = "<init>(Lnet/minecraft/client/RunArgs;)V",
        at = @At(value = "FIELD",
            target = "Lnet/minecraft/client/MinecraftClient;framebuffer:Lnet/minecraft/client/gl/Framebuffer;",
            opcode = org.objectweb.asm.Opcodes.PUTFIELD))
    public void writeNullFramebuffer(MinecraftClient instance, Framebuffer value) {
        // Intentional no-op: leave framebuffer null in V2 mode. All framebuffer
        // method calls below guard on instance != null or nativeIsInitialized().
    }

    @Redirect(method = "<init>(Lnet/minecraft/client/RunArgs;)V",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/resource/ReloadableResourceManagerImpl;registerReloader" +
                "(Lnet/minecraft/resource/ResourceReloader;)V",
            ordinal = 2))
    public void cancelShaderLoaderRegister(ReloadableResourceManagerImpl instance,
        ResourceReloader reloader) {
        instance.registerReloader(reloader);
    }

    @Redirect(method = "<init>(Lnet/minecraft/client/RunArgs;)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gl/Framebuffer;setClearColor(FFFF)V"))
    public void cancelSetClearColor(Framebuffer instance, float r, float g, float b, float a) {
        // no-op in V2 mode
    }

    @Redirect(method = "<init>(Lnet/minecraft/client/RunArgs;)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gl/Framebuffer;clear()V"))
    public void cancelClear(Framebuffer instance) {
        // no-op in V2 mode
    }

    @Redirect(method = "<init>",
        at = @At(value = "FIELD",
            opcode = Opcodes.GETFIELD,
            target = "Lnet/minecraft/client/gl/Framebuffer;textureWidth:I",
            ordinal = 0))
    public int redirectFramebufferTextureWidth(Framebuffer framebuffer) {
        return this.window.getFramebufferWidth();
    }

    @Redirect(method = "<init>",
        at = @At(value = "FIELD", opcode = Opcodes.GETFIELD, target = "Lnet/minecraft/client/gl/Framebuffer;textureHeight:I"),
        require = 0)
    public int redirectFramebufferTextureHeight(Framebuffer framebuffer) {
        return this.window.getFramebufferHeight();
    }
    // endregion

    // region <render>
    @Redirect(method = "render(Z)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gl/Framebuffer;beginWrite(Z)V"))
    public void cancelFramebufferBeginWrite(Framebuffer instance, boolean setViewport) {
        // Always suppressed — V2 engine owns the framebuffer. In legacy mode instance is
        // non-null but we still suppress to avoid the GL state conflict with RendererProxy.
    }

    @Redirect(method = "render(Z)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gl/Framebuffer;endWrite()V"))
    public void cancelFramebufferEndWrite(Framebuffer instance) {
        if (EngineBridge.nativeIsInitialized()) {
            // Flush aggregated area lights to the V2 engine before the frame tick
            AreaLightPacker.flush();
            boolean ok = EngineBridge.nativeTick();
            if (!ok) {
                // V2 engine reported a fatal error (e.g. DEVICE_LOST). Tear it down and
                // schedule a clean JVM exit. Do NOT call instance.endWrite() — the vanilla
                // GL framebuffer was never initialized in V2 mode.
                BootTrace.event("V2_SHUTDOWN_BEGIN", "tick_returned_false");
                EngineBridge.nativeShutdown();
                BootTrace.event("V2_SHUTDOWN_OK", "after_fatal_tick");
                net.minecraft.client.MinecraftClient.getInstance().scheduleStop();
                return;
            }
            // instance is null in V2 mode (writeNullFramebuffer is a no-op above),
            // so the endWrite() branch below is only reached in legacy fallback.
            if (!EngineBridge.nativeIsInWorld() && instance != null) {
                // Not in world — let OpenGL present menus/title screen. The vanilla GL
                // framebuffer endWrite() must be called during LevelLoadingScreen so
                // Minecraft's render state machine advances and the CompletableFuture
                // chain resolves. Without this, the loading screen never transitions.
                instance.endWrite();
                // Yield to let the Netty event loop deliver packets (single-player).
                // Without this the render thread starves the network thread and the
                // GameJoinS2CPacket never arrives, blocking the world transition.
                Thread.yield();
            }
        } else {
            ChunkProxy.waitImportantChunkRebuild();
            synchronized (TextureProxy.class) {
                RendererProxy.submitCommandAndPresent();
                RendererProxy.acquireContext();
            }
        }
    }

    @Redirect(method = "render(Z)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gl/Framebuffer;draw(II)V"))
    public void cancelFramebufferDraw(Framebuffer instance, int width, int height) {
        if (EngineBridge.nativeIsInitialized() && !EngineBridge.nativeIsInWorld() && instance != null) {
            instance.draw(width, height);
        }
    }

    @Redirect(method = "render(Z)V", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/RenderSystem;limitDisplayFPS(I)V"))
    public void disableFPSLimit(int fps) {

    }

    @Redirect(method = "render(Z)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gl/GlTimer;getInstance()Ljava/util/Optional;"))
    public Optional<GlTimer> disableGLTimerInstance() {
        return Optional.empty();
    }
    // endregion

    // region <onResolutionChanged>
    @Redirect(method = "onResolutionChanged()V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gl/Framebuffer;resize(II)V"))
    public void cancelFramebufferResize(Framebuffer instance, int width, int height) {
        if (EngineBridge.nativeIsInitialized()) {
            EngineBridge.nativePostResize(window.getFramebufferWidth(), window.getFramebufferHeight());
        }
    }
    // endregion

    // region <close>
    @Redirect(method = "close()V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gl/ShaderLoader;close()V"))
    public void cancelShaderLoaderClose(ShaderLoader instance) {
        Options.overwriteConfig();
    }
    //endregion

    // region <close>
    @Inject(method = "close()V", at = @At(value = "HEAD"))
    public void closeNativeRenderer(CallbackInfo ci) {
        CloudTileManager.shutdown();
        if (EngineBridge.nativeIsInitialized()) {
            BootTrace.event("V2_SHUTDOWN_BEGIN");
            EngineBridge.nativeShutdown();
            BootTrace.event("V2_SHUTDOWN_OK");
        } else {
            RendererProxy.close();
        }
    }
    // endregion

    // region <disconnect>
    @Redirect(method = "disconnect(Lnet/minecraft/client/gui/screen/Screen;Z)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/MinecraftClient;render(Z)V"))
    public void cancelRenderAfterStop(MinecraftClient instance, boolean tick) {

    }

    @Inject(method = "disconnect(Lnet/minecraft/client/gui/screen/Screen;Z)V",
        at = @At(value = "HEAD"))
    public void resetBuiltChunkNum(Screen disconnectionScreen, boolean transferring,
        CallbackInfo ci) {
        ChunkProxy.builtChunkNum = 0;
    }
    // endregion

}
