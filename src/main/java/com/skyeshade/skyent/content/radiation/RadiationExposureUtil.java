package com.skyeshade.skyent.content.radiation;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class RadiationExposureUtil {
    public static final double DEFAULT_PLAYER_SCAN_RADIUS = 128.0D;
    public static final int MAX_SOURCES_PROCESSED = 256;
    private static final double EXPOSURE_RAY_STEP = 0.5D;
    private static final double MIN_TRANSMISSION = 0.001D;

    private RadiationExposureUtil() {
    }

    public static double calculateEnvironmentalExposure(ServerLevel level, Vec3 entityPos, double scanRadius) {
        return scanEnvironmentalExposure(level, entityPos, scanRadius).exposureMillisievertsPerSecond();
    }

    public static double calculateEnvironmentalExposure(ServerLevel level, Vec3 entityPos, double scanRadius, LivingEntity excludedEntity) {
        return scanEnvironmentalExposure(level, entityPos, scanRadius, excludedEntity).exposureMillisievertsPerSecond();
    }

    public static ExposureScanResult scanEnvironmentalExposure(ServerLevel level, Vec3 entityPos, double scanRadius) {
        return scanEnvironmentalExposure(level, entityPos, scanRadius, null);
    }

    public static ExposureScanResult scanEnvironmentalExposure(ServerLevel level, Vec3 entityPos, double scanRadius, LivingEntity excludedEntity) {
        RadioactiveSourceRegistry registry = RadioactiveSourceRegistry.get(level);
        SourceScanResult sourceScan = findRadioactiveSources(level, entityPos, scanRadius, registry);
        List<SourceCandidate> sources = sourceScan.sources();
        sources.addAll(findCarriedRadiationSources(level, entityPos, scanRadius, excludedEntity));
        sources.addAll(findRadioactiveCarrierEntitySources(level, entityPos, scanRadius));
        int foundSources = sources.size();
        int contributingSources = 0;
        double nearestSourceDistance = Double.NaN;
        List<SourceCandidate> contributing = new ArrayList<>();

        for (SourceCandidate source : sources) {
            nearestSourceDistance = Double.isNaN(nearestSourceDistance)
                    ? source.distance()
                    : Math.min(nearestSourceDistance, source.distance());
            if (source.contributes()) {
                contributingSources++;
                contributing.add(source);
            }
        }

        if (contributing.size() > MAX_SOURCES_PROCESSED) {
            contributing.sort(Comparator.comparingDouble(SourceCandidate::approximateContribution).reversed());
            contributing = contributing.subList(0, MAX_SOURCES_PROCESSED);
        }

        double exposure = 0.0D;
        double strongestContribution = 0.0D;
        for (SourceCandidate source : contributing) {
            double transmission = calculateTransmissionBetween(level, source.center(), entityPos, source.sourceBlockToSkip());
            if (transmission <= 0.0D) {
                continue;
            }

            double contribution = source.baseContribution() * transmission;
            strongestContribution = Math.max(strongestContribution, contribution);
            exposure += contribution;
        }

        return new ExposureScanResult(
                exposure,
                foundSources,
                contributingSources,
                nearestSourceDistance,
                strongestContribution,
                registry.size(),
                sourceScan.registryCandidates()
        );
    }

    public static double calculateRayTransmission(ServerLevel level, Vec3 start, Vec3 end) {
        return calculateTransmissionBetween(level, start, end, null);
    }

    public static PointSourceExposure calculatePointSourceExposure(
            ServerLevel level,
            Vec3 sourceCenter,
            Vec3 entityPos,
            double sourceMillisievertsPerSecond,
            double radius
    ) {
        if (sourceMillisievertsPerSecond <= 0.0D || radius <= 0.0D) {
            return PointSourceExposure.none(sourceCenter.distanceTo(entityPos));
        }

        double distance = sourceCenter.distanceTo(entityPos);
        if (distance > radius) {
            return PointSourceExposure.none(distance);
        }

        double clampedDistance = Math.max(1.0D, distance);
        double baseExposure = sourceMillisievertsPerSecond / (clampedDistance * clampedDistance);
        double transmission = calculateTransmissionBetween(level, sourceCenter, entityPos, null);
        return new PointSourceExposure(distance, transmission, baseExposure * transmission);
    }

    private static SourceScanResult findRadioactiveSources(ServerLevel level, Vec3 entityPos, double scanRadius, RadioactiveSourceRegistry registry) {
        List<SourceCandidate> sources = new ArrayList<>();
        List<BlockPos> candidates = registry.getSourcesNear(entityPos, scanRadius);

        for (BlockPos pos : candidates) {
            if (!level.hasChunkAt(pos)) {
                continue;
            }

            BlockState state = level.getBlockState(pos);
            if (!(state.getBlock() instanceof RadioactiveSource source)) {
                registry.unregister(pos);
                continue;
            }

            Vec3 sourceCenter = Vec3.atCenterOf(pos);
            double distance = sourceCenter.distanceTo(entityPos);
            if (distance > source.getEntityRadiationRange()) {
                sources.add(new SourceCandidate(pos, sourceCenter, distance, 0.0D, pos));
                continue;
            }

            double clampedDistance = Math.max(1.0D, distance);
            double baseContribution = source.getRadiationStrength() / (clampedDistance * clampedDistance);
            sources.add(new SourceCandidate(pos, sourceCenter, distance, baseContribution, pos));
        }

        return new SourceScanResult(sources, candidates.size());
    }

    private static List<SourceCandidate> findCarriedRadiationSources(ServerLevel level, Vec3 entityPos, double scanRadius, LivingEntity excludedEntity) {
        double queryRadius = Math.min(scanRadius, CarriedRadiationUtil.MAX_CARRIED_RADIATION_RANGE);
        if (queryRadius <= 0.0D) {
            return List.of();
        }

        AABB searchBox = new AABB(entityPos, entityPos).inflate(queryRadius);
        List<SourceCandidate> sources = new ArrayList<>();
        for (LivingEntity carrier : level.getEntitiesOfClass(LivingEntity.class, searchBox, entity -> entity.isAlive() && !entity.isRemoved())) {
            if (carrier == excludedEntity) {
                continue;
            }

            double strength = CarriedRadiationUtil.carriedRadiationStrength(carrier);
            int range = CarriedRadiationUtil.carriedRadiationRange(strength);
            if (range <= 0) {
                continue;
            }

            Vec3 sourceCenter = carrier.position().add(0.0D, carrier.getBbHeight() * 0.5D, 0.0D);
            double distance = sourceCenter.distanceTo(entityPos);
            if (distance > scanRadius) {
                continue;
            }

            double baseContribution = 0.0D;
            if (distance <= range) {
                double clampedDistance = Math.max(1.0D, distance);
                baseContribution = strength / (clampedDistance * clampedDistance);
            }

            sources.add(new SourceCandidate(null, sourceCenter, distance, baseContribution, null));
        }

        return sources;
    }

    private static List<SourceCandidate> findRadioactiveCarrierEntitySources(ServerLevel level, Vec3 entityPos, double scanRadius) {
        double queryRadius = Math.min(scanRadius, CarriedRadiationUtil.MAX_CARRIED_RADIATION_RANGE);
        if (queryRadius <= 0.0D) {
            return List.of();
        }

        AABB searchBox = new AABB(entityPos, entityPos).inflate(queryRadius);
        List<SourceCandidate> sources = new ArrayList<>();
        for (Entity entity : level.getEntitiesOfClass(Entity.class, searchBox, candidate -> !candidate.isRemoved() && candidate instanceof RadioactiveCarrierEntity)) {
            if (entity instanceof LivingEntity) {
                continue;
            }

            RadioactiveCarrierEntity carrier = (RadioactiveCarrierEntity) entity;
            ItemStack stack = carrier.skyent$getRadiationStack();
            double strength = RadiationItemValues.getStackRadiation(stack);
            int range = CarriedRadiationUtil.carriedRadiationRange(strength);
            if (range <= 0) {
                continue;
            }

            Vec3 sourceCenter = carrier.skyent$getRadiationPosition();
            double distance = sourceCenter.distanceTo(entityPos);
            if (distance > scanRadius) {
                continue;
            }

            double baseContribution = 0.0D;
            if (distance <= range) {
                double clampedDistance = Math.max(1.0D, distance);
                baseContribution = strength / (clampedDistance * clampedDistance);
            }

            sources.add(new SourceCandidate(null, sourceCenter, distance, baseContribution, null));
        }

        return sources;
    }

    private static double calculateTransmissionBetween(ServerLevel level, Vec3 start, Vec3 end, BlockPos sourcePos) {
        Vec3 delta = end.subtract(start);
        double distance = delta.length();
        if (distance <= 1.0E-6D) {
            return 1.0D;
        }

        Vec3 direction = delta.normalize();
        int steps = Mth.ceil(distance / EXPOSURE_RAY_STEP);
        double transmission = 1.0D;
        Set<BlockPos> visited = new HashSet<>();

        for (int step = 1; step <= steps; step++) {
            double stepDistance = Math.min(distance, step * EXPOSURE_RAY_STEP);
            BlockPos currentPos = BlockPos.containing(start.add(direction.scale(stepDistance)));
            if (sourcePos != null && currentPos.equals(sourcePos) || !visited.add(currentPos)) {
                continue;
            }

            if (!level.hasChunkAt(currentPos)) {
                return 0.0D;
            }

            BlockState state = level.getBlockState(currentPos);
            transmission *= RadiationUtil.environmentalRadiationTransmission(state, level, currentPos);
            if (transmission <= MIN_TRANSMISSION) {
                return 0.0D;
            }
        }

        return transmission;
    }

    public record ExposureScanResult(
            double exposureMillisievertsPerSecond,
            int sourcesFound,
            int contributingSources,
            double nearestSourceDistance,
            double strongestSourceContribution,
            int registeredSources,
            int registryCandidates
    ) {
    }

    public record PointSourceExposure(
            double distance,
            double transmission,
            double exposureMillisievertsPerSecond
    ) {
        private static PointSourceExposure none(double distance) {
            return new PointSourceExposure(distance, 0.0D, 0.0D);
        }
    }

    private record SourceScanResult(List<SourceCandidate> sources, int registryCandidates) {
    }

    private record SourceCandidate(BlockPos pos, Vec3 center, double distance, double baseContribution, BlockPos sourceBlockToSkip) {
        private boolean contributes() {
            return baseContribution > 0.0D;
        }

        private double approximateContribution() {
            return baseContribution;
        }
    }
}
