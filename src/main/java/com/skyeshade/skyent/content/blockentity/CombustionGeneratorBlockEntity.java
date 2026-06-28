package com.skyeshade.skyent.content.blockentity;

import com.skyeshade.skyent.content.block.CombustionGeneratorBlock;
import com.skyeshade.skyent.content.energy.ElectricalTier;
import com.skyeshade.skyent.content.menu.CombustionGeneratorMenu;
import com.skyeshade.skyent.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.Containers;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.energy.EnergyStorage;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.FluidActionResult;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidUtil;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.core.Direction;
import org.jetbrains.annotations.Nullable;

public class CombustionGeneratorBlockEntity extends BlockEntity implements MenuProvider {
    public static final int ENERGY_CAPACITY = 100_000;
    public static final int ENERGY_PER_TICK = 32;
    public static final int MAX_EXTRACT = 64;
    public static final ElectricalTier OUTPUT_TIER = ElectricalTier.LV;
    public static final int OUTPUT_VOLTAGE = OUTPUT_TIER.voltage();
    public static final double MAX_OUTPUT_CURRENT_AMPS = 2.0D;
    public static final int MAX_OUTPUT_RJ_PER_TICK = 64;
    public static final int WATER_CAPACITY = 10_000;
    public static final int WATER_CONSUMPTION_PER_TICK = 1;
    private static final int FIRE_SPREAD_RADIUS = 2;
    private static final float FIRE_PLACEMENT_CHANCE = 0.28F;

    public static final int WATER_INPUT_SLOT = 0;
    public static final int EMPTY_CONTAINER_SLOT = 1;
    public static final int FUEL_SLOT = 2;
    private static final int INVENTORY_SLOT_COUNT = 3;

    private static final int DATA_ENERGY_LOW = 0;
    private static final int DATA_ENERGY_HIGH = 1;
    private static final int DATA_BURN_TIME = 2;
    private static final int DATA_BURN_TIME_TOTAL = 3;
    private static final int DATA_WATER_AMOUNT = 4;
    private static final int DATA_WATER_CAPACITY = 5;
    private static final int DATA_CURRENT_GENERATION_RATE = 6;
    private static final int DATA_COUNT = 7;

    private int burnTime;
    private int burnTimeTotal;
    private int currentGenerationRate;

    private final ItemStackHandler inventory = new ItemStackHandler(INVENTORY_SLOT_COUNT) {
        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return switch (slot) {
                case WATER_INPUT_SLOT -> CombustionGeneratorBlockEntity.isWaterContainer(stack);
                case EMPTY_CONTAINER_SLOT -> false;
                case FUEL_SLOT -> CombustionGeneratorBlockEntity.isFuel(stack);
                default -> false;
            };
        }

        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }
    };

    private final GeneratorEnergyStorage energy = new GeneratorEnergyStorage();
    private final IFluidHandler automationFluidHandler = new WaterInputFluidHandler();
    private final FluidTank waterTank = new FluidTank(WATER_CAPACITY, stack -> stack.is(Fluids.WATER)) {
        @Override
        protected void onContentsChanged() {
            setChanged();
        }
    };

    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case DATA_ENERGY_LOW -> energy.getEnergyStored() & 0xFFFF;
                case DATA_ENERGY_HIGH -> energy.getEnergyStored() >>> 16;
                case DATA_BURN_TIME -> burnTime;
                case DATA_BURN_TIME_TOTAL -> burnTimeTotal;
                case DATA_WATER_AMOUNT -> waterTank.getFluidAmount();
                case DATA_WATER_CAPACITY -> waterTank.getCapacity();
                case DATA_CURRENT_GENERATION_RATE -> currentGenerationRate;
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {
            switch (index) {
                case DATA_ENERGY_LOW -> energy.setEnergy((energy.getEnergyStored() & 0xFFFF0000) | (value & 0xFFFF));
                case DATA_ENERGY_HIGH -> energy.setEnergy((energy.getEnergyStored() & 0xFFFF) | ((value & 0xFFFF) << 16));
                case DATA_BURN_TIME -> burnTime = value;
                case DATA_BURN_TIME_TOTAL -> burnTimeTotal = value;
                case DATA_WATER_AMOUNT -> waterTank.setFluid(value > 0 ? new FluidStack(Fluids.WATER, value) : FluidStack.EMPTY);
                case DATA_CURRENT_GENERATION_RATE -> currentGenerationRate = value;
                default -> {
                }
            }
        }

        @Override
        public int getCount() {
            return DATA_COUNT;
        }
    };

    public CombustionGeneratorBlockEntity(BlockPos pos, BlockState blockState) {
        super(ModBlockEntities.COMBUSTION_GENERATOR.get(), pos, blockState);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, CombustionGeneratorBlockEntity generator) {
        boolean wasBurning = generator.isBurning();
        int previousGenerationRate = generator.currentGenerationRate;
        generator.currentGenerationRate = 0;
        boolean changed = false;

        if (generator.tryDrainWaterContainer()) {
            changed = true;
        }

        if (generator.pushEnergyToNeighbors(level, pos)) {
            changed = true;
        }

        if (generator.isBurning()) {
            if (generator.waterTank.getFluidAmount() <= 0) {
                generator.explode(level, pos);
                return;
            }

            generator.waterTank.drain(WATER_CONSUMPTION_PER_TICK, IFluidHandler.FluidAction.EXECUTE);
            if (generator.waterTank.getFluidAmount() <= 0) {
                generator.explode(level, pos);
                return;
            }

            generator.burnTime--;
            if (generator.energy.generateEnergy(ENERGY_PER_TICK) > 0) {
                generator.currentGenerationRate = ENERGY_PER_TICK;
            }
            changed = true;
        }

        if (!generator.isBurning() && generator.energy.getEnergyStored() < ENERGY_CAPACITY && generator.waterTank.getFluidAmount() > 0) {
            ItemStack fuel = generator.inventory.getStackInSlot(FUEL_SLOT);
            int fuelBurnTime = getBurnTime(fuel);

            if (fuelBurnTime > 0) {
                generator.burnTime = fuelBurnTime;
                generator.burnTimeTotal = fuelBurnTime;
                consumeFuel(generator, fuel);
                changed = true;
            }
        }

        if (wasBurning != generator.isBurning()) {
            level.setBlock(pos, state.setValue(CombustionGeneratorBlock.LIT, generator.isBurning()), Block.UPDATE_CLIENTS);
            changed = true;
        }

        if (previousGenerationRate != generator.currentGenerationRate) {
            changed = true;
        }

        if (changed) {
            setChanged(level, pos, state);
        }
    }

    public static boolean isFuel(ItemStack stack) {
        return getBurnTime(stack) > 0;
    }

    public static boolean isWaterContainer(ItemStack stack) {
        return FluidUtil.getFluidContained(stack).filter(fluid -> fluid.is(Fluids.WATER)).isPresent();
    }

    private static int getBurnTime(ItemStack stack) {
        return stack.isEmpty() ? 0 : stack.getBurnTime(RecipeType.SMELTING);
    }

    private static void consumeFuel(CombustionGeneratorBlockEntity generator, ItemStack fuel) {
        ItemStack remainder = fuel.getCraftingRemainingItem();
        fuel.shrink(1);

        if (fuel.isEmpty()) {
            generator.inventory.setStackInSlot(FUEL_SLOT, remainder);
        }
    }

    private boolean tryDrainWaterContainer() {
        ItemStack input = inventory.getStackInSlot(WATER_INPUT_SLOT);
        if (input.isEmpty()) {
            return false;
        }

        FluidStack contained = FluidUtil.getFluidContained(input).orElse(FluidStack.EMPTY);
        if (contained.isEmpty() || !contained.is(Fluids.WATER) || contained.getAmount() > waterTank.getSpace()) {
            return false;
        }

        FluidActionResult simulated = FluidUtil.tryEmptyContainer(input, waterTank, contained.getAmount(), null, false);
        if (!simulated.isSuccess() || !canPlaceOutput(simulated.getResult())) {
            return false;
        }

        FluidActionResult result = FluidUtil.tryEmptyContainer(input, waterTank, contained.getAmount(), null, true);
        if (!result.isSuccess()) {
            return false;
        }

        input.shrink(1);
        placeOutput(result.getResult());
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
            inventory.setStackInSlot(EMPTY_CONTAINER_SLOT, result.copy());
        } else {
            output.grow(result.getCount());
            inventory.setStackInSlot(EMPTY_CONTAINER_SLOT, output);
        }
    }

    private void explode(Level level, BlockPos pos) {
        level.removeBlock(pos, false);
        level.explode(null, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 3.0F, Level.ExplosionInteraction.BLOCK);

        if (level instanceof ServerLevel serverLevel) {
            placeNearbyFire(serverLevel, pos);
        }
    }

    private static void placeNearbyFire(ServerLevel level, BlockPos center) {
        BlockState fireState = Blocks.FIRE.defaultBlockState();

        for (BlockPos target : BlockPos.betweenClosed(center.offset(-FIRE_SPREAD_RADIUS, -FIRE_SPREAD_RADIUS, -FIRE_SPREAD_RADIUS), center.offset(FIRE_SPREAD_RADIUS, FIRE_SPREAD_RADIUS, FIRE_SPREAD_RADIUS))) {
            if (target.equals(center) || level.random.nextFloat() > FIRE_PLACEMENT_CHANCE) {
                continue;
            }

            BlockPos immutableTarget = target.immutable();
            if (level.isEmptyBlock(immutableTarget) && fireState.canSurvive(level, immutableTarget)) {
                level.setBlock(immutableTarget, fireState, Block.UPDATE_ALL);
            }
        }
    }

    public boolean isBurning() {
        return burnTime > 0;
    }

    public ItemStackHandler getInventory() {
        return inventory;
    }

    public IItemHandler getAutomationItemHandler(@Nullable Direction side) {
        return new AutomationItemHandler(side);
    }

    public ContainerData getData() {
        return data;
    }

    public IEnergyStorage getEnergyStorage() {
        return energy;
    }

    public IFluidHandler getAutomationFluidHandler(@Nullable Direction side) {
        return automationFluidHandler;
    }

    public int getWaterAmount() {
        return waterTank.getFluidAmount();
    }

    public int getWaterCapacity() {
        return waterTank.getCapacity();
    }

    public int getCurrentGenerationRate() {
        return currentGenerationRate;
    }

    public int getStoredRJ() {
        return energy.getEnergyStored();
    }

    public int getMaxOutputRJPerTick() {
        return MAX_OUTPUT_RJ_PER_TICK;
    }

    public int extractRJ(int maxAmount, boolean simulate) {
        int extracted = energy.extractEnergy(maxAmount, simulate);
        if (extracted > 0 && !simulate) {
            setChanged();
        }

        return extracted;
    }

    public int getRedstoneSignal() {
        return Mth.floor((float) energy.getEnergyStored() / ENERGY_CAPACITY * 15.0F);
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
        return Component.translatable("container.skyent.combustion_generator");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new CombustionGeneratorMenu(containerId, playerInventory, this, data);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("Energy", energy.getEnergyStored());
        tag.putInt("BurnTime", burnTime);
        tag.putInt("BurnTimeTotal", burnTimeTotal);
        tag.put("WaterTank", waterTank.writeToNBT(registries, new CompoundTag()));
        tag.put("Inventory", inventory.serializeNBT(registries));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        energy.setEnergy(tag.getInt("Energy"));
        burnTime = tag.getInt("BurnTime");
        burnTimeTotal = tag.getInt("BurnTimeTotal");
        waterTank.readFromNBT(registries, tag.getCompound("WaterTank"));
        inventory.deserializeNBT(registries, tag.getCompound("Inventory"));
    }

    private static final class GeneratorEnergyStorage extends EnergyStorage {
        private GeneratorEnergyStorage() {
            super(ENERGY_CAPACITY, 0, MAX_EXTRACT);
        }

        private int generateEnergy(int amount) {
            int previousEnergy = energy;
            energy = Math.min(capacity, energy + amount);
            return energy - previousEnergy;
        }

        private void setEnergy(int amount) {
            energy = Mth.clamp(amount, 0, capacity);
        }
    }

    private boolean pushEnergyToNeighbors(Level level, BlockPos pos) {
        if (energy.getEnergyStored() <= 0) {
            return false;
        }

        boolean transferred = false;
        for (Direction direction : Direction.values()) {
            IEnergyStorage receiver = level.getCapability(Capabilities.EnergyStorage.BLOCK, pos.relative(direction), direction.getOpposite());
            if (receiver == null || !receiver.canReceive()) {
                continue;
            }

            int simulatedExtract = energy.extractEnergy(MAX_EXTRACT, true);
            if (simulatedExtract <= 0) {
                break;
            }

            int accepted = receiver.receiveEnergy(simulatedExtract, false);
            if (accepted > 0) {
                energy.extractEnergy(accepted, false);
                transferred = true;
            }
        }

        return transferred;
    }

    private final class WaterInputFluidHandler implements IFluidHandler {
        @Override
        public int getTanks() {
            return waterTank.getTanks();
        }

        @Override
        public FluidStack getFluidInTank(int tank) {
            return waterTank.getFluidInTank(tank);
        }

        @Override
        public int getTankCapacity(int tank) {
            return waterTank.getTankCapacity(tank);
        }

        @Override
        public boolean isFluidValid(int tank, FluidStack stack) {
            return waterTank.isFluidValid(tank, stack);
        }

        @Override
        public int fill(FluidStack resource, FluidAction action) {
            return waterTank.fill(resource, action);
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

    private final class AutomationItemHandler implements IItemHandler {
        @Nullable
        private final Direction side;

        private AutomationItemHandler(@Nullable Direction side) {
            this.side = side;
        }

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

            if (isWaterContainer(stack)) {
                return inventory.insertItem(WATER_INPUT_SLOT, stack, simulate);
            }

            if (isFuel(stack)) {
                return inventory.insertItem(FUEL_SLOT, stack, simulate);
            }

            return stack;
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            if (side != Direction.DOWN || slot != EMPTY_CONTAINER_SLOT) {
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
            return switch (slot) {
                case WATER_INPUT_SLOT -> isWaterContainer(stack);
                case FUEL_SLOT -> isFuel(stack);
                default -> false;
            };
        }
    }
}
