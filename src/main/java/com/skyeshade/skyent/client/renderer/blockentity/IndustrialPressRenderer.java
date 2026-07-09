package com.skyeshade.skyent.client.renderer.blockentity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.skyeshade.skyent.SkyesNuclearTech;
import com.skyeshade.skyent.client.render.IndustrialPressLighting;
import com.skyeshade.skyent.content.block.IndustrialPressBlock;
import com.skyeshade.skyent.content.blockentity.IndustrialPressBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;

public class IndustrialPressRenderer implements BlockEntityRenderer<IndustrialPressBlockEntity> {
    public static final ModelResourceLocation PRESS_HEAD_MODEL = ModelResourceLocation.standalone(
            ResourceLocation.fromNamespaceAndPath(SkyesNuclearTech.MOD_ID, "block/industrial_press_press")
    );

    private static final float PRESS_SCALE = 2.0F;

    public IndustrialPressRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(IndustrialPressBlockEntity press, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        if (press.getLevel() == null) {
            return;
        }

        BlockState state = press.getBlockState();
        Direction facing = state.hasProperty(IndustrialPressBlock.FACING) ? state.getValue(IndustrialPressBlock.FACING) : Direction.NORTH;

        poseStack.pushPose();
        translateForFacing(facing, poseStack);
        poseStack.scale(PRESS_SCALE, PRESS_SCALE, PRESS_SCALE);
        rotateForFacing(facing, poseStack);
        poseStack.translate(0.0D, -press.getPressTravelBlocks(partialTick) / PRESS_SCALE, 0.0D);

        int sharedLight = IndustrialPressLighting.computeMaxPackedLight(press.getLevel(), press.getBlockPos(), facing);
        renderModel(PRESS_HEAD_MODEL, state, poseStack, bufferSource, sharedLight, 0.8F);
        poseStack.popPose();
    }

    private static void translateForFacing(Direction facing, PoseStack poseStack) {
        switch (facing) {
            case EAST -> poseStack.translate(-0.5D, 0.0D, 0.0D);
            case SOUTH -> poseStack.translate(-1.0D, 0.0D, -0.5D);
            case WEST -> poseStack.translate(-0.5D, 0.0D, -1.0D);
            case NORTH -> poseStack.translate(0.0D, 0.0D, -0.5D);
            default -> {
            }
        }
    }

    private static void rotateForFacing(Direction facing, PoseStack poseStack) {
        poseStack.translate(0.5D, 0.0D, 0.5D);
        poseStack.mulPose(Axis.YP.rotationDegrees(yRotationForFacing(facing)));
        poseStack.translate(-0.5D, 0.0D, -0.5D);
    }

    private static float yRotationForFacing(Direction facing) {
        return switch (facing) {
            case EAST -> 270.0F;
            case SOUTH -> 180.0F;
            case WEST -> 90.0F;
            default -> 0.0F;
        };
    }

    private static void renderModel(ModelResourceLocation modelLocation, BlockState state, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, float brightnessScale) {
        Minecraft minecraft = Minecraft.getInstance();
        BakedModel model = minecraft.getModelManager().getModel(modelLocation);
        ModelBlockRenderer modelRenderer = minecraft.getBlockRenderer().getModelRenderer();
        VertexConsumer consumer = bufferSource.getBuffer(RenderType.cutout());
        modelRenderer.renderModel(poseStack.last(), consumer, state, model, brightnessScale, brightnessScale, brightnessScale, packedLight, OverlayTexture.NO_OVERLAY);
    }
}
