package com.skyeshade.skyent.content.block;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

public class ContaminatedGrassBlock extends RadioactiveBlock {
    public ContaminatedGrassBlock(Properties properties) {
        super(properties);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        DeadGrassBlock.spawnAmbientParticles(level, pos, random);
    }
}
