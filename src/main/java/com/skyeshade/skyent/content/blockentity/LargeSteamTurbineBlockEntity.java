package com.skyeshade.skyent.content.blockentity;

import com.skyeshade.skyent.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Placement/render anchor only. No tick, inventory, network state, power, steam balance or GUI yet.
 */
public final class LargeSteamTurbineBlockEntity extends BlockEntity {
    public LargeSteamTurbineBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.LARGE_STEAM_TURBINE.get(), pos, state);
    }
}
