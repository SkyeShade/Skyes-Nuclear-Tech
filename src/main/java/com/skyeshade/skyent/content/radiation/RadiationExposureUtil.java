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
import java.util.HashMap;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.UUID;

public final class RadiationExposureUtil {
    private static final double EXPOSURE_RAY_STEP = 0.5D;
    private static final double MIN_TRANSMISSION = 0.001D;
    private static final double SOURCE_STRENGTH_TIE_EPSILON = 1.0E-6D;
    private static final double FAST_MOVE_SCAN_RADIUS_FRACTION = 0.5D;
    private static final Map<CacheKey, RadiationLocalSourceCache> LOCAL_SOURCE_CACHES = new HashMap<>();

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
            if (shouldUseLocalSourceCache(excludedEntity)) {
                return scanEnvironmentalExposureCached(level, entityPos, scanRadius, excludedEntity);
            }
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
                foundSources,
                sourceScan.registryCandidates(),
                true,
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
                0.0D,
                0.0D,
                0.0D,
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
                false,
                0,
                accumulator.sourcesFound(),
                registryStats.sourceRefsVisited(),
                true,
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
                0.0D,
                0.0D,
                0.0D,
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
                nanosToMillis(collectionNs),
                nanosToMillis(selectionNs),
                nanosToMillis(raycastNs),
                nanosToMillis(totalNs)
        );

        debugExposureSampling(level, excludedEntity, result);
        return result;
    }

    private static ExposureScanResult scanEnvironmentalExposureCached(ServerLevel level, Vec3 entityPos, double scanRadius, LivingEntity excludedEntity) {
        long totalStartNs = System.nanoTime();
        long collectionStartNs = totalStartNs;
        RadioactiveSourceRegistry registry = RadioactiveSourceRegistry.get(level);
        RadiationLocalSourceCache cache = LOCAL_SOURCE_CACHES.computeIfAbsent(
                cacheKey(level, entityPos, excludedEntity),
                ignored -> new RadiationLocalSourceCache()
        );
        CacheUpdateStats cacheStats = cache.update(level, registry, entityPos, scanRadius, excludedEntity);
        SelectionAccumulator accumulator = new SelectionAccumulator(level, entityPos);
        MutableCacheCandidateStats candidateStats = new MutableCacheCandidateStats();

        for (CachedSource cachedSource : cache.sources.values()) {
            CacheCandidateAssessment assessment = cachedSource.assess(entityPos, scanRadius, level.getGameTime());
            candidateStats.observe(assessment);
            if (assessment.candidate() != null) {
                accumulator.observe(assessment.candidate());
            }
        }
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
                cacheStats.sourcesWithinRadius(),
                sampledSources.hottestCount(),
                sampledSources.closestCount(),
                sampledSources.randomCount(),
                sampledSources.hotCandidatePoolCount(),
                sampledSources.duplicatesRemoved(),
                contributing.size(),
                raycastsPerformed,
                cacheStats.chunkBucketsVisited(),
                cacheStats.chunkBucketsWithSources(),
                cacheStats.sourceRefsVisited(),
                cacheStats.sourcesWithinRadius(),
                sampledSources.strongestSourceStrength(),
                sampledSources.strongestSourceDistance(),
                sampledSources.strongestSourceSelected(),
                sampledSources.chosenHottestMinStrength(),
                sampledSources.chosenHottestMaxStrength(),
                sampledSources.chosenHottestNearestDistance(),
                sampledSources.chosenHottestFarthestDistance(),
                true,
                cache.sources.size(),
                accumulator.sourcesFound(),
                cacheStats.sampledRefs(),
                cacheStats.fullScanUsed(),
                cacheStats.added(),
                cacheStats.updated(),
                cacheStats.evicted(),
                candidateStats.validEntries,
                candidateStats.invalidEntries,
                candidateStats.withinRadius,
                candidateStats.contributing,
                candidateStats.tooFar,
                candidateStats.stale,
                cacheStats.missingSource(),
                0,
                cacheStats.samplingMillis(),
                cacheStats.evictionMillis(),
                cacheStats.totalMillis(),
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
        if (player && !SkyentRadiationConfig.debugPlayerExposureSampling()) {
            return;
        }
        if (!player && !SkyentRadiationConfig.debugEntityExposureSampling()) {
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

        SkyesNuclearTech.LOGGER.info(
                "{}: target={} dimension={} immune={} cacheEnabled={} cacheSize={} cacheCandidates={} cacheValidEntries={} cacheInvalidEntries={} cacheWithinRadius={} cacheContributing={} cacheTooFar={} cacheStale={} cacheMissingSource={} cacheDuplicateKeys={} cacheAdded={} cacheUpdated={} cacheEvicted={} fullScanUsed={} cacheSamplingMs={} cacheEvictionMs={} cacheMaintenanceMs={} spatialIndexEnabled={} cellSize={} cellsVisited={} cellsSkippedByAabb={} cellsWithSources={} individualSourceRefsVisited={} aggregateSourceRefsVisited={} aggregateSourcesWithinRadius={} individualSourcesWithinRadius={} clusteredBlockSourcesRepresented={} nearbySources={} contributingSources={} chunkBucketsVisited={} chunkBucketsWithSources={} sourceRefsVisited={} sampledRefsThisUpdate={} sourcesWithinRadius={} hotCandidatePool={} chosenHottest={} chosenClosest={} chosenRandom={} duplicatesRemoved={} finalSelected={} raycasts={} strongestSourceStrength={} strongestSourceDistance={} strongestSourceSelected={} chosenHottestStrengthRange=[{},{}] chosenHottestNearestDistance={} chosenHottestFarthestDistance={} collectionMs={} selectionMs={} raycastMs={} totalMs={} exposure={} nearestDistance={} strongestContribution={} playerPath={} entityPath={} scanRadius={}",
                label,
                targetName,
                level.dimension().location(),
                immune,
                result.cacheEnabled(),
                result.cacheSize(),
                result.cacheCandidates(),
                result.cacheValidEntries(),
                result.cacheInvalidEntries(),
                result.cacheWithinRadius(),
                result.cacheContributing(),
                result.cacheTooFar(),
                result.cacheStale(),
                result.cacheMissingSource(),
                result.cacheDuplicateKeys(),
                result.cacheAdded(),
                result.cacheUpdated(),
                result.cacheEvicted(),
                result.fullScanUsed(),
                result.cacheSamplingMillis(),
                result.cacheEvictionMillis(),
                result.cacheMaintenanceMillis(),
                result.spatialIndexEnabled(),
                result.spatialIndexCellSize(),
                result.cellsVisited(),
                result.cellsSkippedByAabb(),
                result.cellsWithSources(),
                result.individualSourceRefsVisited(),
                result.aggregateSourceRefsVisited(),
                result.aggregateSourcesWithinRadius(),
                result.individualSourcesWithinRadius(),
                result.clusteredBlockSourcesRepresented(),
                result.sourcesFound(),
                result.contributingSources(),
                result.chunkBucketsVisited(),
                result.chunkBucketsWithSources(),
                result.sourceRefsVisited(),
                result.sampledRefsThisUpdate(),
                result.sourcesWithinRadius(),
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
                result.chosenHottestFarthestDistance(),
                result.collectionMillis(),
                result.selectionMillis(),
                result.raycastMillis(),
                result.totalMillis(),
                result.exposureMillisievertsPerSecond(),
                result.nearestSourceDistance(),
                result.strongestSourceContribution(),
                "RadiationExposureSystem.tickPlayer -> RadiationExposureUtil.scanEnvironmentalExposure",
                "RadiationExposureSystem.tickLivingEntity -> RadiationExposureUtil.scanEnvironmentalExposure",
                SkyentRadiationConfig.exposureRadioactiveBlockScanRadius()
        );
    }

    private static double nanosToMillis(long nanos) {
        return nanos / 1_000_000.0D;
    }

    private static boolean shouldUseLocalSourceCache(LivingEntity target) {
        if (!SkyentRadiationConfig.exposureLocalSourceCacheEnabled() || target == null) {
            return false;
        }

        if (target instanceof ServerPlayer) {
            return SkyentRadiationConfig.exposurePlayerLocalSourceCacheEnabled();
        }

        return SkyentRadiationConfig.exposureEntityLocalSourceCacheEnabled();
    }

    private static CacheKey cacheKey(ServerLevel level, Vec3 entityPos, LivingEntity target) {
        if (target != null) {
            return new CacheKey(level.dimension().location().toString() + ":" + target.getUUID());
        }

        long x = Mth.floor(entityPos.x * 4.0D);
        long y = Mth.floor(entityPos.y * 4.0D);
        long z = Mth.floor(entityPos.z * 4.0D);
        return new CacheKey(level.dimension().location() + ":pos:" + x + ":" + y + ":" + z);
    }

    private static final class RadiationLocalSourceCache {
        private final Map<Long, CachedSource> sources = new HashMap<>();
        private Vec3 lastTargetPosition;
        private long lastFullRescanTick = Long.MIN_VALUE;
        private int chunkCursor;

        private CacheUpdateStats update(
                ServerLevel level,
                RadioactiveSourceRegistry registry,
                Vec3 entityPos,
                double scanRadius,
                LivingEntity target
        ) {
            long updateStartNs = System.nanoTime();
            long gameTime = level.getGameTime();
            int maxSources = SkyentRadiationConfig.exposureLocalCacheMaxSources();
            double maxDistanceMultiplier = SkyentRadiationConfig.exposureLocalCacheMaxDistanceMultiplier();
            double maxDistanceSqr = scanRadius * scanRadius * maxDistanceMultiplier * maxDistanceMultiplier;
            boolean fastMove = lastTargetPosition != null && lastTargetPosition.distanceToSqr(entityPos) > scanRadius * scanRadius * FAST_MOVE_SCAN_RADIUS_FRACTION * FAST_MOVE_SCAN_RADIUS_FRACTION;
            if (fastMove) {
                evictFar(entityPos, maxDistanceSqr);
                chunkCursor += 31;
            }
            lastTargetPosition = entityPos;

            int sampleBudget = target instanceof ServerPlayer
                    ? SkyentRadiationConfig.exposureLocalCacheSourceRefsSampledPerPlayerUpdate()
                    : SkyentRadiationConfig.exposureLocalCacheSourceRefsSampledPerTick();
            if (fastMove) {
                sampleBudget = Math.min(sampleBudget * 2, sampleBudget + SkyentRadiationConfig.exposureLocalCacheSourceRefsSampledPerPlayerUpdate());
            }

            MutableCacheUpdateStats mutableStats = new MutableCacheUpdateStats();
            boolean fullScan = shouldFullScan(gameTime);
            RadioactiveSourceRegistry.NearbySourceScanStats scanStats;
            long samplingStartNs = System.nanoTime();
            if (fullScan) {
                scanStats = registry.scanSourcesNear(entityPos, scanRadius * maxDistanceMultiplier, pos -> updateSource(level, pos, entityPos, scanRadius, maxDistanceSqr, gameTime, mutableStats));
                lastFullRescanTick = gameTime;
            } else {
                scanStats = registry.sampleSourcesNear(entityPos, scanRadius * maxDistanceMultiplier, chunkCursor, sampleBudget, pos -> updateSource(level, pos, entityPos, scanRadius, maxDistanceSqr, gameTime, mutableStats));
                chunkCursor += Math.max(1, scanStats.chunkBucketsVisited());
            }
            mutableStats.samplingNs = System.nanoTime() - samplingStartNs;

            mutableStats.sampledRefs = fullScan ? scanStats.sourceRefsVisited() : Math.min(sampleBudget, scanStats.sourceRefsVisited());
            mutableStats.fullScanUsed = fullScan;
            mutableStats.chunkBucketsVisited = scanStats.chunkBucketsVisited();
            mutableStats.chunkBucketsWithSources = scanStats.chunkBucketsWithSources();
            mutableStats.sourceRefsVisited = scanStats.sourceRefsVisited();
            mutableStats.sourcesWithinRadius = scanStats.sourcesWithinRadius();
            long evictionStartNs = System.nanoTime();
            mutableStats.evicted += evictInvalidStaleFarAndOverflow(entityPos, scanRadius, gameTime, maxSources);
            mutableStats.evictionNs = System.nanoTime() - evictionStartNs;
            mutableStats.totalNs = System.nanoTime() - updateStartNs;
            return mutableStats.toImmutable();
        }

        private boolean shouldFullScan(long gameTime) {
            int minBeforeFullScan = SkyentRadiationConfig.exposureLocalCacheMinSourcesBeforeFullScan();
            if (minBeforeFullScan > 0 && sources.size() < minBeforeFullScan && lastFullRescanTick == Long.MIN_VALUE) {
                return true;
            }

            int interval = SkyentRadiationConfig.exposureLocalCacheFullRescanIntervalTicks();
            return interval > 0 && (lastFullRescanTick == Long.MIN_VALUE || gameTime - lastFullRescanTick >= interval);
        }

        private void updateSource(
                ServerLevel level,
                BlockPos pos,
                Vec3 entityPos,
                double scanRadius,
                double maxDistanceSqr,
                long gameTime,
                MutableCacheUpdateStats stats
        ) {
            if (!level.hasChunkAt(pos)) {
                return;
            }

            BlockState state = level.getBlockState(pos);
            if (!(state.getBlock() instanceof RadioactiveSource source)) {
                if (sources.remove(pos.asLong()) != null) {
                    stats.evicted++;
                }
                stats.missingSource++;
                RadioactiveSourceRegistry.unregister(level, pos);
                return;
            }

            Vec3 sourceCenter = Vec3.atCenterOf(pos);
            double distanceSqr = sourceCenter.distanceToSqr(entityPos);
            if (distanceSqr > maxDistanceSqr) {
                return;
            }

            long key = pos.asLong();
            CachedSource cachedSource = sources.get(key);
            if (cachedSource == null) {
                sources.put(key, new CachedSource(pos.immutable(), source.getRadiationStrength(), source.getEntityRadiationRange(), gameTime, distanceSqr));
                stats.added++;
            } else {
                cachedSource.refresh(source.getRadiationStrength(), source.getEntityRadiationRange(), gameTime, distanceSqr);
                stats.updated++;
            }
        }

        private void evictFar(Vec3 entityPos, double maxDistanceSqr) {
            sources.values().removeIf(source -> Vec3.atCenterOf(source.pos()).distanceToSqr(entityPos) > maxDistanceSqr);
        }

        private int evictInvalidStaleFarAndOverflow(Vec3 entityPos, double scanRadius, long gameTime, int maxSources) {
            int removed = 0;
            int staleAfterTicks = SkyentRadiationConfig.exposureLocalCacheStaleAfterTicks();
            double maxDistanceMultiplier = SkyentRadiationConfig.exposureLocalCacheMaxDistanceMultiplier();
            double maxDistanceSqr = scanRadius * scanRadius * maxDistanceMultiplier * maxDistanceMultiplier;
            List<CachedSource> evictable = new ArrayList<>();
            for (CachedSource source : sources.values()) {
                double distanceSqr = Vec3.atCenterOf(source.pos()).distanceToSqr(entityPos);
                source.lastDistanceSqr = distanceSqr;
                if (distanceSqr > maxDistanceSqr || gameTime - source.lastSeenTick() > staleAfterTicks) {
                    evictable.add(source);
                }
            }

            int batchSize = SkyentRadiationConfig.exposureLocalCacheEvictionBatchSize();
            evictable.sort(Comparator.comparingDouble(source -> evictionScore(source, gameTime)));
            for (CachedSource source : evictable) {
                if (removed >= batchSize) {
                    break;
                }
                if (sources.remove(source.pos().asLong()) != null) {
                    removed++;
                }
            }

            if (sources.size() > maxSources) {
                List<CachedSource> overflow = new ArrayList<>(sources.values());
                overflow.sort(Comparator.comparingDouble(source -> evictionScore(source, gameTime)));
                for (CachedSource source : overflow) {
                    if (sources.size() <= maxSources) {
                        break;
                    }
                    if (sources.remove(source.pos().asLong()) != null) {
                        removed++;
                    }
                }
            }
            return removed;
        }

        private double evictionScore(CachedSource source, long gameTime) {
            double staleTicks = Math.max(0L, gameTime - source.lastSeenTick());
            double strength = SkyentRadiationConfig.exposureLocalCachePreferEvictionOfWeakSources() ? source.strength() : 0.0D;
            return strength * 0.01D - Math.sqrt(Math.max(0.0D, source.lastDistanceSqr)) - staleTicks * 2.0D;
        }
    }

    private static final class CachedSource {
        private final BlockPos pos;
        private double strength;
        private int range;
        private long lastSeenTick;
        private double lastDistanceSqr;

        private CachedSource(BlockPos pos, double strength, int range, long lastSeenTick, double lastDistanceSqr) {
            this.pos = pos;
            this.strength = strength;
            this.range = range;
            this.lastSeenTick = lastSeenTick;
            this.lastDistanceSqr = lastDistanceSqr;
        }

        private void refresh(double strength, int range, long lastSeenTick, double lastDistanceSqr) {
            this.strength = strength;
            this.range = range;
            this.lastSeenTick = lastSeenTick;
            this.lastDistanceSqr = lastDistanceSqr;
        }

        private CacheCandidateAssessment assess(Vec3 entityPos, double scanRadius, long gameTime) {
            Vec3 center = Vec3.atCenterOf(pos);
            double distance = center.distanceTo(entityPos);
            boolean withinRadius = distance <= scanRadius;
            boolean stale = gameTime - lastSeenTick > SkyentRadiationConfig.exposureLocalCacheStaleAfterTicks();
            if (!withinRadius || distance > range || stale) {
                return new CacheCandidateAssessment(null, withinRadius, false, !withinRadius, stale);
            }

            double clampedDistance = Math.max(1.0D, distance);
            return new CacheCandidateAssessment(
                    new SourceCandidate(pos, center, distance, strength, strength / (clampedDistance * clampedDistance), pos),
                    true,
                    true,
                    false,
                    false
            );
        }

        private BlockPos pos() {
            return pos;
        }

        private double strength() {
            return strength;
        }

        private long lastSeenTick() {
            return lastSeenTick;
        }
    }

    private static final class MutableCacheCandidateStats {
        private int validEntries;
        private int invalidEntries;
        private int withinRadius;
        private int contributing;
        private int tooFar;
        private int stale;

        private void observe(CacheCandidateAssessment assessment) {
            if (assessment.withinRadius()) {
                withinRadius++;
            }
            if (assessment.tooFar()) {
                tooFar++;
            }
            if (assessment.stale()) {
                stale++;
            }
            if (assessment.contributing()) {
                validEntries++;
                contributing++;
            } else {
                invalidEntries++;
            }
        }
    }

    private record CacheCandidateAssessment(
            SourceCandidate candidate,
            boolean withinRadius,
            boolean contributing,
            boolean tooFar,
            boolean stale
    ) {
    }

    private static final class MutableCacheUpdateStats {
        private int chunkBucketsVisited;
        private int chunkBucketsWithSources;
        private int sourceRefsVisited;
        private int sourcesWithinRadius;
        private int sampledRefs;
        private boolean fullScanUsed;
        private int added;
        private int updated;
        private int evicted;
        private int missingSource;
        private long samplingNs;
        private long evictionNs;
        private long totalNs;

        private CacheUpdateStats toImmutable() {
            return new CacheUpdateStats(
                    chunkBucketsVisited,
                    chunkBucketsWithSources,
                    sourceRefsVisited,
                    sourcesWithinRadius,
                    sampledRefs,
                    fullScanUsed,
                    added,
                    updated,
                    evicted,
                    missingSource,
                    nanosToMillis(samplingNs),
                    nanosToMillis(evictionNs),
                    nanosToMillis(totalNs)
            );
        }
    }

    private record CacheUpdateStats(
            int chunkBucketsVisited,
            int chunkBucketsWithSources,
            int sourceRefsVisited,
            int sourcesWithinRadius,
            int sampledRefs,
            boolean fullScanUsed,
            int added,
            int updated,
            int evicted,
            int missingSource,
            double samplingMillis,
            double evictionMillis,
            double totalMillis
    ) {
    }

    private record CacheKey(String value) {
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
            boolean cacheEnabled,
            int cacheSize,
            int cacheCandidates,
            int sampledRefsThisUpdate,
            boolean fullScanUsed,
            int cacheAdded,
            int cacheUpdated,
            int cacheEvicted,
            int cacheValidEntries,
            int cacheInvalidEntries,
            int cacheWithinRadius,
            int cacheContributing,
            int cacheTooFar,
            int cacheStale,
            int cacheMissingSource,
            int cacheDuplicateKeys,
            double cacheSamplingMillis,
            double cacheEvictionMillis,
            double cacheMaintenanceMillis,
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
