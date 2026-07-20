package com.skyeshade.skyent.client.screen;

import com.skyeshade.skyent.SkyesNuclearTech;
import com.skyeshade.skyent.content.blockentity.MVAssemblerBlockEntity;
import com.skyeshade.skyent.content.energy.EnergyUnits;
import com.skyeshade.skyent.content.menu.MVAssemblerMenu;
import com.skyeshade.skyent.content.recipe.MVAssemblerRecipe;
import com.skyeshade.skyent.content.recipe.MVAssemblerRecipes;
import com.skyeshade.skyent.network.SelectMVAssemblerRecipePayload;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.neoforge.network.PacketDistributor;

public class MVAssemblerScreen extends AbstractContainerScreen<MVAssemblerMenu> {
    private static final int BASE_GUI_WIDTH = 176;
    private static final int BASE_GUI_HEIGHT = 166;
    private static final int FULL_GUI_WIDTH = 214;
    private static final int JEI_BUFFER_LEFT = 0;
    private static final int JEI_BUFFER_RIGHT = 0;
    public static final int GUI_TEXTURE_WIDTH = 256;
    public static final int GUI_TEXTURE_HEIGHT = 256;

    public static final int ENERGY_BAR_X = 155;
    public static final int ENERGY_BAR_Y = 14;
    public static final int ENERGY_BAR_WIDTH = 16;
    public static final int ENERGY_BAR_HEIGHT = 57;
    public static final int ENERGY_BAR_U = 240;
    public static final int ENERGY_BAR_V = 16;

    public static final int PROGRESS_ARROW_X = 85;
    public static final int PROGRESS_ARROW_Y = 35;
    public static final int PROGRESS_ARROW_U = 234;
    public static final int PROGRESS_ARROW_V = 0;
    public static final int PROGRESS_ARROW_WIDTH = 22;
    public static final int PROGRESS_ARROW_HEIGHT = 16;
    private static final int TITLE_X = 8;
    private static final int TITLE_Y = 6;
    private static final int TITLE_COLOR = 0x404040;
    private static final int RECIPE_GRID_X = 8;
    private static final int RECIPE_GRID_Y = 17;
    private static final int RECIPE_COLUMNS = 9;
    private static final int RECIPE_ROWS = 3;
    private static final int RECIPE_SLOT_COUNT = RECIPE_COLUMNS * RECIPE_ROWS;
    private static final int SLOT_SIZE = 18;

    public static final ResourceLocation ASSEMBLER_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            SkyesNuclearTech.MOD_ID,
            "textures/gui/assembler.png"
    );
    private static final ResourceLocation RECIPE_SELECT_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            SkyesNuclearTech.MOD_ID,
            "textures/gui/assembler_recipe_select.png"
    );

    private boolean selectingRecipe;

    public MVAssemblerScreen(MVAssemblerMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        imageWidth = BASE_GUI_WIDTH;
        imageHeight = BASE_GUI_HEIGHT;
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        menu.setRecipeSelectMode(selectingRecipe);
        super.render(guiGraphics, mouseX, mouseY, partialTick);

        RecipeHolder<MVAssemblerRecipe> hoveredRecipe = selectingRecipe ? hoveredRecipe(mouseX, mouseY) : null;
        if (hoveredRecipe != null) {
            guiGraphics.renderComponentTooltip(font, recipeTooltip(hoveredRecipe), mouseX, mouseY);
        } else {
            renderTooltip(guiGraphics, mouseX, mouseY);
        }

        if (!selectingRecipe && isHovering(ENERGY_BAR_X, ENERGY_BAR_Y, ENERGY_BAR_WIDTH, ENERGY_BAR_HEIGHT, mouseX, mouseY)) {
            guiGraphics.renderComponentTooltip(font, energyTooltip(), mouseX, mouseY);
        } else if (isPointIn(MVAssemblerMenu.SELECTED_RECIPE_X, MVAssemblerMenu.SELECTED_RECIPE_Y, 16, 16, mouseX, mouseY)) {
            guiGraphics.renderComponentTooltip(font, selectedRecipeTooltip(), mouseX, mouseY);
        }
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        ResourceLocation texture = selectingRecipe ? RECIPE_SELECT_TEXTURE : ASSEMBLER_TEXTURE;
        guiGraphics.blit(texture, leftPos, topPos, 0, 0, FULL_GUI_WIDTH, BASE_GUI_HEIGHT, GUI_TEXTURE_WIDTH, GUI_TEXTURE_HEIGHT);
        if (selectingRecipe) {
            renderRecipeGrid(guiGraphics);
            renderRecipeHoverHighlight(guiGraphics, mouseX, mouseY);
        } else {
            renderEnergyBar(guiGraphics);
            renderProgressArrow(guiGraphics);
        }
        renderSelectedRecipeGhost(guiGraphics);
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        guiGraphics.drawString(font, Component.literal("Assembler"), TITLE_X, TITLE_Y, TITLE_COLOR, false);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && isPointIn(MVAssemblerMenu.RECIPE_BUTTON_X, MVAssemblerMenu.RECIPE_BUTTON_Y, MVAssemblerMenu.RECIPE_BUTTON_SIZE, MVAssemblerMenu.RECIPE_BUTTON_SIZE, mouseX, mouseY)) {
            playClickSound();
            selectingRecipe = !selectingRecipe;
            return true;
        }

        if (button == 0 && selectingRecipe) {
            RecipeHolder<MVAssemblerRecipe> recipe = hoveredRecipe(mouseX, mouseY);
            if (recipe != null) {
                playClickSound();
                PacketDistributor.sendToServer(new SelectMVAssemblerRecipePayload(menu.getBlockPos(), recipe.id()));
                selectingRecipe = false;
                return true;
            }

        }

        menu.setRecipeSelectMode(selectingRecipe);
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    protected void slotClicked(Slot slot, int slotId, int mouseButton, ClickType type) {
        if (selectingRecipe && (slot == null || menu.isMachineSlot(slotId) || type == ClickType.QUICK_MOVE)) {
            return;
        }
        super.slotClicked(slot, slotId, mouseButton, type);
    }

    private void renderRecipeGrid(GuiGraphics guiGraphics) {
        List<RecipeHolder<MVAssemblerRecipe>> recipes = recipes();
        for (int index = 0; index < Math.min(RECIPE_SLOT_COUNT, recipes.size()); index++) {
            int x = leftPos + RECIPE_GRID_X + index % RECIPE_COLUMNS * SLOT_SIZE;
            int y = topPos + RECIPE_GRID_Y + index / RECIPE_COLUMNS * SLOT_SIZE;
            ItemStack result = recipes.get(index).value().result();
            guiGraphics.renderFakeItem(result, x, y);
            guiGraphics.renderItemDecorations(font, result, x, y);
        }
    }

    private void renderRecipeHoverHighlight(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        if (hoveredRecipe(mouseX, mouseY) == null) {
            return;
        }

        int localX = mouseX - leftPos - RECIPE_GRID_X;
        int localY = mouseY - topPos - RECIPE_GRID_Y;
        int x = leftPos + RECIPE_GRID_X + localX / SLOT_SIZE * SLOT_SIZE;
        int y = topPos + RECIPE_GRID_Y + localY / SLOT_SIZE * SLOT_SIZE;
        guiGraphics.fillGradient(x, y, x + 16, y + 16, 0x80FFFFFF, 0x80FFFFFF);
    }

    private void renderEnergyBar(GuiGraphics guiGraphics) {
        int stored = menu.getEnergyStoredRJ();
        if (stored <= 0) {
            return;
        }

        int maxEnergy = Math.max(1, menu.getMaxEnergyStoredRJ());
        int filledHeight = stored * ENERGY_BAR_HEIGHT / maxEnergy;
        guiGraphics.blit(
                ASSEMBLER_TEXTURE,
                leftPos + ENERGY_BAR_X,
                topPos + ENERGY_BAR_Y + ENERGY_BAR_HEIGHT - filledHeight,
                ENERGY_BAR_U,
                ENERGY_BAR_V + ENERGY_BAR_HEIGHT - filledHeight,
                ENERGY_BAR_WIDTH,
                filledHeight,
                GUI_TEXTURE_WIDTH,
                GUI_TEXTURE_HEIGHT
        );
    }

    private void renderProgressArrow(GuiGraphics guiGraphics) {
        int maxProgress = menu.getMaxProgress();
        if (maxProgress <= 0) {
            return;
        }

        int filledWidth = menu.getProgress() * PROGRESS_ARROW_WIDTH / maxProgress;
        if (filledWidth <= 0) {
            return;
        }

        guiGraphics.blit(
                ASSEMBLER_TEXTURE,
                leftPos + PROGRESS_ARROW_X,
                topPos + PROGRESS_ARROW_Y,
                PROGRESS_ARROW_U,
                PROGRESS_ARROW_V,
                filledWidth,
                PROGRESS_ARROW_HEIGHT,
                GUI_TEXTURE_WIDTH,
                GUI_TEXTURE_HEIGHT
        );
    }

    private void renderSelectedRecipeGhost(GuiGraphics guiGraphics) {
        RecipeHolder<MVAssemblerRecipe> recipe = selectedRecipe();
        if (recipe != null) {
            guiGraphics.renderFakeItem(recipe.value().result(), leftPos + MVAssemblerMenu.SELECTED_RECIPE_X, topPos + MVAssemblerMenu.SELECTED_RECIPE_Y);
            guiGraphics.renderItemDecorations(font, recipe.value().result(), leftPos + MVAssemblerMenu.SELECTED_RECIPE_X, topPos + MVAssemblerMenu.SELECTED_RECIPE_Y);
        }
    }

    private List<Component> energyTooltip() {
        return List.of(
                Component.literal(menu.getEnergyStoredRJ() + " / " + menu.getMaxEnergyStoredRJ() + " " + EnergyUnits.UNIT).withStyle(ChatFormatting.RED),
                Component.literal("Usage: " + menu.getCurrentEnergyUsage() + " " + EnergyUnits.UNIT_PER_TICK).withStyle(ChatFormatting.RED),
                Component.literal("Required: " + MVAssemblerBlockEntity.REQUIRED_TIER.displayName()).withStyle(ChatFormatting.RED),
                Component.literal("Current: " + MVAssemblerBlockEntity.RUNNING_CURRENT_AMPS + " A").withStyle(ChatFormatting.RED)
        );
    }

    private List<Component> selectedRecipeTooltip() {
        RecipeHolder<MVAssemblerRecipe> recipe = selectedRecipe();
        if (recipe == null) {
            return List.of(Component.literal("No recipe selected").withStyle(ChatFormatting.GRAY));
        }

        return recipeTooltip(recipe);
    }

    private List<Component> recipeTooltip(RecipeHolder<MVAssemblerRecipe> recipe) {
        List<Component> tooltip = new ArrayList<>();
        tooltip.add(recipe.value().result().getHoverName());
        for (MVAssemblerRecipe.CountedIngredient ingredient : recipe.value().countedIngredientsSortedByCountDescending()) {
            ItemStack[] items = ingredient.ingredient().getItems();
            Component name = items.length == 0 ? Component.literal("Unknown") : items[0].getHoverName();
            boolean hasEnough = menu.getBlockEntity().countMatchingInput(ingredient) >= ingredient.count();
            tooltip.add(Component.literal("")
                    .append(name)
                    .append(" x " + ingredient.count())
                    .withStyle(hasEnough ? ChatFormatting.GREEN : ChatFormatting.RED));
        }
        return tooltip;
    }

    private RecipeHolder<MVAssemblerRecipe> hoveredRecipe(double mouseX, double mouseY) {
        int localX = (int) mouseX - leftPos - RECIPE_GRID_X;
        int localY = (int) mouseY - topPos - RECIPE_GRID_Y;
        if (localX < 0 || localY < 0) {
            return null;
        }

        int column = localX / SLOT_SIZE;
        int row = localY / SLOT_SIZE;
        if (column < 0
                || column >= RECIPE_COLUMNS
                || row < 0
                || row >= RECIPE_ROWS
                || localX % SLOT_SIZE >= 16
                || localY % SLOT_SIZE >= 16) {
            return null;
        }

        int index = row * RECIPE_COLUMNS + column;
        List<RecipeHolder<MVAssemblerRecipe>> recipes = recipes();
        return index >= 0 && index < recipes.size() ? recipes.get(index) : null;
    }

    private RecipeHolder<MVAssemblerRecipe> selectedRecipe() {
        int index = menu.getSelectedRecipeIndex();
        List<RecipeHolder<MVAssemblerRecipe>> recipes = recipes();
        return index >= 0 && index < recipes.size() ? recipes.get(index) : null;
    }

    private List<RecipeHolder<MVAssemblerRecipe>> recipes() {
        return minecraft == null || minecraft.level == null ? List.of() : MVAssemblerRecipes.all(minecraft.level);
    }

    private void playClickSound() {
        if (minecraft != null) {
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
        }
    }

    public List<Rect2i> getJeiExtraAreas() {
        return List.of(
                new Rect2i(leftPos - JEI_BUFFER_LEFT, topPos, JEI_BUFFER_LEFT, BASE_GUI_HEIGHT),
                new Rect2i(
                        leftPos + BASE_GUI_WIDTH,
                        topPos,
                        FULL_GUI_WIDTH - BASE_GUI_WIDTH + JEI_BUFFER_RIGHT,
                        BASE_GUI_HEIGHT
                )
        );
    }

    private boolean isPointIn(int x, int y, int width, int height, double mouseX, double mouseY) {
        return mouseX >= leftPos + x && mouseX < leftPos + x + width
                && mouseY >= topPos + y && mouseY < topPos + y + height;
    }
}
