package com.skyeshade.skyent.content.explosion.destruction;

import com.skyeshade.skyent.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.List;
import java.util.Queue;

public final class NuclearColumnCollapsePass {
    private static final int COLUMN_COLLAPSE_MAX_RUNS_PER_COLUMN = 4;
    private static final int COLUMN_COLLAPSE_SCAN_DEPTH_BELOW_SURFACE = 96;
    private static final double CHARRED_LOG_RADIUS_SCALE = 0.85D;
    private static final double DEAD_VEGETATION_RADIUS_SCALE = 3.0D;
    private static final double AFTERMATH_FIRE_CHANCE = 0.035D;
    private static final long AFTERMATH_FIRE_RANDOM_SEED_SALT = 0x5A5A17F1C3D29B4DL;
    private static final int COLUMN_WORK_CHUNKS_PLANNED_PER_TICK = 2;
    private static final int COLUMN_WORK_MAX_COLUMNS_SCANNED_PER_TICK = 2_048;
    private static final int COLUMN_WORK_MAX_MUTATIONS_PER_TICK = 8_192;
    private static final int COLUMN_WORK_MAX_SECTIONS_PER_TICK = 32;
    private static final int DEFERRED_WORK_RETRY_INTERVAL_TICKS = 20;
    private static final int DEFERRED_WORK_CHECKS_PER_TICK = 64;

    private final ServerLevel level;
    private final List<ChunkWorkUnit> orderedWorkUnits;
    private final Queue<ChunkWorkUnit> readyWorkUnits = new ArrayDeque<>();
    private final Queue<ChunkWorkUnit> deferredWorkUnits = new ArrayDeque<>();
    private final NuclearSectionMutationPlan emptyPlan = new NuclearSectionMutationPlan();
    private final NuclearSectionCompletionTracker sectionCompletionTracker;
    private final Vec3 center;
    private final int centerX;
    private final int centerY;
    private final int centerZ;
    private final int minY;
    private final int maxY;
    private final int maxDropBlocks;
    private final double maxResistance;
    private final double collapseRadius;
    private final double collapseRadiusSqr;
    private final double charredLogRadius;
    private final double charredLogRadiusSqr;
    private final double deadVegetationRadius;
    private final double deadVegetationRadiusSqr;
    private final long seed;
    private final int workUnitsTotal;
    private final int maxRing;
    private int nextWorkUnitIndex;
    private int currentRing = -1;
    private NuclearSectionMutationPlan activePlan;
    private NuclearSectionMutationPlan currentLocalPlan;
    private NuclearPlannedBlockMutationQueue currentLocalMutationQueue;
    private ChunkWorkUnit currentWorkUnit;
    private boolean complete;
    private int tickCounter;
    private long totalColumnsProcessed;
    private long workUnitsCompleted;
    private long workUnitsPlanned;
    private long noOpWorkUnitsSkipped;
    private long totalMutationsApplied;
    private long totalSectionsMutated;
    private long mutationUnloadedSectionSkips;
    private long mutationBlockEntitySkips;
    private long placedFireBlocks;
    private long deferredColumnChecks;
    private long deferredColumnsQueued;
    private long deferredWorkUnitsRequeued;
    private boolean blockedByPendingSections;
    private ChunkWorkUnit blockedWorkUnit;
    private int blockedTicks;
    private int longestBlockedTicks;
    private long skippedUnloadedColumns;
    private long barriersEncountered;
    private long movableBlocksCollected;
    private long surfaceRunsFound;
    private long surfaceRunsMoved;
    private long totalDropDistance;
    private int maxDropDistanceSeen;
    private long skippedBarriersBeforeSurface;
    private long skippedFluidsBeforeSurface;
    private long skippedBlockEntitiesBeforeSurface;
    private long plannedMovementMutations;
    private long plannedCharredLogReplacements;
    private long plannedDeadGrassReplacements;
    private long plannedDeadLeafReplacements;
    private long plannedFireBlocks;
    private long plannedPlantRemovals;

    public NuclearColumnCollapsePass(
            ServerLevel level,
            Vec3 center,
            double radius,
            double maxResistance,
            int maxDropBlocks,
            long seed,
            NuclearSectionCompletionTracker sectionCompletionTracker
    ) {
        this.level = level;
        this.sectionCompletionTracker = sectionCompletionTracker;
        this.center = center;
        this.maxResistance = maxResistance;
        this.maxDropBlocks = Math.max(0, maxDropBlocks);
        this.seed = seed;
        this.centerX = Mth.floor(center.x);
        this.centerY = Mth.floor(center.y);
        this.centerZ = Mth.floor(center.z);
        this.collapseRadius = Math.max(0.0D, radius);
        this.collapseRadiusSqr = this.collapseRadius * this.collapseRadius;
        this.charredLogRadius = this.collapseRadius * CHARRED_LOG_RADIUS_SCALE;
        this.charredLogRadiusSqr = this.charredLogRadius * this.charredLogRadius;
        this.deadVegetationRadius = this.collapseRadius * DEAD_VEGETATION_RADIUS_SCALE;
        this.deadVegetationRadiusSqr = this.deadVegetationRadius * this.deadVegetationRadius;
        int blockRadius = Mth.ceil(this.collapseRadius);
        int aftermathBlockRadius = Mth.ceil(this.deadVegetationRadius);
        this.minY = Math.max(level.getMinBuildHeight(), this.centerY - blockRadius);
        this.maxY = Math.min(level.getMaxBuildHeight() - 1, this.centerY + blockRadius);
        List<ChunkWorkUnit> workUnits = new ArrayList<>();
        int minChunkX = Math.floorDiv(centerX - aftermathBlockRadius, 16);
        int maxChunkX = Math.floorDiv(centerX + aftermathBlockRadius, 16);
        int minChunkZ = Math.floorDiv(centerZ - aftermathBlockRadius, 16);
        int maxChunkZ = Math.floorDiv(centerZ + aftermathBlockRadius, 16);
        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                if (chunkIntersectsRadius(chunkX, chunkZ, deadVegetationRadiusSqr)) {
                    double chunkCenterX = chunkX * 16.0D + 8.0D;
                    double chunkCenterZ = chunkZ * 16.0D + 8.0D;
                    double dx = chunkCenterX - center.x;
                    double dz = chunkCenterZ - center.z;
                    int ring = Math.max(Math.abs(chunkX - Math.floorDiv(centerX, 16)), Math.abs(chunkZ - Math.floorDiv(centerZ, 16)));
                    workUnits.add(new ChunkWorkUnit(new ChunkPos(chunkX, chunkZ), dx * dx + dz * dz, ring));
                }
            }
        }

        workUnits.sort(Comparator
                .comparingInt(ChunkWorkUnit::ring)
                .thenComparingDouble(ChunkWorkUnit::distanceSqr));
        this.orderedWorkUnits = workUnits;
        workUnitsTotal = workUnits.size();
        maxRing = workUnits.stream().mapToInt(ChunkWorkUnit::ring).max().orElse(-1);
        fillNextRing();
        complete = orderedWorkUnits.isEmpty() || minY > maxY;
    }

    public CollapseResult tick(int maxColumns) {
        tickCounter++;
        int columnBudget = Math.min(maxColumns, COLUMN_WORK_MAX_COLUMNS_SCANNED_PER_TICK);
        int columnsThisTick = 0;
        int deferredThisTick = 0;
        int workUnitsPlannedThisTick = 0;
        int sectionsMutatedThisTick = 0;
        int mutationsAppliedThisTick = 0;
        long plannedThisTick = 0L;

        while (!complete) {
            if (currentLocalMutationQueue != null) {
                int remainingSections = COLUMN_WORK_MAX_SECTIONS_PER_TICK - sectionsMutatedThisTick;
                int remainingMutations = COLUMN_WORK_MAX_MUTATIONS_PER_TICK - mutationsAppliedThisTick;
                if (remainingSections <= 0 || remainingMutations <= 0) {
                    break;
                }

                NuclearPlannedBlockMutationQueue.MutationResult result = currentLocalMutationQueue.tick(
                        remainingSections,
                        remainingMutations
                );
                sectionsMutatedThisTick += result.sectionsTouched();
                mutationsAppliedThisTick += result.blocksChanged();
                totalSectionsMutated += result.sectionsTouched();
                totalMutationsApplied += result.blocksChanged();
                if (currentLocalMutationQueue.isComplete()) {
                    finishCurrentWorkUnit();
                }
                if (result.sectionsTouched() == 0 && result.blocksChanged() == 0 && currentLocalMutationQueue != null) {
                    break;
                }
                continue;
            }

            if (workUnitsPlannedThisTick >= COLUMN_WORK_CHUNKS_PLANNED_PER_TICK || columnsThisTick >= columnBudget) {
                break;
            }

            if (readyWorkUnits.isEmpty()) {
                retryDeferredWorkUnits();
                if (readyWorkUnits.isEmpty() && !deferredWorkUnits.isEmpty()) {
                    blockedByPendingSections = true;
                    blockedTicks++;
                    longestBlockedTicks = Math.max(longestBlockedTicks, blockedTicks);
                    break;
                }
                if (readyWorkUnits.isEmpty() && deferredWorkUnits.isEmpty()) {
                    fillNextRing();
                    if (readyWorkUnits.isEmpty()) {
                        updateComplete();
                        break;
                    }
                }
            }

            ChunkWorkUnit workUnit = readyWorkUnits.poll();
            if (workUnit == null) {
                updateComplete();
                break;
            }

            if (!level.hasChunk(workUnit.chunkPos().x, workUnit.chunkPos().z)) {
                skippedUnloadedColumns += 256L;
                workUnitsCompleted++;
                continue;
            }
            if (!isWorkUnitReady(workUnit)) {
                deferredWorkUnits.add(workUnit);
                deferredColumnsQueued += 256L;
                deferredColumnChecks++;
                blockedByPendingSections = true;
                blockedWorkUnit = workUnit;
                deferredThisTick++;
                continue;
            }

            blockedByPendingSections = false;
            blockedWorkUnit = null;
            blockedTicks = 0;
            currentWorkUnit = workUnit;
            currentLocalPlan = new NuclearSectionMutationPlan();
            activePlan = currentLocalPlan;
            int scanned = planWorkUnit(workUnit);
            activePlan = null;
            columnsThisTick += scanned;
            totalColumnsProcessed += scanned;
            workUnitsPlanned++;
            workUnitsPlannedThisTick++;
            plannedThisTick += currentLocalPlan.mutationCount();

            if (currentLocalPlan.isEmpty()) {
                noOpWorkUnitsSkipped++;
                finishCurrentWorkUnit();
                continue;
            }

            currentLocalMutationQueue = new NuclearPlannedBlockMutationQueue(level, currentLocalPlan, center);
        }

        updateComplete();

        return new CollapseResult(
                columnsThisTick,
                deferredThisTick,
                plannedThisTick,
                workUnitsPlannedThisTick,
                sectionsMutatedThisTick,
                mutationsAppliedThisTick,
                complete
        );
    }

    private boolean chunkIntersectsRadius(int chunkX, int chunkZ, double radiusSqr) {
        double minX = chunkX * 16.0D;
        double maxX = minX + 15.0D;
        double minZ = chunkZ * 16.0D;
        double maxZ = minZ + 15.0D;
        double closestX = Mth.clamp(center.x, minX, maxX);
        double closestZ = Mth.clamp(center.z, minZ, maxZ);
        double dx = closestX - center.x;
        double dz = closestZ - center.z;
        return dx * dx + dz * dz <= radiusSqr;
    }

    private void retryDeferredWorkUnits() {
        if (deferredWorkUnits.isEmpty() || (!sectionCompletionTracker.isExplosionMutationComplete() && tickCounter % DEFERRED_WORK_RETRY_INTERVAL_TICKS != 0)) {
            return;
        }

        int checks = Math.min(DEFERRED_WORK_CHECKS_PER_TICK, deferredWorkUnits.size());
        for (int index = 0; index < checks; index++) {
            ChunkWorkUnit workUnit = deferredWorkUnits.poll();
            if (workUnit == null) {
                return;
            }
            deferredColumnChecks++;
            if (isWorkUnitReady(workUnit)) {
                readyWorkUnits.add(workUnit);
                deferredWorkUnitsRequeued++;
            } else {
                deferredWorkUnits.add(workUnit);
                blockedByPendingSections = true;
                blockedWorkUnit = workUnit;
            }
        }
    }

    private void fillNextRing() {
        if (!readyWorkUnits.isEmpty() || !deferredWorkUnits.isEmpty()) {
            return;
        }
        if (nextWorkUnitIndex >= orderedWorkUnits.size()) {
            return;
        }

        currentRing = orderedWorkUnits.get(nextWorkUnitIndex).ring();
        while (nextWorkUnitIndex < orderedWorkUnits.size()) {
            ChunkWorkUnit workUnit = orderedWorkUnits.get(nextWorkUnitIndex);
            if (workUnit.ring() != currentRing) {
                break;
            }
            readyWorkUnits.add(workUnit);
            nextWorkUnitIndex++;
        }
        blockedByPendingSections = false;
        blockedWorkUnit = null;
        blockedTicks = 0;
    }

    private void finishCurrentWorkUnit() {
        if (currentLocalMutationQueue != null) {
            mutationUnloadedSectionSkips += currentLocalMutationQueue.unloadedSectionSkips();
            mutationBlockEntitySkips += currentLocalMutationQueue.blockEntitySkips();
            placedFireBlocks += currentLocalMutationQueue.totalFireBlocksPlaced();
            currentLocalMutationQueue.clear();
        }
        if (currentLocalPlan != null) {
            currentLocalPlan.clear();
        }
        currentLocalMutationQueue = null;
        currentLocalPlan = null;
        currentWorkUnit = null;
        workUnitsCompleted++;
    }

    private void updateComplete() {
        complete = readyWorkUnits.isEmpty()
                && deferredWorkUnits.isEmpty()
                && nextWorkUnitIndex >= orderedWorkUnits.size()
                && currentLocalMutationQueue == null
                && currentLocalPlan == null
                && currentWorkUnit == null;
    }

    private boolean isWorkUnitReady(ChunkWorkUnit workUnit) {
        if (sectionCompletionTracker.isExplosionMutationComplete()) {
            return true;
        }

        int minSectionY = Integer.MAX_VALUE;
        int maxSectionY = Integer.MIN_VALUE;
        int minBlockX = workUnit.chunkPos().getMinBlockX();
        int minBlockZ = workUnit.chunkPos().getMinBlockZ();
        for (int localX = 0; localX < 16; localX++) {
            int x = minBlockX + localX;
            for (int localZ = 0; localZ < 16; localZ++) {
                int z = minBlockZ + localZ;
                if (horizontalDistanceSqr(x, z) > deadVegetationRadiusSqr) {
                    continue;
                }
                int heightmapY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                int startY = startY(heightmapY);
                int endY = endY(startY, heightmapY);
                minSectionY = Math.min(minSectionY, endY >> 4);
                maxSectionY = Math.max(maxSectionY, startY >> 4);
            }
        }

        if (minSectionY == Integer.MAX_VALUE) {
            return true;
        }

        int sectionX = workUnit.chunkPos().x;
        int sectionZ = workUnit.chunkPos().z;
        for (int sectionY = minSectionY; sectionY <= maxSectionY; sectionY++) {
            if (sectionCompletionTracker.isSectionPending(sectionX, sectionY, sectionZ)) {
                return false;
            }
        }
        return true;
    }

    private int planWorkUnit(ChunkWorkUnit workUnit) {
        int columnsScanned = 0;
        int minBlockX = workUnit.chunkPos().getMinBlockX();
        int minBlockZ = workUnit.chunkPos().getMinBlockZ();
        for (int localX = 0; localX < 16; localX++) {
            int x = minBlockX + localX;
            for (int localZ = 0; localZ < 16; localZ++) {
                int z = minBlockZ + localZ;
                int distanceSqr = horizontalDistanceSqr(x, z);
                if (distanceSqr > deadVegetationRadiusSqr) {
                    continue;
                }
                planColumn(new ColumnKey(x, z, distanceSqr));
                columnsScanned++;
            }
        }
        return columnsScanned;
    }

    private int horizontalDistanceSqr(int x, int z) {
        int dx = x - centerX;
        int dz = z - centerZ;
        return dx * dx + dz * dz;
    }

    private void planColumn(ColumnKey column) {
        int heightmapY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column.x(), column.z());
        int startY = startY(heightmapY);
        int endY = endY(startY, heightmapY);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(column.x(), startY, column.z());
        int runsFoundInColumn = 0;
        boolean canCollapseColumn = column.distanceSqr() <= collapseRadiusSqr;

        for (int y = startY; y >= endY && runsFoundInColumn < COLUMN_COLLAPSE_MAX_RUNS_PER_COLUMN; y--) {
            pos.setY(y);
            BlockState state = level.getBlockState(pos);
            if (state.isAir()) {
                continue;
            }
            if (isBarrier(pos, state)) {
                countSurfaceBarrierSkip(pos, state);
                continue;
            }
            if (isMovable(pos, state)) {
                if (canCollapseColumn) {
                    y = planSurfaceRun(column, y, endY) - 1;
                    runsFoundInColumn++;
                } else {
                    planAftermathReplacement(column, y, state);
                }
                continue;
            }
            planAftermathReplacement(column, y, state);
        }

        // TODO: Later, irradiate or char the top 4-5 exposed blocks for this column after compaction.
        applyFutureSurfaceIrradiationHook(column);
    }

    private int planSurfaceRun(ColumnKey column, int runTopY, int endY) {
        List<BlockState> runStatesTopDown = new ArrayList<>();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(column.x(), runTopY, column.z());
        int runBottomY = runTopY;

        for (int y = runTopY; y >= endY; y--) {
            pos.setY(y);
            BlockState state = level.getBlockState(pos);
            if (isMovable(pos, state)) {
                runStatesTopDown.add(state);
                movableBlocksCollected++;
                runBottomY = y;
                continue;
            }
            if (!state.isAir() && isBarrier(pos, state)) {
                countSurfaceBarrierSkip(pos, state);
            } else if (!state.isAir()) {
                planAftermathReplacement(column, y, state);
            }
            break;
        }

        if (runStatesTopDown.isEmpty()) {
            return runBottomY;
        }
        surfaceRunsFound++;

        int dropDistance = findDropDistance(column, runBottomY, endY);
        if (dropDistance <= 0) {
            for (int index = 0; index < runStatesTopDown.size(); index++) {
                int sourceY = runTopY - index;
                planAftermathReplacement(column, sourceY, runStatesTopDown.get(index));
            }
            return runBottomY;
        }

        surfaceRunsMoved++;
        totalDropDistance += dropDistance;
        maxDropDistanceSeen = Math.max(maxDropDistanceSeen, dropDistance);

        for (int index = 0; index < runStatesTopDown.size(); index++) {
            int sourceY = runTopY - index;
            int targetY = sourceY - dropDistance;
            BlockState finalState = aftermathReplacement(column, targetY, runStatesTopDown.get(index));
            planSet(column.x(), targetY, column.z(), finalState);
            plannedMovementMutations++;
            countAftermathReplacement(runStatesTopDown.get(index), finalState);
            if (finalState.is(ModBlocks.DEAD_GRASS.get())) {
                planUnsupportedVegetationRemoval(column.x(), targetY + 1, column.z());
            }
            planSparseFireNearBlock(column, targetY, finalState);
        }

        for (int y = runTopY; y > runTopY - dropDistance; y--) {
            planSet(column.x(), y, column.z(), Blocks.AIR.defaultBlockState());
            plannedMovementMutations++;
        }
        return runBottomY;
    }

    private int findDropDistance(ColumnKey column, int runBottomY, int endY) {
        int dropDistance = 0;
        int lowestY = Math.max(endY, runBottomY - maxDropBlocks);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(column.x(), runBottomY - 1, column.z());
        for (int y = runBottomY - 1; y >= lowestY; y--) {
            pos.setY(y);
            BlockState state = level.getBlockState(pos);
            if (!state.isAir()) {
                break;
            }
            dropDistance++;
        }
        return dropDistance;
    }

    private void countSurfaceBarrierSkip(BlockPos pos, BlockState state) {
        barriersEncountered++;
        if (!state.getFluidState().isEmpty()) {
            skippedFluidsBeforeSurface++;
        } else if (level.getBlockEntity(pos) != null) {
            skippedBlockEntitiesBeforeSurface++;
        } else {
            skippedBarriersBeforeSurface++;
        }
    }

    private boolean isMovable(BlockPos pos, BlockState state) {
        if (state.isAir() || !state.getFluidState().isEmpty()) {
            return false;
        }
        if (level.getBlockEntity(pos) != null) {
            return false;
        }
        float resistance = state.getBlock().getExplosionResistance();
        return resistance >= 0.0F && resistance < maxResistance;
    }

    private boolean isBarrier(BlockPos pos, BlockState state) {
        if (state.isAir()) {
            return false;
        }
        if (!state.getFluidState().isEmpty() || level.getBlockEntity(pos) != null) {
            return true;
        }
        float resistance = state.getBlock().getExplosionResistance();
        return resistance < 0.0F || resistance >= maxResistance;
    }

    private void applyFutureSurfaceIrradiationHook(ColumnKey column) {
    }

    private void planAftermathReplacement(ColumnKey column, int y, BlockState state) {
        if (isSectionPending(column.x(), y, column.z())) {
            return;
        }
        BlockState replacement = aftermathReplacement(column, y, state);
        if (replacement == state) {
            planSparseFireNearBlock(column, y, state);
            return;
        }

        planSet(column.x(), y, column.z(), replacement);
        countAftermathReplacement(state, replacement);
        if (replacement.is(ModBlocks.DEAD_GRASS.get())) {
            planUnsupportedVegetationRemoval(column.x(), y + 1, column.z());
        }
        planSparseFireNearBlock(column, y, replacement);
    }

    private BlockState aftermathReplacement(ColumnKey column, int y, BlockState state) {
        if (state.isAir() || !state.getFluidState().isEmpty()) {
            return state;
        }

        double distanceSqr = column.distanceSqr();
        if (distanceSqr <= charredLogRadiusSqr && state.is(BlockTags.LOGS)) {
            return copyAxis(state, ModBlocks.CHARRED_LOG.get().defaultBlockState());
        }
        if (distanceSqr <= deadVegetationRadiusSqr) {
            BlockState deadVegetation = deadVegetationReplacement(state);
            if (deadVegetation != null) {
                return deadVegetation;
            }
        }
        return state;
    }

    private static BlockState deadVegetationReplacement(BlockState state) {
        if (state.is(Blocks.GRASS_BLOCK)) {
            return ModBlocks.DEAD_GRASS.get().defaultBlockState();
        }
        if (state.is(Blocks.SHORT_GRASS) || state.is(Blocks.FERN)) {
            return ModBlocks.DEAD_SHORT_GRASS.get().defaultBlockState();
        }
        if (state.is(Blocks.TALL_GRASS) || state.is(Blocks.LARGE_FERN)) {
            return copyHalf(state, ModBlocks.DEAD_TALL_GRASS.get().defaultBlockState());
        }
        if (state.is(BlockTags.LEAVES)) {
            return copyLeafProperties(state, deadLeavesReplacement(state));
        }
        return null;
    }

    private void planUnsupportedVegetationRemoval(int x, int y, int z) {
        if (y < level.getMinBuildHeight() || y >= level.getMaxBuildHeight() || isSectionPending(x, y, z)) {
            return;
        }

        BlockPos pos = new BlockPos(x, y, z);
        BlockState state = level.getBlockState(pos);
        if (!isUnsupportedDecorationAboveGrass(state)) {
            return;
        }

        planSet(pos, Blocks.AIR.defaultBlockState());
        plannedPlantRemovals++;
    }

    private static boolean isUnsupportedDecorationAboveGrass(BlockState state) {
        return state.is(BlockTags.FLOWERS)
                || state.is(Blocks.SHORT_GRASS)
                || state.is(Blocks.TALL_GRASS)
                || state.is(Blocks.FERN)
                || state.is(Blocks.LARGE_FERN)
                || state.is(Blocks.DEAD_BUSH)
                || state.is(Blocks.SNOW)
                || state.is(Blocks.SUNFLOWER)
                || state.is(Blocks.LILAC)
                || state.is(Blocks.ROSE_BUSH)
                || state.is(Blocks.PEONY);
    }

    private static BlockState deadLeavesReplacement(BlockState state) {
        if (state.is(Blocks.BIRCH_LEAVES)) {
            return ModBlocks.DEAD_BIRCH_LEAVES.get().defaultBlockState();
        }
        if (state.is(Blocks.SPRUCE_LEAVES)) {
            return ModBlocks.DEAD_SPRUCE_LEAVES.get().defaultBlockState();
        }
        if (state.is(Blocks.JUNGLE_LEAVES)) {
            return ModBlocks.DEAD_JUNGLE_LEAVES.get().defaultBlockState();
        }
        if (state.is(Blocks.ACACIA_LEAVES)) {
            return ModBlocks.DEAD_ACACIA_LEAVES.get().defaultBlockState();
        }
        if (state.is(Blocks.DARK_OAK_LEAVES)) {
            return ModBlocks.DEAD_DARK_OAK_LEAVES.get().defaultBlockState();
        }
        if (state.is(Blocks.MANGROVE_LEAVES)) {
            return ModBlocks.DEAD_MANGROVE_LEAVES.get().defaultBlockState();
        }
        if (state.is(Blocks.CHERRY_LEAVES)) {
            return ModBlocks.DEAD_CHERRY_LEAVES.get().defaultBlockState();
        }
        if (state.is(Blocks.AZALEA_LEAVES)) {
            return ModBlocks.DEAD_AZALEA_LEAVES.get().defaultBlockState();
        }
        if (state.is(Blocks.FLOWERING_AZALEA_LEAVES)) {
            return ModBlocks.DEAD_FLOWERING_AZALEA_LEAVES.get().defaultBlockState();
        }
        return ModBlocks.DEAD_OAK_LEAVES.get().defaultBlockState();
    }

    private static BlockState copyAxis(BlockState original, BlockState replacement) {
        if (original.hasProperty(BlockStateProperties.AXIS) && replacement.hasProperty(BlockStateProperties.AXIS)) {
            return replacement.setValue(BlockStateProperties.AXIS, original.getValue(BlockStateProperties.AXIS));
        }
        return replacement;
    }

    private static BlockState copyHalf(BlockState original, BlockState replacement) {
        if (original.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF) && replacement.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)) {
            return replacement.setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, original.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF));
        }
        return replacement;
    }

    private static BlockState copyLeafProperties(BlockState original, BlockState replacement) {
        BlockState copied = replacement;
        if (original.hasProperty(LeavesBlock.DISTANCE) && copied.hasProperty(LeavesBlock.DISTANCE)) {
            copied = copied.setValue(LeavesBlock.DISTANCE, original.getValue(LeavesBlock.DISTANCE));
        }
        if (original.hasProperty(LeavesBlock.PERSISTENT) && copied.hasProperty(LeavesBlock.PERSISTENT)) {
            copied = copied.setValue(LeavesBlock.PERSISTENT, original.getValue(LeavesBlock.PERSISTENT));
        }
        if (original.hasProperty(BlockStateProperties.WATERLOGGED) && copied.hasProperty(BlockStateProperties.WATERLOGGED)) {
            copied = copied.setValue(BlockStateProperties.WATERLOGGED, original.getValue(BlockStateProperties.WATERLOGGED));
        }
        return copied;
    }

    private void countAftermathReplacement(BlockState original, BlockState replacement) {
        if (replacement == original || replacement.is(original.getBlock())) {
            return;
        }
        if (replacement.is(ModBlocks.CHARRED_LOG.get())) {
            plannedCharredLogReplacements++;
        } else if (replacement.is(ModBlocks.DEAD_GRASS.get())
                || replacement.is(ModBlocks.DEAD_SHORT_GRASS.get())
                || replacement.is(ModBlocks.DEAD_TALL_GRASS.get())) {
            plannedDeadGrassReplacements++;
        } else if (replacement.getBlock() instanceof LeavesBlock) {
            plannedDeadLeafReplacements++;
        }
    }

    private void planSparseFireNearBlock(ColumnKey column, int y, BlockState state) {
        if (column.distanceSqr() > charredLogRadiusSqr || !isFlammableAftermathCandidate(state)) {
            return;
        }
        if (deterministicChance(column.x(), y, column.z()) >= AFTERMATH_FIRE_CHANCE) {
            return;
        }

        BlockPos.MutableBlockPos firePos = new BlockPos.MutableBlockPos(column.x(), y + 1, column.z());
        if (planFireAt(firePos)) {
            return;
        }

        firePos.set(column.x() + 1, y, column.z());
        if (planFireAt(firePos)) {
            return;
        }
        firePos.set(column.x() - 1, y, column.z());
        if (planFireAt(firePos)) {
            return;
        }
        firePos.set(column.x(), y, column.z() + 1);
        if (planFireAt(firePos)) {
            return;
        }
        firePos.set(column.x(), y, column.z() - 1);
        planFireAt(firePos);
    }

    private boolean planFireAt(BlockPos pos) {
        if (isSectionPending(pos.getX(), pos.getY(), pos.getZ())) {
            return false;
        }
        if (pos.getY() < level.getMinBuildHeight() || pos.getY() >= level.getMaxBuildHeight()) {
            return false;
        }
        if (!level.getBlockState(pos).isAir()) {
            return false;
        }
        BlockState fireState = Blocks.FIRE.defaultBlockState();
        if (!fireState.canSurvive(level, pos)) {
            return false;
        }

        planSet(pos, fireState);
        plannedFireBlocks++;
        return true;
    }

    private void planSet(BlockPos pos, BlockState state) {
        if (activePlan != null) {
            activePlan.planSet(pos, state);
        }
    }

    private void planSet(int x, int y, int z, BlockState state) {
        if (activePlan != null) {
            activePlan.planSet(x, y, z, state);
        }
    }

    private boolean isFlammableAftermathCandidate(BlockState state) {
        return state.ignitedByLava()
                || state.is(BlockTags.LOGS)
                || state.is(BlockTags.PLANKS)
                || state.is(BlockTags.LEAVES)
                || state.is(BlockTags.WOOL)
                || state.is(BlockTags.WOODEN_STAIRS)
                || state.is(BlockTags.WOODEN_SLABS)
                || state.is(BlockTags.WOODEN_FENCES)
                || state.is(BlockTags.WOODEN_DOORS)
                || state.is(BlockTags.WOODEN_TRAPDOORS)
                || state.is(ModBlocks.CHARRED_LOG.get());
    }

    private double deterministicChance(int x, int y, int z) {
        long value = seed ^ AFTERMATH_FIRE_RANDOM_SEED_SALT;
        value ^= (long) x * 0x9E3779B97F4A7C15L;
        value ^= (long) y * 0xC2B2AE3D27D4EB4FL;
        value ^= (long) z * 0x165667B19E3779F9L;
        value ^= value >>> 33;
        value *= 0xff51afd7ed558ccdL;
        value ^= value >>> 33;
        value *= 0xc4ceb9fe1a85ec53L;
        value ^= value >>> 33;
        return (value >>> 11) * 0x1.0p-53;
    }

    private int startY(int heightmapY) {
        return Mth.clamp(Math.max(heightmapY + 8, centerY + Mth.ceil(collapseRadius)), level.getMinBuildHeight(), level.getMaxBuildHeight() - 1);
    }

    private int endY(int startY, int heightmapY) {
        return Math.max(level.getMinBuildHeight(), Math.min(startY - COLUMN_COLLAPSE_SCAN_DEPTH_BELOW_SURFACE, heightmapY - COLUMN_COLLAPSE_SCAN_DEPTH_BELOW_SURFACE));
    }

    private boolean isSectionPending(int x, int y, int z) {
        return sectionCompletionTracker.isSectionPending(x >> 4, y >> 4, z >> 4);
    }

    public boolean isComplete() {
        return complete;
    }

    public int columnsRemaining() {
        return (readyWorkUnits.size() + deferredWorkUnits.size() + outerWorkUnitsNotStarted()) * 256
                + (currentWorkUnit == null ? 0 : 256);
    }

    public int deferredColumnsRemaining() {
        return deferredWorkUnits.size() * 256;
    }

    public long deferredColumnChecks() {
        return deferredColumnChecks;
    }

    public long deferredColumnsQueued() {
        return deferredColumnsQueued;
    }

    public int workUnitsTotal() {
        return workUnitsTotal;
    }

    public long workUnitsCompleted() {
        return workUnitsCompleted;
    }

    public int readyWorkUnitsRemaining() {
        return readyWorkUnits.size();
    }

    public int deferredWorkUnitsRemaining() {
        return deferredWorkUnits.size();
    }

    public int outerWorkUnitsNotStarted() {
        return Math.max(0, orderedWorkUnits.size() - nextWorkUnitIndex);
    }

    public int currentRing() {
        return currentRing;
    }

    public int maxRing() {
        return maxRing;
    }

    public boolean blockedByPendingSections() {
        return blockedByPendingSections && !deferredWorkUnits.isEmpty();
    }

    public int blockedRing() {
        return blockedWorkUnit == null ? currentRing : blockedWorkUnit.ring();
    }

    public int blockedTicks() {
        return blockedTicks;
    }

    public int longestBlockedTicks() {
        return longestBlockedTicks;
    }

    public String blockedWorkUnitDebug() {
        return blockedWorkUnit == null ? "none" : blockedWorkUnit.chunkPos().x + "," + blockedWorkUnit.chunkPos().z;
    }

    public long workUnitsPlanned() {
        return workUnitsPlanned;
    }

    public long noOpWorkUnitsSkipped() {
        return noOpWorkUnitsSkipped;
    }

    public long deferredWorkUnitsRequeued() {
        return deferredWorkUnitsRequeued;
    }

    public long totalMutationsApplied() {
        return totalMutationsApplied;
    }

    public long totalSectionsMutated() {
        return totalSectionsMutated;
    }

    public long mutationUnloadedSectionSkips() {
        return mutationUnloadedSectionSkips;
    }

    public long mutationBlockEntitySkips() {
        return mutationBlockEntitySkips;
    }

    public long placedFireBlocks() {
        return placedFireBlocks;
    }

    public int currentLocalPlannedMutations() {
        return currentLocalPlan == null ? 0 : (int) Math.min(currentLocalPlan.mutationCount(), Integer.MAX_VALUE);
    }

    public int currentLocalPlannedSections() {
        return currentLocalPlan == null ? 0 : currentLocalPlan.sectionCount();
    }

    public String currentWorkUnitDebug() {
        return currentWorkUnit == null ? "none" : currentWorkUnit.chunkPos().x + "," + currentWorkUnit.chunkPos().z;
    }

    public void clear() {
        readyWorkUnits.clear();
        deferredWorkUnits.clear();
        orderedWorkUnits.clear();
        if (currentLocalMutationQueue != null) {
            currentLocalMutationQueue.clear();
        }
        if (currentLocalPlan != null) {
            currentLocalPlan.clear();
        }
        currentLocalMutationQueue = null;
        currentLocalPlan = null;
        activePlan = null;
        currentWorkUnit = null;
        emptyPlan.clear();
        complete = true;
    }

    public NuclearSectionMutationPlan mutationPlan() {
        return currentLocalPlan == null ? emptyPlan : currentLocalPlan;
    }

    public long totalColumnsProcessed() {
        return totalColumnsProcessed;
    }

    public long skippedUnloadedColumns() {
        return skippedUnloadedColumns;
    }

    public long barriersEncountered() {
        return barriersEncountered;
    }

    public long movableBlocksCollected() {
        return movableBlocksCollected;
    }

    public long surfaceRunsFound() {
        return surfaceRunsFound;
    }

    public long surfaceRunsMoved() {
        return surfaceRunsMoved;
    }

    public double averageDropDistance() {
        return surfaceRunsMoved == 0 ? 0.0D : totalDropDistance / (double) surfaceRunsMoved;
    }

    public int maxDropDistanceSeen() {
        return maxDropDistanceSeen;
    }

    public int maxDropBlocks() {
        return maxDropBlocks;
    }

    public int maxRunsPerColumn() {
        return COLUMN_COLLAPSE_MAX_RUNS_PER_COLUMN;
    }

    public int scanDepthBelowSurface() {
        return COLUMN_COLLAPSE_SCAN_DEPTH_BELOW_SURFACE;
    }

    public double collapseRadius() {
        return collapseRadius;
    }

    public double charredLogRadius() {
        return charredLogRadius;
    }

    public double deadVegetationRadius() {
        return deadVegetationRadius;
    }

    public double fireRadius() {
        return charredLogRadius;
    }

    public long plannedMovementMutations() {
        return plannedMovementMutations;
    }

    public long plannedCharredLogReplacements() {
        return plannedCharredLogReplacements;
    }

    public long plannedDeadGrassReplacements() {
        return plannedDeadGrassReplacements;
    }

    public long plannedDeadLeafReplacements() {
        return plannedDeadLeafReplacements;
    }

    public long plannedFireBlocks() {
        return plannedFireBlocks;
    }

    public long plannedPlantRemovals() {
        return plannedPlantRemovals;
    }

    public long skippedBarriersBeforeSurface() {
        return skippedBarriersBeforeSurface;
    }

    public long skippedFluidsBeforeSurface() {
        return skippedFluidsBeforeSurface;
    }

    public long skippedBlockEntitiesBeforeSurface() {
        return skippedBlockEntitiesBeforeSurface;
    }

    public record CollapseResult(
            int columnsProcessed,
            int columnsDeferred,
            long mutationsPlanned,
            int workUnitsPlanned,
            int sectionsMutated,
            int mutationsApplied,
            boolean complete
    ) {
    }

    private record ColumnKey(int x, int z, int distanceSqr) {
    }

    private record ChunkWorkUnit(ChunkPos chunkPos, double distanceSqr, int ring) {
    }
}
