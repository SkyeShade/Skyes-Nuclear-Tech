package com.skyeshade.skyent.content.menu;

import com.skyeshade.skyent.content.blockentity.CentrifugeBlockEntity;
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

public class CentrifugeMenu extends AbstractContainerMenu {
    public static final int INPUT_SLOT_X = 9;
    public static final int INPUT_SLOT_Y = 63;
    public static final int OUTPUT_SLOT_X = 117;
    public static final int OUTPUT_SLOT_Y = 63;
    public static final int FUTURE_BATTERY_SLOT_X = 184;
    public static final int FUTURE_BATTERY_SLOT_Y = 90;

    private static final int PLAYER_INVENTORY_X = 9;
    private static final int PLAYER_INVENTORY_Y = 142;
    private static final int HOTBAR_Y = 200;
    private static final int SLOT_SIZE = 18;
    private static final int GRID_SIZE = 3;
    private static final int PLAYER_INVENTORY_ROWS = 3;
    private static final int PLAYER_INVENTORY_COLUMNS = 9;
    private static final int MACHINE_SLOT_COUNT = CentrifugeBlockEntity.INVENTORY_SLOT_COUNT;
    private static final int PLAYER_INVENTORY_SLOT_COUNT = 27;
    private static final int DATA_COUNT = 19;

    private final CentrifugeBlockEntity blockEntity;
    private final ContainerData data;

    public CentrifugeMenu(int containerId, Inventory playerInventory, RegistryFriendlyByteBuf extraData) {
        this(containerId, playerInventory, getBlockEntity(playerInventory, extraData), new SimpleContainerData(DATA_COUNT));
    }

    public CentrifugeMenu(int containerId, Inventory playerInventory, CentrifugeBlockEntity blockEntity, ContainerData data) {
        super(ModMenus.CENTRIFUGE.get(), containerId);
        this.blockEntity = blockEntity;
        this.data = data;

        for (int row = 0; row < GRID_SIZE; row++) {
            for (int column = 0; column < GRID_SIZE; column++) {
                int slot = row * GRID_SIZE + column;
                addSlot(new InputSlot(
                        blockEntity.getInventory(),
                        CentrifugeBlockEntity.FIRST_INPUT_SLOT + slot,
                        INPUT_SLOT_X + column * SLOT_SIZE,
                        INPUT_SLOT_Y + row * SLOT_SIZE
                ));
            }
        }

        for (int row = 0; row < GRID_SIZE; row++) {
            for (int column = 0; column < GRID_SIZE; column++) {
                int slot = row * GRID_SIZE + column;
                addSlot(new OutputSlot(
                        blockEntity.getInventory(),
                        CentrifugeBlockEntity.FIRST_OUTPUT_SLOT + slot,
                        OUTPUT_SLOT_X + column * SLOT_SIZE,
                        OUTPUT_SLOT_Y + row * SLOT_SIZE
                ));
            }
        }

        // TODO add future battery/recharge slot at x=184,y=90 when power item support exists.
        addPlayerInventory(playerInventory);
        addDataSlots(data);
    }

    public CentrifugeBlockEntity getBlockEntity() {
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
        return stillValid(ContainerLevelAccess.create(blockEntity.getLevel(), blockEntity.getBlockPos()), player, ModBlocks.CENTRIFUGE.get());
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
            } else if (!moveItemStackTo(stack, CentrifugeBlockEntity.FIRST_INPUT_SLOT, CentrifugeBlockEntity.FIRST_OUTPUT_SLOT, false)) {
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

    private static CentrifugeBlockEntity getBlockEntity(Inventory playerInventory, RegistryFriendlyByteBuf extraData) {
        BlockEntity blockEntity = playerInventory.player.level().getBlockEntity(extraData.readBlockPos());
        if (blockEntity instanceof CentrifugeBlockEntity centrifuge) {
            return centrifuge;
        }

        throw new IllegalStateException("Expected centrifuge block entity");
    }

    private static int tankDataBase(int tankIndex) {
        return switch (tankIndex) {
            case 0 -> 7;
            case 1 -> 13;
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
