package com.radiance.client.fpv;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.MathHelper;
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
        if (!(entity instanceof PlayerEntity player)) return offset;

        // Use BODY yaw (not camera yaw) for offset projection.
        // The body rotation (R_y in setupTransforms) uses bodyYaw, so the head's
        // world position depends on bodyYaw. Camera yaw can differ significantly
        // during elytra banking, quick turns, etc.
        float bodyYawDeg = MathHelper.lerpAngleDegrees(tickDelta, player.prevBodyYaw, player.bodyYaw);
        float yawRad = (float) Math.toRadians(-bodyYawDeg);
        double fwdX = Math.sin(yawRad);
        double fwdZ = Math.cos(yawRad);
        double rightX = Math.cos(yawRad);
        double rightZ = -Math.sin(yawRad);

        // MC places the camera at entity.feet + (0, eyeHeight, 0).
        // MC's model transforms place the head at entity.feet + R(headModelPos).
        // These two disagree when the body is rotated (swimming/elytra/crawling).
        // Fix: offset entity so the model head lands on the camera position.
        //
        // offset = (0, eyeHeight, 0) - headRelative(angle)
        //
        // For upright (angle=0): headRelative = (0, 1.62, 0), offset = (0, eyeHeight-1.62, 0) ≈ 0
        // For prone: headRelative follows MC's rotation, offset compensates exactly.

        // Use interpolated entity Y to match camera.getPos() interpolation.
        // entity.getY() is tick-quantized (jumps at tick boundaries), while
        // camera.getPos().y is smoothly interpolated — the difference judders.
        double interpEntityY = MathHelper.lerp(tickDelta, entity.lastRenderY, entity.getY());
        float eyeHeight = (float) (camera.getPos().y - interpEntityY);

        // Compute MC's rotation angle and head position after transforms
        boolean isGliding = player.isGliding();
        float leaningPitch = player.getLeaningPitch(tickDelta);

        float headY, headZ;

        if (isGliding) {
            float gt = player.getGlidingTicks() + tickDelta;
            float gp = Math.min(gt * gt / 100.0f, 1.0f);
            float angleDeg = gp * (-90.0f - camera.getPitch());
            float a = (float) Math.toRadians(angleDeg);
            float cosA = (float) Math.cos(a);
            float sinA = (float) Math.sin(a);
            // Elytra: no swim translate. Head base (0, 1.62, 0) after Rx.
            headY = STAND_EYE * cosA;
            headZ = STAND_EYE * sinA;
        } else if (leaningPitch > 0f) {
            float angleDeg;
            boolean swimTranslate;
            if (player.isTouchingWater()) {
                angleDeg = leaningPitch * (-90.0f - camera.getPitch());
                swimTranslate = player.isSwimming();
            } else {
                angleDeg = leaningPitch * -90.0f;
                swimTranslate = false;
            }
            float a = (float) Math.toRadians(angleDeg);
            float cosA = (float) Math.cos(a);
            float sinA = (float) Math.sin(a);
            if (swimTranslate) {
                // Swimming: head base (0, 0.62, 0.3) after swim translate + Rx
                headY = 0.62f * cosA - 0.3f * sinA;
                headZ = 0.62f * sinA + 0.3f * cosA;
            } else {
                // Crawling: head base (0, 1.62, 0) after Rx
                headY = STAND_EYE * cosA;
                headZ = STAND_EYE * sinA;
            }
        } else {
            // Upright: head at (0, 1.62, 0), no rotation
            headY = STAND_EYE;
            headZ = 0f;
        }

        // Head-to-camera alignment offset
        float alignY = eyeHeight - headY;
        float alignFwd = headZ; // body-local -Z = forward; headZ negative = head forward

        offset[0] = fwdX * (alignFwd + offsetForward) + rightX * offsetLateral;
        offset[1] = alignY - offsetVertical;
        offset[2] = fwdZ * (alignFwd + offsetForward) + rightZ * offsetLateral;

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
