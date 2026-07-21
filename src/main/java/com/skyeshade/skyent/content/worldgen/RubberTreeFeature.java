package com.skyeshade.skyent.content.worldgen;

import com.mojang.serialization.Codec;
import com.skyeshade.skyent.SkyesNuclearTech;
import com.skyeshade.skyent.content.block.ResinBearingRubberLogBlock;
import com.skyeshade.skyent.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraft.resources.ResourceLocation;

public class RubberTreeFeature extends Feature<NoneFeatureConfiguration> {
    private static final TagKey<Block> RUBBER_TREE_SPAWNABLE_ON = BlockTags.create(ResourceLocation.fromNamespaceAndPath(
            SkyesNuclearTech.MOD_ID,
            "rubber_tree_spawnable_on"
    ));
    private static final int MIN_TRUNK_HEIGHT = 4;
    private static final int RANDOM_TRUNK_HEIGHT = 3;
    private static final int CANOPY_RADIUS = 2;

    public RubberTreeFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        BlockPos origin = context.origin();
        BlockPos basePos = resolveBasePos(level, origin);
        RandomSource random = context.random();
        int trunkHeight = MIN_TRUNK_HEIGHT + random.nextInt(RANDOM_TRUNK_HEIGHT);

        if (!validateNaturalBase(level, basePos)) {
            return false;
        }

        if (!hasRoomForTree(level, basePos, trunkHeight)) {
            return false;
        }

        placeTrunk(level, basePos, trunkHeight, random);
        placeCanopy(level, basePos.above(trunkHeight), random);
        placeLeafSpire(level, basePos.above(trunkHeight + 1));
        return true;
    }

    private static BlockPos resolveBasePos(WorldGenLevel level, BlockPos origin) {
        if (isSpawnableGround(level.getBlockState(origin)) && canReplace(level, origin.above())) {
            return origin.above();
        }
        return origin;
    }

    private static boolean validateNaturalBase(WorldGenLevel level, BlockPos basePos) {
        if (isRejectedBiome(level, basePos)) {
            return false;
        }

        BlockState baseState = level.getBlockState(basePos);
        if (!baseState.getFluidState().isEmpty() || !canReplace(level, basePos)) {
            return false;
        }

        BlockState ground = level.getBlockState(basePos.below());
        if (!ground.getFluidState().isEmpty() || !isSpawnableGround(ground)) {
            return false;
        }

        if (!level.canSeeSky(basePos)) {
            return false;
        }

        return isDryBaseArea(level, basePos);
    }

    private static boolean isRejectedBiome(WorldGenLevel level, BlockPos basePos) {
        return level.getBiome(basePos).is(Biomes.RIVER)
                || level.getBiome(basePos).is(Biomes.FROZEN_RIVER)
                || level.getBiome(basePos).is(Biomes.BEACH)
                || level.getBiome(basePos).is(Biomes.SNOWY_BEACH)
                || level.getBiome(basePos).is(Biomes.OCEAN)
                || level.getBiome(basePos).is(Biomes.DEEP_OCEAN)
                || level.getBiome(basePos).is(Biomes.COLD_OCEAN)
                || level.getBiome(basePos).is(Biomes.DEEP_COLD_OCEAN)
                || level.getBiome(basePos).is(Biomes.FROZEN_OCEAN)
                || level.getBiome(basePos).is(Biomes.DEEP_FROZEN_OCEAN)
                || level.getBiome(basePos).is(Biomes.LUKEWARM_OCEAN)
                || level.getBiome(basePos).is(Biomes.DEEP_LUKEWARM_OCEAN)
                || level.getBiome(basePos).is(Biomes.WARM_OCEAN);
    }

    private static boolean isDryBaseArea(WorldGenLevel level, BlockPos basePos) {
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                BlockPos checkedBase = basePos.offset(x, 0, z);
                BlockPos checkedGround = checkedBase.below();
                if (!level.getBlockState(checkedBase).getFluidState().isEmpty()
                        || !level.getBlockState(checkedGround).getFluidState().isEmpty()) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean isSpawnableGround(BlockState state) {
        return state.is(RUBBER_TREE_SPAWNABLE_ON);
    }

    private static boolean hasRoomForTree(WorldGenLevel level, BlockPos basePos, int trunkHeight) {
        for (int y = 0; y <= trunkHeight + 1; y++) {
            int radius = y < trunkHeight - 2 ? 0 : CANOPY_RADIUS;
            for (int x = -radius; x <= radius; x++) {
                for (int z = -radius; z <= radius; z++) {
                    BlockPos pos = basePos.offset(x, y, z);
                    if (level.isOutsideBuildHeight(pos) || !canReplace(level, pos)) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    private static boolean canReplace(WorldGenLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.getFluidState().isEmpty()
                && (state.isAir()
                || state.canBeReplaced()
                || state.getBlock() instanceof LeavesBlock);
    }

    private static void placeTrunk(WorldGenLevel level, BlockPos basePos, int trunkHeight, RandomSource random) {
        BlockState log = ModBlocks.RUBBER_LOG.get()
                .defaultBlockState()
                .setValue(RotatedPillarBlock.AXIS, Direction.Axis.Y);
        int resinY = Math.min(trunkHeight - 1, 1 + random.nextInt(Math.max(1, Math.min(2, trunkHeight - 1) + 1)));
        Direction resinFace = Direction.Plane.HORIZONTAL.getRandomDirection(random);
        BlockState resinLog = ModBlocks.RESIN_BEARING_RUBBER_LOG.get()
                .defaultBlockState()
                .setValue(RotatedPillarBlock.AXIS, Direction.Axis.Y)
                .setValue(ResinBearingRubberLogBlock.RESIN_FACE, resinFace);
        for (int y = 0; y < trunkHeight; y++) {
            level.setBlock(basePos.above(y), y == resinY ? resinLog : log, Block.UPDATE_CLIENTS);
        }
    }

    private static void placeCanopy(WorldGenLevel level, BlockPos center, RandomSource random) {
        BlockState leaves = ModBlocks.RUBBER_LEAVES.get()
                .defaultBlockState()
                .setValue(LeavesBlock.DISTANCE, 7)
                .setValue(LeavesBlock.PERSISTENT, false);

        for (int y = -2; y <= 1; y++) {
            int radius = y == 1 ? 1 : CANOPY_RADIUS;
            for (int x = -radius; x <= radius; x++) {
                for (int z = -radius; z <= radius; z++) {
                    if (Math.abs(x) == radius && Math.abs(z) == radius && random.nextBoolean()) {
                        continue;
                    }
                    BlockPos pos = center.offset(x, y, z);
                    if (canReplace(level, pos)) {
                        level.setBlock(pos, leaves, Block.UPDATE_CLIENTS);
                    }
                }
            }
        }
    }

    private static void placeLeafSpire(WorldGenLevel level, BlockPos currentTopCenter) {
        BlockState leaves = ModBlocks.RUBBER_LEAVES.get()
                .defaultBlockState()
                .setValue(LeavesBlock.DISTANCE, 7)
                .setValue(LeavesBlock.PERSISTENT, false);

        for (int offset = 1; offset <= 2; offset++) {
            BlockPos pos = currentTopCenter.above(offset);
            if (canReplace(level, pos)) {
                level.setBlock(pos, leaves, Block.UPDATE_CLIENTS);
            }
        }
    }
}
