package com.skyeshade.skyent.content.radiation;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class RadiationExposureUtil {
    public static final double DEFAULT_PLAYER_SCAN_RADIUS = 64.0D;
    public static final int MAX_SOURCES_PROCESSED = 256;
    private static final double EXPOSURE_RAY_STEP = 0.5D;
    private static final double MIN_TRANSMISSION = 0.001D;

    private RadiationExposureUtil() {
    }

    public static double calculateEnvironmentalExposure(ServerLevel level, Vec3 entityPos, double scanRadius) {
        return scanEnvironmentalExposure(level, entityPos, scanRadius).exposureMillisievertsPerSecond();
    }

    public static ExposureScanResult scanEnvironmentalExposure(ServerLevel level, Vec3 entityPos, double scanRadius) {
        RadioactiveSourceRegistry registry = RadioactiveSourceRegistry.get(level);
        SourceScanResult sourceScan = findRadioactiveSources(level, entityPos, scanRadius, registry);
        List<SourceCandidate> sources = sourceScan.sources();
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
            double transmission = calculateTransmissionBetween(level, source.center(), entityPos, source.pos());
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
                sources.add(new SourceCandidate(pos, sourceCenter, distance, 0.0D));
                continue;
            }

            double clampedDistance = Math.max(1.0D, distance);
            double baseContribution = source.getRadiationStrength() / (clampedDistance * clampedDistance);
            sources.add(new SourceCandidate(pos, sourceCenter, distance, baseContribution));
        }

        return new SourceScanResult(sources, candidates.size());
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
            if (currentPos.equals(sourcePos) || !visited.add(currentPos)) {
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

    private record SourceScanResult(List<SourceCandidate> sources, int registryCandidates) {
    }

    private record SourceCandidate(BlockPos pos, Vec3 center, double distance, double baseContribution) {
        private boolean contributes() {
            return baseContribution > 0.0D;
        }

        private double approximateContribution() {
            return baseContribution;
        }
    }
}
