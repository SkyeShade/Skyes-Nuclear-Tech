package com.skyeshade.skyent.content.conveyor;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;

public final class ConveyorInsertionUtil {
    private ConveyorInsertionUtil() {
    }

    public static ItemStack insertIntoHandler(IItemHandler handler, ItemStack stack, boolean simulate) {
        ItemStack remainder = stack.copy();
        for (int slot = 0; slot < handler.getSlots() && !remainder.isEmpty(); slot++) {
            remainder = handler.insertItem(slot, remainder, simulate);
        }
        return remainder;
    }

    public static boolean canInsertFullStack(IItemHandler handler, ItemStack stack) {
        return insertIntoHandler(handler, stack, true).isEmpty();
    }
}
