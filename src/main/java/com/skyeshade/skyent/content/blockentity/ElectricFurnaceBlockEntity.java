package com.skyeshade.skyent.content.blockentity;

import com.skyeshade.skyent.content.block.ElectricFurnaceBlock;
import com.skyeshade.skyent.content.energy.ElectricalTier;
import com.skyeshade.skyent.content.menu.ElectricFurnaceMenu;
import com.skyeshade.skyent.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
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
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SmeltingRecipe;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.energy.EnergyStorage;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

public class ElectricFurnaceBlockEntity extends BlockEntity implements MenuProvider {
    public static final int ENERGY_CAPACITY = 20_000;
    public static final int ENERGY_USAGE_PER_TICK = 16;
    public static final ElectricalTier REQUIRED_TIER = ElectricalTier.LV;
    public static final int REQUIRED_VOLTAGE = REQUIRED_TIER.voltage();
    public static final double RUNNING_CURRENT_AMPS = 0.5D;
    public static final int INPUT_SLOT = 0;
    public static final int OUTPUT_SLOT = 1;
    public static final int DEFAULT_COOK_TIME = 200;
    private static final int INVENTORY_SLOT_COUNT = 2;

    private static final int DATA_ENERGY_LOW = 0;
    private static final int DATA_ENERGY_HIGH = 1;
    private static final int DATA_MAX_ENERGY_LOW = 2;
    private static final int DATA_MAX_ENERGY_HIGH = 3;
    private static final int DATA_COOK_PROGRESS = 4;
    private static final int DATA_MAX_COOK_PROGRESS = 5;
    private static final int DATA_CURRENT_ENERGY_USAGE = 6;
    private static final int DATA_COUNT = 7;

    private final RecipeManager.CachedCheck<SingleRecipeInput, SmeltingRecipe> recipeCheck = RecipeManager.createCheck(RecipeType.SMELTING);
    private int cookProgress;
    private int maxCookProgress = DEFAULT_COOK_TIME;
    private int currentEnergyUsage;

    private final ItemStackHandler inventory = new ItemStackHandler(INVENTORY_SLOT_COUNT) {
        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return slot == INPUT_SLOT && ElectricFurnaceBlockEntity.this.isSmeltable(stack);
        }

        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }
    };

    private final FurnaceEnergyStorage energy = new FurnaceEnergyStorage();
    private final IItemHandler automationItemHandler = new AutomationItemHandler();

    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case DATA_ENERGY_LOW -> energy.getEnergyStored() & 0xFFFF;
                case DATA_ENERGY_HIGH -> energy.getEnergyStored() >>> 16;
                case DATA_MAX_ENERGY_LOW -> energy.getMaxEnergyStored() & 0xFFFF;
                case DATA_MAX_ENERGY_HIGH -> energy.getMaxEnergyStored() >>> 16;
                case DATA_COOK_PROGRESS -> cookProgress;
                case DATA_MAX_COOK_PROGRESS -> maxCookProgress;
                case DATA_CURRENT_ENERGY_USAGE -> currentEnergyUsage;
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {
            switch (index) {
                case DATA_ENERGY_LOW -> energy.setEnergy((energy.getEnergyStored() & 0xFFFF0000) | (value & 0xFFFF));
                case DATA_ENERGY_HIGH -> energy.setEnergy((energy.getEnergyStored() & 0xFFFF) | ((value & 0xFFFF) << 16));
                case DATA_COOK_PROGRESS -> cookProgress = value;
                case DATA_MAX_COOK_PROGRESS -> maxCookProgress = value;
                case DATA_CURRENT_ENERGY_USAGE -> currentEnergyUsage = value;
                default -> {
                }
            }
        }

        @Override
        public int getCount() {
            return DATA_COUNT;
        }
    };

    public ElectricFurnaceBlockEntity(BlockPos pos, BlockState blockState) {
        super(ModBlockEntities.ELECTRIC_FURNACE.get(), pos, blockState);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, ElectricFurnaceBlockEntity furnace) {
        boolean wasCooking = furnace.isActivelyCooking();
        int previousEnergyUsage = furnace.currentEnergyUsage;
        furnace.currentEnergyUsage = 0;
        boolean changed = false;

        RecipeHolder<SmeltingRecipe> recipe = furnace.getCurrentRecipe();
        if (recipe == null || !furnace.canOutputRecipe(recipe)) {
            if (furnace.cookProgress != 0) {
                furnace.cookProgress = 0;
                changed = true;
            }
        } else if (furnace.energy.getEnergyStored() >= ENERGY_USAGE_PER_TICK) {
            furnace.maxCookProgress = Math.max(1, recipe.value().getCookingTime());
            furnace.energy.consumeEnergy(ENERGY_USAGE_PER_TICK);
            furnace.currentEnergyUsage = ENERGY_USAGE_PER_TICK;
            furnace.cookProgress++;
            changed = true;

            if (furnace.cookProgress >= furnace.maxCookProgress) {
                furnace.completeRecipe(recipe);
                furnace.cookProgress = 0;
            }
        }

        boolean isCooking = furnace.isActivelyCooking();
        if (wasCooking != isCooking) {
            level.setBlock(pos, state.setValue(ElectricFurnaceBlock.LIT, isCooking), Block.UPDATE_CLIENTS);
            changed = true;
        }

        if (previousEnergyUsage != furnace.currentEnergyUsage) {
            changed = true;
        }

        if (changed) {
            setChanged(level, pos, state);
        }
    }

    public boolean isSmeltable(ItemStack stack) {
        return level != null && getRecipeFor(stack) != null;
    }

    private RecipeHolder<SmeltingRecipe> getCurrentRecipe() {
        return getRecipeFor(inventory.getStackInSlot(INPUT_SLOT));
    }

    @Nullable
    private RecipeHolder<SmeltingRecipe> getRecipeFor(ItemStack stack) {
        if (stack.isEmpty() || level == null) {
            return null;
        }

        return recipeCheck.getRecipeFor(new SingleRecipeInput(stack), level).orElse(null);
    }

    private boolean canOutputRecipe(RecipeHolder<SmeltingRecipe> recipe) {
        ItemStack result = recipe.value().assemble(new SingleRecipeInput(inventory.getStackInSlot(INPUT_SLOT)), level.registryAccess());
        if (result.isEmpty()) {
            return false;
        }

        ItemStack output = inventory.getStackInSlot(OUTPUT_SLOT);
        return output.isEmpty() || ItemStack.isSameItemSameComponents(output, result) && output.getCount() + result.getCount() <= output.getMaxStackSize();
    }

    private void completeRecipe(RecipeHolder<SmeltingRecipe> recipe) {
        ItemStack input = inventory.getStackInSlot(INPUT_SLOT);
        ItemStack result = recipe.value().assemble(new SingleRecipeInput(input), level.registryAccess());
        ItemStack output = inventory.getStackInSlot(OUTPUT_SLOT);

        input.shrink(1);
        if (output.isEmpty()) {
            inventory.setStackInSlot(OUTPUT_SLOT, result.copy());
        } else {
            output.grow(result.getCount());
            inventory.setStackInSlot(OUTPUT_SLOT, output);
        }
    }

    private boolean isActivelyCooking() {
        return currentEnergyUsage > 0;
    }

    public ItemStackHandler getInventory() {
        return inventory;
    }

    public IItemHandler getAutomationItemHandler() {
        return automationItemHandler;
    }

    public IEnergyStorage getEnergyStorage() {
        return energy;
    }

    public int getAvailableRJCapacity() {
        return energy.getMaxEnergyStored() - energy.getEnergyStored();
    }

    public int receiveRJ(int maxAmount, boolean simulate) {
        return energy.receiveEnergy(maxAmount, simulate);
    }

    public ContainerData getData() {
        return data;
    }

    public int getRedstoneSignal() {
        return Mth.floor((float) inventory.getStackInSlot(OUTPUT_SLOT).getCount() / inventory.getSlotLimit(OUTPUT_SLOT) * 15.0F);
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
        return Component.translatable("container.skyent.electric_furnace");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new ElectricFurnaceMenu(containerId, playerInventory, this, data);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("Energy", energy.getEnergyStored());
        tag.putInt("CookProgress", cookProgress);
        tag.putInt("MaxCookProgress", maxCookProgress);
        tag.put("Inventory", inventory.serializeNBT(registries));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        energy.setEnergy(tag.getInt("Energy"));
        cookProgress = tag.getInt("CookProgress");
        maxCookProgress = tag.contains("MaxCookProgress") ? tag.getInt("MaxCookProgress") : DEFAULT_COOK_TIME;
        inventory.deserializeNBT(registries, tag.getCompound("Inventory"));
    }

    private final class FurnaceEnergyStorage extends EnergyStorage {
        private FurnaceEnergyStorage() {
            super(ENERGY_CAPACITY, ENERGY_CAPACITY, 0);
        }

        @Override
        public int receiveEnergy(int maxReceive, boolean simulate) {
            int received = super.receiveEnergy(maxReceive, simulate);
            if (received > 0 && !simulate) {
                setChanged();
            }

            return received;
        }

        private void consumeEnergy(int amount) {
            energy = Math.max(0, energy - amount);
        }

        private void setEnergy(int amount) {
            energy = Mth.clamp(amount, 0, capacity);
        }
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
            if (slot != INPUT_SLOT || !isSmeltable(stack)) {
                return stack;
            }

            return inventory.insertItem(INPUT_SLOT, stack, simulate);
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            if (slot != OUTPUT_SLOT) {
                return ItemStack.EMPTY;
            }

            return inventory.extractItem(OUTPUT_SLOT, amount, simulate);
        }

        @Override
        public int getSlotLimit(int slot) {
            return inventory.getSlotLimit(slot);
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return slot == INPUT_SLOT && isSmeltable(stack);
        }
    }
}
