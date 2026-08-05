package com.skyeshade.skyent.content.menu;

import com.skyeshade.skyent.content.blockentity.StainlessSteelCrateBlockEntity;
import com.skyeshade.skyent.content.blockentity.SteelCrateBlockEntity;
import com.skyeshade.skyent.registry.ModBlocks;
import com.skyeshade.skyent.registry.ModMenus;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.items.SlotItemHandler;

public class StainlessSteelCrateMenu extends AbstractContainerMenu {
    public static final int CRATE_INVENTORY_X = 8;
    public static final int CRATE_INVENTORY_Y = 18;
    public static final int PLAYER_INVENTORY_X = 8;
    public static final int PLAYER_INVENTORY_Y = 138;
    private static final int HOTBAR_Y = 196;
    private static final int SLOT_SIZE = 18;
    private static final int CRATE_ROWS = 6;
    private static final int INVENTORY_COLUMNS = 9;
    private static final int PLAYER_INVENTORY_ROWS = 3;


    private final StainlessSteelCrateBlockEntity blockEntity;
    private boolean closed;

    public StainlessSteelCrateMenu(int containerId, Inventory playerInventory, RegistryFriendlyByteBuf extraData) {
        this(containerId, playerInventory, getBlockEntity(playerInventory, extraData));
    }

    public StainlessSteelCrateMenu(int containerId, Inventory playerInventory, StainlessSteelCrateBlockEntity blockEntity) {
        super(ModMenus.STAINLESS_STEEL_CRATE.get(), containerId);
        this.blockEntity = blockEntity;

        for (int row = 0; row < CRATE_ROWS; row++) {
            for (int column = 0; column < INVENTORY_COLUMNS; column++) {
                int slot = column + row * INVENTORY_COLUMNS;
                addSlot(new SlotItemHandler(
                        blockEntity.getInventory(),
                        slot,
                        CRATE_INVENTORY_X + column * SLOT_SIZE,
                        CRATE_INVENTORY_Y + row * SLOT_SIZE
                ));
            }
        }

        addPlayerInventory(playerInventory);
        blockEntity.startOpen(playerInventory.player);
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(ContainerLevelAccess.create(blockEntity.getLevel(), blockEntity.getBlockPos()), player, ModBlocks.STAINLESS_STEEL_CRATE.get());
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack result = ItemStack.EMPTY;
        Slot slot = slots.get(index);

        if (slot.hasItem()) {
            ItemStack stack = slot.getItem();
            result = stack.copy();

            if (index < StainlessSteelCrateBlockEntity.SLOT_COUNT) {
                if (!moveItemStackTo(stack, StainlessSteelCrateBlockEntity.SLOT_COUNT, slots.size(), true)) {
                    return ItemStack.EMPTY;
                }
            } else if (!moveItemStackTo(stack, 0, StainlessSteelCrateBlockEntity.SLOT_COUNT, false)) {
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

    @Override
    public void removed(Player player) {
        super.removed(player);
        if (!closed) {
            closed = true;
            blockEntity.stopOpen(player);
        }
    }

    private void addPlayerInventory(Inventory playerInventory) {
        for (int row = 0; row < PLAYER_INVENTORY_ROWS; row++) {
            for (int column = 0; column < INVENTORY_COLUMNS; column++) {
                addSlot(new Slot(
                        playerInventory,
                        column + row * INVENTORY_COLUMNS + INVENTORY_COLUMNS,
                        PLAYER_INVENTORY_X + column * SLOT_SIZE,
                        PLAYER_INVENTORY_Y + row * SLOT_SIZE
                ));
            }
        }

        for (int column = 0; column < INVENTORY_COLUMNS; column++) {
            addSlot(new Slot(playerInventory, column, PLAYER_INVENTORY_X + column * SLOT_SIZE, HOTBAR_Y));
        }
    }

    private static StainlessSteelCrateBlockEntity getBlockEntity(Inventory playerInventory, RegistryFriendlyByteBuf extraData) {
        BlockEntity blockEntity = playerInventory.player.level().getBlockEntity(extraData.readBlockPos());
        if (blockEntity instanceof StainlessSteelCrateBlockEntity crate) {
            return crate;
        }

        throw new IllegalStateException("Expected stainless steel crate block entity");
    }
}
