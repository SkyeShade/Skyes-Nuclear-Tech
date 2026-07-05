package com.skyeshade.skyent.client.render;

import com.skyeshade.skyent.content.block.HeatingChamberBlock;
import com.skyeshade.skyent.content.blockentity.HeatingChamberBlockEntity;
import com.skyeshade.skyent.registry.ModBlocks;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.block.state.BlockState;

public final class HeatingChamberLightRefreshTracker {
    private static final Set<BlockPos> CONTROLLERS = new HashSet<>();

    private HeatingChamberLightRefreshTracker() {
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
            if (level.getBlockEntity(controllerPos) instanceof HeatingChamberBlockEntity chamber) {
                chamber.refreshSharedLight(false);
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
        if (!state.is(ModBlocks.HEATING_CHAMBER.get())) {
            return false;
        }

        Direction facing = state.hasProperty(HeatingChamberBlock.FACING) ? state.getValue(HeatingChamberBlock.FACING) : Direction.NORTH;
        for (int y = 0; y <= 2; y++) {
            for (int x = 0; x <= 1; x++) {
                for (int z = 0; z <= 1; z++) {
                    BlockPos cellPos = HeatingChamberBlock.localToWorld(controllerPos, facing, x, y, z);
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
