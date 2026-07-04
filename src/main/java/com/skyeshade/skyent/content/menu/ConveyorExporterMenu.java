package com.skyeshade.skyent.content.menu;

import com.skyeshade.skyent.content.blockentity.ConveyorExporterBlockEntity;
import com.skyeshade.skyent.registry.ModBlocks;
import com.skyeshade.skyent.registry.ModMenus;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.SlotItemHandler;

public class ConveyorExporterMenu extends AbstractContainerMenu {
    public static final int FILTER_COLUMNS = 5;
    public static final int FILTER_ROWS = 3;
    public static final int FILTER_X = 44;
    public static final int FILTER_Y = 16;
    public static final int MODE_BUTTON_ID = 0;
    private static final int PLAYER_INVENTORY_X = 8;
    private static final int PLAYER_INVENTORY_Y = 84;
    private static final int HOTBAR_Y = 142;
    private static final int SLOT_SIZE = 18;
    private static final int PLAYER_INVENTORY_ROWS = 3;
    private static final int PLAYER_INVENTORY_COLUMNS = 9;
    private static final int PLAYER_INVENTORY_SLOT_COUNT = 27;
    private static final int DATA_COUNT = 1;

    private final ConveyorExporterBlockEntity blockEntity;
    private final ContainerData data;

    public ConveyorExporterMenu(int containerId, Inventory playerInventory, RegistryFriendlyByteBuf extraData) {
        this(containerId, playerInventory, getBlockEntity(playerInventory, extraData), new SimpleContainerData(DATA_COUNT));
    }

    public ConveyorExporterMenu(int containerId, Inventory playerInventory, ConveyorExporterBlockEntity blockEntity, ContainerData data) {
        super(ModMenus.CONVEYOR_EXPORTER.get(), containerId);
        this.blockEntity = blockEntity;
        this.data = data;

        addFilterSlots(blockEntity.getFilter());
        addPlayerInventory(playerInventory);
        addDataSlots(data);
    }

    public boolean isWhitelist() {
        return data.get(0) != 0;
    }

    @Override
    public void clicked(int slotId, int button, ClickType clickType, Player player) {
        if (slotId >= 0 && slotId < ConveyorExporterBlockEntity.FILTER_SLOTS) {
            if (clickType == ClickType.PICKUP) {
                blockEntity.setFilterSlot(slotId, getCarried());
                broadcastChanges();
            }
            return;
        }

        super.clicked(slotId, button, clickType, player);
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (id == MODE_BUTTON_ID) {
            blockEntity.toggleFilterMode();
            broadcastChanges();
            return true;
        }

        return false;
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(ContainerLevelAccess.create(blockEntity.getLevel(), blockEntity.getBlockPos()), player, ModBlocks.CONVEYOR_EXPORTER.get());
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack result = ItemStack.EMPTY;
        Slot slot = slots.get(index);

        if (slot.hasItem()) {
            ItemStack stack = slot.getItem();
            result = stack.copy();

            if (index < ConveyorExporterBlockEntity.FILTER_SLOTS) {
                return ItemStack.EMPTY;
            }

            int playerStart = ConveyorExporterBlockEntity.FILTER_SLOTS;
            int playerEnd = playerStart + PLAYER_INVENTORY_SLOT_COUNT;
            if (index < playerEnd) {
                if (!moveItemStackTo(stack, playerEnd, slots.size(), false)) {
                    return ItemStack.EMPTY;
                }
            } else if (!moveItemStackTo(stack, playerStart, playerEnd, false)) {
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

    private void addFilterSlots(ItemStackHandler filter) {
        for (int row = 0; row < FILTER_ROWS; row++) {
            for (int column = 0; column < FILTER_COLUMNS; column++) {
                int slot = column + row * FILTER_COLUMNS;
                addSlot(new GhostFilterSlot(filter, slot, FILTER_X + column * SLOT_SIZE, FILTER_Y + row * SLOT_SIZE));
            }
        }
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

    private static ConveyorExporterBlockEntity getBlockEntity(Inventory playerInventory, RegistryFriendlyByteBuf extraData) {
        BlockEntity blockEntity = playerInventory.player.level().getBlockEntity(extraData.readBlockPos());
        if (blockEntity instanceof ConveyorExporterBlockEntity exporter) {
            return exporter;
        }

        throw new IllegalStateException("Expected conveyor exporter block entity");
    }

    private static final class GhostFilterSlot extends SlotItemHandler {
        private GhostFilterSlot(ItemStackHandler itemHandler, int index, int xPosition, int yPosition) {
            super(itemHandler, index, xPosition, yPosition);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return false;
        }

        @Override
        public boolean mayPickup(Player player) {
            return false;
        }
    }
}
