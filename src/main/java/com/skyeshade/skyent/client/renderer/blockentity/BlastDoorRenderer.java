package com.skyeshade.skyent.client.renderer.blockentity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.skyeshade.skyent.SkyesNuclearTech;
import com.skyeshade.skyent.client.model.SkyentModelData;
import com.skyeshade.skyent.client.render.HeatingChamberLighting;
import com.skyeshade.skyent.content.block.BlastDoorBlock;
import com.skyeshade.skyent.content.blockentity.BlastDoorBlockEntity;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.color.block.BlockColors;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.IQuadTransformer;
import net.neoforged.neoforge.client.model.data.ModelData;

public class BlastDoorRenderer implements BlockEntityRenderer<BlastDoorBlockEntity> {
    public static final ModelResourceLocation DOOR_PANEL_MODEL = ModelResourceLocation.standalone(
            ResourceLocation.fromNamespaceAndPath(SkyesNuclearTech.MOD_ID, "block/blast_door_door_scaled")
    );
    private static final float[] FULL_BRIGHTNESS = new float[]{1.0F, 1.0F, 1.0F, 1.0F};
    private static final double DOOR_VISIBLE_MIN_Y = 0.0D;
    // Tune this if the door should disappear into a higher or lower part of the frame.
    private static final double DOOR_VISIBLE_MAX_Y = 3.0D;

    public BlastDoorRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(BlastDoorBlockEntity blastDoor, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        if (blastDoor.getLevel() == null) {
            return;
        }

        BlockState state = blastDoor.getBlockState();
        Direction facing = state.hasProperty(BlastDoorBlock.FACING) ? state.getValue(BlastDoorBlock.FACING) : Direction.NORTH;
        float progress = blastDoor.getAnimationProgress(partialTick);

        double yOffset = progress * 2.7D;
        int sharedLight = HeatingChamberLighting.computeMaxPackedLight(
                blastDoor.getLevel(),
                blastDoor.getBlockPos(),
                facing,
                BlastDoorBlock.SIZE_X,
                BlastDoorBlock.SIZE_Y,
                BlastDoorBlock.SIZE_Z,
                BlastDoorBlock::localToWorld
        );

        poseStack.pushPose();
        rotateForFacing(facing, poseStack);
        renderModel(state, poseStack, bufferSource, sharedLight, yOffset);
        poseStack.popPose();
    }

    @Override
    public boolean shouldRenderOffScreen(BlastDoorBlockEntity blockEntity) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 256;
    }

    private static void rotateForFacing(Direction facing, PoseStack poseStack) {
        poseStack.translate(0.5D, 0.0D, 0.5D);
        poseStack.mulPose(Axis.YP.rotationDegrees(yRotationForFacing(facing)));
        poseStack.translate(-0.5D, 0.0D, -0.5D);
    }

    private static float yRotationForFacing(Direction facing) {
        return switch (facing) {
            case EAST -> 90.0F;
            case SOUTH -> 180.0F;
            case WEST -> 270.0F;
            default -> 0.0F;
        };
    }

    private static void renderModel(BlockState state, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, double yOffset) {
        Minecraft minecraft = Minecraft.getInstance();
        BakedModel model = minecraft.getModelManager().getModel(DOOR_PANEL_MODEL);
        RenderType renderType = RenderType.cutout();
        ModelData modelData = ModelData.of(SkyentModelData.SHARED_PACKED_LIGHT, packedLight);
        List<BakedQuad> quads = model.getQuads(state, null, RandomSource.create(42L), modelData, renderType);
        if (quads.isEmpty()) {
            return;
        }

        VertexConsumer consumer = bufferSource.getBuffer(renderType);
        BlockColors blockColors = minecraft.getBlockColors();
        int[] lightmap = new int[]{packedLight, packedLight, packedLight, packedLight};
        for (BakedQuad quad : quads) {
            float red = 1.0F;
            float green = 1.0F;
            float blue = 1.0F;
            if (quad.isTinted()) {
                int color = blockColors.getColor(state, null, null, quad.getTintIndex());
                red = (color >> 16 & 0xFF) / 255.0F;
                green = (color >> 8 & 0xFF) / 255.0F;
                blue = (color & 0xFF) / 255.0F;
            }
            for (BakedQuad clippedQuad : clipQuadToVisibleDoorY(quad, yOffset)) {
                consumer.putBulkData(poseStack.last(), clippedQuad, FULL_BRIGHTNESS, red, green, blue, 1.0F, lightmap, OverlayTexture.NO_OVERLAY, true);
            }
        }
    }

    private static List<BakedQuad> clipQuadToVisibleDoorY(BakedQuad quad, double yOffset) {
        List<ClipVertex> vertices = new ArrayList<>(4);
        int[] source = quad.getVertices();
        for (int vertex = 0; vertex < 4; vertex++) {
            vertices.add(ClipVertex.from(source, vertex, yOffset));
        }

        vertices = clipAgainstMinY(vertices, DOOR_VISIBLE_MIN_Y);
        if (vertices.size() < 3) {
            return List.of();
        }
        vertices = clipAgainstMaxY(vertices, DOOR_VISIBLE_MAX_Y);
        if (vertices.size() < 3) {
            return List.of();
        }

        List<BakedQuad> clipped = new ArrayList<>(Math.max(1, vertices.size() - 2));
        for (int index = 1; index < vertices.size() - 1; index++) {
            clipped.add(copyAsQuad(quad, vertices.get(0), vertices.get(index), vertices.get(index + 1)));
        }
        return clipped;
    }

    private static List<ClipVertex> clipAgainstMinY(List<ClipVertex> vertices, double minY) {
        return clipAgainstY(vertices, minY, true);
    }

    private static List<ClipVertex> clipAgainstMaxY(List<ClipVertex> vertices, double maxY) {
        return clipAgainstY(vertices, maxY, false);
    }

    private static List<ClipVertex> clipAgainstY(List<ClipVertex> vertices, double planeY, boolean keepAbove) {
        if (vertices.isEmpty()) {
            return vertices;
        }

        List<ClipVertex> clipped = new ArrayList<>(vertices.size() + 1);
        ClipVertex previous = vertices.get(vertices.size() - 1);
        boolean previousInside = isInsideY(previous, planeY, keepAbove);
        for (ClipVertex current : vertices) {
            boolean currentInside = isInsideY(current, planeY, keepAbove);
            if (currentInside != previousInside) {
                clipped.add(ClipVertex.interpolateAtY(previous, current, planeY));
            }
            if (currentInside) {
                clipped.add(current);
            }
            previous = current;
            previousInside = currentInside;
        }
        return clipped;
    }

    private static boolean isInsideY(ClipVertex vertex, double planeY, boolean keepAbove) {
        return keepAbove ? vertex.y() >= planeY : vertex.y() <= planeY;
    }

    private static BakedQuad copyAsQuad(BakedQuad source, ClipVertex first, ClipVertex second, ClipVertex third) {
        int[] vertices = new int[IQuadTransformer.STRIDE * 4];
        first.copyTo(vertices, 0);
        second.copyTo(vertices, 1);
        third.copyTo(vertices, 2);
        third.copyTo(vertices, 3);
        return new BakedQuad(
                vertices,
                source.getTintIndex(),
                source.getDirection(),
                source.getSprite(),
                source.isShade(),
                source.hasAmbientOcclusion()
        );
    }

    private static final class ClipVertex {
        private static final int UV_U = 4;
        private static final int UV_V = 5;

        private final int[] data;

        private ClipVertex(int[] data) {
            this.data = data;
        }

        private static ClipVertex from(int[] source, int vertex, double yOffset) {
            int[] data = new int[IQuadTransformer.STRIDE];
            System.arraycopy(source, vertex * IQuadTransformer.STRIDE, data, 0, IQuadTransformer.STRIDE);
            data[IQuadTransformer.POSITION + 1] = Float.floatToRawIntBits((float) (y(data) + yOffset));
            return new ClipVertex(data);
        }

        private static ClipVertex interpolateAtY(ClipVertex first, ClipVertex second, double y) {
            double delta = second.y() - first.y();
            float t = Math.abs(delta) < 1.0E-7D ? 0.0F : (float) ((y - first.y()) / delta);
            t = Math.max(0.0F, Math.min(1.0F, t));

            int[] data = new int[IQuadTransformer.STRIDE];
            for (int index = 0; index < data.length; index++) {
                if (index >= IQuadTransformer.POSITION && index <= IQuadTransformer.POSITION + 2) {
                    data[index] = Float.floatToRawIntBits(lerp(Float.intBitsToFloat(first.data[index]), Float.intBitsToFloat(second.data[index]), t));
                } else if (index == UV_U || index == UV_V) {
                    data[index] = Float.floatToRawIntBits(lerp(Float.intBitsToFloat(first.data[index]), Float.intBitsToFloat(second.data[index]), t));
                } else {
                    data[index] = t < 0.5F ? first.data[index] : second.data[index];
                }
            }
            data[IQuadTransformer.POSITION + 1] = Float.floatToRawIntBits((float) y);
            return new ClipVertex(data);
        }

        private void copyTo(int[] target, int vertex) {
            System.arraycopy(data, 0, target, vertex * IQuadTransformer.STRIDE, IQuadTransformer.STRIDE);
        }

        private float y() {
            return y(data);
        }

        private static float y(int[] data) {
            return Float.intBitsToFloat(data[IQuadTransformer.POSITION + 1]);
        }

        private static float lerp(float first, float second, float t) {
            return first + (second - first) * t;
        }
    }
}
