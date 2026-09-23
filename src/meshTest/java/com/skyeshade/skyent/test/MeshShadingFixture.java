package com.skyeshade.skyent.test;

import com.mojang.blaze3d.vertex.*;
import com.skyeshade.skyent.content.model.MeshDirectionalShade;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;

import java.util.*;

/**
 * Dev-only white reference: mesh factors, actual existing ScaledBlockModel, actual vanilla block,
 * followed by upward/downward slopes. All six cube normals can be inspected from above and below.
 */
@EventBusSubscriber(modid = "skyent", value = Dist.CLIENT)
public final class MeshShadingFixture {
    public static boolean enabled;
    public static final ModelResourceLocation REFERENCE = ModelResourceLocation.standalone(ResourceLocation.fromNamespaceAndPath("skyent", "block/mesh_shade_reference"));

    @EventBusSubscriber(modid = "skyent", value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
    public static final class Models {
        @SubscribeEvent
        public static void register(ModelEvent.RegisterAdditional event) {
            event.register(REFERENCE);
        }
    }

    @SubscribeEvent
    public static void render(RenderLevelStageEvent event) {
        if (!enabled || event.getStage() != RenderLevelStageEvent.Stage.AFTER_BLOCK_ENTITIES) return;
        var mc = Minecraft.getInstance();
        var pose = event.getPoseStack();
        var camera = event.getCamera().getPosition();
        var buffers = mc.renderBuffers().bufferSource();
        var consumer = buffers.getBuffer(RenderType.cutout());
        int light = LevelRenderer.getLightColor(mc.level, new BlockPos(0, 203, -10));
        int[] lights = {light, light, light, light};
        var vanilla = mc.getBlockRenderer().getBlockModel(Blocks.WHITE_CONCRETE.defaultBlockState());
        var reference = mc.getModelManager().getModel(REFERENCE);
        if (reference == mc.getModelManager().getMissingModel())
            throw new IllegalStateException("Missing Skyent shading reference");
        for (int mode = 0; mode < 2; mode++) {
            pose.pushPose();
            pose.translate(mode * 2 - camera.x, 203 - camera.y, -10 - camera.z);
            var model = mode == 0 ? vanilla : reference;
            var quads = new ArrayList<>(model.getQuads(null, null, RandomSource.create(42)));
            for (Direction d : Direction.values()) quads.addAll(model.getQuads(null, d, RandomSource.create(42)));
            for (var q : quads) {
                float shade = mode == 0 ? MeshDirectionalShade.factor(Vec3.atLowerCornerOf(q.getDirection().getNormal())) : 1;
                consumer.putBulkData(pose.last(), q, new float[]{1, 1, 1, 1}, shade, shade, shade, 1, lights, 0, true);
            }
            pose.popPose();
        }
        var sprite = mc.getTextureAtlas(TextureAtlas.LOCATION_BLOCKS).apply(ResourceLocation.withDefaultNamespace("block/white_concrete"));
        pose.pushPose();
        pose.translate(-camera.x, 203 - camera.y, -7 - camera.z);
        for (int i = 0; i < 4; i++) {
            double angle = Math.toRadians(new double[]{22.5, 45, 67.5, 135}[i]);
            Vec3 a = new Vec3(i * 1.5, 0, 0), b = a.add(0, Math.sin(angle), Math.cos(angle)), c = b.add(1, 0, 0), d = a.add(1, 0, 0);
            Vec3 n = b.subtract(a).cross(c.subtract(a)).normalize();
            float shade = MeshDirectionalShade.factor(n);
            var points = List.of(a, b, c, d);
            for (int v = 0; v < 4; v++) {
                Vec3 p = points.get(v);
                consumer.addVertex(pose.last(), (float) p.x, (float) p.y, (float) p.z).setColor(shade, shade, shade, 1)
                        .setUv(sprite.getU(v >= 2 ? 1 : 0), sprite.getV(v == 1 || v == 2 ? 1 : 0)).setOverlay(0).setLight(light)
                        .setNormal(pose.last(), (float) n.x, (float) n.y, (float) n.z);
            }
        }
        pose.popPose();
    }
}
