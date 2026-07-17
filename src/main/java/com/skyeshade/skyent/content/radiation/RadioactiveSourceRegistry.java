package com.skyeshade.skyent.content.radiation;

import com.skyeshade.skyent.SkyesNuclearTech;
import com.skyeshade.skyent.config.SkyentRadiationConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

public final class RadioactiveSourceRegistry extends SavedData {
    private static final String DATA_NAME = "skyent_radioactive_sources";
    private static final String TAG_POSITIONS = "positions";
    private static final Factory<RadioactiveSourceRegistry> FACTORY = new Factory<>(
            RadioactiveSourceRegistry::new,
            RadioactiveSourceRegistry::load,
            DataFixTypes.LEVEL
    );

    private final Map<Long, Set<BlockPos>> positionsByChunk = new HashMap<>();
    private final Set<BlockPos> positions = new HashSet<>();
    private final Map<Long, ChunkRadiationBucket> spatialChunks = new HashMap<>();
    private final Map<Long, SourceIndexEntry> sourceIndexEntries = new HashMap<>();
    private boolean spatialIndexDirty = true;
    private int indexedCellSize = -1;

    private RadioactiveSourceRegistry() {
    }

    public static RadioactiveSourceRegistry get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }

    private static RadioactiveSourceRegistry load(CompoundTag tag, HolderLookup.Provider registries) {
        RadioactiveSourceRegistry registry = new RadioactiveSourceRegistry();
        for (long packedPos : tag.getLongArray(TAG_POSITIONS)) {
            registry.addLoaded(BlockPos.of(packedPos));
        }
        return registry;
    }

    public static void register(ServerLevel level, BlockPos pos) {
        get(level).registerSource(level, pos);
    }

    public static void unregister(ServerLevel level, BlockPos pos) {
        get(level).unregister(pos);
    }

    private void registerSource(ServerLevel level, BlockPos pos) {
        BlockPos immutablePos = pos.immutable();
        boolean added = positions.add(immutablePos);
        if (added) {
            positionsByChunk.computeIfAbsent(chunkKey(immutablePos), ignored -> new HashSet<>()).add(immutablePos);
            setDirty();
        }

        if (SkyentRadiationConfig.radiationSpatialIndexEnabled()) {
            removeFromSpatialIndex(immutablePos);
            addToSpatialIndex(level, immutablePos, SkyentRadiationConfig.radiationSpatialIndexCellSize());
        } else if (added) {
            spatialIndexDirty = true;
        }
    }

    public void register(BlockPos pos) {
        BlockPos immutablePos = pos.immutable();
        if (!positions.add(immutablePos)) {
            return;
        }

        positionsByChunk.computeIfAbsent(chunkKey(immutablePos), ignored -> new HashSet<>()).add(immutablePos);
        spatialIndexDirty = true;
        setDirty();
    }

    public void unregister(BlockPos pos) {
        BlockPos immutablePos = pos.immutable();
        if (!positions.remove(immutablePos)) {
            return;
        }

        removeFromSpatialIndex(immutablePos);
        long chunkKey = chunkKey(immutablePos);
        Set<BlockPos> chunkPositions = positionsByChunk.get(chunkKey);
        if (chunkPositions == null) {
            return;
        }

        chunkPositions.remove(immutablePos);
        if (chunkPositions.isEmpty()) {
            positionsByChunk.remove(chunkKey);
        }
        setDirty();
    }

    public List<BlockPos> getSourcesNear(Vec3 center, double radius) {
        List<BlockPos> nearbySources = new ArrayList<>();
        scanSourcesNear(center, radius, nearbySources::add);
        return nearbySources;
    }

    public NearbySourceScanStats scanSourcesNear(Vec3 center, double radius, Consumer<BlockPos> sourceConsumer) {
        int centerChunkX = Mth.floor(center.x) >> 4;
        int centerChunkZ = Mth.floor(center.z) >> 4;
        int chunkRadius = Mth.ceil(radius / 16.0D);
        double radiusSq = radius * radius;
        int bucketsVisited = 0;
        int bucketsWithSources = 0;
        int sourceRefsVisited = 0;
        int sourcesWithinRadius = 0;

        for (int chunkX = centerChunkX - chunkRadius; chunkX <= centerChunkX + chunkRadius; chunkX++) {
            for (int chunkZ = centerChunkZ - chunkRadius; chunkZ <= centerChunkZ + chunkRadius; chunkZ++) {
                bucketsVisited++;
                Set<BlockPos> chunkPositions = positionsByChunk.get(chunkKey(chunkX, chunkZ));
                if (chunkPositions == null) {
                    continue;
                }

                bucketsWithSources++;
                sourceRefsVisited += chunkPositions.size();
                for (BlockPos pos : chunkPositions) {
                    if (Vec3.atCenterOf(pos).distanceToSqr(center) <= radiusSq) {
                        sourcesWithinRadius++;
                        sourceConsumer.accept(pos);
                    }
                }
            }
        }

        return new NearbySourceScanStats(bucketsVisited, bucketsWithSources, sourceRefsVisited, sourcesWithinRadius);
    }

    public NearbySourceScanStats scanExposureSourcesNear(ServerLevel level, Vec3 center, double radius, ExposureSourceConsumer sourceConsumer) {
        if (!SkyentRadiationConfig.radiationSpatialIndexEnabled()) {
            return scanSourcesNear(center, radius, pos -> emitIndividualSource(level, center, radius, pos, sourceConsumer));
        }

        int cellSize = SkyentRadiationConfig.radiationSpatialIndexCellSize();
        ensureSpatialIndex(level, cellSize);

        int minChunkX = Mth.floor((center.x - radius) / 16.0D);
        int maxChunkX = Mth.floor((center.x + radius) / 16.0D);
        int minChunkZ = Mth.floor((center.z - radius) / 16.0D);
        int maxChunkZ = Mth.floor((center.z + radius) / 16.0D);
        double radiusSq = radius * radius;
        int chunkBucketsVisited = 0;
        int chunkBucketsWithSources = 0;
        int individualRefsVisited = 0;
        int individualSourcesWithinRadius = 0;
        int aggregateRefsVisited = 0;
        int aggregateSourcesWithinRadius = 0;
        int cellsVisited = 0;
        int cellsSkippedByAabb = 0;
        int cellsWithSources = 0;
        int clusteredBlockSourcesRepresented = 0;

        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                chunkBucketsVisited++;
                ChunkRadiationBucket chunk = spatialChunks.get(chunkKey(chunkX, chunkZ));
                if (chunk == null) {
                    continue;
                }

                chunkBucketsWithSources++;
                for (RadiationCellBucket cell : chunk.cells.values()) {
                    if (distanceSqFromPointToAabb(center, cell.minX(), cell.minY(), cell.minZ(), cell.maxX(), cell.maxY(), cell.maxZ()) > radiusSq) {
                        cellsSkippedByAabb++;
                        continue;
                    }

                    cellsVisited++;
                    cellsWithSources++;
                    AggregateRadiationSource aggregate = cell.aggregate;
                    if (aggregate != null && aggregate.count > 0) {
                        aggregateRefsVisited++;
                        Vec3 aggregateCenter = aggregate.center();
                        double distanceSqr = aggregateCenter.distanceToSqr(center);
                        if (distanceSqr <= radiusSq) {
                            aggregateSourcesWithinRadius++;
                            clusteredBlockSourcesRepresented += aggregate.count;
                            sourceConsumer.accept(new ExposureSourceRef(null, aggregateCenter, aggregate.strength(), aggregate.range(), true, aggregate.count));
                        }
                    }

                    for (SourceIndexEntry entry : cell.individualSources.values()) {
                        individualRefsVisited++;
                        Vec3 sourceCenter = Vec3.atCenterOf(entry.pos());
                        if (sourceCenter.distanceToSqr(center) <= radiusSq) {
                            individualSourcesWithinRadius++;
                            sourceConsumer.accept(new ExposureSourceRef(entry.pos(), sourceCenter, entry.strength(), entry.range(), false, 1));
                        }
                    }
                }
            }
        }

        return new NearbySourceScanStats(
                chunkBucketsVisited,
                chunkBucketsWithSources,
                individualRefsVisited + aggregateRefsVisited,
                individualSourcesWithinRadius + aggregateSourcesWithinRadius,
                cellsVisited,
                cellsSkippedByAabb,
                cellsWithSources,
                individualRefsVisited,
                aggregateRefsVisited,
                aggregateSourcesWithinRadius,
                individualSourcesWithinRadius,
                clusteredBlockSourcesRepresented,
                SkyentRadiationConfig.radiationSpatialIndexEnabled(),
                cellSize
        );
    }

    public NearbySourceScanStats sampleSourcesNear(
            Vec3 center,
            double radius,
            int cursor,
            int maxSourceRefs,
            Consumer<BlockPos> sourceConsumer
    ) {
        int centerChunkX = Mth.floor(center.x) >> 4;
        int centerChunkZ = Mth.floor(center.z) >> 4;
        int chunkRadius = Mth.ceil(radius / 16.0D);
        int side = chunkRadius * 2 + 1;
        int totalBuckets = side * side;
        int start = Math.floorMod(cursor, Math.max(1, totalBuckets));
        double radiusSq = radius * radius;
        int bucketsVisited = 0;
        int bucketsWithSources = 0;
        int sourceRefsVisited = 0;
        int sourcesWithinRadius = 0;

        for (int offsetIndex = 0; offsetIndex < totalBuckets && sourceRefsVisited < maxSourceRefs; offsetIndex++) {
            int index = (start + offsetIndex) % totalBuckets;
            int offsetX = index % side - chunkRadius;
            int offsetZ = index / side - chunkRadius;
            int chunkX = centerChunkX + offsetX;
            int chunkZ = centerChunkZ + offsetZ;
            bucketsVisited++;

            Set<BlockPos> chunkPositions = positionsByChunk.get(chunkKey(chunkX, chunkZ));
            if (chunkPositions == null) {
                continue;
            }

            bucketsWithSources++;
            for (BlockPos pos : chunkPositions) {
                if (sourceRefsVisited >= maxSourceRefs) {
                    break;
                }

                sourceRefsVisited++;
                if (Vec3.atCenterOf(pos).distanceToSqr(center) <= radiusSq) {
                    sourcesWithinRadius++;
                    sourceConsumer.accept(pos);
                }
            }
        }

        return new NearbySourceScanStats(bucketsVisited, bucketsWithSources, sourceRefsVisited, sourcesWithinRadius);
    }

    public List<BlockPos> copyAllSources() {
        return new ArrayList<>(positions);
    }

    public int size() {
        return positions.size();
    }

    private void ensureSpatialIndex(ServerLevel level, int cellSize) {
        if (!spatialIndexDirty && indexedCellSize == cellSize) {
            return;
        }

        long startNs = System.nanoTime();
        spatialChunks.clear();
        sourceIndexEntries.clear();
        indexedCellSize = cellSize;
        int removedStale = 0;
        for (BlockPos pos : new ArrayList<>(positions)) {
            if (!addToSpatialIndex(level, pos, cellSize)) {
                unregister(pos);
                removedStale++;
            }
        }
        spatialIndexDirty = false;
        if (SkyentRadiationConfig.debugRadiationSpatialIndex()) {
            SkyesNuclearTech.LOGGER.info(
                    "Radiation spatial index rebuilt: sources={} chunks={} entries={} cellSize={} removedStale={} elapsedMs={}",
                    positions.size(),
                    spatialChunks.size(),
                    sourceIndexEntries.size(),
                    cellSize,
                    removedStale,
                    (System.nanoTime() - startNs) / 1_000_000.0D
            );
        }
    }

    private boolean addToSpatialIndex(ServerLevel level, BlockPos pos, int cellSize) {
        if (!level.hasChunkAt(pos)) {
            spatialIndexDirty = true;
            return true;
        }

        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof RadioactiveSource source)) {
            return false;
        }

        double strength = source.getRadiationStrength();
        int range = source.getEntityRadiationRange();
        boolean individual = shouldIndexIndividually(state, strength);
        long sourceKey = pos.asLong();
        long chunkKey = chunkKey(pos);
        long cellKey = cellKey(pos, cellSize);
        ChunkRadiationBucket chunk = spatialChunks.computeIfAbsent(chunkKey, ignored -> new ChunkRadiationBucket());
        RadiationCellBucket cell = chunk.cells.computeIfAbsent(cellKey, ignored -> new RadiationCellBucket(cellOriginX(pos, cellSize), cellOriginY(pos, cellSize), cellOriginZ(pos, cellSize), cellSize));
        SourceIndexEntry entry = new SourceIndexEntry(pos.immutable(), chunkKey, cellKey, strength, range, individual);
        sourceIndexEntries.put(sourceKey, entry);
        if (individual) {
            cell.individualSources.put(sourceKey, entry);
        } else {
            if (cell.aggregate == null) {
                cell.aggregate = new AggregateRadiationSource();
            }
            cell.aggregate.add(pos, strength, range);
        }
        return true;
    }

    private void removeFromSpatialIndex(BlockPos pos) {
        SourceIndexEntry entry = sourceIndexEntries.remove(pos.asLong());
        if (entry == null) {
            return;
        }

        ChunkRadiationBucket chunk = spatialChunks.get(entry.chunkKey());
        if (chunk == null) {
            return;
        }

        RadiationCellBucket cell = chunk.cells.get(entry.cellKey());
        if (cell == null) {
            return;
        }

        if (entry.individual()) {
            cell.individualSources.remove(entry.pos().asLong());
        } else if (cell.aggregate != null) {
            cell.aggregate.remove(entry.pos(), entry.strength());
            if (cell.aggregate.count <= 0) {
                cell.aggregate = null;
            }
        }

        if (cell.isEmpty()) {
            chunk.cells.remove(entry.cellKey());
        }
        if (chunk.cells.isEmpty()) {
            spatialChunks.remove(entry.chunkKey());
        }
    }

    private static boolean shouldIndexIndividually(BlockState state, double strength) {
        if (!SkyentRadiationConfig.exposureClusteringEnabled() || !SkyentRadiationConfig.exposureClusterStaticWeakSources()) {
            return true;
        }
        if (state.hasBlockEntity()) {
            return true;
        }
        if (strength >= SkyentRadiationConfig.exposureAlwaysIndividualStrength()) {
            return true;
        }
        return strength > SkyentRadiationConfig.exposureClusterMaxIndividualStrength();
    }

    private static void emitIndividualSource(ServerLevel level, Vec3 center, double radius, BlockPos pos, ExposureSourceConsumer sourceConsumer) {
        if (!level.hasChunkAt(pos)) {
            return;
        }

        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof RadioactiveSource source)) {
            RadioactiveSourceRegistry.unregister(level, pos);
            return;
        }

        Vec3 sourceCenter = Vec3.atCenterOf(pos);
        if (sourceCenter.distanceToSqr(center) <= radius * radius) {
            sourceConsumer.accept(new ExposureSourceRef(pos, sourceCenter, source.getRadiationStrength(), source.getEntityRadiationRange(), false, 1));
        }
    }

    private static long cellKey(BlockPos pos, int cellSize) {
        int localCellX = Math.floorMod(pos.getX(), 16) / cellSize;
        int localCellZ = Math.floorMod(pos.getZ(), 16) / cellSize;
        int cellY = Math.floorDiv(pos.getY(), cellSize);
        return ((long) localCellX & 0xFL)
                | (((long) localCellZ & 0xFL) << 4)
                | (((long) cellY & 0xFFFFFFFFL) << 8);
    }

    private static int cellOriginX(BlockPos pos, int cellSize) {
        int chunkMinX = (pos.getX() >> 4) << 4;
        return chunkMinX + Math.floorMod(pos.getX(), 16) / cellSize * cellSize;
    }

    private static int cellOriginY(BlockPos pos, int cellSize) {
        return Math.floorDiv(pos.getY(), cellSize) * cellSize;
    }

    private static int cellOriginZ(BlockPos pos, int cellSize) {
        int chunkMinZ = (pos.getZ() >> 4) << 4;
        return chunkMinZ + Math.floorMod(pos.getZ(), 16) / cellSize * cellSize;
    }

    private static double distanceSqFromPointToAabb(Vec3 point, double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        double dx = point.x < minX ? minX - point.x : point.x > maxX ? point.x - maxX : 0.0D;
        double dy = point.y < minY ? minY - point.y : point.y > maxY ? point.y - maxY : 0.0D;
        double dz = point.z < minZ ? minZ - point.z : point.z > maxZ ? point.z - maxZ : 0.0D;
        return dx * dx + dy * dy + dz * dz;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        long[] packedPositions = new long[positions.size()];
        int index = 0;
        for (BlockPos pos : positions) {
            packedPositions[index++] = pos.asLong();
        }

        tag.putLongArray(TAG_POSITIONS, packedPositions);
        return tag;
    }

    private void addLoaded(BlockPos pos) {
        BlockPos immutablePos = pos.immutable();
        if (positions.add(immutablePos)) {
            positionsByChunk.computeIfAbsent(chunkKey(immutablePos), ignored -> new HashSet<>()).add(immutablePos);
            spatialIndexDirty = true;
        }
    }

    private static long chunkKey(BlockPos pos) {
        return chunkKey(pos.getX() >> 4, pos.getZ() >> 4);
    }

    private static long chunkKey(int chunkX, int chunkZ) {
        return ((long) chunkX & 0xFFFFFFFFL) | (((long) chunkZ & 0xFFFFFFFFL) << 32);
    }

    public record NearbySourceScanStats(
            int chunkBucketsVisited,
            int chunkBucketsWithSources,
            int sourceRefsVisited,
            int sourcesWithinRadius,
            int cellsVisited,
            int cellsSkippedByAabb,
            int cellsWithSources,
            int individualSourceRefsVisited,
            int aggregateSourceRefsVisited,
            int aggregateSourcesWithinRadius,
            int individualSourcesWithinRadius,
            int clusteredBlockSourcesRepresented,
            boolean spatialIndexEnabled,
            int spatialIndexCellSize
    ) {
        public NearbySourceScanStats(int chunkBucketsVisited, int chunkBucketsWithSources, int sourceRefsVisited, int sourcesWithinRadius) {
            this(
                    chunkBucketsVisited,
                    chunkBucketsWithSources,
                    sourceRefsVisited,
                    sourcesWithinRadius,
                    0,
                    0,
                    0,
                    sourceRefsVisited,
                    0,
                    0,
                    sourcesWithinRadius,
                    0,
                    false,
                    0
            );
        }
    }

    @FunctionalInterface
    public interface ExposureSourceConsumer {
        void accept(ExposureSourceRef source);
    }

    public record ExposureSourceRef(
            BlockPos pos,
            Vec3 center,
            double strength,
            int range,
            boolean aggregate,
            int representedSources
    ) {
    }

    private static final class ChunkRadiationBucket {
        private final Map<Long, RadiationCellBucket> cells = new HashMap<>();
    }

    private static final class RadiationCellBucket {
        private final int originX;
        private final int originY;
        private final int originZ;
        private final int size;
        private final Map<Long, SourceIndexEntry> individualSources = new HashMap<>();
        private AggregateRadiationSource aggregate;

        private RadiationCellBucket(int originX, int originY, int originZ, int size) {
            this.originX = originX;
            this.originY = originY;
            this.originZ = originZ;
            this.size = size;
        }

        private double minX() {
            return originX;
        }

        private double minY() {
            return originY;
        }

        private double minZ() {
            return originZ;
        }

        private double maxX() {
            return originX + size;
        }

        private double maxY() {
            return originY + size;
        }

        private double maxZ() {
            return originZ + size;
        }

        private boolean isEmpty() {
            return individualSources.isEmpty() && (aggregate == null || aggregate.count <= 0);
        }
    }

    private static final class AggregateRadiationSource {
        private int count;
        private double sumStrength;
        private double weightedX;
        private double weightedY;
        private double weightedZ;
        private int maxRange;

        private void add(BlockPos pos, double strength, int range) {
            count++;
            sumStrength += strength;
            Vec3 center = Vec3.atCenterOf(pos);
            weightedX += center.x * strength;
            weightedY += center.y * strength;
            weightedZ += center.z * strength;
            maxRange = Math.max(maxRange, range);
        }

        private void remove(BlockPos pos, double strength) {
            count--;
            sumStrength -= strength;
            Vec3 center = Vec3.atCenterOf(pos);
            weightedX -= center.x * strength;
            weightedY -= center.y * strength;
            weightedZ -= center.z * strength;
            if (count <= 0 || sumStrength <= 0.0D) {
                count = 0;
                sumStrength = 0.0D;
                weightedX = 0.0D;
                weightedY = 0.0D;
                weightedZ = 0.0D;
                maxRange = 0;
            }
        }

        private Vec3 center() {
            if (sumStrength <= 0.0D) {
                return Vec3.ZERO;
            }
            return new Vec3(weightedX / sumStrength, weightedY / sumStrength, weightedZ / sumStrength);
        }

        private double strength() {
            return Math.min(sumStrength, SkyentRadiationConfig.exposureAggregateMaxStrength());
        }

        private int range() {
            return maxRange;
        }
    }

    private record SourceIndexEntry(
            BlockPos pos,
            long chunkKey,
            long cellKey,
            double strength,
            int range,
            boolean individual
    ) {
    }
}
