package com.skyeshade.skyent.content.menu;

import com.skyeshade.skyent.content.blockentity.ArcFurnaceBlockEntity;
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

public class ArcFurnaceMenu extends AbstractContainerMenu {
    public static final int ELECTRODE_0_X = 73;
    public static final int ELECTRODE_0_Y = 23;
    public static final int ELECTRODE_1_X = 91;
    public static final int ELECTRODE_1_Y = 23;
    public static final int ELECTRODE_2_X = 109;
    public static final int ELECTRODE_2_Y = 23;
    public static final int INPUT_GRID_X = 9;
    public static final int INPUT_GRID_Y = 28;
    public static final int INPUT_COLUMNS = 3;
    public static final int INPUT_ROWS = 4;
    public static final int OUTPUT_SLOT_X = 129;
    public static final int OUTPUT_SLOT_Y = 65;
    public static final int POWER_ITEM_SLOT_X = 183;
    public static final int POWER_ITEM_SLOT_Y = 78;

    private static final int PLAYER_INVENTORY_X = 8;
    private static final int PLAYER_INVENTORY_Y = 125;
    private static final int HOTBAR_Y = 183;
    private static final int SLOT_SIZE = 18;
    private static final int PLAYER_INVENTORY_ROWS = 3;
    private static final int PLAYER_INVENTORY_COLUMNS = 9;
    private static final int MACHINE_SLOT_COUNT = ArcFurnaceBlockEntity.INVENTORY_SLOT_COUNT;
    private static final int PLAYER_INVENTORY_SLOT_COUNT = 27;
    private static final int DATA_COUNT = 9;

    private final ArcFurnaceBlockEntity blockEntity;
    private final ContainerData data;

    public ArcFurnaceMenu(int containerId, Inventory playerInventory, RegistryFriendlyByteBuf extraData) {
        this(containerId, playerInventory, getBlockEntity(playerInventory, extraData), new SimpleContainerData(DATA_COUNT));
    }

    public ArcFurnaceMenu(int containerId, Inventory playerInventory, ArcFurnaceBlockEntity blockEntity, ContainerData data) {
        super(ModMenus.ARC_FURNACE.get(), containerId);
        this.blockEntity = blockEntity;
        this.data = data;

        addSlot(new ElectrodeSlot(blockEntity.getInventory(), ArcFurnaceBlockEntity.ELECTRODE_SLOT_0, ELECTRODE_0_X, ELECTRODE_0_Y));
        addSlot(new ElectrodeSlot(blockEntity.getInventory(), ArcFurnaceBlockEntity.ELECTRODE_SLOT_0 + 1, ELECTRODE_1_X, ELECTRODE_1_Y));
        addSlot(new ElectrodeSlot(blockEntity.getInventory(), ArcFurnaceBlockEntity.ELECTRODE_SLOT_0 + 2, ELECTRODE_2_X, ELECTRODE_2_Y));

        for (int row = 0; row < INPUT_ROWS; row++) {
            for (int column = 0; column < INPUT_COLUMNS; column++) {
                int slot = row * INPUT_COLUMNS + column;
                addSlot(new SlotItemHandler(
                        blockEntity.getInventory(),
                        ArcFurnaceBlockEntity.FIRST_INPUT_SLOT + slot,
                        INPUT_GRID_X + column * SLOT_SIZE,
                        INPUT_GRID_Y + row * SLOT_SIZE
                ));
            }
        }

        addSlot(new OutputSlot(blockEntity.getInventory(), ArcFurnaceBlockEntity.OUTPUT_SLOT, OUTPUT_SLOT_X, OUTPUT_SLOT_Y));
        // TODO: make this accept battery/power items once machine item charging exists.
        addSlot(new DisabledPowerSlot(blockEntity.getInventory(), ArcFurnaceBlockEntity.POWER_ITEM_SLOT, POWER_ITEM_SLOT_X, POWER_ITEM_SLOT_Y));
        addPlayerInventory(playerInventory);
        addDataSlots(data);
    }

    public ArcFurnaceBlockEntity getBlockEntity() {
        return blockEntity;
    }

    public BlockPos getBlockPos() {
        return blockEntity.getBlockPos();
    }

    public int getEnergyStoredRJ() {
        return combine(data.get(0), data.get(1));
    }

    public int getMaxEnergyStoredRJ() {
        return combine(data.get(2), data.get(3));
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

    public boolean isRunning() {
        return data.get(7) != 0;
    }

    public int getModeCode() {
        return data.get(8);
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(ContainerLevelAccess.create(blockEntity.getLevel(), blockEntity.getBlockPos()), player, ModBlocks.ARC_FURNACE.get());
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
            } else if (ArcFurnaceBlockEntity.isValidElectrode(stack)) {
                if (!moveItemStackTo(stack, ArcFurnaceBlockEntity.ELECTRODE_SLOT_0, ArcFurnaceBlockEntity.FIRST_INPUT_SLOT, false)) {
                    return ItemStack.EMPTY;
                }
            } else if (!moveItemStackTo(stack, ArcFurnaceBlockEntity.FIRST_INPUT_SLOT, ArcFurnaceBlockEntity.OUTPUT_SLOT, false)) {
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

    private static ArcFurnaceBlockEntity getBlockEntity(Inventory playerInventory, RegistryFriendlyByteBuf extraData) {
        BlockEntity blockEntity = playerInventory.player.level().getBlockEntity(extraData.readBlockPos());
        if (blockEntity instanceof ArcFurnaceBlockEntity furnace) {
            return furnace;
        }

        throw new IllegalStateException("Expected electric blast furnace block entity");
    }

    private static int combine(int low, int high) {
        return (low & 0xFFFF) | ((high & 0xFFFF) << 16);
    }

    private static final class ElectrodeSlot extends SlotItemHandler {
        private ElectrodeSlot(ItemStackHandler itemHandler, int index, int xPosition, int yPosition) {
            super(itemHandler, index, xPosition, yPosition);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return ArcFurnaceBlockEntity.isValidElectrode(stack);
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

    private static final class DisabledPowerSlot extends SlotItemHandler {
        private DisabledPowerSlot(ItemStackHandler itemHandler, int index, int xPosition, int yPosition) {
            super(itemHandler, index, xPosition, yPosition);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return false;
        }
    }
}
