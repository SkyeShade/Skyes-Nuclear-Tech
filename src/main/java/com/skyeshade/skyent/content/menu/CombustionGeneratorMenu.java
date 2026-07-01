package com.skyeshade.skyent.content.menu;

import com.skyeshade.skyent.content.blockentity.CombustionGeneratorBlockEntity;
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

public class CombustionGeneratorMenu extends AbstractContainerMenu {
    public static final int WATER_INPUT_SLOT_X = 29;
    public static final int WATER_INPUT_SLOT_Y = 9;
    public static final int EMPTY_CONTAINER_SLOT_X = 29;
    public static final int EMPTY_CONTAINER_SLOT_Y = 41;
    public static final int FUEL_SLOT_X = 80;
    public static final int FUEL_SLOT_Y = 41;
    public static final int STEAM_CONTAINER_INPUT_SLOT_X = 131;
    public static final int STEAM_CONTAINER_INPUT_SLOT_Y = 9;
    public static final int STEAM_CONTAINER_OUTPUT_SLOT_X = 131;
    public static final int STEAM_CONTAINER_OUTPUT_SLOT_Y = 41;
    private static final int PLAYER_INVENTORY_X = 8;
    private static final int PLAYER_INVENTORY_Y = 84;
    private static final int HOTBAR_Y = 142;
    private static final int SLOT_SIZE = 18;
    private static final int PLAYER_INVENTORY_ROWS = 3;
    private static final int PLAYER_INVENTORY_COLUMNS = 9;
    private static final int MACHINE_SLOT_COUNT = 5;
    private static final int PLAYER_INVENTORY_SLOT_COUNT = 27;
    private static final int DATA_COUNT = 7;

    private final CombustionGeneratorBlockEntity blockEntity;
    private final ContainerData data;

    public CombustionGeneratorMenu(int containerId, Inventory playerInventory, RegistryFriendlyByteBuf extraData) {
        this(containerId, playerInventory, getBlockEntity(playerInventory, extraData), new SimpleContainerData(DATA_COUNT));
    }

    public CombustionGeneratorMenu(int containerId, Inventory playerInventory, CombustionGeneratorBlockEntity blockEntity, ContainerData data) {
        super(ModMenus.COMBUSTION_GENERATOR.get(), containerId);
        this.blockEntity = blockEntity;
        this.data = data;

        addSlot(new WaterInputSlot(blockEntity.getInventory(), CombustionGeneratorBlockEntity.WATER_INPUT_SLOT, WATER_INPUT_SLOT_X, WATER_INPUT_SLOT_Y));
        addSlot(new OutputSlot(blockEntity.getInventory(), CombustionGeneratorBlockEntity.EMPTY_CONTAINER_SLOT, EMPTY_CONTAINER_SLOT_X, EMPTY_CONTAINER_SLOT_Y));
        addSlot(new FuelSlot(blockEntity.getInventory(), CombustionGeneratorBlockEntity.FUEL_SLOT, FUEL_SLOT_X, FUEL_SLOT_Y));
        addSlot(new FillableContainerSlot(blockEntity.getInventory(), CombustionGeneratorBlockEntity.STEAM_CONTAINER_INPUT_SLOT, STEAM_CONTAINER_INPUT_SLOT_X, STEAM_CONTAINER_INPUT_SLOT_Y));
        addSlot(new OutputSlot(blockEntity.getInventory(), CombustionGeneratorBlockEntity.STEAM_CONTAINER_OUTPUT_SLOT, STEAM_CONTAINER_OUTPUT_SLOT_X, STEAM_CONTAINER_OUTPUT_SLOT_Y));
        addPlayerInventory(playerInventory);
        addDataSlots(data);
    }

    public int getBurnTime() {
        return data.get(0);
    }

    public int getBurnTimeTotal() {
        return data.get(1);
    }

    public boolean isBurning() {
        return getBurnTime() > 0;
    }

    public int getWaterAmount() {
        return data.get(2);
    }

    public int getWaterCapacity() {
        return data.get(3);
    }

    public int getSteamAmount() {
        return data.get(4);
    }

    public int getSteamCapacity() {
        return data.get(5);
    }

    public double getTemperatureC() {
        return data.get(6) / 100.0D;
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(ContainerLevelAccess.create(blockEntity.getLevel(), blockEntity.getBlockPos()), player, ModBlocks.COMBUSTION_GENERATOR.get());
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
            } else if (CombustionGeneratorBlockEntity.isWaterContainer(stack)) {
                if (!moveItemStackTo(stack, CombustionGeneratorBlockEntity.WATER_INPUT_SLOT, CombustionGeneratorBlockEntity.WATER_INPUT_SLOT + 1, false)) {
                    return ItemStack.EMPTY;
                }
            } else if (CombustionGeneratorBlockEntity.isFillableFluidContainer(stack)) {
                if (!moveItemStackTo(stack, CombustionGeneratorBlockEntity.STEAM_CONTAINER_INPUT_SLOT, CombustionGeneratorBlockEntity.STEAM_CONTAINER_INPUT_SLOT + 1, false)) {
                    return ItemStack.EMPTY;
                }
            } else if (CombustionGeneratorBlockEntity.isFuel(stack)) {
                if (!moveItemStackTo(stack, CombustionGeneratorBlockEntity.FUEL_SLOT, CombustionGeneratorBlockEntity.FUEL_SLOT + 1, false)) {
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

    private static CombustionGeneratorBlockEntity getBlockEntity(Inventory playerInventory, RegistryFriendlyByteBuf extraData) {
        BlockEntity blockEntity = playerInventory.player.level().getBlockEntity(extraData.readBlockPos());
        if (blockEntity instanceof CombustionGeneratorBlockEntity boiler) {
            return boiler;
        }

        throw new IllegalStateException("Expected combustion generator block entity");
    }

    private static final class WaterInputSlot extends SlotItemHandler {
        private WaterInputSlot(ItemStackHandler itemHandler, int index, int xPosition, int yPosition) {
            super(itemHandler, index, xPosition, yPosition);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return CombustionGeneratorBlockEntity.isWaterContainer(stack);
        }
    }

    private static final class FillableContainerSlot extends SlotItemHandler {
        private FillableContainerSlot(ItemStackHandler itemHandler, int index, int xPosition, int yPosition) {
            super(itemHandler, index, xPosition, yPosition);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return CombustionGeneratorBlockEntity.isFillableFluidContainer(stack);
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

    private static final class FuelSlot extends SlotItemHandler {
        private FuelSlot(ItemStackHandler itemHandler, int index, int xPosition, int yPosition) {
            super(itemHandler, index, xPosition, yPosition);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return CombustionGeneratorBlockEntity.isFuel(stack);
        }
    }
}
