package com.skyeshade.skyent.content.explosion.destruction;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.Iterator;
import java.util.Map;
import java.util.Queue;

public final class NuclearPlannedBlockMutationQueue {
    private final ServerLevel level;
    private final NuclearSectionMutationPlan plan;
    private final Queue<NuclearDestructionMask.SectionKey> sectionQueue;
    private NuclearDestructionMask.SectionKey currentSection;
    private Iterator<Map.Entry<Integer, BlockState>> currentIterator;
    private boolean complete;
    private long totalBlocksChanged;
    private long totalSectionsTouched;
    private long unloadedSectionSkips;
    private long blockEntitySkips;
    private long totalFireBlocksPlaced;

    public NuclearPlannedBlockMutationQueue(ServerLevel level, NuclearSectionMutationPlan plan, Vec3 center) {
        this.level = level;
        this.plan = plan;
        this.sectionQueue = new ArrayDeque<>(plan.sectionKeysSortedByDistance(center));
        this.complete = plan.isEmpty();
    }

    public MutationResult tick(int maxSections, int maxBlocks) {
        int sectionsTouched = 0;
        int blocksChanged = 0;

        while (!complete && sectionsTouched < maxSections && blocksChanged < maxBlocks) {
            if (currentIterator == null && !nextSection()) {
                complete = true;
                break;
            }

            if (!currentIterator.hasNext()) {
                finishCurrentSection();
                sectionsTouched++;
                totalSectionsTouched++;
                continue;
            }

            Map.Entry<Integer, BlockState> entry = currentIterator.next();
            BlockPos pos = NuclearDestructionMask.blockPosFromBit(currentSection, entry.getKey());
            if (!level.hasChunkAt(pos)) {
                unloadedSectionSkips++;
                continue;
            }
            if (level.getBlockEntity(pos) != null) {
                blockEntitySkips++;
                continue;
            }

            BlockState targetState = entry.getValue();
            BlockState currentState = level.getBlockState(pos);
            if (currentState.equals(targetState)) {
                continue;
            }
            if (targetState.is(Blocks.FIRE) && (!currentState.isAir() || !targetState.canSurvive(level, pos))) {
                continue;
            }

            level.setBlock(pos, targetState, NuclearBlockMutationQueue.NUKE_BLOCK_UPDATE_FLAGS);
            if (targetState.is(Blocks.FIRE)) {
                totalFireBlocksPlaced++;
            }
            blocksChanged++;
            totalBlocksChanged++;
        }

        return new MutationResult(sectionsTouched, blocksChanged, complete);
    }

    private boolean nextSection() {
        currentSection = sectionQueue.poll();
        if (currentSection == null) {
            return false;
        }

        Map<Integer, BlockState> section = plan.removeSection(currentSection);
        if (section == null || section.isEmpty()) {
            currentSection = null;
            return nextSection();
        }

        currentIterator = section.entrySet().iterator();
        return true;
    }

    private void finishCurrentSection() {
        currentSection = null;
        currentIterator = null;
        if (sectionQueue.isEmpty()) {
            complete = true;
        }
    }

    public boolean isComplete() {
        return complete;
    }

    public int sectionsRemaining() {
        return sectionQueue.size() + (currentIterator == null ? 0 : 1);
    }

    public long totalBlocksChanged() {
        return totalBlocksChanged;
    }

    public long totalSectionsTouched() {
        return totalSectionsTouched;
    }

    public long unloadedSectionSkips() {
        return unloadedSectionSkips;
    }

    public long blockEntitySkips() {
        return blockEntitySkips;
    }

    public long totalFireBlocksPlaced() {
        return totalFireBlocksPlaced;
    }

    public void clear() {
        sectionQueue.clear();
        plan.clear();
        currentSection = null;
        currentIterator = null;
        complete = true;
    }

    public record MutationResult(int sectionsTouched, int blocksChanged, boolean complete) {
    }
}
