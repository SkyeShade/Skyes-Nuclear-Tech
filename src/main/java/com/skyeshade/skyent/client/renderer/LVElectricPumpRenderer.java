package com.skyeshade.skyent.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.skyeshade.skyent.SkyesNuclearTech;
import com.skyeshade.skyent.content.blockentity.LVElectricPumpBlockEntity;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.FluidState;
import org.joml.Matrix4f;

public class LVElectricPumpRenderer implements BlockEntityRenderer<LVElectricPumpBlockEntity> {
    private static final ResourceLocation PIPE_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            SkyesNuclearTech.MOD_ID,
            "textures/block/pump_pipe.png"
    );
    private static final RenderType PIPE_RENDER_TYPE = RenderType.entityCutout(PIPE_TEXTURE);
    private static final float PIPE_MIN = 0.25F;
    private static final float PIPE_MAX = 0.75F;
    private static final float U_MIN = 0.0F;
    private static final float U_MAX = 1.0F;
    private static final float V_MIN = 0.0F;
    private static final float V_MAX = 1.0F;

    public LVElectricPumpRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(LVElectricPumpBlockEntity pump, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        Level level = pump.getLevel();
        if (level == null) {
            return;
        }

        int depth = fluidColumnDepth(level, pump.getBlockPos());
        if (depth <= 0) {
            return;
        }

        VertexConsumer buffer = bufferSource.getBuffer(PIPE_RENDER_TYPE);
        PoseStack.Pose pose = poseStack.last();
        Matrix4f position = pose.pose();
        BlockPos segmentPos = pump.getBlockPos().below();

        for (int segment = 0; segment < depth; segment++) {
            int light = LevelRenderer.getLightColor(level, segmentPos.below(segment));
            renderSegment(buffer, pose, position, segment, light);
        }
    }

    private static int fluidColumnDepth(Level level, BlockPos pumpPos) {
        int depth = 0;
        BlockPos.MutableBlockPos cursor = pumpPos.below().mutable();

        while (isWaterOrLava(level.getFluidState(cursor))) {
            depth++;
            cursor.move(0, -1, 0);
        }

        return depth;
    }

    private static boolean isWaterOrLava(FluidState state) {
        return state.is(FluidTags.WATER) || state.is(FluidTags.LAVA);
    }

    private static void renderSegment(VertexConsumer buffer, PoseStack.Pose pose, Matrix4f position, int segment, int light) {
        float yTop = -segment;
        float yBottom = yTop - 1.0F;

        addQuad(buffer, pose, position,
                PIPE_MIN, yBottom, PIPE_MIN,
                PIPE_MAX, yBottom, PIPE_MIN,
                PIPE_MAX, yTop, PIPE_MIN,
                PIPE_MIN, yTop, PIPE_MIN,
                0.0F, 0.0F, -1.0F,
                light
        );
        addQuad(buffer, pose, position,
                PIPE_MAX, yBottom, PIPE_MAX,
                PIPE_MIN, yBottom, PIPE_MAX,
                PIPE_MIN, yTop, PIPE_MAX,
                PIPE_MAX, yTop, PIPE_MAX,
                0.0F, 0.0F, 1.0F,
                light
        );
        addQuad(buffer, pose, position,
                PIPE_MIN, yBottom, PIPE_MAX,
                PIPE_MIN, yBottom, PIPE_MIN,
                PIPE_MIN, yTop, PIPE_MIN,
                PIPE_MIN, yTop, PIPE_MAX,
                -1.0F, 0.0F, 0.0F,
                light
        );
        addQuad(buffer, pose, position,
                PIPE_MAX, yBottom, PIPE_MIN,
                PIPE_MAX, yBottom, PIPE_MAX,
                PIPE_MAX, yTop, PIPE_MAX,
                PIPE_MAX, yTop, PIPE_MIN,
                1.0F, 0.0F, 0.0F,
                light
        );
    }

    private static void addQuad(
            VertexConsumer buffer,
            PoseStack.Pose pose,
            Matrix4f position,
            float x1,
            float y1,
            float z1,
            float x2,
            float y2,
            float z2,
            float x3,
            float y3,
            float z3,
            float x4,
            float y4,
            float z4,
            float normalX,
            float normalY,
            float normalZ,
            int light
    ) {
        addVertex(buffer, pose, position, x1, y1, z1, U_MIN, V_MAX, normalX, normalY, normalZ, light);
        addVertex(buffer, pose, position, x4, y4, z4, U_MIN, V_MIN, normalX, normalY, normalZ, light);
        addVertex(buffer, pose, position, x3, y3, z3, U_MAX, V_MIN, normalX, normalY, normalZ, light);
        addVertex(buffer, pose, position, x2, y2, z2, U_MAX, V_MAX, normalX, normalY, normalZ, light);
    }

    private static void addVertex(
            VertexConsumer buffer,
            PoseStack.Pose pose,
            Matrix4f position,
            float x,
            float y,
            float z,
            float u,
            float v,
            float normalX,
            float normalY,
            float normalZ,
            int light
    ) {
        buffer.addVertex(position, x, y, z)
                .setColor(255, 255, 255, 255)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light)
                .setNormal(pose, normalX, normalY, normalZ);
    }
}
