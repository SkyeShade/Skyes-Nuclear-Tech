package com.skyeshade.skyent.content.blockentity;

import com.skyeshade.skyent.content.block.ElectricFurnaceBlock;
import com.skyeshade.skyent.content.energy.ElectricalTier;
import com.skyeshade.skyent.content.energy.LVEnergyConstants;
import com.skyeshade.skyent.content.energy.RJEnergyInfo;
import com.skyeshade.skyent.content.energy.RJStorage;
import com.skyeshade.skyent.content.menu.ElectricFurnaceMenu;
import com.skyeshade.skyent.registry.ModBlockEntities;
import com.skyeshade.skyent.registry.ModSounds;
import java.lang.reflect.Method;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
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
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

public class ElectricFurnaceBlockEntity extends BlockEntity implements MenuProvider, RJEnergyInfo {
    public static final int ENERGY_CAPACITY_RJ = 20_000;
    public static final int ENERGY_USAGE_RJ_PER_TICK = 16;
    public static final ElectricalTier REQUIRED_TIER = ElectricalTier.LV;
    public static final int REQUIRED_VOLTAGE = REQUIRED_TIER.voltage();
    public static final double RUNNING_CURRENT_AMPS = 0.5D;
    public static final double MAX_INPUT_CURRENT_AMPS = LVEnergyConstants.LV_MACHINE_MAX_INPUT_CURRENT_AMPS;
    public static final int MAX_INPUT_RJ_PER_TICK = LVEnergyConstants.LV_MACHINE_MAX_INPUT_RJ_PER_TICK;
    private static final float ELECTRIC_FURNACE_LOOP_VOLUME = 0.6F;
    private static final float ELECTRIC_FURNACE_LOOP_PITCH = 1.0F;
    public static final int INPUT_SLOT = 0;
    public static final int OUTPUT_SLOT = 1;
    public static final int DEFAULT_COOK_TIME = 200;
    private static final int INVENTORY_SLOT_COUNT = 2;
    private static final String TAG_STORED_RJ = "StoredRJ";
    private static final String LEGACY_TAG_ENERGY = "Energy";

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

    private final RJStorage rjStorage = new RJStorage(ENERGY_CAPACITY_RJ);
    private final IItemHandler automationItemHandler = new AutomationItemHandler();

    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case DATA_ENERGY_LOW -> rjStorage.getStoredRJ() & 0xFFFF;
                case DATA_ENERGY_HIGH -> rjStorage.getStoredRJ() >>> 16;
                case DATA_MAX_ENERGY_LOW -> rjStorage.getCapacityRJ() & 0xFFFF;
                case DATA_MAX_ENERGY_HIGH -> rjStorage.getCapacityRJ() >>> 16;
                case DATA_COOK_PROGRESS -> cookProgress;
                case DATA_MAX_COOK_PROGRESS -> maxCookProgress;
                case DATA_CURRENT_ENERGY_USAGE -> currentEnergyUsage;
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {
            switch (index) {
                case DATA_ENERGY_LOW -> rjStorage.setStoredRJ((rjStorage.getStoredRJ() & 0xFFFF0000) | (value & 0xFFFF));
                case DATA_ENERGY_HIGH -> rjStorage.setStoredRJ((rjStorage.getStoredRJ() & 0xFFFF) | ((value & 0xFFFF) << 16));
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
        } else if (furnace.rjStorage.getStoredRJ() >= ENERGY_USAGE_RJ_PER_TICK) {
            furnace.maxCookProgress = Math.max(1, recipe.value().getCookingTime());
            furnace.rjStorage.consumeRJ(ENERGY_USAGE_RJ_PER_TICK);
            furnace.currentEnergyUsage = ENERGY_USAGE_RJ_PER_TICK;
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

    public static void clientTick(Level level, BlockPos pos, BlockState state, ElectricFurnaceBlockEntity furnace) {
        if (state.getValue(ElectricFurnaceBlock.LIT)) {
            startClientLoop(level, pos);
        } else {
            stopClientLoop(level, pos);
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

    public IItemHandler getAutomationItemHandler(@Nullable Direction side) {
        return automationItemHandler;
    }

    public int getAvailableRJCapacity() {
        return Math.min(rjStorage.getAvailableRJCapacity(), MAX_INPUT_RJ_PER_TICK);
    }

    public int receiveRJ(int maxAmount, boolean simulate) {
        int received = rjStorage.receiveRJ(Math.min(maxAmount, MAX_INPUT_RJ_PER_TICK), simulate);
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
        tag.putInt(TAG_STORED_RJ, rjStorage.getStoredRJ());
        tag.putInt("CookProgress", cookProgress);
        tag.putInt("MaxCookProgress", maxCookProgress);
        tag.put("Inventory", inventory.serializeNBT(registries));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        rjStorage.setStoredRJ(tag.contains(TAG_STORED_RJ) ? tag.getInt(TAG_STORED_RJ) : tag.getInt(LEGACY_TAG_ENERGY));
        cookProgress = tag.getInt("CookProgress");
        maxCookProgress = tag.contains("MaxCookProgress") ? tag.getInt("MaxCookProgress") : DEFAULT_COOK_TIME;
        inventory.deserializeNBT(registries, tag.getCompound("Inventory"));
    }

    @Override
    public void setRemoved() {
        if (level != null && level.isClientSide) {
            stopClientLoop(level, worldPosition);
        }
        super.setRemoved();
    }

    private static void startClientLoop(Level level, BlockPos pos) {
        invokeClientLoopMethod("startOrUpdateMachineLoop", level, pos);
    }

    private static void stopClientLoop(Level level, BlockPos pos) {
        invokeClientLoopMethod("stopMachineLoop", level, pos);
    }

    private static void invokeClientLoopMethod(String methodName, Level level, BlockPos pos) {
        if (!level.isClientSide) {
            return;
        }

        try {
            Class<?> clientLevelClass = Class.forName("net.minecraft.client.multiplayer.ClientLevel");
            if (!clientLevelClass.isInstance(level)) {
                return;
            }

            Class<?> managerClass = Class.forName("com.skyeshade.skyent.client.sound.MachineSoundManager");
            if ("startOrUpdateMachineLoop".equals(methodName)) {
                Method method = managerClass.getMethod(
                        methodName,
                        clientLevelClass,
                        BlockPos.class,
                        net.minecraft.sounds.SoundEvent.class,
                        SoundSource.class,
                        float.class,
                        float.class
                );
                method.invoke(null, level, pos, ModSounds.ELECTRIC_FURNACE_LOOP.get(), SoundSource.BLOCKS, ELECTRIC_FURNACE_LOOP_VOLUME, ELECTRIC_FURNACE_LOOP_PITCH);
            } else {
                Method method = managerClass.getMethod(methodName, clientLevelClass, BlockPos.class, net.minecraft.sounds.SoundEvent.class);
                method.invoke(null, level, pos, ModSounds.ELECTRIC_FURNACE_LOOP.get());
            }
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Unable to update Electric Furnace client loop sound", exception);
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
