package com.skyeshade.skyent.mixin.client;

import com.skyeshade.skyent.client.render.MeshDestroyProgress;
import com.skyeshade.skyent.content.block.LargeSteamTurbineBlock;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.BlockDestructionProgress;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;

import java.util.SortedSet;

@Mixin(LevelRenderer.class)
public abstract class MeshLevelRendererMixin implements MeshDestroyProgress {
    @Shadow
    private ClientLevel level;
    @Shadow
    @Final
    private Long2ObjectMap<SortedSet<BlockDestructionProgress>> destructionProgress;

    // Same part-to-controller convention as the existing Skyent interaction mixins. At the receiving
    // boundary it also covers remote miners and normal server destroy-progress packets.
    @ModifyVariable(method = "destroyBlockProgress", at = @At("HEAD"), argsOnly = true)
    private BlockPos skyent$meshControllerProgress(BlockPos pos) {
        return level == null ? pos : LargeSteamTurbineBlock.resolveDestroyProgressPos(level, pos);
    }

    @Override
    public int skyent$meshDestroyStage(BlockPos controller) {
        var stages = destructionProgress.get(controller.asLong());
        return stages == null || stages.isEmpty() ? -1 : stages.last().getProgress();
    }
}
