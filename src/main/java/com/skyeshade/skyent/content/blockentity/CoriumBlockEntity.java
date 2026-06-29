package com.skyeshade.skyent.content.blockentity;

import com.skyeshade.skyent.content.radiation.RadiationConstants;
import com.skyeshade.skyent.content.radiation.RadiationUtil;
import com.skyeshade.skyent.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class CoriumBlockEntity extends BlockEntity {
    public static final int RADIATION_INTERVAL_TICKS = 20;
    public static final int RADIATION_ATTEMPTS_PER_RUN = 16;
    public static final int MAX_CONVERSIONS_PER_RUN = 8;

    private int radiationTickCounter;

    public CoriumBlockEntity(BlockPos pos, BlockState blockState) {
        super(ModBlockEntities.CORIUM_BLOCK.get(), pos, blockState);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, CoriumBlockEntity corium) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }

        corium.radiationTickCounter++;
        if (corium.radiationTickCounter < RADIATION_INTERVAL_TICKS) {
            return;
        }

        corium.radiationTickCounter = 0;
        RadiationUtil.applyFullEnvironmentalRadiation(
                serverLevel,
                pos,
                RadiationConstants.CORIUM_BLOCK_RADIATION_STRENGTH,
                RadiationConstants.CORIUM_BLOCK_RADIATION_RANGE,
                RADIATION_ATTEMPTS_PER_RUN,
                MAX_CONVERSIONS_PER_RUN,
                serverLevel.random
        );
    }
}
