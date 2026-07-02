package com.skyeshade.skyent.client.item;

import com.mojang.blaze3d.vertex.PoseStack;
import com.skyeshade.skyent.content.item.SteelTongsItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.IItemDecorator;

public final class SteelTongsHeldItemDecorator implements IItemDecorator {
    private static final float HELD_ITEM_GUI_X = 9.5F;
    private static final float HELD_ITEM_GUI_Y = 0.5F;
    private static final float HELD_ITEM_GUI_SCALE = 0.38F;
    private static final float HELD_COUNT_GUI_X = 16.0F;
    private static final float HELD_COUNT_GUI_Y = 6.0F;
    private static final float HELD_COUNT_GUI_SCALE = 0.5F;

    @Override
    public boolean render(GuiGraphics guiGraphics, Font font, ItemStack stack, int xOffset, int yOffset) {
        if (Minecraft.getInstance().level == null) {
            return false;
        }

        ItemStack held = SteelTongsItem.getHeldStack(stack, Minecraft.getInstance().level.registryAccess());
        if (held.isEmpty()) {
            return false;
        }

        PoseStack poseStack = guiGraphics.pose();
        poseStack.pushPose();
        poseStack.translate(xOffset + HELD_ITEM_GUI_X, yOffset + HELD_ITEM_GUI_Y, 200.0F);
        poseStack.scale(HELD_ITEM_GUI_SCALE, HELD_ITEM_GUI_SCALE, HELD_ITEM_GUI_SCALE);
        guiGraphics.renderItem(held, 0, 0);
        poseStack.popPose();

        if (held.getCount() > 1) {
            String count = Integer.toString(held.getCount());
            poseStack.pushPose();
            poseStack.translate(xOffset + HELD_COUNT_GUI_X, yOffset + HELD_COUNT_GUI_Y, 300.0F);
            poseStack.scale(HELD_COUNT_GUI_SCALE, HELD_COUNT_GUI_SCALE, HELD_COUNT_GUI_SCALE);
            guiGraphics.drawString(font, count, -font.width(count), 0, 0xFFFFFF, true);
            poseStack.popPose();
        }
        return false;
    }
}
