package com.skyeshade.skyent.content.conveyor;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public interface ConveyorBeltSurface {
    boolean canItemStay(Level level, BlockPos pos, Vec3 itemPos);

    Vec3 getTravelLocation(Level level, BlockPos pos, Vec3 itemPos, double speed);

    Vec3 getClosestSnappingPosition(Level level, BlockPos pos, Vec3 itemPos);

    default double speedMultiplier(Level level, BlockPos pos) {
        return 1.0D;
    }
}
