package com.skyeshade.skyent.content.conveyor;

import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

public final class ConveyorDirectTransfer {
    private ConveyorDirectTransfer() {
    }

    public static Optional<ItemStack> tryInsert(Level level, BlockPos pos, ItemStack stack, Direction fromDirection, boolean simulate) {
        if (stack.isEmpty()) {
            return Optional.of(ItemStack.EMPTY);
        }

        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof ConveyorItemAcceptor acceptor)) {
            return Optional.empty();
        }

        return Optional.of(acceptor.insertConveyorItem(level, pos, state, stack, fromDirection, simulate));
    }
}
