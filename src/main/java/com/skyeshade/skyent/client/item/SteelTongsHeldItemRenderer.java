package com.skyeshade.skyent.client.item;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.skyeshade.skyent.content.item.SteelTongsItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

public final class SteelTongsHeldItemRenderer {
    private static final float HELD_ITEM_GUI_X = 0.32F;
    private static final float HELD_ITEM_GUI_Y = -0.28F;
    private static final float HELD_ITEM_GUI_SCALE = 0.42F;
    private static final float HELD_ITEM_FIRST_PERSON_X = 0.07F;
    private static final float HELD_ITEM_FIRST_PERSON_Y = 0.48F;
    private static final float HELD_ITEM_FIRST_PERSON_Z = 0.18F;
    private static final float HELD_ITEM_FIRST_PERSON_SCALE = 0.26F;
    private static final float HELD_ITEM_FIRST_PERSON_ROT_X = 10.0F;
    private static final float HELD_ITEM_FIRST_PERSON_ROT_Y = 00.0F;
    private static final float HELD_ITEM_FIRST_PERSON_ROT_Z = 00.0F;
    private static final float HELD_ITEM_THIRD_PERSON_X = -0.00F;
    private static final float HELD_ITEM_THIRD_PERSON_Y = 0.58F;
    private static final float HELD_ITEM_THIRD_PERSON_Z = -0.03F;
    private static final float HELD_ITEM_THIRD_PERSON_SCALE = 0.26F;
    private static final float HELD_ITEM_THIRD_PERSON_ROT_X = -10.0F;
    private static final float HELD_ITEM_THIRD_PERSON_ROT_Y = 0.0F;
    private static final float HELD_ITEM_THIRD_PERSON_ROT_Z = 00.0F;

    private SteelTongsHeldItemRenderer() {
    }

    public static void renderHeldItem(LivingEntity entity, ItemStack tongs, ItemDisplayContext displayContext, boolean leftHand, PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        ItemStack held = SteelTongsItem.getHeldStack(tongs, entity.registryAccess());
        if (held.isEmpty()) {
            return;
        }
        if (!shouldRenderHeldItem(displayContext)) {
            return;
        }

        poseStack.pushPose();
        if (displayContext == ItemDisplayContext.GUI) {
            applyGuiTransform(poseStack);
        } else if (isFirstPerson(displayContext)) {
            applyFirstPersonTransform(poseStack, isLeftHand(displayContext, leftHand));
        } else if (isThirdPerson(displayContext)) {
            applyThirdPersonTransform(poseStack, isLeftHand(displayContext, leftHand));
        }

        Minecraft.getInstance().getItemRenderer().renderStatic(
                held,
                displayContext == ItemDisplayContext.GUI ? ItemDisplayContext.GUI : ItemDisplayContext.FIXED,
                packedLight,
                OverlayTexture.NO_OVERLAY,
                poseStack,
                buffer,
                entity.level(),
                0
        );
        poseStack.popPose();
    }

    private static void applyGuiTransform(PoseStack poseStack) {
        poseStack.translate(HELD_ITEM_GUI_X, HELD_ITEM_GUI_Y, 0.0F);
        poseStack.scale(HELD_ITEM_GUI_SCALE, HELD_ITEM_GUI_SCALE, HELD_ITEM_GUI_SCALE);
    }

    private static void applyFirstPersonTransform(PoseStack poseStack, boolean left) {
        float handSign = left ? -1.0F : 1.0F;
        poseStack.translate(HELD_ITEM_FIRST_PERSON_X * handSign, HELD_ITEM_FIRST_PERSON_Y, HELD_ITEM_FIRST_PERSON_Z);
        poseStack.mulPose(Axis.XP.rotationDegrees(HELD_ITEM_FIRST_PERSON_ROT_X));
        poseStack.mulPose(Axis.YP.rotationDegrees(HELD_ITEM_FIRST_PERSON_ROT_Y * handSign));
        poseStack.mulPose(Axis.ZP.rotationDegrees(HELD_ITEM_FIRST_PERSON_ROT_Z * handSign));
        poseStack.scale(HELD_ITEM_FIRST_PERSON_SCALE, HELD_ITEM_FIRST_PERSON_SCALE, HELD_ITEM_FIRST_PERSON_SCALE);
    }

    private static void applyThirdPersonTransform(PoseStack poseStack, boolean left) {
        float handSign = left ? -1.0F : 1.0F;
        poseStack.translate(HELD_ITEM_THIRD_PERSON_X * handSign, HELD_ITEM_THIRD_PERSON_Y, HELD_ITEM_THIRD_PERSON_Z);
        poseStack.mulPose(Axis.XP.rotationDegrees(HELD_ITEM_THIRD_PERSON_ROT_X));
        poseStack.mulPose(Axis.YP.rotationDegrees(HELD_ITEM_THIRD_PERSON_ROT_Y * handSign));
        poseStack.mulPose(Axis.ZP.rotationDegrees(HELD_ITEM_THIRD_PERSON_ROT_Z * handSign));
        poseStack.scale(HELD_ITEM_THIRD_PERSON_SCALE, HELD_ITEM_THIRD_PERSON_SCALE, HELD_ITEM_THIRD_PERSON_SCALE);
    }

    private static boolean shouldRenderHeldItem(ItemDisplayContext displayContext) {
        return displayContext == ItemDisplayContext.GUI || isFirstPerson(displayContext) || isThirdPerson(displayContext);
    }

    private static boolean isFirstPerson(ItemDisplayContext displayContext) {
        return displayContext == ItemDisplayContext.FIRST_PERSON_LEFT_HAND
                || displayContext == ItemDisplayContext.FIRST_PERSON_RIGHT_HAND;
    }

    private static boolean isThirdPerson(ItemDisplayContext displayContext) {
        return displayContext == ItemDisplayContext.THIRD_PERSON_LEFT_HAND
                || displayContext == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND;
    }

    private static boolean isLeftHand(ItemDisplayContext displayContext, boolean fallbackLeftHand) {
        return switch (displayContext) {
            case FIRST_PERSON_LEFT_HAND, THIRD_PERSON_LEFT_HAND -> true;
            case FIRST_PERSON_RIGHT_HAND, THIRD_PERSON_RIGHT_HAND -> false;
            default -> fallbackLeftHand;
        };
    }
}
