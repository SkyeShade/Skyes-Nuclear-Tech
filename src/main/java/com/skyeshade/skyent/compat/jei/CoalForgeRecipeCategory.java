package com.skyeshade.skyent.compat.jei;

import com.mojang.blaze3d.systems.RenderSystem;
import com.skyeshade.skyent.SkyesNuclearTech;
import com.skyeshade.skyent.client.screen.CoalForgeGuiLayout;
import com.skyeshade.skyent.content.blockentity.CoalForgeBlockEntity;
import com.skyeshade.skyent.content.item.HotItemUtil;
import com.skyeshade.skyent.content.item.HotMetalItems;
import com.skyeshade.skyent.registry.ModItems;
import java.util.ArrayList;
import java.util.List;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.builder.ITooltipBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.ingredients.IIngredientRenderer;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.crafting.Ingredient;

public final class CoalForgeRecipeCategory implements IRecipeCategory<CoalForgeRecipeCategory.CoalForgeRecipeDisplay> {
    public static final ResourceLocation UID = ResourceLocation.fromNamespaceAndPath(SkyesNuclearTech.MOD_ID, "coal_forge");
    public static final RecipeType<CoalForgeRecipeDisplay> RECIPE_TYPE = new RecipeType<>(UID, CoalForgeRecipeDisplay.class);

    private static final int PROGRESS_TICKS = 40;
    private static final int BATCH_TICKS = 80;
    private static final int ITEM_SIZE = 16;
    private static final int[][] INPUT_SLOTS = {
            {CoalForgeGuiLayout.localX(CoalForgeGuiLayout.SLOT_1_X), CoalForgeGuiLayout.localY(CoalForgeGuiLayout.SLOT_1_Y)},
            {CoalForgeGuiLayout.localX(CoalForgeGuiLayout.SLOT_2_X), CoalForgeGuiLayout.localY(CoalForgeGuiLayout.SLOT_2_Y)},
            {CoalForgeGuiLayout.localX(CoalForgeGuiLayout.SLOT_3_X), CoalForgeGuiLayout.localY(CoalForgeGuiLayout.SLOT_3_Y)},
            {CoalForgeGuiLayout.localX(CoalForgeGuiLayout.SLOT_4_X), CoalForgeGuiLayout.localY(CoalForgeGuiLayout.SLOT_4_Y)}
    };

    private final IDrawable background;
    private final IDrawable icon;

    public CoalForgeRecipeCategory(IGuiHelper guiHelper) {
        this.background = guiHelper.createDrawable(
                CoalForgeGuiLayout.TEXTURE,
                CoalForgeGuiLayout.CROP_X,
                CoalForgeGuiLayout.CROP_Y,
                CoalForgeGuiLayout.CROP_WIDTH,
                CoalForgeGuiLayout.CROP_HEIGHT
        );
        this.icon = guiHelper.createDrawableIngredient(VanillaTypes.ITEM_STACK, new ItemStack(ModItems.COAL_FORGE.get()));
    }

    @Override
    public RecipeType<CoalForgeRecipeDisplay> getRecipeType() {
        return RECIPE_TYPE;
    }

    @Override
    public Component getTitle() {
        return Component.translatable("jei.skyent.coal_forge");
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
    public void setRecipe(IRecipeLayoutBuilder builder, CoalForgeRecipeDisplay recipe, IFocusGroup focuses) {
        builder.addSlot(RecipeIngredientRole.INPUT, INPUT_SLOTS[0][0], INPUT_SLOTS[0][1])
                .addIngredients(recipe.input());
        builder.addSlot(
                        RecipeIngredientRole.OUTPUT,
                        CoalForgeGuiLayout.localX(CoalForgeGuiLayout.OUTPUT_SLOT_X),
                        CoalForgeGuiLayout.localY(CoalForgeGuiLayout.OUTPUT_SLOT_Y)
                )
                .setCustomRenderer(VanillaTypes.ITEM_STACK, new AnimatedOutputRenderer())
                .addItemStack(new ItemStack(recipe.outputItem()));
    }

    @Override
    public void draw(CoalForgeRecipeDisplay recipe, IRecipeSlotsView recipeSlotsView, GuiGraphics guiGraphics, double mouseX, double mouseY) {
        drawHeatingOverlay(guiGraphics);
        drawProgressArrow(guiGraphics);
        drawAnimatedStacks(recipe, guiGraphics, mouseX, mouseY);
    }

    @Override
    public void getTooltip(ITooltipBuilder tooltip, CoalForgeRecipeDisplay recipe, IRecipeSlotsView recipeSlotsView, double mouseX, double mouseY) {
        int count = displayedInputCount();
        for (int slot = 1; slot < count; slot++) {
            if (isMouseOverSlot(slot, mouseX, mouseY)) {
                tooltip.addAll(Screen.getTooltipFromItem(Minecraft.getInstance(), recipe.inputStack()));
                return;
            }
        }
    }

    public static List<CoalForgeRecipeDisplay> getAllRecipes() {
        List<CoalForgeRecipeDisplay> recipes = new ArrayList<>();
        for (Item input : HotMetalItems.getNormalHeatingInputs()) {
            Item hotItem = HotMetalItems.getHotItem(input);
            if (hotItem != null && HotItemUtil.getForgingTemperature(new ItemStack(input)) <= CoalForgeBlockEntity.MAX_TEMPERATURE_C) {
                recipes.add(new CoalForgeRecipeDisplay(Ingredient.of(input), hotItem));
            }
        }
        return recipes;
    }

    private static void drawHeatingOverlay(GuiGraphics guiGraphics) {
        int x = CoalForgeGuiLayout.localX(CoalForgeGuiLayout.HEATING_OVERLAY_X);
        int y = CoalForgeGuiLayout.localY(CoalForgeGuiLayout.HEATING_OVERLAY_Y);
        float alpha = (Mth.sin(Util.getMillis() / 240.0F) + 1.0F) * 0.5F;

        RenderSystem.enableBlend();
        guiGraphics.setColor(1.0F, 1.0F, 1.0F, 1.0F - alpha);
        guiGraphics.blit(
                CoalForgeGuiLayout.TEXTURE,
                x,
                y,
                CoalForgeGuiLayout.HEATING_OVERLAY_1_U,
                CoalForgeGuiLayout.HEATING_OVERLAY_1_V,
                CoalForgeGuiLayout.HEATING_OVERLAY_SIZE,
                CoalForgeGuiLayout.HEATING_OVERLAY_SIZE,
                CoalForgeGuiLayout.TEXTURE_WIDTH,
                CoalForgeGuiLayout.TEXTURE_HEIGHT
        );
        guiGraphics.setColor(1.0F, 1.0F, 1.0F, alpha);
        guiGraphics.blit(
                CoalForgeGuiLayout.TEXTURE,
                x,
                y,
                CoalForgeGuiLayout.HEATING_OVERLAY_2_U,
                CoalForgeGuiLayout.HEATING_OVERLAY_2_V,
                CoalForgeGuiLayout.HEATING_OVERLAY_SIZE,
                CoalForgeGuiLayout.HEATING_OVERLAY_SIZE,
                CoalForgeGuiLayout.TEXTURE_WIDTH,
                CoalForgeGuiLayout.TEXTURE_HEIGHT
        );
        guiGraphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.disableBlend();
    }

    private static void drawProgressArrow(GuiGraphics guiGraphics) {
        int filledWidth = animationPhase(PROGRESS_TICKS) * CoalForgeGuiLayout.PROGRESS_ARROW_WIDTH / PROGRESS_TICKS;
        if (filledWidth <= 0) {
            return;
        }

        guiGraphics.blit(
                CoalForgeGuiLayout.TEXTURE,
                CoalForgeGuiLayout.localX(CoalForgeGuiLayout.PROGRESS_ARROW_X),
                CoalForgeGuiLayout.localY(CoalForgeGuiLayout.PROGRESS_ARROW_Y),
                CoalForgeGuiLayout.PROGRESS_ARROW_U,
                CoalForgeGuiLayout.PROGRESS_ARROW_V,
                filledWidth,
                CoalForgeGuiLayout.PROGRESS_ARROW_HEIGHT,
                CoalForgeGuiLayout.TEXTURE_WIDTH,
                CoalForgeGuiLayout.TEXTURE_HEIGHT
        );
    }

    private static void drawAnimatedStacks(CoalForgeRecipeDisplay recipe, GuiGraphics guiGraphics, double mouseX, double mouseY) {
        int count = displayedInputCount();
        ItemStack input = recipe.inputStack();
        for (int slot = 1; slot < count; slot++) {
            guiGraphics.renderFakeItem(input, INPUT_SLOTS[slot][0], INPUT_SLOTS[slot][1]);
            if (isMouseOverSlot(slot, mouseX, mouseY)) {
                drawSlotHoverHighlight(guiGraphics, slot);
            }
        }
    }

    private static int displayedInputCount() {
        return animationPhase(BATCH_TICKS) / (BATCH_TICKS / 4) + 1;
    }

    private static int animationPhase(int ticks) {
        return (int) (Util.getMillis() / 50L % ticks);
    }

    private static boolean isMouseOverSlot(int slot, double mouseX, double mouseY) {
        int x = INPUT_SLOTS[slot][0];
        int y = INPUT_SLOTS[slot][1];
        return mouseX >= x && mouseX < x + ITEM_SIZE && mouseY >= y && mouseY < y + ITEM_SIZE;
    }

    private static void drawSlotHoverHighlight(GuiGraphics guiGraphics, int slot) {
        int x = INPUT_SLOTS[slot][0];
        int y = INPUT_SLOTS[slot][1];
        guiGraphics.fill(x, y, x + ITEM_SIZE, y + ITEM_SIZE, 0x80FFFFFF);
    }

    public record CoalForgeRecipeDisplay(Ingredient input, Item outputItem) {
        private ItemStack inputStack() {
            ItemStack[] stacks = input.getItems();
            return stacks.length == 0 ? ItemStack.EMPTY : stacks[0];
        }
    }

    private static final class AnimatedOutputRenderer implements IIngredientRenderer<ItemStack> {
        @Override
        public void render(GuiGraphics guiGraphics, ItemStack ingredient) {
            ItemStack stack = ingredient.copyWithCount(displayedInputCount());
            guiGraphics.renderFakeItem(stack, 0, 0);
            guiGraphics.renderItemDecorations(Minecraft.getInstance().font, stack, 0, 0);
        }

        @Override
        public List<Component> getTooltip(ItemStack ingredient, TooltipFlag tooltipFlag) {
            return Screen.getTooltipFromItem(Minecraft.getInstance(), ingredient.copyWithCount(displayedInputCount()));
        }

        @Override
        public Font getFontRenderer(Minecraft minecraft, ItemStack ingredient) {
            return minecraft.font;
        }

        @Override
        public int getWidth() {
            return ITEM_SIZE;
        }

        @Override
        public int getHeight() {
            return ITEM_SIZE;
        }
    }
}
