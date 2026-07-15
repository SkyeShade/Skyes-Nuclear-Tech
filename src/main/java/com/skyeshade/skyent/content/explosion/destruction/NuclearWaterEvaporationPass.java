package com.skyeshade.skyent.content.explosion.destruction;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class NuclearWaterEvaporationPass {
    private static final boolean EVAPORATE_WATER = true;
    private static final boolean EVAPORATE_LAVA = true;

    private final ServerLevel level;
    private final Vec3 center;
    private final int radius;
    private final double radiusSqr;
    private final int minY;
    private final int maxY;
    private final List<SectionWork> sections;
    private int nextSectionIndex;
    private SectionWork currentSection;
    private int currentLocalIndex;
    private boolean complete;
    private long totalSectionsProcessed;
    private long totalBlocksChecked;
    private long totalWaterBlocksRemoved;
    private long totalLavaBlocksRemoved;
    private long totalWaterloggedBlocksCleared;
    private long skippedUnloadedSections;
    private long skippedBlockEntities;

    public NuclearWaterEvaporationPass(ServerLevel level, Vec3 center, int radius) {
        this.level = level;
        this.center = center;
        this.radius = Math.max(0, radius);
        this.radiusSqr = (double) this.radius * this.radius;
        this.minY = Math.max(level.getMinBuildHeight(), Mth.floor(center.y) - this.radius);
        this.maxY = Math.min(level.getMaxBuildHeight() - 1, Mth.floor(center.y) + this.radius);
        this.sections = buildSectionQueue();
        this.complete = sections.isEmpty();
    }

    public EvaporationResult tick(int maxSections, int maxBlockChecks, int maxBlockChanges) {
        int sectionsProcessed = 0;
        int blockChecks = 0;
        int blockChanges = 0;
        int waterBlocksRemoved = 0;
        int lavaBlocksRemoved = 0;
        int waterloggedBlocksCleared = 0;
        int skippedSections = 0;
        int skippedBlockEntityCount = 0;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

        while (!complete
                && blockChecks < maxBlockChecks
                && blockChanges < maxBlockChanges
                && (currentSection != null || sectionsProcessed < maxSections)) {
            if (currentSection == null) {
                currentSection = sections.get(nextSectionIndex++);
                currentLocalIndex = 0;
                if (!level.hasChunk(currentSection.sectionX(), currentSection.sectionZ())) {
                    skippedSections++;
                    skippedUnloadedSections++;
                    sectionsProcessed++;
                    totalSectionsProcessed++;
                    currentSection = null;
                    updateCompletion();
                    continue;
                }
            }

            while (currentLocalIndex < 4096 && blockChecks < maxBlockChecks && blockChanges < maxBlockChanges) {
                int localIndex = currentLocalIndex++;
                int localX = localIndex & 15;
                int localZ = (localIndex >> 4) & 15;
                int localY = (localIndex >> 8) & 15;
                int x = (currentSection.sectionX() << 4) + localX;
                int y = (currentSection.sectionY() << 4) + localY;
                int z = (currentSection.sectionZ() << 4) + localZ;
                if (y < minY || y > maxY || !isInsideSphere(x, y, z)) {
                    continue;
                }

                pos.set(x, y, z);
                blockChecks++;
                totalBlocksChecked++;
                BlockState state = level.getBlockState(pos);
                boolean water = EVAPORATE_WATER && state.getFluidState().is(FluidTags.WATER);
                boolean lava = EVAPORATE_LAVA && state.getFluidState().is(FluidTags.LAVA);
                if (!water && !lava) {
                    continue;
                }

                if (water
                        && state.hasProperty(BlockStateProperties.WATERLOGGED)
                        && Boolean.TRUE.equals(state.getValue(BlockStateProperties.WATERLOGGED))) {
                    level.setBlock(pos, state.setValue(BlockStateProperties.WATERLOGGED, false), NuclearBlockMutationQueue.NUKE_BLOCK_UPDATE_FLAGS);
                    waterloggedBlocksCleared++;
                    totalWaterloggedBlocksCleared++;
                    blockChanges++;
                    continue;
                }

                if (level.getBlockEntity(pos) != null) {
                    skippedBlockEntityCount++;
                    skippedBlockEntities++;
                    continue;
                }

                level.setBlock(pos, Blocks.AIR.defaultBlockState(), NuclearBlockMutationQueue.NUKE_BLOCK_UPDATE_FLAGS);
                if (lava) {
                    lavaBlocksRemoved++;
                    totalLavaBlocksRemoved++;
                } else {
                    waterBlocksRemoved++;
                    totalWaterBlocksRemoved++;
                }
                blockChanges++;
            }

            if (currentSection != null && currentLocalIndex >= 4096) {
                sectionsProcessed++;
                totalSectionsProcessed++;
                currentSection = null;
                updateCompletion();
            }
        }

        updateCompletion();
        return new EvaporationResult(
                sectionsProcessed,
                blockChecks,
                waterBlocksRemoved,
                lavaBlocksRemoved,
                waterloggedBlocksCleared,
                skippedSections,
                skippedBlockEntityCount,
                complete
        );
    }

    public boolean isComplete() {
        return complete;
    }

    public int radius() {
        return radius;
    }

    public int sectionCount() {
        return sections.size();
    }

    public int sectionsRemaining() {
        return sections.size() - nextSectionIndex + (currentSection == null ? 0 : 1);
    }

    public long totalSectionsProcessed() {
        return totalSectionsProcessed;
    }

    public long totalBlocksChecked() {
        return totalBlocksChecked;
    }

    public long totalWaterBlocksRemoved() {
        return totalWaterBlocksRemoved;
    }

    public long totalLavaBlocksRemoved() {
        return totalLavaBlocksRemoved;
    }

    public long totalWaterloggedBlocksCleared() {
        return totalWaterloggedBlocksCleared;
    }

    public long skippedUnloadedSections() {
        return skippedUnloadedSections;
    }

    public long skippedBlockEntities() {
        return skippedBlockEntities;
    }

    public void clear() {
        sections.clear();
        currentSection = null;
        currentLocalIndex = 0;
        complete = true;
    }

    private List<SectionWork> buildSectionQueue() {
        if (radius <= 0 || minY > maxY) {
            return new ArrayList<>();
        }

        int centerX = Mth.floor(center.x);
        int centerZ = Mth.floor(center.z);
        int minSectionX = (centerX - radius) >> 4;
        int maxSectionX = (centerX + radius) >> 4;
        int minSectionY = minY >> 4;
        int maxSectionY = maxY >> 4;
        int minSectionZ = (centerZ - radius) >> 4;
        int maxSectionZ = (centerZ + radius) >> 4;
        List<SectionWork> queue = new ArrayList<>();

        for (int sectionX = minSectionX; sectionX <= maxSectionX; sectionX++) {
            for (int sectionY = minSectionY; sectionY <= maxSectionY; sectionY++) {
                for (int sectionZ = minSectionZ; sectionZ <= maxSectionZ; sectionZ++) {
                    if (sectionIntersectsSphere(sectionX, sectionY, sectionZ)) {
                        queue.add(new SectionWork(sectionX, sectionY, sectionZ, sectionDistanceSqr(sectionX, sectionY, sectionZ)));
                    }
                }
            }
        }

        queue.sort(Comparator.comparingDouble(SectionWork::distanceSqr));
        return queue;
    }

    private boolean sectionIntersectsSphere(int sectionX, int sectionY, int sectionZ) {
        double dx = axisDistanceToSection(center.x, sectionX);
        double dy = axisDistanceToSection(center.y, sectionY);
        double dz = axisDistanceToSection(center.z, sectionZ);
        return dx * dx + dy * dy + dz * dz <= radiusSqr;
    }

    private static double axisDistanceToSection(double coordinate, int section) {
        double min = section << 4;
        double max = min + 15.0D;
        if (coordinate < min) {
            return min - coordinate;
        }
        if (coordinate > max) {
            return coordinate - max;
        }
        return 0.0D;
    }

    private double sectionDistanceSqr(int sectionX, int sectionY, int sectionZ) {
        double dx = (sectionX << 4) + 7.5D - center.x;
        double dy = (sectionY << 4) + 7.5D - center.y;
        double dz = (sectionZ << 4) + 7.5D - center.z;
        return dx * dx + dy * dy + dz * dz;
    }

    private boolean isInsideSphere(int x, int y, int z) {
        double dx = x + 0.5D - center.x;
        double dy = y + 0.5D - center.y;
        double dz = z + 0.5D - center.z;
        return dx * dx + dy * dy + dz * dz <= radiusSqr;
    }

    private void updateCompletion() {
        complete = currentSection == null && nextSectionIndex >= sections.size();
    }

    private record SectionWork(int sectionX, int sectionY, int sectionZ, double distanceSqr) {
    }

    public record EvaporationResult(
            int sectionsProcessed,
            int blockChecks,
            int waterBlocksRemoved,
            int lavaBlocksRemoved,
            int waterloggedBlocksCleared,
            int skippedUnloadedSections,
            int skippedBlockEntities,
            boolean complete
    ) {
    }
}
