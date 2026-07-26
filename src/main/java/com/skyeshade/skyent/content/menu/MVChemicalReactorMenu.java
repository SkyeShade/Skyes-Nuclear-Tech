package com.skyeshade.skyent.content.menu;

import com.skyeshade.skyent.content.blockentity.MVChemicalReactorBlockEntity;
import com.skyeshade.skyent.registry.ModBlocks;
import com.skyeshade.skyent.registry.ModMenus;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
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
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.SlotItemHandler;

public class MVChemicalReactorMenu extends AbstractContainerMenu {
    private static final int CONTENT_Y_OFFSET = 3;
    public static final int INPUT_SLOT_X = 40;
    public static final int INPUT_SLOT_Y = 69 + CONTENT_Y_OFFSET;
    public static final int OUTPUT_SLOT_X = 123;
    public static final int OUTPUT_SLOT_Y = 69 + CONTENT_Y_OFFSET;
    public static final int FUTURE_POWER_ITEM_SLOT_X = 186;
    public static final int FUTURE_POWER_ITEM_SLOT_Y = 69 + CONTENT_Y_OFFSET;

    private static final int PLAYER_INVENTORY_X = 40;
    private static final int PLAYER_INVENTORY_Y = 112 + CONTENT_Y_OFFSET;
    private static final int HOTBAR_Y = 170 + CONTENT_Y_OFFSET;
    private static final int SLOT_SIZE = 18;
    private static final int PLAYER_INVENTORY_ROWS = 3;
    private static final int PLAYER_INVENTORY_COLUMNS = 9;
    private static final int MACHINE_SLOT_COUNT = MVChemicalReactorBlockEntity.INVENTORY_SLOT_COUNT;
    private static final int PLAYER_INVENTORY_SLOT_COUNT = 27;
    private static final int DATA_COUNT = 31;

    private final MVChemicalReactorBlockEntity blockEntity;
    private final ContainerData data;

    public MVChemicalReactorMenu(int containerId, Inventory playerInventory, RegistryFriendlyByteBuf extraData) {
        this(containerId, playerInventory, getBlockEntity(playerInventory, extraData), new SimpleContainerData(DATA_COUNT));
    }

    public MVChemicalReactorMenu(int containerId, Inventory playerInventory, MVChemicalReactorBlockEntity blockEntity, ContainerData data) {
        super(ModMenus.MV_CHEMICAL_REACTOR.get(), containerId);
        this.blockEntity = blockEntity;
        this.data = data;

        for (int slot = 0; slot < MVChemicalReactorBlockEntity.INPUT_SLOT_COUNT; slot++) {
            addSlot(new InputSlot(blockEntity.getInventory(), slot, INPUT_SLOT_X + slot * SLOT_SIZE, INPUT_SLOT_Y));
        }
        for (int slot = 0; slot < MVChemicalReactorBlockEntity.OUTPUT_SLOT_COUNT; slot++) {
            addSlot(new OutputSlot(blockEntity.getInventory(), MVChemicalReactorBlockEntity.FIRST_OUTPUT_SLOT + slot, OUTPUT_SLOT_X + slot * SLOT_SIZE, OUTPUT_SLOT_Y));
        }
        // TODO add future power item slot at x=186,y=72 when battery behavior exists.
        addPlayerInventory(playerInventory);
        addDataSlots(data);
    }

    public MVChemicalReactorBlockEntity getBlockEntity() {
        return blockEntity;
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

    public int getFluidAmount(int tankIndex) {
        int base = tankDataBase(tankIndex);
        return base < 0 ? 0 : combine(data.get(base), data.get(base + 1));
    }

    public int getFluidCapacity(int tankIndex) {
        int base = tankDataBase(tankIndex);
        return base < 0 ? 0 : combine(data.get(base + 2), data.get(base + 3));
    }

    public Fluid getFluid(int tankIndex) {
        int base = tankDataBase(tankIndex);
        if (base < 0) {
            return Fluids.EMPTY;
        }
        int id = combine(data.get(base + 4), data.get(base + 5));
        return id <= 0 ? Fluids.EMPTY : BuiltInRegistries.FLUID.byId(id);
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(ContainerLevelAccess.create(blockEntity.getLevel(), blockEntity.getBlockPos()), player, ModBlocks.MV_CHEMICAL_REACTOR.get());
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
            } else if (!moveItemStackTo(stack, MVChemicalReactorBlockEntity.FIRST_INPUT_SLOT, MVChemicalReactorBlockEntity.FIRST_OUTPUT_SLOT, false)) {
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

    private static MVChemicalReactorBlockEntity getBlockEntity(Inventory playerInventory, RegistryFriendlyByteBuf extraData) {
        BlockEntity blockEntity = playerInventory.player.level().getBlockEntity(extraData.readBlockPos());
        if (blockEntity instanceof MVChemicalReactorBlockEntity reactor) {
            return reactor;
        }

        throw new IllegalStateException("Expected MV chemical reactor block entity");
    }

    private static int tankDataBase(int tankIndex) {
        return switch (tankIndex) {
            case 0 -> 7;
            case 1 -> 13;
            case 2 -> 19;
            case 3 -> 25;
            default -> -1;
        };
    }

    private static int combine(int low, int high) {
        return (low & 0xFFFF) | ((high & 0xFFFF) << 16);
    }

    private static final class InputSlot extends SlotItemHandler {
        private InputSlot(ItemStackHandler itemHandler, int index, int xPosition, int yPosition) {
            super(itemHandler, index, xPosition, yPosition);
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
