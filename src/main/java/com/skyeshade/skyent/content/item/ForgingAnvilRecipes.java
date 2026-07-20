package com.skyeshade.skyent.content.item;

import com.skyeshade.skyent.registry.ModItems;
import java.util.List;
import java.util.Optional;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ItemLike;

public final class ForgingAnvilRecipes {
    private static final List<ForgingRecipe> PLATE_RECIPES = List.of(
            forgeReadyRecipe(Items.IRON_INGOT, ModItems.IRON_PLATE.get()),
            forgeReadyRecipe(Items.COPPER_INGOT, ModItems.COPPER_PLATE.get()),
            forgeReadyRecipe(ModItems.COBALT_INGOT.get(), ModItems.COBALT_PLATE.get()),
            forgeReadyRecipe(ModItems.NICKEL_INGOT.get(), ModItems.NICKEL_PLATE.get()),
            forgeReadyRecipe(ModItems.COBALT_BRONZE_INGOT.get(), ModItems.COBALT_BRONZE_PLATE.get()),
            forgeReadyRecipe(ModItems.CUPRONICKEL_INGOT.get(), ModItems.CUPRONICKEL_PLATE.get()),
            forgeReadyRecipe(Items.GOLD_INGOT, ModItems.GOLD_PLATE.get()),
            forgeReadyRecipe(ModItems.STEEL_INGOT.get(), ModItems.STEEL_PLATE.get()),
            forgeReadyRecipe(ModItems.ALUMINUM_INGOT.get(), ModItems.ALUMINUM_PLATE.get()),
            forgeReadyRecipe(ModItems.TITANIUM_INGOT.get(), ModItems.TITANIUM_PLATE.get()),
            forgeReadyRecipe(ModItems.LEAD_INGOT.get(), ModItems.LEAD_PLATE.get())
    );
    private static final List<ForgingRecipe> POWDER_RECIPES = List.of(
            recipe(Items.RAW_IRON, ModItems.IRON_POWDER.get()),
            recipe(Items.RAW_GOLD, ModItems.GOLD_POWDER.get()),
            recipe(Items.RAW_COPPER, ModItems.COPPER_POWDER.get()),
            recipe(ModItems.RAW_COBALT.get(), ModItems.COBALT_POWDER.get()),
            recipe(ModItems.RAW_NICKEL.get(), ModItems.NICKEL_POWDER.get()),
            recipe(ModItems.RAW_LEAD.get(), ModItems.LEAD_POWDER.get()),
            recipe(ModItems.RAW_TUNGSTEN.get(), ModItems.TUNGSTEN_POWDER.get()),
            recipe(ModItems.RAW_URANIUM.get(), ModItems.URANIUM_POWDER.get()),
            recipe(ModItems.RAW_TITANIUM.get(), ModItems.TITANIUM_POWDER.get()),
            recipe(ModItems.RAW_ALUMINUM.get(), ModItems.ALUMINUM_POWDER.get())
    );
    private static final List<ForgingRecipe> COLD_BOLT_RECIPES = List.of(
            recipe(Items.IRON_INGOT, ModItems.IRON_BOLT.get()),
            recipe(Items.COPPER_INGOT, ModItems.COPPER_BOLT.get())
    );
    private static final List<ForgingRecipe> ALL_RECIPES = java.util.stream.Stream.of(
                    PLATE_RECIPES,
                    POWDER_RECIPES,
                    COLD_BOLT_RECIPES
            )
            .flatMap(List::stream)
            .toList();

    private ForgingAnvilRecipes() {
    }

    public static Optional<ItemStack> getPlateOutput(ItemStack input) {
        Item item = HotMetalItems.getLookupItem(input.getItem());
        return findOutput(PLATE_RECIPES, item);
    }

    public static Optional<ItemStack> getPowderOutput(ItemStack input) {
        return findOutput(POWDER_RECIPES, input.getItem());
    }

    public static Optional<ItemStack> getColdBoltOutput(ItemStack input) {
        return findOutput(COLD_BOLT_RECIPES, input.getItem());
    }

    public static List<ForgingRecipe> getAllRecipes() {
        return ALL_RECIPES;
    }

    public static boolean isForgeablePlateInput(ItemStack input) {
        return getPlateOutput(input).isPresent();
    }

    public static boolean isColdBoltInput(ItemStack input) {
        return getColdBoltOutput(input).isPresent();
    }

    public static boolean isPowderInput(ItemStack input) {
        return getPowderOutput(input).isPresent();
    }

    public static boolean isAnvilInput(ItemStack input) {
        return isForgeablePlateInput(input) || isPowderInput(input) || isColdBoltInput(input);
    }

    private static Optional<ItemStack> findOutput(List<ForgingRecipe> recipes, Item item) {
        for (ForgingRecipe recipe : recipes) {
            if (recipe.input() == item) {
                return Optional.of(recipe.outputStack());
            }
        }
        return Optional.empty();
    }

    private static ForgingRecipe recipe(ItemLike input, ItemLike output) {
        return new ForgingRecipe(input.asItem(), output.asItem(), false);
    }

    private static ForgingRecipe forgeReadyRecipe(ItemLike input, ItemLike output) {
        return new ForgingRecipe(input.asItem(), output.asItem(), true);
    }

    public record ForgingRecipe(Item input, Item outputItem, boolean requiresForgeReadyInput) {
        public ItemStack inputStack() {
            return new ItemStack(input);
        }

        public ItemStack displayInputStack() {
            ItemStack stack = inputStack();
            return requiresForgeReadyInput ? HotMetalItems.toHotVariant(stack) : stack;
        }

        public ItemStack outputStack() {
            return new ItemStack(outputItem);
        }
    }
}
