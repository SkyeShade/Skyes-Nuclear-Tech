package com.skyeshade.skyent.content.fluid;

import com.skyeshade.skyent.content.item.SteelFluidBarrelItem;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandlerItem;

public class SteelFluidBarrelFluidHandler implements IFluidHandlerItem {
    private final ItemStack container;

    public SteelFluidBarrelFluidHandler(ItemStack stack) {
        this.container = stack.copyWithCount(1);
    }

    @Override
    public int getTanks() {
        return 1;
    }

    @Override
    public FluidStack getFluidInTank(int tank) {
        return tank == 0 ? SteelFluidBarrelItem.getStoredFluid(container).copy() : FluidStack.EMPTY;
    }

    @Override
    public int getTankCapacity(int tank) {
        return tank == 0 ? SteelFluidBarrelItem.CAPACITY_MB : 0;
    }

    @Override
    public boolean isFluidValid(int tank, FluidStack stack) {
        return tank == 0 && !stack.isEmpty();
    }

    @Override
    public int fill(FluidStack resource, FluidAction action) {
        if (resource.isEmpty()) {
            return 0;
        }

        FluidStack stored = SteelFluidBarrelItem.getStoredFluid(container);
        if (!stored.isEmpty() && !FluidStack.isSameFluidSameComponents(stored, resource)) {
            return 0;
        }

        int space = SteelFluidBarrelItem.CAPACITY_MB - stored.getAmount();
        int accepted = Math.min(space, resource.getAmount());
        if (accepted <= 0) {
            return 0;
        }

        if (action.execute()) {
            FluidStack updated = resource.copy();
            updated.setAmount(stored.getAmount() + accepted);
            SteelFluidBarrelItem.setStoredFluid(container, updated);
        }
        return accepted;
    }

    @Override
    public FluidStack drain(FluidStack resource, FluidAction action) {
        if (resource.isEmpty()) {
            return FluidStack.EMPTY;
        }

        FluidStack stored = SteelFluidBarrelItem.getStoredFluid(container);
        if (stored.isEmpty() || !FluidStack.isSameFluidSameComponents(stored, resource)) {
            return FluidStack.EMPTY;
        }

        return drain(resource.getAmount(), action);
    }

    @Override
    public FluidStack drain(int maxDrain, FluidAction action) {
        if (maxDrain <= 0) {
            return FluidStack.EMPTY;
        }

        FluidStack stored = SteelFluidBarrelItem.getStoredFluid(container);
        if (stored.isEmpty()) {
            return FluidStack.EMPTY;
        }

        int drainedAmount = Math.min(maxDrain, stored.getAmount());
        FluidStack drained = stored.copy();
        drained.setAmount(drainedAmount);
        if (action.execute()) {
            FluidStack remaining = stored.copy();
            remaining.shrink(drainedAmount);
            SteelFluidBarrelItem.setStoredFluid(container, remaining);
        }
        return drained;
    }

    @Override
    public ItemStack getContainer() {
        return container;
    }
}
