package com.skyeshade.skyent.content.explosion.destruction;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;

public final class NuclearBlockSnapshot implements NuclearBlastRayPlanner.RayWorldView {
    private static final RayBlockSample UNLOADED = RayBlockSample.unloaded();

    private final int minSectionX;
    private final int minSectionY;
    private final int minSectionZ;
    private final int sectionCountX;
    private final int sectionCountY;
    private final int sectionCountZ;
    private final SectionSnapshot[] sections;
    private final int sectionCount;
    private final long sampledBlocks;
    private final long blockEntityLookups;
    private final long collisionShapeLookups;
    private final double buildMs;
    private final int minBuildHeight;
    private final int maxBuildHeight;

    private NuclearBlockSnapshot(
            int minSectionX,
            int minSectionY,
            int minSectionZ,
            int sectionCountX,
            int sectionCountY,
            int sectionCountZ,
            SectionSnapshot[] sections,
            int sectionCount,
            long sampledBlocks,
            long blockEntityLookups,
            long collisionShapeLookups,
            double buildMs,
            int minBuildHeight,
            int maxBuildHeight
    ) {
        this.minSectionX = minSectionX;
        this.minSectionY = minSectionY;
        this.minSectionZ = minSectionZ;
        this.sectionCountX = sectionCountX;
        this.sectionCountY = sectionCountY;
        this.sectionCountZ = sectionCountZ;
        this.sections = sections;
        this.sectionCount = sectionCount;
        this.sampledBlocks = sampledBlocks;
        this.blockEntityLookups = blockEntityLookups;
        this.collisionShapeLookups = collisionShapeLookups;
        this.buildMs = buildMs;
        this.minBuildHeight = minBuildHeight;
        this.maxBuildHeight = maxBuildHeight;
    }

    public static NuclearBlockSnapshot build(ServerLevel level, Vec3 center, int radius, NuclearResistanceCache resistanceCache) {
        long startNs = System.nanoTime();
        int centerX = Mth.floor(center.x);
        int centerY = Mth.floor(center.y);
        int centerZ = Mth.floor(center.z);
        int minX = centerX - radius;
        int maxX = centerX + radius;
        int minY = Math.max(level.getMinBuildHeight(), centerY - radius);
        int maxY = Math.min(level.getMaxBuildHeight() - 1, centerY + radius);
        int minZ = centerZ - radius;
        int maxZ = centerZ + radius;
        int minSectionX = minX >> 4;
        int maxSectionX = maxX >> 4;
        int minSectionY = minY >> 4;
        int maxSectionY = maxY >> 4;
        int minSectionZ = minZ >> 4;
        int maxSectionZ = maxZ >> 4;
        int sectionCountX = maxSectionX - minSectionX + 1;
        int sectionCountY = maxSectionY - minSectionY + 1;
        int sectionCountZ = maxSectionZ - minSectionZ + 1;
        SectionSnapshot[] sections = new SectionSnapshot[sectionCountX * sectionCountY * sectionCountZ];
        int sectionCount = 0;
        long sampledBlocks = 0;
        long blockEntityLookups = 0;
        long collisionShapeLookups = 0;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

        for (int sectionX = minSectionX; sectionX <= maxSectionX; sectionX++) {
            for (int sectionZ = minSectionZ; sectionZ <= maxSectionZ; sectionZ++) {
                pos.set(sectionX << 4, centerY, sectionZ << 4);
                if (!level.hasChunkAt(pos)) {
                    continue;
                }
                for (int sectionY = minSectionY; sectionY <= maxSectionY; sectionY++) {
                    RayBlockSample[] samples = new RayBlockSample[4096];
                    boolean hasAnySample = false;
                    for (int localY = 0; localY < 16; localY++) {
                        int y = (sectionY << 4) + localY;
                        if (y < minY || y > maxY) {
                            continue;
                        }
                        for (int localZ = 0; localZ < 16; localZ++) {
                            int z = (sectionZ << 4) + localZ;
                            if (z < minZ || z > maxZ) {
                                continue;
                            }
                            for (int localX = 0; localX < 16; localX++) {
                                int x = (sectionX << 4) + localX;
                                if (x < minX || x > maxX) {
                                    continue;
                                }
                                pos.set(x, y, z);
                                BlockState state = level.getBlockState(pos);
                                NuclearResistanceCache.RayBlockClassification classification = resistanceCache.classify(state);
                                boolean hasBlockEntity = false;
                                boolean protectedBlockEntity = false;
                                if (classification.hasBlockEntity()) {
                                    blockEntityLookups++;
                                    hasBlockEntity = level.getBlockEntity(pos) != null;
                                    protectedBlockEntity = hasBlockEntity && resistanceCache.isProtectedBlockEntity(state, level, pos);
                                }
                                boolean nonSolid = false;
                                if (classification.collisionShapeLookupNeeded()) {
                                    collisionShapeLookups++;
                                    nonSolid = state.getCollisionShape(level, pos).isEmpty();
                                }
                                float resistance = resistanceCache.resistanceFor(state, level, pos);
                                float rawResistance = state.getBlock().getExplosionResistance();
                                RayBlockSample sample = new RayBlockSample(
                                        true,
                                        state,
                                        classification.air(),
                                        classification.fluid(),
                                        classification.fragile(),
                                        nonSolid,
                                        false,
                                        false,
                                        hasBlockEntity,
                                        protectedBlockEntity,
                                        classification.canMarkForDestruction() && !protectedBlockEntity,
                                        resistance,
                                        rawResistance
                                );
                                samples[NuclearDestructionMask.localBitIndex(x, y, z)] = sample;
                                sampledBlocks++;
                                hasAnySample = true;
                            }
                        }
                    }
                    if (hasAnySample) {
                        int index = sectionIndex(sectionX, sectionY, sectionZ, minSectionX, minSectionY, minSectionZ, sectionCountX, sectionCountY);
                        sections[index] = new SectionSnapshot(samples);
                        sectionCount++;
                    }
                }
            }
        }

        return new NuclearBlockSnapshot(
                minSectionX,
                minSectionY,
                minSectionZ,
                sectionCountX,
                sectionCountY,
                sectionCountZ,
                sections,
                sectionCount,
                sampledBlocks,
                blockEntityLookups,
                collisionShapeLookups,
                (System.nanoTime() - startNs) / 1_000_000.0D,
                level.getMinBuildHeight(),
                level.getMaxBuildHeight()
        );
    }

    @Override
    public RayBlockSample sample(BlockPos.MutableBlockPos pos, int x, int y, int z) {
        int sectionX = x >> 4;
        int sectionY = y >> 4;
        int sectionZ = z >> 4;
        int localSectionX = sectionX - minSectionX;
        int localSectionY = sectionY - minSectionY;
        int localSectionZ = sectionZ - minSectionZ;
        if (localSectionX < 0 || localSectionX >= sectionCountX
                || localSectionY < 0 || localSectionY >= sectionCountY
                || localSectionZ < 0 || localSectionZ >= sectionCountZ) {
            return UNLOADED;
        }
        SectionSnapshot section = sections[(localSectionZ * sectionCountY + localSectionY) * sectionCountX + localSectionX];
        if (section == null) {
            return UNLOADED;
        }
        RayBlockSample sample = section.samples[NuclearDestructionMask.localBitIndex(x, y, z)];
        return sample == null ? UNLOADED : sample;
    }

    public int sectionCount() {
        return sectionCount;
    }

    public long sampledBlocks() {
        return sampledBlocks;
    }

    public long blockEntityLookups() {
        return blockEntityLookups;
    }

    public long collisionShapeLookups() {
        return collisionShapeLookups;
    }

    public double buildMs() {
        return buildMs;
    }

    public long estimatedBytes() {
        return sampledBlocks * 64L + sectionCount * 4096L * Long.BYTES;
    }

    @Override
    public int minBuildHeight() {
        return minBuildHeight;
    }

    @Override
    public int maxBuildHeight() {
        return maxBuildHeight;
    }

    private static int sectionIndex(int sectionX, int sectionY, int sectionZ, int minSectionX, int minSectionY, int minSectionZ, int sectionCountX, int sectionCountY) {
        return ((sectionZ - minSectionZ) * sectionCountY + (sectionY - minSectionY)) * sectionCountX + (sectionX - minSectionX);
    }

    private record SectionSnapshot(RayBlockSample[] samples) {
    }

    public record RayBlockSample(
            boolean loaded,
            BlockState state,
            boolean air,
            boolean fluid,
            boolean fragile,
            boolean nonSolid,
            boolean blockEntityLookupCounted,
            boolean collisionShapeLookupCounted,
            boolean hasBlockEntity,
            boolean protectedBlockEntity,
            boolean canDestroy,
            float resistance,
            float rawResistance
    ) {
        public static RayBlockSample unloaded() {
            return new RayBlockSample(
                    false,
                    Blocks.AIR.defaultBlockState(),
                    true,
                    false,
                    false,
                    false,
                    false,
                    false,
                    false,
                    false,
                    false,
                    NuclearResistanceCache.AIR_RESISTANCE,
                    0.0F
            );
        }
    }
}
