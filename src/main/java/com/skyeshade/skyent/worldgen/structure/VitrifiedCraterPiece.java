package com.skyeshade.skyent.worldgen.structure;

import com.skyeshade.skyent.registry.ModBlocks;
import com.skyeshade.skyent.registry.ModStructures;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;

public final class VitrifiedCraterPiece extends StructurePiece {
    private static final String ORIGIN_X = "OriginX";
    private static final String ORIGIN_Y = "OriginY";
    private static final String ORIGIN_Z = "OriginZ";
    private static final String PARAMS = "Params";
    private static final int UPDATE_FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_NEIGHBORS | Block.UPDATE_KNOWN_SHAPE;
    private static final int SURFACE_CLEANUP_ABOVE = 3;
    private static final double EDGE_NOISE_BLOCKS = 1.0D;
    private static final double DEPTH_NOISE_BLOCKS = 0.5D;
    private static final double MIN_VITRIFY_DEPTH = 0.35D;
    private static final double EDGE_VITRIFY_NORMALIZED_LIMIT = 0.97D;

    private final BlockPos origin;
    private final VitrifiedCraterParams params;

    public VitrifiedCraterPiece(BoundingBox boundingBox, BlockPos origin, VitrifiedCraterParams params) {
        super(ModStructures.VITRIFIED_CRATER_PIECE.get(), 0, boundingBox);
        this.origin = origin;
        this.params = params;
    }

    public VitrifiedCraterPiece(StructurePieceSerializationContext context, CompoundTag tag) {
        super(ModStructures.VITRIFIED_CRATER_PIECE.get(), tag);
        this.origin = new BlockPos(tag.getInt(ORIGIN_X), tag.getInt(ORIGIN_Y), tag.getInt(ORIGIN_Z));
        this.params = VitrifiedCraterParams.fromTag(tag.getCompound(PARAMS));
    }

    @Override
    protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
        tag.putInt(ORIGIN_X, origin.getX());
        tag.putInt(ORIGIN_Y, origin.getY());
        tag.putInt(ORIGIN_Z, origin.getZ());
        tag.put(PARAMS, params.toTag());
    }

    @Override
    public void postProcess(
            WorldGenLevel level,
            StructureManager structureManager,
            ChunkGenerator generator,
            RandomSource random,
            BoundingBox box,
            ChunkPos chunkPos,
            BlockPos pieceOrigin
    ) {
        int radius = params.radius();
        double maxDistance = radius + EDGE_NOISE_BLOCKS;
        double maxDistanceSq = maxDistance * maxDistance;
        int minX = Math.max(Mth.floor(origin.getX() - maxDistance), box.minX());
        int maxX = Math.min(Mth.ceil(origin.getX() + maxDistance), box.maxX());
        int minZ = Math.max(Mth.floor(origin.getZ() - maxDistance), box.minZ());
        int maxZ = Math.min(Mth.ceil(origin.getZ() + maxDistance), box.maxZ());

        for (int x = minX; x <= maxX; x++) {
            int dx = x - origin.getX();
            for (int z = minZ; z <= maxZ; z++) {
                int dz = z - origin.getZ();
                int distanceSq = dx * dx + dz * dz;
                if (distanceSq > maxDistanceSq) {
                    continue;
                }

                double distance = Math.sqrt(distanceSq);
                double edgeAdjustedRadius = radius + signedNoise(params.seed(), x, z, 17L) * EDGE_NOISE_BLOCKS;
                double normalized = distance / Math.max(1.0D, edgeAdjustedRadius);
                if (normalized > 1.0D) {
                    continue;
                }

                ColumnSurface surface = findColumnSurface(level, x, z, box);
                if (surface.solidTerrainY() <= level.getMinBuildHeight() || surface.solidTerrainY() > box.maxY()) {
                    continue;
                }

                double bowlDepth = params.depth() * bowlDepth(normalized);
                if (bowlDepth <= 0.0D) {
                    continue;
                }
                double depthNoise = signedNoise(params.seed(), x, z, 31L) * DEPTH_NOISE_BLOCKS * (1.0D - normalized);
                int bowlY = Mth.floor(params.rimY() - bowlDepth + depthNoise);
                bowlY = Mth.clamp(bowlY, level.getMinBuildHeight() + 1, level.getMaxBuildHeight() - 2);
                if (surface.solidTerrainY() <= bowlY) {
                    continue;
                }

                carveColumn(level, box, x, z, surface.carveTopY(), bowlY);
                clearSurfaceDecorations(level, box, x, z, bowlY + 1, surface.carveTopY() + SURFACE_CLEANUP_ABOVE);
                if (bowlDepth > MIN_VITRIFY_DEPTH || normalized < EDGE_VITRIFY_NORMALIZED_LIMIT) {
                    vitrifyColumn(level, box, x, z, bowlY, normalized);
                }
            }
        }
    }

    private void carveColumn(WorldGenLevel level, BoundingBox box, int x, int z, int localSurfaceY, int targetY) {
        for (int y = localSurfaceY; y > targetY; y--) {
            BlockPos pos = new BlockPos(x, y, z);
            if (!box.isInside(pos) || !level.ensureCanWrite(pos)) {
                continue;
            }
            BlockState current = level.getBlockState(pos);
            if (!canCraterCarveToAir(current, level, pos)) {
                continue;
            }
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), UPDATE_FLAGS);
        }
    }

    private void vitrifyColumn(WorldGenLevel level, BoundingBox box, int x, int z, int targetY, double normalized) {
        int thickness = 1 + Mth.floor((1.0D - normalized) * 2.0D);
        for (int layer = 0; layer < thickness; layer++) {
            int y = targetY - layer;
            BlockPos pos = new BlockPos(x, y, z);
            if (!box.isInside(pos) || !level.ensureCanWrite(pos)) {
                continue;
            }
            BlockState current = level.getBlockState(pos);
            if (!canCraterReplaceWithVitrified(current, level, pos)) {
                continue;
            }
            level.setBlock(pos, vitrifiedStateFor(pos, normalized, layer), UPDATE_FLAGS);
        }
    }

    private BlockState vitrifiedStateFor(BlockPos pos, double normalized, int layer) {
        if (layer == 0 && pos.getX() == origin.getX() && pos.getZ() == origin.getZ()) {
            return ModBlocks.RADIANT_VITRIFIED_STONE.get().defaultBlockState();
        }

        double centerStrength = 1.0D - Mth.clamp(normalized, 0.0D, 1.0D);
        double heat = Math.pow(centerStrength, 1.65D);
        heat += signedNoise(params.seed(), pos.getX(), pos.getZ(), 73L) * 0.08D;
        int tier = tierForHeat(Mth.clamp(heat, 0.0D, 1.0D));
        tier = Math.max(1, tier - layer);
        return vitrifiedStateForTier(tier);
    }

    private static double bowlDepth(double normalized) {
        double bowl = 1.0D - normalized * normalized;
        bowl = Mth.clamp(bowl, 0.0D, 1.0D);
        bowl = bowl * bowl * (3.0D - 2.0D * bowl);
        return bowl;
    }

    private ColumnSurface findColumnSurface(WorldGenLevel level, int x, int z, BoundingBox box) {
        int heightmapY = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z) - 1;
        int scanTop = Math.min(Math.max(heightmapY + SURFACE_CLEANUP_ABOVE, params.topY()), box.maxY());
        int carveTopY = level.getMinBuildHeight();
        for (int y = scanTop; y >= level.getMinBuildHeight(); y--) {
            BlockPos pos = new BlockPos(x, y, z);
            BlockState state = level.getBlockState(pos);
            if (state.isAir()) {
                continue;
            }
            if (carveTopY == level.getMinBuildHeight()) {
                carveTopY = y;
            }
            if (!state.getFluidState().isEmpty() || isSurfaceDecoration(state)) {
                continue;
            }
            if (!canCraterReplaceWithVitrified(state, level, pos)) {
                continue;
            }
            return new ColumnSurface(y, Math.max(carveTopY, y));
        }
        return new ColumnSurface(level.getMinBuildHeight(), carveTopY);
    }

    private static void clearSurfaceDecorations(WorldGenLevel level, BoundingBox box, int x, int z, int minY, int maxY) {
        int clampedMinY = Math.max(minY, level.getMinBuildHeight());
        int clampedMaxY = Math.min(maxY, level.getMaxBuildHeight() - 1);
        for (int y = clampedMinY; y <= clampedMaxY; y++) {
            BlockPos pos = new BlockPos(x, y, z);
            if (!box.isInside(pos) || !level.ensureCanWrite(pos)) {
                continue;
            }
            BlockState state = level.getBlockState(pos);
            if (isSurfaceDecoration(state)) {
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), UPDATE_FLAGS);
            }
        }
    }

    private static boolean canCraterCarveToAir(BlockState state, WorldGenLevel level, BlockPos pos) {
        return !state.isAir() && (!state.getFluidState().isEmpty() || isSurfaceDecoration(state) || canCraterReplace(level, pos, state));
    }

    private static boolean canCraterReplaceWithVitrified(BlockState state, WorldGenLevel level, BlockPos pos) {
        return !state.isAir() && state.getFluidState().isEmpty() && canCraterReplace(level, pos, state);
    }

    private static boolean canCraterReplace(WorldGenLevel level, BlockPos pos, BlockState state) {
        if (state.isAir()) {
            return false;
        }
        Block block = state.getBlock();
        if (block == Blocks.BEDROCK || block == Blocks.BARRIER || block == Blocks.STRUCTURE_BLOCK
                || block == Blocks.JIGSAW || block == Blocks.COMMAND_BLOCK || block == Blocks.CHAIN_COMMAND_BLOCK
                || block == Blocks.REPEATING_COMMAND_BLOCK || block == Blocks.END_PORTAL || block == Blocks.END_PORTAL_FRAME) {
            return false;
        }
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity != null) {
            return false;
        }
        float destroySpeed = state.getDestroySpeed(level, pos);
        if (destroySpeed < 0.0F) {
            return false;
        }
        return true;
    }

    private static boolean isSurfaceDecoration(BlockState state) {
        return state.is(Blocks.SHORT_GRASS)
                || state.is(Blocks.TALL_GRASS)
                || state.is(Blocks.FERN)
                || state.is(Blocks.LARGE_FERN)
                || state.is(Blocks.DEAD_BUSH)
                || state.is(Blocks.SNOW)
                || state.is(Blocks.DANDELION)
                || state.is(Blocks.POPPY)
                || state.is(Blocks.BLUE_ORCHID)
                || state.is(Blocks.ALLIUM)
                || state.is(Blocks.AZURE_BLUET)
                || state.is(Blocks.RED_TULIP)
                || state.is(Blocks.ORANGE_TULIP)
                || state.is(Blocks.WHITE_TULIP)
                || state.is(Blocks.PINK_TULIP)
                || state.is(Blocks.OXEYE_DAISY)
                || state.is(Blocks.CORNFLOWER)
                || state.is(Blocks.LILY_OF_THE_VALLEY)
                || state.is(Blocks.WITHER_ROSE)
                || state.is(Blocks.SUNFLOWER)
                || state.is(Blocks.LILAC)
                || state.is(Blocks.ROSE_BUSH)
                || state.is(Blocks.PEONY)
                || state.is(Blocks.OAK_SAPLING)
                || state.is(Blocks.SPRUCE_SAPLING)
                || state.is(Blocks.BIRCH_SAPLING)
                || state.is(Blocks.JUNGLE_SAPLING)
                || state.is(Blocks.ACACIA_SAPLING)
                || state.is(Blocks.DARK_OAK_SAPLING)
                || state.is(Blocks.CHERRY_SAPLING)
                || state.is(Blocks.MANGROVE_PROPAGULE)
                || state.is(Blocks.WHEAT)
                || state.is(Blocks.CARROTS)
                || state.is(Blocks.POTATOES)
                || state.is(Blocks.BEETROOTS)
                || state.is(Blocks.BROWN_MUSHROOM)
                || state.is(Blocks.RED_MUSHROOM)
                || state.is(Blocks.VINE)
                || state.is(Blocks.GLOW_LICHEN);
    }

    private static int tierForHeat(double heat) {
        // 1 normal, 2 baked, 3 scorched, 4 irradiated, 5 hot, 6 infernal. Radiant is placed only at the center.
        if (heat < 0.20D) {
            return 1;
        }
        if (heat < 0.40D) {
            return 2;
        }
        if (heat < 0.60D) {
            return 3;
        }
        if (heat < 0.78D) {
            return 4;
        }
        if (heat < 0.90D) {
            return 5;
        }
        return 6;
    }

    private static BlockState vitrifiedStateForTier(int tier) {
        return switch (tier) {
            case 2 -> ModBlocks.BAKED_VITRIFIED_STONE.get().defaultBlockState();
            case 3 -> ModBlocks.SCORCHED_VITRIFIED_STONE.get().defaultBlockState();
            case 4 -> ModBlocks.IRRADIATED_VITRIFIED_STONE.get().defaultBlockState();
            case 5 -> ModBlocks.HOT_VITRIFIED_STONE.get().defaultBlockState();
            case 6 -> ModBlocks.INFERNAL_VITRIFIED_STONE.get().defaultBlockState();
            default -> ModBlocks.VITRIFIED_STONE.get().defaultBlockState();
        };
    }

    private record ColumnSurface(int solidTerrainY, int carveTopY) {
    }

    private static double signedNoise(long seed, int x, int z, long salt) {
        long value = seed ^ salt;
        value ^= (long) x * 341873128712L;
        value ^= (long) z * 132897987541L;
        value = (value ^ (value >>> 30)) * 0xbf58476d1ce4e5b9L;
        value = (value ^ (value >>> 27)) * 0x94d049bb133111ebL;
        value = value ^ (value >>> 31);
        double unit = ((value >>> 11) * 0x1.0p-53);
        return unit * 2.0D - 1.0D;
    }
}
