package com.skyeshade.skyent.content.explosion.destruction;

import net.minecraft.core.SectionPos;

import java.util.HashSet;
import java.util.Set;

public final class NuclearSectionCompletionTracker {
    private final Set<Long> pendingExplosionSections = new HashSet<>();
    private final Set<Long> completedExplosionSections = new HashSet<>();
    private final Set<Long> skippedExplosionSections = new HashSet<>();
    private boolean explosionMutationComplete;

    public void initializeFromDestructionMask(NuclearDestructionMask mask) {
        clear();
        for (NuclearDestructionMask.SectionKey sectionKey : mask.sectionKeys()) {
            pendingExplosionSections.add(asLong(sectionKey));
        }
        explosionMutationComplete = pendingExplosionSections.isEmpty();
    }

    public void markSectionComplete(NuclearDestructionMask.SectionKey sectionKey) {
        long key = asLong(sectionKey);
        pendingExplosionSections.remove(key);
        completedExplosionSections.add(key);
        updateComplete();
    }

    public void markSectionSkipped(NuclearDestructionMask.SectionKey sectionKey) {
        long key = asLong(sectionKey);
        pendingExplosionSections.remove(key);
        skippedExplosionSections.add(key);
        updateComplete();
    }

    public boolean isSectionComplete(int sectionX, int sectionY, int sectionZ) {
        long key = SectionPos.asLong(sectionX, sectionY, sectionZ);
        return explosionMutationComplete || completedExplosionSections.contains(key) || skippedExplosionSections.contains(key) || !pendingExplosionSections.contains(key);
    }

    public boolean isSectionPending(int sectionX, int sectionY, int sectionZ) {
        return !explosionMutationComplete && pendingExplosionSections.contains(SectionPos.asLong(sectionX, sectionY, sectionZ));
    }

    public boolean isExplosionMutationComplete() {
        return explosionMutationComplete;
    }

    public int pendingCount() {
        return pendingExplosionSections.size();
    }

    public int completedCount() {
        return completedExplosionSections.size();
    }

    public int skippedCount() {
        return skippedExplosionSections.size();
    }

    public void clear() {
        pendingExplosionSections.clear();
        completedExplosionSections.clear();
        skippedExplosionSections.clear();
        explosionMutationComplete = true;
    }

    private void updateComplete() {
        explosionMutationComplete = pendingExplosionSections.isEmpty();
    }

    private static long asLong(NuclearDestructionMask.SectionKey key) {
        return SectionPos.asLong(key.sectionX(), key.sectionY(), key.sectionZ());
    }
}
