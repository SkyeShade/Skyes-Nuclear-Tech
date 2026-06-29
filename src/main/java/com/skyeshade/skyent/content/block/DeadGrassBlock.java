package com.skyeshade.skyent.content.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

public class DeadGrassBlock extends Block {
    private static final int RECOVERY_LIGHT_LEVEL = 9;
    private static final int RECOVERY_CHANCE = 8;
    private static final int AMBIENT_PARTICLE_CHANCE = 16;

    public DeadGrassBlock(Properties properties) {
        super(properties);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (random.nextInt(AMBIENT_PARTICLE_CHANCE) != 0) {
            return;
        }

        level.addParticle(
                ParticleTypes.MYCELIUM,
                pos.getX() + random.nextDouble(),
                pos.getY() + 1.1D,
                pos.getZ() + random.nextDouble(),
                0.0D,
                0.0D,
                0.0D
        );
    }

    @Override
    public void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (random.nextInt(RECOVERY_CHANCE) != 0 || level.getMaxLocalRawBrightness(pos.above()) < RECOVERY_LIGHT_LEVEL) {
            return;
        }

        if (!level.getBlockState(pos.above()).isAir() || !hasNearbyGrass(level, pos)) {
            return;
        }

        level.setBlock(pos, Blocks.GRASS_BLOCK.defaultBlockState(), Block.UPDATE_CLIENTS);
    }

    private static boolean hasNearbyGrass(ServerLevel level, BlockPos pos) {
        for (BlockPos nearbyPos : BlockPos.betweenClosed(pos.offset(-1, -1, -1), pos.offset(1, 1, 1))) {
            if (!nearbyPos.equals(pos) && level.getBlockState(nearbyPos).is(Blocks.GRASS_BLOCK)) {
                return true;
            }
        }

        return false;
    }
}
