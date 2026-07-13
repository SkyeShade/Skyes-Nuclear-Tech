package com.skyeshade.skyent.client.render;

import com.skyeshade.skyent.content.block.RollingMillBlock;
import com.skyeshade.skyent.content.blockentity.RollingMillBlockEntity;
import com.skyeshade.skyent.registry.ModBlocks;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.block.state.BlockState;

public final class RollingMillLightRefreshTracker {
    private static final Set<BlockPos> CONTROLLERS = new HashSet<>();

    private RollingMillLightRefreshTracker() {
    }

    public static void register(BlockPos pos) {
        CONTROLLERS.add(pos.immutable());
    }

    public static void unregister(BlockPos pos) {
        CONTROLLERS.remove(pos);
    }

    public static void refreshForDirtySection(ClientLevel level, int sectionX, int sectionY, int sectionZ) {
        if (CONTROLLERS.isEmpty()) {
            return;
        }

        for (BlockPos controllerPos : Set.copyOf(CONTROLLERS)) {
            if (!shouldCheckController(level, controllerPos, sectionX, sectionY, sectionZ)) {
                continue;
            }
            if (level.getBlockEntity(controllerPos) instanceof RollingMillBlockEntity rollingMill) {
                rollingMill.refreshSharedLight(false);
            } else {
                unregister(controllerPos);
            }
        }
    }

    private static boolean shouldCheckController(ClientLevel level, BlockPos controllerPos, int sectionX, int sectionY, int sectionZ) {
        if (Math.abs(SectionPos.blockToSectionCoord(controllerPos.getX()) - sectionX) > 1
                || Math.abs(SectionPos.blockToSectionCoord(controllerPos.getY()) - sectionY) > 1
                || Math.abs(SectionPos.blockToSectionCoord(controllerPos.getZ()) - sectionZ) > 1) {
            return false;
        }

        BlockState state = level.getBlockState(controllerPos);
        if (!state.is(ModBlocks.ROLLING_MILL.get())) {
            return false;
        }

        Direction facing = state.hasProperty(RollingMillBlock.FACING) ? state.getValue(RollingMillBlock.FACING) : Direction.NORTH;
        for (int y = 0; y < RollingMillBlock.SIZE_Y; y++) {
            for (int x = 0; x < RollingMillBlock.SIZE_X; x++) {
                for (int z = 0; z < RollingMillBlock.SIZE_Z; z++) {
                    BlockPos cellPos = RollingMillBlock.localToWorld(controllerPos, facing, x, y, z);
                    if (Math.abs(SectionPos.blockToSectionCoord(cellPos.getX()) - sectionX) <= 1
                            && Math.abs(SectionPos.blockToSectionCoord(cellPos.getY()) - sectionY) <= 1
                            && Math.abs(SectionPos.blockToSectionCoord(cellPos.getZ()) - sectionZ) <= 1) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}
