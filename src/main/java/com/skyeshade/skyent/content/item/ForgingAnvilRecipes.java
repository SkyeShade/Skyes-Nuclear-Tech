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

    public static boolean isForgeablePlateInput(ItemStack input) {
        return getPlateOutput(input).isPresent();
    }
}
