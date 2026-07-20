package com.skyeshade.skyent.content.recipe;

import com.skyeshade.skyent.registry.ModRecipes;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;

public final class MVAssemblerRecipes {
    private MVAssemblerRecipes() {
    }

    public static List<RecipeHolder<MVAssemblerRecipe>> all(Level level) {
        if (level == null) {
            return List.of();
        }

        return level.getRecipeManager().getAllRecipesFor(ModRecipes.MV_ASSEMBLER_TYPE.get()).stream()
                .sorted(Comparator.comparing(holder -> holder.id().toString()))
                .toList();
    }

    public static Optional<RecipeHolder<MVAssemblerRecipe>> byId(Level level, ResourceLocation id) {
        return all(level).stream()
                .filter(recipe -> recipe.id().equals(id))
                .findFirst();
    }

    public static int indexOf(Level level, ResourceLocation id) {
        List<RecipeHolder<MVAssemblerRecipe>> recipes = all(level);
        for (int index = 0; index < recipes.size(); index++) {
            if (recipes.get(index).id().equals(id)) {
                return index;
            }
        }
        return -1;
    }
}
