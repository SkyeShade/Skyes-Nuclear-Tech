package com.skyeshade.skyent.client.renderer.blockentity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.skyeshade.skyent.content.block.CoalForgeBlock;
import com.skyeshade.skyent.content.blockentity.CoalForgeBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
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
        int layers = forge.getBlockState().hasProperty(CoalForgeBlock.LAYERS) ? forge.getBlockState().getValue(CoalForgeBlock.LAYERS) : 0;
        float y = INGOT_BASE_Y + INGOT_RENDER_Y_CORRECTION + layers * INGOT_LAYER_Y_OFFSET;

        for (int slot = 0; slot < Math.min(ingots.getSlots(), INGOT_POSITIONS.length); slot++) {
            ItemStack stack = ingots.getStackInSlot(slot);
            if (stack.isEmpty()) {
                continue;
            }

            poseStack.pushPose();
            poseStack.translate(INGOT_POSITIONS[slot][0], y, INGOT_POSITIONS[slot][1]);
            poseStack.mulPose(Axis.YP.rotationDegrees(slot % 2 == 0 ? -18.0F : 18.0F));
            poseStack.scale(INGOT_SCALE, INGOT_SCALE, INGOT_SCALE);
            poseStack.mulPose(Axis.XP.rotationDegrees(90.0F));
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
    }
}
