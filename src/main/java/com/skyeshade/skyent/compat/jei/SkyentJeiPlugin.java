package com.skyeshade.skyent.compat.jei;

import com.skyeshade.skyent.SkyesNuclearTech;
import com.skyeshade.skyent.client.screen.MVAssemblerScreen;
import com.skyeshade.skyent.content.item.ForgingAnvilRecipes;
import com.skyeshade.skyent.content.item.SteelFluidBarrelItem;
import com.skyeshade.skyent.content.item.SteelFluidBarrelVariants;
import com.skyeshade.skyent.content.item.LVCrusherRecipes;
import com.skyeshade.skyent.content.recipe.BrickBlastFurnaceRecipe;
import com.skyeshade.skyent.content.recipe.CentrifugeRecipe;
import com.skyeshade.skyent.content.recipe.ChemicalReactorRecipe;
import com.skyeshade.skyent.registry.ModItems;
import com.skyeshade.skyent.registry.ModRecipes;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.ingredients.subtypes.ISubtypeInterpreter;
import mezz.jei.api.ingredients.subtypes.UidContext;
import mezz.jei.api.gui.handlers.IGuiContainerHandler;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import mezz.jei.api.registration.IExtraIngredientRegistration;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import mezz.jei.api.registration.ISubtypeRegistration;
import mezz.jei.api.runtime.config.IJeiConfigFile;
import mezz.jei.api.runtime.config.IJeiConfigManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.fluids.FluidStack;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@JeiPlugin
public final class SkyentJeiPlugin implements IModPlugin {
    private static final ResourceLocation PLUGIN_UID = ResourceLocation.fromNamespaceAndPath(SkyesNuclearTech.MOD_ID, "jei_plugin");
    private static final String JEI_CLIENT_CONFIG_FILE = "jei-client.ini";
    private static final String JEI_RECIPE_CATEGORY_SORT_ORDER_FILE = "recipe-category-sort-order.ini";
    private static final String JEI_BLOCK_TAG_RECIPE_CATEGORY = "minecraft:tag_recipes/block";
    private static final String JEI_FLUID_TAG_RECIPE_CATEGORY = "minecraft:tag_recipes/fluid";
    private static final String JEI_ITEM_TAG_RECIPE_CATEGORY = "minecraft:tag_recipes/item";
    private static final String JEI_INFORMATION_CATEGORY = "jei:information";
    private static final List<String> SKYENT_CUSTOM_CATEGORY_ORDER = List.of(
            CrusherRecipeCategory.UID.toString(),
            IndustrialPressRecipeCategory.UID.toString(),
            RollingMillRecipeCategory.UID.toString(),
            WireMillRecipeCategory.UID.toString(),
            HeatingChamberRecipeCategory.UID.toString(),
            MVAssemblerRecipeCategory.UID.toString(),
            ChemicalReactorRecipeCategory.UID.toString(),
            CentrifugeRecipeCategory.UID.toString(),
            ElectricBlastFurnaceRecipeCategory.UID.toString(),
            BrickBlastFurnaceRecipeCategory.UID.toString(),
            CoalForgeRecipeCategory.UID.toString()
    );
    private static final Set<String> JEI_TAG_AND_INFO_CATEGORY_ANCHORS = Set.of(
            JEI_BLOCK_TAG_RECIPE_CATEGORY,
            JEI_FLUID_TAG_RECIPE_CATEGORY,
            JEI_ITEM_TAG_RECIPE_CATEGORY,
            JEI_INFORMATION_CATEGORY
    );
    private static final List<String> DEFAULT_VANILLA_CATEGORY_PREFIX = List.of(
            "minecraft:crafting",
            "minecraft:anvil",
            "minecraft:blasting",
            "minecraft:brewing",
            "minecraft:campfire_cooking",
            "minecraft:compostable",
            "minecraft:fuel",
            "minecraft:smelting",
            "minecraft:smithing",
            "minecraft:smoking",
            "minecraft:stonecutting"
    );
    private static final List<String> SKYENT_EARLY_PROCESSING_CATEGORY_ORDER = List.of(
            ForgingAnvilRecipeCategory.UID.toString()
    );
    private static final List<String> DEFAULT_CATEGORY_SUFFIX = List.of(
            JEI_BLOCK_TAG_RECIPE_CATEGORY,
            JEI_FLUID_TAG_RECIPE_CATEGORY,
            JEI_ITEM_TAG_RECIPE_CATEGORY,
            JEI_INFORMATION_CATEGORY
    );

    @Override
    public ResourceLocation getPluginUid() {
        return PLUGIN_UID;
    }

    @Override
    public void onConfigManagerAvailable(IJeiConfigManager configManager) {
        // JEI 1.21.1 does not expose a public category-priority method on IRecipeCategory.
        // It sorts recipe categories from recipe-category-sort-order.ini before focus results are shown.
        // Seed/repair that order so SkyeNTM custom recipe panels appear before JEI's tag/info panels without hiding them.
        findJeiConfigDirectory(configManager).ifPresent(SkyentJeiPlugin::prioritizeSkyentCustomCategories);
    }

    @Override
    public void registerItemSubtypes(ISubtypeRegistration registration) {
        registration.registerSubtypeInterpreter(VanillaTypes.ITEM_STACK, ModItems.STEEL_FLUID_BARREL.get(), new ISubtypeInterpreter<>() {
            @Override
            public Object getSubtypeData(ItemStack stack, UidContext context) {
                return getSteelFluidBarrelSubtype(stack);
            }

            @Override
            public String getLegacyStringSubtypeInfo(ItemStack stack, UidContext context) {
                return getSteelFluidBarrelSubtype(stack);
            }
        });
    }

    @Override
    public void registerExtraIngredients(IExtraIngredientRegistration registration) {
        registration.addExtraItemStacks(SteelFluidBarrelVariants.createFilledVariants());
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        // JEI's tag/group recipe entries are registered by JEI itself, not by this plugin.
        // Keep SkyeNTM custom categories first so JEI honors our order wherever it uses registration order.
        registration.addRecipeCategories(
                new ForgingAnvilRecipeCategory(registration.getJeiHelpers().getGuiHelper()),
                new CrusherRecipeCategory(registration.getJeiHelpers().getGuiHelper()),
                new IndustrialPressRecipeCategory(registration.getJeiHelpers().getGuiHelper()),
                new RollingMillRecipeCategory(registration.getJeiHelpers().getGuiHelper()),
                new WireMillRecipeCategory(registration.getJeiHelpers().getGuiHelper()),
                new HeatingChamberRecipeCategory(registration.getJeiHelpers().getGuiHelper()),
                new MVAssemblerRecipeCategory(registration.getJeiHelpers().getGuiHelper()),
                new ChemicalReactorRecipeCategory(registration.getJeiHelpers().getGuiHelper()),
                new CentrifugeRecipeCategory(registration.getJeiHelpers().getGuiHelper()),
                new ElectricBlastFurnaceRecipeCategory(registration.getJeiHelpers().getGuiHelper()),
                new BrickBlastFurnaceRecipeCategory(registration.getJeiHelpers().getGuiHelper()),
                new CoalForgeRecipeCategory(registration.getJeiHelpers().getGuiHelper())
        );
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        registration.addRecipes(ForgingAnvilRecipeCategory.RECIPE_TYPE, ForgingAnvilRecipes.getAllRecipes());
        registration.addRecipes(CrusherRecipeCategory.RECIPE_TYPE, LVCrusherRecipes.getAllRecipes());
        registration.addRecipes(IndustrialPressRecipeCategory.RECIPE_TYPE, IndustrialPressRecipeCategory.getAllRecipes());
        registration.addRecipes(RollingMillRecipeCategory.RECIPE_TYPE, RollingMillRecipeCategory.getAllRecipes());
        registration.addRecipes(WireMillRecipeCategory.RECIPE_TYPE, WireMillRecipeCategory.getAllRecipes());
        registration.addRecipes(HeatingChamberRecipeCategory.RECIPE_TYPE, HeatingChamberRecipeCategory.getAllRecipes());

        Level level = Minecraft.getInstance().level;
        if (level != null) {
            List<com.skyeshade.skyent.content.recipe.MVAssemblerRecipe> assemblerRecipes = level.getRecipeManager()
                    .getAllRecipesFor(ModRecipes.MV_ASSEMBLER_TYPE.get())
                    .stream()
                    .map(RecipeHolder::value)
                    .toList();
            registration.addRecipes(MVAssemblerRecipeCategory.RECIPE_TYPE, assemblerRecipes);

            List<ChemicalReactorRecipe> chemicalReactorRecipes = level.getRecipeManager()
                    .getAllRecipesFor(ModRecipes.CHEMICAL_REACTOR_TYPE.get())
                    .stream()
                    .map(RecipeHolder::value)
                    .toList();
            registration.addRecipes(ChemicalReactorRecipeCategory.RECIPE_TYPE, chemicalReactorRecipes);

            List<CentrifugeRecipe> centrifugeRecipes = level.getRecipeManager()
                    .getAllRecipesFor(ModRecipes.CENTRIFUGE_TYPE.get())
                    .stream()
                    .map(RecipeHolder::value)
                    .toList();
            registration.addRecipes(CentrifugeRecipeCategory.RECIPE_TYPE, centrifugeRecipes);
            registration.addRecipes(ElectricBlastFurnaceRecipeCategory.RECIPE_TYPE, ElectricBlastFurnaceRecipeCategory.getAllRecipes(level));

            List<BrickBlastFurnaceRecipe> recipes = level.getRecipeManager()
                    .getAllRecipesFor(ModRecipes.BRICK_BLAST_FURNACE_TYPE.get())
                    .stream()
                    .map(RecipeHolder::value)
                    .toList();
            registration.addRecipes(BrickBlastFurnaceRecipeCategory.RECIPE_TYPE, recipes);
        }

        registration.addRecipes(CoalForgeRecipeCategory.RECIPE_TYPE, CoalForgeRecipeCategory.getAllRecipes());
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        registration.addRecipeCatalyst(ModItems.FORGING_ANVIL.get(), ForgingAnvilRecipeCategory.RECIPE_TYPE);
        registration.addRecipeCatalyst(ModItems.LV_CRUSHER.get(), CrusherRecipeCategory.RECIPE_TYPE);
        registration.addRecipeCatalyst(ModItems.INDUSTRIAL_PRESS.get(), IndustrialPressRecipeCategory.RECIPE_TYPE);
        registration.addRecipeCatalyst(ModItems.ROLLING_MILL.get(), RollingMillRecipeCategory.RECIPE_TYPE);
        registration.addRecipeCatalyst(ModItems.WIRE_MILL.get(), WireMillRecipeCategory.RECIPE_TYPE);
        registration.addRecipeCatalyst(ModItems.HEATING_CHAMBER.get(), HeatingChamberRecipeCategory.RECIPE_TYPE);
        registration.addRecipeCatalyst(ModItems.MV_ASSEMBLER.get(), MVAssemblerRecipeCategory.RECIPE_TYPE);
        registration.addRecipeCatalyst(ModItems.MV_CHEMICAL_REACTOR.get(), ChemicalReactorRecipeCategory.RECIPE_TYPE);
        registration.addRecipeCatalyst(ModItems.CENTRIFUGE.get(), CentrifugeRecipeCategory.RECIPE_TYPE);
        registration.addRecipeCatalyst(ModItems.ELECTRIC_BLAST_FURNACE.get(), ElectricBlastFurnaceRecipeCategory.RECIPE_TYPE);
        registration.addRecipeCatalyst(ModItems.BRICK_BLAST_FURNACE.get(), BrickBlastFurnaceRecipeCategory.RECIPE_TYPE);
        registration.addRecipeCatalyst(ModItems.COAL_FORGE.get(), CoalForgeRecipeCategory.RECIPE_TYPE);
    }

    @Override
    public void registerGuiHandlers(IGuiHandlerRegistration registration) {
        registration.addGuiContainerHandler(MVAssemblerScreen.class, new IGuiContainerHandler<>() {
            @Override
            public List<Rect2i> getGuiExtraAreas(MVAssemblerScreen screen) {
                return screen.getJeiExtraAreas();
            }
        });
    }

    private static String getSteelFluidBarrelSubtype(ItemStack stack) {
        FluidStack fluid = SteelFluidBarrelItem.getContainedFluid(stack);
        if (fluid.isEmpty()) {
            return "empty";
        }

        return BuiltInRegistries.FLUID.getKey(fluid.getFluid()) + ":" + fluid.getAmount();
    }

    private static Optional<Path> findJeiConfigDirectory(IJeiConfigManager configManager) {
        Optional<Path> clientConfigDirectory = configManager.getConfigFiles()
                .stream()
                .map(IJeiConfigFile::getPath)
                .filter(path -> path.getFileName() != null && JEI_CLIENT_CONFIG_FILE.equals(path.getFileName().toString()))
                .map(Path::getParent)
                .filter(path -> path != null)
                .findFirst();
        if (clientConfigDirectory.isPresent()) {
            return clientConfigDirectory;
        }

        return configManager.getConfigFiles()
                .stream()
                .map(IJeiConfigFile::getPath)
                .map(Path::getParent)
                .filter(path -> path != null)
                .findFirst();
    }

    private static void prioritizeSkyentCustomCategories(Path jeiConfigDirectory) {
        Path sortOrderFile = jeiConfigDirectory.resolve(JEI_RECIPE_CATEGORY_SORT_ORDER_FILE);
        try {
            List<String> currentOrder = Files.exists(sortOrderFile)
                    ? readCategoryOrder(sortOrderFile)
                    : createDefaultCategoryOrder();
            List<String> reordered = withSkyentCategoriesBeforeTagAndInfoCategories(currentOrder);
            if (reordered.equals(currentOrder)) {
                return;
            }

            Files.createDirectories(sortOrderFile.getParent());
            Files.write(sortOrderFile, reordered, StandardCharsets.UTF_8);
            SkyesNuclearTech.LOGGER.info("Updated JEI recipe category order so SkyeNTM custom categories appear before tag/info recipe panels.");
        } catch (IOException exception) {
            SkyesNuclearTech.LOGGER.warn("Failed to update JEI recipe category order for SkyeNTM custom categories.", exception);
        }
    }

    private static List<String> readCategoryOrder(Path sortOrderFile) throws IOException {
        return Files.readAllLines(sortOrderFile, StandardCharsets.UTF_8)
                .stream()
                .map(String::trim)
                .filter(line -> !line.isEmpty())
                .toList();
    }

    private static List<String> createDefaultCategoryOrder() {
        List<String> order = new ArrayList<>();
        order.addAll(DEFAULT_VANILLA_CATEGORY_PREFIX);
        order.addAll(SKYENT_EARLY_PROCESSING_CATEGORY_ORDER);
        order.addAll(SKYENT_CUSTOM_CATEGORY_ORDER);
        order.addAll(DEFAULT_CATEGORY_SUFFIX);
        return order;
    }

    private static List<String> withSkyentCategoriesBeforeTagAndInfoCategories(List<String> currentOrder) {
        List<String> skyentCategoryOrder = orderedSkyentCategories();
        Set<String> skyentCategories = new LinkedHashSet<>(skyentCategoryOrder);
        List<String> orderWithoutSkyentCategories = currentOrder.stream()
                .filter(category -> !skyentCategories.contains(category))
                .toList();
        List<String> reordered = new ArrayList<>();
        boolean inserted = false;

        for (String category : orderWithoutSkyentCategories) {
            if (!inserted && JEI_TAG_AND_INFO_CATEGORY_ANCHORS.contains(category)) {
                reordered.addAll(skyentCategoryOrder);
                inserted = true;
            }
            reordered.add(category);
        }

        if (!inserted) {
            reordered.addAll(skyentCategoryOrder);
        }

        return distinctOrder(reordered);
    }

    private static List<String> orderedSkyentCategories() {
        List<String> order = new ArrayList<>();
        order.addAll(SKYENT_EARLY_PROCESSING_CATEGORY_ORDER);
        order.addAll(SKYENT_CUSTOM_CATEGORY_ORDER);
        return order;
    }

    private static List<String> distinctOrder(List<String> categories) {
        return new ArrayList<>(new LinkedHashSet<>(categories));
    }
}
