package com.skyeshade.skyent.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.skyeshade.skyent.client.model.SharedLightingBakedModel;
import com.skyeshade.skyent.client.render.HeatingChamberRenderDebug;
import com.skyeshade.skyent.client.render.SharedLightingContext;
import com.skyeshade.skyent.registry.ModBlocks;
import java.util.Arrays;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

@Mixin(ModelBlockRenderer.class)
public class MixinModelBlockRenderer {
    private static final ThreadLocal<SharedLightingContext> SKYENT_SHARED_LIGHTING = new ThreadLocal<>();
    private static final ThreadLocal<String> SKYENT_RENDER_PATH = new ThreadLocal<>();

    @Inject(
            method = "tesselateBlock(Lnet/minecraft/world/level/BlockAndTintGetter;Lnet/minecraft/client/resources/model/BakedModel;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;ZLnet/minecraft/util/RandomSource;JILnet/neoforged/neoforge/client/model/data/ModelData;Lnet/minecraft/client/renderer/RenderType;)V",
            at = @At("HEAD")
    )
    private void skyent$debugTesselateBlock(BlockAndTintGetter level, BakedModel model, BlockState state, BlockPos pos, PoseStack poseStack, VertexConsumer consumer, boolean checkSides, net.minecraft.util.RandomSource random, long seed, int packedOverlay, net.neoforged.neoforge.client.model.data.ModelData modelData, net.minecraft.client.renderer.RenderType renderType, CallbackInfo callbackInfo) {
        if (state.is(ModBlocks.HEATING_CHAMBER.get())) {
            HeatingChamberRenderDebug.logBlock(
                    pos,
                    state,
                    "tesselateBlock modelClass={} sharedModel={} debugForce={} ambientOcclusionNoArg={} ambientOcclusionState={} renderType={} modelData={}",
                    model.getClass().getName(),
                    model instanceof SharedLightingBakedModel,
                    model instanceof SharedLightingBakedModel sharedLightingModel && sharedLightingModel.skyent$debugForceWhiteFullbright(),
                    model.useAmbientOcclusion(),
                    model.useAmbientOcclusion(state, modelData, renderType),
                    renderType,
                    modelData.getClass().getName()
            );
        }
    }

    @Inject(
            method = "tesselateWithoutAO(Lnet/minecraft/world/level/BlockAndTintGetter;Lnet/minecraft/client/resources/model/BakedModel;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;ZLnet/minecraft/util/RandomSource;JILnet/neoforged/neoforge/client/model/data/ModelData;Lnet/minecraft/client/renderer/RenderType;)V",
            at = @At("HEAD")
    )
    private void skyent$beginSharedLighting(BlockAndTintGetter level, BakedModel model, BlockState state, BlockPos pos, com.mojang.blaze3d.vertex.PoseStack poseStack, com.mojang.blaze3d.vertex.VertexConsumer consumer, boolean checkSides, net.minecraft.util.RandomSource random, long seed, int packedOverlay, net.neoforged.neoforge.client.model.data.ModelData modelData, net.minecraft.client.renderer.RenderType renderType, CallbackInfo callbackInfo) {
        skyent$setSharedLightingContext("non_ao", level, model, state, pos);
    }

    @Inject(
            method = "tesselateWithAO(Lnet/minecraft/world/level/BlockAndTintGetter;Lnet/minecraft/client/resources/model/BakedModel;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;ZLnet/minecraft/util/RandomSource;JILnet/neoforged/neoforge/client/model/data/ModelData;Lnet/minecraft/client/renderer/RenderType;)V",
            at = @At("HEAD")
    )
    private void skyent$beginSharedLightingWithAo(BlockAndTintGetter level, BakedModel model, BlockState state, BlockPos pos, com.mojang.blaze3d.vertex.PoseStack poseStack, com.mojang.blaze3d.vertex.VertexConsumer consumer, boolean checkSides, net.minecraft.util.RandomSource random, long seed, int packedOverlay, net.neoforged.neoforge.client.model.data.ModelData modelData, net.minecraft.client.renderer.RenderType renderType, CallbackInfo callbackInfo) {
        skyent$setSharedLightingContext("ao", level, model, state, pos);
    }

    private static void skyent$setSharedLightingContext(String path, BlockAndTintGetter level, BakedModel model, BlockState state, BlockPos pos) {
        if (model instanceof SharedLightingBakedModel sharedLightingModel && sharedLightingModel.skyent$usesSharedLighting()) {
            SKYENT_SHARED_LIGHTING.set(new SharedLightingContext(sharedLightingModel, level, state, pos));
            SKYENT_RENDER_PATH.set(path);
            skyent$debug(state, pos, "begin path={} modelClass={} sharedModel=true description={}", path, model.getClass().getName(), sharedLightingModel.skyent$getDebugDescription());
            return;
        }
        skyent$debug(state, pos, "begin path={} modelClass={} sharedModel=false", path, model.getClass().getName());
        SKYENT_SHARED_LIGHTING.remove();
        SKYENT_RENDER_PATH.remove();
    }

    @Inject(
            method = "tesselateWithoutAO(Lnet/minecraft/world/level/BlockAndTintGetter;Lnet/minecraft/client/resources/model/BakedModel;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;ZLnet/minecraft/util/RandomSource;JILnet/neoforged/neoforge/client/model/data/ModelData;Lnet/minecraft/client/renderer/RenderType;)V",
            at = @At("RETURN")
    )
    private void skyent$endSharedLighting(BlockAndTintGetter level, BakedModel model, BlockState state, BlockPos pos, com.mojang.blaze3d.vertex.PoseStack poseStack, com.mojang.blaze3d.vertex.VertexConsumer consumer, boolean checkSides, net.minecraft.util.RandomSource random, long seed, int packedOverlay, net.neoforged.neoforge.client.model.data.ModelData modelData, net.minecraft.client.renderer.RenderType renderType, CallbackInfo callbackInfo) {
        SKYENT_SHARED_LIGHTING.remove();
        SKYENT_RENDER_PATH.remove();
    }

    @Inject(
            method = "tesselateWithAO(Lnet/minecraft/world/level/BlockAndTintGetter;Lnet/minecraft/client/resources/model/BakedModel;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;ZLnet/minecraft/util/RandomSource;JILnet/neoforged/neoforge/client/model/data/ModelData;Lnet/minecraft/client/renderer/RenderType;)V",
            at = @At("RETURN")
    )
    private void skyent$endSharedLightingWithAo(BlockAndTintGetter level, BakedModel model, BlockState state, BlockPos pos, com.mojang.blaze3d.vertex.PoseStack poseStack, com.mojang.blaze3d.vertex.VertexConsumer consumer, boolean checkSides, net.minecraft.util.RandomSource random, long seed, int packedOverlay, net.neoforged.neoforge.client.model.data.ModelData modelData, net.minecraft.client.renderer.RenderType renderType, CallbackInfo callbackInfo) {
        SKYENT_SHARED_LIGHTING.remove();
        SKYENT_RENDER_PATH.remove();
    }

    @ModifyArgs(
            method = "renderModelFaceFlat",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/block/ModelBlockRenderer;putQuadData(Lnet/minecraft/world/level/BlockAndTintGetter;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;Lcom/mojang/blaze3d/vertex/VertexConsumer;Lcom/mojang/blaze3d/vertex/PoseStack$Pose;Lnet/minecraft/client/renderer/block/model/BakedQuad;FFFFIIIII)V")
    )
    private void skyent$useSharedLight(Args args) {
        skyent$applySharedLight(args);
    }

    @ModifyArgs(
            method = "renderModelFaceAO",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/block/ModelBlockRenderer;putQuadData(Lnet/minecraft/world/level/BlockAndTintGetter;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;Lcom/mojang/blaze3d/vertex/VertexConsumer;Lcom/mojang/blaze3d/vertex/PoseStack$Pose;Lnet/minecraft/client/renderer/block/model/BakedQuad;FFFFIIIII)V")
    )
    private void skyent$useSharedLightWithAo(Args args) {
        skyent$applySharedLight(args);
    }

    private static void skyent$applySharedLight(Args args) {
        SharedLightingContext context = SKYENT_SHARED_LIGHTING.get();
        if (context == null) {
            return;
        }

        BakedQuad quad = args.get(5);
        int sharedLight = context.model().skyent$getSharedLight(context.level(), context.state(), context.pos(), quad.getDirection());
        if (sharedLight == SharedLightingBakedModel.NO_SHARED_LIGHT) {
            return;
        }

        if (context.model().skyent$ignoresNeighborShading()) {
            args.set(6, 1.0F);
            args.set(7, 1.0F);
            args.set(8, 1.0F);
            args.set(9, 1.0F);
        }
        skyent$debug(context.state(), context.pos(), "putQuadData path={} quadDirection={} oldBrightness=[{},{},{},{}] oldLight=[{},{},{},{}] sharedLight={}",
                SKYENT_RENDER_PATH.get(),
                quad.getDirection(),
                args.get(6), args.get(7), args.get(8), args.get(9),
                args.get(10), args.get(11), args.get(12), args.get(13),
                sharedLight);
        args.set(10, sharedLight);
        args.set(11, sharedLight);
        args.set(12, sharedLight);
        args.set(13, sharedLight);
    }

    @ModifyArgs(
            method = "putQuadData",
            at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/VertexConsumer;putBulkData(Lcom/mojang/blaze3d/vertex/PoseStack$Pose;Lnet/minecraft/client/renderer/block/model/BakedQuad;[FFFFF[IIZ)V")
    )
    private void skyent$debugOrForceFinalBulkData(Args args) {
        SharedLightingContext context = SKYENT_SHARED_LIGHTING.get();
        if (context == null) {
            return;
        }

        BakedQuad quad = args.get(1);
        float[] brightness = args.get(2);
        int[] lightmap = args.get(7);
        skyent$debug(context.state(), context.pos(), "putBulkData path={} quadDirection={} brightness={} rgb=[{},{},{}] lightmap={} forceWhite={}",
                SKYENT_RENDER_PATH.get(),
                quad.getDirection(),
                Arrays.toString(brightness),
                args.get(3), args.get(4), args.get(5),
                Arrays.toString(lightmap),
                context.model().skyent$debugForceWhiteFullbright());

        if (!context.model().skyent$debugForceWhiteFullbright()) {
            return;
        }

        args.set(2, new float[]{1.0F, 1.0F, 1.0F, 1.0F});
        args.set(3, 1.0F);
        args.set(4, 0.0F);
        args.set(5, 1.0F);
        args.set(7, new int[]{HeatingChamberRenderDebug.FULLBRIGHT_MAGENTA, HeatingChamberRenderDebug.FULLBRIGHT_MAGENTA, HeatingChamberRenderDebug.FULLBRIGHT_MAGENTA, HeatingChamberRenderDebug.FULLBRIGHT_MAGENTA});
    }

    private static void skyent$debug(BlockState state, BlockPos pos, String message, Object... args) {
        if (state.is(ModBlocks.HEATING_CHAMBER.get())) {
            HeatingChamberRenderDebug.logBlock(pos, state, message, args);
        }
    }
}
