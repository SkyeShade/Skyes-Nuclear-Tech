package com.skyeshade.skyent.client.debug;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.skyeshade.skyent.network.RadiationRayBatchPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public final class RadiationRayDebugClient {
    private static final int RAY_LIFETIME_TICKS = 30;
    private static final int MAX_ACTIVE_RAYS = 2048;
    private static final float RAY_HALF_WIDTH = 0.025F;
    private static final double FULL_VISUAL_STRENGTH = 50.0D;
    private static final RenderType RAY_RENDER_TYPE = RenderType.create(
            "skyent_radiation_ray_quads",
            DefaultVertexFormat.POSITION_COLOR_LIGHTMAP,
            VertexFormat.Mode.QUADS,
            1024,
            RenderType.CompositeState.builder()
                    .setShaderState(RenderStateShard.RENDERTYPE_LEASH_SHADER)
                    .setTextureState(RenderStateShard.NO_TEXTURE)
                    .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                    .setCullState(RenderStateShard.NO_CULL)
                    .setLightmapState(RenderStateShard.LIGHTMAP)
                    .createCompositeState(false)
    );
    private static final List<DebugRadiationRay> ACTIVE_RAYS = new ArrayList<>();
    private static boolean enabled;

    private RadiationRayDebugClient() {
    }

    public static void setEnabled(boolean enabled) {
        RadiationRayDebugClient.enabled = enabled;
        if (!enabled) {
            ACTIVE_RAYS.clear();
        }
    }

    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null) {
            ACTIVE_RAYS.clear();
            return;
        }

        removeExpiredRays(level.getGameTime());
    }

    public static void addRays(RadiationRayBatchPayload payload) {
        if (!enabled || payload.rays().isEmpty()) {
            return;
        }

        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }

        long gameTime = level.getGameTime();
        for (RadiationRayBatchPayload.Ray ray : payload.rays()) {
            if (ACTIVE_RAYS.size() >= MAX_ACTIVE_RAYS) {
                ACTIVE_RAYS.remove(0);
            }

            float strengthFactor = (float) Mth.clamp(ray.strength() / FULL_VISUAL_STRENGTH, 0.0D, 1.0D);
            ACTIVE_RAYS.add(new DebugRadiationRay(
                    ray.start(),
                    ray.end(),
                    gameTime,
                    RAY_LIFETIME_TICKS,
                    alpha(strengthFactor, ray.blocked(), ray.validTarget(), ray.convertibleHits(), ray.convertedCount()),
                    ray.convertedCount() > 0 ? RAY_HALF_WIDTH * 1.5F : RAY_HALF_WIDTH,
                    color(strengthFactor, ray.blocked(), ray.validTarget(), ray.convertibleHits(), ray.convertedCount())
            ));
        }
    }

    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (!enabled || ACTIVE_RAYS.isEmpty() || event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null) {
            return;
        }

        long gameTime = level.getGameTime();
        float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        Vec3 cameraPosition = event.getCamera().getPosition();
        PoseStack poseStack = event.getPoseStack();
        MultiBufferSource.BufferSource bufferSource = minecraft.renderBuffers().bufferSource();
        VertexConsumer buffer = bufferSource.getBuffer(RAY_RENDER_TYPE);

        poseStack.pushPose();
        poseStack.translate(-cameraPosition.x, -cameraPosition.y, -cameraPosition.z);
        Matrix4f pose = poseStack.last().pose();

        for (DebugRadiationRay ray : ACTIVE_RAYS) {
            float alpha = ray.alphaAt(gameTime, partialTick);
            if (alpha > 0.0F) {
                drawRay(buffer, pose, cameraPosition, ray, alpha);
            }
        }

        poseStack.popPose();
        bufferSource.endBatch(RAY_RENDER_TYPE);
    }

    private static float alpha(float strengthFactor, boolean blocked, boolean validTarget, int convertibleHits, int convertedCount) {
        if (convertedCount > 0) {
            return 0.85F;
        }

        if (blocked) {
            return 0.35F;
        }

        if (!validTarget) {
            return 0.25F;
        }

        if (convertibleHits <= 0) {
            return 0.3F;
        }

        return Mth.lerp(strengthFactor, 0.35F, 0.65F);
    }

    private static int color(float strengthFactor, boolean blocked, boolean validTarget, int convertibleHits, int convertedCount) {
        if (blocked) {
            return 90 << 16 | 140 << 8 | 90;
        }

        if (!validTarget) {
            return 70 << 16 | 150 << 8 | 70;
        }

        if (convertibleHits <= 0) {
            return 95 << 16 | 180 << 8 | 80;
        }

        int red = convertedCount > 0 ? 255 : Math.round(Mth.lerp(strengthFactor, 120.0F, 255.0F));
        int green = convertedCount > 0 ? 170 : Math.round(Mth.lerp(strengthFactor, 255.0F, 245.0F));
        int blue = convertedCount > 0 ? 40 : Math.round(Mth.lerp(strengthFactor, 30.0F, 70.0F));
        return red << 16 | green << 8 | blue;
    }

    private static void removeExpiredRays(long gameTime) {
        Iterator<DebugRadiationRay> iterator = ACTIVE_RAYS.iterator();
        while (iterator.hasNext()) {
            if (iterator.next().isExpired(gameTime)) {
                iterator.remove();
            }
        }
    }

    private static void drawRay(VertexConsumer buffer, Matrix4f pose, Vec3 cameraPosition, DebugRadiationRay ray, float alpha) {
        Vector3f start = toVector(ray.start());
        Vector3f end = toVector(ray.end());
        Vector3f direction = new Vector3f(end).sub(start);
        if (direction.lengthSquared() <= 0.0001F) {
            return;
        }

        direction.normalize();
        Vec3 midpoint = ray.start().add(ray.end()).scale(0.5D);
        Vector3f viewDirection = new Vector3f(
                (float) (cameraPosition.x - midpoint.x),
                (float) (cameraPosition.y - midpoint.y),
                (float) (cameraPosition.z - midpoint.z)
        );
        if (viewDirection.lengthSquared() <= 0.0001F) {
            viewDirection.set(0.0F, 1.0F, 0.0F);
        } else {
            viewDirection.normalize();
        }

        Vector3f side = new Vector3f(direction).cross(viewDirection);
        if (side.lengthSquared() <= 0.0001F) {
            Vector3f fallback = Math.abs(direction.y()) > 0.95F ? new Vector3f(1.0F, 0.0F, 0.0F) : new Vector3f(0.0F, 1.0F, 0.0F);
            side = new Vector3f(direction).cross(fallback);
        }
        side.normalize().mul(ray.width());

        Vector3f startLeft = new Vector3f(start).sub(side);
        Vector3f startRight = new Vector3f(start).add(side);
        Vector3f endRight = new Vector3f(end).add(side);
        Vector3f endLeft = new Vector3f(end).sub(side);
        int red = ray.red();
        int green = ray.green();
        int blue = ray.blue();

        addVertex(buffer, pose, startLeft, red, green, blue, alpha);
        addVertex(buffer, pose, startRight, red, green, blue, alpha);
        addVertex(buffer, pose, endRight, red, green, blue, alpha);
        addVertex(buffer, pose, endLeft, red, green, blue, alpha);
    }

    private static Vector3f toVector(Vec3 position) {
        return new Vector3f((float) position.x, (float) position.y, (float) position.z);
    }

    private static void addVertex(VertexConsumer buffer, Matrix4f pose, Vector3f position, int red, int green, int blue, float alpha) {
        buffer.addVertex(pose, position.x(), position.y(), position.z())
                .setColor(red, green, blue, Math.round(alpha * 255.0F))
                .setLight(LightTexture.FULL_BRIGHT);
    }

    private record DebugRadiationRay(Vec3 start, Vec3 end, long spawnTick, int lifetimeTicks, float alpha, float width, int color) {
        private boolean isExpired(long gameTime) {
            return gameTime - spawnTick >= lifetimeTicks;
        }

        private float alphaAt(long gameTime, float partialTick) {
            float age = (float) (gameTime - spawnTick) + partialTick;
            float progress = Mth.clamp(age / lifetimeTicks, 0.0F, 1.0F);
            return alpha * (1.0F - progress);
        }

        private int red() {
            return color >> 16 & 0xFF;
        }

        private int green() {
            return color >> 8 & 0xFF;
        }

        private int blue() {
            return color & 0xFF;
        }
    }
}
