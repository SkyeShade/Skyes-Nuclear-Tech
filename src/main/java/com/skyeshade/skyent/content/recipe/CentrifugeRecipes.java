package com.skyeshade.skyent.content.recipe;

import com.skyeshade.skyent.registry.ModRecipes;
import java.util.Comparator;
import java.util.List;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;

public final class CentrifugeRecipes {
    private CentrifugeRecipes() {
    }

    public static List<RecipeHolder<CentrifugeRecipe>> all(Level level) {
        if (level == null) {
            return List.of();
        }

        return level.getRecipeManager().getAllRecipesFor(ModRecipes.CENTRIFUGE_TYPE.get()).stream()
                .sorted(Comparator.comparing(holder -> holder.id().toString()))
                .toList();
    }
}
