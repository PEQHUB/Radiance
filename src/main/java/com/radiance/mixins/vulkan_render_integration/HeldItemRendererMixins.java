package com.radiance.mixins.vulkan_render_integration;

import com.google.common.base.MoreObjects;
import com.radiance.client.util.MaterialBlock;
import com.radiance.client.vertex.PBRVertexConsumer;
import com.radiance.mixin_related.extensions.vulkan_render_integration.IHeldItemRendererExt;
import net.minecraft.block.Block;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(HeldItemRenderer.class)
public abstract class HeldItemRendererMixins implements IHeldItemRendererExt {

    @Shadow
    private ItemStack mainHand;

    @Shadow
    private ItemStack offHand;

    @Shadow
    private float equipProgressMainHand;

    @Shadow
    private float prevEquipProgressMainHand;

    @Shadow
    private float equipProgressOffHand;

    @Shadow
    private float prevEquipProgressOffHand;

    @Shadow
    protected abstract void renderFirstPersonItem(AbstractClientPlayerEntity player,
        float tickDelta,
        float pitch,
        Hand hand,
        float swingProgress,
        ItemStack item,
        float equipProgress,
        MatrixStack matrices,
        VertexConsumerProvider vertexConsumers,
        int light);

    private static void setItemMaterialType(ItemStack stack) {
        if (!stack.isEmpty() && stack.getItem() instanceof BlockItem blockItem) {
            MaterialBlock mb = MaterialBlock.fromBlock(blockItem.getBlock());
            if (mb != null) {
                PBRVertexConsumer.setItemMaterialBlockType(mb.ordinal());
                return;
            }
        }
        PBRVertexConsumer.clearItemMaterialBlockType();
    }

    @Override
    public void neoVoxelRT$renderItem(float tickDelta,
        MatrixStack matrices,
        VertexConsumerProvider vertexConsumers,
        ClientPlayerEntity player,
        int light) {
        float f = player.getHandSwingProgress(tickDelta);
        Hand hand = MoreObjects.firstNonNull(player.preferredHand, Hand.MAIN_HAND);
        float g = player.getLerpedPitch(tickDelta);
        HeldItemRenderer.HandRenderType handRenderType = HeldItemRenderer.getHandRenderType(player);
        float h = MathHelper.lerp(tickDelta, player.lastRenderPitch, player.renderPitch);
        float i = MathHelper.lerp(tickDelta, player.lastRenderYaw, player.renderYaw);
        matrices.multiply(
            RotationAxis.POSITIVE_X.rotationDegrees((player.getPitch(tickDelta) - h) * 0.1F));
        matrices.multiply(
            RotationAxis.POSITIVE_Y.rotationDegrees((player.getYaw(tickDelta) - i) * 0.1F));
        if (handRenderType.renderMainHand) {
            float j = hand == Hand.MAIN_HAND ? f : 0.0F;
            float k = 1.0F - MathHelper.lerp(tickDelta, this.prevEquipProgressMainHand,
                this.equipProgressMainHand);
            setItemMaterialType(this.mainHand);
            try {
                this.renderFirstPersonItem(player, tickDelta, g, Hand.MAIN_HAND, j, this.mainHand, k,
                    matrices, vertexConsumers, light);
            } finally {
                PBRVertexConsumer.clearItemMaterialBlockType();
            }
        }

        if (handRenderType.renderOffHand) {
            float j = hand == Hand.OFF_HAND ? f : 0.0F;
            float k = 1.0F - MathHelper.lerp(tickDelta, this.prevEquipProgressOffHand,
                this.equipProgressOffHand);
            setItemMaterialType(this.offHand);
            try {
                this.renderFirstPersonItem(player, tickDelta, g, Hand.OFF_HAND, j, this.offHand, k,
                    matrices, vertexConsumers, light);
            } finally {
                PBRVertexConsumer.clearItemMaterialBlockType();
            }
        }
    }
}
