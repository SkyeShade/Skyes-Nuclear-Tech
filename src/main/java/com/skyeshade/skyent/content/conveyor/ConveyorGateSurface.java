package com.skyeshade.skyent.content.conveyor;

import com.skyeshade.skyent.content.entity.ConveyorMovingItemEntity;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public interface ConveyorGateSurface {
    default boolean skyent$canConveyorItemEnter(Level level, BlockPos pos, BlockState state, Direction fromDirection) {
        return true;
    }

    default boolean skyent$canConveyorItemMove(Level level, BlockPos pos, BlockState state) {
        return true;
    }

    default boolean skyent$canConveyorItemMove(Level level, BlockPos pos, BlockState state, ConveyorMovingItemEntity item) {
        return skyent$canConveyorItemMove(level, pos, state);
    }

    default boolean skyent$canConveyorItemOutput(Level level, BlockPos pos, BlockState state, Direction outputDirection) {
        return true;
    }

    default boolean skyent$canConveyorItemOutput(Level level, BlockPos pos, BlockState state, ConveyorMovingItemEntity item, Direction outputDirection) {
        return skyent$canConveyorItemOutput(level, pos, state, outputDirection);
    }

    @Nullable
    default Vec3 skyent$getConveyorHoldPosition(Level level, BlockPos pos, BlockState state, ConveyorMovingItemEntity item, Direction outputDirection) {
        return null;
    }

    default void skyent$onConveyorItemMoved(Level level, BlockPos pos, BlockState state, ConveyorMovingItemEntity item, Vec3 from, Vec3 to) {
    }
}
