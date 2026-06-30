package com.skyeshade.skyent.client.renderer.blockentity;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;
import com.skyeshade.skyent.content.block.GeigerCounterPlacedBlock;
import com.skyeshade.skyent.content.blockentity.GeigerCounterPlacedBlockEntity;
import com.skyeshade.skyent.content.item.GeigerNeedleUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Matrix4f;

public class GeigerCounterPlacedRenderer implements BlockEntityRenderer<GeigerCounterPlacedBlockEntity> {
    private static final RenderType NEEDLE_RENDER_TYPE = RenderType.create(
            "skyent_placed_geiger_counter_needle",
            DefaultVertexFormat.POSITION_COLOR_LIGHTMAP,
            VertexFormat.Mode.QUADS,
            256,
            RenderType.CompositeState.builder()
                    .setShaderState(RenderStateShard.RENDERTYPE_LEASH_SHADER)
                    .setTextureState(RenderStateShard.NO_TEXTURE)
                    .setCullState(RenderStateShard.NO_CULL)
                    .setLightmapState(RenderStateShard.LIGHTMAP)
                    .createCompositeState(false)
    );

    // Blockbench gauge face: X 6-10, Y 1.625, Z 3.125-6.125 in 16px block coordinates.
    private static final float NEEDLE_PIVOT_X = 8.0F / 16.0F;
    private static final float NEEDLE_PIVOT_Y = 1.725F / 16.0F + 0.003F;
    private static final float NEEDLE_PIVOT_Z = 5.1F / 16.0F;
    private static final float NEEDLE_LENGTH = 0.12F;
    private static final float NEEDLE_WIDTH = 0.010F;
    private static final float NEEDLE_ZERO_OFFSET_DEGREES = 0.0F;
    private static final float NEEDLE_ANGLE_MULTIPLIER = 1.0F;

    public GeigerCounterPlacedRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(GeigerCounterPlacedBlockEntity geiger, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        BlockState state = geiger.getBlockState();
        Direction attachedFace = state.hasProperty(GeigerCounterPlacedBlock.ATTACHED_FACE)
                ? state.getValue(GeigerCounterPlacedBlock.ATTACHED_FACE)
                : Direction.UP;
        Direction facing = state.hasProperty(GeigerCounterPlacedBlock.FACING)
                ? state.getValue(GeigerCounterPlacedBlock.FACING)
                : Direction.NORTH;
        Direction needleDirection = attachedFace == Direction.UP ? facing : attachedFace;

        Minecraft minecraft = Minecraft.getInstance();
        float ticks = minecraft.player == null ? 0.0F : minecraft.player.tickCount;
        float angle = placedNeedleRotation(
                attachedFace,
                needleDirection,
                GeigerNeedleUtil.valueToRenderedNeedleDegrees(geiger.getDisplayedNeedleValue(), ticks)
        );

        poseStack.pushPose();
        applyMountTransform(poseStack, attachedFace, facing);
        poseStack.translate(NEEDLE_PIVOT_X, NEEDLE_PIVOT_Y, NEEDLE_PIVOT_Z);
        applyWallNeedleUprightCorrection(poseStack, attachedFace);
        poseStack.mulPose(Axis.YP.rotationDegrees(angle));

        Matrix4f pose = poseStack.last().pose();
        VertexConsumer buffer = bufferSource.getBuffer(NEEDLE_RENDER_TYPE);
        float halfWidth = NEEDLE_WIDTH * 0.5F;

        addVertex(buffer, pose, -halfWidth, 0.0F, 0.0F, packedLight);
        addVertex(buffer, pose, halfWidth, 0.0F, 0.0F, packedLight);
        addVertex(buffer, pose, halfWidth, 0.0F, -NEEDLE_LENGTH, packedLight);
        addVertex(buffer, pose, -halfWidth, 0.0F, -NEEDLE_LENGTH, packedLight);

        poseStack.popPose();
    }

    private static void applyMountTransform(PoseStack poseStack, Direction attachedFace, Direction facing) {
        if (attachedFace == Direction.UP) {
            poseStack.translate(0.5F, 0.0F, 0.5F);
            poseStack.mulPose(Axis.YP.rotationDegrees(yRotationForFacing(facing)));
            poseStack.translate(-0.5F, 0.0F, -0.5F);
            return;
        }

        poseStack.translate(0.5F, 0.5F, 0.5F);
        poseStack.mulPose(Axis.XP.rotationDegrees(-90.0F));
        poseStack.mulPose(Axis.YP.rotationDegrees(wallYRotationForAttachedFace(attachedFace)));
        applyWallBlockCenteredNeedleCorrection(poseStack, attachedFace);
        poseStack.translate(-0.5F, -0.5F, -0.5F);
    }

    private static float yRotationForFacing(Direction facing) {
        return switch (facing) {
            case EAST -> 270.0F;
            case SOUTH -> 180.0F;
            case WEST -> 90.0F;
            default -> 0.0F;
        };
    }

    private static float wallYRotationForAttachedFace(Direction attachedFace) {
        return switch (attachedFace) {
            case EAST -> 270.0F;
            case SOUTH -> 0.0F;
            case WEST -> 90.0F;
            case NORTH -> 180.0F;
            default -> 180.0F;
        };
    }

    private static float placedNeedleRotation(Direction attachedFace, Direction facing, float handheldNeedleDegrees) {
        float dynamicDegrees = handheldNeedleDegrees * NEEDLE_ANGLE_MULTIPLIER;

        if (attachedFace == Direction.SOUTH || attachedFace == Direction.EAST) {
            return NEEDLE_ZERO_OFFSET_DEGREES - dynamicDegrees;
        }



        return NEEDLE_ZERO_OFFSET_DEGREES + dynamicDegrees;
    }

    private static void applyWallBlockCenteredNeedleCorrection(PoseStack poseStack, Direction attachedFace) {
        switch (attachedFace) {
            case SOUTH -> {
                poseStack.mulPose(Axis.XP.rotationDegrees(180.0F));
            }
            case EAST -> {
                poseStack.mulPose(Axis.XP.rotationDegrees(-90.0F));
                poseStack.mulPose(Axis.YP.rotationDegrees(-90.0F));
            }
            case WEST -> {
                poseStack.mulPose(Axis.XP.rotationDegrees(-90.0F));
                poseStack.mulPose(Axis.YP.rotationDegrees(90.0F));
            }
            default -> {
            }
        }
    }

    private static void applyWallNeedleUprightCorrection(PoseStack poseStack, Direction attachedFace) {
        switch (attachedFace) {
            case SOUTH -> {
                poseStack.mulPose(Axis.ZP.rotationDegrees(180.0F));
            }
            case EAST -> {
                poseStack.mulPose(Axis.ZP.rotationDegrees(180.0F));
            }
            default -> {
            }
        }
    }

    private static void addVertex(VertexConsumer buffer, Matrix4f pose, float x, float y, float z, int packedLight) {
        buffer.addVertex(pose, x, y, z)
                .setColor(0, 0, 0, 255)
                .setLight(packedLight);
    }
}
