package com.skyeshade.skyent.content.explosion.destruction;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import com.skyeshade.skyent.registry.ModBlocks;

public final class NuclearBlockSnapshot implements NuclearBlastRayPlanner.RayWorldView {
    public static final int FLAG_LOADED = 1;
    public static final int FLAG_AIR = 1 << 1;
    public static final int FLAG_FLUID = 1 << 2;
    public static final int FLAG_FRAGILE = 1 << 3;
    public static final int FLAG_NON_SOLID = 1 << 4;
    public static final int FLAG_HAS_BLOCK_ENTITY = 1 << 5;
    public static final int FLAG_PROTECTED_BLOCK_ENTITY = 1 << 6;
    public static final int FLAG_CAN_DESTROY = 1 << 7;
    public static final int FLAG_HIGH_RESISTANCE = 1 << 8;
    public static final int FLAG_OBSIDIAN = 1 << 9;
    public static final int FLAG_CONCRETE_BRICKS = 1 << 10;
    public static final int FLAG_COLLISION_SHAPE_LOOKUP_COUNTED = 1 << 11;
    public static final int FLAG_BLOCK_ENTITY_LOOKUP_COUNTED = 1 << 12;
    private static final int ALL_AIR_FLAGS = FLAG_LOADED | FLAG_AIR;

    private final int minSectionX;
    private final int minSectionY;
    private final int minSectionZ;
    private final int sectionCountX;
    private final int sectionCountY;
    private final int sectionCountZ;
    private final SectionSnapshot[] sections;
    private final int sectionCount;
    private final int fullSectionCount;
    private final int allAirSectionCount;
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
            int fullSectionCount,
            int allAirSectionCount,
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
        this.fullSectionCount = fullSectionCount;
        this.allAirSectionCount = allAirSectionCount;
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
        int fullSectionCount = 0;
        int allAirSectionCount = 0;
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
                    float[] resistances = new float[4096];
                    int[] flags = new int[4096];
                    boolean hasAnySample = false;
                    boolean allAir = true;
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
                                int sampleFlags = FLAG_LOADED;
                                if (classification.air()) {
                                    sampleFlags |= FLAG_AIR;
                                } else {
                                    allAir = false;
                                }
                                if (classification.fluid()) {
                                    sampleFlags |= FLAG_FLUID;
                                }
                                if (classification.fragile()) {
                                    sampleFlags |= FLAG_FRAGILE;
                                }
                                boolean hasBlockEntity = false;
                                boolean protectedBlockEntity = false;
                                if (classification.hasBlockEntity()) {
                                    blockEntityLookups++;
                                    sampleFlags |= FLAG_BLOCK_ENTITY_LOOKUP_COUNTED;
                                    hasBlockEntity = level.getBlockEntity(pos) != null;
                                    protectedBlockEntity = hasBlockEntity && resistanceCache.isProtectedBlockEntity(state, level, pos);
                                }
                                if (hasBlockEntity) {
                                    sampleFlags |= FLAG_HAS_BLOCK_ENTITY;
                                }
                                if (protectedBlockEntity) {
                                    sampleFlags |= FLAG_PROTECTED_BLOCK_ENTITY;
                                }
                                if (classification.canMarkForDestruction() && !protectedBlockEntity) {
                                    sampleFlags |= FLAG_CAN_DESTROY;
                                }
                                boolean nonSolid = false;
                                if (classification.collisionShapeLookupNeeded()) {
                                    collisionShapeLookups++;
                                    sampleFlags |= FLAG_COLLISION_SHAPE_LOOKUP_COUNTED;
                                    nonSolid = state.getCollisionShape(level, pos).isEmpty();
                                }
                                if (nonSolid) {
                                    sampleFlags |= FLAG_NON_SOLID;
                                }
                                float resistance = resistanceCache.resistanceFor(state, level, pos);
                                float rawResistance = state.getBlock().getExplosionResistance();
                                if (resistance >= 12.0F || rawResistance >= 12.0F) {
                                    sampleFlags |= FLAG_HIGH_RESISTANCE;
                                }
                                if (state.is(Blocks.OBSIDIAN)) {
                                    sampleFlags |= FLAG_OBSIDIAN;
                                }
                                if (state.is(ModBlocks.CONCRETE_BRICKS.get())) {
                                    sampleFlags |= FLAG_CONCRETE_BRICKS;
                                }
                                int localIndex = NuclearDestructionMask.localBitIndex(x, y, z);
                                resistances[localIndex] = resistance;
                                flags[localIndex] = sampleFlags;
                                sampledBlocks++;
                                hasAnySample = true;
                            }
                        }
                    }
                    if (hasAnySample) {
                        int index = sectionIndex(sectionX, sectionY, sectionZ, minSectionX, minSectionY, minSectionZ, sectionCountX, sectionCountY);
                        if (allAir) {
                            sections[index] = AllAirSectionSnapshot.INSTANCE;
                            allAirSectionCount++;
                        } else {
                            sections[index] = new FullSectionSnapshot(resistances, flags);
                            fullSectionCount++;
                        }
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
                fullSectionCount,
                allAirSectionCount,
                sampledBlocks,
                blockEntityLookups,
                collisionShapeLookups,
                (System.nanoTime() - startNs) / 1_000_000.0D,
                level.getMinBuildHeight(),
                level.getMaxBuildHeight()
        );
    }

    @Override
    public void fillSample(BlockPos.MutableBlockPos pos, int x, int y, int z, MutableRayBlockSample sample) {
        int sectionX = x >> 4;
        int sectionY = y >> 4;
        int sectionZ = z >> 4;
        int localSectionX = sectionX - minSectionX;
        int localSectionY = sectionY - minSectionY;
        int localSectionZ = sectionZ - minSectionZ;
        if (localSectionX < 0 || localSectionX >= sectionCountX
                || localSectionY < 0 || localSectionY >= sectionCountY
                || localSectionZ < 0 || localSectionZ >= sectionCountZ) {
            sample.setUnloaded();
            return;
        }
        SectionSnapshot section = sections[(localSectionZ * sectionCountY + localSectionY) * sectionCountX + localSectionX];
        if (section == null) {
            sample.setUnloaded();
            return;
        }
        section.fill(NuclearDestructionMask.localBitIndex(x, y, z), sample);
    }

    public int sectionCount() {
        return sectionCount;
    }

    public int fullSectionCount() {
        return fullSectionCount;
    }

    public int allAirSectionCount() {
        return allAirSectionCount;
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
        return fullSectionCount * (4096L * Float.BYTES + 4096L * Integer.BYTES + 64L)
                + allAirSectionCount * 32L
                + (long) sections.length * Long.BYTES;
    }

    public long oldStyleEstimatedBytes() {
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

    private interface SectionSnapshot {
        void fill(int localIndex, MutableRayBlockSample sample);
    }

    private record FullSectionSnapshot(float[] resistances, int[] flags) implements SectionSnapshot {
        @Override
        public void fill(int localIndex, MutableRayBlockSample sample) {
            sample.set(flags[localIndex], resistances[localIndex], null);
        }
    }

    private enum AllAirSectionSnapshot implements SectionSnapshot {
        INSTANCE;

        @Override
        public void fill(int localIndex, MutableRayBlockSample sample) {
            sample.set(ALL_AIR_FLAGS, NuclearResistanceCache.AIR_RESISTANCE, null);
        }
    }

    public static final class MutableRayBlockSample {
        private int flags;
        private float resistance;
        private BlockState state;

        public void set(int flags, float resistance, BlockState state) {
            this.flags = flags;
            this.resistance = resistance;
            this.state = state;
        }

        private void setUnloaded() {
            set(0, NuclearResistanceCache.AIR_RESISTANCE, null);
        }

        public int flags() {
            return flags;
        }

        public float resistance() {
            return resistance;
        }

        public BlockState state() {
            return state;
        }

        public boolean has(int flag) {
            return (flags & flag) != 0;
        }
    }
}
