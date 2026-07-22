package com.skyeshade.skyent.compat.jei;

import com.skyeshade.skyent.SkyesNuclearTech;
import com.skyeshade.skyent.client.screen.LVCrusherScreen;
import com.skyeshade.skyent.content.item.LVCrusherRecipes;
import com.skyeshade.skyent.content.menu.LVCrusherMenu;
import com.skyeshade.skyent.registry.ModItems;
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
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

public final class CrusherRecipeCategory implements IRecipeCategory<LVCrusherRecipes.CrusherRecipeDisplay> {
    public static final ResourceLocation UID = ResourceLocation.fromNamespaceAndPath(SkyesNuclearTech.MOD_ID, "crusher");
    public static final RecipeType<LVCrusherRecipes.CrusherRecipeDisplay> RECIPE_TYPE =
            new RecipeType<>(UID, LVCrusherRecipes.CrusherRecipeDisplay.class);

    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            SkyesNuclearTech.MOD_ID,
            "textures/gui/jei/crusher_jei.png"
    );
    private static final int CROP_X = 45;
    private static final int CROP_Y = 8;
    private static final int WIDTH = 97;
    private static final int HEIGHT = 51;
    private static final int PROGRESS_TICKS = 40;

    private final IDrawable background;
    private final IDrawable icon;

    public CrusherRecipeCategory(IGuiHelper guiHelper) {
        this.background = guiHelper.createDrawable(TEXTURE, CROP_X, CROP_Y, WIDTH, HEIGHT);
        this.icon = guiHelper.createDrawableIngredient(VanillaTypes.ITEM_STACK, new ItemStack(ModItems.LV_CRUSHER.get()));
    }

    @Override
    public RecipeType<LVCrusherRecipes.CrusherRecipeDisplay> getRecipeType() {
        return RECIPE_TYPE;
    }

    @Override
    public Component getTitle() {
        return Component.translatable("jei.skyent.crusher");
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
    public void setRecipe(IRecipeLayoutBuilder builder, LVCrusherRecipes.CrusherRecipeDisplay recipe, IFocusGroup focuses) {
        builder.addSlot(RecipeIngredientRole.INPUT, localX(LVCrusherMenu.INPUT_SLOT_X), localY(LVCrusherMenu.INPUT_SLOT_Y))
                .addIngredients(recipe.input());

        if (recipe.hasPrimaryOutput()) {
            builder.addSlot(RecipeIngredientRole.OUTPUT, localX(LVCrusherMenu.OUTPUT_SLOT_X), localY(LVCrusherMenu.OUTPUT_SLOT_Y))
                    .addItemStack(recipe.primaryOutput());
        }

        if (recipe.hasSecondaryOutput()) {
            builder.addSlot(RecipeIngredientRole.OUTPUT, localX(LVCrusherMenu.SECONDARY_OUTPUT_SLOT_X), localY(LVCrusherMenu.SECONDARY_OUTPUT_SLOT_Y))
                    .addItemStack(recipe.secondaryOutput());
        }
    }

    @Override
    public void draw(LVCrusherRecipes.CrusherRecipeDisplay recipe, IRecipeSlotsView recipeSlotsView, GuiGraphics guiGraphics, double mouseX, double mouseY) {
        drawProgress(guiGraphics);
        drawSecondaryChance(recipe, guiGraphics);
    }

    private static void drawProgress(GuiGraphics guiGraphics) {
        int filledWidth = animationPhase(PROGRESS_TICKS) * LVCrusherScreen.PROGRESS_WIDTH / PROGRESS_TICKS;
        if (filledWidth <= 0) {
            return;
        }

        guiGraphics.blit(
                LVCrusherScreen.TEXTURE,
                localX(LVCrusherScreen.PROGRESS_X),
                localY(LVCrusherScreen.PROGRESS_Y),
                LVCrusherScreen.PROGRESS_U,
                LVCrusherScreen.PROGRESS_V,
                filledWidth,
                LVCrusherScreen.PROGRESS_HEIGHT,
                LVCrusherScreen.GUI_TEXTURE_WIDTH,
                LVCrusherScreen.GUI_TEXTURE_HEIGHT
        );
    }

    private static void drawSecondaryChance(LVCrusherRecipes.CrusherRecipeDisplay recipe, GuiGraphics guiGraphics) {
        if (!recipe.hasSecondaryOutput()) {
            return;
        }

        String chance = formatChance(recipe.secondaryChance());
        int x = localX(LVCrusherMenu.SECONDARY_OUTPUT_SLOT_X) + 9 - Minecraft.getInstance().font.width(chance) / 2;
        int y = localY(LVCrusherMenu.SECONDARY_OUTPUT_SLOT_Y) - 10;
        guiGraphics.drawString(Minecraft.getInstance().font, chance, x, y, 0x404040, false);
    }

    private static String formatChance(double chance) {
        return Math.round(chance * 100.0D) + "%";
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
