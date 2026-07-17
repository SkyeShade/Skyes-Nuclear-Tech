package com.skyeshade.skyent.content.explosion.destruction;

import com.skyeshade.skyent.SkyesNuclearTech;
import com.skyeshade.skyent.config.SkyentNuclearExplosionConfig;
import com.skyeshade.skyent.content.entity.NuclearExplosionChunkLoading;
import com.skyeshade.skyent.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class NuclearColumnCollapsePass {
    private static final int COLUMN_COLLAPSE_MAX_RUNS_PER_COLUMN = 4;
    private static final int COLUMN_COLLAPSE_SCAN_DEPTH_BELOW_SURFACE = 96;
    private static final double CHARRED_LOG_RADIUS_SCALE = 0.85D;
    private static final double CONTAMINATED_GRASS_RADIUS_SCALE = 2.0D;
    private static final double CONTAMINATED_GRASS_FULL_RADIUS_SCALE = 1.65D;
    private static final double CONTAMINATED_GRASS_FEATHER_RADIUS_SCALE = 0.55D;
    private static final double DEAD_VEGETATION_RADIUS_SCALE = 3.0D;
    private static final double VITRIFICATION_RADIUS_SCALE = 1.0D;
    private static final double VITRIFICATION_FEATHER_RADIUS_SCALE = 0.25D;
    private static final double VITRIFICATION_GENERATION_SCALE = 1.4D;
    private static final double VITRIFICATION_BASELINE_RADIUS = 200.0D;
    private static final double VITRIFICATION_HOT_TIER_FALLOFF_POWER = 3.0D;
    private static final double VITRIFICATION_TIER_CURVE_POWER = 0.85D;
    private static final double VITRIFICATION_OUTER_FADE_STRENGTH = 0.035D;
    private static final double VITRIFICATION_MIN_EDGE_PLACEMENT_CHANCE = 0.0D;
    private static final double VITRIFICATION_STRENGTH_NOISE = 0.08D;
    private static final int VITRIFICATION_SURFACE_SCAN_DEPTH = 16;
    private static final double AFTERMATH_FIRE_CHANCE = 0.035D;
    private static final long AFTERMATH_FIRE_RANDOM_SEED_SALT = 0x5A5A17F1C3D29B4DL;
    private static final long VITRIFICATION_RANDOM_SEED_SALT = 0x6D1F2A9B4C8E3779L;
    private static final TagKey<Block> VITRIFIABLE_BLOCKS = BlockTags.create(
            ResourceLocation.fromNamespaceAndPath(SkyesNuclearTech.MOD_ID, "vitrifiable_blocks")
    );
    private static final int COLUMN_WORK_MAX_COLUMNS_SCANNED_PER_TICK = 65_536;
    private static final int TIME_BUDGET_CHECK_INTERVAL_COLUMNS = 64;
    private static final int COLUMN_WORK_MAX_MUTATIONS_PER_TICK = 8_192;
    private static final int COLUMN_WORK_MAX_SECTIONS_PER_TICK = 32;
    private static final int DEFERRED_WORK_RETRY_INTERVAL_TICKS = 20;
    private static final int DEFERRED_WORK_CHECKS_PER_TICK = 64;
    private static final boolean DEBUG_NUKE_MUTATIONS = Boolean.getBoolean("skyent.debugNukeMutations");

    private final ServerLevel level;
    private final List<ChunkWorkUnit> orderedWorkUnits;
    private final ArrayDeque<ChunkWorkUnit> readyWorkUnits = new ArrayDeque<>();
    private final ArrayDeque<ChunkWorkUnit> deferredWorkUnits = new ArrayDeque<>();
    private final NuclearSectionMutationPlan emptyPlan = new NuclearSectionMutationPlan();
    private final NuclearSectionCompletionTracker sectionCompletionTracker;
    private final UUID chunkTicketOwner;
    private final Set<ChunkPos> forcedMutationChunks = new HashSet<>();
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
    private final double leafEvaporationRadius;
    private final double leafEvaporationRadiusSqr;
    private final double contaminatedGrassRadius;
    private final double contaminatedGrassRadiusSqr;
    private final double contaminatedGrassFullRadius;
    private final double contaminatedGrassFeatherRadius;
    private final double deadVegetationRadius;
    private final double deadVegetationRadiusSqr;
    private final double vitrificationRadius;
    private final double vitrificationRadiusSqr;
    private final double vitrificationFeatherRadius;
    private final double unscaledVitrificationRadius;
    private final double unscaledVitrificationFeatherRadius;
    private final double vitrificationMaxRadius;
    private final double vitrificationMaxRadiusSqr;
    private final double radiusScale;
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
    private long mutationChunksForced;
    private long mutationChunksUnforced;
    private long mutationChunkLoadWaits;
    private long mutationChunkForceFailures;
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
    private long plannedLeafEvaporations;
    private long plannedContaminatedGrassReplacements;
    private long contaminatedGrassGuaranteed;
    private long contaminatedGrassFeathered;
    private long contaminatedGrassSkippedByNoise;
    private long deadGrassInContaminatedFeather;
    private long plannedDeadGrassReplacements;
    private long plannedDeadLeafReplacements;
    private long plannedVitrifiedStoneReplacements;
    private final long[] plannedVitrifiedTierReplacements = new long[7];
    private final long[] plannedVitrifiedTopTierCounts = new long[7];
    private final long[] vitrificationLowerTierCounts = new long[7];
    private final long[] vitrificationUpperTierCounts = new long[7];
    private final long[] vitrificationFinalTierCounts = new long[7];
    private long vitrificationDitherPromotions;
    private long vitrificationOuterFadeConsidered;
    private long vitrificationOuterFadePlaced;
    private long vitrificationOuterFadeSkipped;
    private long vitrificationWeakEdgePlacements;
    private long vitrificationContinuousTierSamples;
    private double vitrificationContinuousTierSum;
    private double vitrificationContinuousTierMin = Double.POSITIVE_INFINITY;
    private double vitrificationContinuousTierMax = Double.NEGATIVE_INFINITY;
    private long vitrificationColumnsConsidered;
    private long vitrificationColumnsChecked;
    private long vitrificationColumnsAffected;
    private long vitrificationSkippedOutsideRadius;
    private long vitrificationEdgeFeatherPlacements;
    private long vitrificationSkippedBySoftEdge;
    private long vitrificationSkippedNoSurface;
    private long vitrificationSkippedSurfaceAir;
    private long vitrificationSkippedNoVitrifiableBlock;
    private long vitrificationSkippedTagMismatch;
    private long vitrificationSkippedPlannedDeletion;
    private long vitrificationSkippedPlannedMovementConflict;
    private long vitrificationSkippedBlockEntity;
    private long vitrificationSkippedFluid;
    private long vitrificationSkippedUnloaded;
    private long vitrificationReplacementsPlanned;
    private int vitrificationDebugFailureLogs;
    private int vitrificationBoundaryDebugLogs;
    private int maxVitrificationDepthSeen;
    private long plannedFireBlocks;
    private long plannedPlantRemovals;

    public NuclearColumnCollapsePass(
            ServerLevel level,
            Vec3 center,
            double radius,
            double maxResistance,
            int maxDropBlocks,
            long seed,
            UUID chunkTicketOwner,
            NuclearSectionCompletionTracker sectionCompletionTracker
    ) {
        this.level = level;
        this.sectionCompletionTracker = sectionCompletionTracker;
        this.chunkTicketOwner = chunkTicketOwner;
        this.center = center;
        this.maxResistance = maxResistance;
        this.maxDropBlocks = Math.max(0, maxDropBlocks);
        this.seed = seed;
        this.centerX = Mth.floor(center.x);
        this.centerY = Mth.floor(center.y);
        this.centerZ = Mth.floor(center.z);
        this.collapseRadius = Math.max(0.0D, radius);
        this.collapseRadiusSqr = this.collapseRadius * this.collapseRadius;
        this.charredLogRadius = this.collapseRadius * CHARRED_LOG_RADIUS_SCALE * SkyentNuclearExplosionConfig.fireCharringRadiusMultiplier();
        this.charredLogRadiusSqr = this.charredLogRadius * this.charredLogRadius;
        this.leafEvaporationRadius = this.charredLogRadius * SkyentNuclearExplosionConfig.fireCharringLeafEvaporationInnerFraction();
        this.leafEvaporationRadiusSqr = this.leafEvaporationRadius * this.leafEvaporationRadius;
        this.contaminatedGrassFullRadius = this.collapseRadius * CONTAMINATED_GRASS_FULL_RADIUS_SCALE;
        this.contaminatedGrassFeatherRadius = this.collapseRadius * CONTAMINATED_GRASS_FEATHER_RADIUS_SCALE;
        this.contaminatedGrassRadius = this.contaminatedGrassFullRadius + this.contaminatedGrassFeatherRadius;
        this.contaminatedGrassRadiusSqr = this.contaminatedGrassRadius * this.contaminatedGrassRadius;
        this.deadVegetationRadius = this.collapseRadius * DEAD_VEGETATION_RADIUS_SCALE;
        this.deadVegetationRadiusSqr = this.deadVegetationRadius * this.deadVegetationRadius;
        this.unscaledVitrificationRadius = this.collapseRadius * VITRIFICATION_RADIUS_SCALE;
        this.unscaledVitrificationFeatherRadius = this.collapseRadius * VITRIFICATION_FEATHER_RADIUS_SCALE;
        this.vitrificationRadius = this.unscaledVitrificationRadius * VITRIFICATION_GENERATION_SCALE;
        this.vitrificationRadiusSqr = this.vitrificationRadius * this.vitrificationRadius;
        this.vitrificationFeatherRadius = this.unscaledVitrificationFeatherRadius * VITRIFICATION_GENERATION_SCALE;
        this.vitrificationMaxRadius = this.vitrificationRadius + this.vitrificationFeatherRadius;
        this.vitrificationMaxRadiusSqr = this.vitrificationMaxRadius * this.vitrificationMaxRadius;
        this.radiusScale = Math.max(0.05D, this.collapseRadius / VITRIFICATION_BASELINE_RADIUS);
        int blockRadius = Mth.ceil(this.collapseRadius);
        int aftermathBlockRadius = Mth.ceil(Math.max(this.deadVegetationRadius, this.vitrificationRadius));
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

    public CollapseResult tick(int maxWorkUnits, int maxColumns, double maxMilliseconds) {
        long startNs = System.nanoTime();
        long maxNs = Math.max(1L, (long) (Math.max(0.0D, maxMilliseconds) * 1_000_000.0D));
        tickCounter++;
        int workUnitBudget = Math.max(1, maxWorkUnits);
        int columnBudget = Math.min(maxColumns, COLUMN_WORK_MAX_COLUMNS_SCANNED_PER_TICK);
        int columnsThisTick = 0;
        int deferredThisTick = 0;
        int unloadedWorkUnitsSkippedThisTick = 0;
        int workUnitsPlannedThisTick = 0;
        int workUnitsProcessedThisTick = 0;
        int sectionsMutatedThisTick = 0;
        int mutationsAppliedThisTick = 0;
        long plannedThisTick = 0L;
        boolean stoppedByWorkBudget = false;
        boolean stoppedByColumnBudget = false;
        boolean stoppedByTimeBudget = false;
        boolean stoppedByNoWork = false;

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
                    workUnitsProcessedThisTick++;
                }
                if (System.nanoTime() - startNs >= maxNs && (result.sectionsTouched() > 0 || result.blocksChanged() > 0)) {
                    stoppedByTimeBudget = true;
                    break;
                }
                if (result.sectionsTouched() == 0 && result.blocksChanged() == 0 && currentLocalMutationQueue != null) {
                    stoppedByNoWork = true;
                    break;
                }
                continue;
            }

            if (workUnitsProcessedThisTick >= workUnitBudget) {
                stoppedByWorkBudget = true;
                break;
            }
            if (columnsThisTick >= columnBudget) {
                stoppedByColumnBudget = true;
                break;
            }
            if ((columnsThisTick == 0 || columnsThisTick % TIME_BUDGET_CHECK_INTERVAL_COLUMNS == 0)
                    && System.nanoTime() - startNs >= maxNs
                    && (workUnitsProcessedThisTick > 0 || columnsThisTick > 0 || sectionsMutatedThisTick > 0 || mutationsAppliedThisTick > 0)) {
                stoppedByTimeBudget = true;
                break;
            }

            if (readyWorkUnits.isEmpty()) {
                retryDeferredWorkUnits();
                if (readyWorkUnits.isEmpty() && !deferredWorkUnits.isEmpty()) {
                    blockedByPendingSections = true;
                    blockedTicks++;
                    longestBlockedTicks = Math.max(longestBlockedTicks, blockedTicks);
                    stoppedByNoWork = true;
                    break;
                }
                if (readyWorkUnits.isEmpty() && deferredWorkUnits.isEmpty()) {
                    fillNextRing();
                    if (readyWorkUnits.isEmpty()) {
                        updateComplete();
                        stoppedByNoWork = !complete;
                        break;
                    }
                }
            }

            ChunkWorkUnit workUnit = readyWorkUnits.poll();
            if (workUnit == null) {
                updateComplete();
                stoppedByNoWork = !complete;
                break;
            }

            if (!ensureWorkUnitChunkReady(workUnit)) {
                readyWorkUnits.addFirst(workUnit);
                unloadedWorkUnitsSkippedThisTick++;
                stoppedByNoWork = true;
                break;
            }
            if (!isWorkUnitReady(workUnit)) {
                releaseMutationChunk(workUnit.chunkPos());
                deferredWorkUnits.add(workUnit);
                deferredColumnsQueued += 256L;
                deferredColumnChecks++;
                blockedByPendingSections = true;
                blockedWorkUnit = workUnit;
                deferredThisTick++;
                workUnitsProcessedThisTick++;
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
            workUnitsProcessedThisTick++;
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
                unloadedWorkUnitsSkippedThisTick,
                plannedThisTick,
                workUnitsPlannedThisTick,
                workUnitsProcessedThisTick,
                sectionsMutatedThisTick,
                mutationsAppliedThisTick,
                complete,
                (System.nanoTime() - startNs) / 1_000_000.0D,
                stoppedByWorkBudget,
                stoppedByColumnBudget,
                stoppedByTimeBudget,
                stoppedByNoWork
        );
    }

    public CollapseResult tick(int maxColumns) {
        return tick(2, maxColumns, Double.MAX_VALUE);
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
        ChunkPos finishedChunk = currentWorkUnit == null ? null : currentWorkUnit.chunkPos();
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
        releaseMutationChunk(finishedChunk);
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

        planVitrifiedSurfaceLayer(column, startY, endY);
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
            if (removesUnsupportedVegetationAbove(finalState)) {
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

    private void planAftermathReplacement(ColumnKey column, int y, BlockState state) {
        if (isSectionPending(column.x(), y, column.z())) {
            return;
        }
        BlockState replacement = aftermathReplacement(column, y, state);
        if (isDeadPlantReplacement(replacement)) {
            BlockPos plantPos = new BlockPos(column.x(), y, column.z());
            if (!replacement.canSurvive(level, plantPos)) {
                replacement = Blocks.AIR.defaultBlockState();
            }
        }
        if (replacement == state) {
            planSparseFireNearBlock(column, y, state);
            return;
        }

        planSet(column.x(), y, column.z(), replacement);
        countAftermathReplacement(state, replacement);
        if (removesUnsupportedVegetationAbove(replacement)) {
            planUnsupportedVegetationRemoval(column.x(), y + 1, column.z());
        }
        planSparseFireNearBlock(column, y, replacement);
    }

    private BlockState aftermathReplacement(ColumnKey column, int y, BlockState state) {
        if (state.isAir() || !state.getFluidState().isEmpty()) {
            return state;
        }

        double distanceSqr = column.distanceSqr();
        if (distanceSqr <= leafEvaporationRadiusSqr && state.is(BlockTags.LEAVES)) {
            return Blocks.AIR.defaultBlockState();
        }
        if (distanceSqr <= charredLogRadiusSqr && state.is(BlockTags.LOGS)) {
            return copyAxis(state, ModBlocks.CHARRED_LOG.get().defaultBlockState());
        }
        if (distanceSqr <= deadVegetationRadiusSqr) {
            if (isGrassAftermathBlock(state)) {
                return grassBlockAftermathReplacement(column, state);
            }
            BlockState deadVegetation = deadVegetationReplacement(state);
            if (deadVegetation != null) {
                return deadVegetation;
            }
        }
        return state;
    }

    private BlockState grassBlockAftermathReplacement(ColumnKey column, BlockState currentState) {
        double distance = Math.sqrt(column.distanceSqr());
        double chance = contaminatedGrassChance(distance);
        if (chance >= 1.0D) {
            contaminatedGrassGuaranteed++;
            return ModBlocks.CONTAMINATED_GRASS_BLOCK.get().defaultBlockState();
        }
        if (chance > 0.0D && deterministicColumnNoise(column.x(), column.z()) < chance) {
            contaminatedGrassFeathered++;
            return ModBlocks.CONTAMINATED_GRASS_BLOCK.get().defaultBlockState();
        }
        if (chance > 0.0D) {
            contaminatedGrassSkippedByNoise++;
            deadGrassInContaminatedFeather++;
        }
        if (currentState.is(ModBlocks.CONTAMINATED_GRASS_BLOCK.get())) {
            return currentState;
        }
        if (currentState.is(ModBlocks.DEAD_GRASS.get())) {
            return currentState;
        }
        return ModBlocks.DEAD_GRASS.get().defaultBlockState();
    }

    private double contaminatedGrassChance(double distance) {
        if (distance <= contaminatedGrassFullRadius) {
            return 1.0D;
        }
        double contaminatedMaxRadius = contaminatedGrassFullRadius + contaminatedGrassFeatherRadius;
        if (distance > contaminatedMaxRadius || contaminatedGrassFeatherRadius <= 0.0D) {
            return 0.0D;
        }
        double t = (distance - contaminatedGrassFullRadius) / contaminatedGrassFeatherRadius;
        return 1.0D - smoothstep(t);
    }

    private static double smoothstep(double t) {
        t = Mth.clamp(t, 0.0D, 1.0D);
        return t * t * (3.0D - 2.0D * t);
    }

    private void planVitrifiedSurfaceLayer(ColumnKey column, int startY, int endY) {
        vitrificationColumnsConsidered++;
        if (vitrificationRadius <= 0.0D || column.distanceSqr() > vitrificationMaxRadiusSqr) {
            vitrificationSkippedOutsideRadius++;
            return;
        }
        vitrificationColumnsChecked++;

        double distance = Math.sqrt(column.distanceSqr());
        double noise = deterministicColumnNoise(column.x(), column.z());
        double normalizedDistance = Mth.clamp(distance / vitrificationRadius, 0.0D, 1.0D);
        double core = Math.max(0.0D, 1.0D - normalizedDistance);
        double tierIntensity = Math.pow(core, VITRIFICATION_HOT_TIER_FALLOFF_POWER);
        double featherMultiplier = 1.0D;
        if (distance > vitrificationRadius) {
            double edgeProgress = Mth.clamp((distance - vitrificationRadius) / Math.max(1.0D, vitrificationFeatherRadius), 0.0D, 1.0D);
            featherMultiplier = 1.0D - smoothstep(edgeProgress);
        }
        double placementStrength = tierIntensity * featherMultiplier;
        double shapeNoise = deterministicColumnNoise(column.x() + 34123, column.z() - 9127) - 0.5D;
        double noisyStrength = Mth.clamp(
                placementStrength + shapeNoise * VITRIFICATION_STRENGTH_NOISE,
                0.0D,
                1.0D
        );
        int sizeTierCap = sizeTierCap();

        int lowerTier;
        int upperTier;
        double tierFraction;
        int topTier;
        boolean ditherPromoted = false;
        boolean weakEdgePlacement = false;
        boolean skippedByOuterFade = false;
        double edgeChance = 1.0D;
        double edgeNoise = deterministicColumnNoise(column.x() + 19237, column.z() - 7193);
        double continuousTier;
        if (noisyStrength <= 0.0D) {
            continuousTier = 0.0D;
            recordVitrificationContinuousTier(continuousTier);
            if (distance > vitrificationRadius) {
                vitrificationSkippedBySoftEdge++;
            }
            vitrificationSkippedNoVitrifiableBlock++;
            vitrificationOuterFadeSkipped++;
            logVitrificationBoundarySample(column, distance, startY, endY, Integer.MIN_VALUE, Integer.MIN_VALUE, continuousTier, 0, 0, 0.0D, false, false, true, false, "zero_strength", tierIntensity, featherMultiplier, placementStrength, noisyStrength, edgeChance, edgeNoise, noise);
            return;
        }
        if (noisyStrength < VITRIFICATION_OUTER_FADE_STRENGTH) {
            vitrificationOuterFadeConsidered++;
            edgeChance = Math.max(
                    VITRIFICATION_MIN_EDGE_PLACEMENT_CHANCE,
                    smoothstep(noisyStrength / VITRIFICATION_OUTER_FADE_STRENGTH)
            );
            if (edgeNoise > edgeChance) {
                skippedByOuterFade = true;
                if (distance > vitrificationRadius) {
                    vitrificationSkippedBySoftEdge++;
                }
                vitrificationSkippedNoVitrifiableBlock++;
                vitrificationOuterFadeSkipped++;
                continuousTier = Math.pow(noisyStrength, VITRIFICATION_TIER_CURVE_POWER) * sizeTierCap;
                recordVitrificationContinuousTier(continuousTier);
                logVitrificationBoundarySample(column, distance, startY, endY, Integer.MIN_VALUE, Integer.MIN_VALUE, continuousTier, 0, 0, 0.0D, false, false, true, skippedByOuterFade, "outer_fade_skip", tierIntensity, featherMultiplier, placementStrength, noisyStrength, edgeChance, edgeNoise, noise);
                return;
            }
            continuousTier = 0.0D;
            recordVitrificationContinuousTier(continuousTier);
            lowerTier = 0;
            upperTier = 0;
            tierFraction = 0.0D;
            topTier = 0;
            weakEdgePlacement = true;
            vitrificationWeakEdgePlacements++;
            vitrificationOuterFadePlaced++;
        } else {
            continuousTier = Math.pow(noisyStrength, VITRIFICATION_TIER_CURVE_POWER) * sizeTierCap;
            recordVitrificationContinuousTier(continuousTier);
            lowerTier = Mth.clamp(Mth.floor(continuousTier), 0, sizeTierCap);
            upperTier = Math.min(sizeTierCap, lowerTier + 1);
            tierFraction = Mth.clamp(continuousTier - lowerTier, 0.0D, 1.0D);
            if (noise < tierFraction) {
                topTier = upperTier;
                ditherPromoted = upperTier > lowerTier;
            } else {
                topTier = lowerTier;
            }
            if (ditherPromoted) {
                vitrificationDitherPromotions++;
            }
        }
        vitrificationLowerTierCounts[lowerTier]++;
        vitrificationUpperTierCounts[upperTier]++;
        vitrificationFinalTierCounts[topTier]++;
        if (distance > vitrificationRadius) {
            vitrificationEdgeFeatherPlacements++;
        }

        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(column.x(), startY, column.z());
        int surfaceY = findPostCollapseSurfaceY(column, startY, endY, pos);
        if (surfaceY == Integer.MIN_VALUE) {
            vitrificationSkippedNoSurface++;
            logVitrificationFailure(column, distance, startY, endY, Integer.MIN_VALUE, "no_surface");
            logVitrificationBoundarySample(column, distance, startY, endY, Integer.MIN_VALUE, Integer.MIN_VALUE, continuousTier, lowerTier, upperTier, tierFraction, ditherPromoted, weakEdgePlacement, false, skippedByOuterFade, "no_surface", tierIntensity, featherMultiplier, placementStrength, noisyStrength, edgeChance, edgeNoise, noise);
            return;
        }

        int replacements = 0;
        int tagMismatches = 0;
        int firstVitrifiableY = Integer.MIN_VALUE;
        int lowestScanY = Math.max(endY, surfaceY - VITRIFICATION_SURFACE_SCAN_DEPTH);
        for (int y = surfaceY; y >= lowestScanY && replacements <= topTier; y--) {
            if (isSectionPending(column.x(), y, column.z())) {
                vitrificationSkippedUnloaded++;
                continue;
            }
            pos.setY(y);
            BlockState state = stateForVitrificationScan(column.x(), y, column.z(), pos);
            if (state.isAir()) {
                if (isPlannedAir(column.x(), y, column.z())) {
                    vitrificationSkippedPlannedDeletion++;
                } else if (y == surfaceY) {
                    vitrificationSkippedSurfaceAir++;
                }
                continue;
            }
            if (!state.getFluidState().isEmpty()) {
                vitrificationSkippedFluid++;
                break;
            }
            if (level.getBlockEntity(pos) != null) {
                vitrificationSkippedBlockEntity++;
                continue;
            }

            if (!state.is(VITRIFIABLE_BLOCKS) && vitrifiedSeverity(state) <= 0) {
                tagMismatches++;
                if (isSoftVitrificationCover(state)) {
                    continue;
                }
                vitrificationSkippedPlannedMovementConflict++;
                break;
            }

            if (firstVitrifiableY == Integer.MIN_VALUE) {
                firstVitrifiableY = y;
            }
            int tier = Math.max(0, topTier - replacements);
            BlockState replacement = combineVitrifiedState(state, tier);
            if (state.is(replacement.getBlock())) {
                replacements++;
                continue;
            }
            planSet(pos, replacement);
            plannedVitrifiedStoneReplacements++;
            vitrificationReplacementsPlanned++;
            plannedVitrifiedTierReplacements[tier]++;
            replacements++;
        }

        if (replacements > 0) {
            vitrificationColumnsAffected++;
            plannedVitrifiedTopTierCounts[topTier]++;
            maxVitrificationDepthSeen = Math.max(maxVitrificationDepthSeen, replacements);
            logVitrificationBoundarySample(column, distance, startY, endY, surfaceY, firstVitrifiableY, continuousTier, lowerTier, upperTier, tierFraction, ditherPromoted, weakEdgePlacement, true, skippedByOuterFade, "planned", tierIntensity, featherMultiplier, placementStrength, noisyStrength, edgeChance, edgeNoise, noise);
        } else {
            vitrificationSkippedNoVitrifiableBlock++;
            vitrificationSkippedTagMismatch += tagMismatches;
            logVitrificationFailure(column, distance, startY, endY, surfaceY, "no_vitrifiable_block");
            logVitrificationBoundarySample(column, distance, startY, endY, surfaceY, firstVitrifiableY, continuousTier, lowerTier, upperTier, tierFraction, ditherPromoted, weakEdgePlacement, false, skippedByOuterFade, "no_vitrifiable_block", tierIntensity, featherMultiplier, placementStrength, noisyStrength, edgeChance, edgeNoise, noise);
        }
    }

    private int findPostCollapseSurfaceY(ColumnKey column, int startY, int endY, BlockPos.MutableBlockPos pos) {
        for (int y = startY; y >= endY; y--) {
            if (isSectionPending(column.x(), y, column.z())) {
                continue;
            }
            pos.setY(y);
            BlockState state = stateForVitrificationScan(column.x(), y, column.z(), pos);
            if (!state.isAir() && state.getFluidState().isEmpty()) {
                return y;
            }
        }
        return Integer.MIN_VALUE;
    }

    private BlockState plannedOrWorldState(int x, int y, int z, BlockPos pos) {
        if (activePlan != null) {
            BlockState planned = activePlan.plannedState(x, y, z);
            if (planned != null) {
                return planned;
            }
        }
        return level.getBlockState(pos);
    }

    private BlockState stateForVitrificationScan(int x, int y, int z, BlockPos pos) {
        BlockState actual = level.getBlockState(pos);
        if (activePlan == null) {
            return actual;
        }
        BlockState planned = activePlan.plannedState(x, y, z);
        if (planned == null) {
            return actual;
        }
        if (planned.isAir()) {
            return planned;
        }
        if (isCosmeticAftermathState(planned)) {
            return actual;
        }
        return planned;
    }

    private boolean isPlannedAir(int x, int y, int z) {
        return activePlan != null
                && activePlan.plannedState(x, y, z) != null
                && activePlan.plannedState(x, y, z).isAir();
    }

    private static boolean isSoftVitrificationCover(BlockState state) {
        return state.is(Blocks.GRASS_BLOCK)
                || state.is(ModBlocks.CONTAMINATED_GRASS_BLOCK.get())
                || state.is(ModBlocks.DEAD_GRASS.get())
                || state.is(Blocks.DIRT)
                || state.is(Blocks.COARSE_DIRT)
                || state.is(Blocks.ROOTED_DIRT)
                || state.is(Blocks.PODZOL)
                || state.is(Blocks.MYCELIUM)
                || state.is(Blocks.SNOW)
                || state.is(BlockTags.FLOWERS)
                || state.is(Blocks.SHORT_GRASS)
                || state.is(Blocks.TALL_GRASS)
                || state.is(Blocks.FERN)
                || state.is(Blocks.LARGE_FERN)
                || state.is(Blocks.DEAD_BUSH);
    }

    private static boolean isCosmeticAftermathState(BlockState state) {
        return isGrassAftermathState(state)
                || state.is(ModBlocks.DEAD_SHORT_GRASS.get())
                || state.is(ModBlocks.DEAD_TALL_GRASS.get())
                || state.is(BlockTags.LEAVES)
                || state.is(Blocks.FIRE);
    }

    private static boolean isGrassAftermathState(BlockState state) {
        return state.is(ModBlocks.CONTAMINATED_GRASS_BLOCK.get()) || state.is(ModBlocks.DEAD_GRASS.get());
    }

    private void logVitrificationFailure(ColumnKey column, double distance, int startY, int endY, int surfaceY, String reason) {
        if (!SkyentNuclearExplosionConfig.debugContamination() || vitrificationDebugFailureLogs >= 20) {
            return;
        }
        vitrificationDebugFailureLogs++;
        StringBuilder scanned = new StringBuilder();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(column.x(), surfaceY == Integer.MIN_VALUE ? startY : surfaceY, column.z());
        int scanStart = surfaceY == Integer.MIN_VALUE ? startY : surfaceY;
        int scanEnd = Math.max(endY, scanStart - 7);
        for (int y = scanStart; y >= scanEnd; y--) {
            pos.setY(y);
            BlockState actual = level.getBlockState(pos);
            BlockState planned = activePlan == null ? null : activePlan.plannedState(column.x(), y, column.z());
            BlockState effective = stateForVitrificationScan(column.x(), y, column.z(), pos);
            if (!scanned.isEmpty()) {
                scanned.append(" | ");
            }
            scanned.append("y=").append(y)
                    .append(" actual=").append(actual.getBlock().builtInRegistryHolder().key().location())
                    .append(" planned=").append(planned == null ? "none" : planned.getBlock().builtInRegistryHolder().key().location())
                    .append(" effective=").append(effective.getBlock().builtInRegistryHolder().key().location())
                    .append(" air=").append(effective.isAir())
                    .append(" fluid=").append(!effective.getFluidState().isEmpty())
                    .append(" vitrifiable=").append(effective.is(VITRIFIABLE_BLOCKS))
                    .append(" blockEntity=").append(level.getBlockEntity(pos) != null);
        }
        SkyesNuclearTech.LOGGER.info(
                "Nuke vitrification column skipped: reason={} x={} z={} distance={} hardRadius={} featherRadius={} startY={} endY={} surfaceY={} scanned={}",
                reason,
                column.x(),
                column.z(),
                distance,
                vitrificationRadius,
                vitrificationFeatherRadius,
                startY,
                endY,
                surfaceY == Integer.MIN_VALUE ? "none" : Integer.toString(surfaceY),
                scanned
        );
    }

    private void logVitrificationBoundarySample(
            ColumnKey column,
            double distance,
            int startY,
            int endY,
            int surfaceY,
            int firstVitrifiableY,
            double continuousTier,
            int lowerTier,
            int upperTier,
            double tierFraction,
            boolean ditherPromoted,
            boolean weakEdgePlacement,
            boolean plannedVitrification,
            boolean skippedByOuterFade,
            String reason,
            double tierIntensity,
            double featherMultiplier,
            double placementStrength,
            double noisyStrength,
            double edgeChance,
            double edgeNoise,
            double noise
    ) {
        if (!SkyentNuclearExplosionConfig.debugContamination()
                || vitrificationBoundaryDebugLogs >= 20
                || !isNearVitrificationDebugBoundary(distance)) {
            return;
        }
        vitrificationBoundaryDebugLogs++;
        boolean plannedBiologicalOverlay = hasPlannedBiologicalOverlay(column, surfaceY == Integer.MIN_VALUE ? startY : surfaceY, endY);
        SkyesNuclearTech.LOGGER.info(
                "Nuke vitrification boundary sample: x={} z={} distance={} contaminatedGrassRadius={} deadVegetationRadius={} vitrificationRadius={} vitrificationFeatherRadius={} tierIntensity={} featherMultiplier={} placementStrength={} noisyStrength={} outerFadeStrength={} edgeChance={} edgeNoise={} skippedByOuterFade={} continuousTier={} lowerTier={} upperTier={} tierFraction={} noise={} ditherPromoted={} weakEdgePlacement={} sizeTierCap={} startY={} endY={} scanStartY={} firstVitrifiableY={} plannedBiologicalOverlay={} vitrificationPlanned={} reason={}",
                column.x(),
                column.z(),
                distance,
                contaminatedGrassRadius,
                deadVegetationRadius,
                vitrificationRadius,
                vitrificationFeatherRadius,
                tierIntensity,
                featherMultiplier,
                placementStrength,
                noisyStrength,
                VITRIFICATION_OUTER_FADE_STRENGTH,
                edgeChance,
                edgeNoise,
                skippedByOuterFade,
                continuousTier,
                lowerTier,
                upperTier,
                tierFraction,
                noise,
                ditherPromoted,
                weakEdgePlacement,
                sizeTierCap(),
                startY,
                endY,
                surfaceY == Integer.MIN_VALUE ? "none" : Integer.toString(surfaceY),
                firstVitrifiableY == Integer.MIN_VALUE ? "none" : Integer.toString(firstVitrifiableY),
                plannedBiologicalOverlay,
                plannedVitrification,
                reason
        );
    }

    private void recordVitrificationContinuousTier(double continuousTier) {
        vitrificationContinuousTierSamples++;
        vitrificationContinuousTierSum += continuousTier;
        vitrificationContinuousTierMin = Math.min(vitrificationContinuousTierMin, continuousTier);
        vitrificationContinuousTierMax = Math.max(vitrificationContinuousTierMax, continuousTier);
    }

    private boolean isNearVitrificationDebugBoundary(double distance) {
        return Math.abs(distance - contaminatedGrassRadius) < 2.0D
                || Math.abs(distance - vitrificationRadius) < 2.0D
                || Math.abs(distance - vitrificationMaxRadius) < 2.0D;
    }

    private boolean hasPlannedBiologicalOverlay(ColumnKey column, int scanStartY, int endY) {
        if (activePlan == null) {
            return false;
        }
        int scanEndY = Math.max(endY, scanStartY - VITRIFICATION_SURFACE_SCAN_DEPTH);
        for (int y = scanStartY; y >= scanEndY; y--) {
            BlockState planned = activePlan.plannedState(column.x(), y, column.z());
            if (planned != null && isCosmeticAftermathState(planned)) {
                return true;
            }
        }
        return false;
    }

    private int sizeTierCap() {
        if (radiusScale < 0.20D) {
            return 2;
        }
        if (radiusScale < 0.50D) {
            return 3;
        }
        if (radiusScale < 1.0D) {
            return 4;
        }
        return 6;
    }

    private static BlockState vitrifiedStateForTier(int tier) {
        return switch (Mth.clamp(tier, 0, 6)) {
            case 1 -> ModBlocks.BAKED_VITRIFIED_STONE.get().defaultBlockState();
            case 2 -> ModBlocks.SCORCHED_VITRIFIED_STONE.get().defaultBlockState();
            case 3 -> ModBlocks.IRRADIATED_VITRIFIED_STONE.get().defaultBlockState();
            case 4 -> ModBlocks.HOT_VITRIFIED_STONE.get().defaultBlockState();
            case 5 -> ModBlocks.RADIANT_VITRIFIED_STONE.get().defaultBlockState();
            case 6 -> ModBlocks.INFERNAL_VITRIFIED_STONE.get().defaultBlockState();
            default -> ModBlocks.VITRIFIED_STONE.get().defaultBlockState();
        };
    }

    private static BlockState combineVitrifiedState(BlockState currentState, int incomingTier) {
        int incomingSeverity = Mth.clamp(incomingTier, 0, 6) + 1;
        int currentSeverity = vitrifiedSeverity(currentState);
        if (currentSeverity <= 0) {
            return vitrifiedStateForTier(incomingTier);
        }
        return vitrifiedStateForSeverity(currentSeverity + incomingSeverity);
    }

    private static int vitrifiedSeverity(BlockState state) {
        if (state.is(ModBlocks.VITRIFIED_STONE.get())) {
            return 1;
        }
        if (state.is(ModBlocks.BAKED_VITRIFIED_STONE.get())) {
            return 2;
        }
        if (state.is(ModBlocks.SCORCHED_VITRIFIED_STONE.get())) {
            return 3;
        }
        if (state.is(ModBlocks.IRRADIATED_VITRIFIED_STONE.get())) {
            return 4;
        }
        if (state.is(ModBlocks.HOT_VITRIFIED_STONE.get())) {
            return 5;
        }
        if (state.is(ModBlocks.RADIANT_VITRIFIED_STONE.get())) {
            return 6;
        }
        if (state.is(ModBlocks.INFERNAL_VITRIFIED_STONE.get())) {
            return 7;
        }
        return 0;
    }

    private static BlockState vitrifiedStateForSeverity(int severity) {
        return switch (Mth.clamp(severity, 1, 7)) {
            case 2 -> ModBlocks.BAKED_VITRIFIED_STONE.get().defaultBlockState();
            case 3 -> ModBlocks.SCORCHED_VITRIFIED_STONE.get().defaultBlockState();
            case 4 -> ModBlocks.IRRADIATED_VITRIFIED_STONE.get().defaultBlockState();
            case 5 -> ModBlocks.HOT_VITRIFIED_STONE.get().defaultBlockState();
            case 6 -> ModBlocks.RADIANT_VITRIFIED_STONE.get().defaultBlockState();
            case 7 -> ModBlocks.INFERNAL_VITRIFIED_STONE.get().defaultBlockState();
            default -> ModBlocks.VITRIFIED_STONE.get().defaultBlockState();
        };
    }

    private static BlockState deadVegetationReplacement(BlockState state) {
        if (state.is(Blocks.GRASS_BLOCK)) {
            return ModBlocks.DEAD_GRASS.get().defaultBlockState();
        }
        if (state.is(Blocks.SHORT_GRASS) || state.is(Blocks.FERN)) {
            return ModBlocks.DEAD_SHORT_GRASS.get().defaultBlockState();
        }
        if (state.is(Blocks.TALL_GRASS) || state.is(Blocks.LARGE_FERN)) {
            if (state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)
                    && state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.UPPER) {
                return Blocks.AIR.defaultBlockState();
            }
            return ModBlocks.DEAD_TALL_GRASS.get().defaultBlockState();
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

    private static boolean isGrassAftermathBlock(BlockState state) {
        return state.is(Blocks.GRASS_BLOCK)
                || state.is(ModBlocks.DEAD_GRASS.get())
                || state.is(ModBlocks.CONTAMINATED_GRASS_BLOCK.get());
    }

    private static boolean removesUnsupportedVegetationAbove(BlockState state) {
        return state.is(ModBlocks.CONTAMINATED_GRASS_BLOCK.get()) || state.is(ModBlocks.DEAD_GRASS.get());
    }

    private static boolean isDeadPlantReplacement(BlockState state) {
        return state.is(ModBlocks.DEAD_SHORT_GRASS.get()) || state.is(ModBlocks.DEAD_TALL_GRASS.get());
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
        } else if (original.is(BlockTags.LEAVES) && replacement.isAir()) {
            plannedLeafEvaporations++;
        } else if (replacement.is(ModBlocks.CONTAMINATED_GRASS_BLOCK.get())) {
            plannedContaminatedGrassReplacements++;
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

    private double deterministicColumnNoise(int x, int z) {
        long value = seed ^ VITRIFICATION_RANDOM_SEED_SALT;
        value ^= (long) x * 0x9E3779B97F4A7C15L;
        value ^= (long) z * 0xC2B2AE3D27D4EB4FL;
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
        releaseAllMutationChunks();
        complete = true;
    }

    private boolean ensureWorkUnitChunkReady(ChunkWorkUnit workUnit) {
        ChunkPos chunk = workUnit.chunkPos();
        if (level.hasChunk(chunk.x, chunk.z)) {
            forceMutationChunk(chunk);
            return true;
        }

        mutationChunkLoadWaits++;
        if (forceMutationChunk(chunk)) {
            if (level.hasChunk(chunk.x, chunk.z)) {
                return true;
            }
            if (DEBUG_NUKE_MUTATIONS && tickCounter % 20 == 0) {
                SkyesNuclearTech.LOGGER.info(
                        "Nuke aftermath mutation chunk waiting: owner={} chunk={} forced={} waits={} forcedChunks={}",
                        chunkTicketOwner,
                        chunk,
                        true,
                        mutationChunkLoadWaits,
                        forcedMutationChunks.size()
                );
            }
            return false;
        }

        mutationChunkForceFailures++;
        skippedUnloadedColumns += 256L;
        if (DEBUG_NUKE_MUTATIONS) {
            SkyesNuclearTech.LOGGER.warn(
                    "Nuke aftermath mutation chunk force failed: owner={} chunk={} failures={}",
                    chunkTicketOwner,
                    chunk,
                    mutationChunkForceFailures
            );
        }
        return false;
    }

    private boolean forceMutationChunk(ChunkPos chunk) {
        if (forcedMutationChunks.contains(chunk)) {
            return true;
        }
        boolean forced = NuclearExplosionChunkLoading.forceSingleChunk(
                level,
                chunkTicketOwner,
                chunk,
                SkyentNuclearExplosionConfig.chunkLoadingTickingTickets()
        );
        if (forced) {
            forcedMutationChunks.add(chunk);
            mutationChunksForced++;
        }
        return forced;
    }

    private void releaseMutationChunk(ChunkPos chunk) {
        if (chunk == null || !forcedMutationChunks.remove(chunk)) {
            return;
        }
        if (NuclearExplosionChunkLoading.unforceSingleChunk(
                level,
                chunkTicketOwner,
                chunk,
                SkyentNuclearExplosionConfig.chunkLoadingTickingTickets()
        )) {
            mutationChunksUnforced++;
        }
    }

    private void releaseAllMutationChunks() {
        if (forcedMutationChunks.isEmpty()) {
            return;
        }
        List<ChunkPos> chunks = new ArrayList<>(forcedMutationChunks);
        for (ChunkPos chunk : chunks) {
            releaseMutationChunk(chunk);
        }
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

    public int forcedMutationChunks() {
        return forcedMutationChunks.size();
    }

    public long mutationChunksForced() {
        return mutationChunksForced;
    }

    public long mutationChunksUnforced() {
        return mutationChunksUnforced;
    }

    public long mutationChunkLoadWaits() {
        return mutationChunkLoadWaits;
    }

    public long mutationChunkForceFailures() {
        return mutationChunkForceFailures;
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

    public double contaminatedGrassRadius() {
        return contaminatedGrassRadius;
    }

    public double contaminatedGrassFullRadius() {
        return contaminatedGrassFullRadius;
    }

    public double contaminatedGrassFeatherRadius() {
        return contaminatedGrassFeatherRadius;
    }

    public String contaminatedGrassBlendDebug() {
        return "fullRadius=" + contaminatedGrassFullRadius
                + ",featherRadius=" + contaminatedGrassFeatherRadius
                + ",maxRadius=" + contaminatedGrassRadius
                + ",guaranteed=" + contaminatedGrassGuaranteed
                + ",feathered=" + contaminatedGrassFeathered
                + ",skippedByNoise=" + contaminatedGrassSkippedByNoise
                + ",deadInFeather=" + deadGrassInContaminatedFeather;
    }

    public double deadVegetationRadius() {
        return deadVegetationRadius;
    }

    public double vitrificationRadius() {
        return vitrificationRadius;
    }

    public double unscaledVitrificationRadius() {
        return unscaledVitrificationRadius;
    }

    public double vitrificationFeatherRadius() {
        return vitrificationFeatherRadius;
    }

    public double unscaledVitrificationFeatherRadius() {
        return unscaledVitrificationFeatherRadius;
    }

    public double vitrificationGenerationScale() {
        return VITRIFICATION_GENERATION_SCALE;
    }

    public double vitrificationHotTierFalloffPower() {
        return VITRIFICATION_HOT_TIER_FALLOFF_POWER;
    }

    public int vitrificationSizeTierCap() {
        return sizeTierCap();
    }

    public boolean vitrificationUsesPostCollapseSurface() {
        return true;
    }

    public double fireRadius() {
        return charredLogRadius;
    }

    public double leafEvaporationRadius() {
        return leafEvaporationRadius;
    }

    public long plannedMovementMutations() {
        return plannedMovementMutations;
    }

    public long plannedCharredLogReplacements() {
        return plannedCharredLogReplacements;
    }

    public long plannedLeafEvaporations() {
        return plannedLeafEvaporations;
    }

    public long plannedContaminatedGrassReplacements() {
        return plannedContaminatedGrassReplacements;
    }

    public long plannedDeadGrassReplacements() {
        return plannedDeadGrassReplacements;
    }

    public long plannedDeadLeafReplacements() {
        return plannedDeadLeafReplacements;
    }

    public long plannedVitrifiedStoneReplacements() {
        return plannedVitrifiedStoneReplacements;
    }

    public long vitrificationColumnsConsidered() {
        return vitrificationColumnsConsidered;
    }

    public String plannedVitrifiedTierReplacementsDebug() {
        return "vitrified=" + plannedVitrifiedTierReplacements[0]
                + ",baked=" + plannedVitrifiedTierReplacements[1]
                + ",scorched=" + plannedVitrifiedTierReplacements[2]
                + ",irradiated=" + plannedVitrifiedTierReplacements[3]
                + ",hot=" + plannedVitrifiedTierReplacements[4]
                + ",radiant=" + plannedVitrifiedTierReplacements[5]
                + ",infernal=" + plannedVitrifiedTierReplacements[6];
    }

    public String plannedVitrifiedTopTierCountsDebug() {
        return "vitrified=" + plannedVitrifiedTopTierCounts[0]
                + ",baked=" + plannedVitrifiedTopTierCounts[1]
                + ",scorched=" + plannedVitrifiedTopTierCounts[2]
                + ",irradiated=" + plannedVitrifiedTopTierCounts[3]
                + ",hot=" + plannedVitrifiedTopTierCounts[4]
                + ",radiant=" + plannedVitrifiedTopTierCounts[5]
                + ",infernal=" + plannedVitrifiedTopTierCounts[6];
    }

    public String vitrificationLowerTierCountsDebug() {
        return "vitrified=" + vitrificationLowerTierCounts[0]
                + ",baked=" + vitrificationLowerTierCounts[1]
                + ",scorched=" + vitrificationLowerTierCounts[2]
                + ",irradiated=" + vitrificationLowerTierCounts[3]
                + ",hot=" + vitrificationLowerTierCounts[4]
                + ",radiant=" + vitrificationLowerTierCounts[5]
                + ",infernal=" + vitrificationLowerTierCounts[6];
    }

    public String vitrificationBaseTierCountsDebug() {
        return vitrificationLowerTierCountsDebug();
    }

    public String vitrificationUpperTierCountsDebug() {
        return "vitrified=" + vitrificationUpperTierCounts[0]
                + ",baked=" + vitrificationUpperTierCounts[1]
                + ",scorched=" + vitrificationUpperTierCounts[2]
                + ",irradiated=" + vitrificationUpperTierCounts[3]
                + ",hot=" + vitrificationUpperTierCounts[4]
                + ",radiant=" + vitrificationUpperTierCounts[5]
                + ",infernal=" + vitrificationUpperTierCounts[6];
    }

    public String vitrificationFinalTierCountsDebug() {
        return "vitrified=" + vitrificationFinalTierCounts[0]
                + ",baked=" + vitrificationFinalTierCounts[1]
                + ",scorched=" + vitrificationFinalTierCounts[2]
                + ",irradiated=" + vitrificationFinalTierCounts[3]
                + ",hot=" + vitrificationFinalTierCounts[4]
                + ",radiant=" + vitrificationFinalTierCounts[5]
                + ",infernal=" + vitrificationFinalTierCounts[6];
    }

    public String vitrificationDitherDebug() {
        double average = vitrificationContinuousTierSamples == 0
                ? 0.0D
                : vitrificationContinuousTierSum / vitrificationContinuousTierSamples;
        double min = vitrificationContinuousTierSamples == 0 ? 0.0D : vitrificationContinuousTierMin;
        double max = vitrificationContinuousTierSamples == 0 ? 0.0D : vitrificationContinuousTierMax;
        return "tierCurvePower=" + VITRIFICATION_TIER_CURVE_POWER
                + ",outerFadeStrength=" + VITRIFICATION_OUTER_FADE_STRENGTH
                + ",minEdgePlacementChance=" + VITRIFICATION_MIN_EDGE_PLACEMENT_CHANCE
                + ",strengthNoise=" + VITRIFICATION_STRENGTH_NOISE
                + ",generationScale=" + VITRIFICATION_GENERATION_SCALE
                + ",continuousTierSamples=" + vitrificationContinuousTierSamples
                + ",continuousTierMin=" + min
                + ",continuousTierMax=" + max
                + ",continuousTierAverage=" + average
                + ",ditherPromotions=" + vitrificationDitherPromotions
                + ",outerFadeConsidered=" + vitrificationOuterFadeConsidered
                + ",outerFadePlaced=" + vitrificationOuterFadePlaced
                + ",outerFadeSkipped=" + vitrificationOuterFadeSkipped
                + ",weakEdgePlacements=" + vitrificationWeakEdgePlacements;
    }

    public String vitrificationDowngradeDebug() {
        return vitrificationDitherDebug();
    }

    public long vitrificationColumnsChecked() {
        return vitrificationColumnsChecked;
    }

    public long vitrificationSkippedOutsideRadius() {
        return vitrificationSkippedOutsideRadius;
    }

    public long vitrificationColumnsAffected() {
        return vitrificationColumnsAffected;
    }

    public int maxVitrificationDepthSeen() {
        return maxVitrificationDepthSeen;
    }

    public long vitrificationEdgeFeatherPlacements() {
        return vitrificationEdgeFeatherPlacements;
    }

    public long vitrificationSkippedBySoftEdge() {
        return vitrificationSkippedBySoftEdge;
    }

    public long vitrificationSkippedNoSurface() {
        return vitrificationSkippedNoSurface;
    }

    public long vitrificationSkippedSurfaceAir() {
        return vitrificationSkippedSurfaceAir;
    }

    public long vitrificationSkippedNoVitrifiableBlock() {
        return vitrificationSkippedNoVitrifiableBlock;
    }

    public long vitrificationSkippedTagMismatch() {
        return vitrificationSkippedTagMismatch;
    }

    public long vitrificationSkippedPlannedDeletion() {
        return vitrificationSkippedPlannedDeletion;
    }

    public long vitrificationSkippedPlannedMovementConflict() {
        return vitrificationSkippedPlannedMovementConflict;
    }

    public long vitrificationSkippedBlockEntity() {
        return vitrificationSkippedBlockEntity;
    }

    public long vitrificationSkippedFluid() {
        return vitrificationSkippedFluid;
    }

    public long vitrificationSkippedUnloaded() {
        return vitrificationSkippedUnloaded;
    }

    public long vitrificationReplacementsPlanned() {
        return vitrificationReplacementsPlanned;
    }

    public String vitrificationSkipDebug() {
        return "considered=" + vitrificationColumnsConsidered
                + ",outsideRadius=" + vitrificationSkippedOutsideRadius
                + ",softEdge=" + vitrificationSkippedBySoftEdge
                + ",noSurface=" + vitrificationSkippedNoSurface
                + ",surfaceAir=" + vitrificationSkippedSurfaceAir
                + ",noVitrifiable=" + vitrificationSkippedNoVitrifiableBlock
                + ",tagMismatch=" + vitrificationSkippedTagMismatch
                + ",plannedDeletion=" + vitrificationSkippedPlannedDeletion
                + ",movementConflict=" + vitrificationSkippedPlannedMovementConflict
                + ",blockEntity=" + vitrificationSkippedBlockEntity
                + ",fluid=" + vitrificationSkippedFluid
                + ",unloaded=" + vitrificationSkippedUnloaded
                + ",planned=" + vitrificationReplacementsPlanned
                + ",lowerTiers={" + vitrificationLowerTierCountsDebug() + "}"
                + ",upperTiers={" + vitrificationUpperTierCountsDebug() + "}"
                + ",finalTiers={" + vitrificationFinalTierCountsDebug() + "}"
                + ",dither={" + vitrificationDitherDebug() + "}";
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
            int unloadedWorkUnitsSkipped,
            long mutationsPlanned,
            int workUnitsPlanned,
            int workUnitsProcessed,
            int sectionsMutated,
            int mutationsApplied,
            boolean complete,
            double elapsedMs,
            boolean stoppedByWorkBudget,
            boolean stoppedByColumnBudget,
            boolean stoppedByTimeBudget,
            boolean stoppedByNoWork
    ) {
    }

    private record ColumnKey(int x, int z, int distanceSqr) {
    }

    private record ChunkWorkUnit(ChunkPos chunkPos, double distanceSqr, int ring) {
    }
}
