package com.skyeshade.skyent.content.item;

import com.skyeshade.skyent.registry.ModBlocks;
import com.skyeshade.skyent.registry.ModItems;
import java.util.List;
import java.util.Optional;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Blocks;

public final class LVCrusherRecipes {
    private static final List<Entry> RECIPES = List.of(
            raw(Items.RAW_IRON, ModItems.IRON_POWDER.get()),
            raw(Items.RAW_GOLD, ModItems.GOLD_POWDER.get()),
            raw(Items.RAW_COPPER, ModItems.COPPER_POWDER.get()),
            raw(ModItems.RAW_LEAD.get(), ModItems.LEAD_POWDER.get()),
            raw(ModItems.RAW_TUNGSTEN.get(), ModItems.TUNGSTEN_POWDER.get()),
            raw(ModItems.RAW_URANIUM.get(), ModItems.URANIUM_POWDER.get()),
            raw(ModItems.RAW_TITANIUM.get(), ModItems.TITANIUM_POWDER.get()),
            raw(ModItems.RAW_ALUMINUM.get(), ModItems.ALUMINUM_POWDER.get()),

            ingot(Items.IRON_INGOT, ModItems.IRON_POWDER.get()),
            ingot(Items.GOLD_INGOT, ModItems.GOLD_POWDER.get()),
            ingot(Items.COPPER_INGOT, ModItems.COPPER_POWDER.get()),
            ingot(ModItems.LEAD_INGOT.get(), ModItems.LEAD_POWDER.get()),
            ingot(ModItems.TUNGSTEN_INGOT.get(), ModItems.TUNGSTEN_POWDER.get()),
            ingot(ModItems.URANIUM_INGOT.get(), ModItems.URANIUM_POWDER.get()),
            ingot(ModItems.TITANIUM_INGOT.get(), ModItems.TITANIUM_POWDER.get()),
            ingot(ModItems.ALUMINUM_INGOT.get(), ModItems.ALUMINUM_POWDER.get()),
            ingot(ModItems.STEEL_INGOT.get(), ModItems.STEEL_POWDER.get()),

            plate(ModItems.IRON_PLATE.get(), ModItems.IRON_POWDER.get()),
            plate(ModItems.GOLD_PLATE.get(), ModItems.GOLD_POWDER.get()),
            plate(ModItems.COPPER_PLATE.get(), ModItems.COPPER_POWDER.get()),
            plate(ModItems.LEAD_PLATE.get(), ModItems.LEAD_POWDER.get()),
            plate(ModItems.TITANIUM_PLATE.get(), ModItems.TITANIUM_POWDER.get()),
            plate(ModItems.ALUMINUM_PLATE.get(), ModItems.ALUMINUM_POWDER.get()),
            plate(ModItems.STEEL_PLATE.get(), ModItems.STEEL_POWDER.get()),

            ore(Blocks.IRON_ORE, ModItems.IRON_POWDER.get()),
            ore(Blocks.DEEPSLATE_IRON_ORE, ModItems.IRON_POWDER.get()),
            ore(Blocks.GOLD_ORE, ModItems.GOLD_POWDER.get()),
            ore(Blocks.DEEPSLATE_GOLD_ORE, ModItems.GOLD_POWDER.get()),
            ore(Blocks.COPPER_ORE, ModItems.COPPER_POWDER.get()),
            ore(Blocks.DEEPSLATE_COPPER_ORE, ModItems.COPPER_POWDER.get()),
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

    public static Optional<ItemStack> getResult(ItemStack input) {
        if (input.isEmpty()) {
            return Optional.empty();
        }

        for (Entry entry : RECIPES) {
            if (input.is(entry.input().asItem())) {
                // TODO: when isotope components exist, preserve uranium composition into powder outputs.
                return Optional.of(new ItemStack(entry.output(), entry.count()));
            }
        }
        return Optional.empty();
    }

    public static boolean isCrushable(ItemStack input) {
        return getResult(input).isPresent();
    }

    private static Entry raw(ItemLike input, ItemLike output) {
        return new Entry(input, output, 2);
    }

    private static Entry ingot(ItemLike input, ItemLike output) {
        return new Entry(input, output, 1);
    }

    private static Entry plate(ItemLike input, ItemLike output) {
        return new Entry(input, output, 1);
    }

    private static Entry ore(ItemLike input, ItemLike output) {
        return new Entry(input, output, 3);
    }

    private record Entry(ItemLike input, ItemLike output, int count) {
    }
}
