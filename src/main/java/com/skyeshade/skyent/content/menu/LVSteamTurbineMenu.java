package com.skyeshade.skyent.content.menu;

import com.skyeshade.skyent.content.blockentity.LVSteamTurbineBlockEntity;
import com.skyeshade.skyent.registry.ModBlocks;
import com.skyeshade.skyent.registry.ModMenus;
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

public class LVSteamTurbineMenu extends AbstractContainerMenu {
    public static final int STEAM_INPUT_SLOT_X = 49;
    public static final int STEAM_INPUT_SLOT_Y = 9;
    public static final int EMPTY_CONTAINER_SLOT_X = 49;
    public static final int EMPTY_CONTAINER_SLOT_Y = 41;
    private static final int PLAYER_INVENTORY_X = 8;
    private static final int PLAYER_INVENTORY_Y = 84;
    private static final int HOTBAR_Y = 142;
    private static final int SLOT_SIZE = 18;
    private static final int PLAYER_INVENTORY_ROWS = 3;
    private static final int PLAYER_INVENTORY_COLUMNS = 9;
    private static final int MACHINE_SLOT_COUNT = 2;
    private static final int PLAYER_INVENTORY_SLOT_COUNT = 27;
    private static final int DATA_COUNT = 10;

    private final LVSteamTurbineBlockEntity blockEntity;
    private final ContainerData data;

    public LVSteamTurbineMenu(int containerId, Inventory playerInventory, RegistryFriendlyByteBuf extraData) {
        this(containerId, playerInventory, getBlockEntity(playerInventory, extraData), new SimpleContainerData(DATA_COUNT));
    }

    public LVSteamTurbineMenu(int containerId, Inventory playerInventory, LVSteamTurbineBlockEntity blockEntity, ContainerData data) {
        super(ModMenus.LV_STEAM_TURBINE.get(), containerId);
        this.blockEntity = blockEntity;
        this.data = data;

        addSlot(new SteamInputSlot(blockEntity.getInventory(), LVSteamTurbineBlockEntity.STEAM_INPUT_SLOT, STEAM_INPUT_SLOT_X, STEAM_INPUT_SLOT_Y));
        addSlot(new OutputSlot(blockEntity.getInventory(), LVSteamTurbineBlockEntity.EMPTY_CONTAINER_SLOT, EMPTY_CONTAINER_SLOT_X, EMPTY_CONTAINER_SLOT_Y));
        addPlayerInventory(playerInventory);
        addDataSlots(data);
    }

    public int getEnergyStoredRJ() {
        return (data.get(1) << 16) | data.get(0);
    }

    public int getMaxEnergyStoredRJ() {
        return (data.get(3) << 16) | data.get(2);
    }

    public int getSteamAmount() {
        return data.get(4);
    }

    public int getSteamCapacity() {
        return data.get(5);
    }

    public int getRpm() {
        return data.get(6);
    }

    public int getMaxRpm() {
        return data.get(7);
    }

    public int getCurrentOutput() {
        return data.get(8);
    }

    public int getCurrentSteamUsage() {
        return data.get(9);
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(ContainerLevelAccess.create(blockEntity.getLevel(), blockEntity.getBlockPos()), player, ModBlocks.LV_STEAM_TURBINE.get());
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack result = ItemStack.EMPTY;
        Slot slot = slots.get(index);

        if (slot.hasItem()) {
            ItemStack stack = slot.getItem();
            result = stack.copy();

            if (index < MACHINE_SLOT_COUNT) {
                if (!moveItemStackTo(stack, MACHINE_SLOT_COUNT, slots.size(), true)) {
                    return ItemStack.EMPTY;
                }
            } else if (LVSteamTurbineBlockEntity.isSteamContainer(stack)) {
                if (!moveItemStackTo(stack, LVSteamTurbineBlockEntity.STEAM_INPUT_SLOT, LVSteamTurbineBlockEntity.STEAM_INPUT_SLOT + 1, false)) {
                    return ItemStack.EMPTY;
                }
            } else if (index < MACHINE_SLOT_COUNT + PLAYER_INVENTORY_SLOT_COUNT) {
                if (!moveItemStackTo(stack, MACHINE_SLOT_COUNT + PLAYER_INVENTORY_SLOT_COUNT, slots.size(), false)) {
                    return ItemStack.EMPTY;
                }
            } else if (!moveItemStackTo(stack, MACHINE_SLOT_COUNT, MACHINE_SLOT_COUNT + PLAYER_INVENTORY_SLOT_COUNT, false)) {
                return ItemStack.EMPTY;
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

    private static LVSteamTurbineBlockEntity getBlockEntity(Inventory playerInventory, RegistryFriendlyByteBuf extraData) {
        BlockEntity blockEntity = playerInventory.player.level().getBlockEntity(extraData.readBlockPos());
        if (blockEntity instanceof LVSteamTurbineBlockEntity turbine) {
            return turbine;
        }

        throw new IllegalStateException("Expected LV steam turbine block entity");
    }

    private static final class SteamInputSlot extends SlotItemHandler {
        private SteamInputSlot(ItemStackHandler itemHandler, int index, int xPosition, int yPosition) {
            super(itemHandler, index, xPosition, yPosition);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return LVSteamTurbineBlockEntity.isSteamContainer(stack);
        }
    }

    private static final class OutputSlot extends SlotItemHandler {
        private OutputSlot(ItemStackHandler itemHandler, int index, int xPosition, int yPosition) {
            super(itemHandler, index, xPosition, yPosition);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return false;
        }
    }
}
