package com.skyeshade.skyent.client.render;

import com.skyeshade.skyent.content.block.MVAssemblerBlock;
import com.skyeshade.skyent.content.blockentity.MVAssemblerBlockEntity;
import com.skyeshade.skyent.registry.ModBlocks;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.block.state.BlockState;

public final class MVAssemblerLightRefreshTracker {
    private static final Set<BlockPos> CONTROLLERS = new HashSet<>();

    private MVAssemblerLightRefreshTracker() {
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
            if (level.getBlockEntity(controllerPos) instanceof MVAssemblerBlockEntity assembler) {
                assembler.refreshSharedLight(false);
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
        if (!state.is(ModBlocks.MV_ASSEMBLER.get())) {
            return false;
        }

        Direction facing = state.hasProperty(MVAssemblerBlock.FACING) ? state.getValue(MVAssemblerBlock.FACING) : Direction.NORTH;
        for (int y = 0; y < MVAssemblerBlock.SIZE_Y; y++) {
            for (int x = 0; x < MVAssemblerBlock.SIZE_X; x++) {
                for (int z = 0; z < MVAssemblerBlock.SIZE_Z; z++) {
                    BlockPos cellPos = MVAssemblerBlock.localToWorld(controllerPos, facing, x, y, z);
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
