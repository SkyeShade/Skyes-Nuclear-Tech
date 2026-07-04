package com.skyeshade.skyent.content.blockentity;

import com.skyeshade.skyent.content.block.LVSteamTurbineBlock;
import com.skyeshade.skyent.content.energy.ElectricalTier;
import com.skyeshade.skyent.content.energy.RJEnergyInfo;
import com.skyeshade.skyent.content.energy.RJStorage;
import com.skyeshade.skyent.content.fluid.SafeFluidItemUtil;
import com.skyeshade.skyent.content.item.SteelFluidBarrelItem;
import com.skyeshade.skyent.content.menu.LVSteamTurbineMenu;
import com.skyeshade.skyent.registry.ModBlockEntities;
import com.skyeshade.skyent.registry.ModFluids;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.Containers;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

public class LVSteamTurbineBlockEntity extends BlockEntity implements MenuProvider, RJEnergyInfo {
    public static final int STEAM_CAPACITY_MB = 8_000;
    public static final int ENERGY_CAPACITY_RJ = 32_000;
    public static final ElectricalTier OUTPUT_TIER = ElectricalTier.LV;
    public static final int OUTPUT_VOLTAGE = OUTPUT_TIER.voltage();
    public static final double GENERATED_CURRENT_AMPS = 1.0D;
    public static final double MAX_OUTPUT_CURRENT_AMPS = 2.0D;
    // Turbine generation remains 1 amp LV = 32 RJ/t.
    public static final int GENERATED_RJ_PER_TICK = (int) Math.round(OUTPUT_VOLTAGE * GENERATED_CURRENT_AMPS);
    // Output/export is allowed to burst up to 2 amps LV = 64 RJ/t from the internal buffer.
    public static final int MAX_OUTPUT_RJ_PER_TICK = (int) Math.round(OUTPUT_VOLTAGE * MAX_OUTPUT_CURRENT_AMPS);
    public static final double MAX_RPM = 1_000.0D;
    public static final double RPM_RAMP_UP_PER_TICK = 2.0D;
    public static final double RPM_RAMP_DOWN_PER_TICK = 8.0D;
    public static final int STEAM_CONSUMPTION_MB_PER_TICK = 10;

    public static final int STEAM_INPUT_SLOT = 0;
    public static final int EMPTY_CONTAINER_SLOT = 1;
    private static final int INVENTORY_SLOT_COUNT = 2;
    private static final String TAG_STORED_RJ = "StoredRJ";

    private static final int DATA_ENERGY_LOW = 0;
    private static final int DATA_ENERGY_HIGH = 1;
    private static final int DATA_MAX_ENERGY_LOW = 2;
    private static final int DATA_MAX_ENERGY_HIGH = 3;
    private static final int DATA_STEAM_AMOUNT = 4;
    private static final int DATA_STEAM_CAPACITY = 5;
    private static final int DATA_RPM = 6;
    private static final int DATA_MAX_RPM = 7;
    private static final int DATA_CURRENT_OUTPUT = 8;
    private static final int DATA_CURRENT_STEAM_USAGE = 9;
    private static final int DATA_CONTAINER_REVISION = 10;
    private static final int DATA_COUNT = 11;

    private double rpm;
    private int currentOutput;
    private int currentSteamUsage;
    private int containerRevision;
    private final Set<ServerPlayer> viewers = new HashSet<>();

    private final ItemStackHandler inventory = new ItemStackHandler(INVENTORY_SLOT_COUNT) {
        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return slot == STEAM_INPUT_SLOT && LVSteamTurbineBlockEntity.isSteamContainer(stack);
        }

        @Override
        protected void onContentsChanged(int slot) {
            containerRevision++;
            setChanged();
        }

        @Override
        public int getSlotLimit(int slot) {
            return switch (slot) {
                case STEAM_INPUT_SLOT, EMPTY_CONTAINER_SLOT -> 16;
                default -> super.getSlotLimit(slot);
            };
        }
    };

    private final RJStorage rjStorage = new RJStorage(ENERGY_CAPACITY_RJ);
    private final FluidTank steamTank = new FluidTank(STEAM_CAPACITY_MB, stack -> stack.is(ModFluids.STEAM.get())) {
        @Override
        protected void onContentsChanged() {
            setChanged();
        }
    };
    private final IItemHandler automationItemHandler = new AutomationItemHandler();
    private final IFluidHandler automationFluidHandler = new SteamInputFluidHandler();

    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            int stored = rjStorage.getStoredRJ();
            int capacity = rjStorage.getCapacityRJ();
            return switch (index) {
                case DATA_ENERGY_LOW -> stored & 0xFFFF;
                case DATA_ENERGY_HIGH -> stored >>> 16;
                case DATA_MAX_ENERGY_LOW -> capacity & 0xFFFF;
                case DATA_MAX_ENERGY_HIGH -> capacity >>> 16;
                case DATA_STEAM_AMOUNT -> steamTank.getFluidAmount();
                case DATA_STEAM_CAPACITY -> steamTank.getCapacity();
                case DATA_RPM -> Mth.floor(rpm);
                case DATA_MAX_RPM -> Mth.floor(MAX_RPM);
                case DATA_CURRENT_OUTPUT -> currentOutput;
                case DATA_CURRENT_STEAM_USAGE -> currentSteamUsage;
                case DATA_CONTAINER_REVISION -> containerRevision;
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {
            switch (index) {
                case DATA_ENERGY_LOW -> rjStorage.setStoredRJ((rjStorage.getStoredRJ() & 0xFFFF0000) | (value & 0xFFFF));
                case DATA_ENERGY_HIGH -> rjStorage.setStoredRJ((rjStorage.getStoredRJ() & 0xFFFF) | ((value & 0xFFFF) << 16));
                case DATA_RPM -> rpm = value;
                case DATA_CURRENT_OUTPUT -> currentOutput = value;
                case DATA_CURRENT_STEAM_USAGE -> currentSteamUsage = value;
                case DATA_CONTAINER_REVISION -> containerRevision = value;
                default -> {
                }
            }
        }

        @Override
        public int getCount() {
            return DATA_COUNT;
        }
    };

    public LVSteamTurbineBlockEntity(BlockPos pos, BlockState blockState) {
        super(ModBlockEntities.LV_STEAM_TURBINE.get(), pos, blockState);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, LVSteamTurbineBlockEntity turbine) {
        boolean wasActive = turbine.isSpinning();
        int previousOutput = turbine.currentOutput;
        int previousSteamUsage = turbine.currentSteamUsage;
        turbine.currentOutput = 0;
        turbine.currentSteamUsage = 0;
        boolean changed = false;

        changed |= turbine.tryDrainSteamContainer();

        boolean hasSteam = turbine.steamTank.getFluidAmount() > 0;
        if (hasSteam) {
            int consumed = turbine.steamTank.drain(STEAM_CONSUMPTION_MB_PER_TICK, IFluidHandler.FluidAction.EXECUTE).getAmount();
            turbine.currentSteamUsage = consumed;
            if (consumed > 0) {
                turbine.rpm = Math.min(MAX_RPM, turbine.rpm + RPM_RAMP_UP_PER_TICK);
                changed = true;
            }
        } else if (turbine.rpm > 0.0D) {
            turbine.rpm = Math.max(0.0D, turbine.rpm - RPM_RAMP_DOWN_PER_TICK);
            changed = true;
        }

        if (turbine.rpm > 0.0D && turbine.rjStorage.getAvailableRJCapacity() > 0) {
            int generated = Math.max(1, Mth.floor(GENERATED_RJ_PER_TICK * (turbine.rpm / MAX_RPM)));
            turbine.currentOutput = turbine.rjStorage.receiveRJ(generated, false);
            changed |= turbine.currentOutput > 0;
        }

        boolean active = turbine.isSpinning();
        if (wasActive != active) {
            level.setBlock(pos, state.setValue(LVSteamTurbineBlock.LIT, active), Block.UPDATE_CLIENTS);
            changed = true;
        }
        if (previousOutput != turbine.currentOutput || previousSteamUsage != turbine.currentSteamUsage) {
            changed = true;
        }
        if (changed) {
            setChanged(level, pos, state);
        }
    }

    public static boolean isSteamContainer(ItemStack stack) {
        if (SteelFluidBarrelItem.isSteelFluidBarrel(stack)) {
            FluidStack fluid = SteelFluidBarrelItem.getContainedFluid(stack);
            return stack.getCount() == 1
                    ? SafeFluidItemUtil.containsSteam(stack)
                    : SteelFluidBarrelItem.isFullBarrel(stack) && fluid.is(ModFluids.STEAM.get());
        }
        if (stack.getCount() > 1) {
            return false;
        }
        return SafeFluidItemUtil.containsSteam(stack);
    }

    private boolean tryDrainSteamContainer() {
        ItemStack input = inventory.getStackInSlot(STEAM_INPUT_SLOT);
        if (input.isEmpty()) {
            return false;
        }

        if (tryDrainStackedSteelBarrel(STEAM_INPUT_SLOT, EMPTY_CONTAINER_SLOT, steamTank, stack -> !stack.isEmpty() && stack.is(ModFluids.STEAM.get()))) {
            return true;
        }
        if (SteelFluidBarrelItem.isSteelFluidBarrel(input) && input.getCount() > 1) {
            return false;
        }

        SafeFluidItemUtil.TransferResult result = SafeFluidItemUtil.drainContainerIntoTank(
                input,
                steamTank,
                stack -> !stack.isEmpty() && stack.is(ModFluids.STEAM.get()),
                steamTank.getSpace()
        );
        if (!result.transferred()) {
            return false;
        }

        ItemStack container = result.container();
        if (SafeFluidItemUtil.isEmptyFluidContainer(container) && canPlaceOutput(container)) {
            forceSetItemSlot(STEAM_INPUT_SLOT, ItemStack.EMPTY);
            placeOutput(container);
        } else {
            forceSetItemSlot(STEAM_INPUT_SLOT, container);
        }
        return true;
    }

    private boolean tryDrainStackedSteelBarrel(int inputSlot, int outputSlot, IFluidHandler targetTank, Predicate<FluidStack> acceptedFluid) {
        ItemStack input = inventory.getStackInSlot(inputSlot);
        if (!SteelFluidBarrelItem.isSteelFluidBarrel(input) || input.getCount() <= 1) {
            return false;
        }

        FluidStack fluid = SteelFluidBarrelItem.getContainedFluid(input);
        if (!SteelFluidBarrelItem.isFullBarrel(input) || !acceptedFluid.test(fluid)) {
            return false;
        }

        FluidStack fullFluid = fluid.copy();
        fullFluid.setAmount(SteelFluidBarrelItem.CAPACITY_MB);
        if (targetTank.fill(fullFluid, IFluidHandler.FluidAction.SIMULATE) != SteelFluidBarrelItem.CAPACITY_MB) {
            return false;
        }

        ItemStack emptyBarrel = SteelFluidBarrelItem.createEmptyBarrel(1);
        if (!canPlaceOutput(emptyBarrel)) {
            return false;
        }

        int filled = targetTank.fill(fullFluid, IFluidHandler.FluidAction.EXECUTE);
        if (filled != SteelFluidBarrelItem.CAPACITY_MB) {
            return false;
        }

        ItemStack remaining = input.copy();
        remaining.shrink(1);
        forceSetItemSlot(inputSlot, remaining);
        placeOutput(emptyBarrel);
        return true;
    }

    private boolean canPlaceOutput(ItemStack result) {
        if (result.isEmpty()) {
            return true;
        }

        ItemStack output = inventory.getStackInSlot(EMPTY_CONTAINER_SLOT);
        return output.isEmpty() || ItemStack.isSameItemSameComponents(output, result) && output.getCount() + result.getCount() <= output.getMaxStackSize();
    }

    private void placeOutput(ItemStack result) {
        if (result.isEmpty()) {
            return;
        }

        ItemStack output = inventory.getStackInSlot(EMPTY_CONTAINER_SLOT);
        if (output.isEmpty()) {
            forceSetItemSlot(EMPTY_CONTAINER_SLOT, result);
        } else {
            ItemStack merged = output.copy();
            merged.grow(result.getCount());
            forceSetItemSlot(EMPTY_CONTAINER_SLOT, merged);
        }
    }

    private void forceSetItemSlot(int slot, ItemStack stack) {
        inventory.setStackInSlot(slot, ItemStack.EMPTY);
        if (!stack.isEmpty()) {
            inventory.setStackInSlot(slot, stack.copy());
        }
        containerRevision++;
        setChanged();
        syncContainerSlotToViewers(slot, stack);
    }

    private void syncContainerSlotToViewers(int handlerSlot, ItemStack stack) {
        for (ServerPlayer viewer : Set.copyOf(viewers)) {
            if (viewer.containerMenu instanceof LVSteamTurbineMenu menu && menu.getBlockEntity() == this) {
                menu.syncHandlerSlot(viewer, handlerSlot, stack);
            } else {
                viewers.remove(viewer);
            }
        }
    }

    private boolean isSpinning() {
        return rpm > 0.0D;
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

    public int getStoredRJ() {
        return rjStorage.getStoredRJ();
    }

    @Override
    public int getMaxOutputRJPerTick() {
        return MAX_OUTPUT_RJ_PER_TICK;
    }

    public int extractRJ(int maxAmount, boolean simulate) {
        int extracted = rjStorage.extractRJ(maxAmount, simulate);
        if (extracted > 0 && !simulate) {
            setChanged();
        }
        return extracted;
    }

    public ContainerData getData() {
        return data;
    }

    public void addViewer(Player player) {
        if (player instanceof ServerPlayer serverPlayer) {
            viewers.add(serverPlayer);
        }
    }

    public void removeViewer(Player player) {
        if (player instanceof ServerPlayer serverPlayer) {
            viewers.remove(serverPlayer);
        }
    }

    public int getRedstoneSignal() {
        return Mth.floor(rpm / MAX_RPM * 15.0D);
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
        return Component.translatable("container.skyent.lv_steam_turbine");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new LVSteamTurbineMenu(containerId, playerInventory, this, data);
    }

    @Override
    public int getEnergyStoredRJ() {
        return rjStorage.getStoredRJ();
    }

    @Override
    public int getEnergyCapacityRJ() {
        return rjStorage.getCapacityRJ();
    }

    @Override
    public int getCurrentGenerationRJPerTick() {
        return currentOutput;
    }

    @Override
    public String getVoltageTierName() {
        return OUTPUT_TIER.displayName();
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt(TAG_STORED_RJ, rjStorage.getStoredRJ());
        tag.putDouble("RPM", rpm);
        tag.put("SteamTank", steamTank.writeToNBT(registries, new CompoundTag()));
        tag.put("Inventory", inventory.serializeNBT(registries));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        rjStorage.setStoredRJ(tag.getInt(TAG_STORED_RJ));
        rpm = tag.getDouble("RPM");
        steamTank.readFromNBT(registries, tag.getCompound("SteamTank"));
        inventory.deserializeNBT(registries, tag.getCompound("Inventory"));
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
            if (slot != STEAM_INPUT_SLOT || !isSteamContainer(stack)) {
                return stack;
            }
            return inventory.insertItem(STEAM_INPUT_SLOT, stack, simulate);
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            if (slot != EMPTY_CONTAINER_SLOT) {
                return ItemStack.EMPTY;
            }
            return inventory.extractItem(EMPTY_CONTAINER_SLOT, amount, simulate);
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

    private final class SteamInputFluidHandler implements IFluidHandler {
        @Override
        public int getTanks() {
            return steamTank.getTanks();
        }

        @Override
        public FluidStack getFluidInTank(int tank) {
            return steamTank.getFluidInTank(tank);
        }

        @Override
        public int getTankCapacity(int tank) {
            return steamTank.getTankCapacity(tank);
        }

        @Override
        public boolean isFluidValid(int tank, FluidStack stack) {
            return steamTank.isFluidValid(tank, stack);
        }

        @Override
        public int fill(FluidStack resource, FluidAction action) {
            return steamTank.fill(resource, action);
        }

        @Override
        public FluidStack drain(FluidStack resource, FluidAction action) {
            return FluidStack.EMPTY;
        }

        @Override
        public FluidStack drain(int maxDrain, FluidAction action) {
            return FluidStack.EMPTY;
        }
    }
}
