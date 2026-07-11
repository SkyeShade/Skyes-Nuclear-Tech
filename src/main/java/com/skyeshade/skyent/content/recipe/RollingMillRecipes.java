package com.skyeshade.skyent.content.recipe;

import com.skyeshade.skyent.registry.ModItems;
import java.util.List;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;

public final class RollingMillRecipes {
    private static final List<RollingRecipe> RECIPES = List.of(
            recipe(ModItems.HOT_IRON_INGOT.get(), ModItems.IRON_ROD.get()),
            recipe(ModItems.HOT_COPPER_INGOT.get(), ModItems.COPPER_ROD.get()),
            recipe(ModItems.HOT_STEEL_INGOT.get(), ModItems.STEEL_ROD.get()),
            recipe(ModItems.HOT_ALUMINUM_INGOT.get(), ModItems.ALUMINUM_ROD.get()),
            recipe(ModItems.HOT_TITANIUM_INGOT.get(), ModItems.TITANIUM_ROD.get()),
            recipe(ModItems.HOT_TUNGSTEN_INGOT.get(), ModItems.TUNGSTEN_ROD.get()),
            recipe(ModItems.HOT_COBALT_INGOT.get(), ModItems.COBALT_ROD.get()),
            recipe(ModItems.HOT_NICKEL_INGOT.get(), ModItems.NICKEL_ROD.get())
    );

    private RollingMillRecipes() {
    }

    public static List<RollingRecipe> getAllRecipes() {
        return RECIPES;
    }

    public static ItemStack getRollingResult(ItemStack input) {
        if (input.isEmpty()) {
            return ItemStack.EMPTY;
        }
        for (RollingRecipe recipe : RECIPES) {
            if (input.is(recipe.input())) {
                return new ItemStack(recipe.output(), input.getCount());
            }
        }
        return ItemStack.EMPTY;
    }

    public static boolean isRollingInput(ItemStack stack) {
        return !getRollingResult(stack).isEmpty();
    }

    public static boolean isRollingOutput(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        for (RollingRecipe recipe : RECIPES) {
            if (stack.is(recipe.output())) {
                return true;
            }
        }
        return false;
    }

    private static RollingRecipe recipe(ItemLike input, ItemLike output) {
        return new RollingRecipe(input.asItem(), output.asItem());
    }

    public record RollingRecipe(Item input, Item output) {
        public ItemStack inputStack() {
            return new ItemStack(input);
        }

        public ItemStack outputStack() {
            return new ItemStack(output);
        }
    }
}
