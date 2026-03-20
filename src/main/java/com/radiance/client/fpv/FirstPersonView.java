package com.radiance.client.fpv;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import com.radiance.client.vertex.StorageVertexConsumerProvider;

public class FirstPersonView {
    // Static state set from Options.java
    public static boolean enabled = false;
    public static float offsetForward = 0.0f;  // meters
    public static float offsetVertical = 0.0f;  // meters
    public static float offsetLateral = 0.0f;   // meters

    // Per-frame render state (set by EntityProxy during FPV render passes)
    public static StorageVertexConsumerProvider fpvItemProvider = null;
    public static boolean renderingBodyPass = false;
    public static boolean renderingHeadPass = false;

    // Smooth crouch interpolation
    private static float crouchProgress = 0.0f;  // 0.0 (standing) to 1.0 (crouched)
    private static float lastCameraY = 0.0f;
    private static boolean initialized = false;

    // Standing eye height = 1.62, crouching = 1.27, difference = 0.35
    private static final float STAND_EYE = 1.62f;
    private static final float CROUCH_EYE = 1.27f;
    private static final float EYE_DIFF = STAND_EYE - CROUCH_EYE;

    public static boolean isActive() {
        if (!enabled) return false;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.player == null) return false;
        return mc.options.getPerspective() == Perspective.FIRST_PERSON;
    }

    public static void updateCrouchProgress(float tickDelta, Camera camera) {
        // Access camera's interpolated Y through the accessor mixin
        float cameraY;
        float prevCameraY;
        try {
            var accessor = (com.radiance.mixins.vulkan_render_integration.CameraAccessorMixin) camera;
            cameraY = accessor.radiance$getCameraY();
            prevCameraY = accessor.radiance$getLastCameraY();
        } catch (ClassCastException e) {
            return;
        }

        float interpY = prevCameraY + (cameraY - prevCameraY) * tickDelta;

        if (!initialized) {
            lastCameraY = interpY;
            initialized = true;
        }

        // Compute crouch progress from eye height delta
        // Lower eye = more crouched
        float normalizedY = (interpY - CROUCH_EYE) / EYE_DIFF;
        crouchProgress = 1.0f - Math.max(0.0f, Math.min(1.0f, normalizedY));

        lastCameraY = interpY;
    }

    public static float getCrouchProgress() {
        return crouchProgress;
    }

    public static double[] computeOffset(Entity entity, float tickDelta, Camera camera) {
        double[] offset = new double[3];
        if (!(entity instanceof PlayerEntity)) return offset;

        // Camera yaw in radians (Minecraft yaw: 0=south, 90=west, 180=north, 270=east)
        float yawRad = (float) Math.toRadians(-camera.getYaw());

        // Forward vector (horizontal plane)
        double forwardX = Math.sin(yawRad);
        double forwardZ = Math.cos(yawRad);

        // Right vector (horizontal plane, perpendicular to forward)
        double rightX = Math.cos(yawRad);
        double rightZ = -Math.sin(yawRad);

        // Apply offsets
        offset[0] = forwardX * offsetForward + rightX * offsetLateral;
        offset[1] = -offsetVertical; // positive offsetVertical = down in Options, but Y is up
        offset[2] = forwardZ * offsetForward + rightZ * offsetLateral;

        // Uncrouch vertical compensation: push model DOWN and slightly FORWARD
        // during uncrouch transitions to prevent head clipping through camera
        float eyeY;
        try {
            var accessor = (com.radiance.mixins.vulkan_render_integration.CameraAccessorMixin) camera;
            float cY = accessor.radiance$getCameraY();
            float pY = accessor.radiance$getLastCameraY();
            eyeY = pY + (cY - pY) * tickDelta;
        } catch (ClassCastException e) {
            eyeY = STAND_EYE;
        }

        float signedDelta = eyeY - lastCameraY;
        if (signedDelta < -0.02f) {
            // Uncrouching: camera rising, push model down and forward
            float compensation = Math.min(0.15f, Math.abs(signedDelta) * 0.5f);
            offset[1] -= compensation;
            offset[0] += forwardX * compensation * 0.3;
            offset[2] += forwardZ * compensation * 0.3;
        }

        return offset;
    }

    public static void reset() {
        crouchProgress = 0.0f;
        lastCameraY = 0.0f;
        initialized = false;
        renderingBodyPass = false;
        renderingHeadPass = false;
        fpvItemProvider = null;
    }
}
