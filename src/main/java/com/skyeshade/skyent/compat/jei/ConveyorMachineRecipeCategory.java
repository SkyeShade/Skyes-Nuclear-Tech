package com.skyeshade.skyent.compat.jei;

import com.skyeshade.skyent.SkyesNuclearTech;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.builder.ITooltipBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;

public abstract class ConveyorMachineRecipeCategory<T> implements IRecipeCategory<T> {
    public static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            SkyesNuclearTech.MOD_ID,
            "textures/gui/jei/conveyor_machines_jei.png"
    );

    public static final int BACKGROUND_U = 8;
    public static final int BACKGROUND_V = 16;
    public static final int BACKGROUND_WIDTH = 146;
    public static final int BACKGROUND_HEIGHT = 36;

    public static final int INPUT_SLOT_X = localX(11);
    public static final int INPUT_SLOT_Y = localY(26);
    public static final int OUTPUT_SLOT_X = localX(131);
    public static final int OUTPUT_SLOT_Y = localY(26);
    public static final int ARROW_X = localX(30);
    public static final int ARROW_Y = localY(26);
    public static final int MACHINE_CENTER_X = localX(77);
    public static final int MACHINE_CENTER_Y = localY(34);

    private static final int ARROW_U = 30;
    private static final int ARROW_V = 74;
    private static final int ARROW_WIDTH = 94;
    private static final int ARROW_HEIGHT = 16;
    private static final int ARROW_TICKS = 40;
    private static final float MACHINE_SCALE = 1.9F;
    private static final int ITEM_SIZE = 16;

    private final IDrawable background;
    private final IDrawable icon;
    private final ItemStack machineStack;

    protected ConveyorMachineRecipeCategory(IGuiHelper guiHelper, ItemStack machineStack) {
        this.background = guiHelper.createDrawable(TEXTURE, BACKGROUND_U, BACKGROUND_V, BACKGROUND_WIDTH, BACKGROUND_HEIGHT);
        this.icon = guiHelper.createDrawableIngredient(VanillaTypes.ITEM_STACK, machineStack);
        this.machineStack = machineStack;
    }

    @Override
    public IDrawable getBackground() {
        return background;
    }

    @Override
    public IDrawable getIcon() {
        return icon;
    }

    protected static void addInputSlot(IRecipeLayoutBuilder builder, Ingredient input) {
        builder.addSlot(RecipeIngredientRole.INPUT, INPUT_SLOT_X, INPUT_SLOT_Y)
                .addIngredients(input);
    }

    protected static void addOutputSlot(IRecipeLayoutBuilder builder, ItemStack output) {
        builder.addSlot(RecipeIngredientRole.OUTPUT, OUTPUT_SLOT_X, OUTPUT_SLOT_Y)
                .addItemStack(output);
    }

    protected void addMachineTooltipSlot(IRecipeLayoutBuilder builder) {
        // Tooltip is handled by getTooltip so JEI does not draw a slot highlight over the large manual render.
    }

    @Override
    public void getTooltip(ITooltipBuilder tooltip, T recipe, IRecipeSlotsView recipeSlotsView, double mouseX, double mouseY) {
        if (isMouseOverMachine(mouseX, mouseY)) {
            tooltip.addAll(Screen.getTooltipFromItem(Minecraft.getInstance(), machineStack));
        }
    }

    protected void drawConveyorMachine(GuiGraphics guiGraphics) {
        drawLongArrow(guiGraphics);
        drawMachine(guiGraphics);
    }

    protected static void drawLongArrow(GuiGraphics guiGraphics) {
        int filledWidth = animationPhase(ARROW_TICKS) * ARROW_WIDTH / ARROW_TICKS;
        if (filledWidth <= 0) {
            return;
        }

        guiGraphics.blit(
                TEXTURE,
                ARROW_X,
                ARROW_Y,
                ARROW_U,
                ARROW_V,
                filledWidth,
                ARROW_HEIGHT,
                256,
                256
        );
    }

    protected void drawMachine(GuiGraphics guiGraphics) {
        float scaledSize = ITEM_SIZE * MACHINE_SCALE;
        float drawX = MACHINE_CENTER_X - scaledSize / 2.0F;
        float drawY = MACHINE_CENTER_Y - scaledSize / 2.0F;

        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(drawX, drawY, 100.0F);
        guiGraphics.pose().scale(MACHINE_SCALE, MACHINE_SCALE, 1.0F);
        guiGraphics.renderFakeItem(machineStack, 0, 0);
        guiGraphics.pose().popPose();
    }

    private static boolean isMouseOverMachine(double mouseX, double mouseY) {
        float scaledSize = ITEM_SIZE * MACHINE_SCALE;
        float drawX = MACHINE_CENTER_X - scaledSize / 2.0F;
        float drawY = MACHINE_CENTER_Y - scaledSize / 2.0F;
        float padding = 4.0F;
        return mouseX >= drawX - padding
                && mouseX < drawX + scaledSize + padding
                && mouseY >= drawY - padding
                && mouseY < drawY + scaledSize + padding;
    }

    private static int animationPhase(int ticks) {
        return (int) (Util.getMillis() / 50L % ticks);
    }

    private static int localX(int textureX) {
        return textureX - BACKGROUND_U;
    }

    private static int localY(int textureY) {
        return textureY - BACKGROUND_V;
    }
}
