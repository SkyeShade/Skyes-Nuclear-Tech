package com.skyeshade.skyent.content.block;

import com.skyeshade.skyent.SkyesNuclearTech;
import com.skyeshade.skyent.content.radiation.RadioactiveSource;
import com.skyeshade.skyent.content.radiation.RadioactiveSourceRegistry;
import com.skyeshade.skyent.content.radiation.RadiationBlockProfiles;
import com.skyeshade.skyent.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FlowingFluid;

public class MoltenCoriumBlock extends LiquidBlock implements RadioactiveSource {
    public static final TagKey<Block> CORIUM_MELTABLE = BlockTags.create(ResourceLocation.fromNamespaceAndPath(SkyesNuclearTech.MOD_ID, "corium_meltable"));

    private static final boolean DEBUG_CORIUM_COOLING = false;
    private static final int SOURCE_COOLING_CHANCE = 60;
    private static final int HORIZONTAL_MELT_CHANCE = 3;
    private static final int UP_MELT_CHANCE = 16;

    private static final Direction[] HORIZONTAL_DIRECTIONS = {
            Direction.NORTH,
            Direction.SOUTH,
            Direction.WEST,
            Direction.EAST
    };

    public MoltenCoriumBlock(FlowingFluid fluid, Properties properties) {
        super(fluid, properties.randomTicks());
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (level instanceof ServerLevel serverLevel) {
            RadioactiveSourceRegistry.register(serverLevel, pos);
            com.skyeshade.skyent.event.systems.RadiationSourceTickSystem.registerActiveSourceIfNeeded(serverLevel, pos, state);
        }
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (level instanceof ServerLevel serverLevel && !newState.is(state.getBlock())) {
            RadioactiveSourceRegistry.unregister(serverLevel, pos);
            com.skyeshade.skyent.event.systems.RadiationSourceTickSystem.unregisterActiveSource(serverLevel, pos);
        }

        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        // Registration fallback only; environmental rays and corium source ticking run through RadiationSourceTickSystem.
        RadioactiveSourceRegistry.register(level, pos);
        com.skyeshade.skyent.event.systems.RadiationSourceTickSystem.registerActiveSourceIfNeeded(level, pos, state);
    }

    public static void tickFromSourceRegistry(ServerLevel level, BlockPos pos, BlockState state, RandomSource random) {
        if (!state.is(ModBlocks.MOLTEN_CORIUM_BLOCK.get())) {
            return;
        }

        RadioactiveSourceRegistry.register(level, pos);

        tryMeltNeighbor(level, pos, Direction.DOWN, random);

        Direction horizontal = HORIZONTAL_DIRECTIONS[random.nextInt(HORIZONTAL_DIRECTIONS.length)];
        if (random.nextInt(HORIZONTAL_MELT_CHANCE) == 0) {
            tryMeltNeighbor(level, pos, horizontal, random);
        }

        if (random.nextInt(UP_MELT_CHANCE) == 0) {
            tryMeltNeighbor(level, pos, Direction.UP, random);
        }

        boolean source = isSource(state);
        boolean cools = source && random.nextInt(SOURCE_COOLING_CHANCE) == 0;
        if (DEBUG_CORIUM_COOLING) {
            SkyesNuclearTech.LOGGER.info(
                    "Molten corium cooling tick at {} state={} fluidSource={} cools={}",
                    pos,
                    state,
                    source,
                    cools
            );
        }

        if (cools) {
            level.setBlock(pos, ModBlocks.CORIUM_BLOCK.get().defaultBlockState(), Block.UPDATE_ALL);
        }
    }

    private static void tryMeltNeighbor(ServerLevel level, BlockPos pos, Direction direction, RandomSource random) {
        BlockPos targetPos = pos.relative(direction);
        if (!level.hasChunkAt(targetPos)) {
            return;
        }

        BlockState targetState = level.getBlockState(targetPos);
        if (!canMelt(targetState)) {
            return;
        }

        level.setBlock(targetPos, ModBlocks.MOLTEN_CORIUM_BLOCK.get().defaultBlockState(), Block.UPDATE_ALL);
    }

    private static boolean canMelt(BlockState state) {
        if (state.isAir() || state.is(Blocks.BEDROCK) || !state.getFluidState().isEmpty()) {
            return false;
        }

        return state.is(CORIUM_MELTABLE);
    }

    private static boolean isSource(BlockState state) {
        return state.getFluidState().isSource() || (state.hasProperty(LEVEL) && state.getValue(LEVEL) == 0);
    }

    @Override
    public double getRadiationStrength() {
        return RadiationBlockProfiles.getRadiationStrength(this);
    }

    @Override
    public int getEnvironmentalRadiationRange() {
        return RadiationBlockProfiles.getEnvironmentalRange(this);
    }

    @Override
    public int getEntityRadiationRange() {
        return RadiationBlockProfiles.getEntityRange(this);
    }
}
