package com.skyeshade.skyent.client.renderer.blockentity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.skyeshade.skyent.SkyesNuclearTech;
import com.skyeshade.skyent.client.render.HeatingChamberLighting;
import com.skyeshade.skyent.content.block.HeatingChamberBlock;
import com.skyeshade.skyent.content.blockentity.HeatingChamberBlockEntity;
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

public class HeatingChamberRenderer implements BlockEntityRenderer<HeatingChamberBlockEntity> {
    public static final ModelResourceLocation CHAMBER_MODEL = ModelResourceLocation.standalone(
            ResourceLocation.fromNamespaceAndPath(SkyesNuclearTech.MOD_ID, "block/heating_chamber_chamber")
    );

    private static final float CHAMBER_SCALE = 2.0F;

    public HeatingChamberRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(HeatingChamberBlockEntity chamber, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        if (chamber.getLevel() == null) {
            return;
        }

        BlockState state = chamber.getBlockState();
        Direction facing = state.hasProperty(HeatingChamberBlock.FACING) ? state.getValue(HeatingChamberBlock.FACING) : Direction.NORTH;

        poseStack.pushPose();
        translateForFacing(facing, poseStack);
        poseStack.scale(CHAMBER_SCALE, CHAMBER_SCALE, CHAMBER_SCALE);
        rotateForFacing(facing, poseStack);

        float offset = chamber.getChamberTravelBlocks(partialTick);
        // The model is rendered at 2x scale, so divide the local translation to
        // keep the final visible travel at exactly 10 Minecraft pixels.
        poseStack.translate(0.0D, -offset / CHAMBER_SCALE, 0.0D);
        int sharedLight = HeatingChamberLighting.reducePackedLight(
                HeatingChamberLighting.computeControllerPackedLight(chamber.getLevel(), chamber.getBlockPos()),
                HeatingChamberLighting.SHARED_LIGHT_REDUCTION
        );
        renderModel(CHAMBER_MODEL, state, poseStack, bufferSource, sharedLight, renderBrightnessScale());
        poseStack.popPose();
    }

    private static void translateForFacing(Direction facing, PoseStack poseStack) {
        switch (facing) {
            case EAST -> poseStack.translate(-1.0D, 0.0D, 0.0D);
            case SOUTH -> poseStack.translate(-1.0D, 0.0D, -1.0D);
            case WEST -> poseStack.translate(0.0D, 0.0D, -1.0D);
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

    private static float renderBrightnessScale() {
        float multiplier = Math.clamp(HeatingChamberLighting.RENDER_BRIGHTNESS_MULTIPLIER, 0.0F, 1.0F);
        float floor = Math.clamp(HeatingChamberLighting.RENDER_BRIGHTNESS_FLOOR, 0.0F, 1.0F);
        return Math.max(multiplier, floor);
    }

    private static void renderModel(ModelResourceLocation modelLocation, BlockState state, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, float brightnessScale) {
        Minecraft minecraft = Minecraft.getInstance();
        BakedModel model = minecraft.getModelManager().getModel(modelLocation);
        ModelBlockRenderer modelRenderer = minecraft.getBlockRenderer().getModelRenderer();
        VertexConsumer consumer = bufferSource.getBuffer(RenderType.cutout());
        modelRenderer.renderModel(poseStack.last(), consumer, state, model, brightnessScale, brightnessScale, brightnessScale, packedLight, OverlayTexture.NO_OVERLAY);
    }
}
