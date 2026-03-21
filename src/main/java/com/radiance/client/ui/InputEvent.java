package com.radiance.client.ui;

public sealed interface InputEvent {

    long nanoTime();

    record MouseMove(long nanoTime, double x, double y) implements InputEvent {}

    record MouseButton(long nanoTime, int button, int action, int mods) implements InputEvent {}

    record MouseScroll(long nanoTime, double horizontal, double vertical) implements InputEvent {}

    record KeyPress(long nanoTime, int key, int scancode, int action, int mods) implements InputEvent {}

    record CharTyped(long nanoTime, int codepoint, int mods) implements InputEvent {}
}
