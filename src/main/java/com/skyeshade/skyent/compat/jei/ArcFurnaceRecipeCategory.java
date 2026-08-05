package com.skyeshade.skyent.compat.jei;

import com.skyeshade.skyent.SkyesNuclearTech;
import com.skyeshade.skyent.content.blockentity.ArcFurnaceBlockEntity;
import com.skyeshade.skyent.content.recipe.ArcFurnaceMode;
import com.skyeshade.skyent.content.recipe.ArcFurnaceRecipe;
import com.skyeshade.skyent.content.recipe.ArcFurnaceRecipes;
import com.skyeshade.skyent.registry.ModItems;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.builder.ITooltipBuilder;
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
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.BlastingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.item.crafting.SmeltingRecipe;
import net.minecraft.world.level.Level;

public final class ArcFurnaceRecipeCategory implements IRecipeCategory<ArcFurnaceRecipeCategory.Display> {
    public static final ResourceLocation UID = ResourceLocation.fromNamespaceAndPath(SkyesNuclearTech.MOD_ID, "arc_furnace");
    public static final RecipeType<Display> RECIPE_TYPE = new RecipeType<>(UID, Display.class);
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            SkyesNuclearTech.MOD_ID,
            "textures/gui/jei/arc_furnace_jei.png"
    );
    private static final int TEXTURE_WIDTH = 256;
    private static final int TEXTURE_HEIGHT = 256;

    private static final int CROP_X = 8;
    private static final int CROP_Y = 45;
    private static final int WIDTH = 126;
    private static final int HEIGHT = 54;
    private static final int PROGRESS_TICKS = 40;

    private static final int INPUT_GRID_X = localX(9);
    private static final int INPUT_GRID_Y = localY(46);
    private static final int INPUT_COLUMNS = 4;
    private static final int SLOT_SPACING = 18;
    private static final int OUTPUT_SLOT_X = localX(113);
    private static final int OUTPUT_SLOT_Y = localY(73);
    private static final int MODE_ICON_X = localX(113);
    private static final int MODE_ICON_Y = localY(48);
    private static final int MODE_ICON_SIZE = 16;
    private static final int TIME_X = localX(80);
    private static final int TIME_Y = localY(45);
    private static final int TIME_WIDTH = 30;
    private static final int TIME_HEIGHT = 18;

    private static final int PROGRESS_X = localX(83);
    private static final int PROGRESS_Y = localY(73);
    private static final int PROGRESS_U = 181;
    private static final int PROGRESS_V = 104;
    private static final int PROGRESS_WIDTH = 22;
    private static final int PROGRESS_HEIGHT = 16;

    private static final int ALLOYING_ICON_U = 229;
    private static final int ALLOYING_ICON_V = 59;
    private static final int SMELTING_ICON_U = 229;
    private static final int SMELTING_ICON_V = 78;

    private final IDrawable background;
    private final IDrawable icon;

    public ArcFurnaceRecipeCategory(IGuiHelper guiHelper) {
        this.background = guiHelper.createDrawable(TEXTURE, CROP_X, CROP_Y, WIDTH, HEIGHT);
        this.icon = guiHelper.createDrawableIngredient(VanillaTypes.ITEM_STACK, new ItemStack(ModItems.ARC_FURNACE.get()));
    }

    @Override
    public RecipeType<Display> getRecipeType() {
        return RECIPE_TYPE;
    }

    @Override
    public Component getTitle() {
        return Component.translatable("jei.skyent.arc_furnace");
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
    public void setRecipe(IRecipeLayoutBuilder builder, Display recipe, IFocusGroup focuses) {
        List<CountedIngredientDisplay> inputs = recipe.inputs();
        for (int index = 0; index < Math.min(ArcFurnaceBlockEntity.INPUT_SLOT_COUNT, inputs.size()); index++) {
            CountedIngredientDisplay ingredient = inputs.get(index);
            int column = index % INPUT_COLUMNS;
            int row = index / INPUT_COLUMNS;
            builder.addSlot(RecipeIngredientRole.INPUT, INPUT_GRID_X + column * SLOT_SPACING, INPUT_GRID_Y + row * SLOT_SPACING)
                    .addItemStacks(stacksWithCount(ingredient.ingredient(), ingredient.count()));
        }

        builder.addSlot(RecipeIngredientRole.OUTPUT, OUTPUT_SLOT_X, OUTPUT_SLOT_Y)
                .addItemStack(recipe.output());
    }

    @Override
    public void draw(Display recipe, IRecipeSlotsView recipeSlotsView, GuiGraphics guiGraphics, double mouseX, double mouseY) {
        drawProgressArrow(guiGraphics);
        drawModeIcon(recipe.mode(), guiGraphics);
        drawProcessTime(recipe, guiGraphics);
    }

    @Override
    public void getTooltip(ITooltipBuilder tooltip, Display recipe, IRecipeSlotsView recipeSlotsView, double mouseX, double mouseY) {
        if (mouseX >= MODE_ICON_X && mouseX < MODE_ICON_X + MODE_ICON_SIZE
                && mouseY >= MODE_ICON_Y && mouseY < MODE_ICON_Y + MODE_ICON_SIZE) {
            tooltip.add(Component.translatable(modeTooltipKey(recipe.mode())));
        }
    }

    public static List<Display> getAllRecipes(Level level) {
        List<Display> recipes = new ArrayList<>();
        recipes.addAll(ArcFurnaceRecipes.all(level, ArcFurnaceMode.ALLOYING)
                .stream()
                .map(ArcFurnaceRecipeCategory::arcFurnaceDisplay)
                .toList());
        recipes.addAll(ArcFurnaceRecipes.all(level, ArcFurnaceMode.SMELTING)
                .stream()
                .map(ArcFurnaceRecipeCategory::arcFurnaceDisplay)
                .toList());
        recipes.addAll(inheritedCookingDisplays(level));
        return recipes;
    }

    private static Display arcFurnaceDisplay(RecipeHolder<ArcFurnaceRecipe> holder) {
        ArcFurnaceRecipe recipe = holder.value();
        return new Display(
                recipe.mode(),
                recipe.countedIngredients()
                        .stream()
                        .map(ingredient -> new CountedIngredientDisplay(ingredient.ingredient(), ingredient.count()))
                        .toList(),
                recipe.result(),
                recipe.processTime(),
                holder.id()
        );
    }

    private static List<Display> inheritedCookingDisplays(Level level) {
        Map<String, Display> displays = new LinkedHashMap<>();
        addCookingDisplays(displays, level.getRecipeManager().getAllRecipesFor(net.minecraft.world.item.crafting.RecipeType.BLASTING), level);
        addCookingDisplays(displays, level.getRecipeManager().getAllRecipesFor(net.minecraft.world.item.crafting.RecipeType.SMELTING), level);
        return displays.values()
                .stream()
                .sorted(Comparator.comparing(display -> display.id().toString()))
                .toList();
    }

    private static <T extends AbstractCookingRecipe> void addCookingDisplays(Map<String, Display> displays, List<RecipeHolder<T>> holders, Level level) {
        for (RecipeHolder<T> holder : holders.stream().sorted(Comparator.comparing(recipe -> recipe.id().toString())).toList()) {
            Display display = cookingDisplay(holder, level);
            if (display == null) {
                continue;
            }
            String key = cookingDedupKey(display);
            Display existing = displays.get(key);
            if (existing == null || display.processTime() < existing.processTime()) {
                displays.put(key, display);
            }
        }
    }

    private static Display cookingDisplay(RecipeHolder<? extends AbstractCookingRecipe> holder, Level level) {
        AbstractCookingRecipe recipe = holder.value();
        if (recipe.getIngredients().isEmpty()) {
            return null;
        }

        Ingredient ingredient = recipe.getIngredients().getFirst();
        ItemStack[] samples = ingredient.getItems();
        if (samples.length == 0 || samples[0].isEmpty()) {
            return null;
        }

        ItemStack result = recipe.assemble(new SingleRecipeInput(samples[0]), level.registryAccess());
        if (result.isEmpty()) {
            return null;
        }

        return new Display(
                ArcFurnaceMode.SMELTING,
                List.of(new CountedIngredientDisplay(ingredient, 1)),
                result,
                ArcFurnaceBlockEntity.inheritedSmeltingProcessTime(recipe),
                holder.id()
        );
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

    private static void drawModeIcon(ArcFurnaceMode mode, GuiGraphics guiGraphics) {
        int sourceY = mode == ArcFurnaceMode.ALLOYING ? ALLOYING_ICON_V : SMELTING_ICON_V;
        guiGraphics.blit(
                TEXTURE,
                MODE_ICON_X,
                MODE_ICON_Y,
                ALLOYING_ICON_U,
                sourceY,
                MODE_ICON_SIZE,
                MODE_ICON_SIZE,
                TEXTURE_WIDTH,
                TEXTURE_HEIGHT
        );
    }

    private static void drawProcessTime(Display recipe, GuiGraphics guiGraphics) {
        String text = formatProcessTime(recipe.processTime());
        var font = Minecraft.getInstance().font;
        int x = TIME_X + Math.max(0, (TIME_WIDTH - font.width(text)) / 2);
        guiGraphics.drawString(font, text, x, TIME_Y + 5, 0x404040, false);
    }

    private static String formatProcessTime(int ticks) {
        int seconds = Math.max(1, (ticks + 19) / 20);
        return seconds + "s";
    }

    private static String cookingDedupKey(Display display) {
        String inputKey = display.inputs().isEmpty() ? "" : ingredientKey(display.inputs().getFirst().ingredient());
        return inputKey + "->" + stackKey(display.output());
    }

    private static String ingredientKey(Ingredient ingredient) {
        List<String> ids = new ArrayList<>();
        for (ItemStack stack : ingredient.getItems()) {
            if (!stack.isEmpty()) {
                ids.add(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
            }
        }
        ids.sort(String::compareTo);
        return String.join("|", ids);
    }

    private static String stackKey(ItemStack stack) {
        return BuiltInRegistries.ITEM.getKey(stack.getItem()) + "x" + stack.getCount();
    }

    private static String modeTooltipKey(ArcFurnaceMode mode) {
        return mode == ArcFurnaceMode.ALLOYING
                ? "jei.skyent.arc_furnace.mode.alloying"
                : "jei.skyent.arc_furnace.mode.smelting";
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

    public record Display(ArcFurnaceMode mode, List<CountedIngredientDisplay> inputs, ItemStack output, int processTime, ResourceLocation id) {
        public Display {
            inputs = List.copyOf(inputs);
            output = output.copy();
            processTime = Math.max(1, processTime);
        }
    }

    public record CountedIngredientDisplay(Ingredient ingredient, int count) {
        public CountedIngredientDisplay {
            count = Math.max(1, count);
        }
    }
}
