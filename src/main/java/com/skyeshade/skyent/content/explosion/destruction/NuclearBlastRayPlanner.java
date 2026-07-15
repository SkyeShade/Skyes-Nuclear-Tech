package com.skyeshade.skyent.content.explosion.destruction;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public final class NuclearBlastRayPlanner {
    private static final int MIN_RAYS = 512;
    private static final int MAX_RAYS = 320_000;
    private static final double GOLDEN_ANGLE = Math.PI * (3.0D - Math.sqrt(5.0D));
    private static final double NUKE_RESISTANCE_COST_MULTIPLIER = 1.0D;
    private static final double NUKE_RESISTANCE_COST_OFFSET = 0.20D;
    private static final double NUKE_RESISTANCE_POWER = 3.65D;
    private static final double NUKE_DISTANCE_RESISTANCE_GROWTH = 2.0D;
    private static final double NUKE_RESISTANCE_MIN_SOLID_COST = 0.02D;

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
    private final double closeRangeArmorPiercingRadiusFraction;
    private final double closeRangeResistanceCostMultiplier;
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
    private long unbreakableStops;

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
        this.closeRangeArmorPiercingRadiusFraction = closeRangeArmorPiercingRadiusFraction;
        this.closeRangeResistanceCostMultiplier = closeRangeResistanceCostMultiplier;
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
        double rayEnergy = initialRayEnergy;
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
                float resistance = resistanceCache.resistanceFor(state, level, pos);
                if (resistanceCache.isRayBlocking(resistance)) {
                    unbreakableStops++;
                    return new RayResult(steps, marked, StopReason.BLOCKED);
                }
                if (hasBlockEntity) {
                    blockEntitySkips++;
                }

                if (resistance > 0.0F) {
                    double distanceProgress = Mth.clamp(traveled / radius, 0.0D, 1.0D);
                    double distanceCostMultiplier = 1.0D + distanceProgress * distanceProgress * NUKE_DISTANCE_RESISTANCE_GROWTH;
                    double cost = resistanceCost(resistance, distanceCostMultiplier);
                    if (distanceProgress < closeRangeArmorPiercingRadiusFraction) {
                        cost *= closeRangeResistanceCostMultiplier;
                    }
                    rayEnergy -= cost;
                    if (rayEnergy <= 0.0D) {
                        return new RayResult(steps, marked, StopReason.ENERGY);
                    }
                    if (!hasBlockEntity && resistanceCache.canMarkForDestruction(state) && mask.mark(blockX, blockY, blockZ)) {
                        marked++;
                        if (fragile) {
                            fragileBlocksMarked++;
                        }
                        if (nonSolid) {
                            nonSolidBlocksMarked++;
                        }
                        if (fluid) {
                            fluidBlocksMarked++;
                        }
                    }
                }
            }

            steps++;
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
        }

        return new RayResult(steps, marked, StopReason.RADIUS);
    }

    private static double resistanceCost(double resistance, double distanceCostMultiplier) {
        double baseCost = Math.pow(
                Math.max(0.0D, resistance) + NUKE_RESISTANCE_COST_OFFSET,
                NUKE_RESISTANCE_POWER
        );
        double cost = baseCost * NUKE_RESISTANCE_COST_MULTIPLIER * distanceCostMultiplier;
        if (resistance > 0.0D) {
            cost = Math.max(cost, NUKE_RESISTANCE_MIN_SOLID_COST);
        }
        return cost;
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

    public double resistanceCostMultiplier() {
        return NUKE_RESISTANCE_COST_MULTIPLIER;
    }

    public double resistanceNonlinearPower() {
        return NUKE_RESISTANCE_POWER;
    }

    public double resistanceCostOffset() {
        return NUKE_RESISTANCE_COST_OFFSET;
    }

    public double distanceResistanceGrowth() {
        return NUKE_DISTANCE_RESISTANCE_GROWTH;
    }

    public String resistanceCostSamples() {
        return "r0.6[d0=" + formatCost(sampleResistanceCost(0.6D, 0.0D))
                + ",d0.5=" + formatCost(sampleResistanceCost(0.6D, 0.5D))
                + ",d1=" + formatCost(sampleResistanceCost(0.6D, 1.0D))
                + "] r6[d0=" + formatCost(sampleResistanceCost(6.0D, 0.0D))
                + ",d0.5=" + formatCost(sampleResistanceCost(6.0D, 0.5D))
                + ",d1=" + formatCost(sampleResistanceCost(6.0D, 1.0D))
                + "] r12[d0=" + formatCost(sampleResistanceCost(12.0D, 0.0D))
                + ",d0.5=" + formatCost(sampleResistanceCost(12.0D, 0.5D))
                + ",d1=" + formatCost(sampleResistanceCost(12.0D, 1.0D))
                + "] r18[d0=" + formatCost(sampleResistanceCost(18.0D, 0.0D))
                + ",d0.5=" + formatCost(sampleResistanceCost(18.0D, 0.5D))
                + ",d1=" + formatCost(sampleResistanceCost(18.0D, 1.0D))
                + "]";
    }

    private static double sampleResistanceCost(double resistance, double distanceProgress) {
        double distanceCostMultiplier = 1.0D + distanceProgress * distanceProgress * NUKE_DISTANCE_RESISTANCE_GROWTH;
        return resistanceCost(resistance, distanceCostMultiplier);
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

    public long unbreakableStops() {
        return unbreakableStops;
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
