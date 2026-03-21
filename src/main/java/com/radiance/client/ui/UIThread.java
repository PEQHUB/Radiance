package com.radiance.client.ui;

import com.mojang.blaze3d.systems.RenderSystem;
import com.radiance.client.proxy.vulkan.UIThreadProxy;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.client.render.VertexConsumerProvider;
import org.joml.Matrix4f;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Dedicated UI thread that renders overlay at display rate (120 FPS).
 * Active when frame generation is enabled. Paced by DXGI waitForDisplayReady.
 */
public class UIThread {

    static volatile Thread uiThread;
    private static volatile boolean running;

    // Screen handoff
    private static final Object CLOSE_SENTINEL = new Object();
    private static final AtomicReference<Object> pendingScreen = new AtomicReference<>(null);
    private static volatile Screen currentScreen;
    private static volatile double mouseX, mouseY;
    private static final boolean[] mouseButtonsDown = new boolean[8];

    // HUD renderer
    private static final DecoupledHudRenderer hudRenderer = new DecoupledHudRenderer();

    public static boolean isUIThread() {
        return Thread.currentThread() == uiThread;
    }

    public static boolean isRunning() {
        return running;
    }

    public static Screen getCurrentScreen() {
        return currentScreen;
    }

    public static void handoffScreen(Screen screen) {
        pendingScreen.set(screen);
    }

    public static void closeScreen() {
        pendingScreen.set(CLOSE_SENTINEL);
    }

    public static void start() {
        if (running) return;
        running = true;
        InputEventQueue.activate();
        Thread t = new Thread(UIThread::loop, "RadSER-UIThread");
        t.setDaemon(true);
        uiThread = t;
        t.start();
        System.out.println("[UIThread] started");
    }

    public static void stop() {
        if (!running) return;
        running = false;
        InputEventQueue.deactivate();
        Thread t = uiThread;
        uiThread = null;
        if (t != null) {
            try {
                t.join(2000);
            } catch (InterruptedException ignored) {}
        }
        currentScreen = null;
        System.out.println("[UIThread] stopped");
    }

    private static void loop() {
        MinecraftClient mc = MinecraftClient.getInstance();
        BufferAllocator allocator = new BufferAllocator(786432);
        try {
            VertexConsumerProvider.Immediate immediate = VertexConsumerProvider.immediate(allocator);

            // Outer loop: survives recreate cycles. UIThread stays alive, just
            // destroys/recreates the C++ UIRenderContext across swapchain recreate.
            while (running) {
                // Phase 1: Wait for UIRenderContext (created on-demand when FG + overlay ready)
                System.out.println("[UIThread] waiting for UIRenderContext...");
                while (running && !UIThreadProxy.createUIRenderContext()) {
                    try { Thread.sleep(100); } catch (InterruptedException ignored) {}
                }
                if (!running) break;
                System.out.println("[UIThread] UIRenderContext created, entering frame loop");

                // Phase 2: Frame loop — renders until stop is requested (recreate) or shutdown
                while (running) {
                    // Stop/pause checkpoint — must be FIRST. Returns false if render thread
                    // requested stop for swapchain recreate. UIThread exits frame loop,
                    // destroys context, then loops back to Phase 1 to create a fresh one.
                    if (!UIThreadProxy.checkPause()) {
                        System.out.println("[UIThread] stop requested — exiting frame loop");
                        break;
                    }

                    if (!UIThreadProxy.isDecoupledUIActive()) {
                        try { Thread.sleep(5); } catch (InterruptedException ignored) {}
                        continue;
                    }

                    try {
                        // Process screen handoff
                        Object pending = pendingScreen.getAndSet(null);
                        if (pending == CLOSE_SENTINEL) {
                            currentScreen = null;
                        } else if (pending instanceof Screen s) {
                            currentScreen = s;
                        }

                        // Process input events
                        processInput();

                        // Get game state
                        UIStateSnapshot snap = UIStateSnapshot.current();
                        int sw = snap.scaledWidth();
                        int sh = snap.scaledHeight();
                        if (sw <= 0 || sh <= 0) {
                            try { Thread.sleep(16); } catch (InterruptedException ignored) {}
                            continue;
                        }

                        UIThreadProxy.setUIClearColor(0f, 0f, 0f, 0f);
                        UIThreadProxy.beginUIFrame();

                        // Set up 2D orthographic projection (routes to thread-local via mixin)
                        Matrix4f ortho = new Matrix4f().ortho(0, sw, sh, 0, 1000f, 21000f);
                        RenderSystem.setProjectionMatrix(ortho, com.mojang.blaze3d.systems.ProjectionType.ORTHOGRAPHIC);
                        RenderSystem.getModelViewStack().identity();
                        RenderSystem.getModelViewStack().translate(0, 0, -11000f);
                        UIThreadProxy.setUIViewport(0, 0, snap.framebufferWidth(), snap.framebufferHeight());

                        DrawContext ctx = new DrawContext(mc, immediate);

                        Screen screen = currentScreen;  // snapshot
                        if (screen != null) {
                            screen.render(ctx, (int) mouseX, (int) mouseY, snap.tickDelta());
                        } else if (mc.player != null) {
                            hudRenderer.render(ctx, snap, mc.textRenderer);
                        }

                        // Flush all pending vertex batches
                        ctx.draw();

                        UIThreadProxy.endUIFrame();
                        UIThreadProxy.submitUIFrame();
                    } catch (OutOfMemoryError e) {
                        System.err.println("[UIThread] OOM, stopping: " + e.getMessage());
                        running = false;
                        break;
                    } catch (Exception e) {
                        System.err.println("[UIThread] frame error (retrying): " + e.getMessage());
                        try { Thread.sleep(16); } catch (InterruptedException ignored) {}
                    }
                }

                // Context cleanup — render thread waits for loopActive==false then destroys,
                // but we also call destroy as a safety net (no-op if already destroyed).
                UIThreadProxy.destroyUIRenderContext();
                System.out.println("[UIThread] UIRenderContext destroyed, will retry if running");
            }
        } finally {
            allocator.close();
            UIThreadProxy.destroyUIRenderContext();  // final cleanup on shutdown
        }
    }

    private static void processInput() {
        List<InputEvent> events = InputEventQueue.drainAll();
        Screen screen = currentScreen;  // snapshot to prevent TOCTOU NPE
        UIStateSnapshot snap = UIStateSnapshot.current();
        double scale = snap.scaleFactor();
        if (scale <= 0) scale = 1.0;

        for (InputEvent e : events) {
            switch (e) {
                case InputEvent.MouseMove(long t, double x, double y) -> {
                    double prevX = mouseX, prevY = mouseY;
                    mouseX = x / scale;
                    mouseY = y / scale;
                    if (screen != null) {
                        screen.mouseMoved(mouseX, mouseY);
                        for (int btn = 0; btn < mouseButtonsDown.length; btn++) {
                            if (mouseButtonsDown[btn]) {
                                screen.mouseDragged(mouseX, mouseY, btn,
                                    mouseX - prevX, mouseY - prevY);
                            }
                        }
                    }
                }
                case InputEvent.MouseButton(long t, int button, int action, int mods) -> {
                    if (screen != null) {
                        if (action == 1) { // GLFW_PRESS
                            if (button >= 0 && button < mouseButtonsDown.length)
                                mouseButtonsDown[button] = true;
                            screen.mouseClicked(mouseX, mouseY, button);
                        } else if (action == 0) { // GLFW_RELEASE
                            if (button >= 0 && button < mouseButtonsDown.length)
                                mouseButtonsDown[button] = false;
                            screen.mouseReleased(mouseX, mouseY, button);
                        }
                    }
                }
                case InputEvent.MouseScroll(long t, double h, double v) -> {
                    if (screen != null) {
                        screen.mouseScrolled(mouseX, mouseY, h, v);
                    }
                }
                case InputEvent.KeyPress(long t, int key, int scancode, int action, int mods) -> {
                    if (screen != null) {
                        if (action == 1 || action == 2) { // PRESS or REPEAT
                            screen.keyPressed(key, scancode, mods);
                        } else if (action == 0) { // RELEASE
                            screen.keyReleased(key, scancode, mods);
                        }
                    }
                }
                case InputEvent.CharTyped(long t, int codepoint, int mods) -> {
                    if (screen != null) {
                        screen.charTyped((char) codepoint, mods);
                    }
                }
            }
        }
    }
}
