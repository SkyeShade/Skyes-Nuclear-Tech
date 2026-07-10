package com.skyeshade.skyent.content.item;

import com.skyeshade.skyent.registry.ModBlocks;
import com.skyeshade.skyent.registry.ModItems;
import java.util.List;
import java.util.Optional;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Blocks;

public final class LVCrusherRecipes {
    private static final List<Entry> RECIPES = List.of(
            rawWithSecondary(Items.RAW_IRON, ModItems.IRON_POWDER.get(), ModItems.NICKEL_POWDER.get(), 0.10D),
            raw(Items.RAW_GOLD, ModItems.GOLD_POWDER.get()),
            rawWithSecondary(Items.RAW_COPPER, ModItems.COPPER_POWDER.get(), ModItems.COBALT_POWDER.get(), 0.10D),
            raw(ModItems.RAW_COBALT.get(), ModItems.COBALT_POWDER.get()),
            raw(ModItems.RAW_NICKEL.get(), ModItems.NICKEL_POWDER.get()),
            raw(ModItems.RAW_LEAD.get(), ModItems.LEAD_POWDER.get()),
            raw(ModItems.RAW_TUNGSTEN.get(), ModItems.TUNGSTEN_POWDER.get()),
            raw(ModItems.RAW_URANIUM.get(), ModItems.URANIUM_POWDER.get()),
            raw(ModItems.RAW_TITANIUM.get(), ModItems.TITANIUM_POWDER.get()),
            raw(ModItems.RAW_ALUMINUM.get(), ModItems.ALUMINUM_POWDER.get()),
            siltWithSecondary(ModItems.SILT.get(), Blocks.SAND, ModItems.SMALL_TIN_POWDER.get()),

            ingot(Items.IRON_INGOT, ModItems.IRON_POWDER.get()),
            ingot(Items.GOLD_INGOT, ModItems.GOLD_POWDER.get()),
            ingot(Items.COPPER_INGOT, ModItems.COPPER_POWDER.get()),
            ingot(ModItems.COBALT_INGOT.get(), ModItems.COBALT_POWDER.get()),
            ingot(ModItems.NICKEL_INGOT.get(), ModItems.NICKEL_POWDER.get()),
            ingot(ModItems.LEAD_INGOT.get(), ModItems.LEAD_POWDER.get()),
            ingot(ModItems.TUNGSTEN_INGOT.get(), ModItems.TUNGSTEN_POWDER.get()),
            ingot(ModItems.URANIUM_INGOT.get(), ModItems.URANIUM_POWDER.get()),
            ingot(ModItems.TITANIUM_INGOT.get(), ModItems.TITANIUM_POWDER.get()),
            ingot(ModItems.ALUMINUM_INGOT.get(), ModItems.ALUMINUM_POWDER.get()),
            ingot(ModItems.STEEL_INGOT.get(), ModItems.STEEL_POWDER.get()),

            plate(ModItems.IRON_PLATE.get(), ModItems.IRON_POWDER.get()),
            plate(ModItems.GOLD_PLATE.get(), ModItems.GOLD_POWDER.get()),
            plate(ModItems.COPPER_PLATE.get(), ModItems.COPPER_POWDER.get()),
            plate(ModItems.COBALT_PLATE.get(), ModItems.COBALT_POWDER.get()),
            plate(ModItems.NICKEL_PLATE.get(), ModItems.NICKEL_POWDER.get()),
            plate(ModItems.LEAD_PLATE.get(), ModItems.LEAD_POWDER.get()),
            plate(ModItems.TITANIUM_PLATE.get(), ModItems.TITANIUM_POWDER.get()),
            plate(ModItems.ALUMINUM_PLATE.get(), ModItems.ALUMINUM_POWDER.get()),
            plate(ModItems.STEEL_PLATE.get(), ModItems.STEEL_POWDER.get()),

            oreWithSecondary(Blocks.IRON_ORE, ModItems.IRON_POWDER.get(), ModItems.NICKEL_POWDER.get(), 0.20D),
            oreWithSecondary(Blocks.DEEPSLATE_IRON_ORE, ModItems.IRON_POWDER.get(), ModItems.NICKEL_POWDER.get(), 0.20D),
            ore(Blocks.GOLD_ORE, ModItems.GOLD_POWDER.get()),
            ore(Blocks.DEEPSLATE_GOLD_ORE, ModItems.GOLD_POWDER.get()),
            oreWithSecondary(Blocks.COPPER_ORE, ModItems.COPPER_POWDER.get(), ModItems.COBALT_POWDER.get(), 0.20D),
            oreWithSecondary(Blocks.DEEPSLATE_COPPER_ORE, ModItems.COPPER_POWDER.get(), ModItems.COBALT_POWDER.get(), 0.20D),
            ore(ModBlocks.LEAD_ORE.get(), ModItems.LEAD_POWDER.get()),
            ore(ModBlocks.TUNGSTEN_ORE.get(), ModItems.TUNGSTEN_POWDER.get()),
            ore(ModBlocks.DEEPSLATE_TUNGSTEN_ORE.get(), ModItems.TUNGSTEN_POWDER.get()),
            ore(ModBlocks.URANIUM_ORE.get(), ModItems.URANIUM_POWDER.get()),
            ore(ModBlocks.DEEPSLATE_URANIUM_ORE.get(), ModItems.URANIUM_POWDER.get()),
            ore(ModBlocks.TITANIUM_ORE.get(), ModItems.TITANIUM_POWDER.get()),
            ore(ModBlocks.DEEPSLATE_TITANIUM_ORE.get(), ModItems.TITANIUM_POWDER.get()),
            ore(ModBlocks.ALUMINUM_ORE.get(), ModItems.ALUMINUM_POWDER.get()),
            ore(ModBlocks.DEEPSLATE_ALUMINUM_ORE.get(), ModItems.ALUMINUM_POWDER.get())
    );

    private LVCrusherRecipes() {
    }

    public static Optional<CrusherRecipe> getRecipe(ItemStack input) {
        if (input.isEmpty()) {
            return Optional.empty();
        }

        for (Entry entry : RECIPES) {
            if (input.is(entry.input().asItem())) {
                // TODO: when isotope components exist, preserve uranium composition into powder outputs.
                ItemStack primary = entry.output() == null
                        ? ItemStack.EMPTY
                        : new ItemStack(entry.output(), entry.count());
                ItemStack secondary = entry.secondaryOutput() == null
                        ? ItemStack.EMPTY
                        : new ItemStack(entry.secondaryOutput(), entry.secondaryCount());
                return Optional.of(new CrusherRecipe(primary, secondary, entry.secondaryChance()));
            }
        }
        return Optional.empty();
    }

    public static Optional<ItemStack> getResult(ItemStack input) {
        return getRecipe(input).map(CrusherRecipe::primaryOutput);
    }

    public static boolean isCrushable(ItemStack input) {
        return getRecipe(input).isPresent();
    }

    public static List<CrusherRecipeDisplay> getAllRecipes() {
        return RECIPES.stream()
                .map(entry -> new CrusherRecipeDisplay(
                        Ingredient.of(entry.input()),
                        entry.output() == null ? ItemStack.EMPTY : new ItemStack(entry.output(), entry.count()),
                        entry.secondaryOutput() == null ? ItemStack.EMPTY : new ItemStack(entry.secondaryOutput(), entry.secondaryCount()),
                        entry.secondaryChance()
                ))
                .toList();
    }

    private static Entry raw(ItemLike input, ItemLike output) {
        return new Entry(input, output, 2, null, 0, 0.0D);
    }

    private static Entry rawWithSecondary(ItemLike input, ItemLike output, ItemLike secondaryOutput, double secondaryChance) {
        return new Entry(input, output, 2, secondaryOutput, 1, secondaryChance);
    }

    private static Entry ingot(ItemLike input, ItemLike output) {
        return new Entry(input, output, 1, null, 0, 0.0D);
    }

    private static Entry plate(ItemLike input, ItemLike output) {
        return new Entry(input, output, 1, null, 0, 0.0D);
    }

    private static Entry ore(ItemLike input, ItemLike output) {
        return new Entry(input, output, 3, null, 0, 0.0D);
    }

    private static Entry oreWithSecondary(ItemLike input, ItemLike output, ItemLike secondaryOutput, double secondaryChance) {
        return new Entry(input, output, 3, secondaryOutput, 1, secondaryChance);
    }

    private static Entry siltWithSecondary(ItemLike input, ItemLike output, ItemLike secondaryOutput) {
        return new Entry(input, output, 1, secondaryOutput, 2, 1.0D);
    }

    private static Entry secondaryOnly(ItemLike input, ItemLike secondaryOutput, double secondaryChance) {
        return new Entry(input, null, 0, secondaryOutput, 1, secondaryChance);
    }

    public record CrusherRecipe(ItemStack primaryOutput, ItemStack secondaryOutput, double secondaryChance) {
        public boolean hasPrimaryOutput() {
            return !primaryOutput.isEmpty();
        }

        public boolean hasSecondaryOutput() {
            return !secondaryOutput.isEmpty() && secondaryChance > 0.0D;
        }
    }

    public record CrusherRecipeDisplay(Ingredient input, ItemStack primaryOutput, ItemStack secondaryOutput, double secondaryChance) {
        public boolean hasPrimaryOutput() {
            return !primaryOutput.isEmpty();
        }

        public boolean hasSecondaryOutput() {
            return !secondaryOutput.isEmpty() && secondaryChance > 0.0D;
        }
    }

    private record Entry(ItemLike input, ItemLike output, int count, ItemLike secondaryOutput, int secondaryCount, double secondaryChance) {
    }
}
