package com.radiance.mixins.vulkan_render_integration;

import com.radiance.client.fpv.FirstPersonView;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.entity.state.BipedEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BipedEntityModel.class)
public abstract class BipedEntityModelMixin {
    @Shadow public ModelPart head;
    @Shadow public ModelPart body;
    @Shadow public ModelPart rightArm;
    @Shadow public ModelPart leftArm;
    @Shadow public ModelPart rightLeg;
    @Shadow public ModelPart leftLeg;

    @Inject(method = "setAngles(Lnet/minecraft/client/render/entity/state/BipedEntityRenderState;)V",
            at = @At("RETURN"))
    private void radiance$smoothCrouch(BipedEntityRenderState state, CallbackInfo ci) {
        if (!FirstPersonView.isActive()) return;
        if (!FirstPersonView.renderingBodyPass && !FirstPersonView.renderingHeadPass) return;

        // Use render state's sneaking pose flag for crouch detection
        float t = state.isInSneakingPose ? FirstPersonView.getCrouchProgress() : 0.0f;
        if (t <= 0.001f) return;

        // Body tilt forward (vanilla sneak is ~0.2 radians forward pitch)
        float bodyPitch = t * 0.2f;
        body.pitch = body.pitch + bodyPitch * t;

        // Head compensates for body tilt
        head.pitch = head.pitch - bodyPitch * 0.5f * t;

        // Legs shift forward to match crouched stance
        rightLeg.pivotZ = rightLeg.pivotZ + 4.0f * t;
        leftLeg.pivotZ = leftLeg.pivotZ + 4.0f * t;
        rightLeg.pivotY = rightLeg.pivotY - 1.0f * t;
        leftLeg.pivotY = leftLeg.pivotY - 1.0f * t;

        // Arms droop slightly
        rightArm.pitch = rightArm.pitch + 0.05f * t;
        leftArm.pitch = leftArm.pitch + 0.05f * t;
    }
}
