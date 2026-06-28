package com.skyeshade.skyent.content.blockentity;

import com.skyeshade.skyent.content.energy.ElectricalTier;
import com.skyeshade.skyent.content.energy.RJEnergyInfo;
import com.skyeshade.skyent.content.energy.RJStorage;
import com.skyeshade.skyent.content.menu.LVElectricPumpMenu;
import com.skyeshade.skyent.registry.ModBlockEntities;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.Containers;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidActionResult;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidUtil;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

public class LVElectricPumpBlockEntity extends BlockEntity implements MenuProvider, RJEnergyInfo {
    public static final int ENERGY_CAPACITY_RJ = 20_000;
    public static final int ENERGY_USAGE_RJ_PER_TICK = 16;
    public static final ElectricalTier REQUIRED_TIER = ElectricalTier.LV;
    public static final int REQUIRED_VOLTAGE = REQUIRED_TIER.voltage();
    public static final double MAX_INPUT_AMPS = 2.0D;
    public static final int TANK_CAPACITY_MB = 40_000;
    public static final int PUMP_RATE_MB_PER_TICK = 50;
    public static final int FLUID_OUTPUT_MB_PER_TICK = 100;
    public static final int WATER_SOURCE_THRESHOLD = 2;
    public static final int LAVA_SOURCE_THRESHOLD = 200;
    private static final int SOURCE_SEARCH_LIMIT = 512;
    private static final String TAG_STORED_RJ = "StoredRJ";

    public static final int DUMP_INPUT_SLOT = 0;
    public static final int DUMP_OUTPUT_SLOT = 1;
    public static final int FILL_INPUT_SLOT = 2;
    public static final int FILL_OUTPUT_SLOT = 3;
    private static final int INVENTORY_SLOT_COUNT = 4;

    private static final int DATA_ENERGY_LOW = 0;
    private static final int DATA_ENERGY_HIGH = 1;
    private static final int DATA_MAX_ENERGY_LOW = 2;
    private static final int DATA_MAX_ENERGY_HIGH = 3;
    private static final int DATA_CURRENT_ENERGY_USAGE = 4;
    private static final int DATA_FLUID_AMOUNT_LOW = 5;
    private static final int DATA_FLUID_AMOUNT_HIGH = 6;
    private static final int DATA_FLUID_CAPACITY_LOW = 7;
    private static final int DATA_FLUID_CAPACITY_HIGH = 8;
    private static final int DATA_FLUID_ID_LOW = 9;
    private static final int DATA_FLUID_ID_HIGH = 10;
    private static final int DATA_ACTIVE = 11;
    private static final int DATA_COUNT = 12;

    private int currentEnergyUsage;
    private boolean active;

    private final ItemStackHandler inventory = new ItemStackHandler(INVENTORY_SLOT_COUNT) {
        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return switch (slot) {
                case DUMP_INPUT_SLOT -> isFilledFluidContainer(stack);
                case FILL_INPUT_SLOT -> isFillableFluidContainer(stack);
                default -> false;
            };
        }

        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }
    };

    private final RJStorage rjStorage = new RJStorage(ENERGY_CAPACITY_RJ);
    private final FluidTank fluidTank = new FluidTank(TANK_CAPACITY_MB) {
        @Override
        public boolean isFluidValid(FluidStack stack) {
            return canAcceptFluid(stack);
        }

        @Override
        protected void onContentsChanged() {
            setChanged();
        }
    };
    private final IItemHandler automationItemHandler = new AutomationItemHandler();
    private final IFluidHandler automationFluidHandler = new AutomationFluidHandler();

    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            int stored = rjStorage.getStoredRJ();
            int fluidAmount = fluidTank.getFluidAmount();
            int fluidCapacity = fluidTank.getCapacity();
            int fluidId = getFluidId();

            return switch (index) {
                case DATA_ENERGY_LOW -> low(stored);
                case DATA_ENERGY_HIGH -> high(stored);
                case DATA_MAX_ENERGY_LOW -> low(rjStorage.getCapacityRJ());
                case DATA_MAX_ENERGY_HIGH -> high(rjStorage.getCapacityRJ());
                case DATA_CURRENT_ENERGY_USAGE -> currentEnergyUsage;
                case DATA_FLUID_AMOUNT_LOW -> low(fluidAmount);
                case DATA_FLUID_AMOUNT_HIGH -> high(fluidAmount);
                case DATA_FLUID_CAPACITY_LOW -> low(fluidCapacity);
                case DATA_FLUID_CAPACITY_HIGH -> high(fluidCapacity);
                case DATA_FLUID_ID_LOW -> low(fluidId);
                case DATA_FLUID_ID_HIGH -> high(fluidId);
                case DATA_ACTIVE -> active ? 1 : 0;
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {
            switch (index) {
                case DATA_ENERGY_LOW -> rjStorage.setStoredRJ(combine(value, data.get(DATA_ENERGY_HIGH)));
                case DATA_ENERGY_HIGH -> rjStorage.setStoredRJ(combine(data.get(DATA_ENERGY_LOW), value));
                case DATA_CURRENT_ENERGY_USAGE -> currentEnergyUsage = value;
                case DATA_ACTIVE -> active = value != 0;
                default -> {
                }
            }
        }

        @Override
        public int getCount() {
            return DATA_COUNT;
        }
    };

    public LVElectricPumpBlockEntity(BlockPos pos, BlockState blockState) {
        super(ModBlockEntities.LV_ELECTRIC_PUMP.get(), pos, blockState);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, LVElectricPumpBlockEntity pump) {
        int previousUsage = pump.currentEnergyUsage;
        boolean wasActive = pump.active;
        pump.currentEnergyUsage = 0;
        pump.active = false;
        boolean changed = false;

        if (pump.tryDumpContainer()) {
            changed = true;
        }
        if (pump.tryFillContainer()) {
            changed = true;
        }
        if (pump.tryPumpSource(level, pos)) {
            changed = true;
        }
        if (pump.outputFluidToNeighbors()) {
            changed = true;
        }

        if (previousUsage != pump.currentEnergyUsage || wasActive != pump.active) {
            changed = true;
        }

        if (changed) {
            setChanged(level, pos, state);
        }
    }

    public static boolean isFilledFluidContainer(ItemStack stack) {
        return FluidUtil.getFluidContained(stack).filter(fluid -> !fluid.isEmpty()).isPresent();
    }

    public static boolean isFillableFluidContainer(ItemStack stack) {
        return !stack.isEmpty() && FluidUtil.getFluidHandler(stack).isPresent() && FluidUtil.getFluidContained(stack).orElse(FluidStack.EMPTY).isEmpty();
    }

    private boolean tryDumpContainer() {
        ItemStack input = inventory.getStackInSlot(DUMP_INPUT_SLOT);
        if (input.isEmpty()) {
            return false;
        }

        FluidStack contained = FluidUtil.getFluidContained(input).orElse(FluidStack.EMPTY);
        if (contained.isEmpty() || !canAcceptFluid(contained) || contained.getAmount() > fluidTank.getSpace()) {
            return false;
        }

        FluidActionResult simulated = FluidUtil.tryEmptyContainer(input, fluidTank, contained.getAmount(), null, false);
        if (!simulated.isSuccess() || !canPlaceOutput(DUMP_OUTPUT_SLOT, simulated.getResult())) {
            return false;
        }

        FluidActionResult result = FluidUtil.tryEmptyContainer(input, fluidTank, contained.getAmount(), null, true);
        if (!result.isSuccess()) {
            return false;
        }

        input.shrink(1);
        placeOutput(DUMP_OUTPUT_SLOT, result.getResult());
        return true;
    }

    private boolean tryFillContainer() {
        ItemStack input = inventory.getStackInSlot(FILL_INPUT_SLOT);
        if (input.isEmpty() || fluidTank.getFluidAmount() <= 0) {
            return false;
        }

        FluidStack available = fluidTank.getFluid().copy();
        FluidActionResult simulated = FluidUtil.tryFillContainer(input, fluidTank, available.getAmount(), null, false);
        if (!simulated.isSuccess() || !canPlaceOutput(FILL_OUTPUT_SLOT, simulated.getResult())) {
            return false;
        }

        FluidActionResult result = FluidUtil.tryFillContainer(input, fluidTank, available.getAmount(), null, true);
        if (!result.isSuccess()) {
            return false;
        }

        input.shrink(1);
        placeOutput(FILL_OUTPUT_SLOT, result.getResult());
        return true;
    }

    private boolean tryPumpSource(Level level, BlockPos pos) {
        if (rjStorage.getStoredRJ() < ENERGY_USAGE_RJ_PER_TICK || fluidTank.getSpace() <= 0) {
            return false;
        }

        Fluid fluid = findPumpableFluid(level, pos.below());
        if (fluid == Fluids.EMPTY || !canAcceptFluid(new FluidStack(fluid, PUMP_RATE_MB_PER_TICK))) {
            return false;
        }

        int filled = fluidTank.fill(new FluidStack(fluid, PUMP_RATE_MB_PER_TICK), IFluidHandler.FluidAction.EXECUTE);
        if (filled <= 0) {
            return false;
        }

        rjStorage.consumeRJ(ENERGY_USAGE_RJ_PER_TICK);
        currentEnergyUsage = ENERGY_USAGE_RJ_PER_TICK;
        active = true;
        return true;
    }

    private Fluid findPumpableFluid(Level level, BlockPos intakePos) {
        FluidState fluidState = level.getFluidState(intakePos);
        if (!fluidState.isSource()) {
            return Fluids.EMPTY;
        }

        Fluid fluid = fluidState.getType();
        if (fluid == Fluids.WATER) {
            return countConnectedSources(level, intakePos, fluid, WATER_SOURCE_THRESHOLD) >= WATER_SOURCE_THRESHOLD ? fluid : Fluids.EMPTY;
        }
        if (fluid == Fluids.LAVA) {
            return countConnectedSources(level, intakePos, fluid, LAVA_SOURCE_THRESHOLD) >= LAVA_SOURCE_THRESHOLD ? fluid : Fluids.EMPTY;
        }

        return fluid;
    }

    private int countConnectedSources(Level level, BlockPos start, Fluid fluid, int threshold) {
        Set<BlockPos> visited = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        queue.add(start);
        int count = 0;

        while (!queue.isEmpty() && visited.size() < SOURCE_SEARCH_LIMIT && count < threshold) {
            BlockPos pos = queue.removeFirst();
            if (!visited.add(pos)) {
                continue;
            }

            FluidState state = level.getFluidState(pos);
            if (!state.isSource() || state.getType() != fluid) {
                continue;
            }

            count++;
            for (Direction direction : Direction.values()) {
                queue.add(pos.relative(direction));
            }
        }

        return count;
    }

    private boolean outputFluidToNeighbors() {
        if (level == null || fluidTank.getFluidAmount() <= 0) {
            return false;
        }

        int remaining = Math.min(FLUID_OUTPUT_MB_PER_TICK, fluidTank.getFluidAmount());
        boolean changed = false;
        for (Direction direction : Direction.values()) {
            if (remaining <= 0) {
                break;
            }

            IFluidHandler receiver = level.getCapability(
                    Capabilities.FluidHandler.BLOCK,
                    worldPosition.relative(direction),
                    direction.getOpposite()
            );
            if (receiver == null) {
                continue;
            }

            FluidStack offered = fluidTank.drain(remaining, IFluidHandler.FluidAction.SIMULATE);
            int accepted = receiver.fill(offered, IFluidHandler.FluidAction.EXECUTE);
            if (accepted > 0) {
                fluidTank.drain(accepted, IFluidHandler.FluidAction.EXECUTE);
                remaining -= accepted;
                changed = true;
            }
        }

        return changed;
    }

    private boolean canAcceptFluid(FluidStack stack) {
        return stack.isEmpty() || fluidTank.getFluid().isEmpty() || fluidTank.getFluid().is(stack.getFluid());
    }

    private boolean canPlaceOutput(int slot, ItemStack result) {
        if (result.isEmpty()) {
            return true;
        }

        ItemStack output = inventory.getStackInSlot(slot);
        return output.isEmpty() || ItemStack.isSameItemSameComponents(output, result) && output.getCount() + result.getCount() <= output.getMaxStackSize();
    }

    private void placeOutput(int slot, ItemStack result) {
        if (result.isEmpty()) {
            return;
        }

        ItemStack output = inventory.getStackInSlot(slot);
        if (output.isEmpty()) {
            inventory.setStackInSlot(slot, result.copy());
        } else {
            output.grow(result.getCount());
            inventory.setStackInSlot(slot, output);
        }
    }

    public ItemStackHandler getInventory() {
        return inventory;
    }

    public IItemHandler getAutomationItemHandler() {
        return automationItemHandler;
    }

    public IFluidHandler getAutomationFluidHandler() {
        return automationFluidHandler;
    }

    public int getAvailableRJCapacity() {
        return rjStorage.getAvailableRJCapacity();
    }

    public int receiveRJ(int maxAmount, boolean simulate) {
        int received = rjStorage.receiveRJ(maxAmount, simulate);
        if (received > 0 && !simulate) {
            setChanged();
        }

        return received;
    }

    @Override
    public int getEnergyStoredRJ() {
        return rjStorage.getStoredRJ();
    }

    @Override
    public int getEnergyCapacityRJ() {
        return ENERGY_CAPACITY_RJ;
    }

    @Override
    public int getCurrentUsageRJPerTick() {
        return currentEnergyUsage;
    }

    @Override
    public String getVoltageTierName() {
        return REQUIRED_TIER.displayName();
    }

    public ContainerData getData() {
        return data;
    }

    public FluidStack getFluidInTank() {
        return fluidTank.getFluid();
    }

    public int getFluidAmount() {
        return fluidTank.getFluidAmount();
    }

    public int getFluidCapacity() {
        return fluidTank.getCapacity();
    }

    public int getRedstoneSignal() {
        return Mth.floor((float) fluidTank.getFluidAmount() / fluidTank.getCapacity() * 15.0F);
    }

    public void dropContents(Level level, BlockPos pos) {
        for (int slot = 0; slot < inventory.getSlots(); slot++) {
            ItemStack stack = inventory.getStackInSlot(slot);
            if (!stack.isEmpty()) {
                Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), stack);
            }
        }
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.skyent.lv_electric_pump");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new LVElectricPumpMenu(containerId, playerInventory, this, data);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt(TAG_STORED_RJ, rjStorage.getStoredRJ());
        tag.put("FluidTank", fluidTank.writeToNBT(registries, new CompoundTag()));
        tag.put("Inventory", inventory.serializeNBT(registries));
        tag.putBoolean("Active", active);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        rjStorage.setStoredRJ(tag.getInt(TAG_STORED_RJ));
        fluidTank.readFromNBT(registries, tag.getCompound("FluidTank"));
        inventory.deserializeNBT(registries, tag.getCompound("Inventory"));
        active = tag.getBoolean("Active");
    }

    private int getFluidId() {
        FluidStack fluid = fluidTank.getFluid();
        return fluid.isEmpty() ? 0 : BuiltInRegistries.FLUID.getId(fluid.getFluid());
    }

    private static int low(int value) {
        return value & 0xFFFF;
    }

    private static int high(int value) {
        return value >>> 16;
    }

    private static int combine(int low, int high) {
        return (low & 0xFFFF) | ((high & 0xFFFF) << 16);
    }

    private final class AutomationItemHandler implements IItemHandler {
        @Override
        public int getSlots() {
            return INVENTORY_SLOT_COUNT;
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            return inventory.getStackInSlot(slot);
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            if (stack.isEmpty()) {
                return ItemStack.EMPTY;
            }
            if (isFilledFluidContainer(stack)) {
                return inventory.insertItem(DUMP_INPUT_SLOT, stack, simulate);
            }
            if (isFillableFluidContainer(stack)) {
                return inventory.insertItem(FILL_INPUT_SLOT, stack, simulate);
            }
            return stack;
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            if (slot != DUMP_OUTPUT_SLOT && slot != FILL_OUTPUT_SLOT) {
                return ItemStack.EMPTY;
            }
            return inventory.extractItem(slot, amount, simulate);
        }

        @Override
        public int getSlotLimit(int slot) {
            return inventory.getSlotLimit(slot);
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return inventory.isItemValid(slot, stack);
        }
    }

    private final class AutomationFluidHandler implements IFluidHandler {
        @Override
        public int getTanks() {
            return fluidTank.getTanks();
        }

        @Override
        public FluidStack getFluidInTank(int tank) {
            return fluidTank.getFluidInTank(tank);
        }

        @Override
        public int getTankCapacity(int tank) {
            return fluidTank.getTankCapacity(tank);
        }

        @Override
        public boolean isFluidValid(int tank, FluidStack stack) {
            return fluidTank.isFluidValid(tank, stack);
        }

        @Override
        public int fill(FluidStack resource, FluidAction action) {
            return fluidTank.fill(resource, action);
        }

        @Override
        public FluidStack drain(FluidStack resource, FluidAction action) {
            return fluidTank.drain(resource, action);
        }

        @Override
        public FluidStack drain(int maxDrain, FluidAction action) {
            return fluidTank.drain(maxDrain, action);
        }
    }
}
