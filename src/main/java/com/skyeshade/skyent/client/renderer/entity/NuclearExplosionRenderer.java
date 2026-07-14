package com.skyeshade.skyent.client.renderer.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.skyeshade.skyent.SkyesNuclearTech;
import com.skyeshade.skyent.content.entity.NuclearExplosionEntity;
import com.skyeshade.skyent.content.entity.NuclearMushroomCloudSimulation;
import com.skyeshade.skyent.content.entity.NuclearExplosionEntity.NuclearCloudlet;
import com.skyeshade.skyent.content.entity.NuclearExplosionEntity.NuclearCloudletType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

import java.util.HashMap;
import java.util.Map;

public class NuclearExplosionRenderer extends EntityRenderer<NuclearExplosionEntity> {
    private static final int RAY_COUNT = 240;
    private static final boolean DEBUG_SHOCKWAVE_VISUALS = Boolean.getBoolean("skyent.debugNukeShockwave");
    private static final Map<Integer, Integer> LAST_DEBUG_RENDER_TICK = new HashMap<>();
    private static final ResourceLocation CLOUDLET_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            SkyesNuclearTech.MOD_ID,
            "textures/particle/particle_base.png"
    );
    private static final float HALF_SQRT_3 = 0.8660254F;

    public NuclearExplosionRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(NuclearExplosionEntity entity, float entityYaw, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {
        float age = entity.tickCount + partialTick;
        if (age < NuclearExplosionEntity.RAY_TOTAL_TICKS) {
            renderRays(entity, age, poseStack, bufferSource);
        }
        renderMushroomCloudlets(entity, partialTick, poseStack, bufferSource);
        renderShockwaveCloudlets(entity, partialTick, poseStack, bufferSource);
        super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
    }

    private static void renderRays(NuclearExplosionEntity entity, float age, PoseStack poseStack, MultiBufferSource bufferSource) {
        float growProgress = Mth.clamp(age / NuclearExplosionEntity.RAY_GROW_TICKS, 0.0F, 1.0F);
        float easedGrow = 1.0F - (1.0F - growProgress) * (1.0F - growProgress);
        float fadeProgress = age < NuclearExplosionEntity.RAY_GROW_TICKS ? 0.0F : Mth.clamp((age - NuclearExplosionEntity.RAY_GROW_TICKS) / NuclearExplosionEntity.RAY_FADE_TICKS, 0.0F, 1.0F);
        float scale = (age < NuclearExplosionEntity.RAY_GROW_TICKS ? easedGrow : 1.0F) * NuclearExplosionEntity.RAY_SCALE;
        float alpha = 1.0F - fadeProgress;
        if (scale <= 0.0F || alpha <= 0.0F) {
            return;
        }

        poseStack.pushPose();
        poseStack.scale(scale, scale, scale);
        VertexConsumer rays = bufferSource.getBuffer(RenderType.dragonRays());
        VertexConsumer raysDepth = bufferSource.getBuffer(RenderType.dragonRaysDepth());
        RandomSource random = RandomSource.create(entity.getVisualSeed());

        for (int index = 0; index < RAY_COUNT; index++) {
            poseStack.pushPose();
            poseStack.mulPose(Axis.XP.rotationDegrees(random.nextFloat() * 360.0F));
            poseStack.mulPose(Axis.YP.rotationDegrees(random.nextFloat() * 360.0F));
            poseStack.mulPose(Axis.ZP.rotationDegrees(random.nextFloat() * 360.0F));
            poseStack.mulPose(new Quaternionf().rotateXYZ(
                    random.nextFloat() * Mth.TWO_PI,
                    random.nextFloat() * Mth.TWO_PI,
                    random.nextFloat() * Mth.TWO_PI
            ));
            float length = 0.75F + random.nextFloat() * 2.35F;
            float width = 0.15F + random.nextFloat() * 0.45F;
            Matrix4f pose = poseStack.last().pose();
            drawDragonRay(rays, pose, length, width, 255, 255, 255, Math.round(alpha * 255.0F));
            drawDragonRay(raysDepth, pose, length, width, 255, 245, 220, Math.round(alpha * 180.0F));
            poseStack.popPose();
        }

        poseStack.popPose();
    }

    private static void drawDragonRay(VertexConsumer buffer, Matrix4f pose, float length, float width, int red, int green, int blue, int alpha) {
        vertex(buffer, pose, 0.0F, 0.0F, 0.0F, red, green, blue, alpha);
        vertex(buffer, pose, -HALF_SQRT_3 * width, length, -0.5F * width, red, green, blue, 0);
        vertex(buffer, pose, HALF_SQRT_3 * width, length, -0.5F * width, red, green, blue, 0);

        vertex(buffer, pose, 0.0F, 0.0F, 0.0F, red, green, blue, alpha);
        vertex(buffer, pose, HALF_SQRT_3 * width, length, -0.5F * width, red, green, blue, 0);
        vertex(buffer, pose, 0.0F, length, width, red, green, blue, 0);

        vertex(buffer, pose, 0.0F, 0.0F, 0.0F, red, green, blue, alpha);
        vertex(buffer, pose, 0.0F, length, width, red, green, blue, 0);
        vertex(buffer, pose, -HALF_SQRT_3 * width, length, -0.5F * width, red, green, blue, 0);
    }

    private static void renderMushroomCloudlets(NuclearExplosionEntity entity, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource) {
        if (entity.getMushroomCloudlets().isEmpty()) {
            return;
        }

        VertexConsumer buffer = bufferSource.getBuffer(RenderType.entityTranslucent(CLOUDLET_TEXTURE));
        Quaternionf cameraRotation = Minecraft.getInstance().getEntityRenderDispatcher().cameraOrientation();
        for (NuclearMushroomCloudSimulation.MushroomCloudlet cloudlet : entity.getMushroomCloudlets()) {
            renderCloudlet(buffer, poseStack, cameraRotation, cloudlet, partialTick);
        }
    }

    private static void renderShockwaveCloudlets(NuclearExplosionEntity entity, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource) {
        if (entity.getCloudlets().isEmpty()) {
            return;
        }

        VertexConsumer buffer = bufferSource.getBuffer(RenderType.entityTranslucent(CLOUDLET_TEXTURE));
        Quaternionf cameraRotation = Minecraft.getInstance().getEntityRenderDispatcher().cameraOrientation();
        int shockwaveCount = 0;
        int renderedShockwaveCount = 0;
        for (NuclearCloudlet cloudlet : entity.getCloudlets()) {
            if (cloudlet.type() == NuclearCloudletType.SHOCKWAVE) {
                shockwaveCount++;
            }

            boolean rendered = renderCloudlet(buffer, poseStack, cameraRotation, cloudlet, partialTick);
            if (rendered && cloudlet.type() == NuclearCloudletType.SHOCKWAVE) {
                renderedShockwaveCount++;
            }
        }

        logRenderDebug(entity, shockwaveCount, renderedShockwaveCount);
    }

    private static void logRenderDebug(
            NuclearExplosionEntity entity,
            int shockwaveCount,
            int renderedShockwaveCount
    ) {
        if (!DEBUG_SHOCKWAVE_VISUALS || entity.tickCount % 20 != 0) {
            return;
        }

        Integer lastTick = LAST_DEBUG_RENDER_TICK.get(entity.getId());
        if (lastTick != null && lastTick == entity.tickCount) {
            return;
        }
        LAST_DEBUG_RENDER_TICK.put(entity.getId(), entity.tickCount);

        SkyesNuclearTech.LOGGER.info(
                "Nuke shockwave renderer debug: id={} tick={} total={} mushroom={} shockwave={} renderedShockwave={}",
                entity.getId(),
                entity.tickCount,
                entity.getCloudlets().size(),
                entity.getMushroomCloudlets().size(),
                shockwaveCount,
                renderedShockwaveCount
        );
    }

    private static boolean renderCloudlet(
            VertexConsumer buffer,
            PoseStack poseStack,
            Quaternionf cameraRotation,
            NuclearMushroomCloudSimulation.MushroomCloudlet cloudlet,
            float partialTick
    ) {
        float size = cloudlet.size(partialTick) * 0.5F;
        int alpha = Math.round(cloudlet.alpha(partialTick) * 255.0F);
        if (alpha <= 0) {
            return false;
        }

        poseStack.pushPose();
        poseStack.translate(cloudlet.x(partialTick), cloudlet.y(partialTick), cloudlet.z(partialTick));
        poseStack.mulPose(cameraRotation);
        Matrix4f pose = poseStack.last().pose();
        int red = cloudlet.red(partialTick);
        int green = cloudlet.green(partialTick);
        int blue = cloudlet.blue(partialTick);

        cloudletVertex(buffer, pose, -size, -size, 0.0F, 1.0F, red, green, blue, alpha);
        cloudletVertex(buffer, pose, size, -size, 1.0F, 1.0F, red, green, blue, alpha);
        cloudletVertex(buffer, pose, size, size, 1.0F, 0.0F, red, green, blue, alpha);
        cloudletVertex(buffer, pose, -size, size, 0.0F, 0.0F, red, green, blue, alpha);
        poseStack.popPose();
        return true;
    }

    private static boolean renderCloudlet(VertexConsumer buffer, PoseStack poseStack, Quaternionf cameraRotation, NuclearCloudlet cloudlet, float partialTick) {
        float size = cloudlet.size(partialTick) * 0.5F;
        int alpha = Math.round(cloudlet.alpha(partialTick) * 255.0F);
        if (alpha <= 0) {
            return false;
        }

        poseStack.pushPose();
        poseStack.translate(cloudlet.x(partialTick), cloudlet.y(partialTick), cloudlet.z(partialTick));
        poseStack.mulPose(cameraRotation);
        Matrix4f pose = poseStack.last().pose();
        int red = cloudlet.red(partialTick);
        int green = cloudlet.green(partialTick);
        int blue = cloudlet.blue(partialTick);

        cloudletVertex(buffer, pose, -size, -size, 0.0F, 1.0F, red, green, blue, alpha);
        cloudletVertex(buffer, pose, size, -size, 1.0F, 1.0F, red, green, blue, alpha);
        cloudletVertex(buffer, pose, size, size, 1.0F, 0.0F, red, green, blue, alpha);
        cloudletVertex(buffer, pose, -size, size, 0.0F, 0.0F, red, green, blue, alpha);
        poseStack.popPose();
        return true;
    }

    private static void cloudletVertex(VertexConsumer buffer, Matrix4f pose, float x, float y, float u, float v, int red, int green, int blue, int alpha) {
        buffer.addVertex(pose, x, y, 0.0F)
                .setColor(red, green, blue, alpha)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(0xF000F0)
                .setNormal(0.0F, 1.0F, 0.0F);
    }

    private static void vertex(VertexConsumer buffer, Matrix4f pose, float x, float y, float z, int red, int green, int blue, int alpha) {
        buffer.addVertex(pose, x, y, z)
                .setColor(red, green, blue, alpha);
    }

    @Override
    public ResourceLocation getTextureLocation(NuclearExplosionEntity entity) {
        return TextureAtlas.LOCATION_BLOCKS;
    }
}
