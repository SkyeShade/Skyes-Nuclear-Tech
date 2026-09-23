package com.skyeshade.skyent.mixin.client;

import com.mojang.blaze3d.vertex.*;
import com.skyeshade.skyent.client.renderer.blockentity.LargeSteamTurbineRenderer;
import com.skyeshade.skyent.content.block.LargeSteamTurbineBlock;
import com.skyeshade.skyent.registry.ModBlocks;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Opt the ENTITYBLOCK_ANIMATED mesh into the same destroy pass used by Skyent's block models.
 * The engine owns stage selection, projection, depth bias, draw ordering and cleanup.
 */
@Mixin(BlockRenderDispatcher.class)
public abstract class MeshBlockRenderDispatcherMixin {
    private static final float[] SKYENT_MESH_BRIGHTNESS = {1, 1, 1, 1};
    private static final int[] SKYENT_MESH_LIGHT = {0, 0, 0, 0};

    @Inject(method = "renderBreakingTexture(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/BlockAndTintGetter;Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;Lnet/neoforged/neoforge/client/model/data/ModelData;)V",
            at = @At("HEAD"), cancellable = true)
    private void skyent$meshDestroyTexture(BlockState state, BlockPos pos, BlockAndTintGetter level, PoseStack pose,
                                           VertexConsumer consumer, ModelData data, CallbackInfo ci) {
        if (!state.is(ModBlocks.LARGE_STEAM_TURBINE.get())) return;
        var model = LargeSteamTurbineRenderer.model();
        if (model != null) for (var quad : model.quads(state.getValue(LargeSteamTurbineBlock.FACING)))
            consumer.putBulkData(pose.last(), quad, SKYENT_MESH_BRIGHTNESS, 1, 1, 1, 1, SKYENT_MESH_LIGHT, OverlayTexture.NO_OVERLAY, false);
        ci.cancel();
    }
}
