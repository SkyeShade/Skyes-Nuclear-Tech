package com.skyeshade.skyent.content.explosion.destruction;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.Container;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;

public final class NuclearBlockMutationQueue {
    public static final int NUKE_BLOCK_UPDATE_FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_SUPPRESS_DROPS;

    private final ServerLevel level;
    private final NuclearDestructionMask mask;
    private final Queue<NuclearDestructionMask.SectionKey> sectionQueue;
    private final Vec3 center;
    private final double maxPlannedHorizontalDistance;
    private final NuclearSectionCompletionTracker sectionCompletionTracker;
    private final NuclearResistanceCache resistanceCache;
    private NuclearDestructionMask.SectionKey currentSection;
    private BitSet currentMask;
    private Map<Integer, BlockState> currentReplacements;
    private List<Integer> currentReplacementBits;
    private int currentBitIndex;
    private int currentReplacementIndex;
    private boolean complete;
    private long totalBlocksRemoved;
    private long totalBlocksReplaced;
    private long totalSectionsTouched;
    private long unloadedSectionSkips;
    private double farthestProcessedHorizontalDistance;
    private long prunedEmptySections;
    private long prunedAirSections;
    private long initiallyPrunedEmptySections;
    private long initiallyPrunedAirSections;
    private long obsidianBlocksRemoved;
    private long highResistanceBlocksRemoved;
    private long blockEntitiesRemoved;
    private long protectedBlockEntitySkips;
    private long containerBlockEntitiesCleared;

    public NuclearBlockMutationQueue(ServerLevel level, NuclearDestructionMask mask, Vec3 center, NuclearSectionCompletionTracker sectionCompletionTracker, NuclearResistanceCache resistanceCache) {
        this.level = level;
        this.mask = mask;
        this.center = center;
        this.sectionCompletionTracker = sectionCompletionTracker;
        this.resistanceCache = resistanceCache;
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
        int blocksChanged = 0;

        while (!complete && sectionsTouched < maxSections && blocksChanged < maxBlocks) {
            if (currentMask == null && currentReplacements == null && !nextSection()) {
                complete = true;
                break;
            }

            int bitIndex = currentMask == null ? -1 : currentMask.nextSetBit(currentBitIndex);
            if (bitIndex >= 0) {
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
                if (!removeBlockWithoutDrops(pos, state)) {
                    continue;
                }
                blocksChanged++;
                totalBlocksRemoved++;
                continue;
            }

            if (processNextReplacement()) {
                blocksChanged++;
                continue;
            }

            if (bitIndex < 0) {
                finishCurrentSection();
                sectionsTouched++;
                totalSectionsTouched++;
                continue;
            }
        }

        return new MutationResult(sectionsTouched, blocksChanged, complete);
    }

    private boolean nextSection() {
        while (true) {
            currentSection = sectionQueue.poll();
            if (currentSection == null) {
                return false;
            }
            currentMask = mask.removeMask(currentSection);
            currentReplacements = mask.removeReplacements(currentSection);
            if (currentReplacements != null && currentMask != null) {
                for (int bitIndex = currentMask.nextSetBit(0); bitIndex >= 0; bitIndex = currentMask.nextSetBit(bitIndex + 1)) {
                    currentReplacements.remove(bitIndex);
                }
                if (currentReplacements.isEmpty()) {
                    currentReplacements = null;
                }
            }
            currentReplacementBits = currentReplacements == null ? null : new ArrayList<>(currentReplacements.keySet());
            if (currentReplacementBits != null) {
                currentReplacementBits.sort(Integer::compareTo);
            }
            currentBitIndex = 0;
            currentReplacementIndex = 0;
            boolean emptyDeletionMask = currentMask == null || currentMask.isEmpty();
            boolean emptyReplacementMap = currentReplacements == null || currentReplacements.isEmpty();
            if (emptyDeletionMask && emptyReplacementMap) {
                prunedEmptySections++;
                sectionCompletionTracker.markSectionSkipped(currentSection);
                currentMask = null;
                currentReplacements = null;
                currentReplacementBits = null;
                currentSection = null;
                continue;
            }
            if (!sectionHasAnyPlannedChange(currentSection, currentMask, currentReplacements)) {
                prunedAirSections++;
                sectionCompletionTracker.markSectionSkipped(currentSection);
                currentMask = null;
                currentReplacements = null;
                currentReplacementBits = null;
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
        currentReplacements = null;
        currentReplacementBits = null;
        currentBitIndex = 0;
        currentReplacementIndex = 0;
        if (sectionQueue.isEmpty()) {
            complete = true;
        }
    }

    private boolean processNextReplacement() {
        if (currentReplacements == null || currentReplacementBits == null) {
            return false;
        }
        while (currentReplacementIndex < currentReplacementBits.size()) {
            int bitIndex = currentReplacementBits.get(currentReplacementIndex++);
            BlockState replacementState = currentReplacements.get(bitIndex);
            if (replacementState == null) {
                continue;
            }
            BlockPos pos = NuclearDestructionMask.blockPosFromBit(currentSection, bitIndex);
            if (!level.hasChunkAt(pos)) {
                unloadedSectionSkips++;
                continue;
            }
            BlockState currentState = level.getBlockState(pos);
            if (currentState.isAir() || currentState == replacementState || currentState.equals(replacementState)) {
                continue;
            }
            if (!replaceBlockWithoutDrops(pos, currentState, replacementState)) {
                continue;
            }
            totalBlocksReplaced++;
            return true;
        }
        return false;
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

    public long totalBlocksReplaced() {
        return totalBlocksReplaced;
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

    public long blockEntitiesRemoved() {
        return blockEntitiesRemoved;
    }

    public long protectedBlockEntitySkips() {
        return protectedBlockEntitySkips;
    }

    public long containerBlockEntitiesCleared() {
        return containerBlockEntitiesCleared;
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
        currentReplacements = null;
        currentReplacementBits = null;
        currentBitIndex = 0;
        currentReplacementIndex = 0;
        complete = true;
    }

    private double horizontalSectionDistance(NuclearDestructionMask.SectionKey key) {
        double dx = (key.sectionX() << 4) + 7.5D - center.x;
        double dz = (key.sectionZ() << 4) + 7.5D - center.z;
        return Math.sqrt(dx * dx + dz * dz);
    }

    private boolean sectionHasAnyPlannedChange(NuclearDestructionMask.SectionKey sectionKey, BitSet deletionMask, Map<Integer, BlockState> replacements) {
        if (deletionMask != null) {
            for (int bitIndex = deletionMask.nextSetBit(0); bitIndex >= 0; bitIndex = deletionMask.nextSetBit(bitIndex + 1)) {
                BlockPos pos = NuclearDestructionMask.blockPosFromBit(sectionKey, bitIndex);
                if (!level.hasChunkAt(pos)) {
                    return true;
                }
                if (!level.getBlockState(pos).isAir()) {
                    return true;
                }
            }
        }
        if (replacements == null || replacements.isEmpty()) {
            return false;
        }
        for (Map.Entry<Integer, BlockState> entry : replacements.entrySet()) {
            BlockPos pos = NuclearDestructionMask.blockPosFromBit(sectionKey, entry.getKey());
            if (!level.hasChunkAt(pos)) {
                return true;
            }
            BlockState currentState = level.getBlockState(pos);
            if (!currentState.isAir() && !currentState.equals(entry.getValue())) {
                return true;
            }
        }
        return false;
    }

    private boolean removeBlockWithoutDrops(BlockPos pos, BlockState state) {
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity != null) {
            if (resistanceCache.isProtectedBlockEntity(state, level, pos)) {
                protectedBlockEntitySkips++;
                return false;
            }
            if (blockEntity instanceof Container container) {
                container.clearContent();
                containerBlockEntitiesCleared++;
            }
            blockEntitiesRemoved++;
        }
        return level.setBlock(pos, Blocks.AIR.defaultBlockState(), NUKE_BLOCK_UPDATE_FLAGS);
    }

    private boolean replaceBlockWithoutDrops(BlockPos pos, BlockState currentState, BlockState replacementState) {
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity != null) {
            if (resistanceCache.isProtectedBlockEntity(currentState, level, pos)) {
                protectedBlockEntitySkips++;
                return false;
            }
            if (blockEntity instanceof Container container) {
                container.clearContent();
                containerBlockEntitiesCleared++;
            }
            blockEntitiesRemoved++;
        }
        return level.setBlock(pos, replacementState, NUKE_BLOCK_UPDATE_FLAGS);
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
            Map<Integer, BlockState> replacements = mask.getReplacements(sectionKey);
            boolean emptySectionMask = sectionMask == null || sectionMask.isEmpty();
            boolean emptyReplacements = replacements == null || replacements.isEmpty();
            if (emptySectionMask && emptyReplacements) {
                mask.removeMask(sectionKey);
                mask.removeReplacements(sectionKey);
                sectionCompletionTracker.markSectionSkipped(sectionKey);
                initiallyPrunedEmptySections++;
                prunedEmptySections++;
                continue;
            }
            if (!sectionHasAnyPlannedChange(sectionKey, sectionMask, replacements)) {
                mask.removeMask(sectionKey);
                mask.removeReplacements(sectionKey);
                sectionCompletionTracker.markSectionSkipped(sectionKey);
                initiallyPrunedAirSections++;
                prunedAirSections++;
            }
        }
    }

    public record MutationResult(int sectionsTouched, int blocksRemoved, boolean complete) {
    }
}
