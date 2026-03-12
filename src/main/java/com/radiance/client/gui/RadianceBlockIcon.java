package com.radiance.client.gui;

import net.minecraft.block.Block;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;

/**
 * Utility for rendering Minecraft block icons in Radiance GUI screens.
 * Handles scaling for arbitrary icon sizes via matrix transformations.
 */
public final class RadianceBlockIcon {

    private RadianceBlockIcon() {}

    /**
     * Draw a block icon at the given position and size.
     *
     * @param context the draw context
     * @param block   the block whose item form will be rendered
     * @param x       left edge in screen pixels
     * @param y       top edge in screen pixels
     * @param size    desired icon size in pixels (standard is 16)
     */
    public static void drawBlockIcon(DrawContext context, Block block, int x, int y, int size) {
        drawBlockIcon(context, new ItemStack(block.asItem()), x, y, size);
    }

    /**
     * Draw an item-stack icon at the given position and size.
     *
     * @param context the draw context
     * @param stack   the item stack to render
     * @param x       left edge in screen pixels
     * @param y       top edge in screen pixels
     * @param size    desired icon size in pixels (standard is 16)
     */
    public static void drawBlockIcon(DrawContext context, ItemStack stack, int x, int y, int size) {
        if (size == 16) {
            context.drawItem(stack, x, y);
            return;
        }

        float scale = size / 16f;
        context.getMatrices().push();
        context.getMatrices().translate(x, y, 0);
        context.getMatrices().scale(scale, scale, 1f);
        context.drawItem(stack, 0, 0);
        context.getMatrices().pop();
    }
}
