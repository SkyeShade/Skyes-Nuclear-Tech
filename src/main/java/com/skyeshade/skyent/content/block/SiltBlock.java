package com.skyeshade.skyent.content.block;

import com.mojang.serialization.MapCodec;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.DirectionalPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

public class SiltBlock extends FallingBlock {
    public static final MapCodec<SiltBlock> CODEC = simpleCodec(SiltBlock::new);
    private static final String TAG_SPREAD_COUNT = "SkyentSiltSpreadCount";
    private static final int MAX_SPREAD_RELOCATIONS = 8;
    private static final int[][] HORIZONTAL_OFFSETS = {
            {0, -1},
            {0, 1},
            {1, 0},
            {-1, 0},
            {1, -1},
            {-1, -1},
            {1, 1},
            {-1, 1}
    };

    public SiltBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends FallingBlock> codec() {
        return CODEC;
    }

    @Override
    public void onLand(Level level, BlockPos pos, BlockState state, BlockState replacedState, FallingBlockEntity fallingBlock) {
        if (level.isClientSide || spreadCount(fallingBlock) >= MAX_SPREAD_RELOCATIONS) {
            return;
        }

        List<BlockPos> candidates = spreadCandidates(level, pos, state);
        if (candidates.isEmpty()) {
            return;
        }

        BlockPos target = candidates.get(level.random.nextInt(candidates.size()));
        if (level.getBlockState(target.below()).isAir()) {
            level.removeBlock(pos, false);
            FallingBlockEntity nextFallingBlock = FallingBlockEntity.fall(level, target, state);
            setSpreadCount(nextFallingBlock, spreadCount(fallingBlock) + 1);
            return;
        }

        if (state.canSurvive(level, target) && level.setBlock(target, state, 3)) {
            level.removeBlock(pos, false);
        }
    }

    private static List<BlockPos> spreadCandidates(Level level, BlockPos pos, BlockState siltState) {
        List<BlockPos> candidates = new ArrayList<>();
        for (int[] offset : HORIZONTAL_OFFSETS) {
            BlockPos target = pos.offset(offset[0], 0, offset[1]);
            if (canSpreadTo(level, target, siltState)) {
                candidates.add(target);
            }
        }

        return candidates;
    }

    private static boolean canSpreadTo(Level level, BlockPos pos, BlockState siltState) {
        if (!level.isLoaded(pos) || !level.isLoaded(pos.below())) {
            return false;
        }

        BlockState targetState = level.getBlockState(pos);
        if (!targetState.getFluidState().isEmpty()) {
            return false;
        }

        boolean replaceable = targetState.isAir() || targetState.canBeReplaced(new DirectionalPlaceContext(level, pos, Direction.DOWN, ItemStack.EMPTY, Direction.UP));
        return replaceable && siltState.canSurvive(level, pos);
    }

    private static int spreadCount(FallingBlockEntity fallingBlock) {
        return fallingBlock.blockData == null ? 0 : fallingBlock.blockData.getInt(TAG_SPREAD_COUNT);
    }

    private static void setSpreadCount(FallingBlockEntity fallingBlock, int count) {
        CompoundTag tag = fallingBlock.blockData == null ? new CompoundTag() : fallingBlock.blockData.copy();
        tag.putInt(TAG_SPREAD_COUNT, count);
        fallingBlock.blockData = tag;
    }
}
