package com.skyeshade.skyent.content.blockentity;

import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

final class CombustionGeneratorAutomationItemHandler implements IItemHandler {
    private final CombustionGeneratorBlockEntity generator;
    @Nullable
    private final Direction side;

    CombustionGeneratorAutomationItemHandler(CombustionGeneratorBlockEntity generator, @Nullable Direction side) {
        this.generator = generator;
        this.side = side;
    }

    @Override
    public int getSlots() {
        return CombustionGeneratorBlockEntity.STEAM_CONTAINER_OUTPUT_SLOT + 1;
    }

    @Override
    public ItemStack getStackInSlot(int slot) {
        return inventory().getStackInSlot(slot);
    }

    @Override
    public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
        if (stack.isEmpty()) {
            return ItemStack.EMPTY;
        }

        if (CombustionGeneratorBlockEntity.isWaterContainer(stack)) {
            return inventory().insertItem(CombustionGeneratorBlockEntity.WATER_INPUT_SLOT, stack, simulate);
        }
        if (CombustionGeneratorBlockEntity.isFillableFluidContainer(stack)) {
            return inventory().insertItem(CombustionGeneratorBlockEntity.STEAM_CONTAINER_INPUT_SLOT, stack, simulate);
        }
        if (CombustionGeneratorBlockEntity.isFuel(stack)) {
            return inventory().insertItem(CombustionGeneratorBlockEntity.FUEL_SLOT, stack, simulate);
        }

        return stack;
    }

    @Override
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        if (side != null && side != Direction.DOWN) {
            return ItemStack.EMPTY;
        }
        if (slot != CombustionGeneratorBlockEntity.EMPTY_CONTAINER_SLOT
                && slot != CombustionGeneratorBlockEntity.STEAM_CONTAINER_OUTPUT_SLOT) {
            return ItemStack.EMPTY;
        }

        return inventory().extractItem(slot, amount, simulate);
    }

    @Override
    public int getSlotLimit(int slot) {
        return inventory().getSlotLimit(slot);
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        return inventory().isItemValid(slot, stack);
    }

    private ItemStackHandler inventory() {
        return generator.getInventory();
    }
}
