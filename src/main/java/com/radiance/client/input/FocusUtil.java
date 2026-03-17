package com.radiance.client.input;

import com.radiance.client.option.Options;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

/**
 * Focus distance raycast utility for AF-S and AF-C modes.
 * Works from arbitrary position (freecam-aware), 256-block range.
 */
public class FocusUtil {

    public static final double MAX_DIST = 256.0;

    /**
     * Raycast from the given camera position/orientation up to maxDist blocks.
     * @return hit distance in blocks, or -1 on miss (sky/void)
     */
    public static double raycastFromCamera(ClientWorld world, float yaw, float pitch,
                                           double x, double y, double z, double maxDist) {
        if (world == null) return -1;

        // Need a non-null entity for RaycastContext (MC requires it)
        var client = net.minecraft.client.MinecraftClient.getInstance();
        Entity entity = client.player;
        if (entity == null) return -1;

        // Build direction vector from yaw/pitch (Minecraft convention)
        double yawRad = Math.toRadians(yaw);
        double pitchRad = Math.toRadians(pitch);
        double cosPitch = Math.cos(pitchRad);
        double dirX = -Math.sin(yawRad) * cosPitch;
        double dirY = -Math.sin(pitchRad);
        double dirZ = Math.cos(yawRad) * cosPitch;

        Vec3d start = new Vec3d(x, y, z);
        Vec3d end = new Vec3d(x + dirX * maxDist, y + dirY * maxDist, z + dirZ * maxDist);

        HitResult result = world.raycast(new RaycastContext(
            start, end,
            RaycastContext.ShapeType.OUTLINE,
            RaycastContext.FluidHandling.NONE,
            entity
        ));

        if (result == null || result.getType() == HitResult.Type.MISS) {
            return -1;
        }

        return start.distanceTo(result.getPos());
    }

    /**
     * Raycast from the current camera (freecam or player) center.
     * Convenience method that reads position from Options.freecam or the provided camera coords.
     */
    public static double raycastFromCurrentCamera(ClientWorld world) {
        if (world == null) return -1;

        double x, y, z;
        float yaw, pitch;

        if (Options.freecamEnabled && Options.offlineState != 0) {
            x = Options.freecam.x;
            y = Options.freecam.y;
            z = Options.freecam.z;
            yaw = Options.freecam.yaw;
            pitch = Options.freecam.pitch;
        } else {
            var client = net.minecraft.client.MinecraftClient.getInstance();
            if (client.gameRenderer == null) return -1;
            var camera = client.gameRenderer.getCamera();
            if (camera == null) return -1;
            var pos = camera.getPos();
            if (pos == null) return -1;
            x = pos.x;
            y = pos.y;
            z = pos.z;
            yaw = camera.getYaw();
            pitch = camera.getPitch();
        }

        return raycastFromCamera(world, yaw, pitch, x, y, z, MAX_DIST);
    }
}
