package com.skyeshade.skyent.compat.jei;

import com.skyeshade.skyent.SkyesNuclearTech;
import com.skyeshade.skyent.content.item.ForgingAnvilRecipes;
import com.skyeshade.skyent.registry.ModItems;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.gui.widgets.IRecipeExtrasBuilder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

public final class ForgingAnvilRecipeCategory implements IRecipeCategory<ForgingAnvilRecipes.ForgingRecipe> {
    public static final ResourceLocation UID = ResourceLocation.fromNamespaceAndPath(SkyesNuclearTech.MOD_ID, "forging_anvil");
    public static final RecipeType<ForgingAnvilRecipes.ForgingRecipe> RECIPE_TYPE =
            new RecipeType<>(UID, ForgingAnvilRecipes.ForgingRecipe.class);

    private static final int WIDTH = 125;
    private static final int HEIGHT = 38;
    private static final int INPUT_SLOT_X = 1;
    private static final int INPUT_SLOT_Y = 1;
    private static final int HAMMER_SLOT_X = 50;
    private static final int HAMMER_SLOT_Y = 1;
    private static final int OUTPUT_SLOT_X = 108;
    private static final int OUTPUT_SLOT_Y = 1;
    private static final int PLUS_X = 27;
    private static final int PLUS_Y = 3;
    private static final int ARROW_X = 76;
    private static final int ARROW_Y = 1;

    private final IDrawable background;
    private final IDrawable icon;

    public ForgingAnvilRecipeCategory(IGuiHelper guiHelper) {
        this.background = guiHelper.createBlankDrawable(WIDTH, HEIGHT);
        this.icon = guiHelper.createDrawableIngredient(VanillaTypes.ITEM_STACK, new ItemStack(ModItems.FORGING_ANVIL.get()));
    }

    @Override
    public RecipeType<ForgingAnvilRecipes.ForgingRecipe> getRecipeType() {
        return RECIPE_TYPE;
    }

    @Override
    public Component getTitle() {
        return Component.translatable("jei.skyent.forging_anvil");
    }

    @Override
    public IDrawable getBackground() {
        return background;
    }

    @Override
    public IDrawable getIcon() {
        return icon;
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, ForgingAnvilRecipes.ForgingRecipe recipe, IFocusGroup focuses) {
        builder.addSlot(RecipeIngredientRole.INPUT, INPUT_SLOT_X, INPUT_SLOT_Y)
                .addItemStack(recipe.displayInputStack())
                .setStandardSlotBackground();
        builder.addSlot(RecipeIngredientRole.CATALYST, HAMMER_SLOT_X, HAMMER_SLOT_Y)
                .addItemStack(new ItemStack(ModItems.FORGING_HAMMER.get()))
                .setStandardSlotBackground();
        builder.addSlot(RecipeIngredientRole.OUTPUT, OUTPUT_SLOT_X, OUTPUT_SLOT_Y)
                .addItemStack(recipe.outputStack())
                .setStandardSlotBackground();
    }

    @Override
    public void createRecipeExtras(IRecipeExtrasBuilder builder, ForgingAnvilRecipes.ForgingRecipe recipe, IFocusGroup focuses) {
        builder.addRecipePlusSign()
                .setPosition(PLUS_X, PLUS_Y);
        builder.addRecipeArrow()
                .setPosition(ARROW_X, ARROW_Y);
    }
}
