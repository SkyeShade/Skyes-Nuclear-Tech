package com.skyeshade.skyent.content.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.function.Supplier;

public class DeadLeavesBlock extends LeavesBlock {
    private static final int RECOVERY_CHANCE = 4;
    private static final boolean DEBUG_RECOVERY = Boolean.getBoolean("skyent.debugDeadLeavesRecovery");
    private static final boolean DEBUG_ALWAYS_RECOVER = Boolean.getBoolean("skyent.debugDeadLeavesAlwaysRecover");
    private final Supplier<? extends Block> livingLeaves;

    public DeadLeavesBlock(Properties properties, Supplier<? extends Block> livingLeaves) {
        super(properties);
        this.livingLeaves = livingLeaves;
    }

    @Override
    public boolean isRandomlyTicking(BlockState state) {
        return true;
    }

    @Override
    public void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        boolean matchingLeavesNearby = hasMatchingLiveLeavesNearby(level, pos);
        boolean recovered = matchingLeavesNearby && (DEBUG_ALWAYS_RECOVER || random.nextInt(RECOVERY_CHANCE) == 0);
        if (DEBUG_RECOVERY) {
            logRecoveryTick(level, pos, matchingLeavesNearby, recovered);
        }
        if (recovered) {
            boolean changed = level.setBlock(pos, copyLeafProperties(state, livingLeaves.get().defaultBlockState()), Block.UPDATE_ALL | Block.UPDATE_SUPPRESS_DROPS);
            if (DEBUG_RECOVERY) {
                com.skyeshade.skyent.SkyesNuclearTech.LOGGER.info("Dead leaves recovery setBlock: pos={} changed={}", pos, changed);
            }
        }
    }

    private boolean hasMatchingLiveLeavesNearby(ServerLevel level, BlockPos pos) {
        Block livingBlock = livingLeaves.get();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0) {
                        continue;
                    }
                    if (level.getBlockState(pos.offset(dx, dy, dz)).is(livingBlock)) {
                        return true;
                    }
                }
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

    private void logRecoveryTick(ServerLevel level, BlockPos pos, boolean matchingLeavesNearby, boolean recovered) {
        Block deadBlock = level.getBlockState(pos).getBlock();
        Block livingBlock = livingLeaves.get();
        com.skyeshade.skyent.SkyesNuclearTech.LOGGER.info(
                "Dead leaves randomTick fired: pos={} dead={} expectedMatchingLive={} matchingLiveNearby={} recovered={}",
                pos,
                BuiltInRegistries.BLOCK.getKey(deadBlock),
                BuiltInRegistries.BLOCK.getKey(livingBlock),
                matchingLeavesNearby,
                recovered
        );
    }
}
