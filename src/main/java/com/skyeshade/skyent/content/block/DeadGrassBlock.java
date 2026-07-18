package com.skyeshade.skyent.content.block;

import com.skyeshade.skyent.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

public class DeadGrassBlock extends Block {
    static final int RECOVERY_LIGHT_LEVEL = 9;
    static final int RECOVERY_CHANCE = 8;
    private static final int AMBIENT_PARTICLE_CHANCE = 16;
    private static final int UPDATE_FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_NEIGHBORS;

    public DeadGrassBlock(Properties properties) {
        super(properties);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        spawnAmbientParticles(level, pos, random);
    }

    static void spawnAmbientParticles(Level level, BlockPos pos, RandomSource random) {
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

        BlockState aboveState = level.getBlockState(pos.above());
        if ((!aboveState.isAir() && !DeadPlantBlock.isDeadGrassPlant(aboveState)) || !hasNearbyGrass(level, pos)) {
            return;
        }

        level.setBlock(pos, Blocks.GRASS_BLOCK.defaultBlockState(), UPDATE_FLAGS);
        recoverDeadPlantAbove(level, pos);
    }

    static boolean canRecoverGrass(ServerLevel level, BlockPos pos, RandomSource random) {
        return random.nextInt(RECOVERY_CHANCE) == 0
                && level.getMaxLocalRawBrightness(pos.above()) >= RECOVERY_LIGHT_LEVEL
                && hasNearbyGrass(level, pos);
    }

    static boolean hasNearbyGrass(ServerLevel level, BlockPos pos) {
        for (BlockPos nearbyPos : BlockPos.betweenClosed(pos.offset(-1, -1, -1), pos.offset(1, 1, 1))) {
            if (!nearbyPos.equals(pos) && level.getBlockState(nearbyPos).is(Blocks.GRASS_BLOCK)) {
                return true;
            }
        }

        return false;
    }

    private static void recoverDeadPlantAbove(ServerLevel level, BlockPos grassPos) {
        BlockPos plantPos = grassPos.above();
        BlockState plantState = level.getBlockState(plantPos);
        if (plantState.is(ModBlocks.DEAD_SHORT_GRASS.get())) {
            level.setBlock(plantPos, Blocks.SHORT_GRASS.defaultBlockState(), UPDATE_FLAGS);
        } else if (plantState.is(ModBlocks.DEAD_TALL_GRASS.get())) {
            DeadPlantBlock.recoverTallGrass(level, plantPos);
        }
    }
}
