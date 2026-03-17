package com.radiance.client.input;

import net.minecraft.client.render.Camera;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

/**
 * Manages the free-flying virtual camera state for offline rendering mode.
 * When active, the camera detaches from the player and can fly freely in 3D space.
 */
public class FreecamState {

    public double x, y, z;
    public float yaw, pitch;

    /** Initialize freecam at the current camera position and rotation. */
    public void initFromCamera(Camera camera) {
        Vec3d pos = camera.getPos();
        x = pos.x;
        y = pos.y;
        z = pos.z;
        yaw = camera.getYaw();
        pitch = camera.getPitch();
    }

    /**
     * Update position based on movement input.
     * @param forward WASD forward/back (-1 to 1)
     * @param strafe WASD left/right (-1 to 1)
     * @param up vertical (space=1, shift=-1)
     * @param speed speed multiplier
     * @param slowDown true if slow modifier (Ctrl) is held
     */
    public void tick(float forward, float strafe, float up, float speed, boolean slowDown) {
        float effectiveSpeed = speed * 0.5f; // base speed in blocks/tick
        if (slowDown) effectiveSpeed *= 0.2f;

        // Compute forward direction from yaw (horizontal plane only)
        double yawRad = Math.toRadians(yaw);
        double sinYaw = Math.sin(yawRad);
        double cosYaw = Math.cos(yawRad);

        // Forward = (-sin(yaw), cos(yaw)), Left = (cos(yaw), sin(yaw)) in Minecraft XZ coords
        double dx = (-sinYaw * forward + cosYaw * strafe) * effectiveSpeed;
        double dz = (cosYaw * forward + sinYaw * strafe) * effectiveSpeed;
        double dy = up * effectiveSpeed;

        x += dx;
        y += dy;
        z += dz;
    }

    /** Apply mouse delta to rotation. */
    public void applyMouseDelta(double deltaX, double deltaY, double sensitivity) {
        yaw += (float) (deltaX * sensitivity);
        pitch += (float) (deltaY * sensitivity);
        // Clamp pitch to ±90
        if (pitch > 90.0f) pitch = 90.0f;
        if (pitch < -90.0f) pitch = -90.0f;
        // Normalize yaw
        yaw = yaw % 360.0f;
    }
}
