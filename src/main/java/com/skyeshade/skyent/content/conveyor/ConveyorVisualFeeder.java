package com.skyeshade.skyent.content.conveyor;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

public interface ConveyorVisualFeeder {
    boolean skyent$feedsConveyorToward(BlockState state, Direction direction);
}
