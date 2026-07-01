package com.skyeshade.skyent.content.blockentity;

import com.skyeshade.skyent.content.block.CombustionGeneratorBlock;
import com.skyeshade.skyent.content.menu.CombustionGeneratorMenu;
import com.skyeshade.skyent.registry.ModBlockEntities;
import com.skyeshade.skyent.registry.ModFluids;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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

public class CombustionGeneratorBlockEntity extends BlockEntity implements MenuProvider {
    public static final int INPUT_TANK_CAPACITY_MB = 8_000;
    public static final int OUTPUT_TANK_CAPACITY_MB = 8_000;
    public static final double MIN_TEMPERATURE_C = 21.0D;
    public static final double BOILING_TEMPERATURE_C = 100.0D;
    public static final double MAX_TEMPERATURE_C = 512.0D;
    public static final int HEATUP_TIME_TICKS = 1_200;
    public static final double HEATUP_PER_TICK_C = (MAX_TEMPERATURE_C - MIN_TEMPERATURE_C) / HEATUP_TIME_TICKS;
    public static final double COOLDOWN_PER_TICK_C = HEATUP_PER_TICK_C / 3.0D;
    public static final int WATER_TO_STEAM_RATIO = 10;
    public static final double MAX_WATER_CONSUMPTION_MB_PER_TICK = 2.0D;
    public static final int STEAM_AUTO_OUTPUT_MB_PER_TICK = 100;
    private static final int FIRE_SPREAD_RADIUS = 2;
    private static final float FIRE_PLACEMENT_CHANCE = 0.28F;
    private static final int DRY_EXPLOSION_TICKS = 200;

    public static final int WATER_INPUT_SLOT = 0;
    public static final int EMPTY_CONTAINER_SLOT = 1;
    public static final int FUEL_SLOT = 2;
    public static final int STEAM_CONTAINER_INPUT_SLOT = 3;
    public static final int STEAM_CONTAINER_OUTPUT_SLOT = 4;
    private static final int INVENTORY_SLOT_COUNT = 5;

    private static final int DATA_BURN_TIME = 0;
    private static final int DATA_BURN_TIME_TOTAL = 1;
    private static final int DATA_WATER_AMOUNT = 2;
    private static final int DATA_WATER_CAPACITY = 3;
    private static final int DATA_STEAM_AMOUNT = 4;
    private static final int DATA_STEAM_CAPACITY = 5;
    private static final int DATA_TEMPERATURE_CENTI = 6;
    private static final int DATA_COUNT = 7;

    private int burnTime;
    private int burnTimeTotal;
    private double temperatureC = MIN_TEMPERATURE_C;
    private double waterProductionAccumulator;
    private int dryTicks;

    private final ItemStackHandler inventory = new ItemStackHandler(INVENTORY_SLOT_COUNT) {
        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return switch (slot) {
                case WATER_INPUT_SLOT -> CombustionGeneratorBlockEntity.isWaterContainer(stack);
                case FUEL_SLOT -> CombustionGeneratorBlockEntity.isFuel(stack);
                case STEAM_CONTAINER_INPUT_SLOT -> CombustionGeneratorBlockEntity.isFillableFluidContainer(stack);
                default -> false;
            };
        }

        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }
    };

    private final FluidTank waterTank = new FluidTank(INPUT_TANK_CAPACITY_MB, stack -> stack.is(Fluids.WATER)) {
        @Override
        protected void onContentsChanged() {
            setChanged();
        }
    };
    private final FluidTank steamTank = new FluidTank(OUTPUT_TANK_CAPACITY_MB, stack -> stack.is(ModFluids.STEAM.get())) {
        @Override
        protected void onContentsChanged() {
            setChanged();
        }
    };
    private final IFluidHandler waterFillHandler = new WaterFillHandler();
    private final IFluidHandler automationFluidHandler = new BoilerFluidHandler();

    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case DATA_BURN_TIME -> burnTime;
                case DATA_BURN_TIME_TOTAL -> burnTimeTotal;
                case DATA_WATER_AMOUNT -> waterTank.getFluidAmount();
                case DATA_WATER_CAPACITY -> waterTank.getCapacity();
                case DATA_STEAM_AMOUNT -> steamTank.getFluidAmount();
                case DATA_STEAM_CAPACITY -> steamTank.getCapacity();
                case DATA_TEMPERATURE_CENTI -> Mth.floor(temperatureC * 100.0D);
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {
            switch (index) {
                case DATA_BURN_TIME -> burnTime = value;
                case DATA_BURN_TIME_TOTAL -> burnTimeTotal = value;
                case DATA_WATER_AMOUNT -> waterTank.setFluid(value > 0 ? new FluidStack(Fluids.WATER, value) : FluidStack.EMPTY);
                case DATA_STEAM_AMOUNT -> steamTank.setFluid(value > 0 ? new FluidStack(ModFluids.STEAM.get(), value) : FluidStack.EMPTY);
                case DATA_TEMPERATURE_CENTI -> temperatureC = value / 100.0D;
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

    public static void serverTick(Level level, BlockPos pos, BlockState state, CombustionGeneratorBlockEntity boiler) {
        boolean wasBurning = boiler.isBurning();
        double previousTemperature = boiler.temperatureC;
        int previousWater = boiler.waterTank.getFluidAmount();
        int previousSteam = boiler.steamTank.getFluidAmount();
        boolean changed = false;

        changed |= boiler.tryDrainWaterContainer();
        changed |= boiler.tryFillSteamContainer();

        if (!boiler.isBurning() && boiler.hasValidBoilerInput()) {
            ItemStack fuel = boiler.inventory.getStackInSlot(FUEL_SLOT);
            int fuelBurnTime = getBurnTime(fuel);
            if (fuelBurnTime > 0) {
                boiler.burnTime = fuelBurnTime;
                boiler.burnTimeTotal = fuelBurnTime;
                consumeFuel(boiler, fuel);
                changed = true;
            }
        }

        if (boiler.isBurning()) {
            boiler.burnTime--;
            boiler.temperatureC = Math.min(MAX_TEMPERATURE_C, boiler.temperatureC + HEATUP_PER_TICK_C);
            changed = true;
        } else if (boiler.temperatureC > MIN_TEMPERATURE_C) {
            boiler.temperatureC = Math.max(MIN_TEMPERATURE_C, boiler.temperatureC - COOLDOWN_PER_TICK_C);
            boiler.waterProductionAccumulator = 0.0D;
            changed = true;
        }

        changed |= boiler.produceSteam(level, pos);
        changed |= boiler.outputSteamToNeighbors();

        if (wasBurning != boiler.isBurning()) {
            level.setBlock(pos, state.setValue(CombustionGeneratorBlock.LIT, boiler.isBurning()), Block.UPDATE_CLIENTS);
            changed = true;
        }

        if (Math.abs(previousTemperature - boiler.temperatureC) >= 0.01D
                || previousWater != boiler.waterTank.getFluidAmount()
                || previousSteam != boiler.steamTank.getFluidAmount()) {
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

    private static boolean isValidBoilerInput(FluidStack stack) {
        return !stack.isEmpty() && stack.is(Fluids.WATER);
    }

    private boolean hasValidBoilerInput() {
        return isValidBoilerInput(waterTank.getFluid());
    }

    public static boolean isFillableFluidContainer(ItemStack stack) {
        return !stack.isEmpty()
                && FluidUtil.getFluidHandler(stack).isPresent()
                && FluidUtil.getFluidContained(stack).orElse(FluidStack.EMPTY).isEmpty();
    }

    private static int getBurnTime(ItemStack stack) {
        return stack.isEmpty() ? 0 : stack.getBurnTime(RecipeType.SMELTING);
    }

    private static void consumeFuel(CombustionGeneratorBlockEntity boiler, ItemStack fuel) {
        ItemStack remainder = fuel.getCraftingRemainingItem();
        fuel.shrink(1);

        if (fuel.isEmpty()) {
            boiler.inventory.setStackInSlot(FUEL_SLOT, remainder);
        }
    }

    private boolean produceSteam(Level level, BlockPos pos) {
        if (temperatureC < BOILING_TEMPERATURE_C || waterTank.getFluidAmount() <= 0 || steamTank.getSpace() <= 0) {
            if (isBurning() && temperatureC >= BOILING_TEMPERATURE_C && waterTank.getFluidAmount() <= 0) {
                dryTicks++;
                if (dryTicks >= DRY_EXPLOSION_TICKS) {
                    explode(level, pos);
                }
            } else {
                dryTicks = 0;
            }
            return false;
        }

        dryTicks = 0;
        double heatFactor = Mth.clamp((temperatureC - BOILING_TEMPERATURE_C) / (MAX_TEMPERATURE_C - BOILING_TEMPERATURE_C), 0.0D, 1.0D);
        waterProductionAccumulator += heatFactor * MAX_WATER_CONSUMPTION_MB_PER_TICK;
        int waterToUse = Mth.floor(waterProductionAccumulator);
        if (waterToUse <= 0) {
            return false;
        }

        waterToUse = Math.min(waterToUse, waterTank.getFluidAmount());
        waterToUse = Math.min(waterToUse, steamTank.getSpace() / WATER_TO_STEAM_RATIO);
        if (waterToUse <= 0) {
            return false;
        }

        int steamToMake = waterToUse * WATER_TO_STEAM_RATIO;
        waterTank.drain(waterToUse, IFluidHandler.FluidAction.EXECUTE);
        steamTank.fill(new FluidStack(ModFluids.STEAM.get(), steamToMake), IFluidHandler.FluidAction.EXECUTE);
        waterProductionAccumulator -= waterToUse;
        return true;
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

        FluidActionResult simulated = FluidUtil.tryEmptyContainer(input, waterFillHandler, contained.getAmount(), null, false);
        if (!simulated.isSuccess() || !canPlaceOutput(EMPTY_CONTAINER_SLOT, simulated.getResult())) {
            return false;
        }

        FluidActionResult result = FluidUtil.tryEmptyContainer(input, waterFillHandler, contained.getAmount(), null, true);
        if (!result.isSuccess()) {
            return false;
        }

        input.shrink(1);
        placeOutput(EMPTY_CONTAINER_SLOT, result.getResult());
        return true;
    }

    private boolean tryFillSteamContainer() {
        ItemStack input = inventory.getStackInSlot(STEAM_CONTAINER_INPUT_SLOT);
        if (input.isEmpty() || steamTank.getFluidAmount() <= 0) {
            return false;
        }

        FluidActionResult simulated = FluidUtil.tryFillContainer(input, steamTank, steamTank.getFluidAmount(), null, false);
        if (!simulated.isSuccess() || !canPlaceOutput(STEAM_CONTAINER_OUTPUT_SLOT, simulated.getResult())) {
            return false;
        }

        FluidActionResult result = FluidUtil.tryFillContainer(input, steamTank, steamTank.getFluidAmount(), null, true);
        if (!result.isSuccess()) {
            return false;
        }

        input.shrink(1);
        placeOutput(STEAM_CONTAINER_OUTPUT_SLOT, result.getResult());
        return true;
    }

    private boolean outputSteamToNeighbors() {
        if (level == null || steamTank.getFluidAmount() <= 0) {
            return false;
        }

        int remaining = STEAM_AUTO_OUTPUT_MB_PER_TICK;
        boolean moved = false;
        for (Direction direction : Direction.values()) {
            if (remaining <= 0 || steamTank.getFluidAmount() <= 0) {
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

            FluidStack offered = steamTank.drain(Math.min(remaining, steamTank.getFluidAmount()), IFluidHandler.FluidAction.SIMULATE);
            int accepted = receiver.fill(offered, IFluidHandler.FluidAction.SIMULATE);
            if (accepted <= 0) {
                continue;
            }

            FluidStack drained = steamTank.drain(accepted, IFluidHandler.FluidAction.EXECUTE);
            int inserted = receiver.fill(drained, IFluidHandler.FluidAction.EXECUTE);
            if (inserted < drained.getAmount()) {
                steamTank.fill(copyWithAmount(drained, drained.getAmount() - inserted), IFluidHandler.FluidAction.EXECUTE);
            }
            if (inserted > 0) {
                remaining -= inserted;
                moved = true;
            }
        }

        return moved;
    }

    private int fillInputTank(FluidStack resource, IFluidHandler.FluidAction action) {
        if (!isValidBoilerInput(resource)) {
            return 0;
        }

        boolean hotAndDry = isHotAndDry();
        int filled = waterTank.fill(resource, action);
        if (action.execute() && filled > 0 && hotAndDry && level != null) {
            explode(level, worldPosition);
        }

        return filled;
    }

    private boolean isHotAndDry() {
        return waterTank.getFluidAmount() <= 0 && temperatureC > BOILING_TEMPERATURE_C;
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

    private static FluidStack copyWithAmount(FluidStack stack, int amount) {
        FluidStack copy = stack.copy();
        copy.setAmount(amount);
        return copy;
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

    public IFluidHandler getAutomationFluidHandler(@Nullable Direction side) {
        return automationFluidHandler;
    }

    public ContainerData getData() {
        return data;
    }

    public int getWaterAmount() {
        return waterTank.getFluidAmount();
    }

    public int getWaterCapacity() {
        return waterTank.getCapacity();
    }

    public int getSteamAmount() {
        return steamTank.getFluidAmount();
    }

    public int getSteamCapacity() {
        return steamTank.getCapacity();
    }

    public double getTemperatureC() {
        return temperatureC;
    }

    public int getRedstoneSignal() {
        return Mth.floor((float) steamTank.getFluidAmount() / steamTank.getCapacity() * 15.0F);
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
        tag.putInt("BurnTime", burnTime);
        tag.putInt("BurnTimeTotal", burnTimeTotal);
        tag.putDouble("TemperatureC", temperatureC);
        tag.putDouble("WaterProductionAccumulator", waterProductionAccumulator);
        tag.putInt("DryTicks", dryTicks);
        tag.put("WaterTank", waterTank.writeToNBT(registries, new CompoundTag()));
        tag.put("SteamTank", steamTank.writeToNBT(registries, new CompoundTag()));
        tag.put("Inventory", inventory.serializeNBT(registries));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        burnTime = tag.getInt("BurnTime");
        burnTimeTotal = tag.getInt("BurnTimeTotal");
        temperatureC = tag.contains("TemperatureC") ? tag.getDouble("TemperatureC") : MIN_TEMPERATURE_C;
        waterProductionAccumulator = tag.getDouble("WaterProductionAccumulator");
        dryTicks = tag.getInt("DryTicks");
        waterTank.readFromNBT(registries, tag.getCompound("WaterTank"));
        if (tag.contains("SteamTank")) {
            steamTank.readFromNBT(registries, tag.getCompound("SteamTank"));
        }
        inventory.deserializeNBT(registries, tag.getCompound("Inventory"));
    }

    private final class BoilerFluidHandler implements IFluidHandler {
        @Override
        public int getTanks() {
            return 2;
        }

        @Override
        public FluidStack getFluidInTank(int tank) {
            return tank == 0 ? waterTank.getFluidInTank(0) : steamTank.getFluidInTank(0);
        }

        @Override
        public int getTankCapacity(int tank) {
            return tank == 0 ? waterTank.getTankCapacity(0) : steamTank.getTankCapacity(0);
        }

        @Override
        public boolean isFluidValid(int tank, FluidStack stack) {
            return tank == 0 && waterTank.isFluidValid(stack);
        }

        @Override
        public int fill(FluidStack resource, FluidAction action) {
            return fillInputTank(resource, action);
        }

        @Override
        public FluidStack drain(FluidStack resource, FluidAction action) {
            if (resource.isEmpty() || !resource.is(ModFluids.STEAM.get())) {
                return FluidStack.EMPTY;
            }
            return steamTank.drain(resource, action);
        }

        @Override
        public FluidStack drain(int maxDrain, FluidAction action) {
            return steamTank.drain(maxDrain, action);
        }
    }

    private final class WaterFillHandler implements IFluidHandler {
        @Override
        public int getTanks() {
            return 1;
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
            return fillInputTank(resource, action);
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
            if (isFillableFluidContainer(stack)) {
                return inventory.insertItem(STEAM_CONTAINER_INPUT_SLOT, stack, simulate);
            }
            if (isFuel(stack)) {
                return inventory.insertItem(FUEL_SLOT, stack, simulate);
            }
            return stack;
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            if (side != Direction.DOWN && side != null) {
                return ItemStack.EMPTY;
            }
            if (slot != EMPTY_CONTAINER_SLOT && slot != STEAM_CONTAINER_OUTPUT_SLOT) {
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
}
