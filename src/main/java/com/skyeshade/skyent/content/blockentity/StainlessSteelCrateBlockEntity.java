package com.skyeshade.skyent.content.blockentity;

import com.skyeshade.skyent.content.menu.StainlessSteelCrateMenu;
import com.skyeshade.skyent.content.menu.SteelCrateMenu;
import com.skyeshade.skyent.registry.ModBlockEntities;
import com.skyeshade.skyent.registry.ModSounds;
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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

public class StainlessSteelCrateBlockEntity extends BlockEntity implements MenuProvider {
    public static final int SLOT_COUNT = 54;
    private static final float SOUND_VOLUME = 1.8F;
    private static final float SOUND_PITCH = 1.0F;
    private int openCount;

    private final ItemStackHandler inventory = new ItemStackHandler(SLOT_COUNT) {
        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }
    };

    public StainlessSteelCrateBlockEntity(BlockPos pos, BlockState blockState) {
        super(ModBlockEntities.STAINLESS_STEEL_CRATE.get(), pos, blockState);
    }

    public ItemStackHandler getInventory() {
        return inventory;
    }

    public IItemHandler getAutomationItemHandler(@Nullable Direction side) {
        return inventory;
    }

    public void startOpen(Player player) {
        if (level == null || level.isClientSide || player.isSpectator()) {
            return;
        }

        if (openCount == 0) {
            playSound(level, ModSounds.STEEL_CRATE_OPEN.get());
        }
        openCount++;
    }

    public void stopOpen(Player player) {
        if (level == null || level.isClientSide || player.isSpectator()) {
            return;
        }

        openCount = Math.max(0, openCount - 1);
        if (openCount == 0) {
            playSound(level, ModSounds.STEEL_CRATE_CLOSE.get());
        }
    }

    public int getRedstoneSignal() {
        float fullness = 0.0F;
        int occupiedSlots = 0;
        for (int slot = 0; slot < inventory.getSlots(); slot++) {
            ItemStack stack = inventory.getStackInSlot(slot);
            if (!stack.isEmpty()) {
                fullness += (float) stack.getCount() / Math.min(inventory.getSlotLimit(slot), stack.getMaxStackSize());
                occupiedSlots++;
            }
        }

        fullness /= inventory.getSlots();
        return Mth.floor(fullness * 14.0F) + (occupiedSlots > 0 ? 1 : 0);
    }

    public void dropContents(Level level, BlockPos pos) {
        for (int slot = 0; slot < inventory.getSlots(); slot++) {
            ItemStack stack = inventory.getStackInSlot(slot);
            if (!stack.isEmpty()) {
                Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), stack);
                inventory.setStackInSlot(slot, ItemStack.EMPTY);
            }
        }
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.skyent.stainless_steel_crate");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new StainlessSteelCrateMenu(containerId, playerInventory, this);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("Inventory", inventory.serializeNBT(registries));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        inventory.deserializeNBT(registries, tag.getCompound("Inventory"));
    }

    private void playSound(Level level, net.minecraft.sounds.SoundEvent sound) {
        level.playSound(null, worldPosition, sound, SoundSource.BLOCKS, SOUND_VOLUME, SOUND_PITCH);
    }
}
