package com.skyeshade.skyent.content.block;

import com.skyeshade.skyent.SkyesNuclearTech;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.function.Supplier;

public class DeadLeavesBlock extends LeavesBlock {
    private static final TagKey<Block> BURNT_LOGS = BlockTags.create(ResourceLocation.fromNamespaceAndPath(
            SkyesNuclearTech.MOD_ID,
            "burnt_logs"
    ));
    private static final int RECOVERY_CHANCE = 4;
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
        RecoverySource recoverySource = findRecoverySourceNearby(level, pos);
        if (recoverySource != RecoverySource.NONE && random.nextInt(RECOVERY_CHANCE) == 0) {
            level.setBlock(pos, copyLeafProperties(state, livingLeaves.get().defaultBlockState()), Block.UPDATE_ALL | Block.UPDATE_SUPPRESS_DROPS);
        }
    }

    private RecoverySource findRecoverySourceNearby(ServerLevel level, BlockPos pos) {
        Block livingBlock = livingLeaves.get();
        boolean foundLivingLog = false;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0) {
                        continue;
                    }
                    BlockState neighborState = level.getBlockState(pos.offset(dx, dy, dz));
                    if (neighborState.is(livingBlock)) {
                        return RecoverySource.LIVE_LEAVES;
                    }
                    if (isLivingRecoveryLog(neighborState)) {
                        foundLivingLog = true;
                    }
                }
            }
        }
        return foundLivingLog ? RecoverySource.LIVE_LOG : RecoverySource.NONE;
    }

    private static boolean isLivingRecoveryLog(BlockState state) {
        return state.is(BlockTags.LOGS) && !state.is(BURNT_LOGS);
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

    private enum RecoverySource {
        NONE,
        LIVE_LEAVES,
        LIVE_LOG
    }
}
