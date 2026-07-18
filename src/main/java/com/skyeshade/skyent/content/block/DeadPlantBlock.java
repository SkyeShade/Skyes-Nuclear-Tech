package com.skyeshade.skyent.content.block;

import com.mojang.serialization.MapCodec;
import com.skyeshade.skyent.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

public class DeadPlantBlock extends BushBlock {
    public static final MapCodec<DeadPlantBlock> CODEC = simpleCodec(DeadPlantBlock::new);
    private static final int UPDATE_FLAGS = UPDATE_CLIENTS | UPDATE_NEIGHBORS;

    public DeadPlantBlock(BlockBehaviour.Properties properties) {
        super(properties.randomTicks());
    }

    @Override
    protected MapCodec<? extends BushBlock> codec() {
        return CODEC;
    }

    @Override
    protected boolean mayPlaceOn(BlockState state, BlockGetter level, BlockPos pos) {
        return state.is(ModBlocks.DEAD_GRASS.get())
                || state.is(ModBlocks.CONTAMINATED_GRASS_BLOCK)
                || state.is(Blocks.GRASS_BLOCK)
                || state.is(Blocks.DIRT)
                || state.is(Blocks.COARSE_DIRT)
                || state.is(Blocks.PODZOL)
                || state.is(Blocks.ROOTED_DIRT)
                || state.is(Blocks.FARMLAND)
                || super.mayPlaceOn(state, level, pos);
    }

    @Override
    public void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        BlockPos belowPos = pos.below();
        BlockState belowState = level.getBlockState(belowPos);
        if (belowState.is(Blocks.GRASS_BLOCK)) {
            recoverPlant(level, pos, state);
            return;
        }

        if (belowState.is(ModBlocks.DEAD_GRASS.get()) && DeadGrassBlock.canRecoverGrass(level, belowPos, random)) {
            level.setBlock(belowPos, Blocks.GRASS_BLOCK.defaultBlockState(), UPDATE_FLAGS);
            recoverPlant(level, pos, state);
        }
    }

    static boolean isDeadGrassPlant(BlockState state) {
        return state.is(ModBlocks.DEAD_SHORT_GRASS.get()) || state.is(ModBlocks.DEAD_TALL_GRASS.get());
    }

    private static void recoverPlant(ServerLevel level, BlockPos pos, BlockState state) {
        if (state.is(ModBlocks.DEAD_TALL_GRASS.get())) {
            recoverTallGrass(level, pos);
            return;
        }

        level.setBlock(pos, Blocks.SHORT_GRASS.defaultBlockState(), UPDATE_FLAGS);
    }

    static void recoverTallGrass(ServerLevel level, BlockPos pos) {
        BlockPos upperPos = pos.above();
        if (canPlaceTallGrass(level, pos, upperPos)) {
            level.setBlock(pos, Blocks.TALL_GRASS.defaultBlockState().setValue(DoublePlantBlock.HALF, DoubleBlockHalf.LOWER), UPDATE_FLAGS);
            level.setBlock(upperPos, Blocks.TALL_GRASS.defaultBlockState().setValue(DoublePlantBlock.HALF, DoubleBlockHalf.UPPER), UPDATE_FLAGS);
            return;
        }

        level.setBlock(pos, Blocks.SHORT_GRASS.defaultBlockState(), UPDATE_FLAGS);
    }

    private static boolean canPlaceTallGrass(LevelReader level, BlockPos lowerPos, BlockPos upperPos) {
        return level.getBlockState(lowerPos.below()).is(Blocks.GRASS_BLOCK)
                && level.getBlockState(upperPos).canBeReplaced()
                && Blocks.TALL_GRASS.defaultBlockState().canSurvive(level, lowerPos);
    }
}
