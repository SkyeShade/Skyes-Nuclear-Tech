package com.skyeshade.skyent.content.explosion.destruction;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class NuclearDestructionMask {
    private final Map<SectionKey, BitSet> sectionMasks = new HashMap<>();
    private long estimatedBlockCount;

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
        estimatedBlockCount++;
        return true;
    }

    public boolean isEmpty() {
        return sectionMasks.isEmpty();
    }

    public int sectionCount() {
        return sectionMasks.size();
    }

    public long estimatedBlockCount() {
        return estimatedBlockCount;
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

    public List<SectionKey> sectionKeys() {
        return new ArrayList<>(sectionMasks.keySet());
    }

    public void clear() {
        sectionMasks.clear();
        estimatedBlockCount = 0;
    }

    public List<SectionKey> sectionKeysSortedByDistance(Vec3 center) {
        double centerSectionX = center.x / 16.0D;
        double centerSectionY = center.y / 16.0D;
        double centerSectionZ = center.z / 16.0D;
        List<SectionKey> keys = new ArrayList<>(sectionMasks.keySet());
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
