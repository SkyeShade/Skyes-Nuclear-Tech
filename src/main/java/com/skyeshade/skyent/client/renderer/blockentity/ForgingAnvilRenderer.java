package com.skyeshade.skyent.client.renderer.blockentity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.skyeshade.skyent.content.block.ForgingAnvilBlock;
import com.skyeshade.skyent.content.blockentity.ForgingAnvilBlockEntity;
import com.skyeshade.skyent.registry.ModItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

public class ForgingAnvilRenderer implements BlockEntityRenderer<ForgingAnvilBlockEntity> {
    private static final float WORKPIECE_Y = 1.03F;
    private static final float WORKPIECE_SCALE = 0.72F;
    private static final float WORKPIECE_LOCAL_X = 0.00F;
    private static final float WORKPIECE_LOCAL_Z = 0.09F;
    private static final float WORKPIECE_LOCAL_YAW = 18.0F;

    public ForgingAnvilRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(ForgingAnvilBlockEntity anvil, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        ItemStack stack = getRenderStack(anvil);
        if (stack.isEmpty()) {
            return;
        }

        Direction facing = getFacing(anvil.getBlockState());

        poseStack.pushPose();
        // Workpiece is rendered in anvil-local space.
        // Rotate around block center so the item follows the block FACING.
        poseStack.translate(0.5F, WORKPIECE_Y, 0.5F);
        poseStack.mulPose(Axis.YP.rotationDegrees(90.0F));
        poseStack.mulPose(Axis.YP.rotationDegrees(rotationDegreesForFacing(facing)));
        poseStack.translate(WORKPIECE_LOCAL_X, 0.0F, WORKPIECE_LOCAL_Z);
        poseStack.mulPose(Axis.YP.rotationDegrees(WORKPIECE_LOCAL_YAW));
        poseStack.scale(WORKPIECE_SCALE, WORKPIECE_SCALE, WORKPIECE_SCALE);
        poseStack.mulPose(Axis.XP.rotationDegrees(-90.0F));
        Minecraft.getInstance().getItemRenderer().renderStatic(
                stack,
                ItemDisplayContext.GROUND,
                packedLight,
                packedOverlay,
                poseStack,
                bufferSource,
                anvil.getLevel(),
                0
        );
        poseStack.popPose();
    }

    private static ItemStack getRenderStack(ForgingAnvilBlockEntity anvil) {
        if (!anvil.hasInput()) {
            return ItemStack.EMPTY;
        }
        if (anvil.isFinished()) {
            return anvil.getInput();
        }
        return switch (anvil.getStrikes()) {
            case 1 -> new ItemStack(ModItems.HOT_PLATE_FORGING_STAGE_1.get());
            case 2 -> new ItemStack(ModItems.HOT_PLATE_FORGING_STAGE_2.get());
            default -> anvil.getInput();
        };
    }

    private static Direction getFacing(BlockState state) {
        return state.hasProperty(ForgingAnvilBlock.FACING) ? state.getValue(ForgingAnvilBlock.FACING) : Direction.NORTH;
    }

    private static float rotationDegreesForFacing(Direction facing) {
        return switch (facing) {
            case SOUTH -> 180.0F;
            case WEST -> 90.0F;
            case EAST -> -90.0F;
            default -> 0.0F;
        };
    }
}
