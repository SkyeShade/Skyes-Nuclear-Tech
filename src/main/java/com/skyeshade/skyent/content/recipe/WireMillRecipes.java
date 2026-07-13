package com.skyeshade.skyent.content.recipe;

import com.skyeshade.skyent.registry.ModItems;
import java.util.List;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;

public final class WireMillRecipes {
    private static final List<WireMillRecipe> RECIPES = List.of(
            recipe(ModItems.HOT_COPPER_ROD.get(), ModItems.COPPER_WIRE.get(), 4),
            recipe(ModItems.HOT_TIN_ROD.get(), ModItems.TIN_WIRE.get(), 4),
            recipe(ModItems.HOT_STEEL_ROD.get(), ModItems.STEEL_WIRE.get(), 4),
            recipe(ModItems.HOT_COBALT_ROD.get(), ModItems.COBALT_WIRE.get(), 4),
            recipe(ModItems.HOT_COBALT_BRONZE_ROD.get(), ModItems.COBALT_BRONZE_WIRE.get(), 4),
            recipe(ModItems.HOT_CUPRONICKEL_ROD.get(), ModItems.CUPRONICKEL_WIRE.get(), 4)
    );

    private WireMillRecipes() {
    }

    public static List<WireMillRecipe> getAllRecipes() {
        return RECIPES;
    }

    public static WireMillRecipe find(ItemStack input) {
        if (input.isEmpty()) {
            return null;
        }
        for (WireMillRecipe recipe : RECIPES) {
            if (input.is(recipe.input())) {
                return recipe;
            }
        }
        return null;
    }

    public static ItemStack getWireResult(ItemStack input) {
        WireMillRecipe recipe = find(input);
        return recipe == null ? ItemStack.EMPTY : recipe.outputStack();
    }

    public static boolean isWireInput(ItemStack input) {
        return find(input) != null;
    }

    private static WireMillRecipe recipe(ItemLike input, ItemLike output, int outputCount) {
        return new WireMillRecipe(input.asItem(), output.asItem(), outputCount);
    }

    public record WireMillRecipe(Item input, Item output, int outputCount) {
        public ItemStack inputStack() {
            return new ItemStack(input);
        }

        public ItemStack outputStack() {
            return new ItemStack(output);
        }

        public ItemStack outputStackForDisplay() {
            return new ItemStack(output, outputCount);
        }
    }
}
