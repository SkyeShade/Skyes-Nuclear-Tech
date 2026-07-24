package com.skyeshade.skyent.content.explosion.destruction;

import com.skyeshade.skyent.registry.ModBlocks;
import com.skyeshade.skyent.config.SkyentNuclearExplosionConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.concurrent.atomic.AtomicBoolean;

public final class NuclearBlastRayPlanner {
    //TODO: ngl kinda spaghetti (but works mostly?) clean up later
    private static final int MIN_RAYS = 512;
    private static final double GOLDEN_ANGLE = Math.PI * (3.0D - Math.sqrt(5.0D));
    private static final double NUKE_BASELINE_RADIUS = 200.0D;
    // Distance-shaped resistance model:
    // - near power controls core vaporization; lower means hard blocks cost almost nothing near center.
    // - far power controls outer resistance; higher means obsidian/concrete survive farther out.
    // - distance curve controls how quickly resistance ramps up with radius.
    // - material stacking growth controls how quickly rays lose energy through thick material.
    // - starting energy controls total penetration budget.
    private static final double NUKE_RESISTANCE_COST_MULTIPLIER = 1.0D;
    private static final double NUKE_RESISTANCE_COST_OFFSET = 1.0D;
    private static final double NUKE_RESISTANCE_POWER_NEAR = 0.05D;
    private static final double NUKE_RESISTANCE_POWER_FAR = 20.50D;
    private static final double NUKE_RESISTANCE_POWER_DISTANCE_CURVE = 1.01D;
    private static final double NUKE_DISTANCE_RESISTANCE_GROWTH = 2.0D;
    private static final double NUKE_RAY_DISTANCE_DECAY_PER_BLOCK = 0.0015D;
    private static final double NUKE_MATERIAL_PENETRATION_STACKING_GROWTH = 2.00D;
    private static final double NUKE_RESISTANCE_MIN_SOLID_COST = 0.0D;
    private static final double NUKE_SMALL_RADIUS_DISTANCE_PROGRESS_EXPONENT = 0.25D;
    private static final double NUKE_SMALL_RADIUS_PROGRESS_BOOST_MAX = 4.0D;
    private static final double NUKE_CLOSE_RANGE_FRACTION_RADIUS_POWER = 0.25D;
    private static final double NUKE_CLOSE_RANGE_COST_RADIUS_POWER = 0.35D;
    private static final double NUKE_MATERIAL_STACKING_RADIUS_POWER = 0.25D;
    private static final double NUKE_RAY_ENERGY_JITTER_AT_RADIUS_200 = 0.18D;
    private static final double NUKE_RAY_ENERGY_JITTER_SMALL_RADIUS_BONUS = 0.45D;
    private static final double NUKE_RAY_ENERGY_JITTER_MIN_MULTIPLIER = 0.35D;
    private static final double NUKE_RAY_ENERGY_JITTER_MAX_MULTIPLIER = 1.45D;
    private static final double NUKE_RAY_ENERGY_JITTER_RADIUS_EXPONENT = 0.65D;
    private static final double NUKE_RAY_ENERGY_JITTER_MAX_AMOUNT = 0.80D;
    private static final double CONCRETE_BRICKS_CRACK_THRESHOLD_FRACTION = 0.50D;

    private final ServerLevel level;
    private final Vec3 center;
    private final int radius;
    private final double strength;
    private final NuclearDestructionMask mask;
    private final NuclearResistanceCache resistanceCache;
    private final int totalRays;
    private final double initialRayEnergy;
    private final double radiusScale;
    private final double inverseRadiusScale;
    private final double smallRadiusProgressBoost;
    private final double scaledDistanceResistanceGrowth;
    private final double scaledDistanceDecayPerBlock;
    private final double scaledMaterialPenetrationStackingGrowth;
    private final double scaledCloseRangeArmorPiercingRadiusFraction;
    private final double scaledCloseRangeResistanceCostMultiplier;
    private final double rayEnergyJitterAmount;
    private final long seed;

    private int rayIndex;
    private boolean complete;
    private final PlannerCounters globalCounters = new PlannerCounters();

    public NuclearBlastRayPlanner(
            ServerLevel level,
            Vec3 center,
            int radius,
            double strength,
            double rayDensityMultiplier,
            double initialRayEnergyMultiplier,
            double closeRangeArmorPiercingRadiusFraction,
            double closeRangeResistanceCostMultiplier,
            NuclearDestructionMask mask,
            NuclearResistanceCache resistanceCache,
            long seed
    ) {
        this.level = level;
        this.center = center;
        this.radius = Math.max(1, radius);
        this.strength = Math.max(1.0D, strength);
        this.mask = mask;
        this.resistanceCache = resistanceCache;
        double effectiveRayDensityMultiplier = Math.max(1.0D, rayDensityMultiplier);
        int rawRayEstimate = Mth.ceil(effectiveRayDensityMultiplier * Math.PI * this.radius * this.radius / 16.0D);
        int maxRays = Math.max(MIN_RAYS, SkyentNuclearExplosionConfig.rayPlanningMaxRays());
        this.totalRays = Mth.clamp(rawRayEstimate, MIN_RAYS, maxRays);
        this.initialRayEnergy = this.strength * initialRayEnergyMultiplier;
        this.radiusScale = Math.max(0.01D, this.radius / NUKE_BASELINE_RADIUS);
        this.inverseRadiusScale = 1.0D / this.radiusScale;
        this.smallRadiusProgressBoost = Math.min(
                NUKE_SMALL_RADIUS_PROGRESS_BOOST_MAX,
                Math.pow(this.inverseRadiusScale, NUKE_SMALL_RADIUS_DISTANCE_PROGRESS_EXPONENT)
        );
        this.scaledDistanceResistanceGrowth = NUKE_DISTANCE_RESISTANCE_GROWTH * Math.pow(this.inverseRadiusScale, 0.75D);
        this.scaledDistanceDecayPerBlock = NUKE_RAY_DISTANCE_DECAY_PER_BLOCK * Math.pow(this.inverseRadiusScale, 0.65D);
        this.scaledMaterialPenetrationStackingGrowth = Math.min(
                NUKE_MATERIAL_PENETRATION_STACKING_GROWTH * 4.0D,
                NUKE_MATERIAL_PENETRATION_STACKING_GROWTH * Math.pow(this.inverseRadiusScale, NUKE_MATERIAL_STACKING_RADIUS_POWER)
        );
        this.scaledCloseRangeArmorPiercingRadiusFraction = closeRangeArmorPiercingRadiusFraction
                * Mth.clamp(Math.pow(this.radiusScale, NUKE_CLOSE_RANGE_FRACTION_RADIUS_POWER), 0.35D, 1.5D);
        this.scaledCloseRangeResistanceCostMultiplier = Mth.clamp(
                closeRangeResistanceCostMultiplier * Math.pow(this.inverseRadiusScale, NUKE_CLOSE_RANGE_COST_RADIUS_POWER),
                closeRangeResistanceCostMultiplier,
                1.0D
        );
        double smallNukeFactor = this.radiusScale >= 1.0D
                ? 0.0D
                : Math.pow(1.0D - this.radiusScale, NUKE_RAY_ENERGY_JITTER_RADIUS_EXPONENT);
        this.rayEnergyJitterAmount = Mth.clamp(
                NUKE_RAY_ENERGY_JITTER_AT_RADIUS_200 + NUKE_RAY_ENERGY_JITTER_SMALL_RADIUS_BONUS * smallNukeFactor,
                0.0D,
                NUKE_RAY_ENERGY_JITTER_MAX_AMOUNT
        );
        this.seed = seed;
    }

    public PlannerResult tickBudget(int maxRays, int maxSteps) {
        int raysThisTick = 0;
        int stepsThisTick = 0;
        long markedBefore = globalCounters.blocksMarkedTotal;
        long blockStateReadsBefore = globalCounters.blockStateReadCount;
        long blockEntityLookupsBefore = globalCounters.blockEntityLookupCount;
        long collisionShapeLookupsBefore = globalCounters.collisionShapeLookupCount;
        long duplicateMaskMarksBefore = globalCounters.duplicateMaskMarkAttempts;
        long airFastPathBefore = globalCounters.airFastPathCount;
        long fluidFastPathBefore = globalCounters.fluidFastPathCount;
        long classificationHitsBefore = resistanceCache.classificationCacheHits();
        long classificationMissesBefore = resistanceCache.classificationCacheMisses();
        RayWorldView worldView = new LiveLevelRayWorldView();

        while (!complete && raysThisTick < maxRays && stepsThisTick < maxSteps) {
            RayResult result = traceRay(rayIndex, mask, worldView, globalCounters);
            rayIndex++;
            raysThisTick++;
            stepsThisTick += result.steps();
            globalCounters.raysProcessedTotal++;
            globalCounters.stepsProcessedTotal += result.steps();
            globalCounters.blocksMarkedTotal += result.blocksMarked();

            switch (result.stopReason()) {
                case UNLOADED_CHUNK -> globalCounters.unloadedChunkStops++;
                case BLOCKED -> globalCounters.blockedRayStops++;
                case ENERGY -> globalCounters.energyStops++;
                case OUT_OF_WORLD -> globalCounters.outOfWorldStops++;
                case RADIUS -> {
                }
            }

            if (rayIndex >= totalRays) {
                complete = true;
            }
        }

        return new PlannerResult(
                raysThisTick,
                stepsThisTick,
                globalCounters.blocksMarkedTotal - markedBefore,
                globalCounters.blockStateReadCount - blockStateReadsBefore,
                globalCounters.blockEntityLookupCount - blockEntityLookupsBefore,
                globalCounters.collisionShapeLookupCount - collisionShapeLookupsBefore,
                globalCounters.duplicateMaskMarkAttempts - duplicateMaskMarksBefore,
                globalCounters.airFastPathCount - airFastPathBefore,
                globalCounters.fluidFastPathCount - fluidFastPathBefore,
                resistanceCache.classificationCacheHits() - classificationHitsBefore,
                resistanceCache.classificationCacheMisses() - classificationMissesBefore,
                complete
        );
    }

    public WorkerResult planRayRange(NuclearBlockSnapshot snapshot, int startRayInclusive, int endRayExclusive, AtomicBoolean canceled) {
        NuclearDestructionMask localMask = new NuclearDestructionMask();
        PlannerCounters localCounters = new PlannerCounters();
        int raysProcessed = 0;

        for (int index = startRayInclusive; index < endRayExclusive && !canceled.get(); index++) {
            RayResult result = traceRay(index, localMask, snapshot, localCounters);
            raysProcessed++;
            localCounters.raysProcessedTotal++;
            localCounters.stepsProcessedTotal += result.steps();
            localCounters.blocksMarkedTotal += result.blocksMarked();

            switch (result.stopReason()) {
                case UNLOADED_CHUNK -> localCounters.unloadedChunkStops++;
                case BLOCKED -> localCounters.blockedRayStops++;
                case ENERGY -> localCounters.energyStops++;
                case OUT_OF_WORLD -> localCounters.outOfWorldStops++;
                case RADIUS -> {
                }
            }
        }

        return new WorkerResult(
                startRayInclusive,
                endRayExclusive,
                raysProcessed,
                localMask,
                localCounters
        );
    }

    public void mergeWorkerResult(WorkerResult result) {
        mask.mergeFrom(result.mask());
        globalCounters.add(result.counters());
    }

    public void finishAsyncPlanning() {
        rayIndex = totalRays;
        complete = true;
    }

    private RayResult traceRay(int index, NuclearDestructionMask targetMask, RayWorldView worldView, PlannerCounters counters) {
        Vec3 direction = fibonacciDirection(index);
        double rayEnergy = initialRayEnergy * rayEnergyJitterMultiplier(index);
        int blockX = Mth.floor(center.x);
        int blockY = Mth.floor(center.y);
        int blockZ = Mth.floor(center.z);

        int stepX = direction.x > 0.0D ? 1 : -1;
        int stepY = direction.y > 0.0D ? 1 : -1;
        int stepZ = direction.z > 0.0D ? 1 : -1;
        double tMaxX = firstBoundaryDistance(center.x, blockX, direction.x);
        double tMaxY = firstBoundaryDistance(center.y, blockY, direction.y);
        double tMaxZ = firstBoundaryDistance(center.z, blockZ, direction.z);
        double tDeltaX = direction.x == 0.0D ? Double.POSITIVE_INFINITY : Math.abs(1.0D / direction.x);
        double tDeltaY = direction.y == 0.0D ? Double.POSITIVE_INFINITY : Math.abs(1.0D / direction.y);
        double tDeltaZ = direction.z == 0.0D ? Double.POSITIVE_INFINITY : Math.abs(1.0D / direction.z);

        int steps = 0;
        int marked = 0;
        int materialBlocksPierced = 0;
        int obsidianBlocksMarkedThisRay = 0;
        double traveled = 0.0D;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        NuclearBlockSnapshot.MutableRayBlockSample sample = new NuclearBlockSnapshot.MutableRayBlockSample();
        boolean allowSharedDebugLogging = counters == globalCounters;

        while (traveled <= radius) {
            if (blockY < worldView.minBuildHeight() || blockY >= worldView.maxBuildHeight()) {
                return new RayResult(steps, marked, StopReason.OUT_OF_WORLD);
            }

            pos.set(blockX, blockY, blockZ);
            worldView.fillSample(pos, blockX, blockY, blockZ, sample);
            int flags = sample.flags();
            if ((flags & NuclearBlockSnapshot.FLAG_LOADED) == 0) {
                return new RayResult(steps, marked, StopReason.UNLOADED_CHUNK);
            }

            counters.blockStateReadCount++;
            if ((flags & NuclearBlockSnapshot.FLAG_AIR) != 0) {
                counters.airBlocksSkipped++;
                counters.airFastPathCount++;
            } else {
                boolean hasBlockEntity = (flags & NuclearBlockSnapshot.FLAG_HAS_BLOCK_ENTITY) != 0;
                if ((flags & NuclearBlockSnapshot.FLAG_BLOCK_ENTITY_LOOKUP_COUNTED) != 0) {
                    counters.blockEntityLookupCount++;
                }
                boolean fragile = (flags & NuclearBlockSnapshot.FLAG_FRAGILE) != 0;
                boolean fluid = (flags & NuclearBlockSnapshot.FLAG_FLUID) != 0;
                if (fluid) {
                    counters.fluidFastPathCount++;
                }
                boolean nonSolid = (flags & NuclearBlockSnapshot.FLAG_NON_SOLID) != 0;
                if ((flags & NuclearBlockSnapshot.FLAG_COLLISION_SHAPE_LOOKUP_COUNTED) != 0) {
                    counters.collisionShapeLookupCount++;
                }
                boolean obsidian = (flags & NuclearBlockSnapshot.FLAG_OBSIDIAN) != 0;
                float resistance = sample.resistance();
                boolean highResistance = (flags & NuclearBlockSnapshot.FLAG_HIGH_RESISTANCE) != 0;
                BlockState state = sample.state();
                if (highResistance) {
                    counters.highResistanceBlocksHit++;
                }
                if (obsidian) {
                    counters.obsidianBlocksHit++;
                }
                boolean rayBlocking = resistanceCache.isRayBlocking(resistance);
                if (resistanceCache.isRayBlocking(resistance)) {
                    counters.unbreakableStops++;
                    if (highResistance) {
                        counters.highResistanceBlocksBlocked++;
                    }
                    if (obsidian) {
                        counters.obsidianBlocksBlocked++;
                    }
                    if (allowSharedDebugLogging) {
                    }
                    return new RayResult(steps, marked, StopReason.BLOCKED);
                }
                boolean protectedBlockEntity = (flags & NuclearBlockSnapshot.FLAG_PROTECTED_BLOCK_ENTITY) != 0;
                if (hasBlockEntity) {
                    counters.blockEntityBlocksHit++;
                }
                if (protectedBlockEntity) {
                    counters.blockEntitySkips++;
                    counters.protectedBlockEntitySkips++;
                }

                boolean canDestroy = (flags & NuclearBlockSnapshot.FLAG_CAN_DESTROY) != 0;
                boolean markSucceeded = false;
                boolean concreteBricks = (flags & NuclearBlockSnapshot.FLAG_CONCRETE_BRICKS) != 0;
                boolean resistanceGatedRemoval = concreteBricks || highResistance;
                if (!resistanceGatedRemoval && rayEnergy > 0.0D && canDestroy) {
                    if (targetMask.mark(blockX, blockY, blockZ)) {
                        markSucceeded = true;
                        marked++;
                        if (hasBlockEntity) {
                            counters.blockEntityBlocksMarked++;
                        }
                        if (fragile) {
                            counters.fragileBlocksMarked++;
                        }
                        if (nonSolid) {
                            counters.nonSolidBlocksMarked++;
                        }
                        if (fluid) {
                            counters.fluidBlocksMarked++;
                        }
                        if (highResistance) {
                            counters.highResistanceBlocksMarked++;
                        }
                        if (obsidian) {
                            counters.obsidianBlocksMarked++;
                            obsidianBlocksMarkedThisRay++;
                            counters.maxObsidianDepthMarkedOnSingleRay = Math.max(counters.maxObsidianDepthMarkedOnSingleRay, obsidianBlocksMarkedThisRay);
                        }
                    } else {
                        counters.duplicateMaskMarkAttempts++;
                    }
                }

                if (resistance > 0.0F) {
                    double distanceProgress = Mth.clamp(traveled / radius, 0.0D, 1.0D);
                    double distanceCostMultiplier = 1.0D + distanceProgress * distanceProgress * scaledDistanceResistanceGrowth;
                    double materialStackMultiplier = 1.0D + materialBlocksPierced * scaledMaterialPenetrationStackingGrowth;
                    double rawCost = resistanceCost(resistance, distanceProgress, distanceCostMultiplier, materialStackMultiplier);
                    double cost = rawCost;
                    boolean closeRangeApplied = distanceProgress < scaledCloseRangeArmorPiercingRadiusFraction;
                    if (closeRangeApplied) {
                        cost *= scaledCloseRangeResistanceCostMultiplier;
                    }
                    double rayEnergyBefore = rayEnergy;
                    if (concreteBricks && rayEnergyBefore > 0.0D && canDestroy) {
                        if (rayEnergyBefore >= cost) {
                            markSucceeded = targetMask.mark(blockX, blockY, blockZ);
                            if (markSucceeded) {
                                marked++;
                                if (hasBlockEntity) {
                                    counters.blockEntityBlocksMarked++;
                                }
                                if (highResistance) {
                                    counters.highResistanceBlocksMarked++;
                                }
                            } else {
                                counters.duplicateMaskMarkAttempts++;
                            }
                        } else if (rayEnergyBefore >= cost * CONCRETE_BRICKS_CRACK_THRESHOLD_FRACTION
                                && targetMask.markReplacement(blockX, blockY, blockZ, ModBlocks.CRACKED_CONCRETE_BRICKS.get().defaultBlockState())) {
                            counters.crackedConcreteBricksPlanned++;
                        }
                    } else if (resistanceGatedRemoval && rayEnergyBefore > 0.0D && canDestroy && rayEnergyBefore >= cost) {
                        if (targetMask.mark(blockX, blockY, blockZ)) {
                            markSucceeded = true;
                            marked++;
                            if (hasBlockEntity) {
                                counters.blockEntityBlocksMarked++;
                            }
                            if (fragile) {
                                counters.fragileBlocksMarked++;
                            }
                            if (nonSolid) {
                                counters.nonSolidBlocksMarked++;
                            }
                            if (fluid) {
                                counters.fluidBlocksMarked++;
                            }
                            if (highResistance) {
                                counters.highResistanceBlocksMarked++;
                            }
                            if (obsidian) {
                                counters.obsidianBlocksMarked++;
                                obsidianBlocksMarkedThisRay++;
                                counters.maxObsidianDepthMarkedOnSingleRay = Math.max(counters.maxObsidianDepthMarkedOnSingleRay, obsidianBlocksMarkedThisRay);
                            }
                        } else {
                            counters.duplicateMaskMarkAttempts++;
                        }
                    }
                    rayEnergy -= cost;
                    if (allowSharedDebugLogging) {
                    }
                    if (rayEnergy <= 0.0D) {
                        if (highResistance) {
                            counters.highResistanceBlocksStoppedByEnergy++;
                        }
                        if (obsidian) {
                            counters.obsidianBlocksStoppedByEnergy++;
                        }
                        return new RayResult(steps, marked, StopReason.ENERGY);
                    }
                    if (!fluid && !fragile && !nonSolid) {
                        materialBlocksPierced++;
                    }
                }
            }

            steps++;
            double previousTraveled = traveled;
            if (tMaxX <= tMaxY && tMaxX <= tMaxZ) {
                traveled = tMaxX;
                tMaxX += tDeltaX;
                blockX += stepX;
            } else if (tMaxY <= tMaxZ) {
                traveled = tMaxY;
                tMaxY += tDeltaY;
                blockY += stepY;
            } else {
                traveled = tMaxZ;
                tMaxZ += tDeltaZ;
                blockZ += stepZ;
            }
            rayEnergy -= distanceCost(previousTraveled, traveled);
            if (rayEnergy <= 0.0D) {
                return new RayResult(steps, marked, StopReason.ENERGY);
            }
        }

        return new RayResult(steps, marked, StopReason.RADIUS);
    }

    private double distanceCost(double previousTraveled, double traveled) {
        double distanceDelta = Math.max(0.0D, Math.min(traveled, radius) - Math.min(previousTraveled, radius));
        if (distanceDelta <= 0.0D) {
            return 0.0D;
        }

        double distanceProgress = Mth.clamp(traveled / radius, 0.0D, 1.0D);
        double costPerBlock = initialRayEnergy * scaledDistanceDecayPerBlock / radius;
        return costPerBlock * distanceDelta * (1.0D + distanceProgress * distanceProgress * 2.0D);
    }

    private double resistanceCost(double resistance, double distanceProgress, double distanceCostMultiplier, double materialStackMultiplier) {
        double effectivePower = effectiveResistancePower(distanceProgress);
        double baseCost = Math.pow(
                Math.max(0.0D, resistance) + NUKE_RESISTANCE_COST_OFFSET,
                effectivePower
        ) - 1.0D;
        baseCost = Math.max(0.0D, baseCost);
        double cost = baseCost * NUKE_RESISTANCE_COST_MULTIPLIER * distanceCostMultiplier * materialStackMultiplier;
        if (resistance > 0.0D) {
            cost = Math.max(cost, NUKE_RESISTANCE_MIN_SOLID_COST);
        }
        return cost;
    }

    private double effectiveResistancePower(double distanceProgress) {
        double scaledDistanceProgress = scaledDistanceProgress(distanceProgress);
        double curvedDistance = Math.pow(scaledDistanceProgress, NUKE_RESISTANCE_POWER_DISTANCE_CURVE);
        return Mth.lerp(curvedDistance, NUKE_RESISTANCE_POWER_NEAR, NUKE_RESISTANCE_POWER_FAR);
    }

    private double scaledDistanceProgress(double distanceProgress) {
        return Mth.clamp(distanceProgress * smallRadiusProgressBoost, 0.0D, 1.0D);
    }

    private double rayEnergyJitterMultiplier(int rayIndex) {
        long mixed = seed
                ^ 0x9E3779B97F4A7C15L
                ^ ((long) rayIndex * 0xBF58476D1CE4E5B9L);
        mixed ^= mixed >>> 30;
        mixed *= 0xBF58476D1CE4E5B9L;
        mixed ^= mixed >>> 27;
        mixed *= 0x94D049BB133111EBL;
        mixed ^= mixed >>> 31;

        double random01 = (mixed >>> 11) * 0x1.0p-53;
        double centered = random01 * 2.0D - 1.0D;
        double multiplier = 1.0D + centered * rayEnergyJitterAmount;
        return Mth.clamp(
                multiplier,
                NUKE_RAY_ENERGY_JITTER_MIN_MULTIPLIER,
                NUKE_RAY_ENERGY_JITTER_MAX_MULTIPLIER
        );
    }

    private Vec3 fibonacciDirection(int index) {
        double offsetIndex = Math.floorMod(index + Long.hashCode(seed), totalRays);
        double t = (offsetIndex + 0.5D) / totalRays;
        double y = 1.0D - 2.0D * t;
        double horizontalRadius = Math.sqrt(Math.max(0.0D, 1.0D - y * y));
        double theta = offsetIndex * GOLDEN_ANGLE;
        return new Vec3(Math.cos(theta) * horizontalRadius, y, Math.sin(theta) * horizontalRadius);
    }

    private static double firstBoundaryDistance(double coordinate, int blockCoordinate, double direction) {
        if (direction == 0.0D) {
            return Double.POSITIVE_INFINITY;
        }
        double boundary = direction > 0.0D ? blockCoordinate + 1.0D : blockCoordinate;
        return Math.max(0.0D, (boundary - coordinate) / direction);
    }

    public boolean isComplete() {
        return complete;
    }

    public int totalRays() {
        return totalRays;
    }

    public double initialRayEnergy() {
        return initialRayEnergy;
    }

    public record PlannerResult(
            int raysProcessed,
            int stepsProcessed,
            long blocksMarked,
            long blockStateReads,
            long blockEntityLookups,
            long collisionShapeLookups,
            long duplicateMaskMarkAttempts,
            long airFastPaths,
            long fluidFastPaths,
            long classificationCacheHits,
            long classificationCacheMisses,
            boolean complete
    ) {
    }

    public record WorkerResult(
            int startRayInclusive,
            int endRayExclusive,
            int raysProcessed,
            NuclearDestructionMask mask,
            PlannerCounters counters
    ) {
    }

    private record RayResult(int steps, int blocksMarked, StopReason stopReason) {
    }

    private enum StopReason {
        RADIUS,
        UNLOADED_CHUNK,
        BLOCKED,
        ENERGY,
        OUT_OF_WORLD
    }

    public interface RayWorldView {
        void fillSample(BlockPos.MutableBlockPos pos, int x, int y, int z, NuclearBlockSnapshot.MutableRayBlockSample sample);

        int minBuildHeight();

        int maxBuildHeight();
    }

    private final class LiveLevelRayWorldView implements RayWorldView {
        @Override
        public void fillSample(BlockPos.MutableBlockPos pos, int x, int y, int z, NuclearBlockSnapshot.MutableRayBlockSample sample) {
            pos.set(x, y, z);
            if (!level.hasChunkAt(pos)) {
                sample.set(0, NuclearResistanceCache.AIR_RESISTANCE, null);
                return;
            }
            BlockState state = level.getBlockState(pos);
            NuclearResistanceCache.RayBlockClassification classification = resistanceCache.classify(state);
            int flags = NuclearBlockSnapshot.FLAG_LOADED;
            if (classification.air()) {
                flags |= NuclearBlockSnapshot.FLAG_AIR;
            }
            if (classification.fluid()) {
                flags |= NuclearBlockSnapshot.FLAG_FLUID;
            }
            if (classification.fragile()) {
                flags |= NuclearBlockSnapshot.FLAG_FRAGILE;
            }
            boolean hasBlockEntity = false;
            boolean protectedBlockEntity = false;
            if (classification.hasBlockEntity()) {
                flags |= NuclearBlockSnapshot.FLAG_BLOCK_ENTITY_LOOKUP_COUNTED;
                hasBlockEntity = level.getBlockEntity(pos) != null;
                protectedBlockEntity = hasBlockEntity && resistanceCache.isProtectedBlockEntity(state, level, pos);
            }
            if (hasBlockEntity) {
                flags |= NuclearBlockSnapshot.FLAG_HAS_BLOCK_ENTITY;
            }
            if (protectedBlockEntity) {
                flags |= NuclearBlockSnapshot.FLAG_PROTECTED_BLOCK_ENTITY;
            }
            if (classification.canMarkForDestruction() && !protectedBlockEntity) {
                flags |= NuclearBlockSnapshot.FLAG_CAN_DESTROY;
            }
            boolean nonSolid = false;
            if (classification.collisionShapeLookupNeeded()) {
                flags |= NuclearBlockSnapshot.FLAG_COLLISION_SHAPE_LOOKUP_COUNTED;
                nonSolid = state.getCollisionShape(level, pos).isEmpty();
            }
            if (nonSolid) {
                flags |= NuclearBlockSnapshot.FLAG_NON_SOLID;
            }
            float resistance = resistanceCache.resistanceFor(state, level, pos);
            float rawResistance = state.getBlock().getExplosionResistance();
            if (resistance >= 12.0F || rawResistance >= 12.0F) {
                flags |= NuclearBlockSnapshot.FLAG_HIGH_RESISTANCE;
            }
            if (state.is(Blocks.OBSIDIAN)) {
                flags |= NuclearBlockSnapshot.FLAG_OBSIDIAN;
            }
            if (state.is(ModBlocks.CONCRETE_BRICKS.get())) {
                flags |= NuclearBlockSnapshot.FLAG_CONCRETE_BRICKS;
            }
            sample.set(flags, resistance, state);
        }

        @Override
        public int minBuildHeight() {
            return level.getMinBuildHeight();
        }

        @Override
        public int maxBuildHeight() {
            return level.getMaxBuildHeight();
        }
    }

    public static final class PlannerCounters {
        private long raysProcessedTotal;
        private long stepsProcessedTotal;
        private long blocksMarkedTotal;
        private long blockStateReadCount;
        private long blockEntityLookupCount;
        private long collisionShapeLookupCount;
        private long duplicateMaskMarkAttempts;
        private long airFastPathCount;
        private long fluidFastPathCount;
        private long unloadedChunkStops;
        private long blockedRayStops;
        private long energyStops;
        private long outOfWorldStops;
        private long fragileBlocksMarked;
        private long nonSolidBlocksMarked;
        private long fluidBlocksMarked;
        private long airBlocksSkipped;
        private long blockEntitySkips;
        private long blockEntityBlocksHit;
        private long blockEntityBlocksMarked;
        private long protectedBlockEntitySkips;
        private long unbreakableStops;
        private long crackedConcreteBricksPlanned;
        private long highResistanceBlocksHit;
        private long highResistanceBlocksMarked;
        private long highResistanceBlocksBlocked;
        private long highResistanceBlocksStoppedByEnergy;
        private long obsidianBlocksHit;
        private long obsidianBlocksMarked;
        private long obsidianBlocksBlocked;
        private long obsidianBlocksStoppedByEnergy;
        private int maxObsidianDepthMarkedOnSingleRay;

        private void add(PlannerCounters other) {
            raysProcessedTotal += other.raysProcessedTotal;
            stepsProcessedTotal += other.stepsProcessedTotal;
            blocksMarkedTotal += other.blocksMarkedTotal;
            blockStateReadCount += other.blockStateReadCount;
            blockEntityLookupCount += other.blockEntityLookupCount;
            collisionShapeLookupCount += other.collisionShapeLookupCount;
            duplicateMaskMarkAttempts += other.duplicateMaskMarkAttempts;
            airFastPathCount += other.airFastPathCount;
            fluidFastPathCount += other.fluidFastPathCount;
            unloadedChunkStops += other.unloadedChunkStops;
            blockedRayStops += other.blockedRayStops;
            energyStops += other.energyStops;
            outOfWorldStops += other.outOfWorldStops;
            fragileBlocksMarked += other.fragileBlocksMarked;
            nonSolidBlocksMarked += other.nonSolidBlocksMarked;
            fluidBlocksMarked += other.fluidBlocksMarked;
            airBlocksSkipped += other.airBlocksSkipped;
            blockEntitySkips += other.blockEntitySkips;
            blockEntityBlocksHit += other.blockEntityBlocksHit;
            blockEntityBlocksMarked += other.blockEntityBlocksMarked;
            protectedBlockEntitySkips += other.protectedBlockEntitySkips;
            unbreakableStops += other.unbreakableStops;
            crackedConcreteBricksPlanned += other.crackedConcreteBricksPlanned;
            highResistanceBlocksHit += other.highResistanceBlocksHit;
            highResistanceBlocksMarked += other.highResistanceBlocksMarked;
            highResistanceBlocksBlocked += other.highResistanceBlocksBlocked;
            highResistanceBlocksStoppedByEnergy += other.highResistanceBlocksStoppedByEnergy;
            obsidianBlocksHit += other.obsidianBlocksHit;
            obsidianBlocksMarked += other.obsidianBlocksMarked;
            obsidianBlocksBlocked += other.obsidianBlocksBlocked;
            obsidianBlocksStoppedByEnergy += other.obsidianBlocksStoppedByEnergy;
            maxObsidianDepthMarkedOnSingleRay = Math.max(maxObsidianDepthMarkedOnSingleRay, other.maxObsidianDepthMarkedOnSingleRay);
        }
    }
}


