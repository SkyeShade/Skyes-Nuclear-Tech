package com.skyeshade.skyent.content.recipe;

import com.skyeshade.skyent.registry.ModRecipes;
import java.util.Comparator;
import java.util.List;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;

public final class ElectricBlastFurnaceRecipes {
    private ElectricBlastFurnaceRecipes() {
    }

    public static List<RecipeHolder<ElectricBlastFurnaceRecipe>> all(Level level, ElectricBlastFurnaceMode mode) {
        if (level == null) {
            return List.of();
        }

        Comparator<RecipeHolder<ElectricBlastFurnaceRecipe>> comparator = mode == ElectricBlastFurnaceMode.ALLOYING
                ? ElectricBlastFurnaceRecipe.alloyPriorityComparator()
                : Comparator.comparing(holder -> holder.id().toString());
        return level.getRecipeManager().getAllRecipesFor(ModRecipes.ELECTRIC_BLAST_FURNACE_TYPE.get()).stream()
                .filter(holder -> holder.value().mode() == mode)
                .sorted(comparator)
                .toList();
    }
}
