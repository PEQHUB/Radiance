package com.radiance.client.input;

import net.minecraft.client.render.Camera;
import net.minecraft.util.math.Vec3d;

/**
 * Manages the free-flying virtual camera state for offline rendering mode.
 * When active, the camera detaches from the player and can fly freely in 3D space.
 * Smooth acceleration/deceleration for cinematic feel.
 */
public class FreecamState {

    public double x, y, z;
    public float yaw, pitch;

    // Velocity for smooth movement
    private double vx, vy, vz;
    private static final float ACCEL = 8.0f;   // acceleration factor (blocks/s²)
    private static final float DRAG = 6.0f;    // deceleration factor (1/s)
    private long lastTickNanos = 0;

    /** Initialize freecam at the current camera position and rotation. */
    public void initFromCamera(Camera camera) {
        Vec3d pos = camera.getPos();
        x = pos.x;
        y = pos.y;
        z = pos.z;
        yaw = camera.getYaw();
        pitch = camera.getPitch();
        vx = vy = vz = 0;
        lastTickNanos = System.nanoTime();
    }

    /**
     * Update position based on movement input.
     * Uses frame-rate-independent physics with smooth acceleration and drag.
     * @param forward WASD forward/back (-1 to 1)
     * @param strafe WASD left/right (-1 to 1)
     * @param up vertical (space=1, shift=-1)
     * @param speed speed multiplier (0.1 to 10.0)
     * @param slowDown true if slow modifier (Ctrl) is held
     */
    public void tick(float forward, float strafe, float up, float speed, boolean slowDown) {
        long now = System.nanoTime();
        float dt = (lastTickNanos == 0) ? 0.05f : Math.min((now - lastTickNanos) / 1e9f, 0.1f);
        lastTickNanos = now;

        float effectiveSpeed = speed;
        if (slowDown) effectiveSpeed *= 0.15f;

        // Compute movement direction from yaw
        double yawRad = Math.toRadians(yaw);
        double sinYaw = Math.sin(yawRad);
        double cosYaw = Math.cos(yawRad);

        // Target velocity from input
        double targetVx = (-sinYaw * forward + cosYaw * strafe) * effectiveSpeed;
        double targetVz = (cosYaw * forward + sinYaw * strafe) * effectiveSpeed;
        double targetVy = up * effectiveSpeed;

        // Smooth acceleration toward target, drag when no input
        float accel = ACCEL * dt;
        float drag = (float) Math.exp(-DRAG * dt);

        if (forward != 0 || strafe != 0) {
            vx += (targetVx - vx) * Math.min(accel, 1.0);
            vz += (targetVz - vz) * Math.min(accel, 1.0);
        } else {
            vx *= drag;
            vz *= drag;
        }

        if (up != 0) {
            vy += (targetVy - vy) * Math.min(accel, 1.0);
        } else {
            vy *= drag;
        }

        // Snap to zero when velocity is negligible
        if (Math.abs(vx) < 0.001) vx = 0;
        if (Math.abs(vy) < 0.001) vy = 0;
        if (Math.abs(vz) < 0.001) vz = 0;

        x += vx * dt;
        y += vy * dt;
        z += vz * dt;
    }

    /** Apply mouse delta to rotation. */
    public void applyMouseDelta(double deltaX, double deltaY, double sensitivity) {
        yaw += (float) (deltaX * sensitivity);
        pitch += (float) (deltaY * sensitivity);
        if (pitch > 90.0f) pitch = 90.0f;
        if (pitch < -90.0f) pitch = -90.0f;
        yaw = yaw % 360.0f;
    }
}
