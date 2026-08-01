package com.skyeshade.skyent.client.renderer.blockentity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.skyeshade.skyent.SkyesNuclearTech;
import com.skyeshade.skyent.content.block.CentrifugeBlock;
import com.skyeshade.skyent.content.blockentity.CentrifugeBlockEntity;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import net.minecraft.client.renderer.LightTexture;
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
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.IQuadTransformer;
import net.neoforged.neoforge.client.model.data.ModelData;

public class CentrifugeRenderer implements BlockEntityRenderer<CentrifugeBlockEntity> {
    public static final ModelResourceLocation DRUM_MODEL = ModelResourceLocation.standalone(
            ResourceLocation.fromNamespaceAndPath(SkyesNuclearTech.MOD_ID, "block/centrifuge_drum")
    );

    private static final float[] FULL_BRIGHTNESS = new float[]{1.0F, 1.0F, 1.0F, 1.0F};
    private static final float MODEL_SCALE = 2.0F;
    private static final float RENDER_BRIGHTNESS_MULTIPLIER = 0.8F;
    private static final float RENDER_BRIGHTNESS_FLOOR = 0.3F;
    private static final float ROTATION_DEGREES_PER_TICK = 16.0F;
    private static final float RAMP_TICKS = 20.0F;
    private static final double DRUM_PIVOT_X = 12.00685D / 16.0D;
    private static final double DRUM_PIVOT_Y = 18.5125D / 16.0D;
    private static final double DRUM_PIVOT_Z = 4.0D / 16.0D;
    private static final Map<AnimationKey, AnimationState> ANIMATION_STATES = new HashMap<>();

    public CentrifugeRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(CentrifugeBlockEntity centrifuge, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        if (centrifuge.getLevel() == null) {
            return;
        }

        BlockState state = centrifuge.getBlockState();
        Direction facing = state.hasProperty(CentrifugeBlock.FACING) ? state.getValue(CentrifugeBlock.FACING) : Direction.NORTH;
        AnimationState animationState = animationStateFor(centrifuge);
        float angle = animationState.update(centrifuge.getLevel().getGameTime() + partialTick, centrifuge.isRunning());
        pruneAnimationStates(centrifuge.getLevel());

        renderDrumModel(state, facing, angle, poseStack, bufferSource, centrifuge.getSharedPackedLight());
    }

    @Override
    public boolean shouldRenderOffScreen(CentrifugeBlockEntity blockEntity) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 256;
    }

    private static AnimationState animationStateFor(CentrifugeBlockEntity centrifuge) {
        Level level = centrifuge.getLevel();
        AnimationKey key = new AnimationKey(level.dimension(), centrifuge.getBlockPos().immutable());
        return ANIMATION_STATES.computeIfAbsent(key, unused -> new AnimationState());
    }

    private static void pruneAnimationStates(Level level) {
        if (ANIMATION_STATES.size() <= 256) {
            return;
        }

        ResourceKey<Level> dimension = level.dimension();
        Iterator<AnimationKey> iterator = ANIMATION_STATES.keySet().iterator();
        while (iterator.hasNext()) {
            if (!iterator.next().dimension().equals(dimension)) {
                iterator.remove();
            }
        }
    }

    private static void renderDrumModel(BlockState state, Direction facing, float angle, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {
        Minecraft minecraft = Minecraft.getInstance();
        BakedModel model = minecraft.getModelManager().getModel(DRUM_MODEL);
        RenderType renderType = RenderType.cutout();
        List<BakedQuad> quads = transformedDrumQuads(model, state, facing, angle, packedLight, renderType);
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
            consumer.putBulkData(poseStack.last(), quad, FULL_BRIGHTNESS, red, green, blue, 1.0F, lightmap, OverlayTexture.NO_OVERLAY, true);
        }
    }

    private static List<BakedQuad> transformedDrumQuads(BakedModel model, BlockState state, Direction facing, float angle, int packedLight, RenderType renderType) {
        List<BakedQuad> sourceQuads = new ArrayList<>(model.getQuads(state, null, RandomSource.create(42L), ModelData.EMPTY, renderType));
        for (Direction direction : Direction.values()) {
            sourceQuads.addAll(model.getQuads(state, direction, RandomSource.create(42L), ModelData.EMPTY, renderType));
        }

        List<BakedQuad> transformed = new ArrayList<>(sourceQuads.size());
        for (BakedQuad quad : sourceQuads) {
            transformed.add(transformDrumQuad(quad, facing, angle, packedLight));
        }
        return transformed;
    }

    private static BakedQuad transformDrumQuad(BakedQuad quad, Direction facing, float angle, int packedLight) {
        int[] vertices = quad.getVertices().clone();
        for (int vertex = 0; vertex < 4; vertex++) {
            int offset = vertex * IQuadTransformer.STRIDE + IQuadTransformer.POSITION;
            float sourceX = Float.intBitsToFloat(vertices[offset]);
            float sourceY = Float.intBitsToFloat(vertices[offset + 1]);
            float sourceZ = Float.intBitsToFloat(vertices[offset + 2]);
            float[] transformed = transformSourcePoint(sourceX, sourceY, sourceZ, facing, angle);
            vertices[offset] = Float.floatToRawIntBits(transformed[0]);
            vertices[offset + 1] = Float.floatToRawIntBits(transformed[1]);
            vertices[offset + 2] = Float.floatToRawIntBits(transformed[2]);
            vertices[vertex * IQuadTransformer.STRIDE + IQuadTransformer.UV2] = packedLight;
            vertices[vertex * IQuadTransformer.STRIDE + IQuadTransformer.NORMAL] = 0;
        }

        Direction transformedDirection = directionFromVertices(vertices);
        applyRenderBrightnessMultiplier(vertices, RENDER_BRIGHTNESS_MULTIPLIER, RENDER_BRIGHTNESS_FLOOR);
        applyDirectionalFaceColor(vertices, transformedDirection);
        return new BakedQuad(
                vertices,
                quad.getTintIndex(),
                transformedDirection,
                quad.getSprite(),
                false,
                false
        );
    }

    private static float[] transformSourcePoint(float sourceX, float sourceY, float sourceZ, Direction facing, float angle) {
        double radians = Math.toRadians(angle);
        double offsetX = sourceX - DRUM_PIVOT_X;
        double offsetZ = sourceZ - DRUM_PIVOT_Z;
        float spunX = (float) (DRUM_PIVOT_X + offsetX * Math.cos(radians) - offsetZ * Math.sin(radians));
        float spunZ = (float) (DRUM_PIVOT_Z + offsetX * Math.sin(radians) + offsetZ * Math.cos(radians));

        float rotatedX;
        float rotatedZ;
        switch (facing) {
            case EAST -> {
                rotatedX = 1.0F - spunZ;
                rotatedZ = spunX;
            }
            case SOUTH -> {
                rotatedX = 1.0F - spunX;
                rotatedZ = 1.0F - spunZ;
            }
            case WEST -> {
                rotatedX = spunZ;
                rotatedZ = 1.0F - spunX;
            }
            default -> {
                rotatedX = spunX;
                rotatedZ = spunZ;
            }
        }

        return new float[]{
                rotatedX * MODEL_SCALE + translationX(facing),
                sourceY * MODEL_SCALE,
                rotatedZ * MODEL_SCALE + translationZ(facing)
        };
    }

    private static float translationX(Direction facing) {
        return switch (facing) {
            case EAST, SOUTH -> 0.0F;
            default -> -1.0F;
        };
    }

    private static float translationZ(Direction facing) {
        return switch (facing) {
            case SOUTH, WEST -> 0.0F;
            default -> -1.0F;
        };
    }

    private static Direction directionFromVertices(int[] vertices) {
        float x0 = vertexPosition(vertices, 0, 0);
        float y0 = vertexPosition(vertices, 0, 1);
        float z0 = vertexPosition(vertices, 0, 2);
        float x1 = vertexPosition(vertices, 1, 0);
        float y1 = vertexPosition(vertices, 1, 1);
        float z1 = vertexPosition(vertices, 1, 2);
        float x2 = vertexPosition(vertices, 2, 0);
        float y2 = vertexPosition(vertices, 2, 1);
        float z2 = vertexPosition(vertices, 2, 2);

        float ax = x1 - x0;
        float ay = y1 - y0;
        float az = z1 - z0;
        float bx = x2 - x0;
        float by = y2 - y0;
        float bz = z2 - z0;
        float normalX = ay * bz - az * by;
        float normalY = az * bx - ax * bz;
        float normalZ = ax * by - ay * bx;

        float absX = Math.abs(normalX);
        float absY = Math.abs(normalY);
        float absZ = Math.abs(normalZ);
        if (absY >= absX && absY >= absZ) {
            return normalY >= 0.0F ? Direction.UP : Direction.DOWN;
        }
        if (absX >= absZ) {
            return normalX >= 0.0F ? Direction.EAST : Direction.WEST;
        }
        return normalZ >= 0.0F ? Direction.SOUTH : Direction.NORTH;
    }

    private static float vertexPosition(int[] vertices, int vertex, int axisOffset) {
        return Float.intBitsToFloat(vertices[vertex * IQuadTransformer.STRIDE + IQuadTransformer.POSITION + axisOffset]);
    }

    private static void applyRenderBrightnessMultiplier(int[] vertices, float multiplier, float floor) {
        float scale = Math.max(Math.max(0.0F, Math.min(1.0F, multiplier)), Math.max(0.0F, Math.min(1.0F, floor)));
        if (scale >= 0.999F) {
            return;
        }

        for (int vertex = 0; vertex < 4; vertex++) {
            int offset = vertex * IQuadTransformer.STRIDE + IQuadTransformer.COLOR;
            vertices[offset] = scaleColor(vertices[offset], scale);
        }
    }

    private static void applyDirectionalFaceColor(int[] vertices, Direction direction) {
        float shade = directionalFaceShade(direction);
        if (shade >= 0.999F) {
            return;
        }
        for (int vertex = 0; vertex < 4; vertex++) {
            int offset = vertex * IQuadTransformer.STRIDE + IQuadTransformer.COLOR;
            vertices[offset] = scaleColor(vertices[offset], shade);
        }
    }

    private static float directionalFaceShade(Direction direction) {
        return switch (direction) {
            case UP -> 1.0F;
            case DOWN -> 0.55F;
            case NORTH, SOUTH -> 0.82F;
            case EAST, WEST -> 0.70F;
        };
    }

    private static int scaleColor(int color, float scale) {
        int red = Math.max(0, Math.min(255, Math.round((color & 0xFF) * scale)));
        int green = Math.max(0, Math.min(255, Math.round(((color >> 8) & 0xFF) * scale)));
        int blue = Math.max(0, Math.min(255, Math.round(((color >> 16) & 0xFF) * scale)));
        int alpha = (color >> 24) & 0xFF;
        return red | green << 8 | blue << 16 | alpha << 24;
    }

    private record AnimationKey(ResourceKey<Level> dimension, BlockPos pos) {
    }

    private static final class AnimationState {
        private float angle;
        private float speedFactor;
        private float lastRenderTime = Float.NaN;

        private float update(float renderTime, boolean running) {
            float deltaTicks = Float.isNaN(lastRenderTime) ? 0.0F : Math.max(0.0F, renderTime - lastRenderTime);
            lastRenderTime = renderTime;

            float targetSpeed = running ? 1.0F : 0.0F;
            float rampStep = deltaTicks / RAMP_TICKS;
            if (speedFactor < targetSpeed) {
                speedFactor = Math.min(targetSpeed, speedFactor + rampStep);
            } else if (speedFactor > targetSpeed) {
                speedFactor = Math.max(targetSpeed, speedFactor - rampStep);
            }

            angle = (angle + ROTATION_DEGREES_PER_TICK * speedFactor * deltaTicks) % 360.0F;
            return angle;
        }
    }
}
