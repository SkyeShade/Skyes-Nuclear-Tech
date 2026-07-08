package com.skyeshade.skyent.content.menu;

import com.skyeshade.skyent.content.blockentity.LVCrusherBlockEntity;
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

public class LVCrusherMenu extends AbstractContainerMenu {
    public static final int INPUT_SLOT_X = 46;
    public static final int INPUT_SLOT_Y = 26;
    public static final int OUTPUT_SLOT_X = 102;
    public static final int OUTPUT_SLOT_Y = 26;
    public static final int SECONDARY_OUTPUT_SLOT_X = 125;
    public static final int SECONDARY_OUTPUT_SLOT_Y = 26;
    private static final int PLAYER_INVENTORY_X = 8;
    private static final int PLAYER_INVENTORY_Y = 84;
    private static final int HOTBAR_Y = 142;
    private static final int SLOT_SIZE = 18;
    private static final int PLAYER_INVENTORY_ROWS = 3;
    private static final int PLAYER_INVENTORY_COLUMNS = 9;
    private static final int MACHINE_SLOT_COUNT = 3;
    private static final int PLAYER_INVENTORY_SLOT_COUNT = 27;
    private static final int DATA_COUNT = 7;

    private final LVCrusherBlockEntity blockEntity;
    private final ContainerData data;

    public LVCrusherMenu(int containerId, Inventory playerInventory, RegistryFriendlyByteBuf extraData) {
        this(containerId, playerInventory, getBlockEntity(playerInventory, extraData), new SimpleContainerData(DATA_COUNT));
    }

    public LVCrusherMenu(int containerId, Inventory playerInventory, LVCrusherBlockEntity blockEntity, ContainerData data) {
        super(ModMenus.LV_CRUSHER.get(), containerId);
        this.blockEntity = blockEntity;
        this.data = data;

        addSlot(new InputSlot(blockEntity, blockEntity.getInventory(), LVCrusherBlockEntity.INPUT_SLOT, INPUT_SLOT_X, INPUT_SLOT_Y));
        addSlot(new OutputSlot(blockEntity.getInventory(), LVCrusherBlockEntity.OUTPUT_SLOT, OUTPUT_SLOT_X, OUTPUT_SLOT_Y));
        addSlot(new OutputSlot(blockEntity.getInventory(), LVCrusherBlockEntity.SECONDARY_OUTPUT_SLOT, SECONDARY_OUTPUT_SLOT_X, SECONDARY_OUTPUT_SLOT_Y));
        addPlayerInventory(playerInventory);
        addDataSlots(data);
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

    public boolean isActive() {
        return getCurrentEnergyUsage() > 0;
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(ContainerLevelAccess.create(blockEntity.getLevel(), blockEntity.getBlockPos()), player, ModBlocks.LV_CRUSHER.get());
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
            } else if (blockEntity.isCrushable(stack)) {
                if (!moveItemStackTo(stack, LVCrusherBlockEntity.INPUT_SLOT, LVCrusherBlockEntity.INPUT_SLOT + 1, false)) {
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

    private static LVCrusherBlockEntity getBlockEntity(Inventory playerInventory, RegistryFriendlyByteBuf extraData) {
        BlockEntity blockEntity = playerInventory.player.level().getBlockEntity(extraData.readBlockPos());
        if (blockEntity instanceof LVCrusherBlockEntity crusher) {
            return crusher;
        }

        throw new IllegalStateException("Expected LV crusher block entity");
    }

    private static final class InputSlot extends SlotItemHandler {
        private final LVCrusherBlockEntity blockEntity;

        private InputSlot(LVCrusherBlockEntity blockEntity, ItemStackHandler itemHandler, int index, int xPosition, int yPosition) {
            super(itemHandler, index, xPosition, yPosition);
            this.blockEntity = blockEntity;
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return blockEntity.isCrushable(stack);
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
