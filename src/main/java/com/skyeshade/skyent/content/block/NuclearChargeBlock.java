package com.skyeshade.skyent.content.block;

import com.skyeshade.skyent.content.explosion.NuclearExplosion;
import com.skyeshade.skyent.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

public class NuclearChargeBlock extends Block {
    public NuclearChargeBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos fromPos, boolean isMoving) {
        if (!level.isClientSide && level.hasNeighborSignal(pos) && level instanceof ServerLevel serverLevel) {
            detonate(serverLevel, pos, null);
            return;
        }

        super.neighborChanged(state, level, pos, block, fromPos, isMoving);
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        if (!level.isClientSide && !state.is(oldState.getBlock()) && level.hasNeighborSignal(pos) && level instanceof ServerLevel serverLevel) {
            detonate(serverLevel, pos, null);
            return;
        }

        super.onPlace(state, level, pos, oldState, movedByPiston);
    }

    public static boolean detonate(ServerLevel level, BlockPos pos, @Nullable Entity source) {
        if (!level.getBlockState(pos).is(ModBlocks.NUCLEAR_CHARGE.get())) {
            return false;
        }

        level.removeBlock(pos, false);
        NuclearExplosion.explode(level, pos.getCenter(), source, true, true, true, true);
        return true;
    }
}
