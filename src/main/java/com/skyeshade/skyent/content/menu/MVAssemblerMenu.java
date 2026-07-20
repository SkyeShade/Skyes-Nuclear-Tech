package com.skyeshade.skyent.content.menu;

import com.skyeshade.skyent.content.blockentity.MVAssemblerBlockEntity;
import com.skyeshade.skyent.registry.ModBlocks;
import com.skyeshade.skyent.registry.ModMenus;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.SlotItemHandler;

public class MVAssemblerMenu extends AbstractContainerMenu {
    public static final int INPUT_GRID_X = 8;
    public static final int INPUT_GRID_Y = 17;
    public static final int INPUT_COLUMNS = 4;
    public static final int INPUT_ROWS = 3;
    public static final int RECIPE_BUTTON_X = 180;
    public static final int RECIPE_BUTTON_Y = 16;
    public static final int RECIPE_BUTTON_SIZE = 18;
    public static final int SELECTED_RECIPE_X = 181;
    public static final int SELECTED_RECIPE_Y = 40;
    public static final int OUTPUT_SLOT_X = 121;
    public static final int OUTPUT_SLOT_Y = 35;
    private static final int PLAYER_INVENTORY_X = 8;
    private static final int PLAYER_INVENTORY_Y = 84;
    private static final int HOTBAR_Y = 142;
    private static final int SLOT_SIZE = 18;
    private static final int PLAYER_INVENTORY_ROWS = 3;
    private static final int PLAYER_INVENTORY_COLUMNS = 9;
    private static final int MACHINE_SLOT_COUNT = MVAssemblerBlockEntity.INVENTORY_SLOT_COUNT;
    private static final int PLAYER_INVENTORY_SLOT_COUNT = 27;
    private static final int DATA_COUNT = 9;

    private final MVAssemblerBlockEntity blockEntity;
    private final ContainerData data;
    private boolean recipeSelectMode;

    public MVAssemblerMenu(int containerId, Inventory playerInventory, RegistryFriendlyByteBuf extraData) {
        this(containerId, playerInventory, getBlockEntity(playerInventory, extraData), new SimpleContainerData(DATA_COUNT));
    }

    public MVAssemblerMenu(int containerId, Inventory playerInventory, MVAssemblerBlockEntity blockEntity, ContainerData data) {
        super(ModMenus.MV_ASSEMBLER.get(), containerId);
        this.blockEntity = blockEntity;
        this.data = data;

        for (int row = 0; row < INPUT_ROWS; row++) {
            for (int column = 0; column < INPUT_COLUMNS; column++) {
                int slot = row * INPUT_COLUMNS + column;
                addSlot(new MachineInputSlot(
                        this,
                        blockEntity.getInventory(),
                        slot,
                        INPUT_GRID_X + column * SLOT_SIZE,
                        INPUT_GRID_Y + row * SLOT_SIZE
                ));
            }
        }
        addSlot(new OutputSlot(this, blockEntity.getInventory(), MVAssemblerBlockEntity.OUTPUT_SLOT, OUTPUT_SLOT_X, OUTPUT_SLOT_Y));
        addPlayerInventory(playerInventory);
        addDataSlots(data);
    }

    public MVAssemblerBlockEntity getBlockEntity() {
        return blockEntity;
    }

    public BlockPos getBlockPos() {
        return blockEntity.getBlockPos();
    }

    public int getEnergyStoredRJ() {
        return (data.get(1) << 16) | data.get(0);
    }

    public int getMaxEnergyStoredRJ() {
        return (data.get(3) << 16) | data.get(2);
    }

    public int getProgress() {
        return data.get(4);
    }

    public int getMaxProgress() {
        return data.get(5);
    }

    public int getCurrentEnergyUsage() {
        return data.get(6);
    }

    public int getSelectedRecipeIndex() {
        return data.get(7);
    }

    public int getStatusCode() {
        return data.get(8);
    }

    public boolean isPlayerInventorySlot(int index) {
        return index >= MACHINE_SLOT_COUNT && index < MACHINE_SLOT_COUNT + PLAYER_INVENTORY_SLOT_COUNT + PLAYER_INVENTORY_COLUMNS;
    }

    public boolean isMachineSlot(int index) {
        return index >= 0 && index < MACHINE_SLOT_COUNT;
    }

    public void setRecipeSelectMode(boolean recipeSelectMode) {
        this.recipeSelectMode = recipeSelectMode;
    }

    private boolean isRecipeSelectMode() {
        return recipeSelectMode;
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(ContainerLevelAccess.create(blockEntity.getLevel(), blockEntity.getBlockPos()), player, ModBlocks.MV_ASSEMBLER.get());
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (recipeSelectMode) {
            return ItemStack.EMPTY;
        }

        ItemStack result = ItemStack.EMPTY;
        Slot slot = slots.get(index);

        if (slot.hasItem()) {
            ItemStack stack = slot.getItem();
            result = stack.copy();

            if (index < MACHINE_SLOT_COUNT) {
                if (!moveItemStackTo(stack, MACHINE_SLOT_COUNT, slots.size(), true)) {
                    return ItemStack.EMPTY;
                }
            } else if (!moveItemStackTo(stack, 0, MVAssemblerBlockEntity.INPUT_SLOT_COUNT, false)) {
                if (index < MACHINE_SLOT_COUNT + PLAYER_INVENTORY_SLOT_COUNT) {
                    if (!moveItemStackTo(stack, MACHINE_SLOT_COUNT + PLAYER_INVENTORY_SLOT_COUNT, slots.size(), false)) {
                        return ItemStack.EMPTY;
                    }
                } else if (!moveItemStackTo(stack, MACHINE_SLOT_COUNT, MACHINE_SLOT_COUNT + PLAYER_INVENTORY_SLOT_COUNT, false)) {
                    return ItemStack.EMPTY;
                }
            }

            if (stack.isEmpty()) {
                slot.setByPlayer(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }
        }

        return result;
    }

    private void addPlayerInventory(Inventory playerInventory) {
        for (int row = 0; row < PLAYER_INVENTORY_ROWS; row++) {
            for (int column = 0; column < PLAYER_INVENTORY_COLUMNS; column++) {
                addSlot(new Slot(
                        playerInventory,
                        column + row * PLAYER_INVENTORY_COLUMNS + PLAYER_INVENTORY_COLUMNS,
                        PLAYER_INVENTORY_X + column * SLOT_SIZE,
                        PLAYER_INVENTORY_Y + row * SLOT_SIZE
                ));
            }
        }

        for (int column = 0; column < PLAYER_INVENTORY_COLUMNS; column++) {
            addSlot(new Slot(playerInventory, column, PLAYER_INVENTORY_X + column * SLOT_SIZE, HOTBAR_Y));
        }
    }

    private static MVAssemblerBlockEntity getBlockEntity(Inventory playerInventory, RegistryFriendlyByteBuf extraData) {
        BlockEntity blockEntity = playerInventory.player.level().getBlockEntity(extraData.readBlockPos());
        if (blockEntity instanceof MVAssemblerBlockEntity assembler) {
            return assembler;
        }

        throw new IllegalStateException("Expected MV assembler block entity");
    }

    private static final class MachineInputSlot extends SlotItemHandler {
        private final MVAssemblerMenu menu;

        private MachineInputSlot(MVAssemblerMenu menu, ItemStackHandler itemHandler, int index, int xPosition, int yPosition) {
            super(itemHandler, index, xPosition, yPosition);
            this.menu = menu;
        }

        @Override
        public boolean isActive() {
            return !menu.isRecipeSelectMode();
        }
    }

    private static final class OutputSlot extends SlotItemHandler {
        private final MVAssemblerMenu menu;

        private OutputSlot(MVAssemblerMenu menu, ItemStackHandler itemHandler, int index, int xPosition, int yPosition) {
            super(itemHandler, index, xPosition, yPosition);
            this.menu = menu;
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return false;
        }

        @Override
        public boolean isActive() {
            return !menu.isRecipeSelectMode();
        }
    }
}
