package com.skyeshade.skyent.client.screen;

import com.skyeshade.skyent.SkyesNuclearTech;
import com.skyeshade.skyent.content.menu.MVAssemblerRecipeSelectMenu;
import com.skyeshade.skyent.content.recipe.MVAssemblerRecipe;
import com.skyeshade.skyent.network.OpenMVAssemblerPayload;
import com.skyeshade.skyent.network.SelectMVAssemblerRecipePayload;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.neoforge.network.PacketDistributor;

public class MVAssemblerRecipeSelectScreen extends AbstractContainerScreen<MVAssemblerRecipeSelectMenu> {
    private static final int RECIPE_SELECT_IMAGE_WIDTH = 214;
    private static final int IMAGE_HEIGHT = 166;
    private static final int GUI_TEXTURE_WIDTH = 256;
    private static final int GUI_TEXTURE_HEIGHT = 256;
    private static final int TITLE_X = 8;
    private static final int TITLE_Y = 6;
    private static final int TITLE_COLOR = 0x404040;
    private static final int BACK_BUTTON_X = 180;
    private static final int BACK_BUTTON_Y = 16;
    private static final int BACK_BUTTON_SIZE = 18;
    private static final int SLOT_SIZE = 18;
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            SkyesNuclearTech.MOD_ID,
            "textures/gui/assembler_recipe_select.png"
    );

    public MVAssemblerRecipeSelectScreen(MVAssemblerRecipeSelectMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        imageWidth = RECIPE_SELECT_IMAGE_WIDTH;
        imageHeight = IMAGE_HEIGHT;
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);

        RecipeHolder<MVAssemblerRecipe> hovered = hoveredRecipe(mouseX, mouseY);
        if (hovered != null) {
            guiGraphics.renderComponentTooltip(font, recipeTooltip(hovered), mouseX, mouseY);
        } else {
            renderTooltip(guiGraphics, mouseX, mouseY);
        }
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        guiGraphics.blit(TEXTURE, leftPos, topPos, 0, 0, RECIPE_SELECT_IMAGE_WIDTH, IMAGE_HEIGHT, GUI_TEXTURE_WIDTH, GUI_TEXTURE_HEIGHT);
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        guiGraphics.drawString(font, Component.literal("Select Assembler Recipe"), TITLE_X, TITLE_Y, TITLE_COLOR, false);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && isPointIn(BACK_BUTTON_X, BACK_BUTTON_Y, BACK_BUTTON_SIZE, BACK_BUTTON_SIZE, mouseX, mouseY)) {
            playClickSound();
            PacketDistributor.sendToServer(new OpenMVAssemblerPayload(menu.getBlockPos()));
            return true;
        }

        if (button == 0) {
            int slot = hoveredRecipeSlot(mouseX, mouseY);
            RecipeHolder<MVAssemblerRecipe> recipe = menu.getRecipe(slot);
            if (recipe != null) {
                playClickSound();
                PacketDistributor.sendToServer(new SelectMVAssemblerRecipePayload(menu.getBlockPos(), recipe.id()));
                return true;
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    private List<Component> recipeTooltip(RecipeHolder<MVAssemblerRecipe> recipe) {
        List<Component> tooltip = new ArrayList<>();
        tooltip.add(recipe.value().result().getHoverName());
        for (MVAssemblerRecipe.CountedIngredient ingredient : recipe.value().countedIngredientsSortedByCountDescending()) {
            ItemStack[] items = ingredient.ingredient().getItems();
            Component name = items.length == 0 ? Component.literal("Unknown") : items[0].getHoverName();
            boolean hasEnough = menu.countMatchingInput(ingredient) >= ingredient.count();
            tooltip.add(Component.literal("")
                    .append(name)
                    .append(" x " + ingredient.count())
                    .withStyle(hasEnough ? ChatFormatting.GREEN : ChatFormatting.RED));
        }
        return tooltip;
    }

    private RecipeHolder<MVAssemblerRecipe> hoveredRecipe(int mouseX, int mouseY) {
        return menu.getRecipe(hoveredRecipeSlot(mouseX, mouseY));
    }

    private int hoveredRecipeSlot(double mouseX, double mouseY) {
        int localX = (int) mouseX - leftPos - MVAssemblerRecipeSelectMenu.RECIPE_GRID_X;
        int localY = (int) mouseY - topPos - MVAssemblerRecipeSelectMenu.RECIPE_GRID_Y;
        if (localX < 0 || localY < 0) {
            return -1;
        }

        int column = localX / SLOT_SIZE;
        int row = localY / SLOT_SIZE;
        if (column < 0
                || column >= MVAssemblerRecipeSelectMenu.RECIPE_COLUMNS
                || row < 0
                || row >= MVAssemblerRecipeSelectMenu.RECIPE_ROWS
                || localX % SLOT_SIZE >= 16
                || localY % SLOT_SIZE >= 16) {
            return -1;
        }

        return row * MVAssemblerRecipeSelectMenu.RECIPE_COLUMNS + column;
    }

    private boolean isPointIn(int x, int y, int width, int height, double mouseX, double mouseY) {
        return mouseX >= leftPos + x && mouseX < leftPos + x + width
                && mouseY >= topPos + y && mouseY < topPos + y + height;
    }

    private void playClickSound() {
        if (minecraft != null) {
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
        }
    }
}
