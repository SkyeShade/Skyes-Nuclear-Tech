package com.skyeshade.skyent.content.conveyor;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

public interface ConveyorItemAcceptor {
    ItemStack insertConveyorItem(Level level, BlockPos pos, BlockState state, ItemStack stack, Direction fromDirection, boolean simulate);
}
