package com.skyeshade.skyent.compat.jei;

import com.skyeshade.skyent.SkyesNuclearTech;
import com.skyeshade.skyent.client.screen.MVAssemblerScreen;
import com.skyeshade.skyent.content.menu.MVAssemblerMenu;
import com.skyeshade.skyent.content.recipe.MVAssemblerRecipe;
import com.skyeshade.skyent.registry.ModItems;
import java.util.ArrayList;
import java.util.List;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;

public final class MVAssemblerRecipeCategory implements IRecipeCategory<MVAssemblerRecipe> {
    public static final ResourceLocation UID = ResourceLocation.fromNamespaceAndPath(SkyesNuclearTech.MOD_ID, "assembler");
    public static final RecipeType<MVAssemblerRecipe> RECIPE_TYPE = new RecipeType<>(UID, MVAssemblerRecipe.class);

    private static final int CROP_X = 4;
    private static final int CROP_Y = 13;
    private static final int WIDTH = 142;
    private static final int HEIGHT = 60;
    private static final int PROGRESS_TICKS = 40;

    private final IDrawable background;
    private final IDrawable icon;

    public MVAssemblerRecipeCategory(IGuiHelper guiHelper) {
        this.background = guiHelper.createDrawable(MVAssemblerScreen.ASSEMBLER_TEXTURE, CROP_X, CROP_Y, WIDTH, HEIGHT);
        this.icon = guiHelper.createDrawableIngredient(VanillaTypes.ITEM_STACK, new ItemStack(ModItems.MV_ASSEMBLER.get()));
    }

    @Override
    public RecipeType<MVAssemblerRecipe> getRecipeType() {
        return RECIPE_TYPE;
    }

    @Override
    public Component getTitle() {
        return Component.translatable("jei.skyent.mv_assembler");
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
    public void setRecipe(IRecipeLayoutBuilder builder, MVAssemblerRecipe recipe, IFocusGroup focuses) {
        List<MVAssemblerRecipe.CountedIngredient> ingredients = recipe.countedIngredientsSortedByCountDescending();
        for (int index = 0; index < Math.min(MVAssemblerMenu.INPUT_COLUMNS * MVAssemblerMenu.INPUT_ROWS, ingredients.size()); index++) {
            MVAssemblerRecipe.CountedIngredient ingredient = ingredients.get(index);
            int column = index % MVAssemblerMenu.INPUT_COLUMNS;
            int row = index / MVAssemblerMenu.INPUT_COLUMNS;
            builder.addSlot(
                            RecipeIngredientRole.INPUT,
                            localX(MVAssemblerMenu.INPUT_GRID_X + column * 18),
                            localY(MVAssemblerMenu.INPUT_GRID_Y + row * 18)
                    )
                    .addItemStacks(stacksWithCount(ingredient.ingredient(), ingredient.count()));
        }

        builder.addSlot(RecipeIngredientRole.OUTPUT, localX(MVAssemblerMenu.OUTPUT_SLOT_X), localY(MVAssemblerMenu.OUTPUT_SLOT_Y))
                .addItemStack(recipe.result());
    }

    @Override
    public void draw(MVAssemblerRecipe recipe, IRecipeSlotsView recipeSlotsView, GuiGraphics guiGraphics, double mouseX, double mouseY) {
        drawProgressArrow(guiGraphics);
    }

    private static List<ItemStack> stacksWithCount(Ingredient ingredient, int count) {
        List<ItemStack> stacks = new ArrayList<>();
        for (ItemStack stack : ingredient.getItems()) {
            ItemStack counted = stack.copy();
            counted.setCount(count);
            stacks.add(counted);
        }
        return stacks;
    }

    private static void drawProgressArrow(GuiGraphics guiGraphics) {
        int filledWidth = animationPhase(PROGRESS_TICKS) * MVAssemblerScreen.PROGRESS_ARROW_WIDTH / PROGRESS_TICKS;
        if (filledWidth <= 0) {
            return;
        }

        guiGraphics.blit(
                MVAssemblerScreen.ASSEMBLER_TEXTURE,
                localX(MVAssemblerScreen.PROGRESS_ARROW_X),
                localY(MVAssemblerScreen.PROGRESS_ARROW_Y),
                MVAssemblerScreen.PROGRESS_ARROW_U,
                MVAssemblerScreen.PROGRESS_ARROW_V,
                filledWidth,
                MVAssemblerScreen.PROGRESS_ARROW_HEIGHT,
                MVAssemblerScreen.GUI_TEXTURE_WIDTH,
                MVAssemblerScreen.GUI_TEXTURE_HEIGHT
        );
    }

    private static int animationPhase(int ticks) {
        return (int) (Util.getMillis() / 50L % ticks);
    }

    private static int localX(int guiX) {
        return guiX - CROP_X;
    }

    private static int localY(int guiY) {
        return guiY - CROP_Y;
    }
}
