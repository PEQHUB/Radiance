package com.radiance.client.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

public final class InputEventQueue {

    private static final ConcurrentLinkedQueue<InputEvent> queue = new ConcurrentLinkedQueue<>();
    private static volatile boolean active = false;

    public static void activate() { active = true; }
    public static void deactivate() { active = false; queue.clear(); }
    public static boolean isActive() { return active; }

    public static void enqueue(InputEvent event) {
        if (active) queue.offer(event);
    }

    public static List<InputEvent> drainAll() {
        List<InputEvent> events = new ArrayList<>();
        InputEvent e;
        while ((e = queue.poll()) != null) {
            events.add(e);
        }
        return events;
    }

    public static int size() { return queue.size(); }
}
