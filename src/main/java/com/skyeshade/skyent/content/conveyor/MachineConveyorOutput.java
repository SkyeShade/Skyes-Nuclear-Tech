package com.skyeshade.skyent.content.conveyor;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;

public final class MachineConveyorOutput {
    private MachineConveyorOutput() {
    }

    public static ItemStack tryInsert(Level level, BlockPos targetPos, Direction outputDirection, ItemStack stack, boolean simulate) {
        if (stack.isEmpty()) {
            return ItemStack.EMPTY;
        }

        Direction fromDirection = outputDirection.getOpposite();
        BlockState targetState = level.getBlockState(targetPos);
        IItemHandler handler = level.getCapability(Capabilities.ItemHandler.BLOCK, targetPos, fromDirection);
        if (isConveyorOutputTarget(targetState)) {
            ItemStack single = stack.copyWithCount(1);
            ItemStack singleRemainder = ConveyorOutputHelper.insertIntoOutput(level, targetPos, fromDirection, handler, single, simulate);
            return remainderAfterSingleTransfer(stack, singleRemainder);
        }

        return handler == null ? stack : ConveyorInsertionUtil.insertIntoHandler(handler, stack, simulate);
    }

    private static boolean isConveyorOutputTarget(BlockState state) {
        return state.getBlock() instanceof ConveyorItemAcceptor
                || state.getBlock() instanceof ConveyorBeltSurface;
    }

    private static ItemStack remainderAfterSingleTransfer(ItemStack original, ItemStack singleRemainder) {
        if (!singleRemainder.isEmpty()) {
            return original;
        }
        return original.getCount() <= 1 ? ItemStack.EMPTY : original.copyWithCount(original.getCount() - 1);
    }
}
