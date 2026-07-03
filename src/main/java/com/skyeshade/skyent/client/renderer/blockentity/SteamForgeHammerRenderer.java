package com.skyeshade.skyent.client.renderer.blockentity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.skyeshade.skyent.SkyesNuclearTech;
import com.skyeshade.skyent.content.block.SteamForgeHammerBlock;
import com.skyeshade.skyent.content.blockentity.SteamForgeHammerBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.client.renderer.texture.OverlayTexture;

public class SteamForgeHammerRenderer implements BlockEntityRenderer<SteamForgeHammerBlockEntity> {
    public static final ModelResourceLocation PISTON_MODEL = ModelResourceLocation.standalone(
            ResourceLocation.fromNamespaceAndPath(SkyesNuclearTech.MOD_ID, "block/steam_forge_hammer_piston")
    );
    private static final float PISTON_SCALE = 2.0F;
    private static final float PISTON_OFFSET_X = 0.0F;
    private static final float PISTON_OFFSET_Y = 0.0F;
    private static final float PISTON_OFFSET_Z = 0.0F;
    private static final float WORK_ITEM_X = 0.5F;
    private static final float WORK_ITEM_Y = 1.01F;
    private static final float WORK_ITEM_Z = 0.25F;
    private static final float WORK_ITEM_SCALE = 1.00F;
    private static final float WORK_ITEM_ROT_X = -90.0F;
    private static final float WORK_ITEM_ROT_Y = 0.0F;
    private static final float WORK_ITEM_ROT_Z = 180.0F;

    public SteamForgeHammerRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(SteamForgeHammerBlockEntity hammer, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        BlockState state = hammer.getBlockState();
        Direction facing = state.hasProperty(SteamForgeHammerBlock.FACING) ? state.getValue(SteamForgeHammerBlock.FACING) : Direction.NORTH;

        poseStack.pushPose();
        // Body/base uses the normal block renderer via steam_forge_hammer_scaled.json.
        // Rotate the piston around the master block center so it follows block FACING.
        poseStack.translate(0.5D, 0.0D, 0.5D);
        poseStack.mulPose(Axis.YP.rotationDegrees(yRotationForFacing(facing)));
        poseStack.scale(PISTON_SCALE, PISTON_SCALE, PISTON_SCALE);
        poseStack.translate(-0.5D + PISTON_OFFSET_X, PISTON_OFFSET_Y, -0.5D + PISTON_OFFSET_Z);

        float pistonOffset = hammer.getPistonOffset(partialTick);
        poseStack.pushPose();
        // This translation happens after the 2x model scale, so divide to keep
        // the final visible travel at exactly one block.
        poseStack.translate(0.0D, -pistonOffset / PISTON_SCALE, 0.0D);
        renderModel(PISTON_MODEL, state, poseStack, bufferSource, packedLight);
        poseStack.popPose();

        poseStack.popPose();

        renderWorkItem(hammer, facing, poseStack, bufferSource, packedLight, packedOverlay);
    }

    private static void renderModel(ModelResourceLocation modelLocation, BlockState state, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {
        Minecraft minecraft = Minecraft.getInstance();
        BakedModel model = minecraft.getModelManager().getModel(modelLocation);
        ModelBlockRenderer modelRenderer = minecraft.getBlockRenderer().getModelRenderer();
        VertexConsumer consumer = bufferSource.getBuffer(RenderType.cutout());
        modelRenderer.renderModel(poseStack.last(), consumer, state, model, 1.0F, 1.0F, 1.0F, packedLight, OverlayTexture.NO_OVERLAY);
    }

    private static float yRotationForFacing(Direction facing) {
        return switch (facing) {
            case EAST -> 270.0F;
            case SOUTH -> 180.0F;
            case WEST -> 90.0F;
            default -> 0.0F;
        };
    }

    private static void renderWorkItem(SteamForgeHammerBlockEntity hammer, Direction facing, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        ItemStack workStack = hammer.shouldRenderPlateForgeStages()
                ? ForgeStageRenderUtil.getSteamHammerPlateForgeRenderStack(
                hammer.getWorkStack(),
                hammer.getStrikesDone(),
                hammer.isFinished()
        )
                : hammer.getWorkStack();
        if (workStack.isEmpty()) {
            return;
        }

        poseStack.pushPose();
        // Work item constants are block-local coordinates; rotate around the master center.
        poseStack.translate(0.5F, 0.0F, 0.5F);
        poseStack.mulPose(Axis.YP.rotationDegrees(yRotationForFacing(facing)));
        poseStack.translate(WORK_ITEM_X - 0.5F, WORK_ITEM_Y, WORK_ITEM_Z - 0.5F);
        poseStack.mulPose(Axis.XP.rotationDegrees(WORK_ITEM_ROT_X));
        poseStack.mulPose(Axis.YP.rotationDegrees(WORK_ITEM_ROT_Y));
        poseStack.mulPose(Axis.ZP.rotationDegrees(WORK_ITEM_ROT_Z));
        poseStack.scale(WORK_ITEM_SCALE, WORK_ITEM_SCALE, WORK_ITEM_SCALE);
        Minecraft.getInstance().getItemRenderer().renderStatic(
                workStack,
                ItemDisplayContext.GROUND,
                packedLight,
                packedOverlay,
                poseStack,
                bufferSource,
                hammer.getLevel(),
                0
        );
        poseStack.popPose();
    }
}
