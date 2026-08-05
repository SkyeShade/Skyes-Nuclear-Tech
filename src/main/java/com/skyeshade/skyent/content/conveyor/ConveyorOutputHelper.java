package com.skyeshade.skyent.content.conveyor;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.items.IItemHandler;

public final class ConveyorOutputHelper {
    private ConveyorOutputHelper() {
    }

    public static ItemStack insertIntoOutput(
            Level level,
            BlockPos outputPos,
            Direction fromDirection,
            @Nullable IItemHandler fallbackHandler,
            ItemStack stack,
            boolean simulate
    ) {
        if (stack.isEmpty()) {
            return ItemStack.EMPTY;
        }

        var directRemainder = ConveyorDirectTransfer.tryInsert(level, outputPos, stack, fromDirection, simulate);
        if (directRemainder.isPresent()) {
            return directRemainder.get();
        }

        return fallbackHandler == null ? stack : ConveyorInsertionUtil.insertIntoHandler(fallbackHandler, stack, simulate);
    }
}
