package com.skyeshade.skyent.client.renderer.blockentity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.skyeshade.skyent.content.block.CoalForgeBlock;
import com.skyeshade.skyent.content.blockentity.CoalForgeBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.ItemStackHandler;

public class CoalForgeRenderer implements BlockEntityRenderer<CoalForgeBlockEntity> {
    private static final float[][] INGOT_POSITIONS = {
            {0.34F, 0.26F},
            {0.66F, 0.26F},
            {0.34F, 0.62F},
            {0.66F, 0.62F}
    };
    private static final float INGOT_BASE_Y = 0.63F;
    private static final float INGOT_RENDER_Y_CORRECTION = -2.0F / 16.0F;
    private static final float INGOT_LAYER_Y_OFFSET = 2.0F / 16.0F;
    private static final float INGOT_SCALE = 0.65F;

    public CoalForgeRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(CoalForgeBlockEntity forge, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        ItemStackHandler ingots = forge.getIngots();
        BlockState state = forge.getBlockState();
        int layers = state.hasProperty(CoalForgeBlock.LAYERS) ? state.getValue(CoalForgeBlock.LAYERS) : 0;
        float y = INGOT_BASE_Y + INGOT_RENDER_Y_CORRECTION + layers * INGOT_LAYER_Y_OFFSET;
        Direction facing = state.hasProperty(CoalForgeBlock.FACING) ? state.getValue(CoalForgeBlock.FACING) : Direction.NORTH;

        poseStack.pushPose();
        // Slot positions are authored in forge-local north-facing space.
        // Rotate around the block center so the visible items follow the block FACING.
        poseStack.translate(0.5F, 0.0F, 0.5F);
        poseStack.mulPose(Axis.YP.rotationDegrees(itemLayoutRotation(facing)));
        poseStack.translate(-0.5F, 0.0F, -0.5F);

        for (int slot = 0; slot < Math.min(ingots.getSlots(), INGOT_POSITIONS.length); slot++) {
            renderSlot(forge, ingots, slot, y, poseStack, bufferSource, packedLight, packedOverlay);
        }
        poseStack.popPose();
    }

    private static void renderSlot(CoalForgeBlockEntity forge, ItemStackHandler ingots, int slot, float y, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        ItemStack stack = ingots.getStackInSlot(slot);
        if (stack.isEmpty()) {
            return;
        }

        poseStack.pushPose();
        poseStack.translate(INGOT_POSITIONS[slot][0], y, INGOT_POSITIONS[slot][1]);
        poseStack.mulPose(Axis.YP.rotationDegrees(slot % 2 == 0 ? -18.0F : 18.0F));
        poseStack.scale(INGOT_SCALE, INGOT_SCALE, INGOT_SCALE);
        poseStack.mulPose(Axis.XP.rotationDegrees(90.0F));
        // The forge item layout is already rotated by block facing.
        // This correction is intentionally local to the rendered item only;
        // do not move it above the slot translation or it will mirror slot offsets.
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F));
        Minecraft.getInstance().getItemRenderer().renderStatic(
                stack,
                ItemDisplayContext.GROUND,
                packedLight,
                packedOverlay,
                poseStack,
                bufferSource,
                forge.getLevel(),
                slot
        );
        poseStack.popPose();
    }

    private static float itemLayoutRotation(Direction facing) {
        return switch (facing) {
            case EAST -> 270.0F;
            case SOUTH -> 180.0F;
            case WEST -> 90.0F;
            default -> 0.0F;
        };
    }
}
