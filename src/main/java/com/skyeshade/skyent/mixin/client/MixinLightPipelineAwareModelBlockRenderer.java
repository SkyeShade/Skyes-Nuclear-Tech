package com.skyeshade.skyent.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.skyeshade.skyent.client.model.SharedLightingBakedModel;
import com.skyeshade.skyent.client.render.HeatingChamberRenderDebug;
import com.skyeshade.skyent.registry.ModBlocks;
import java.util.Arrays;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.color.block.BlockColors;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.client.model.lighting.LightPipelineAwareModelBlockRenderer;
import net.neoforged.neoforge.client.model.lighting.QuadLighter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = LightPipelineAwareModelBlockRenderer.class, remap = false)
public class MixinLightPipelineAwareModelBlockRenderer {
    private static final Direction[] SKYENT_DIRECTIONS = Direction.values();
    private static final float[] SKYENT_FULL_BRIGHTNESS = new float[]{1.0F, 1.0F, 1.0F, 1.0F};

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private static void skyent$renderWithSharedLighting(VertexConsumer vertexConsumer, QuadLighter lighter, BlockAndTintGetter level, BakedModel model, BlockState state, BlockPos pos, PoseStack poseStack, boolean checkSides, RandomSource rand, long seed, int packedOverlay, ModelData modelData, RenderType renderType, CallbackInfoReturnable<Boolean> callbackInfo) {
        if (!(model instanceof SharedLightingBakedModel sharedLightingModel) || !sharedLightingModel.skyent$usesSharedLighting()) {
            skyent$debug(state, pos, "light_pipeline_begin modelClass={} sharedModel=false", model.getClass().getName());
            return;
        }
        skyent$debug(state, pos, "light_pipeline_begin modelClass={} sharedModel=true description={}", model.getClass().getName(), sharedLightingModel.skyent$getDebugDescription());

        PoseStack.Pose pose = poseStack.last();
        boolean rendered = false;

        rand.setSeed(seed);
        rendered |= skyent$renderQuads(vertexConsumer, pose, sharedLightingModel, level, state, pos, model.getQuads(state, null, rand, modelData, renderType), packedOverlay);

        for (Direction side : SKYENT_DIRECTIONS) {
            if (checkSides && !Block.shouldRenderFace(state, level, pos, side, pos.relative(side))) {
                continue;
            }
            rand.setSeed(seed);
            rendered |= skyent$renderQuads(vertexConsumer, pose, sharedLightingModel, level, state, pos, model.getQuads(state, side, rand, modelData, renderType), packedOverlay);
        }

        callbackInfo.setReturnValue(rendered);
    }

    private static boolean skyent$renderQuads(VertexConsumer consumer, PoseStack.Pose pose, SharedLightingBakedModel model, BlockAndTintGetter level, BlockState state, BlockPos pos, List<BakedQuad> quads, int packedOverlay) {
        if (quads.isEmpty()) {
            return false;
        }

        BlockColors blockColors = Minecraft.getInstance().getBlockColors();
        int[] lightmap = new int[4];
        for (BakedQuad quad : quads) {
            int sharedLight = model.skyent$getSharedLight(level, state, pos, quad.getDirection());
            if (sharedLight == SharedLightingBakedModel.NO_SHARED_LIGHT) {
                continue;
            }
            if (model.skyent$debugForceWhiteFullbright()) {
                sharedLight = HeatingChamberRenderDebug.FULLBRIGHT_MAGENTA;
            }
            lightmap[0] = sharedLight;
            lightmap[1] = sharedLight;
            lightmap[2] = sharedLight;
            lightmap[3] = sharedLight;

            float[] brightness = model.skyent$ignoresNeighborShading()
                    ? SKYENT_FULL_BRIGHTNESS
                    : new float[]{
                    level.getShade(quad.getDirection(), quad.isShade()),
                    level.getShade(quad.getDirection(), quad.isShade()),
                    level.getShade(quad.getDirection(), quad.isShade()),
                    level.getShade(quad.getDirection(), quad.isShade())
            };

            float red = 1.0F;
            float green = 1.0F;
            float blue = 1.0F;
            if (quad.isTinted()) {
                int color = blockColors.getColor(state, level, pos, quad.getTintIndex());
                red = (float) (color >> 16 & 0xFF) / 255.0F;
                green = (float) (color >> 8 & 0xFF) / 255.0F;
                blue = (float) (color & 0xFF) / 255.0F;
            }
            if (model.skyent$debugForceWhiteFullbright()) {
                red = 1.0F;
                green = 0.0F;
                blue = 1.0F;
            }

            skyent$debug(state, pos, "light_pipeline_emit quadDirection={} brightness={} rgb=[{},{},{}] lightmap={} forceWhite={}",
                    quad.getDirection(),
                    Arrays.toString(brightness),
                    red, green, blue,
                    Arrays.toString(lightmap),
                    model.skyent$debugForceWhiteFullbright());

            consumer.putBulkData(pose, quad, brightness, red, green, blue, 1.0F, lightmap, packedOverlay, true);
        }
        return true;
    }

    private static void skyent$debug(BlockState state, BlockPos pos, String message, Object... args) {
        if (state.is(ModBlocks.HEATING_CHAMBER.get())) {
            HeatingChamberRenderDebug.logBlock(pos, state, message, args);
        }
    }
}
