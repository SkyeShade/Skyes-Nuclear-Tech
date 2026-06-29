package com.skyeshade.skyent.content.block;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.function.Supplier;

public class DeadLeavesBlock extends LeavesBlock {
    private static final int RECOVERY_CHANCE = 16;
    private static final int LOG_SUPPORT_RADIUS = 6;
    private final Supplier<? extends Block> livingLeaves;

    public DeadLeavesBlock(Properties properties, Supplier<? extends Block> livingLeaves) {
        super(properties);
        this.livingLeaves = livingLeaves;
    }

    @Override
    public void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (canRecover(level, pos, random)) {
            level.setBlock(pos, copyLeafProperties(state, livingLeaves.get().defaultBlockState()), Block.UPDATE_CLIENTS);
            return;
        }

        super.randomTick(state, level, pos, random);
    }

    private boolean canRecover(ServerLevel level, BlockPos pos, RandomSource random) {
        return random.nextInt(RECOVERY_CHANCE) == 0
                && hasNearbyLivingLeaves(level, pos)
                && hasNearbyLog(level, pos);
    }

    private boolean hasNearbyLivingLeaves(ServerLevel level, BlockPos pos) {
        Block livingBlock = livingLeaves.get();
        for (BlockPos nearbyPos : BlockPos.betweenClosed(pos.offset(-1, -1, -1), pos.offset(1, 1, 1))) {
            if (!nearbyPos.equals(pos) && level.getBlockState(nearbyPos).is(livingBlock)) {
                return true;
            }
        }

        return false;
    }

    private static boolean hasNearbyLog(ServerLevel level, BlockPos pos) {
        for (BlockPos nearbyPos : BlockPos.betweenClosed(
                pos.offset(-LOG_SUPPORT_RADIUS, -LOG_SUPPORT_RADIUS, -LOG_SUPPORT_RADIUS),
                pos.offset(LOG_SUPPORT_RADIUS, LOG_SUPPORT_RADIUS, LOG_SUPPORT_RADIUS))) {
            if (level.getBlockState(nearbyPos).is(BlockTags.LOGS)) {
                return true;
            }
        }

        return false;
    }

    private static BlockState copyLeafProperties(BlockState source, BlockState target) {
        if (source.hasProperty(DISTANCE) && target.hasProperty(DISTANCE)) {
            target = target.setValue(DISTANCE, source.getValue(DISTANCE));
        }
        if (source.hasProperty(PERSISTENT) && target.hasProperty(PERSISTENT)) {
            target = target.setValue(PERSISTENT, source.getValue(PERSISTENT));
        }
        if (source.hasProperty(WATERLOGGED) && target.hasProperty(WATERLOGGED)) {
            target = target.setValue(WATERLOGGED, source.getValue(WATERLOGGED));
        }

        return target;
    }
}
