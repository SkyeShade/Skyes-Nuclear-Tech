package com.skyeshade.skyent.content.menu;

import com.skyeshade.skyent.content.blockentity.LVElectricPumpBlockEntity;
import com.skyeshade.skyent.content.item.SteelFluidBarrelItem;
import com.skyeshade.skyent.registry.ModBlocks;
import com.skyeshade.skyent.registry.ModMenus;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.server.level.ServerPlayer;
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

public class LVElectricPumpMenu extends AbstractContainerMenu {
    public static final int DUMP_INPUT_SLOT_X = 49;
    public static final int DUMP_INPUT_SLOT_Y = 9;
    public static final int DUMP_OUTPUT_SLOT_X = 49;
    public static final int DUMP_OUTPUT_SLOT_Y = 41;
    public static final int FILL_INPUT_SLOT_X = 110;
    public static final int FILL_INPUT_SLOT_Y = 9;
    public static final int FILL_OUTPUT_SLOT_X = 110;
    public static final int FILL_OUTPUT_SLOT_Y = 41;

    private static final int PLAYER_INVENTORY_X = 8;
    private static final int PLAYER_INVENTORY_Y = 84;
    private static final int HOTBAR_Y = 142;
    private static final int SLOT_SIZE = 18;
    private static final int PLAYER_INVENTORY_ROWS = 3;
    private static final int PLAYER_INVENTORY_COLUMNS = 9;
    private static final int MACHINE_SLOT_COUNT = 4;
    private static final int PLAYER_INVENTORY_SLOT_COUNT = 27;
    private static final int DATA_COUNT = 13;

    private final LVElectricPumpBlockEntity blockEntity;
    private final ContainerData data;

    public LVElectricPumpMenu(int containerId, Inventory playerInventory, RegistryFriendlyByteBuf extraData) {
        this(containerId, playerInventory, getBlockEntity(playerInventory, extraData), new SimpleContainerData(DATA_COUNT));
    }

    public LVElectricPumpMenu(int containerId, Inventory playerInventory, LVElectricPumpBlockEntity blockEntity, ContainerData data) {
        super(ModMenus.LV_ELECTRIC_PUMP.get(), containerId);
        this.blockEntity = blockEntity;
        this.data = data;
        blockEntity.addViewer(playerInventory.player);

        addSlot(new DumpInputSlot(blockEntity, blockEntity.getInventory(), LVElectricPumpBlockEntity.DUMP_INPUT_SLOT, DUMP_INPUT_SLOT_X, DUMP_INPUT_SLOT_Y));
        addSlot(new OutputSlot(blockEntity.getInventory(), LVElectricPumpBlockEntity.DUMP_OUTPUT_SLOT, DUMP_OUTPUT_SLOT_X, DUMP_OUTPUT_SLOT_Y));
        addSlot(new FillInputSlot(blockEntity, blockEntity.getInventory(), LVElectricPumpBlockEntity.FILL_INPUT_SLOT, FILL_INPUT_SLOT_X, FILL_INPUT_SLOT_Y));
        addSlot(new OutputSlot(blockEntity.getInventory(), LVElectricPumpBlockEntity.FILL_OUTPUT_SLOT, FILL_OUTPUT_SLOT_X, FILL_OUTPUT_SLOT_Y));
        addPlayerInventory(playerInventory);
        addDataSlots(data);
    }

    public int getEnergyStoredRJ() {
        return combine(data.get(0), data.get(1));
    }

    public int getMaxEnergyStoredRJ() {
        return combine(data.get(2), data.get(3));
    }

    public int getCurrentEnergyUsage() {
        return data.get(4);
    }

    public int getFluidAmount() {
        return combine(data.get(5), data.get(6));
    }

    public int getFluidCapacity() {
        return combine(data.get(7), data.get(8));
    }

    public Fluid getFluid() {
        int id = combine(data.get(9), data.get(10));
        return id <= 0 ? Fluids.EMPTY : BuiltInRegistries.FLUID.byId(id);
    }

    public boolean isActive() {
        return data.get(11) != 0;
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(ContainerLevelAccess.create(blockEntity.getLevel(), blockEntity.getBlockPos()), player, ModBlocks.LV_ELECTRIC_PUMP.get());
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        blockEntity.removeViewer(player);
    }

    public LVElectricPumpBlockEntity getBlockEntity() {
        return blockEntity;
    }

    public void syncHandlerSlot(ServerPlayer player, int handlerSlot, ItemStack stack) {
        int menuSlot = menuSlotForHandlerSlot(handlerSlot);
        player.connection.send(new ClientboundContainerSetSlotPacket(containerId, incrementStateId(), menuSlot, stack.copy()));
    }

    public static int menuSlotForHandlerSlot(int handlerSlot) {
        return handlerSlot;
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
            } else if (blockEntity.canFillContainerFromTank(stack)) {
                if (!moveItemStackTo(stack, LVElectricPumpBlockEntity.FILL_INPUT_SLOT, LVElectricPumpBlockEntity.FILL_INPUT_SLOT + 1, false)) {
                    return ItemStack.EMPTY;
                }
            } else if (LVElectricPumpBlockEntity.isFilledFluidContainer(stack)) {
                if (!moveItemStackTo(stack, LVElectricPumpBlockEntity.DUMP_INPUT_SLOT, LVElectricPumpBlockEntity.DUMP_INPUT_SLOT + 1, false)) {
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

    private static LVElectricPumpBlockEntity getBlockEntity(Inventory playerInventory, RegistryFriendlyByteBuf extraData) {
        BlockEntity blockEntity = playerInventory.player.level().getBlockEntity(extraData.readBlockPos());
        if (blockEntity instanceof LVElectricPumpBlockEntity pump) {
            return pump;
        }

        throw new IllegalStateException("Expected LV electric pump block entity");
    }

    private static int combine(int low, int high) {
        return (low & 0xFFFF) | ((high & 0xFFFF) << 16);
    }

    private static final class DumpInputSlot extends SlotItemHandler {
        private final LVElectricPumpBlockEntity blockEntity;

        private DumpInputSlot(LVElectricPumpBlockEntity blockEntity, ItemStackHandler itemHandler, int index, int xPosition, int yPosition) {
            super(itemHandler, index, xPosition, yPosition);
            this.blockEntity = blockEntity;
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return blockEntity.getInventory().isItemValid(getSlotIndex(), stack);
        }

        @Override
        public int getMaxStackSize() {
            return 16;
        }

        @Override
        public int getMaxStackSize(ItemStack stack) {
            return SteelFluidBarrelItem.isFullBarrel(stack) && LVElectricPumpBlockEntity.isFilledFluidContainer(stack) ? 16 : 1;
        }
    }

    private static final class FillInputSlot extends SlotItemHandler {
        private final LVElectricPumpBlockEntity blockEntity;

        private FillInputSlot(LVElectricPumpBlockEntity blockEntity, ItemStackHandler itemHandler, int index, int xPosition, int yPosition) {
            super(itemHandler, index, xPosition, yPosition);
            this.blockEntity = blockEntity;
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return blockEntity.getInventory().isItemValid(getSlotIndex(), stack);
        }

        @Override
        public int getMaxStackSize() {
            return 16;
        }

        @Override
        public int getMaxStackSize(ItemStack stack) {
            return SteelFluidBarrelItem.isEmptyBarrel(stack) ? 16 : 1;
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
