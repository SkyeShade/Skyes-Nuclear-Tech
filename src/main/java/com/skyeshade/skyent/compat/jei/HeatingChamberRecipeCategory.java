package com.skyeshade.skyent.compat.jei;

import com.skyeshade.skyent.SkyesNuclearTech;
import com.skyeshade.skyent.content.item.HotMetalItems;
import com.skyeshade.skyent.registry.ModItems;
import java.util.ArrayList;
import java.util.List;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeType;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;

public final class HeatingChamberRecipeCategory extends ConveyorMachineRecipeCategory<HeatingChamberRecipeCategory.HeatingRecipeDisplay> {
    public static final ResourceLocation UID = ResourceLocation.fromNamespaceAndPath(SkyesNuclearTech.MOD_ID, "heating_chamber");
    public static final RecipeType<HeatingRecipeDisplay> RECIPE_TYPE = new RecipeType<>(UID, HeatingRecipeDisplay.class);

    public HeatingChamberRecipeCategory(IGuiHelper guiHelper) {
        super(guiHelper, new ItemStack(ModItems.HEATING_CHAMBER.get()));
    }

    @Override
    public RecipeType<HeatingRecipeDisplay> getRecipeType() {
        return RECIPE_TYPE;
    }

    @Override
    public Component getTitle() {
        return Component.translatable("jei.skyent.heating_chamber");
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, HeatingRecipeDisplay recipe, IFocusGroup focuses) {
        addInputSlot(builder, recipe.input());
        addOutputSlot(builder, recipe.output());
        addMachineTooltipSlot(builder);
    }

    @Override
    public void draw(HeatingRecipeDisplay recipe, IRecipeSlotsView recipeSlotsView, GuiGraphics guiGraphics, double mouseX, double mouseY) {
        drawConveyorMachine(guiGraphics);
    }

    public static List<HeatingRecipeDisplay> getAllRecipes() {
        List<HeatingRecipeDisplay> recipes = new ArrayList<>();
        for (Item input : HotMetalItems.getNormalHeatingInputs()) {
            Item hotItem = HotMetalItems.getHotItem(input);
            if (hotItem != null) {
                recipes.add(recipe(input, hotItem));
            }
        }
        return recipes;
    }

    private static HeatingRecipeDisplay recipe(net.minecraft.world.level.ItemLike input, net.minecraft.world.level.ItemLike output) {
        return new HeatingRecipeDisplay(Ingredient.of(input), new ItemStack(output));
    }

    public record HeatingRecipeDisplay(Ingredient input, ItemStack output) {
    }
}
