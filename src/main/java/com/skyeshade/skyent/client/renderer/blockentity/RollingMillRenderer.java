package com.skyeshade.skyent.client.renderer.blockentity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.skyeshade.skyent.SkyesNuclearTech;
import com.skyeshade.skyent.content.block.RollingMillBlock;
import com.skyeshade.skyent.content.blockentity.RollingMillBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.Material;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class RollingMillRenderer implements BlockEntityRenderer<RollingMillBlockEntity> {
    public static final ModelResourceLocation ROLLERS_MODEL = ModelResourceLocation.standalone(
            ResourceLocation.fromNamespaceAndPath(SkyesNuclearTech.MOD_ID, "block/rolling_mill_rollers")
    );

    private static final Material ROLLING_MILL_TEXTURE = new Material(
            TextureAtlas.LOCATION_BLOCKS,
            ResourceLocation.fromNamespaceAndPath(SkyesNuclearTech.MOD_ID, "block/rolling_mill")
    );
    private static final float MODEL_SCALE = 2.0F;
    private static final float ROTATION_DEGREES_PER_TICK = 16.0F;
    private static final float RAMP_TICKS = 20.0F;
    private static final float BOTTOM_ROLLER_PHASE_OFFSET_DEGREES = 45.0F;
    private static final float TOP_ROLLER_MIN_CENTER_Y = 11.0F;
    private static final Map<AnimationKey, AnimationState> ANIMATION_STATES = new HashMap<>();
    private static final RollerCuboid[] ROLLERS = {
            new RollerCuboid(0.5F, 11.25F, 10.5F, 16.0F, 14.25F, 13.5F,
                    new FaceUv(7.875F, 3.375F, 9.8125F, 3.75F),
                    new FaceUv(7.875F, 3.75F, 9.8125F, 4.125F),
                    new FaceUv(2.875F, 9.75F, 3.25F, 10.125F),
                    null,
                    new FaceUv(9.8125F, 4.5F, 7.875F, 4.125F),
                    new FaceUv(9.8125F, 4.5F, 7.875F, 4.875F)),
            new RollerCuboid(0.5F, 7.75F, 10.5F, 16.0F, 10.75F, 13.5F,
                    new FaceUv(7.875F, 4.875F, 9.8125F, 5.25F),
                    new FaceUv(7.875F, 5.25F, 9.8125F, 5.625F),
                    null,
                    null,
                    new FaceUv(9.8125F, 6.0F, 7.875F, 5.625F),
                    new FaceUv(9.8125F, 6.0F, 7.875F, 6.375F)),
            new RollerCuboid(0.5F, 11.25F, 2.5F, 16.0F, 14.25F, 5.5F,
                    new FaceUv(7.875F, 6.375F, 9.8125F, 6.75F),
                    new FaceUv(7.875F, 6.75F, 9.8125F, 7.125F),
                    null,
                    null,
                    new FaceUv(9.8125F, 7.5F, 7.875F, 7.125F),
                    new FaceUv(9.8125F, 7.5F, 7.875F, 7.875F)),
            new RollerCuboid(0.5F, 7.75F, 2.5F, 16.0F, 10.75F, 5.5F,
                    new FaceUv(7.875F, 7.875F, 9.8125F, 8.25F),
                    new FaceUv(3.75F, 8.125F, 5.6875F, 8.5F),
                    null,
                    null,
                    new FaceUv(9.1875F, 8.625F, 7.25F, 8.25F),
                    new FaceUv(10.3125F, 0.375F, 8.375F, 0.75F))
    };

    public RollingMillRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(RollingMillBlockEntity rollingMill, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        if (rollingMill.getLevel() == null) {
            return;
        }

        BlockState state = rollingMill.getBlockState();
        Direction facing = state.hasProperty(RollingMillBlock.FACING) ? state.getValue(RollingMillBlock.FACING) : Direction.NORTH;
        AnimationState animationState = animationStateFor(rollingMill);
        float angle = animationState.update(rollingMill.getLevel().getGameTime() + partialTick, rollingMill.isRunning());
        pruneAnimationStates(rollingMill.getLevel());
        TextureAtlasSprite sprite = Minecraft.getInstance()
                .getTextureAtlas(TextureAtlas.LOCATION_BLOCKS)
                .apply(ROLLING_MILL_TEXTURE.texture());
        VertexConsumer consumer = bufferSource.getBuffer(RenderType.cutout());
        ModelTransform transform = ModelTransform.forFacing(facing);

        for (RollerCuboid roller : ROLLERS) {
            renderRoller(roller, angle, transform, poseStack, consumer, sprite, packedLight);
        }
    }

    private static AnimationState animationStateFor(RollingMillBlockEntity rollingMill) {
        Level level = rollingMill.getLevel();
        AnimationKey key = new AnimationKey(level.dimension(), rollingMill.getBlockPos().immutable());
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

    private static void renderRoller(RollerCuboid roller, float angle, ModelTransform transform, PoseStack poseStack, VertexConsumer consumer, TextureAtlasSprite sprite, int packedLight) {
        renderCuboid(roller, roller.isTopRoller() ? angle : -angle + BOTTOM_ROLLER_PHASE_OFFSET_DEGREES, transform, poseStack, consumer, sprite, packedLight);
    }

    private static void renderCuboid(RollerCuboid cuboid, float angle, ModelTransform transform, PoseStack poseStack, VertexConsumer consumer, TextureAtlasSprite sprite, int packedLight) {
        PoseStack.Pose pose = poseStack.last();
        Matrix4f position = pose.pose();
        Vector3f nwb = transform.point(cuboid.minX, cuboid.minY, cuboid.minZ, cuboid, angle);
        Vector3f neb = transform.point(cuboid.maxX, cuboid.minY, cuboid.minZ, cuboid, angle);
        Vector3f net = transform.point(cuboid.maxX, cuboid.maxY, cuboid.minZ, cuboid, angle);
        Vector3f nwt = transform.point(cuboid.minX, cuboid.maxY, cuboid.minZ, cuboid, angle);
        Vector3f swb = transform.point(cuboid.minX, cuboid.minY, cuboid.maxZ, cuboid, angle);
        Vector3f seb = transform.point(cuboid.maxX, cuboid.minY, cuboid.maxZ, cuboid, angle);
        Vector3f set = transform.point(cuboid.maxX, cuboid.maxY, cuboid.maxZ, cuboid, angle);
        Vector3f swt = transform.point(cuboid.minX, cuboid.maxY, cuboid.maxZ, cuboid, angle);

        addQuad(consumer, pose, position, nwb, neb, net, nwt, 0.0F, 0.0F, -1.0F, cuboid.north, sprite, packedLight);
        addQuad(consumer, pose, position, seb, swb, swt, set, 0.0F, 0.0F, 1.0F, cuboid.south, sprite, packedLight);
        addQuad(consumer, pose, position, swb, nwb, nwt, swt, -1.0F, 0.0F, 0.0F, cuboid.west, sprite, packedLight);
        addQuad(consumer, pose, position, neb, seb, set, net, 1.0F, 0.0F, 0.0F, cuboid.east, sprite, packedLight);
        addQuad(consumer, pose, position, nwt, net, set, swt, 0.0F, 1.0F, 0.0F, cuboid.up, sprite, packedLight);
        addQuad(consumer, pose, position, swb, seb, neb, nwb, 0.0F, -1.0F, 0.0F, cuboid.down, sprite, packedLight);
    }

    private static void addQuad(
            VertexConsumer consumer,
            PoseStack.Pose pose,
            Matrix4f position,
            Vector3f v1,
            Vector3f v2,
            Vector3f v3,
            Vector3f v4,
            float normalX,
            float normalY,
            float normalZ,
            FaceUv uv,
            TextureAtlasSprite sprite,
            int packedLight
    ) {
        if (uv == null) {
            return;
        }
        float u0 = atlasU(sprite, uv.u0);
        float u1 = atlasU(sprite, uv.u1);
        float v0 = atlasV(sprite, uv.v0);
        float v1Atlas = atlasV(sprite, uv.v1);
        addVertex(consumer, pose, position, v1.x(), v1.y(), v1.z(), u0, v1Atlas, normalX, normalY, normalZ, packedLight);
        addVertex(consumer, pose, position, v4.x(), v4.y(), v4.z(), u0, v0, normalX, normalY, normalZ, packedLight);
        addVertex(consumer, pose, position, v3.x(), v3.y(), v3.z(), u1, v0, normalX, normalY, normalZ, packedLight);
        addVertex(consumer, pose, position, v2.x(), v2.y(), v2.z(), u1, v1Atlas, normalX, normalY, normalZ, packedLight);
    }

    private static float atlasU(TextureAtlasSprite sprite, float modelU) {
        return sprite.getU0() + (sprite.getU1() - sprite.getU0()) * modelU / 16.0F;
    }

    private static float atlasV(TextureAtlasSprite sprite, float modelV) {
        return sprite.getV0() + (sprite.getV1() - sprite.getV0()) * modelV / 16.0F;
    }

    private static void addVertex(
            VertexConsumer consumer,
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
            int packedLight
    ) {
        consumer.addVertex(position, x, y, z)
                .setColor(255, 255, 255, 255)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(packedLight)
                .setNormal(pose, normalX, normalY, normalZ);
    }

    private record ModelTransform(Direction facing, float translateX, float translateY, float translateZ) {
        private static ModelTransform forFacing(Direction facing) {
            return switch (facing) {
                case EAST -> new ModelTransform(facing, -16.0F, 0.0F, 16.0F);
                case SOUTH -> new ModelTransform(facing, -32.0F, 0.0F, -16.0F);
                case WEST -> new ModelTransform(facing, 0.0F, 0.0F, -32.0F);
                default -> new ModelTransform(facing, 16.0F, 0.0F, 0.0F);
            };
        }

        private Vector3f point(float modelX, float modelY, float modelZ, RollerCuboid roller, float angle) {
            // Match the static Rolling Mill model chain:
            // source model coords -> blockstate Y rotation -> scaled_block_model scale/translation.
            // The roller spin is applied first in source model space so each cuboid keeps its own local X-axis pivot.
            Vector3f spun = spinAroundRollerLocalX(modelX, modelY, modelZ, roller, angle);
            Vector3f rotated = rotateLikeBlockstateVariant(spun.x(), spun.y(), spun.z());
            return new Vector3f(
                    (rotated.x() * MODEL_SCALE + translateX) / 16.0F,
                    (rotated.y() * MODEL_SCALE + translateY) / 16.0F,
                    (rotated.z() * MODEL_SCALE + translateZ) / 16.0F
            );
        }

        private Vector3f spinAroundRollerLocalX(float modelX, float modelY, float modelZ, RollerCuboid roller, float angle) {
            float centerY = (roller.minY + roller.maxY) * 0.5F;
            float centerZ = (roller.minZ + roller.maxZ) * 0.5F;
            double radians = Math.toRadians(angle);
            float offsetY = modelY - centerY;
            float offsetZ = modelZ - centerZ;
            float spunY = centerY + (float) (offsetY * Math.cos(radians) - offsetZ * Math.sin(radians));
            float spunZ = centerZ + (float) (offsetY * Math.sin(radians) + offsetZ * Math.cos(radians));
            return new Vector3f(modelX, spunY, spunZ);
        }

        private Vector3f rotateLikeBlockstateVariant(float modelX, float modelY, float modelZ) {
            return switch (facing) {
                case EAST -> new Vector3f(16.0F - modelZ, modelY, modelX);
                case SOUTH -> new Vector3f(16.0F - modelX, modelY, 16.0F - modelZ);
                case WEST -> new Vector3f(modelZ, modelY, 16.0F - modelX);
                default -> new Vector3f(modelX, modelY, modelZ);
            };
        }
    }

    private record FaceUv(float u0, float v0, float u1, float v1) {
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

    private record RollerCuboid(
            float minX,
            float minY,
            float minZ,
            float maxX,
            float maxY,
            float maxZ,
            FaceUv north,
            FaceUv south,
            FaceUv west,
            FaceUv east,
            FaceUv up,
            FaceUv down
    ) {
        private boolean isTopRoller() {
            return (minY + maxY) * 0.5F >= TOP_ROLLER_MIN_CENTER_Y;
        }
    }
}
