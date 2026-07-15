package com.skyeshade.skyent.content.explosion.destruction;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.BitSet;
import java.util.Queue;

public final class NuclearBlockMutationQueue {
    public static final int NUKE_BLOCK_UPDATE_FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_SUPPRESS_DROPS;

    private final ServerLevel level;
    private final NuclearDestructionMask mask;
    private final Queue<NuclearDestructionMask.SectionKey> sectionQueue;
    private final Vec3 center;
    private final double maxPlannedHorizontalDistance;
    private final NuclearSectionCompletionTracker sectionCompletionTracker;
    private NuclearDestructionMask.SectionKey currentSection;
    private BitSet currentMask;
    private int currentBitIndex;
    private boolean complete;
    private long totalBlocksRemoved;
    private long totalSectionsTouched;
    private long unloadedSectionSkips;
    private double farthestProcessedHorizontalDistance;
    private long prunedEmptySections;
    private long prunedAirSections;
    private long initiallyPrunedEmptySections;
    private long initiallyPrunedAirSections;
    private long obsidianBlocksRemoved;
    private long highResistanceBlocksRemoved;

    public NuclearBlockMutationQueue(ServerLevel level, NuclearDestructionMask mask, Vec3 center, NuclearSectionCompletionTracker sectionCompletionTracker) {
        this.level = level;
        this.mask = mask;
        this.center = center;
        this.sectionCompletionTracker = sectionCompletionTracker;
        pruneNoOpSectionsBeforeQueueing();
        var sectionKeys = mask.sectionKeysSortedByDistance(center);
        this.sectionQueue = new ArrayDeque<>(sectionKeys);
        double maxDistance = 0.0D;
        for (NuclearDestructionMask.SectionKey key : sectionKeys) {
            maxDistance = Math.max(maxDistance, horizontalSectionDistance(key));
        }
        this.maxPlannedHorizontalDistance = maxDistance;
        this.complete = mask.isEmpty();
    }

    public MutationResult tick(int maxSections, int maxBlocks) {
        int sectionsTouched = 0;
        int blocksRemoved = 0;

        while (!complete && sectionsTouched < maxSections && blocksRemoved < maxBlocks) {
            if (currentMask == null && !nextSection()) {
                complete = true;
                break;
            }

            int bitIndex = currentMask.nextSetBit(currentBitIndex);
            if (bitIndex < 0) {
                finishCurrentSection();
                sectionsTouched++;
                totalSectionsTouched++;
                continue;
            }

            currentBitIndex = bitIndex + 1;
            BlockPos pos = NuclearDestructionMask.blockPosFromBit(currentSection, bitIndex);
            if (!level.hasChunkAt(pos)) {
                unloadedSectionSkips++;
                continue;
            }

            BlockState state = level.getBlockState(pos);
            if (state.isAir()) {
                continue;
            }

            if (state.is(Blocks.OBSIDIAN)) {
                obsidianBlocksRemoved++;
            }
            if (state.getBlock().getExplosionResistance() >= 12.0F) {
                highResistanceBlocksRemoved++;
            }
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), NUKE_BLOCK_UPDATE_FLAGS);
            blocksRemoved++;
            totalBlocksRemoved++;
        }

        return new MutationResult(sectionsTouched, blocksRemoved, complete);
    }

    private boolean nextSection() {
        while (true) {
            currentSection = sectionQueue.poll();
            if (currentSection == null) {
                return false;
            }
            currentMask = mask.removeMask(currentSection);
            currentBitIndex = 0;
            if (currentMask == null || currentMask.isEmpty()) {
                prunedEmptySections++;
                sectionCompletionTracker.markSectionSkipped(currentSection);
                currentMask = null;
                currentSection = null;
                continue;
            }
            if (!sectionHasAnyNonAirPlannedBlock(currentSection, currentMask)) {
                prunedAirSections++;
                sectionCompletionTracker.markSectionSkipped(currentSection);
                currentMask = null;
                currentSection = null;
                continue;
            }
            return true;
        }
    }

    private void finishCurrentSection() {
        if (currentSection != null) {
            farthestProcessedHorizontalDistance = Math.max(
                    farthestProcessedHorizontalDistance,
                    horizontalSectionDistance(currentSection)
            );
            sectionCompletionTracker.markSectionComplete(currentSection);
        }
        currentSection = null;
        currentMask = null;
        currentBitIndex = 0;
        if (sectionQueue.isEmpty()) {
            complete = true;
        }
    }

    public boolean isComplete() {
        return complete;
    }

    public int sectionsRemaining() {
        return sectionQueue.size() + (currentMask == null ? 0 : 1);
    }

    public long totalBlocksRemoved() {
        return totalBlocksRemoved;
    }

    public long totalSectionsTouched() {
        return totalSectionsTouched;
    }

    public long unloadedSectionSkips() {
        return unloadedSectionSkips;
    }

    public long prunedEmptySections() {
        return prunedEmptySections;
    }

    public long prunedAirSections() {
        return prunedAirSections;
    }

    public long initiallyPrunedEmptySections() {
        return initiallyPrunedEmptySections;
    }

    public long initiallyPrunedAirSections() {
        return initiallyPrunedAirSections;
    }

    public long obsidianBlocksRemoved() {
        return obsidianBlocksRemoved;
    }

    public long highResistanceBlocksRemoved() {
        return highResistanceBlocksRemoved;
    }

    public double processedRadius() {
        return farthestProcessedHorizontalDistance;
    }

    public double maxPlannedHorizontalDistance() {
        return maxPlannedHorizontalDistance;
    }

    public double processedRadiusFraction() {
        if (complete) {
            return 1.0D;
        }
        if (maxPlannedHorizontalDistance <= 0.0D) {
            return 0.0D;
        }
        return Mth.clamp(farthestProcessedHorizontalDistance / maxPlannedHorizontalDistance, 0.0D, 1.0D);
    }

    public void clear() {
        sectionQueue.clear();
        mask.clear();
        currentSection = null;
        currentMask = null;
        currentBitIndex = 0;
        complete = true;
    }

    private double horizontalSectionDistance(NuclearDestructionMask.SectionKey key) {
        double dx = (key.sectionX() << 4) + 7.5D - center.x;
        double dz = (key.sectionZ() << 4) + 7.5D - center.z;
        return Math.sqrt(dx * dx + dz * dz);
    }

    private boolean sectionHasAnyNonAirPlannedBlock(NuclearDestructionMask.SectionKey sectionKey, BitSet mask) {
        for (int bitIndex = mask.nextSetBit(0); bitIndex >= 0; bitIndex = mask.nextSetBit(bitIndex + 1)) {
            BlockPos pos = NuclearDestructionMask.blockPosFromBit(sectionKey, bitIndex);
            if (!level.hasChunkAt(pos)) {
                return true;
            }
            if (!level.getBlockState(pos).isAir()) {
                return true;
            }
        }
        return false;
    }

    private void pruneNoOpSectionsBeforeQueueing() {
        for (NuclearDestructionMask.SectionKey sectionKey : mask.sectionKeys()) {
            BitSet sectionMask = mask.getMask(sectionKey);
            if (sectionMask == null || sectionMask.isEmpty()) {
                mask.removeMask(sectionKey);
                sectionCompletionTracker.markSectionSkipped(sectionKey);
                initiallyPrunedEmptySections++;
                prunedEmptySections++;
                continue;
            }
            if (!sectionHasAnyNonAirPlannedBlock(sectionKey, sectionMask)) {
                mask.removeMask(sectionKey);
                sectionCompletionTracker.markSectionSkipped(sectionKey);
                initiallyPrunedAirSections++;
                prunedAirSections++;
            }
        }
    }

    public record MutationResult(int sectionsTouched, int blocksRemoved, boolean complete) {
    }
}
