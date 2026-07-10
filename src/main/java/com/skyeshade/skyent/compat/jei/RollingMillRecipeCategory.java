package com.skyeshade.skyent.compat.jei;

import com.skyeshade.skyent.SkyesNuclearTech;
import com.skyeshade.skyent.content.recipe.RollingMillRecipes;
import com.skyeshade.skyent.registry.ModItems;
import java.util.List;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeType;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;

public final class RollingMillRecipeCategory extends ConveyorMachineRecipeCategory<RollingMillRecipeCategory.RollingRecipeDisplay> {
    public static final ResourceLocation UID = ResourceLocation.fromNamespaceAndPath(SkyesNuclearTech.MOD_ID, "rolling_mill");
    public static final RecipeType<RollingRecipeDisplay> RECIPE_TYPE = new RecipeType<>(UID, RollingRecipeDisplay.class);

    public RollingMillRecipeCategory(IGuiHelper guiHelper) {
        super(guiHelper, new ItemStack(ModItems.ROLLING_MILL.get()));
    }

    @Override
    public RecipeType<RollingRecipeDisplay> getRecipeType() {
        return RECIPE_TYPE;
    }

    @Override
    public Component getTitle() {
        return Component.translatable("jei.skyent.rolling_mill");
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, RollingRecipeDisplay recipe, IFocusGroup focuses) {
        addInputSlot(builder, recipe.input());
        addOutputSlot(builder, recipe.output());
        addMachineTooltipSlot(builder);
    }

    @Override
    public void draw(RollingRecipeDisplay recipe, IRecipeSlotsView recipeSlotsView, GuiGraphics guiGraphics, double mouseX, double mouseY) {
        drawConveyorMachine(guiGraphics);
    }

    public static List<RollingRecipeDisplay> getAllRecipes() {
        return RollingMillRecipes.getAllRecipes()
                .stream()
                .map(recipe -> new RollingRecipeDisplay(Ingredient.of(recipe.input()), recipe.outputStack()))
                .toList();
    }

    public record RollingRecipeDisplay(Ingredient input, ItemStack output) {
    }
}
