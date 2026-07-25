package com.skyeshade.skyent.content.blockentity;

import com.skyeshade.skyent.content.radiation.RadioactiveSourceRegistry;
import com.skyeshade.skyent.event.systems.RadiationSourceTickSystem;
import com.skyeshade.skyent.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class CoriumBlockEntity extends BlockEntity {
    public CoriumBlockEntity(BlockPos pos, BlockState blockState) {
        super(ModBlockEntities.CORIUM_BLOCK.get(), pos, blockState);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, CoriumBlockEntity corium) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }

        RadioactiveSourceRegistry.register(serverLevel, pos);
        RadiationSourceTickSystem.registerActiveSourceIfNeeded(serverLevel, pos, state);
    }
}
