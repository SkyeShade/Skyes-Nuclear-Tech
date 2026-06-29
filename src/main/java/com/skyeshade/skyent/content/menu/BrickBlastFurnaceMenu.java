package com.skyeshade.skyent.content.menu;

import com.skyeshade.skyent.content.blockentity.BrickBlastFurnaceBlockEntity;
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

public class BrickBlastFurnaceMenu extends AbstractContainerMenu {
    public static final int FUEL_SLOT_X = 17;
    public static final int FUEL_SLOT_Y = 35;
    public static final int TOP_INPUT_SLOT_X = 83;
    public static final int TOP_INPUT_SLOT_Y = 17;
    public static final int BOTTOM_INPUT_SLOT_X = 83;
    public static final int BOTTOM_INPUT_SLOT_Y = 53;
    public static final int OUTPUT_SLOT_X = 139;
    public static final int OUTPUT_SLOT_Y = 35;
    private static final int PLAYER_INVENTORY_X = 8;
    private static final int PLAYER_INVENTORY_Y = 84;
    private static final int HOTBAR_Y = 142;
    private static final int SLOT_SIZE = 18;
    private static final int PLAYER_INVENTORY_ROWS = 3;
    private static final int PLAYER_INVENTORY_COLUMNS = 9;
    private static final int MACHINE_SLOT_COUNT = 4;
    private static final int PLAYER_INVENTORY_SLOT_COUNT = 27;
    private static final int DATA_COUNT = 5;

    private final BrickBlastFurnaceBlockEntity blockEntity;
    private final ContainerData data;

    public BrickBlastFurnaceMenu(int containerId, Inventory playerInventory, RegistryFriendlyByteBuf extraData) {
        this(containerId, playerInventory, getBlockEntity(playerInventory, extraData), new SimpleContainerData(DATA_COUNT));
    }

    public BrickBlastFurnaceMenu(int containerId, Inventory playerInventory, BrickBlastFurnaceBlockEntity blockEntity, ContainerData data) {
        super(ModMenus.BRICK_BLAST_FURNACE.get(), containerId);
        this.blockEntity = blockEntity;
        this.data = data;

        addSlot(new FuelSlot(blockEntity.getInventory(), BrickBlastFurnaceBlockEntity.FUEL_SLOT, FUEL_SLOT_X, FUEL_SLOT_Y));
        addSlot(new InputSlot(blockEntity.getInventory(), BrickBlastFurnaceBlockEntity.TOP_INPUT_SLOT, TOP_INPUT_SLOT_X, TOP_INPUT_SLOT_Y));
        addSlot(new InputSlot(blockEntity.getInventory(), BrickBlastFurnaceBlockEntity.BOTTOM_INPUT_SLOT, BOTTOM_INPUT_SLOT_X, BOTTOM_INPUT_SLOT_Y));
        addSlot(new OutputSlot(blockEntity.getInventory(), BrickBlastFurnaceBlockEntity.OUTPUT_SLOT, OUTPUT_SLOT_X, OUTPUT_SLOT_Y));
        addPlayerInventory(playerInventory);
        addDataSlots(data);
    }

    public int getFuelHeat() {
        return data.get(0);
    }

    public int getMaxFuelHeat() {
        return data.get(1);
    }

    public int getProgress() {
        return data.get(2);
    }

    public int getMaxProgress() {
        return data.get(3);
    }

    public boolean isActive() {
        return data.get(4) != 0;
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(ContainerLevelAccess.create(blockEntity.getLevel(), blockEntity.getBlockPos()), player, ModBlocks.BRICK_BLAST_FURNACE.get());
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
            } else {
                boolean movedToMachine = false;

                if (!stack.isEmpty()) {
                    movedToMachine |= moveItemStackTo(stack, BrickBlastFurnaceBlockEntity.TOP_INPUT_SLOT, BrickBlastFurnaceBlockEntity.TOP_INPUT_SLOT + 1, false);
                }

                if (!stack.isEmpty()) {
                    movedToMachine |= moveItemStackTo(stack, BrickBlastFurnaceBlockEntity.BOTTOM_INPUT_SLOT, BrickBlastFurnaceBlockEntity.BOTTOM_INPUT_SLOT + 1, false);
                }

                if (!stack.isEmpty() && BrickBlastFurnaceBlockEntity.isFuel(stack)) {
                    movedToMachine |= moveItemStackTo(stack, BrickBlastFurnaceBlockEntity.FUEL_SLOT, BrickBlastFurnaceBlockEntity.FUEL_SLOT + 1, false);
                }

                if (!movedToMachine) {
                    if (index < MACHINE_SLOT_COUNT + PLAYER_INVENTORY_SLOT_COUNT) {
                        if (!moveItemStackTo(stack, MACHINE_SLOT_COUNT + PLAYER_INVENTORY_SLOT_COUNT, slots.size(), false)) {
                            return ItemStack.EMPTY;
                        }
                    } else if (!moveItemStackTo(stack, MACHINE_SLOT_COUNT, MACHINE_SLOT_COUNT + PLAYER_INVENTORY_SLOT_COUNT, false)) {
                        return ItemStack.EMPTY;
                    }
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

    private static BrickBlastFurnaceBlockEntity getBlockEntity(Inventory playerInventory, RegistryFriendlyByteBuf extraData) {
        BlockEntity blockEntity = playerInventory.player.level().getBlockEntity(extraData.readBlockPos());
        if (blockEntity instanceof BrickBlastFurnaceBlockEntity furnace) {
            return furnace;
        }

        throw new IllegalStateException("Expected brick blast furnace block entity");
    }

    private static final class FuelSlot extends SlotItemHandler {
        private FuelSlot(ItemStackHandler itemHandler, int index, int xPosition, int yPosition) {
            super(itemHandler, index, xPosition, yPosition);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return BrickBlastFurnaceBlockEntity.isFuel(stack);
        }
    }

    private static final class InputSlot extends SlotItemHandler {
        private InputSlot(ItemStackHandler itemHandler, int index, int xPosition, int yPosition) {
            super(itemHandler, index, xPosition, yPosition);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return !stack.isEmpty();
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
