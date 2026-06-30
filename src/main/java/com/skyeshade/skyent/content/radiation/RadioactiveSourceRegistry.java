package com.skyeshade.skyent.content.radiation;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
        get(level).register(pos);
    }

    public static void unregister(ServerLevel level, BlockPos pos) {
        get(level).unregister(pos);
    }

    public void register(BlockPos pos) {
        BlockPos immutablePos = pos.immutable();
        if (!positions.add(immutablePos)) {
            return;
        }

        positionsByChunk.computeIfAbsent(chunkKey(immutablePos), ignored -> new HashSet<>()).add(immutablePos);
        setDirty();
    }

    public void unregister(BlockPos pos) {
        BlockPos immutablePos = pos.immutable();
        if (!positions.remove(immutablePos)) {
            return;
        }

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
        int centerChunkX = Mth.floor(center.x) >> 4;
        int centerChunkZ = Mth.floor(center.z) >> 4;
        int chunkRadius = Mth.ceil(radius / 16.0D);
        double radiusSq = radius * radius;

        for (int chunkX = centerChunkX - chunkRadius; chunkX <= centerChunkX + chunkRadius; chunkX++) {
            for (int chunkZ = centerChunkZ - chunkRadius; chunkZ <= centerChunkZ + chunkRadius; chunkZ++) {
                Set<BlockPos> chunkPositions = positionsByChunk.get(chunkKey(chunkX, chunkZ));
                if (chunkPositions == null) {
                    continue;
                }

                for (BlockPos pos : chunkPositions) {
                    if (Vec3.atCenterOf(pos).distanceToSqr(center) <= radiusSq) {
                        nearbySources.add(pos);
                    }
                }
            }
        }

        return nearbySources;
    }

    public List<BlockPos> copyAllSources() {
        return new ArrayList<>(positions);
    }

    public int size() {
        return positions.size();
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
        }
    }

    private static long chunkKey(BlockPos pos) {
        return chunkKey(pos.getX() >> 4, pos.getZ() >> 4);
    }

    private static long chunkKey(int chunkX, int chunkZ) {
        return ((long) chunkX & 0xFFFFFFFFL) | (((long) chunkZ & 0xFFFFFFFFL) << 32);
    }
}
