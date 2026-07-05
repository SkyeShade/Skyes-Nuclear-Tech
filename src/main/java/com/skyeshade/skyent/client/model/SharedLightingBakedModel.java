package com.skyeshade.skyent.client.model;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

public interface SharedLightingBakedModel {
    int NO_SHARED_LIGHT = -1;

    boolean skyent$usesSharedLighting();

    int skyent$getSharedLight(BlockAndTintGetter level, BlockState state, BlockPos pos, @Nullable Direction direction);

    default boolean skyent$ignoresNeighborShading() {
        return false;
    }

    default boolean skyent$debugForceWhiteFullbright() {
        return false;
    }

    default String skyent$getDebugDescription() {
        return getClass().getName();
    }
}
