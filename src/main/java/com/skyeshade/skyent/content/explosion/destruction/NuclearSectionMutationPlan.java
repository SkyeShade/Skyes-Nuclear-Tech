package com.skyeshade.skyent.content.explosion.destruction;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class NuclearSectionMutationPlan {
    private final Map<NuclearDestructionMask.SectionKey, Map<Integer, BlockState>> sectionMutations = new HashMap<>();
    private long mutationCount;

    public void planSet(BlockPos pos, BlockState state) {
        planSet(pos.getX(), pos.getY(), pos.getZ(), state);
    }

    public void planSet(int x, int y, int z, BlockState state) {
        NuclearDestructionMask.SectionKey key = new NuclearDestructionMask.SectionKey(x >> 4, y >> 4, z >> 4);
        Map<Integer, BlockState> section = sectionMutations.computeIfAbsent(key, ignored -> new HashMap<>());
        BlockState previous = section.put(NuclearDestructionMask.localBitIndex(x, y, z), state);
        if (previous == null) {
            mutationCount++;
        }
    }

    public boolean isEmpty() {
        return sectionMutations.isEmpty();
    }

    public int sectionCount() {
        return sectionMutations.size();
    }

    public long mutationCount() {
        return mutationCount;
    }

    public Map<Integer, BlockState> removeSection(NuclearDestructionMask.SectionKey key) {
        Map<Integer, BlockState> removed = sectionMutations.remove(key);
        if (removed != null) {
            mutationCount -= removed.size();
        }
        return removed;
    }

    public void clear() {
        sectionMutations.clear();
        mutationCount = 0;
    }

    public List<NuclearDestructionMask.SectionKey> sectionKeysSortedByDistance(Vec3 center) {
        double centerSectionX = center.x / 16.0D;
        double centerSectionY = center.y / 16.0D;
        double centerSectionZ = center.z / 16.0D;
        List<NuclearDestructionMask.SectionKey> keys = new ArrayList<>(sectionMutations.keySet());
        keys.sort((left, right) -> Double.compare(
                distanceSqr(left, centerSectionX, centerSectionY, centerSectionZ),
                distanceSqr(right, centerSectionX, centerSectionY, centerSectionZ)
        ));
        return keys;
    }

    private static double distanceSqr(NuclearDestructionMask.SectionKey key, double x, double y, double z) {
        double dx = key.sectionX() + 0.5D - x;
        double dy = key.sectionY() + 0.5D - y;
        double dz = key.sectionZ() + 0.5D - z;
        return dx * dx + dy * dy + dz * dz;
    }
}
