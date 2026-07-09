package com.skyeshade.skyent.content.item;

import com.skyeshade.skyent.registry.ModItems;
import java.util.Optional;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class ForgingAnvilRecipes {
    private ForgingAnvilRecipes() {
    }

    public static Optional<ItemStack> getPlateOutput(ItemStack input) {
        if (input.is(Items.IRON_INGOT)) {
            return Optional.of(new ItemStack(ModItems.IRON_PLATE.get()));
        }
        if (input.is(Items.COPPER_INGOT)) {
            return Optional.of(new ItemStack(ModItems.COPPER_PLATE.get()));
        }
        if (input.is(ModItems.COBALT_INGOT.get())) {
            return Optional.of(new ItemStack(ModItems.COBALT_PLATE.get()));
        }
        if (input.is(ModItems.NICKEL_INGOT.get())) {
            return Optional.of(new ItemStack(ModItems.NICKEL_PLATE.get()));
        }
        if (input.is(ModItems.COBALT_BRONZE_INGOT.get())) {
            return Optional.of(new ItemStack(ModItems.COBALT_BRONZE_PLATE.get()));
        }
        if (input.is(Items.GOLD_INGOT)) {
            return Optional.of(new ItemStack(ModItems.GOLD_PLATE.get()));
        }
        if (input.is(ModItems.STEEL_INGOT.get())) {
            return Optional.of(new ItemStack(ModItems.STEEL_PLATE.get()));
        }
        if (input.is(ModItems.ALUMINUM_INGOT.get())) {
            return Optional.of(new ItemStack(ModItems.ALUMINUM_PLATE.get()));
        }
        if (input.is(ModItems.TITANIUM_INGOT.get())) {
            return Optional.of(new ItemStack(ModItems.TITANIUM_PLATE.get()));
        }
        if (input.is(ModItems.LEAD_INGOT.get())) {
            return Optional.of(new ItemStack(ModItems.LEAD_PLATE.get()));
        }
        return Optional.empty();
    }

    public static Optional<ItemStack> getPowderOutput(ItemStack input) {
        if (input.is(Items.RAW_IRON)) {
            return Optional.of(new ItemStack(ModItems.IRON_POWDER.get()));
        }
        if (input.is(Items.RAW_GOLD)) {
            return Optional.of(new ItemStack(ModItems.GOLD_POWDER.get()));
        }
        if (input.is(Items.RAW_COPPER)) {
            return Optional.of(new ItemStack(ModItems.COPPER_POWDER.get()));
        }
        if (input.is(ModItems.RAW_COBALT.get())) {
            return Optional.of(new ItemStack(ModItems.COBALT_POWDER.get()));
        }
        if (input.is(ModItems.RAW_NICKEL.get())) {
            return Optional.of(new ItemStack(ModItems.NICKEL_POWDER.get()));
        }
        if (input.is(ModItems.RAW_LEAD.get())) {
            return Optional.of(new ItemStack(ModItems.LEAD_POWDER.get()));
        }
        if (input.is(ModItems.RAW_TUNGSTEN.get())) {
            return Optional.of(new ItemStack(ModItems.TUNGSTEN_POWDER.get()));
        }
        if (input.is(ModItems.RAW_URANIUM.get())) {
            return Optional.of(new ItemStack(ModItems.URANIUM_POWDER.get()));
        }
        if (input.is(ModItems.RAW_TITANIUM.get())) {
            return Optional.of(new ItemStack(ModItems.TITANIUM_POWDER.get()));
        }
        if (input.is(ModItems.RAW_ALUMINUM.get())) {
            return Optional.of(new ItemStack(ModItems.ALUMINUM_POWDER.get()));
        }
        return Optional.empty();
    }

    public static Optional<ItemStack> getColdBoltOutput(ItemStack input) {
        if (input.is(Items.IRON_INGOT)) {
            return Optional.of(new ItemStack(ModItems.IRON_BOLT.get()));
        }
        if (input.is(Items.COPPER_INGOT)) {
            return Optional.of(new ItemStack(ModItems.COPPER_BOLT.get()));
        }
        return Optional.empty();
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
}
