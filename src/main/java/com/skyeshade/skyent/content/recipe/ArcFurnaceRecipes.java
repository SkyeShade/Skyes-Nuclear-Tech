package com.skyeshade.skyent.content.recipe;

import com.skyeshade.skyent.registry.ModRecipes;
import java.util.Comparator;
import java.util.List;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;

public final class ArcFurnaceRecipes {
    private ArcFurnaceRecipes() {
    }

    public static List<RecipeHolder<ArcFurnaceRecipe>> all(Level level, ArcFurnaceMode mode) {
        if (level == null) {
            return List.of();
        }

        Comparator<RecipeHolder<ArcFurnaceRecipe>> comparator = mode == ArcFurnaceMode.ALLOYING
                ? ArcFurnaceRecipe.alloyPriorityComparator()
                : Comparator.comparing(holder -> holder.id().toString());
        return level.getRecipeManager().getAllRecipesFor(ModRecipes.ARC_FURNACE_TYPE.get()).stream()
                .filter(holder -> holder.value().mode() == mode)
                .sorted(comparator)
                .toList();
    }
}
