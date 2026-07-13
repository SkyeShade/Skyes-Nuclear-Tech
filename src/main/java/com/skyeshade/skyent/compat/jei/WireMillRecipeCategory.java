package com.skyeshade.skyent.compat.jei;

import com.skyeshade.skyent.SkyesNuclearTech;
import com.skyeshade.skyent.content.recipe.WireMillRecipes;
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

public final class WireMillRecipeCategory extends ConveyorMachineRecipeCategory<WireMillRecipeCategory.WireMillRecipeDisplay> {
    public static final ResourceLocation UID = ResourceLocation.fromNamespaceAndPath(SkyesNuclearTech.MOD_ID, "wire_mill");
    public static final RecipeType<WireMillRecipeDisplay> RECIPE_TYPE = new RecipeType<>(UID, WireMillRecipeDisplay.class);

    public WireMillRecipeCategory(IGuiHelper guiHelper) {
        super(guiHelper, new ItemStack(ModItems.WIRE_MILL.get()));
    }

    @Override
    public RecipeType<WireMillRecipeDisplay> getRecipeType() {
        return RECIPE_TYPE;
    }

    @Override
    public Component getTitle() {
        return Component.translatable("jei.skyent.wire_mill");
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, WireMillRecipeDisplay recipe, IFocusGroup focuses) {
        addInputSlot(builder, recipe.input());
        addOutputSlot(builder, recipe.output());
        addMachineTooltipSlot(builder);
    }

    @Override
    public void draw(WireMillRecipeDisplay recipe, IRecipeSlotsView recipeSlotsView, GuiGraphics guiGraphics, double mouseX, double mouseY) {
        drawConveyorMachine(guiGraphics);
    }

    public static List<WireMillRecipeDisplay> getAllRecipes() {
        return WireMillRecipes.getAllRecipes()
                .stream()
                .map(recipe -> new WireMillRecipeDisplay(Ingredient.of(recipe.input()), recipe.outputStackForDisplay()))
                .toList();
    }

    public record WireMillRecipeDisplay(Ingredient input, ItemStack output) {
    }
}
