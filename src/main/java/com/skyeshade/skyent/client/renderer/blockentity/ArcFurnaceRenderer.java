package com.skyeshade.skyent.client.renderer.blockentity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.skyeshade.skyent.SkyesNuclearTech;
import com.skyeshade.skyent.client.render.HeatingChamberLighting;
import com.skyeshade.skyent.content.block.ArcFurnaceBlock;
import com.skyeshade.skyent.content.blockentity.ArcFurnaceBlockEntity;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.color.block.BlockColors;
import net.minecraft.client.renderer.LightTexture;
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

public class ArcFurnaceRenderer implements BlockEntityRenderer<ArcFurnaceBlockEntity> {
    public static final ModelResourceLocation BASE_MODEL = ModelResourceLocation.standalone(
            ResourceLocation.fromNamespaceAndPath(SkyesNuclearTech.MOD_ID, "block/arc_furnace")
    );
    public static final ModelResourceLocation ELECTRODE_1_MODEL = electrodeModel("arc_furnace_electrode_1");
    public static final ModelResourceLocation ELECTRODE_2_MODEL = electrodeModel("arc_furnace_electrode_2");
    public static final ModelResourceLocation ELECTRODE_3_MODEL = electrodeModel("arc_furnace_electrode_3");
    public static final ModelResourceLocation ELECTRODE_3_TOPS_MODEL = electrodeModel("arc_furnace_electrode_3_tops");
    public static final ModelResourceLocation ELECTRODE_HOT_MODEL = electrodeModel("arc_furnace_electrode_hot");

    private static final ModelResourceLocation[] ELECTRODE_MODELS = {
            null,
            ELECTRODE_1_MODEL,
            ELECTRODE_2_MODEL,
            ELECTRODE_3_MODEL
    };
    private static final float[] FULL_BRIGHTNESS = new float[]{1.0F, 1.0F, 1.0F, 1.0F};
    private static final float MODEL_SCALE = 2.0F;
    private static final float RENDER_BRIGHTNESS_MULTIPLIER = 0.8F;
    private static final float RENDER_BRIGHTNESS_FLOOR = 0.3F;

    public ArcFurnaceRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(ArcFurnaceBlockEntity furnace, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        if (furnace.getLevel() == null) {
            return;
        }

        int electrodeCount = furnace.getElectrodeRenderCount();
        BlockState state = furnace.getBlockState();
        Direction facing = state.hasProperty(ArcFurnaceBlock.FACING) ? state.getValue(ArcFurnaceBlock.FACING) : Direction.NORTH;
        int sharedLight = HeatingChamberLighting.computeMaxPackedLight(
                furnace.getLevel(),
                furnace.getBlockPos(),
                facing,
                ArcFurnaceBlock.SIZE_X,
                ArcFurnaceBlock.SIZE_Y,
                ArcFurnaceBlock.SIZE_Z,
                ArcFurnaceBlock::localToWorld
        );

        renderModel(state, facing, BASE_MODEL, poseStack, bufferSource, sharedLight, false);

        if (furnace.isRunning()) {
            renderModel(state, facing, ELECTRODE_HOT_MODEL, poseStack, bufferSource, sharedLight, true);
            renderModel(state, facing, ELECTRODE_3_TOPS_MODEL, poseStack, bufferSource, sharedLight, false);
            return;
        }

        if (electrodeCount > 0) {
            renderModel(state, facing, ELECTRODE_MODELS[Math.min(electrodeCount, 3)], poseStack, bufferSource, sharedLight, false);
        }
    }

    @Override
    public boolean shouldRenderOffScreen(ArcFurnaceBlockEntity blockEntity) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 256;
    }

    private static ModelResourceLocation electrodeModel(String path) {
        return ModelResourceLocation.standalone(ResourceLocation.fromNamespaceAndPath(SkyesNuclearTech.MOD_ID, "block/" + path));
    }

    private static void renderModel(
            BlockState state,
            Direction facing,
            ModelResourceLocation modelLocation,
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            int packedLight,
            boolean emissive
    ) {
        Minecraft minecraft = Minecraft.getInstance();
        BakedModel model = minecraft.getModelManager().getModel(modelLocation);
        RenderType renderType = RenderType.cutout();
        int renderLight = emissive ? LightTexture.FULL_BRIGHT : packedLight;
        List<BakedQuad> quads = transformedQuads(model, state, facing, renderLight, emissive, renderType);
        if (quads.isEmpty()) {
            return;
        }

        VertexConsumer consumer = bufferSource.getBuffer(renderType);
        BlockColors blockColors = minecraft.getBlockColors();
        int[] lightmap = new int[]{renderLight, renderLight, renderLight, renderLight};
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

    private static List<BakedQuad> transformedQuads(BakedModel model, BlockState state, Direction facing, int packedLight, boolean emissive, RenderType renderType) {
        List<BakedQuad> sourceQuads = new ArrayList<>(model.getQuads(state, null, RandomSource.create(42L), ModelData.EMPTY, renderType));
        for (Direction direction : Direction.values()) {
            sourceQuads.addAll(model.getQuads(state, direction, RandomSource.create(42L), ModelData.EMPTY, renderType));
        }

        List<BakedQuad> transformed = new ArrayList<>(sourceQuads.size());
        for (BakedQuad quad : sourceQuads) {
            transformed.add(transformQuad(quad, facing, packedLight, emissive));
        }
        return transformed;
    }

    private static BakedQuad transformQuad(BakedQuad quad, Direction facing, int packedLight, boolean emissive) {
        int[] vertices = quad.getVertices().clone();
        for (int vertex = 0; vertex < 4; vertex++) {
            int offset = vertex * IQuadTransformer.STRIDE + IQuadTransformer.POSITION;
            float sourceX = Float.intBitsToFloat(vertices[offset]);
            float sourceY = Float.intBitsToFloat(vertices[offset + 1]);
            float sourceZ = Float.intBitsToFloat(vertices[offset + 2]);
            float[] transformed = transformSourcePoint(sourceX, sourceY, sourceZ, facing);
            vertices[offset] = Float.floatToRawIntBits(transformed[0]);
            vertices[offset + 1] = Float.floatToRawIntBits(transformed[1]);
            vertices[offset + 2] = Float.floatToRawIntBits(transformed[2]);
            vertices[vertex * IQuadTransformer.STRIDE + IQuadTransformer.UV2] = packedLight;
            vertices[vertex * IQuadTransformer.STRIDE + IQuadTransformer.NORMAL] = 0;
        }

        Direction transformedDirection = directionFromVertices(vertices);
        if (!emissive) {
            applyRenderBrightnessMultiplier(vertices, RENDER_BRIGHTNESS_MULTIPLIER, RENDER_BRIGHTNESS_FLOOR);
            applyDirectionalFaceColor(vertices, transformedDirection);
        }
        return new BakedQuad(
                vertices,
                quad.getTintIndex(),
                transformedDirection,
                quad.getSprite(),
                false,
                false
        );
    }

    private static float[] transformSourcePoint(float sourceX, float sourceY, float sourceZ, Direction facing) {
        float rotatedX;
        float rotatedZ;
        switch (facing) {
            case EAST -> {
                rotatedX = 1.0F - sourceZ;
                rotatedZ = sourceX;
            }
            case SOUTH -> {
                rotatedX = 1.0F - sourceX;
                rotatedZ = 1.0F - sourceZ;
            }
            case WEST -> {
                rotatedX = sourceZ;
                rotatedZ = 1.0F - sourceX;
            }
            default -> {
                rotatedX = sourceX;
                rotatedZ = sourceZ;
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
}
