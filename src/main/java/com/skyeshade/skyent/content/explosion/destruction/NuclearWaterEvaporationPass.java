package com.skyeshade.skyent.content.explosion.destruction;

import com.skyeshade.skyent.config.SkyentNuclearExplosionConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;

public final class NuclearWaterEvaporationPass {
    private static final boolean EVAPORATE_WATER = true;
    private static final boolean EVAPORATE_LAVA = true;

    private final ServerLevel level;
    private final Vec3 center;
    private final int centerX;
    private final int centerY;
    private final int centerZ;
    private final int radius;
    private final int minY;
    private final int maxY;
    private final int recheckOverlap;
    private int currentInnerRadius = -1;
    private int currentOuterRadius = -1;
    private int currentXOffset;
    private int currentZOffset;
    private int currentY;
    private boolean complete;
    private long totalShellsProcessed;
    private long totalBlocksChecked;
    private long totalWaterBlocksRemoved;
    private long totalLavaBlocksRemoved;
    private long totalWaterloggedBlocksCleared;
    private long skippedUnloadedColumns;
    private long skippedBlockEntities;

    public NuclearWaterEvaporationPass(ServerLevel level, Vec3 center, int radius) {
        this.level = level;
        this.center = center;
        this.centerX = Mth.floor(center.x);
        this.centerY = Mth.floor(center.y);
        this.centerZ = Mth.floor(center.z);
        this.radius = Math.max(0, radius);
        this.minY = Math.max(level.getMinBuildHeight(), centerY - SkyentNuclearExplosionConfig.waterEvaporationVerticalRangeDown());
        this.maxY = Math.min(level.getMaxBuildHeight() - 1, centerY + SkyentNuclearExplosionConfig.waterEvaporationVerticalRangeUp());
        this.recheckOverlap = SkyentNuclearExplosionConfig.waterEvaporationRecheckOverlap();
        this.complete = this.radius <= 0 || minY > maxY;
    }

    public EvaporationResult tick(int radialLayersPerStep, int maxBlockChecks, int maxBlockChanges) {
        long startNs = System.nanoTime();
        int shellsProcessed = 0;
        int columnsScanned = 0;
        int blockChecks = 0;
        int blockChanges = 0;
        int waterBlocksRemoved = 0;
        int lavaBlocksRemoved = 0;
        int waterloggedBlocksCleared = 0;
        int skippedColumns = 0;
        int skippedBlockEntityCount = 0;
        boolean capHit = false;
        int stepBlocks = Math.max(1, radialLayersPerStep);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        BlockPos.MutableBlockPos chunkCheckPos = new BlockPos.MutableBlockPos();

        while (!complete && blockChecks < maxBlockChecks && blockChanges < maxBlockChanges) {
            if (currentOuterRadius < 0 || isCurrentShellComplete()) {
                if (currentOuterRadius >= radius) {
                    complete = true;
                    break;
                }
                startNextShell(stepBlocks);
                shellsProcessed++;
                totalShellsProcessed++;
            }

            int x = centerX + currentXOffset;
            int z = centerZ + currentZOffset;
            chunkCheckPos.set(x, centerY, z);
            if (!level.hasChunkAt(chunkCheckPos)) {
                skippedColumns++;
                skippedUnloadedColumns++;
                advanceColumn();
                continue;
            }

            if (currentY == minY) {
                columnsScanned++;
            }
            while (currentY <= maxY && blockChecks < maxBlockChecks && blockChanges < maxBlockChanges) {
                pos.set(x, currentY, z);
                currentY++;
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

            if (currentY <= maxY) {
                capHit = true;
                break;
            }
            advanceColumn();
        }

        if (!complete && (blockChecks >= maxBlockChecks || blockChanges >= maxBlockChanges)) {
            capHit = true;
        }

        return new EvaporationResult(
                shellsProcessed,
                columnsScanned,
                blockChecks,
                waterBlocksRemoved,
                lavaBlocksRemoved,
                waterloggedBlocksCleared,
                skippedColumns,
                skippedBlockEntityCount,
                complete,
                currentInnerRadius,
                currentOuterRadius,
                radius,
                currentY,
                (System.nanoTime() - startNs) / 1_000_000.0D,
                capHit
        );
    }

    public boolean isComplete() {
        return complete;
    }

    public int radius() {
        return radius;
    }

    public int sectionCount() {
        return radius <= 0 ? 0 : Mth.ceil(radius / 16.0D);
    }

    public int sectionsRemaining() {
        return complete ? 0 : Math.max(0, radius - Math.max(0, currentOuterRadius));
    }

    public int recheckOverlap() {
        return recheckOverlap;
    }

    public long totalSectionsProcessed() {
        return totalShellsProcessed;
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
        return skippedUnloadedColumns;
    }

    public long skippedBlockEntities() {
        return skippedBlockEntities;
    }

    public void clear() {
        complete = true;
    }

    private void startNextShell(int stepBlocks) {
        int previousOuterRadius = currentOuterRadius;
        currentInnerRadius = previousOuterRadius < 0 ? -1 : Math.max(0, previousOuterRadius - recheckOverlap);
        currentOuterRadius = Math.min(radius, Math.max(0, previousOuterRadius) + stepBlocks);
        currentXOffset = -currentOuterRadius;
        currentZOffset = -currentOuterRadius;
        currentY = minY;
        if (!isInCurrentShell()) {
            advanceColumn();
        }
    }

    private boolean isCurrentShellComplete() {
        return currentXOffset > currentOuterRadius;
    }

    private void advanceColumn() {
        do {
            currentZOffset++;
            if (currentZOffset > currentOuterRadius) {
                currentZOffset = -currentOuterRadius;
                currentXOffset++;
            }
            currentY = minY;
        } while (!isCurrentShellComplete() && !isInCurrentShell());
    }

    private boolean isInCurrentShell() {
        int distanceSqr = currentXOffset * currentXOffset + currentZOffset * currentZOffset;
        if (distanceSqr > currentOuterRadius * currentOuterRadius) {
            return false;
        }
        return currentInnerRadius < 0 || distanceSqr > currentInnerRadius * currentInnerRadius;
    }

    public record EvaporationResult(
            int sectionsProcessed,
            int columnsScanned,
            int blockChecks,
            int waterBlocksRemoved,
            int lavaBlocksRemoved,
            int waterloggedBlocksCleared,
            int skippedUnloadedSections,
            int skippedBlockEntities,
            boolean complete,
            int innerRadius,
            int currentRadius,
            int targetRadius,
            int currentY,
            double elapsedMs,
            boolean capHit
    ) {
    }
}
