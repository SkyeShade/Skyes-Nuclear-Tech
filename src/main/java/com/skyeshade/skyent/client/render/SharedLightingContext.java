package com.skyeshade.skyent.client.render;

import com.skyeshade.skyent.client.model.SharedLightingBakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;

public record SharedLightingContext(SharedLightingBakedModel model, BlockAndTintGetter level, BlockState state, BlockPos pos) {
}
