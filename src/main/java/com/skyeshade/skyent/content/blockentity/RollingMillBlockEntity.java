package com.skyeshade.skyent.content.blockentity;

import com.skyeshade.skyent.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class RollingMillBlockEntity extends BlockEntity {
    public RollingMillBlockEntity(BlockPos pos, BlockState blockState) {
        super(ModBlockEntities.ROLLING_MILL.get(), pos, blockState);
    }
}
