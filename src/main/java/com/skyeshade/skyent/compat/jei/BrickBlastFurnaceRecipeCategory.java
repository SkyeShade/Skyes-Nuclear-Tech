package com.skyeshade.skyent.compat.jei;

import com.skyeshade.skyent.SkyesNuclearTech;
import com.skyeshade.skyent.client.screen.BrickBlastFurnaceScreen;
import com.skyeshade.skyent.client.screen.FluidGaugeRenderer;
import com.skyeshade.skyent.content.blockentity.BrickBlastFurnaceBlockEntity;
import com.skyeshade.skyent.content.menu.BrickBlastFurnaceMenu;
import com.skyeshade.skyent.content.recipe.BrickBlastFurnaceRecipe;
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
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluids;

import java.util.List;

public final class BrickBlastFurnaceRecipeCategory implements IRecipeCategory<BrickBlastFurnaceRecipe> {
    public static final ResourceLocation UID = ResourceLocation.fromNamespaceAndPath(SkyesNuclearTech.MOD_ID, "brick_blast_furnace");
    public static final RecipeType<BrickBlastFurnaceRecipe> RECIPE_TYPE =
            new RecipeType<>(UID, BrickBlastFurnaceRecipe.class);

    private static final int CROP_X = 12;
    private static final int CROP_Y = 11;
    private static final int WIDTH = 152;
    private static final int HEIGHT = 63;
    private static final int FUEL_DRAIN_TICKS = 40;
    private static final int FIRE_TICKS = 28;
    private static final int ARROW_TICKS = 48;
    private static final List<ItemStack> FUEL_ITEMS = BuiltInRegistries.ITEM.stream()
            .map(ItemStack::new)
            .filter(BrickBlastFurnaceBlockEntity::isFuel)
            .toList();

    private final IDrawable background;
    private final IDrawable icon;

    public BrickBlastFurnaceRecipeCategory(IGuiHelper guiHelper) {
        this.background = guiHelper.createDrawable(BrickBlastFurnaceScreen.TEXTURE, CROP_X, CROP_Y, WIDTH, HEIGHT);
        this.icon = guiHelper.createDrawableIngredient(VanillaTypes.ITEM_STACK, new ItemStack(ModItems.BRICK_BLAST_FURNACE.get()));
    }

    @Override
    public RecipeType<BrickBlastFurnaceRecipe> getRecipeType() {
        return RECIPE_TYPE;
    }

    @Override
    public Component getTitle() {
        return Component.translatable("jei.skyent.brick_blast_furnace");
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
    public void setRecipe(IRecipeLayoutBuilder builder, BrickBlastFurnaceRecipe recipe, IFocusGroup focuses) {
        builder.addSlot(RecipeIngredientRole.RENDER_ONLY, localX(BrickBlastFurnaceMenu.FUEL_SLOT_X), localY(BrickBlastFurnaceMenu.FUEL_SLOT_Y))
                .addItemStacks(FUEL_ITEMS);
        builder.addSlot(RecipeIngredientRole.INPUT, localX(BrickBlastFurnaceMenu.TOP_INPUT_SLOT_X), localY(BrickBlastFurnaceMenu.TOP_INPUT_SLOT_Y))
                .addIngredients(recipe.getFirstInput());
        builder.addSlot(RecipeIngredientRole.INPUT, localX(BrickBlastFurnaceMenu.BOTTOM_INPUT_SLOT_X), localY(BrickBlastFurnaceMenu.BOTTOM_INPUT_SLOT_Y))
                .addIngredients(recipe.getSecondInput());
        builder.addSlot(RecipeIngredientRole.OUTPUT, localX(BrickBlastFurnaceMenu.OUTPUT_SLOT_X), localY(BrickBlastFurnaceMenu.OUTPUT_SLOT_Y))
                .addItemStack(recipe.getResult());
    }

    @Override
    public void draw(BrickBlastFurnaceRecipe recipe, IRecipeSlotsView recipeSlotsView, GuiGraphics guiGraphics, double mouseX, double mouseY) {
        drawFuelGauge(guiGraphics);
        drawFuelGaugeOverlay(guiGraphics);
        drawFire(guiGraphics);
        drawProgressArrow(guiGraphics);
    }

    private static void drawFuelGauge(GuiGraphics guiGraphics) {
        int phase = animationPhase(FUEL_DRAIN_TICKS);
        int amount = FUEL_DRAIN_TICKS - phase;
        FluidGaugeRenderer.renderMaskedTiledFluid(
                guiGraphics,
                Fluids.LAVA,
                amount,
                FUEL_DRAIN_TICKS,
                localX(BrickBlastFurnaceScreen.FUEL_FILL_X),
                localY(BrickBlastFurnaceScreen.FUEL_FILL_Y),
                BrickBlastFurnaceScreen.FUEL_FILL_WIDTH,
                BrickBlastFurnaceScreen.FUEL_FILL_HEIGHT
        );
    }

    private static void drawFuelGaugeOverlay(GuiGraphics guiGraphics) {
        guiGraphics.blit(
                BrickBlastFurnaceScreen.TEXTURE,
                localX(BrickBlastFurnaceScreen.FUEL_GAUGE_X),
                localY(BrickBlastFurnaceScreen.FUEL_GAUGE_Y),
                BrickBlastFurnaceScreen.FUEL_GAUGE_OVERLAY_U,
                BrickBlastFurnaceScreen.FUEL_GAUGE_OVERLAY_V,
                BrickBlastFurnaceScreen.FUEL_GAUGE_OVERLAY_WIDTH,
                BrickBlastFurnaceScreen.FUEL_GAUGE_OVERLAY_HEIGHT,
                BrickBlastFurnaceScreen.GUI_TEXTURE_WIDTH,
                BrickBlastFurnaceScreen.GUI_TEXTURE_HEIGHT
        );
    }

    private static void drawFire(GuiGraphics guiGraphics) {
        int flameHeight = Math.max(1, (FIRE_TICKS - animationPhase(FIRE_TICKS)) * BrickBlastFurnaceScreen.FIRE_HEIGHT / FIRE_TICKS);
        guiGraphics.blit(
                BrickBlastFurnaceScreen.TEXTURE,
                localX(BrickBlastFurnaceScreen.FIRE_X),
                localY(BrickBlastFurnaceScreen.FIRE_Y) + BrickBlastFurnaceScreen.FIRE_HEIGHT - flameHeight,
                BrickBlastFurnaceScreen.FIRE_U,
                BrickBlastFurnaceScreen.FIRE_V + BrickBlastFurnaceScreen.FIRE_HEIGHT - flameHeight,
                BrickBlastFurnaceScreen.FIRE_WIDTH,
                flameHeight,
                BrickBlastFurnaceScreen.GUI_TEXTURE_WIDTH,
                BrickBlastFurnaceScreen.GUI_TEXTURE_HEIGHT
        );
    }

    private static void drawProgressArrow(GuiGraphics guiGraphics) {
        int filledWidth = animationPhase(ARROW_TICKS) * BrickBlastFurnaceScreen.PROGRESS_ARROW_WIDTH / ARROW_TICKS;
        if (filledWidth <= 0) {
            return;
        }

        guiGraphics.blit(
                BrickBlastFurnaceScreen.TEXTURE,
                localX(BrickBlastFurnaceScreen.PROGRESS_ARROW_X),
                localY(BrickBlastFurnaceScreen.PROGRESS_ARROW_Y),
                BrickBlastFurnaceScreen.PROGRESS_ARROW_U,
                BrickBlastFurnaceScreen.PROGRESS_ARROW_V,
                filledWidth,
                BrickBlastFurnaceScreen.PROGRESS_ARROW_HEIGHT,
                BrickBlastFurnaceScreen.GUI_TEXTURE_WIDTH,
                BrickBlastFurnaceScreen.GUI_TEXTURE_HEIGHT
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
