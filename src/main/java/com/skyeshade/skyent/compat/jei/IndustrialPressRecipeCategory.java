package com.skyeshade.skyent.compat.jei;

import com.skyeshade.skyent.SkyesNuclearTech;
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

public final class IndustrialPressRecipeCategory extends ConveyorMachineRecipeCategory<IndustrialPressRecipeCategory.PressRecipeDisplay> {
    public static final ResourceLocation UID = ResourceLocation.fromNamespaceAndPath(SkyesNuclearTech.MOD_ID, "industrial_press");
    public static final RecipeType<PressRecipeDisplay> RECIPE_TYPE = new RecipeType<>(UID, PressRecipeDisplay.class);

    public IndustrialPressRecipeCategory(IGuiHelper guiHelper) {
        super(guiHelper, new ItemStack(ModItems.INDUSTRIAL_PRESS.get()));
    }

    @Override
    public RecipeType<PressRecipeDisplay> getRecipeType() {
        return RECIPE_TYPE;
    }

    @Override
    public Component getTitle() {
        return Component.translatable("jei.skyent.industrial_press");
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, PressRecipeDisplay recipe, IFocusGroup focuses) {
        addInputSlot(builder, recipe.input());
        addOutputSlot(builder, recipe.output());
        addMachineTooltipSlot(builder);
    }

    @Override
    public void draw(PressRecipeDisplay recipe, IRecipeSlotsView recipeSlotsView, GuiGraphics guiGraphics, double mouseX, double mouseY) {
        drawConveyorMachine(guiGraphics);
    }

    public static List<PressRecipeDisplay> getAllRecipes() {
        return List.of(
                recipe(ModItems.HOT_COPPER_INGOT.get(), ModItems.COPPER_PLATE.get()),
                recipe(ModItems.HOT_IRON_INGOT.get(), ModItems.IRON_PLATE.get()),
                recipe(ModItems.HOT_GOLD_INGOT.get(), ModItems.GOLD_PLATE.get()),
                recipe(ModItems.HOT_STEEL_INGOT.get(), ModItems.STEEL_PLATE.get()),
                recipe(ModItems.HOT_ALUMINUM_INGOT.get(), ModItems.ALUMINUM_PLATE.get()),
                recipe(ModItems.HOT_TITANIUM_INGOT.get(), ModItems.TITANIUM_PLATE.get()),
                recipe(ModItems.HOT_TUNGSTEN_INGOT.get(), ModItems.TUNGSTEN_PLATE.get()),
                recipe(ModItems.HOT_COBALT_INGOT.get(), ModItems.COBALT_PLATE.get()),
                recipe(ModItems.HOT_NICKEL_INGOT.get(), ModItems.NICKEL_PLATE.get()),
                recipe(ModItems.HOT_COBALT_BRONZE_INGOT.get(), ModItems.COBALT_BRONZE_PLATE.get()),
                recipe(ModItems.HOT_CUPRONICKEL_INGOT.get(), ModItems.CUPRONICKEL_PLATE.get()),
                recipe(ModItems.HOT_COPPER_ROD.get(), ModItems.COPPER_BOLT.get()),
                recipe(ModItems.HOT_IRON_ROD.get(), ModItems.IRON_BOLT.get()),
                recipe(ModItems.HOT_STEEL_ROD.get(), ModItems.STEEL_BOLT.get()),
                recipe(ModItems.HOT_ALUMINUM_ROD.get(), ModItems.ALUMINUM_BOLT.get()),
                recipe(ModItems.HOT_TITANIUM_ROD.get(), ModItems.TITANIUM_BOLT.get()),
                recipe(ModItems.HOT_COBALT_ROD.get(), ModItems.COBALT_BOLT.get()),
                recipe(ModItems.HOT_TUNGSTEN_ROD.get(), ModItems.TUNGSTEN_BOLT.get()),
                recipe(ModItems.HOT_NICKEL_ROD.get(), ModItems.NICKEL_BOLT.get())
        );
    }

    private static PressRecipeDisplay recipe(net.minecraft.world.level.ItemLike input, net.minecraft.world.level.ItemLike output) {
        return recipe(input, new ItemStack(output));
    }

    private static PressRecipeDisplay recipe(net.minecraft.world.level.ItemLike input, ItemStack output) {
        return new PressRecipeDisplay(Ingredient.of(input), output);
    }

    public record PressRecipeDisplay(Ingredient input, ItemStack output) {
    }
}
