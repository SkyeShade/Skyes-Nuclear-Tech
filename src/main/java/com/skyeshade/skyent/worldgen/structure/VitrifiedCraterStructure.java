package com.skyeshade.skyent.worldgen.structure;

import com.mojang.serialization.MapCodec;
import com.skyeshade.skyent.registry.ModStructures;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;

import java.util.Optional;

public final class VitrifiedCraterStructure extends Structure {
    public static final MapCodec<VitrifiedCraterStructure> CODEC = simpleCodec(VitrifiedCraterStructure::new);
    private static final float GENERATION_CHANCE = 0.20F;
    private static final int MIN_RADIUS = 14;
    private static final int RADIUS_VARIATION = 22;
    private static final int EDGE_PADDING = 4;
    private static final int CLEANUP_ABOVE_Y = 6;

    public VitrifiedCraterStructure(StructureSettings settings) {
        super(settings);
    }

    @Override
    protected Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
        RandomSource random = context.random();
        if (random.nextFloat() > GENERATION_CHANCE) {
            return Optional.empty();
        }

        ChunkPos chunkPos = context.chunkPos();
        int centerX = chunkPos.getMiddleBlockX();
        int centerZ = chunkPos.getMiddleBlockZ();
        int worldSurfaceY = context.chunkGenerator().getFirstOccupiedHeight(
                centerX,
                centerZ,
                Heightmap.Types.WORLD_SURFACE_WG,
                context.heightAccessor(),
                context.randomState()
        );
        int oceanFloorY = context.chunkGenerator().getFirstOccupiedHeight(
                centerX,
                centerZ,
                Heightmap.Types.OCEAN_FLOOR_WG,
                context.heightAccessor(),
                context.randomState()
        );
        if (worldSurfaceY <= context.heightAccessor().getMinBuildHeight() + 8) {
            return Optional.empty();
        }

        BlockState surfaceState = context.chunkGenerator().getBaseColumn(
                centerX,
                centerZ,
                context.heightAccessor(),
                context.randomState()
        ).getBlock(worldSurfaceY - 1);
        int surfaceY = surfaceState.getFluidState().isEmpty() ? worldSurfaceY : oceanFloorY;

        int radius = MIN_RADIUS + random.nextInt(RADIUS_VARIATION);
        RimSample rimSample = sampleRim(context, centerX, centerZ, radius, surfaceY);
        int rimY = rimSample.averageY();
        int depth = Math.max(4, radius / 4 + random.nextInt(radius / 5 + 2));
        int bottomY = rimY - depth - 5;
        int topY = Math.max(rimSample.maxY(), Math.max(surfaceY, rimY)) + CLEANUP_ABOVE_Y;
        long seed = random.nextLong();
        BlockPos origin = new BlockPos(centerX, rimY, centerZ);
        VitrifiedCraterParams params = new VitrifiedCraterParams(radius, depth, rimY, bottomY, topY, seed);
        BoundingBox fullBox = new BoundingBox(
                centerX - radius - EDGE_PADDING,
                bottomY,
                centerZ - radius - EDGE_PADDING,
                centerX + radius + EDGE_PADDING,
                topY,
                centerZ + radius + EDGE_PADDING
        );

        return Optional.of(new GenerationStub(origin, builder -> builder.addPiece(new VitrifiedCraterPiece(fullBox, origin, params))));
    }

    @Override
    public StructureType<?> type() {
        return ModStructures.VITRIFIED_CRATER.get();
    }

    private static RimSample sampleRim(GenerationContext context, int centerX, int centerZ, int radius, int fallbackY) {
        int halfRadius = Math.max(4, radius / 2);
        int diagonal = Math.max(3, Mth.floor(halfRadius * 0.70710678D));
        int[][] offsets = {
                {0, 0},
                {halfRadius, 0},
                {-halfRadius, 0},
                {0, halfRadius},
                {0, -halfRadius},
                {diagonal, diagonal},
                {diagonal, -diagonal},
                {-diagonal, diagonal},
                {-diagonal, -diagonal}
        };

        int total = 0;
        int count = 0;
        int maxY = fallbackY;
        for (int[] offset : offsets) {
            int y = context.chunkGenerator().getFirstOccupiedHeight(
                    centerX + offset[0],
                    centerZ + offset[1],
                    Heightmap.Types.OCEAN_FLOOR_WG,
                    context.heightAccessor(),
                    context.randomState()
            );
            if (y > context.heightAccessor().getMinBuildHeight() + 4) {
                total += y;
                count++;
                maxY = Math.max(maxY, y);
            }
        }
        int averageY = count == 0 ? fallbackY : Mth.floor((double) total / count);
        return new RimSample(averageY, maxY);
    }

    private record RimSample(int averageY, int maxY) {
    }
}
