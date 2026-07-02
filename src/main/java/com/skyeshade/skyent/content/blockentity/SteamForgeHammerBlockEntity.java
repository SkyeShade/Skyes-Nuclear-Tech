package com.skyeshade.skyent.content.blockentity;

import com.skyeshade.skyent.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class SteamForgeHammerBlockEntity extends BlockEntity {
    public SteamForgeHammerBlockEntity(BlockPos pos, BlockState blockState) {
        super(ModBlockEntities.STEAM_FORGE_HAMMER.get(), pos, blockState);
    }
}
