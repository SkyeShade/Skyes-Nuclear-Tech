package com.skyeshade.skyent.content.menu;

import com.skyeshade.skyent.content.blockentity.MVAssemblerBlockEntity;
import com.skyeshade.skyent.content.recipe.MVAssemblerRecipe;
import com.skyeshade.skyent.content.recipe.MVAssemblerRecipes;
import com.skyeshade.skyent.registry.ModBlocks;
import com.skyeshade.skyent.registry.ModMenus;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.SlotItemHandler;

public class MVAssemblerRecipeSelectMenu extends AbstractContainerMenu {
    public static final int RECIPE_GRID_X = 8;
    public static final int RECIPE_GRID_Y = 17;
    public static final int RECIPE_COLUMNS = 9;
    public static final int RECIPE_ROWS = 3;
    public static final int RECIPE_SLOT_COUNT = RECIPE_COLUMNS * RECIPE_ROWS;
    private static final int SLOT_SIZE = 18;
    private static final int HIDDEN_INPUT_X = -10_000;
    private static final int HIDDEN_INPUT_Y = -10_000;

    private final MVAssemblerBlockEntity blockEntity;
    private final List<RecipeHolder<MVAssemblerRecipe>> recipes;
    private final SimpleContainer recipeDisplay;

    public MVAssemblerRecipeSelectMenu(int containerId, Inventory playerInventory, RegistryFriendlyByteBuf extraData) {
        this(containerId, playerInventory, getBlockEntity(playerInventory, extraData));
    }

    public MVAssemblerRecipeSelectMenu(int containerId, Inventory playerInventory, MVAssemblerBlockEntity blockEntity) {
        super(ModMenus.ASSEMBLER_RECIPE_SELECT.get(), containerId);
        this.blockEntity = blockEntity;
        this.recipes = blockEntity.getLevel() == null ? List.of() : MVAssemblerRecipes.all(blockEntity.getLevel());
        this.recipeDisplay = new SimpleContainer(RECIPE_SLOT_COUNT);

        for (int slot = 0; slot < Math.min(RECIPE_SLOT_COUNT, recipes.size()); slot++) {
            recipeDisplay.setItem(slot, recipes.get(slot).value().result().copy());
        }

        for (int row = 0; row < RECIPE_ROWS; row++) {
            for (int column = 0; column < RECIPE_COLUMNS; column++) {
                int slot = row * RECIPE_COLUMNS + column;
                addSlot(new RecipeSlot(recipeDisplay, slot, RECIPE_GRID_X + column * SLOT_SIZE, RECIPE_GRID_Y + row * SLOT_SIZE));
            }
        }

        ItemStackHandler inventory = blockEntity.getInventory();
        for (int slot = 0; slot < MVAssemblerBlockEntity.INPUT_SLOT_COUNT; slot++) {
            addSlot(new HiddenInputSlot(inventory, slot, HIDDEN_INPUT_X, HIDDEN_INPUT_Y));
        }
    }

    public BlockPos getBlockPos() {
        return blockEntity.getBlockPos();
    }

    public MVAssemblerBlockEntity getBlockEntity() {
        return blockEntity;
    }

    public RecipeHolder<MVAssemblerRecipe> getRecipe(int slot) {
        return slot >= 0 && slot < recipes.size() && slot < RECIPE_SLOT_COUNT ? recipes.get(slot) : null;
    }

    public int countMatchingInput(MVAssemblerRecipe.CountedIngredient countedIngredient) {
        int count = 0;
        for (int slot = RECIPE_SLOT_COUNT; slot < RECIPE_SLOT_COUNT + MVAssemblerBlockEntity.INPUT_SLOT_COUNT; slot++) {
            ItemStack stack = slots.get(slot).getItem();
            if (countedIngredient.ingredient().test(stack)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(ContainerLevelAccess.create(blockEntity.getLevel(), blockEntity.getBlockPos()), player, ModBlocks.MV_ASSEMBLER.get());
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    private static MVAssemblerBlockEntity getBlockEntity(Inventory playerInventory, RegistryFriendlyByteBuf extraData) {
        BlockEntity blockEntity = playerInventory.player.level().getBlockEntity(extraData.readBlockPos());
        if (blockEntity instanceof MVAssemblerBlockEntity assembler) {
            return assembler;
        }

        throw new IllegalStateException("Expected MV assembler block entity");
    }

    private static final class RecipeSlot extends Slot {
        private RecipeSlot(SimpleContainer container, int slot, int x, int y) {
            super(container, slot, x, y);
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

    private static final class HiddenInputSlot extends SlotItemHandler {
        private HiddenInputSlot(ItemStackHandler itemHandler, int index, int xPosition, int yPosition) {
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

        @Override
        public boolean isActive() {
            return false;
        }
    }
}
