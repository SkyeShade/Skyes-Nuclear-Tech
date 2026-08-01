package com.skyeshade.skyent.compat.jei;

import com.skyeshade.skyent.SkyesNuclearTech;
import com.skyeshade.skyent.content.blockentity.CentrifugeBlockEntity;
import com.skyeshade.skyent.content.recipe.CentrifugeRecipe;
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
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.fluids.FluidStack;

public final class CentrifugeRecipeCategory implements IRecipeCategory<CentrifugeRecipe> {
    public static final ResourceLocation UID = ResourceLocation.fromNamespaceAndPath(SkyesNuclearTech.MOD_ID, "centrifuge");
    public static final RecipeType<CentrifugeRecipe> RECIPE_TYPE = new RecipeType<>(UID, CentrifugeRecipe.class);
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            SkyesNuclearTech.MOD_ID,
            "textures/gui/jei/centrifuge_jei.png"
    );
    private static final int TEXTURE_WIDTH = 256;
    private static final int TEXTURE_HEIGHT = 256;

    private static final int CROP_X = 8;
    private static final int CROP_Y = 62;
    private static final int WIDTH = 161;
    private static final int HEIGHT = 54;
    private static final int PROGRESS_TICKS = 40;

    private static final int INPUT_SLOT_X = localX(9);
    private static final int OUTPUT_SLOT_X = localX(117);
    private static final int SLOT_Y = localY(63);
    private static final int SLOT_SPACING = 18;
    private static final int GRID_SIZE = 3;

    private static final int INPUT_GAUGE_X = localX(64);
    private static final int OUTPUT_GAUGE_X = localX(106);
    private static final int GAUGE_Y = localY(69);
    private static final int GAUGE_WIDTH = 8;
    private static final int GAUGE_HEIGHT = 40;
    private static final int GAUGE_OVERLAY_U = 208;
    private static final int GAUGE_OVERLAY_V = 90;

    private static final int PROGRESS_X = localX(73);
    private static final int PROGRESS_Y = localY(80);
    private static final int PROGRESS_U = 184;
    private static final int PROGRESS_V = 136;
    private static final int PROGRESS_WIDTH = 31;
    private static final int PROGRESS_HEIGHT = 20;

    private final IDrawable background;
    private final IDrawable icon;
    private final IDrawableStatic gaugeOverlay;

    public CentrifugeRecipeCategory(IGuiHelper guiHelper) {
        this.background = guiHelper.createDrawable(TEXTURE, CROP_X, CROP_Y, WIDTH, HEIGHT);
        this.icon = guiHelper.createDrawableIngredient(VanillaTypes.ITEM_STACK, new ItemStack(ModItems.CENTRIFUGE.get()));
        this.gaugeOverlay = guiHelper.createDrawable(TEXTURE, GAUGE_OVERLAY_U, GAUGE_OVERLAY_V, GAUGE_WIDTH, GAUGE_HEIGHT);
    }

    @Override
    public RecipeType<CentrifugeRecipe> getRecipeType() {
        return RECIPE_TYPE;
    }

    @Override
    public Component getTitle() {
        return Component.translatable("jei.skyent.centrifuge");
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
    public void setRecipe(IRecipeLayoutBuilder builder, CentrifugeRecipe recipe, IFocusGroup focuses) {
        List<CentrifugeRecipe.FluidIngredient> fluidInputs = recipe.fluidInputs();
        List<FluidStack> fluidOutputs = recipe.fluidOutputs();
        long displayFluidCapacity = maxRecipeFluidAmount(fluidInputs, fluidOutputs);
        addInputFluidSlot(builder, fluidInputs, displayFluidCapacity);
        addOutputFluidSlot(builder, fluidOutputs, displayFluidCapacity);

        List<CentrifugeRecipe.CountedIngredient> itemInputs = recipe.itemInputs();
        for (int index = 0; index < Math.min(CentrifugeBlockEntity.INPUT_SLOT_COUNT, itemInputs.size()); index++) {
            CentrifugeRecipe.CountedIngredient ingredient = itemInputs.get(index);
            int column = index % GRID_SIZE;
            int row = index / GRID_SIZE;
            builder.addSlot(RecipeIngredientRole.INPUT, INPUT_SLOT_X + column * SLOT_SPACING, SLOT_Y + row * SLOT_SPACING)
                    .addItemStacks(stacksWithCount(ingredient.ingredient(), ingredient.count()));
        }

        List<CentrifugeRecipe.ItemOutput> itemOutputs = recipe.itemOutputs();
        for (int index = 0; index < Math.min(CentrifugeBlockEntity.OUTPUT_SLOT_COUNT, itemOutputs.size()); index++) {
            int column = index % GRID_SIZE;
            int row = index / GRID_SIZE;
            builder.addSlot(RecipeIngredientRole.OUTPUT, OUTPUT_SLOT_X + column * SLOT_SPACING, SLOT_Y + row * SLOT_SPACING)
                    .addItemStack(itemOutputs.get(index).stack());
        }
    }

    @Override
    public void draw(CentrifugeRecipe recipe, IRecipeSlotsView recipeSlotsView, GuiGraphics guiGraphics, double mouseX, double mouseY) {
        drawProgress(guiGraphics);
        drawOutputChances(recipe, guiGraphics);
    }

    private void addInputFluidSlot(
            IRecipeLayoutBuilder builder,
            List<CentrifugeRecipe.FluidIngredient> fluids,
            long displayFluidCapacity
    ) {
        if (fluids.isEmpty() || displayFluidCapacity <= 0) {
            return;
        }
        CentrifugeRecipe.FluidIngredient fluid = fluids.getFirst();
        builder.addSlot(RecipeIngredientRole.INPUT, INPUT_GAUGE_X, GAUGE_Y)
                .setFluidRenderer(displayFluidCapacity, false, GAUGE_WIDTH, GAUGE_HEIGHT)
                .setOverlay(gaugeOverlay, 0, 0)
                .addFluidStack(fluid.fluid(), fluid.amount());
    }

    private void addOutputFluidSlot(IRecipeLayoutBuilder builder, List<FluidStack> fluids, long displayFluidCapacity) {
        if (fluids.isEmpty() || fluids.getFirst().isEmpty() || displayFluidCapacity <= 0) {
            return;
        }
        FluidStack fluid = fluids.getFirst();
        builder.addSlot(RecipeIngredientRole.OUTPUT, OUTPUT_GAUGE_X, GAUGE_Y)
                .setFluidRenderer(displayFluidCapacity, false, GAUGE_WIDTH, GAUGE_HEIGHT)
                .setOverlay(gaugeOverlay, 0, 0)
                .addFluidStack(fluid.getFluid(), fluid.getAmount(), fluid.getComponentsPatch());
    }

    private static long maxRecipeFluidAmount(List<CentrifugeRecipe.FluidIngredient> fluidInputs, List<FluidStack> fluidOutputs) {
        long maxAmount = 0L;
        for (CentrifugeRecipe.FluidIngredient input : fluidInputs) {
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

    private static void drawProgress(GuiGraphics guiGraphics) {
        int filledWidth = animationPhase(PROGRESS_TICKS) * PROGRESS_WIDTH / PROGRESS_TICKS;
        if (filledWidth <= 0) {
            return;
        }

        guiGraphics.blit(
                TEXTURE,
                PROGRESS_X,
                PROGRESS_Y,
                PROGRESS_U,
                PROGRESS_V,
                filledWidth,
                PROGRESS_HEIGHT,
                TEXTURE_WIDTH,
                TEXTURE_HEIGHT
        );
    }

    private static void drawOutputChances(CentrifugeRecipe recipe, GuiGraphics guiGraphics) {
        if (!chanceLabelsVisible()) {
            return;
        }

        List<CentrifugeRecipe.ItemOutput> itemOutputs = recipe.itemOutputs();
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(0.0F, 0.0F, 300.0F);
        for (int index = 0; index < Math.min(CentrifugeBlockEntity.OUTPUT_SLOT_COUNT, itemOutputs.size()); index++) {
            CentrifugeRecipe.ItemOutput output = itemOutputs.get(index);
            if (output.isGuaranteed()) {
                continue;
            }

            String chance = formatChance(output.chance());
            int column = index % GRID_SIZE;
            int row = index / GRID_SIZE;
            int slotX = OUTPUT_SLOT_X + column * SLOT_SPACING;
            int slotY = SLOT_Y + row * SLOT_SPACING;
            int x = slotX + 9 - Minecraft.getInstance().font.width(chance) / 2;
            int y = slotY + 10;
            guiGraphics.drawString(Minecraft.getInstance().font, chance, x, y, 0xFFFF55, true);
        }
        guiGraphics.pose().popPose();
    }

    private static boolean chanceLabelsVisible() {
        return Util.getMillis() % 4000L < 2000L;
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
