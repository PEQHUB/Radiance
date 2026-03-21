package com.radiance.client.ui;

import com.radiance.client.proxy.vulkan.UIThreadProxy;

/**
 * Dedicated UI thread that renders overlay at display rate (120 FPS).
 * Active when frame generation is enabled. Paced by DXGI waitForDisplayReady.
 */
public class UIThread {

    static volatile Thread uiThread;
    private static volatile boolean running;

    public static boolean isUIThread() {
        return Thread.currentThread() == uiThread;
    }

    public static boolean isRunning() {
        return running;
    }

    public static void start() {
        if (running) return;
        running = true;
        Thread t = new Thread(UIThread::loop, "RadSER-UIThread");
        t.setDaemon(true);
        uiThread = t;
        t.start();
        System.out.println("[UIThread] started");
    }

    public static void stop() {
        if (!running) return;
        running = false;
        Thread t = uiThread;
        uiThread = null;
        if (t != null) {
            try {
                t.join(2000);
            } catch (InterruptedException ignored) {}
        }
        System.out.println("[UIThread] stopped");
    }

    private static void loop() {
        // Create UIRenderContext on-demand (waits until FG + overlay are ready)
        System.out.println("[UIThread] waiting for UIRenderContext...");
        while (running && !UIThreadProxy.createUIRenderContext()) {
            try { Thread.sleep(100); } catch (InterruptedException ignored) {}
        }
        if (!running) return;
        System.out.println("[UIThread] UIRenderContext created, entering frame loop");

        // Phase A: solid color test — empty command buffer submit
        while (running) {
            if (!UIThreadProxy.isDecoupledUIActive()) {
                try { Thread.sleep(16); } catch (InterruptedException ignored) {}
                continue;
            }

            UIThreadProxy.beginUIFrame();
            UIThreadProxy.endUIFrame();
            UIThreadProxy.submitUIFrame(); // paces to display rate
        }

        UIThreadProxy.destroyUIRenderContext();
        System.out.println("[UIThread] UIRenderContext destroyed");
    }
}
