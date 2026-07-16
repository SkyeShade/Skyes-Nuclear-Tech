package com.skyeshade.skyent.content.explosion.destruction;

import com.skyeshade.skyent.SkyesNuclearTech;
import com.skyeshade.skyent.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public final class NuclearBlastRayPlanner {
    private static final boolean DEBUG_NUKE_OBSIDIAN_RAYS = Boolean.getBoolean("skyent.debugNukeObsidianRays");
    private static final int MAX_OBSIDIAN_DEBUG_HIT_LOGS = 200;
    private static final int MAX_OBSIDIAN_DEBUG_STEP_LOGS = 20;
    private static final int MIN_RAYS = 512;
    private static final int MAX_RAYS = 320_000;
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
    private static final double NUKE_MATERIAL_PENETRATION_STACKING_GROWTH = 20.00D;
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
    private final double rayCountMultiplier;
    private final double extraRayCountMultiplier;
    private final int baseRayCount;
    private final int totalRays;
    private final double initialRayEnergy;
    private final double radiusScale;
    private final double inverseRadiusScale;
    private final double smallRadiusProgressBoost;
    private final double scaledDistanceResistanceGrowth;
    private final double scaledDistanceDecayPerBlock;
    private final double scaledMaterialPenetrationStackingGrowth;
    private final double closeRangeArmorPiercingRadiusFraction;
    private final double closeRangeResistanceCostMultiplier;
    private final double scaledCloseRangeArmorPiercingRadiusFraction;
    private final double scaledCloseRangeResistanceCostMultiplier;
    private final double rayEnergyJitterAmount;
    private final long seed;

    private int rayIndex;
    private boolean complete;
    private long raysProcessedTotal;
    private long stepsProcessedTotal;
    private long blocksMarkedTotal;
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
    private int obsidianDebugHitLogs;
    private int obsidianDebugStepLogs;
    private int obsidianDebugTraceRayIndex = -1;

    public NuclearBlastRayPlanner(
            ServerLevel level,
            Vec3 center,
            int radius,
            double strength,
            double rayCountMultiplier,
            double extraRayCountMultiplier,
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
        this.rayCountMultiplier = rayCountMultiplier;
        this.extraRayCountMultiplier = extraRayCountMultiplier;
        this.baseRayCount = Mth.clamp(
                Mth.ceil(rayCountMultiplier * Math.PI * this.radius * this.radius / 16.0D),
                MIN_RAYS,
                MAX_RAYS
        );
        this.totalRays = Mth.clamp(Mth.ceil(this.baseRayCount * extraRayCountMultiplier), MIN_RAYS, MAX_RAYS);
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
        this.closeRangeArmorPiercingRadiusFraction = closeRangeArmorPiercingRadiusFraction;
        this.closeRangeResistanceCostMultiplier = closeRangeResistanceCostMultiplier;
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
        long markedBefore = blocksMarkedTotal;

        while (!complete && raysThisTick < maxRays && stepsThisTick < maxSteps) {
            RayResult result = traceRay(rayIndex);
            rayIndex++;
            raysThisTick++;
            stepsThisTick += result.steps();
            raysProcessedTotal++;
            stepsProcessedTotal += result.steps();
            blocksMarkedTotal += result.blocksMarked();

            switch (result.stopReason()) {
                case UNLOADED_CHUNK -> unloadedChunkStops++;
                case BLOCKED -> blockedRayStops++;
                case ENERGY -> energyStops++;
                case OUT_OF_WORLD -> outOfWorldStops++;
                case RADIUS -> {
                }
            }

            if (rayIndex >= totalRays) {
                complete = true;
            }
        }

        return new PlannerResult(raysThisTick, stepsThisTick, blocksMarkedTotal - markedBefore, complete);
    }

    private RayResult traceRay(int index) {
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

        while (traveled <= radius) {
            if (blockY < level.getMinBuildHeight() || blockY >= level.getMaxBuildHeight()) {
                return new RayResult(steps, marked, StopReason.OUT_OF_WORLD);
            }

            pos.set(blockX, blockY, blockZ);
            if (!level.hasChunkAt(pos)) {
                return new RayResult(steps, marked, StopReason.UNLOADED_CHUNK);
            }

            BlockState state = level.getBlockState(pos);
            if (state.isAir()) {
                airBlocksSkipped++;
            } else {
                boolean hasBlockEntity = level.getBlockEntity(pos) != null;
                boolean fragile = resistanceCache.isFragile(state);
                boolean fluid = !state.getFluidState().isEmpty();
                boolean nonSolid = state.getCollisionShape(level, pos).isEmpty();
                boolean obsidian = state.is(Blocks.OBSIDIAN);
                float resistance = resistanceCache.resistanceFor(state, level, pos);
                float rawResistance = state.getBlock().getExplosionResistance();
                boolean highResistance = resistance >= 12.0F || rawResistance >= 12.0F;
                if (highResistance) {
                    highResistanceBlocksHit++;
                }
                if (obsidian) {
                    obsidianBlocksHit++;
                    if (DEBUG_NUKE_OBSIDIAN_RAYS && obsidianDebugTraceRayIndex < 0) {
                        obsidianDebugTraceRayIndex = index;
                    }
                }
                boolean rayBlocking = resistanceCache.isRayBlocking(resistance);
                if (resistanceCache.isRayBlocking(resistance)) {
                    unbreakableStops++;
                    if (highResistance) {
                        highResistanceBlocksBlocked++;
                    }
                    if (obsidian) {
                        obsidianBlocksBlocked++;
                    }
                    logHighResistanceHit(index, pos, state, traveled, rayEnergy, rawResistance, resistance, true, false, false, 0.0D, rayEnergy, StopReason.BLOCKED);
                    return new RayResult(steps, marked, StopReason.BLOCKED);
                }
                boolean protectedBlockEntity = hasBlockEntity && resistanceCache.isProtectedBlockEntity(state, level, pos);
                if (hasBlockEntity) {
                    blockEntityBlocksHit++;
                }
                if (protectedBlockEntity) {
                    blockEntitySkips++;
                    protectedBlockEntitySkips++;
                }

                boolean canDestroy = !protectedBlockEntity && resistanceCache.canMarkForDestruction(state);
                boolean markSucceeded = false;
                boolean concreteBricks = state.is(ModBlocks.CONCRETE_BRICKS.get());
                if (!concreteBricks && rayEnergy > 0.0D && canDestroy && mask.mark(blockX, blockY, blockZ)) {
                    markSucceeded = true;
                    marked++;
                    if (hasBlockEntity) {
                        blockEntityBlocksMarked++;
                    }
                    if (fragile) {
                        fragileBlocksMarked++;
                    }
                    if (nonSolid) {
                        nonSolidBlocksMarked++;
                    }
                    if (fluid) {
                        fluidBlocksMarked++;
                    }
                    if (highResistance) {
                        highResistanceBlocksMarked++;
                    }
                    if (obsidian) {
                        obsidianBlocksMarked++;
                        obsidianBlocksMarkedThisRay++;
                        maxObsidianDepthMarkedOnSingleRay = Math.max(maxObsidianDepthMarkedOnSingleRay, obsidianBlocksMarkedThisRay);
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
                            markSucceeded = mask.mark(blockX, blockY, blockZ);
                            if (markSucceeded) {
                                marked++;
                                if (hasBlockEntity) {
                                    blockEntityBlocksMarked++;
                                }
                                if (highResistance) {
                                    highResistanceBlocksMarked++;
                                }
                            }
                        } else if (rayEnergyBefore >= cost * CONCRETE_BRICKS_CRACK_THRESHOLD_FRACTION
                                && mask.markReplacement(blockX, blockY, blockZ, ModBlocks.CRACKED_CONCRETE_BRICKS.get().defaultBlockState())) {
                            crackedConcreteBricksPlanned++;
                        }
                    }
                    rayEnergy -= cost;
                    logHighResistanceHit(index, pos, state, traveled, rayEnergyBefore, rawResistance, resistance, rayBlocking, canDestroy, markSucceeded, rawCost, rayEnergy, rayEnergy <= 0.0D ? StopReason.ENERGY : null);
                    logObsidianStepTrace(index, pos, state, traveled, rayEnergyBefore, rawResistance, resistance, closeRangeApplied, cost, rayEnergy, markSucceeded);
                    if (rayEnergy <= 0.0D) {
                        if (highResistance) {
                            highResistanceBlocksStoppedByEnergy++;
                        }
                        if (obsidian) {
                            obsidianBlocksStoppedByEnergy++;
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

    private void logHighResistanceHit(
            int rayIndex,
            BlockPos pos,
            BlockState state,
            double traveled,
            double rayEnergyBefore,
            float rawResistance,
            float effectiveResistance,
            boolean rayBlocking,
            boolean canDestroy,
            boolean markSucceeded,
            double rawCost,
            double rayEnergyAfter,
            StopReason stopReason
    ) {
        if (!DEBUG_NUKE_OBSIDIAN_RAYS || obsidianDebugHitLogs >= MAX_OBSIDIAN_DEBUG_HIT_LOGS) {
            return;
        }
        if (!state.is(Blocks.OBSIDIAN) && rawResistance < 12.0F && effectiveResistance < 12.0F) {
            return;
        }

        obsidianDebugHitLogs++;
        double distanceProgress = Mth.clamp(traveled / radius, 0.0D, 1.0D);
        double effectivePower = effectiveResistancePower(distanceProgress);
        boolean closeRangeApplied = distanceProgress < scaledCloseRangeArmorPiercingRadiusFraction;
        double finalCost = Math.max(0.0D, rayEnergyBefore - rayEnergyAfter);
        SkyesNuclearTech.LOGGER.info(
                "Nuke obsidian/high-res ray hit: ray={} pos={} traveled={} distanceProgress={} effectivePower={} block={} state={} rawResistance={} effectiveResistance={} rayBlocking={} canMark={} hasBlockEntity={} rayEnergyBefore={} rawCost={} closeRangeApplied={} baseCloseFraction={} baseCloseMultiplier={} scaledCloseFraction={} scaledCloseMultiplier={} finalCost={} rayEnergyAfter={} markSucceeded={} stopReason={}",
                rayIndex,
                pos,
                traveled,
                distanceProgress,
                effectivePower,
                BuiltInRegistries.BLOCK.getKey(state.getBlock()),
                state,
                rawResistance,
                effectiveResistance,
                rayBlocking,
                canDestroy,
                level.getBlockEntity(pos) != null,
                rayEnergyBefore,
                rawCost,
                closeRangeApplied,
                closeRangeArmorPiercingRadiusFraction,
                closeRangeResistanceCostMultiplier,
                scaledCloseRangeArmorPiercingRadiusFraction,
                scaledCloseRangeResistanceCostMultiplier,
                finalCost,
                rayEnergyAfter,
                markSucceeded,
                stopReason
        );
    }

    private void logObsidianStepTrace(
            int rayIndex,
            BlockPos pos,
            BlockState state,
            double traveled,
            double rayEnergyBefore,
            float rawResistance,
            float effectiveResistance,
            boolean closeRangeApplied,
            double finalCost,
            double rayEnergyAfter,
            boolean markSucceeded
    ) {
        if (!DEBUG_NUKE_OBSIDIAN_RAYS || rayIndex != obsidianDebugTraceRayIndex || obsidianDebugStepLogs >= MAX_OBSIDIAN_DEBUG_STEP_LOGS) {
            return;
        }

        obsidianDebugStepLogs++;
        SkyesNuclearTech.LOGGER.info(
                "Nuke obsidian DDA trace: ray={} step={} pos={} block={} traveled={} rawResistance={} effectiveResistance={} closeRangeApplied={} energyBefore={} finalCost={} energyAfter={} markSucceeded={}",
                rayIndex,
                obsidianDebugStepLogs,
                pos,
                BuiltInRegistries.BLOCK.getKey(state.getBlock()),
                traveled,
                rawResistance,
                effectiveResistance,
                closeRangeApplied,
                rayEnergyBefore,
                finalCost,
                rayEnergyAfter,
                markSucceeded
        );
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

    public int rayIndex() {
        return rayIndex;
    }

    public int totalRays() {
        return totalRays;
    }

    public int baseRayCount() {
        return baseRayCount;
    }

    public double rayCountMultiplier() {
        return rayCountMultiplier;
    }

    public double extraRayCountMultiplier() {
        return extraRayCountMultiplier;
    }

    public double initialRayEnergy() {
        return initialRayEnergy;
    }

    public double baselineRadius() {
        return NUKE_BASELINE_RADIUS;
    }

    public double radiusScale() {
        return radiusScale;
    }

    public double inverseRadiusScale() {
        return inverseRadiusScale;
    }

    public double smallRadiusProgressBoost() {
        return smallRadiusProgressBoost;
    }

    public double resistanceCostMultiplier() {
        return NUKE_RESISTANCE_COST_MULTIPLIER;
    }

    public double resistanceNonlinearPower() {
        return NUKE_RESISTANCE_POWER_FAR;
    }

    public double resistancePowerNear() {
        return NUKE_RESISTANCE_POWER_NEAR;
    }

    public double resistancePowerFar() {
        return NUKE_RESISTANCE_POWER_FAR;
    }

    public double resistancePowerDistanceCurve() {
        return NUKE_RESISTANCE_POWER_DISTANCE_CURVE;
    }

    public double resistanceCostOffset() {
        return NUKE_RESISTANCE_COST_OFFSET;
    }

    public double distanceResistanceGrowth() {
        return NUKE_DISTANCE_RESISTANCE_GROWTH;
    }

    public double scaledDistanceResistanceGrowth() {
        return scaledDistanceResistanceGrowth;
    }

    public double distanceDecayPerBlock() {
        return NUKE_RAY_DISTANCE_DECAY_PER_BLOCK;
    }

    public double scaledDistanceDecayPerBlock() {
        return scaledDistanceDecayPerBlock;
    }

    public double materialPenetrationStackingGrowth() {
        return NUKE_MATERIAL_PENETRATION_STACKING_GROWTH;
    }

    public double scaledMaterialPenetrationStackingGrowth() {
        return scaledMaterialPenetrationStackingGrowth;
    }

    public double closeRangeArmorPiercingRadiusFraction() {
        return closeRangeArmorPiercingRadiusFraction;
    }

    public double closeRangeResistanceCostMultiplier() {
        return closeRangeResistanceCostMultiplier;
    }

    public double scaledCloseRangeArmorPiercingRadiusFraction() {
        return scaledCloseRangeArmorPiercingRadiusFraction;
    }

    public double scaledCloseRangeResistanceCostMultiplier() {
        return scaledCloseRangeResistanceCostMultiplier;
    }

    public double rayEnergyJitterAtRadius200() {
        return NUKE_RAY_ENERGY_JITTER_AT_RADIUS_200;
    }

    public double rayEnergyJitterSmallRadiusBonus() {
        return NUKE_RAY_ENERGY_JITTER_SMALL_RADIUS_BONUS;
    }

    public double rayEnergyJitterAmount() {
        return rayEnergyJitterAmount;
    }

    public double rayEnergyJitterMinMultiplier() {
        return NUKE_RAY_ENERGY_JITTER_MIN_MULTIPLIER;
    }

    public double rayEnergyJitterMaxMultiplier() {
        return NUKE_RAY_ENERGY_JITTER_MAX_MULTIPLIER;
    }

    public String rayEnergyJitterSamples() {
        return "r0=" + formatCost(rayEnergyJitterMultiplier(0))
                + ",r1=" + formatCost(rayEnergyJitterMultiplier(1))
                + ",r2=" + formatCost(rayEnergyJitterMultiplier(2))
                + ",r3=" + formatCost(rayEnergyJitterMultiplier(3))
                + ",r4=" + formatCost(rayEnergyJitterMultiplier(4));
    }

    public String resistanceCostSamples() {
        return "r0.6[" + sampleResistanceCosts(0.6D)
                + "] r6[" + sampleResistanceCosts(6.0D)
                + "] r18[" + sampleResistanceCosts(18.0D)
                + "] r50[" + sampleResistanceCosts(50.0D)
                + "] penetration[r18Raw="
                + formatCost(sampleResistanceCost(18.0D, 0.0D))
                + ",r18Close="
                + formatCost(sampleCloseResistanceCost(18.0D))
                + ",r18CloseDestroyed="
                + formatCost(estimatedCloseBlocksDestroyed(18.0D))
                + ",r6Close="
                + formatCost(sampleCloseResistanceCost(6.0D))
                + ",r6CloseDestroyed="
                + formatCost(estimatedCloseBlocksDestroyed(6.0D))
                + "]";
    }

    private String sampleResistanceCosts(double resistance) {
        return sampleResistanceCostEntry("d0", resistance, 0.0D)
                + "," + sampleResistanceCostEntry("d0.1", resistance, 0.1D)
                + "," + sampleResistanceCostEntry("d0.25", resistance, 0.25D)
                + "," + sampleResistanceCostEntry("d0.5", resistance, 0.5D)
                + "," + sampleResistanceCostEntry("d1", resistance, 1.0D);
    }

    private String sampleResistanceCostEntry(String label, double resistance, double distanceProgress) {
        return label + "=p" + formatCost(effectiveResistancePower(distanceProgress))
                + "/sp" + formatCost(scaledDistanceProgress(distanceProgress))
                + "/c" + formatCost(sampleResistanceCost(resistance, distanceProgress));
    }

    private double sampleResistanceCost(double resistance, double distanceProgress) {
        double distanceCostMultiplier = 1.0D + distanceProgress * distanceProgress * scaledDistanceResistanceGrowth;
        return resistanceCost(resistance, distanceProgress, distanceCostMultiplier, 1.0D);
    }

    private double sampleCloseResistanceCost(double resistance) {
        return resistanceCost(resistance, 0.0D, 1.0D, 1.0D) * scaledCloseRangeResistanceCostMultiplier;
    }

    private double estimatedCloseBlocksDestroyed(double resistance) {
        double closeCost = sampleCloseResistanceCost(resistance);
        return closeCost <= 0.0D ? 0.0D : initialRayEnergy / closeCost;
    }

    private static String formatCost(double cost) {
        return String.format(java.util.Locale.ROOT, "%.2f", cost);
    }

    public long raysProcessedTotal() {
        return raysProcessedTotal;
    }

    public long stepsProcessedTotal() {
        return stepsProcessedTotal;
    }

    public long blocksMarkedTotal() {
        return blocksMarkedTotal;
    }

    public long unloadedChunkStops() {
        return unloadedChunkStops;
    }

    public long blockedRayStops() {
        return blockedRayStops;
    }

    public long energyStops() {
        return energyStops;
    }

    public long outOfWorldStops() {
        return outOfWorldStops;
    }

    public long fragileBlocksMarked() {
        return fragileBlocksMarked;
    }

    public long nonSolidBlocksMarked() {
        return nonSolidBlocksMarked;
    }

    public long fluidBlocksMarked() {
        return fluidBlocksMarked;
    }

    public long airBlocksSkipped() {
        return airBlocksSkipped;
    }

    public long blockEntitySkips() {
        return blockEntitySkips;
    }

    public long blockEntityBlocksHit() {
        return blockEntityBlocksHit;
    }

    public long blockEntityBlocksMarked() {
        return blockEntityBlocksMarked;
    }

    public long protectedBlockEntitySkips() {
        return protectedBlockEntitySkips;
    }

    public long unbreakableStops() {
        return unbreakableStops;
    }

    public long crackedConcreteBricksPlanned() {
        return crackedConcreteBricksPlanned;
    }

    public long highResistanceBlocksHit() {
        return highResistanceBlocksHit;
    }

    public long highResistanceBlocksMarked() {
        return highResistanceBlocksMarked;
    }

    public long highResistanceBlocksBlocked() {
        return highResistanceBlocksBlocked;
    }

    public long highResistanceBlocksStoppedByEnergy() {
        return highResistanceBlocksStoppedByEnergy;
    }

    public long obsidianBlocksHit() {
        return obsidianBlocksHit;
    }

    public long obsidianBlocksMarked() {
        return obsidianBlocksMarked;
    }

    public long obsidianBlocksBlocked() {
        return obsidianBlocksBlocked;
    }

    public long obsidianBlocksStoppedByEnergy() {
        return obsidianBlocksStoppedByEnergy;
    }

    public int maxObsidianDepthMarkedOnSingleRay() {
        return maxObsidianDepthMarkedOnSingleRay;
    }

    public record PlannerResult(int raysProcessed, int stepsProcessed, long blocksMarked, boolean complete) {
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
}
