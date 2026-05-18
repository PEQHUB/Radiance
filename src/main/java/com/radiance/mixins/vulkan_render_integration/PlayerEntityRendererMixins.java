package com.radiance.mixins.vulkan_render_integration;

import com.radiance.client.fpv.FirstPersonView;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.render.entity.state.LivingEntityRenderState;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntityRenderer.class)
public abstract class PlayerEntityRendererMixins {

    /**
     * Before rendering the player model, set part visibility based on FPV pass.
     * Pass 1 (body): hide head+hat, show body/arms/legs
     * Pass 2 (head): show head+hat only, hide everything else
     */
    @Inject(method = "render(Lnet/minecraft/client/render/entity/state/LivingEntityRenderState;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
            at = @At("HEAD"))
    private void radiance$setFpvVisibility(LivingEntityRenderState state, MatrixStack matrices,
                                            VertexConsumerProvider vertexConsumers, int light,
                                            CallbackInfo ci) {
        if (!FirstPersonView.isActive()) return;
        if (!(state instanceof PlayerEntityRenderState)) return;

        @SuppressWarnings("unchecked")
        LivingEntityRenderer<?, ?, PlayerEntityModel> self =
            (LivingEntityRenderer<?, ?, PlayerEntityModel>) (Object) this;
        PlayerEntityModel model = self.getModel();

        if (FirstPersonView.renderingBodyPass) {
            model.head.visible = false;
            model.hat.visible = false;
            setBodyVisible(model, true);
        } else if (FirstPersonView.renderingHeadPass) {
            model.head.visible = true;
            model.hat.visible = true;
            setBodyVisible(model, false);
        }
    }

    // TODO: @Redirect for FeatureRenderer.render() needs 1.21.4 render pipeline investigation.
    // The held item isolation (fpvItemProvider redirect) is deferred — feature renderers
    // may use a different dispatch pattern in 1.21.4's shouldRenderFeatures() flow.

    private static void setBodyVisible(PlayerEntityModel model, boolean visible) {
        model.body.visible = visible;
        model.rightArm.visible = visible;
        model.leftArm.visible = visible;
        model.rightLeg.visible = visible;
        model.leftLeg.visible = visible;
        model.jacket.visible = visible;
        model.rightSleeve.visible = visible;
        model.leftSleeve.visible = visible;
        model.rightPants.visible = visible;
        model.leftPants.visible = visible;
    }
}
