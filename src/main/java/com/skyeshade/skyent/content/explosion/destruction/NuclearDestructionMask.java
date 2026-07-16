package com.skyeshade.skyent.content.explosion.destruction;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class NuclearDestructionMask {
    private final Map<SectionKey, BitSet> sectionMasks = new HashMap<>();
    private final Map<SectionKey, Map<Integer, BlockState>> sectionReplacements = new HashMap<>();
    private long estimatedBlockCount;
    private long estimatedReplacementCount;

    public boolean mark(BlockPos pos) {
        return mark(pos.getX(), pos.getY(), pos.getZ());
    }

    public boolean mark(int x, int y, int z) {
        SectionKey key = new SectionKey(x >> 4, y >> 4, z >> 4);
        BitSet mask = sectionMasks.computeIfAbsent(key, ignored -> new BitSet(4096));
        int bitIndex = localBitIndex(x, y, z);
        if (mask.get(bitIndex)) {
            return false;
        }

        mask.set(bitIndex);
        Map<Integer, BlockState> replacements = sectionReplacements.get(key);
        if (replacements != null && replacements.remove(bitIndex) != null) {
            estimatedReplacementCount--;
            if (replacements.isEmpty()) {
                sectionReplacements.remove(key);
            }
        }
        estimatedBlockCount++;
        return true;
    }

    public boolean markReplacement(int x, int y, int z, BlockState replacementState) {
        SectionKey key = new SectionKey(x >> 4, y >> 4, z >> 4);
        int bitIndex = localBitIndex(x, y, z);
        BitSet deletionMask = sectionMasks.get(key);
        if (deletionMask != null && deletionMask.get(bitIndex)) {
            return false;
        }

        Map<Integer, BlockState> replacements = sectionReplacements.computeIfAbsent(key, ignored -> new HashMap<>());
        BlockState previous = replacements.put(bitIndex, replacementState);
        if (previous == null) {
            estimatedReplacementCount++;
            return true;
        }
        return false;
    }

    public boolean isEmpty() {
        return sectionMasks.isEmpty() && sectionReplacements.isEmpty();
    }

    public int sectionCount() {
        return sectionKeys().size();
    }

    public long estimatedBlockCount() {
        return estimatedBlockCount;
    }

    public long estimatedReplacementCount() {
        return estimatedReplacementCount;
    }

    public BitSet getMask(SectionKey key) {
        return sectionMasks.get(key);
    }

    public BitSet removeMask(SectionKey key) {
        BitSet removed = sectionMasks.remove(key);
        if (removed != null) {
            estimatedBlockCount -= removed.cardinality();
        }
        return removed;
    }

    public Map<Integer, BlockState> getReplacements(SectionKey key) {
        return sectionReplacements.get(key);
    }

    public Map<Integer, BlockState> removeReplacements(SectionKey key) {
        Map<Integer, BlockState> removed = sectionReplacements.remove(key);
        if (removed != null) {
            estimatedReplacementCount -= removed.size();
        }
        return removed;
    }

    public List<SectionKey> sectionKeys() {
        Set<SectionKey> keys = new HashSet<>(sectionMasks.keySet());
        keys.addAll(sectionReplacements.keySet());
        return new ArrayList<>(keys);
    }

    public void clear() {
        sectionMasks.clear();
        sectionReplacements.clear();
        estimatedBlockCount = 0;
        estimatedReplacementCount = 0;
    }

    public void mergeFrom(NuclearDestructionMask other) {
        List<SectionKey> keys = other.sectionKeys();
        keys.sort((left, right) -> {
            int x = Integer.compare(left.sectionX(), right.sectionX());
            if (x != 0) {
                return x;
            }
            int y = Integer.compare(left.sectionY(), right.sectionY());
            return y != 0 ? y : Integer.compare(left.sectionZ(), right.sectionZ());
        });
        for (SectionKey key : keys) {
            BitSet deletionMask = other.getMask(key);
            if (deletionMask != null) {
                for (int bitIndex = deletionMask.nextSetBit(0); bitIndex >= 0; bitIndex = deletionMask.nextSetBit(bitIndex + 1)) {
                    BlockPos pos = blockPosFromBit(key, bitIndex);
                    mark(pos);
                }
            }
            Map<Integer, BlockState> replacements = other.getReplacements(key);
            if (replacements != null) {
                List<Integer> replacementBits = new ArrayList<>(replacements.keySet());
                replacementBits.sort(Integer::compareTo);
                for (int bitIndex : replacementBits) {
                    BlockPos pos = blockPosFromBit(key, bitIndex);
                    markReplacement(pos.getX(), pos.getY(), pos.getZ(), replacements.get(bitIndex));
                }
            }
        }
    }

    public List<SectionKey> sectionKeysSortedByDistance(Vec3 center) {
        double centerSectionX = center.x / 16.0D;
        double centerSectionY = center.y / 16.0D;
        double centerSectionZ = center.z / 16.0D;
        List<SectionKey> keys = sectionKeys();
        keys.sort((left, right) -> Double.compare(
                left.distanceSqrTo(centerSectionX, centerSectionY, centerSectionZ),
                right.distanceSqrTo(centerSectionX, centerSectionY, centerSectionZ)
        ));
        return keys;
    }

    public static int localBitIndex(int x, int y, int z) {
        int localX = x & 15;
        int localY = y & 15;
        int localZ = z & 15;
        // Bit layout is Y-major, then Z, then X: yyyy zzzz xxxx.
        return (localY << 8) | (localZ << 4) | localX;
    }

    public static BlockPos blockPosFromBit(SectionKey key, int bitIndex) {
        int localX = bitIndex & 15;
        int localZ = (bitIndex >> 4) & 15;
        int localY = (bitIndex >> 8) & 15;
        return new BlockPos(
                (key.sectionX() << 4) + localX,
                (key.sectionY() << 4) + localY,
                (key.sectionZ() << 4) + localZ
        );
    }

    public record SectionKey(int sectionX, int sectionY, int sectionZ) {
        private double distanceSqrTo(double x, double y, double z) {
            double dx = sectionX + 0.5D - x;
            double dy = sectionY + 0.5D - y;
            double dz = sectionZ + 0.5D - z;
            return Mth.square(dx) + Mth.square(dy) + Mth.square(dz);
        }
    }
}
