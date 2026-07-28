package com.skyeshade.skyent.content.radiation;

import com.skyeshade.skyent.SkyesNuclearTech;
import com.skyeshade.skyent.config.SkyentRadiationConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Set;

public final class RadiationExposureUtil {
    private static final double EXPOSURE_RAY_STEP = 0.5D;
    private static final double MIN_TRANSMISSION = 0.001D;
    private static final double SOURCE_STRENGTH_TIE_EPSILON = 1.0E-6D;

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
        if (SkyentRadiationConfig.exposureSourceSamplingCapEnabled()
                && SkyentRadiationConfig.exposureStreamingSourceSelectionEnabled()) {
            return scanEnvironmentalExposureStreaming(level, entityPos, scanRadius, excludedEntity);
        }

        long totalStartNs = System.nanoTime();
        long collectionStartNs = totalStartNs;
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
        long collectionNs = System.nanoTime() - collectionStartNs;

        long selectionStartNs = System.nanoTime();
        SampledSources sampledSources = sampleSources(level, entityPos, contributing, excludedEntity);
        contributing = sampledSources.sources();
        long selectionNs = System.nanoTime() - selectionStartNs;

        long raycastStartNs = System.nanoTime();
        double exposure = 0.0D;
        double strongestContribution = 0.0D;
        int raycastsPerformed = 0;
        for (SourceCandidate source : contributing) {
            raycastsPerformed++;
            double transmission = calculateTransmissionBetween(level, source.center(), entityPos, source.sourceBlockToSkip());
            if (transmission <= 0.0D) {
                continue;
            }

            double contribution = source.baseContribution() * transmission;
            strongestContribution = Math.max(strongestContribution, contribution);
            exposure += contribution;
        }
        long raycastNs = System.nanoTime() - raycastStartNs;
        long totalNs = System.nanoTime() - totalStartNs;

        ExposureScanResult result = new ExposureScanResult(
                exposure,
                foundSources,
                contributingSources,
                nearestSourceDistance,
                strongestContribution,
                registry.size(),
                sourceScan.registryCandidates(),
                sampledSources.hottestCount(),
                sampledSources.closestCount(),
                sampledSources.randomCount(),
                sampledSources.hotCandidatePoolCount(),
                sampledSources.duplicatesRemoved(),
                contributing.size(),
                raycastsPerformed,
                0,
                0,
                sourceScan.registryCandidates(),
                foundSources,
                sampledSources.strongestSourceStrength(),
                sampledSources.strongestSourceDistance(),
                sampledSources.strongestSourceSelected(),
                sampledSources.chosenHottestMinStrength(),
                sampledSources.chosenHottestMaxStrength(),
                sampledSources.chosenHottestNearestDistance(),
                sampledSources.chosenHottestFarthestDistance(),
                false,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                scanRadius,
                nanosToMillis(collectionNs),
                nanosToMillis(selectionNs),
                nanosToMillis(raycastNs),
                nanosToMillis(totalNs)
        );

        debugExposureSampling(level, excludedEntity, result);
        return result;
    }

    private static ExposureScanResult scanEnvironmentalExposureStreaming(ServerLevel level, Vec3 entityPos, double scanRadius, LivingEntity excludedEntity) {
        long totalStartNs = System.nanoTime();
        long collectionStartNs = totalStartNs;
        RadioactiveSourceRegistry registry = RadioactiveSourceRegistry.get(level);
        SelectionAccumulator accumulator = new SelectionAccumulator(level, entityPos);

        RadioactiveSourceRegistry.NearbySourceScanStats registryStats = registry.scanExposureSourcesNear(level, entityPos, scanRadius, sourceRef -> {
            Vec3 sourceCenter = sourceRef.center();
            double distance = sourceCenter.distanceTo(entityPos);
            double strength = sourceRef.strength();
            double baseContribution = 0.0D;
            if (distance <= sourceRef.range()) {
                double clampedDistance = Math.max(1.0D, distance);
                baseContribution = strength / (clampedDistance * clampedDistance);
            }
            accumulator.observe(new SourceCandidate(sourceRef.pos(), sourceCenter, distance, strength, baseContribution, sourceRef.pos()));
        });

        for (SourceCandidate source : findCarriedRadiationSources(level, entityPos, scanRadius, excludedEntity)) {
            accumulator.observe(source);
        }
        for (SourceCandidate source : findRadioactiveCarrierEntitySources(level, entityPos, scanRadius)) {
            accumulator.observe(source);
        }
        long collectionNs = System.nanoTime() - collectionStartNs;

        long selectionStartNs = System.nanoTime();
        SampledSources sampledSources = accumulator.finish();
        List<SourceCandidate> contributing = sampledSources.sources();
        long selectionNs = System.nanoTime() - selectionStartNs;

        long raycastStartNs = System.nanoTime();
        double exposure = 0.0D;
        double strongestContribution = 0.0D;
        int raycastsPerformed = 0;
        for (SourceCandidate source : contributing) {
            raycastsPerformed++;
            double transmission = calculateTransmissionBetween(level, source.center(), entityPos, source.sourceBlockToSkip());
            if (transmission <= 0.0D) {
                continue;
            }

            double contribution = source.baseContribution() * transmission;
            strongestContribution = Math.max(strongestContribution, contribution);
            exposure += contribution;
        }
        long raycastNs = System.nanoTime() - raycastStartNs;
        long totalNs = System.nanoTime() - totalStartNs;

        ExposureScanResult result = new ExposureScanResult(
                exposure,
                accumulator.sourcesFound(),
                accumulator.contributingSources(),
                accumulator.nearestSourceDistance(),
                strongestContribution,
                registry.size(),
                registryStats.sourcesWithinRadius(),
                sampledSources.hottestCount(),
                sampledSources.closestCount(),
                sampledSources.randomCount(),
                sampledSources.hotCandidatePoolCount(),
                sampledSources.duplicatesRemoved(),
                contributing.size(),
                raycastsPerformed,
                registryStats.chunkBucketsVisited(),
                registryStats.chunkBucketsWithSources(),
                registryStats.sourceRefsVisited(),
                registryStats.sourcesWithinRadius(),
                sampledSources.strongestSourceStrength(),
                sampledSources.strongestSourceDistance(),
                sampledSources.strongestSourceSelected(),
                sampledSources.chosenHottestMinStrength(),
                sampledSources.chosenHottestMaxStrength(),
                sampledSources.chosenHottestNearestDistance(),
                sampledSources.chosenHottestFarthestDistance(),
                registryStats.spatialIndexEnabled(),
                registryStats.spatialIndexCellSize(),
                registryStats.cellsVisited(),
                registryStats.cellsSkippedByAabb(),
                registryStats.cellsWithSources(),
                registryStats.individualSourceRefsVisited(),
                registryStats.aggregateSourceRefsVisited(),
                registryStats.aggregateSourcesWithinRadius(),
                registryStats.individualSourcesWithinRadius(),
                registryStats.clusteredBlockSourcesRepresented(),
                registryStats.aggregateCellsEnabled(),
                registryStats.aggregateCellsBlockedSparse(),
                registryStats.aggregateCellsBlockedShielding(),
                registryStats.aggregateCellsBlockedDominantSource(),
                registryStats.aggregateCellsBlockedHotSource(),
                registryStats.individualSourcesFromUnaggregatedCells(),
                registryStats.forcedIndividualSources(),
                scanRadius,
                nanosToMillis(collectionNs),
                nanosToMillis(selectionNs),
                nanosToMillis(raycastNs),
                nanosToMillis(totalNs)
        );

        debugExposureSampling(level, excludedEntity, result);
        return result;
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
            double strength = source.getRadiationStrength();
            if (distance > source.getEntityRadiationRange()) {
                sources.add(new SourceCandidate(pos, sourceCenter, distance, strength, 0.0D, pos));
                continue;
            }

            double clampedDistance = Math.max(1.0D, distance);
            double baseContribution = strength / (clampedDistance * clampedDistance);
            sources.add(new SourceCandidate(pos, sourceCenter, distance, strength, baseContribution, pos));
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

            sources.add(new SourceCandidate(null, sourceCenter, distance, strength, baseContribution, null));
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

            sources.add(new SourceCandidate(null, sourceCenter, distance, strength, baseContribution, null));
        }

        return sources;
    }

    private static SampledSources sampleSources(ServerLevel level, Vec3 entityPos, List<SourceCandidate> contributing, LivingEntity target) {
        SourceCandidate strongestSource = strongestSource(contributing);
        if (!SkyentRadiationConfig.exposureSourceSamplingCapEnabled()) {
            int selected = contributing.size();
            return buildSampledSources(contributing, selected, 0, 0, selected, 0, strongestSource, contributing);
        }

        int maxHottest = SkyentRadiationConfig.exposureMaxHottestSources();
        int maxClosest = SkyentRadiationConfig.exposureMaxClosestSources();
        int maxRandom = SkyentRadiationConfig.exposureMaxRandomSources();
        int desiredTotal = maxHottest + maxClosest + maxRandom;
        if (desiredTotal <= 0 || contributing.isEmpty()) {
            return buildSampledSources(List.of(), 0, 0, 0, 0, 0, strongestSource, List.of());
        }

        if (contributing.size() <= desiredTotal) {
            return buildSampledSources(
                    contributing,
                    Math.min(maxHottest, contributing.size()),
                    0,
                    0,
                    Math.min(contributing.size(), SkyentRadiationConfig.exposureHottestCandidatePoolSize()),
                    0,
                    strongestSource,
                    contributing.subList(0, Math.min(maxHottest, contributing.size()))
            );
        }

        LinkedHashSet<SourceCandidate> selected = new LinkedHashSet<>(desiredTotal * 2);
        TopSelection hottestSelection = addHottestByCandidatePool(
                contributing,
                selected,
                maxHottest
        );
        TopSelection closestSelection = addSortedTop(
                contributing,
                selected,
                maxClosest,
                Comparator.comparingDouble(SourceCandidate::distance)
                        .thenComparing(Comparator.comparingDouble(SourceCandidate::sourceStrength).reversed())
        );
        int randomSelected = addRandomRemaining(level, entityPos, contributing, selected, maxRandom);

        List<SourceCandidate> sampled = new ArrayList<>(selected);
        return new SampledSources(
                sampled,
                hottestSelection.added(),
                closestSelection.added(),
                randomSelected,
                hottestSelection.candidatePoolCount(),
                hottestSelection.duplicatesRemoved() + closestSelection.duplicatesRemoved(),
                strongestSource == null ? 0.0D : strongestSource.sourceStrength(),
                strongestSource == null ? Double.NaN : strongestSource.distance(),
                strongestSource != null && selected.contains(strongestSource),
                hottestSelection.minStrength(),
                hottestSelection.maxStrength(),
                hottestSelection.nearestDistance(),
                hottestSelection.farthestDistance()
        );
    }

    private static TopSelection addHottestByCandidatePool(
            List<SourceCandidate> sources,
            Set<SourceCandidate> selected,
            int limit
    ) {
        if (limit <= 0) {
            return TopSelection.EMPTY;
        }

        List<SourceCandidate> strongest = new ArrayList<>(sources);
        strongest.sort(RadiationExposureUtil::compareHottestFirst);
        int poolSize = Math.min(strongest.size(), Math.max(limit, SkyentRadiationConfig.exposureHottestCandidatePoolSize()));
        List<SourceCandidate> hotPool = new ArrayList<>(strongest.subList(0, poolSize));
        TopSelection selectedFromPool = addSortedTop(hotPool, selected, limit, RadiationExposureUtil::compareHottestFirst);
        HottestDebugStats hottestStats = HottestDebugStats.from(hotPool.subList(0, Math.min(limit, hotPool.size())));
        return new TopSelection(
                selectedFromPool.added(),
                selectedFromPool.duplicatesRemoved(),
                poolSize,
                hottestStats.minStrength(),
                hottestStats.maxStrength(),
                hottestStats.nearestDistance(),
                hottestStats.farthestDistance()
        );
    }

    private static TopSelection addSortedTop(
            List<SourceCandidate> sources,
            Set<SourceCandidate> selected,
            int limit,
            Comparator<SourceCandidate> comparator
    ) {
        if (limit <= 0) {
            return TopSelection.EMPTY;
        }

        List<SourceCandidate> sorted = new ArrayList<>(sources);
        sorted.sort(comparator);
        int added = 0;
        int duplicates = 0;
        for (SourceCandidate source : sorted) {
            if (selected.add(source)) {
                added++;
                if (added >= limit) {
                    break;
                }
            } else {
                duplicates++;
            }
        }
        return new TopSelection(added, duplicates, sorted.size(), 0.0D, 0.0D, Double.NaN, Double.NaN);
    }

    private static int addRandomRemaining(
            ServerLevel level,
            Vec3 entityPos,
            List<SourceCandidate> sources,
            Set<SourceCandidate> selected,
            int limit
    ) {
        if (limit <= 0) {
            return 0;
        }

        List<SourceCandidate> remaining = new ArrayList<>();
        for (SourceCandidate source : sources) {
            if (!selected.contains(source)) {
                remaining.add(source);
            }
        }

        int added = 0;
        RandomSource random = RandomSource.create(sampleSeed(level, entityPos));
        for (int index = remaining.size() - 1; index >= 0 && added < limit; index--) {
            int pickedIndex = random.nextInt(index + 1);
            SourceCandidate picked = remaining.get(pickedIndex);
            remaining.set(pickedIndex, remaining.get(index));
            if (selected.add(picked)) {
                added++;
            }
        }
        return added;
    }

    private static SampledSources buildSampledSources(
            List<SourceCandidate> sampled,
            int hottestCount,
            int closestCount,
            int randomCount,
            int hotCandidatePoolCount,
            int duplicatesRemoved,
            SourceCandidate strongestSource,
            List<SourceCandidate> chosenHottest
    ) {
        HottestDebugStats hottestStats = HottestDebugStats.from(chosenHottest);
        return new SampledSources(
                sampled,
                hottestCount,
                closestCount,
                randomCount,
                hotCandidatePoolCount,
                duplicatesRemoved,
                strongestSource == null ? 0.0D : strongestSource.sourceStrength(),
                strongestSource == null ? Double.NaN : strongestSource.distance(),
                strongestSource != null && sampled.contains(strongestSource),
                hottestStats.minStrength(),
                hottestStats.maxStrength(),
                hottestStats.nearestDistance(),
                hottestStats.farthestDistance()
        );
    }

    private static SourceCandidate strongestSource(List<SourceCandidate> sources) {
        SourceCandidate strongest = null;
        for (SourceCandidate source : sources) {
            if (strongest == null || compareHottestFirst(source, strongest) < 0) {
                strongest = source;
            }
        }
        return strongest;
    }

    private static int compareHottestFirst(SourceCandidate first, SourceCandidate second) {
        double strengthDelta = second.sourceStrength() - first.sourceStrength();
        if (Math.abs(strengthDelta) > SOURCE_STRENGTH_TIE_EPSILON) {
            return strengthDelta > 0.0D ? 1 : -1;
        }

        int distance = Double.compare(first.distance(), second.distance());
        if (distance != 0) {
            return distance;
        }

        return Long.compare(first.stableKey(), second.stableKey());
    }

    private static long sampleSeed(ServerLevel level, Vec3 entityPos) {
        long seed = level.getSeed() ^ (level.getGameTime() * 0x9E3779B97F4A7C15L);
        seed ^= Double.doubleToLongBits(entityPos.x) * 31L;
        seed ^= Double.doubleToLongBits(entityPos.y) * 17L;
        seed ^= Double.doubleToLongBits(entityPos.z) * 13L;
        return seed;
    }

    private static void debugExposureSampling(ServerLevel level, LivingEntity target, ExposureScanResult result) {
        boolean player = target instanceof ServerPlayer;
        boolean compactDebug = player
                ? SkyentRadiationConfig.debugPlayerExposureSampling()
                : SkyentRadiationConfig.debugEntityExposureSampling();
        boolean traceDebug = SkyentRadiationConfig.traceExposureSampling();
        if (!compactDebug && !traceDebug) {
            return;
        }

        if (!player && level.getGameTime() % 100L != 0L) {
            return;
        }

        boolean immune = target instanceof ServerPlayer serverPlayer && (serverPlayer.isCreative() || serverPlayer.isSpectator());
        String targetName = target instanceof ServerPlayer serverPlayer
                ? serverPlayer.getGameProfile().getName()
                : target == null ? "geiger_or_unknown" : target.getType().toShortString();
        String label = player ? "Radiation player exposure debug" : "Radiation entity exposure debug";
        String path = player
                ? "RadiationExposureSystem.tickPlayer -> RadiationExposureUtil.scanEnvironmentalExposure"
                : "RadiationExposureSystem.tickLivingEntity -> RadiationExposureUtil.scanEnvironmentalExposure";

        if (compactDebug) {
            logCompactExposureSampling(level, targetName, label, immune, result);
        }
        if (traceDebug) {
            logTraceExposureSampling(level, targetName, path, immune, result);
        }
    }

    private static void logCompactExposureSampling(ServerLevel level, String targetName, String label, boolean immune, ExposureScanResult result) {
        SkyesNuclearTech.LOGGER.info(
                "{}: target={} dim={} immune={} exposure={} mSv/s nearest={} strongest={} contributors={} selected={} raycasts={} sources={} scanRadius={} timings[collect={}ms select={}ms ray={}ms total={}ms]",
                label,
                targetName,
                level.dimension().location(),
                immune,
                result.exposureMillisievertsPerSecond(),
                result.nearestSourceDistance(),
                result.strongestSourceContribution(),
                result.contributingSources(),
                result.sampledSources(),
                result.exposureRaycasts(),
                result.sourcesWithinRadius(),
                result.scanRadius(),
                result.collectionMillis(),
                result.selectionMillis(),
                result.raycastMillis(),
                result.totalMillis()
        );
    }
    //TODO: eventually remove this before publish bc its only *really* needed for reworking this system bc its a bitch to get good so ive left it here
    private static void logTraceExposureSampling(ServerLevel level, String targetName, String path, boolean immune, ExposureScanResult result) {
        String label = "Radiation exposure trace";
        String sourceSelectionMode = result.spatialIndexEnabled() ? "SPATIAL_INDEX_CLUSTERED" : "CHUNK_BUCKET_FALLBACK";

        SkyesNuclearTech.LOGGER.info(
                "{} spatial: target={} dim={} path={} mode={} spatialIndex={} cellSize={} chunkBucketsVisited={} chunkBucketsWithSources={} sourceRefsVisited={} sourcesWithinRadius={} cellsVisited={} cellsSkippedByAabb={} cellsWithSources={}",
                label,
                targetName,
                level.dimension().location(),
                path,
                sourceSelectionMode,
                result.spatialIndexEnabled(),
                result.spatialIndexCellSize(),
                result.chunkBucketsVisited(),
                result.chunkBucketsWithSources(),
                result.sourceRefsVisited(),
                result.sourcesWithinRadius(),
                result.cellsVisited(),
                result.cellsSkippedByAabb(),
                result.cellsWithSources()
        );
        SkyesNuclearTech.LOGGER.info(
                "{} aggregation: target={} aggregateCellsEnabled={} blockedSparse={} blockedShielding={} blockedDominant={} blockedHot={} individualFromUnaggregated={} forcedIndividual={} individualRefs={} aggregateRefs={} aggregateWithinRadius={} individualWithinRadius={} clusteredRepresented={}",
                label,
                targetName,
                result.aggregateCellsEnabled(),
                result.aggregateCellsBlockedSparse(),
                result.aggregateCellsBlockedShielding(),
                result.aggregateCellsBlockedDominantSource(),
                result.aggregateCellsBlockedHotSource(),
                result.individualSourcesFromUnaggregatedCells(),
                result.forcedIndividualSources(),
                result.individualSourceRefsVisited(),
                result.aggregateSourceRefsVisited(),
                result.aggregateSourcesWithinRadius(),
                result.individualSourcesWithinRadius(),
                result.clusteredBlockSourcesRepresented()
        );
        SkyesNuclearTech.LOGGER.info(
                "{} selection: target={} registered={} found={} contributing={} hotCandidatePool={} chosenHottest={} chosenClosest={} chosenRandom={} duplicatesRemoved={} finalSelected={} raycasts={} strongestSourceStrength={} strongestSourceDistance={} strongestSourceSelected={} chosenHottestStrengthRange=[{},{}] chosenHottestNearestDistance={} chosenHottestFarthestDistance={}",
                label,
                targetName,
                result.registeredSources(),
                result.sourcesFound(),
                result.contributingSources(),
                result.hotCandidatePoolSize(),
                result.sampledHottestSources(),
                result.sampledClosestSources(),
                result.sampledRandomSources(),
                result.duplicatesRemoved(),
                result.sampledSources(),
                result.exposureRaycasts(),
                result.strongestSourceStrength(),
                result.strongestSourceDistance(),
                result.strongestSourceSelected(),
                result.chosenHottestMinStrength(),
                result.chosenHottestMaxStrength(),
                result.chosenHottestNearestDistance(),
                result.chosenHottestFarthestDistance()
        );
        SkyesNuclearTech.LOGGER.info(
                "{} exposure: target={} dim={} immune={} exposure={} mSv/s nearest={} strongestContribution={} scanRadius={} collectionMs={} selectionMs={} raycastMs={} totalMs={}",
                label,
                targetName,
                level.dimension().location(),
                immune,
                result.exposureMillisievertsPerSecond(),
                result.nearestSourceDistance(),
                result.strongestSourceContribution(),
                result.scanRadius(),
                result.collectionMillis(),
                result.selectionMillis(),
                result.raycastMillis(),
                result.totalMillis()
        );
    }

    private static double nanosToMillis(long nanos) {
        return nanos / 1_000_000.0D;
    }

    private static final class SelectionAccumulator {
        private static final Comparator<SourceCandidate> HOTTEST_POOL_HEAD = Comparator
                .comparingDouble(SourceCandidate::sourceStrength)
                .thenComparing(Comparator.comparingDouble(SourceCandidate::distance).reversed());
        private static final Comparator<SourceCandidate> CLOSEST_POOL_HEAD = Comparator
                .comparingDouble(SourceCandidate::distance)
                .reversed()
                .thenComparingDouble(SourceCandidate::sourceStrength);

        private final ServerLevel level;
        private final Vec3 entityPos;
        private final int maxHottest = SkyentRadiationConfig.exposureMaxHottestSources();
        private final int hottestPoolLimit = Math.max(maxHottest, SkyentRadiationConfig.exposureHottestCandidatePoolSize());
        private final int maxClosest = SkyentRadiationConfig.exposureMaxClosestSources();
        private final int maxRandom = SkyentRadiationConfig.exposureMaxRandomSources();
        private final PriorityQueue<SourceCandidate> hottestPool = new PriorityQueue<>(HOTTEST_POOL_HEAD);
        private final PriorityQueue<SourceCandidate> closestPool = new PriorityQueue<>(CLOSEST_POOL_HEAD);
        private final List<SourceCandidate> randomReservoir = new ArrayList<>();
        private final RandomSource random;
        private int sourcesFound;
        private int contributingSources;
        private int randomSeen;
        private double nearestSourceDistance = Double.NaN;
        private SourceCandidate strongestSource;

        private SelectionAccumulator(ServerLevel level, Vec3 entityPos) {
            this.level = level;
            this.entityPos = entityPos;
            this.random = RandomSource.create(sampleSeed(level, entityPos));
        }

        private void observe(SourceCandidate source) {
            sourcesFound++;
            nearestSourceDistance = Double.isNaN(nearestSourceDistance)
                    ? source.distance()
                    : Math.min(nearestSourceDistance, source.distance());
            if (!source.contributes()) {
                return;
            }

            contributingSources++;
            if (strongestSource == null || compareHottestFirst(source, strongestSource) < 0) {
                strongestSource = source;
            }
            offerHottest(source);
            offerClosest(source);
            offerRandom(source);
        }

        private void offerHottest(SourceCandidate source) {
            if (hottestPoolLimit <= 0) {
                return;
            }

            if (hottestPool.size() < hottestPoolLimit) {
                hottestPool.add(source);
                return;
            }

            SourceCandidate weakest = hottestPool.peek();
            if (weakest != null && compareHotter(source, weakest) > 0) {
                hottestPool.poll();
                hottestPool.add(source);
            }
        }

        private void offerClosest(SourceCandidate source) {
            if (maxClosest <= 0) {
                return;
            }

            if (closestPool.size() < maxClosest) {
                closestPool.add(source);
                return;
            }

            SourceCandidate farthest = closestPool.peek();
            if (farthest != null && compareCloser(source, farthest) < 0) {
                closestPool.poll();
                closestPool.add(source);
            }
        }

        private void offerRandom(SourceCandidate source) {
            if (maxRandom <= 0) {
                return;
            }

            randomSeen++;
            if (randomReservoir.size() < maxRandom) {
                randomReservoir.add(source);
                return;
            }

            int picked = random.nextInt(randomSeen);
            if (picked < maxRandom) {
                randomReservoir.set(picked, source);
            }
        }

        private SampledSources finish() {
            int desiredTotal = maxHottest + maxClosest + maxRandom;
            if (desiredTotal <= 0 || contributingSources <= 0) {
                return buildSampledSources(List.of(), 0, 0, 0, hottestPool.size(), 0, strongestSource, List.of());
            }

            LinkedHashSet<SourceCandidate> selected = new LinkedHashSet<>(desiredTotal * 2);
            int duplicates = 0;

            List<SourceCandidate> hotCandidates = new ArrayList<>(hottestPool);
            hotCandidates.sort(RadiationExposureUtil::compareHottestFirst);
            List<SourceCandidate> chosenHottest = hotCandidates.subList(0, Math.min(maxHottest, hotCandidates.size()));
            int hottestSelected = addOrdered(hotCandidates, selected, maxHottest);

            List<SourceCandidate> closestCandidates = new ArrayList<>(closestPool);
            closestCandidates.sort(Comparator.comparingDouble(SourceCandidate::distance)
                    .thenComparing(Comparator.comparingDouble(SourceCandidate::sourceStrength).reversed()));
            int selectedBeforeClosest = selected.size();
            int closestSelected = addOrdered(closestCandidates, selected, maxClosest);
            duplicates += Math.max(0, Math.min(maxClosest, closestCandidates.size()) - (selected.size() - selectedBeforeClosest));

            int selectedBeforeRandom = selected.size();
            int randomSelected = addOrdered(randomReservoir, selected, maxRandom);
            duplicates += Math.max(0, Math.min(maxRandom, randomReservoir.size()) - (selected.size() - selectedBeforeRandom));

            HottestDebugStats hottestStats = HottestDebugStats.from(chosenHottest);
            return new SampledSources(
                    new ArrayList<>(selected),
                    hottestSelected,
                    closestSelected,
                    randomSelected,
                    hotCandidates.size(),
                    duplicates,
                    strongestSource == null ? 0.0D : strongestSource.sourceStrength(),
                    strongestSource == null ? Double.NaN : strongestSource.distance(),
                    strongestSource != null && selected.contains(strongestSource),
                    hottestStats.minStrength(),
                    hottestStats.maxStrength(),
                    hottestStats.nearestDistance(),
                    hottestStats.farthestDistance()
            );
        }

        private int sourcesFound() {
            return sourcesFound;
        }

        private int contributingSources() {
            return contributingSources;
        }

        private double nearestSourceDistance() {
            return nearestSourceDistance;
        }

        private static int addOrdered(List<SourceCandidate> candidates, Set<SourceCandidate> selected, int limit) {
            int added = 0;
            for (SourceCandidate candidate : candidates) {
                if (selected.add(candidate)) {
                    added++;
                    if (added >= limit) {
                        break;
                    }
                }
            }
            return added;
        }

        private static int compareHotter(SourceCandidate first, SourceCandidate second) {
            double strengthDelta = first.sourceStrength() - second.sourceStrength();
            if (Math.abs(strengthDelta) > SOURCE_STRENGTH_TIE_EPSILON) {
                return strengthDelta > 0.0D ? 1 : -1;
            }
            int distance = Double.compare(second.distance(), first.distance());
            if (distance != 0) {
                return distance;
            }
            return Long.compare(second.stableKey(), first.stableKey());
        }

        private static int compareCloser(SourceCandidate first, SourceCandidate second) {
            int distance = Double.compare(first.distance(), second.distance());
            if (distance != 0) {
                return distance;
            }
            return Double.compare(second.sourceStrength(), first.sourceStrength());
        }
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
            transmission *= RadiationUtil.entityRadiationTransmission(state, level, currentPos, start, end);
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
            int registryCandidates,
            int sampledHottestSources,
            int sampledClosestSources,
            int sampledRandomSources,
            int hotCandidatePoolSize,
            int duplicatesRemoved,
            int sampledSources,
            int exposureRaycasts,
            int chunkBucketsVisited,
            int chunkBucketsWithSources,
            int sourceRefsVisited,
            int sourcesWithinRadius,
            double strongestSourceStrength,
            double strongestSourceDistance,
            boolean strongestSourceSelected,
            double chosenHottestMinStrength,
            double chosenHottestMaxStrength,
            double chosenHottestNearestDistance,
            double chosenHottestFarthestDistance,
            boolean spatialIndexEnabled,
            int spatialIndexCellSize,
            int cellsVisited,
            int cellsSkippedByAabb,
            int cellsWithSources,
            int individualSourceRefsVisited,
            int aggregateSourceRefsVisited,
            int aggregateSourcesWithinRadius,
            int individualSourcesWithinRadius,
            int clusteredBlockSourcesRepresented,
            int aggregateCellsEnabled,
            int aggregateCellsBlockedSparse,
            int aggregateCellsBlockedShielding,
            int aggregateCellsBlockedDominantSource,
            int aggregateCellsBlockedHotSource,
            int individualSourcesFromUnaggregatedCells,
            int forcedIndividualSources,
            double scanRadius,
            double collectionMillis,
            double selectionMillis,
            double raycastMillis,
            double totalMillis
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

    private record TopSelection(
            int added,
            int duplicatesRemoved,
            int candidatePoolCount,
            double minStrength,
            double maxStrength,
            double nearestDistance,
            double farthestDistance
    ) {
        private static final TopSelection EMPTY = new TopSelection(0, 0, 0, 0.0D, 0.0D, Double.NaN, Double.NaN);
    }

    private record HottestDebugStats(double minStrength, double maxStrength, double nearestDistance, double farthestDistance) {
        private static HottestDebugStats from(List<SourceCandidate> sources) {
            if (sources.isEmpty()) {
                return new HottestDebugStats(0.0D, 0.0D, Double.NaN, Double.NaN);
            }

            double minStrength = Double.POSITIVE_INFINITY;
            double maxStrength = 0.0D;
            double nearest = Double.NaN;
            double farthest = Double.NaN;
            for (SourceCandidate source : sources) {
                minStrength = Math.min(minStrength, source.sourceStrength());
                maxStrength = Math.max(maxStrength, source.sourceStrength());
                nearest = Double.isNaN(nearest) ? source.distance() : Math.min(nearest, source.distance());
                farthest = Double.isNaN(farthest) ? source.distance() : Math.max(farthest, source.distance());
            }

            return new HottestDebugStats(minStrength, maxStrength, nearest, farthest);
        }
    }

    private record SampledSources(
            List<SourceCandidate> sources,
            int hottestCount,
            int closestCount,
            int randomCount,
            int hotCandidatePoolCount,
            int duplicatesRemoved,
            double strongestSourceStrength,
            double strongestSourceDistance,
            boolean strongestSourceSelected,
            double chosenHottestMinStrength,
            double chosenHottestMaxStrength,
            double chosenHottestNearestDistance,
            double chosenHottestFarthestDistance
    ) {
    }

    private record SourceCandidate(BlockPos pos, Vec3 center, double distance, double sourceStrength, double baseContribution, BlockPos sourceBlockToSkip) {
        private boolean contributes() {
            return baseContribution > 0.0D;
        }

        private long stableKey() {
            if (pos != null) {
                return pos.asLong();
            }

            long x = Double.doubleToLongBits(center.x);
            long y = Double.doubleToLongBits(center.y);
            long z = Double.doubleToLongBits(center.z);
            return x ^ Long.rotateLeft(y, 21) ^ Long.rotateLeft(z, 42);
        }
    }
}
