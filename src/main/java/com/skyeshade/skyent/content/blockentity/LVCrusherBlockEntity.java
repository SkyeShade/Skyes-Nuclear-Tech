package com.skyeshade.skyent.content.blockentity;

import com.skyeshade.skyent.content.block.LVCrusherBlock;
import com.skyeshade.skyent.content.energy.ElectricalTier;
import com.skyeshade.skyent.content.energy.LVEnergyConstants;
import com.skyeshade.skyent.content.energy.RJEnergyInfo;
import com.skyeshade.skyent.content.energy.RJStorage;
import com.skyeshade.skyent.content.item.LVCrusherRecipes;
import com.skyeshade.skyent.content.menu.LVCrusherMenu;
import com.skyeshade.skyent.registry.ModBlockEntities;
import java.lang.reflect.Method;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
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
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

public class LVCrusherBlockEntity extends BlockEntity implements MenuProvider, RJEnergyInfo {
    public static final int ENERGY_CAPACITY_RJ = 8_000;
    public static final int ENERGY_USAGE_RJ_PER_TICK = 32;
    public static final int MAX_PROGRESS = 400;
    public static final ElectricalTier REQUIRED_TIER = ElectricalTier.LV;
    public static final double RUNNING_CURRENT_AMPS = 1.0D;
    public static final double MAX_INPUT_CURRENT_AMPS = LVEnergyConstants.LV_MACHINE_MAX_INPUT_CURRENT_AMPS;
    public static final int MAX_INPUT_RJ_PER_TICK = LVEnergyConstants.LV_MACHINE_MAX_INPUT_RJ_PER_TICK;
    public static final int INPUT_SLOT = 0;
    public static final int OUTPUT_SLOT = 1;
    private static final int INVENTORY_SLOT_COUNT = 2;
    private static final float CRUSHER_LOOP_VOLUME = 0.4F;
    private static final float CRUSHER_LOOP_PITCH = 0.65F;
    private static final String TAG_STORED_RJ = "StoredRJ";

    private static final int DATA_ENERGY_LOW = 0;
    private static final int DATA_ENERGY_HIGH = 1;
    private static final int DATA_MAX_ENERGY_LOW = 2;
    private static final int DATA_MAX_ENERGY_HIGH = 3;
    private static final int DATA_PROGRESS = 4;
    private static final int DATA_MAX_PROGRESS = 5;
    private static final int DATA_CURRENT_ENERGY_USAGE = 6;
    private static final int DATA_COUNT = 7;

    private int progress;
    private int currentEnergyUsage;

    private final ItemStackHandler inventory = new ItemStackHandler(INVENTORY_SLOT_COUNT) {
        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return slot == INPUT_SLOT && LVCrusherRecipes.isCrushable(stack);
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
                case DATA_PROGRESS -> progress;
                case DATA_MAX_PROGRESS -> MAX_PROGRESS;
                case DATA_CURRENT_ENERGY_USAGE -> currentEnergyUsage;
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {
            switch (index) {
                case DATA_ENERGY_LOW -> rjStorage.setStoredRJ((rjStorage.getStoredRJ() & 0xFFFF0000) | (value & 0xFFFF));
                case DATA_ENERGY_HIGH -> rjStorage.setStoredRJ((rjStorage.getStoredRJ() & 0xFFFF) | ((value & 0xFFFF) << 16));
                case DATA_PROGRESS -> progress = value;
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

    public LVCrusherBlockEntity(BlockPos pos, BlockState blockState) {
        super(ModBlockEntities.LV_CRUSHER.get(), pos, blockState);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, LVCrusherBlockEntity crusher) {
        boolean wasActive = crusher.isActive();
        int previousEnergyUsage = crusher.currentEnergyUsage;
        crusher.currentEnergyUsage = 0;
        boolean changed = false;

        ItemStack result = crusher.getCurrentResult();
        if (result.isEmpty() || !crusher.canOutput(result)) {
            if (crusher.progress != 0) {
                crusher.progress = 0;
                changed = true;
            }
        } else if (crusher.rjStorage.getStoredRJ() >= ENERGY_USAGE_RJ_PER_TICK) {
            crusher.rjStorage.consumeRJ(ENERGY_USAGE_RJ_PER_TICK);
            crusher.currentEnergyUsage = ENERGY_USAGE_RJ_PER_TICK;
            crusher.progress++;
            changed = true;

            if (crusher.progress >= MAX_PROGRESS) {
                crusher.completeRecipe(result);
                crusher.progress = 0;
            }
        }

        boolean isActive = crusher.isActive();
        if (wasActive != isActive) {
            level.setBlock(pos, state.setValue(LVCrusherBlock.LIT, isActive), Block.UPDATE_CLIENTS);
            changed = true;
        }

        if (previousEnergyUsage != crusher.currentEnergyUsage) {
            changed = true;
        }

        if (changed) {
            setChanged(level, pos, state);
        }
    }

    public static void clientTick(Level level, BlockPos pos, BlockState state, LVCrusherBlockEntity crusher) {
        if (state.getValue(LVCrusherBlock.LIT)) {
            startClientLoop(level, pos);
        } else {
            stopClientLoop(level, pos);
        }
    }

    public boolean isCrushable(ItemStack stack) {
        return LVCrusherRecipes.isCrushable(stack);
    }

    private ItemStack getCurrentResult() {
        return LVCrusherRecipes.getResult(inventory.getStackInSlot(INPUT_SLOT)).orElse(ItemStack.EMPTY);
    }

    private boolean canOutput(ItemStack result) {
        if (result.isEmpty()) {
            return false;
        }

        ItemStack output = inventory.getStackInSlot(OUTPUT_SLOT);
        return output.isEmpty()
                || ItemStack.isSameItemSameComponents(output, result) && output.getCount() + result.getCount() <= output.getMaxStackSize();
    }

    private void completeRecipe(ItemStack result) {
        ItemStack input = inventory.getStackInSlot(INPUT_SLOT);
        ItemStack output = inventory.getStackInSlot(OUTPUT_SLOT);

        input.shrink(1);
        if (output.isEmpty()) {
            inventory.setStackInSlot(OUTPUT_SLOT, result.copy());
        } else {
            ItemStack merged = output.copy();
            merged.grow(result.getCount());
            inventory.setStackInSlot(OUTPUT_SLOT, merged);
        }
    }

    private boolean isActive() {
        return currentEnergyUsage > 0;
    }

    public ItemStackHandler getInventory() {
        return inventory;
    }

    public IItemHandler getAutomationItemHandler() {
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
        return Component.translatable("container.skyent.lv_crusher");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new LVCrusherMenu(containerId, playerInventory, this, data);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt(TAG_STORED_RJ, rjStorage.getStoredRJ());
        tag.putInt("Progress", progress);
        tag.put("Inventory", inventory.serializeNBT(registries));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        rjStorage.setStoredRJ(tag.getInt(TAG_STORED_RJ));
        progress = tag.getInt("Progress");
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
                method.invoke(null, level, pos, SoundEvents.MINECART_RIDING, SoundSource.BLOCKS, CRUSHER_LOOP_VOLUME, CRUSHER_LOOP_PITCH);
            } else {
                Method method = managerClass.getMethod(methodName, clientLevelClass, BlockPos.class, net.minecraft.sounds.SoundEvent.class);
                method.invoke(null, level, pos, SoundEvents.MINECART_RIDING);
            }
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Unable to update LV Crusher client loop sound", exception);
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
            if (slot != INPUT_SLOT || !isCrushable(stack)) {
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
            return slot == INPUT_SLOT && isCrushable(stack);
        }
    }
}
