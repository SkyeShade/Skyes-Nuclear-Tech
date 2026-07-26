package com.skyeshade.skyent.compat.jei;

import com.skyeshade.skyent.SkyesNuclearTech;
import com.skyeshade.skyent.content.blockentity.MVChemicalReactorBlockEntity;
import com.skyeshade.skyent.content.recipe.ChemicalReactorRecipe;
import com.skyeshade.skyent.registry.ModItems;
import java.util.ArrayList;
import java.util.List;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.drawable.IDrawableStatic;
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
import net.neoforged.neoforge.fluids.FluidStack;

public final class ChemicalReactorRecipeCategory implements IRecipeCategory<ChemicalReactorRecipe> {
    public static final ResourceLocation UID = ResourceLocation.fromNamespaceAndPath(SkyesNuclearTech.MOD_ID, "chemical_reactor");
    public static final RecipeType<ChemicalReactorRecipe> RECIPE_TYPE = new RecipeType<>(UID, ChemicalReactorRecipe.class);
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            SkyesNuclearTech.MOD_ID,
            "textures/gui/jei/chemical_reactor_jei.png"
    );
    private static final int TEXTURE_WIDTH = 256;
    private static final int TEXTURE_HEIGHT = 256;

    private static final int CROP_X = 39;
    private static final int CROP_Y = 36;
    private static final int WIDTH = 137;
    private static final int HEIGHT = 53;
    private static final int PROGRESS_TICKS = 40;

    private static final int GAUGE_WIDTH = 13;
    private static final int GAUGE_HEIGHT = 32;
    private static final int GAUGE_OVERLAY_U = 232;
    private static final int GAUGE_OVERLAY_V = 14;
    private static final int INPUT_GAUGE_1_X = localX(41);
    private static final int INPUT_GAUGE_2_X = localX(78);
    private static final int OUTPUT_GAUGE_1_X = localX(124);
    private static final int OUTPUT_GAUGE_2_X = localX(161);
    private static final int GAUGE_Y = localY(37);

    private static final int INPUT_SLOT_X = localX(40);
    private static final int OUTPUT_SLOT_X = localX(123);
    private static final int SLOT_Y = localY(72);
    private static final int SLOT_SPACING = 18;

    private static final int PROGRESS_ARROW_X = localX(96);
    private static final int PROGRESS_ARROW_Y = localY(73);
    private static final int PROGRESS_ARROW_U = 211;
    private static final int PROGRESS_ARROW_V = 70;
    private static final int PROGRESS_ARROW_WIDTH = 22;
    private static final int PROGRESS_ARROW_HEIGHT = 16;

    private final IDrawable background;
    private final IDrawable icon;
    private final IDrawableStatic gaugeOverlay;

    public ChemicalReactorRecipeCategory(IGuiHelper guiHelper) {
        this.background = guiHelper.createDrawable(TEXTURE, CROP_X, CROP_Y, WIDTH, HEIGHT);
        this.icon = guiHelper.createDrawableIngredient(VanillaTypes.ITEM_STACK, new ItemStack(ModItems.MV_CHEMICAL_REACTOR.get()));
        this.gaugeOverlay = guiHelper.createDrawable(
                TEXTURE,
                GAUGE_OVERLAY_U,
                GAUGE_OVERLAY_V,
                GAUGE_WIDTH,
                GAUGE_HEIGHT
        );
    }

    @Override
    public RecipeType<ChemicalReactorRecipe> getRecipeType() {
        return RECIPE_TYPE;
    }

    @Override
    public Component getTitle() {
        return Component.translatable("jei.skyent.chemical_reactor");
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
    public void setRecipe(IRecipeLayoutBuilder builder, ChemicalReactorRecipe recipe, IFocusGroup focuses) {
        List<ChemicalReactorRecipe.FluidIngredient> fluidInputs = recipe.fluidInputs();
        List<FluidStack> fluidOutputs = recipe.fluidOutputs();
        long displayFluidCapacity = maxRecipeFluidAmount(fluidInputs, fluidOutputs);
        addInputFluidSlot(builder, fluidInputs, 0, INPUT_GAUGE_1_X, displayFluidCapacity);
        addInputFluidSlot(builder, fluidInputs, 1, INPUT_GAUGE_2_X, displayFluidCapacity);
        addOutputFluidSlot(builder, fluidOutputs, 0, OUTPUT_GAUGE_1_X, displayFluidCapacity);
        addOutputFluidSlot(builder, fluidOutputs, 1, OUTPUT_GAUGE_2_X, displayFluidCapacity);

        List<ChemicalReactorRecipe.CountedIngredient> itemInputs = recipe.itemInputs();
        for (int index = 0; index < Math.min(MVChemicalReactorBlockEntity.INPUT_SLOT_COUNT, itemInputs.size()); index++) {
            ChemicalReactorRecipe.CountedIngredient ingredient = itemInputs.get(index);
            builder.addSlot(RecipeIngredientRole.INPUT, INPUT_SLOT_X + index * SLOT_SPACING, SLOT_Y)
                    .addItemStacks(stacksWithCount(ingredient.ingredient(), ingredient.count()));
        }

        List<ItemStack> itemOutputs = recipe.itemOutputs();
        for (int index = 0; index < Math.min(MVChemicalReactorBlockEntity.OUTPUT_SLOT_COUNT, itemOutputs.size()); index++) {
            builder.addSlot(RecipeIngredientRole.OUTPUT, OUTPUT_SLOT_X + index * SLOT_SPACING, SLOT_Y)
                    .addItemStack(itemOutputs.get(index));
        }
    }

    @Override
    public void draw(ChemicalReactorRecipe recipe, IRecipeSlotsView recipeSlotsView, GuiGraphics guiGraphics, double mouseX, double mouseY) {
        drawProgressArrow(guiGraphics);
    }

    private void addInputFluidSlot(
            IRecipeLayoutBuilder builder,
            List<ChemicalReactorRecipe.FluidIngredient> fluids,
            int index,
            int x,
            long displayFluidCapacity
    ) {
        if (index >= fluids.size() || displayFluidCapacity <= 0) {
            return;
        }
        ChemicalReactorRecipe.FluidIngredient fluid = fluids.get(index);
        builder.addSlot(RecipeIngredientRole.INPUT, x, GAUGE_Y)
                .setFluidRenderer(displayFluidCapacity, false, GAUGE_WIDTH, GAUGE_HEIGHT)
                .setOverlay(gaugeOverlay, 0, 0)
                .addFluidStack(fluid.fluid(), fluid.amount());
    }

    private void addOutputFluidSlot(IRecipeLayoutBuilder builder, List<FluidStack> fluids, int index, int x, long displayFluidCapacity) {
        if (index >= fluids.size() || fluids.get(index).isEmpty() || displayFluidCapacity <= 0) {
            return;
        }
        FluidStack fluid = fluids.get(index);
        builder.addSlot(RecipeIngredientRole.OUTPUT, x, GAUGE_Y)
                .setFluidRenderer(displayFluidCapacity, false, GAUGE_WIDTH, GAUGE_HEIGHT)
                .setOverlay(gaugeOverlay, 0, 0)
                .addFluidStack(fluid.getFluid(), fluid.getAmount(), fluid.getComponentsPatch());
    }

    private static long maxRecipeFluidAmount(List<ChemicalReactorRecipe.FluidIngredient> fluidInputs, List<FluidStack> fluidOutputs) {
        long maxAmount = 0L;
        for (ChemicalReactorRecipe.FluidIngredient input : fluidInputs) {
            if (input.amount() > 0) {
                maxAmount = Math.max(maxAmount, input.amount());
            }
        }
        for (FluidStack output : fluidOutputs) {
            if (!output.isEmpty() && output.getAmount() > 0) {
                maxAmount = Math.max(maxAmount, output.getAmount());
            }
        }
        return maxAmount;
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
        int filledWidth = animationPhase(PROGRESS_TICKS) * PROGRESS_ARROW_WIDTH / PROGRESS_TICKS;
        if (filledWidth <= 0) {
            return;
        }

        guiGraphics.blit(
                TEXTURE,
                PROGRESS_ARROW_X,
                PROGRESS_ARROW_Y,
                PROGRESS_ARROW_U,
                PROGRESS_ARROW_V,
                filledWidth,
                PROGRESS_ARROW_HEIGHT,
                TEXTURE_WIDTH,
                TEXTURE_HEIGHT
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
