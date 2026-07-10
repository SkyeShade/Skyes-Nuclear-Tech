package com.skyeshade.skyent.compat.jei;

import com.skyeshade.skyent.SkyesNuclearTech;
import com.skyeshade.skyent.content.item.SteelFluidBarrelItem;
import com.skyeshade.skyent.content.item.SteelFluidBarrelVariants;
import com.skyeshade.skyent.content.item.LVCrusherRecipes;
import com.skyeshade.skyent.content.recipe.BrickBlastFurnaceRecipe;
import com.skyeshade.skyent.registry.ModItems;
import com.skyeshade.skyent.registry.ModRecipes;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.ingredients.subtypes.ISubtypeInterpreter;
import mezz.jei.api.ingredients.subtypes.UidContext;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import mezz.jei.api.registration.IExtraIngredientRegistration;
import mezz.jei.api.registration.ISubtypeRegistration;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.fluids.FluidStack;

import java.util.List;

@JeiPlugin
public final class SkyentJeiPlugin implements IModPlugin {
    private static final ResourceLocation PLUGIN_UID = ResourceLocation.fromNamespaceAndPath(SkyesNuclearTech.MOD_ID, "jei_plugin");

    @Override
    public ResourceLocation getPluginUid() {
        return PLUGIN_UID;
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
        registration.addRecipeCategories(
                new CoalForgeRecipeCategory(registration.getJeiHelpers().getGuiHelper()),
                new HeatingChamberRecipeCategory(registration.getJeiHelpers().getGuiHelper()),
                new IndustrialPressRecipeCategory(registration.getJeiHelpers().getGuiHelper()),
                new BrickBlastFurnaceRecipeCategory(registration.getJeiHelpers().getGuiHelper()),
                new CrusherRecipeCategory(registration.getJeiHelpers().getGuiHelper())
        );
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        registration.addRecipes(CoalForgeRecipeCategory.RECIPE_TYPE, CoalForgeRecipeCategory.getAllRecipes());
        registration.addRecipes(HeatingChamberRecipeCategory.RECIPE_TYPE, HeatingChamberRecipeCategory.getAllRecipes());
        registration.addRecipes(IndustrialPressRecipeCategory.RECIPE_TYPE, IndustrialPressRecipeCategory.getAllRecipes());
        registration.addRecipes(CrusherRecipeCategory.RECIPE_TYPE, LVCrusherRecipes.getAllRecipes());

        Level level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }

        List<BrickBlastFurnaceRecipe> recipes = level.getRecipeManager()
                .getAllRecipesFor(ModRecipes.BRICK_BLAST_FURNACE_TYPE.get())
                .stream()
                .map(RecipeHolder::value)
                .toList();
        registration.addRecipes(BrickBlastFurnaceRecipeCategory.RECIPE_TYPE, recipes);
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        registration.addRecipeCatalyst(ModItems.COAL_FORGE.get(), CoalForgeRecipeCategory.RECIPE_TYPE);
        registration.addRecipeCatalyst(ModItems.HEATING_CHAMBER.get(), HeatingChamberRecipeCategory.RECIPE_TYPE);
        registration.addRecipeCatalyst(ModItems.INDUSTRIAL_PRESS.get(), IndustrialPressRecipeCategory.RECIPE_TYPE);
        registration.addRecipeCatalyst(ModItems.BRICK_BLAST_FURNACE.get(), BrickBlastFurnaceRecipeCategory.RECIPE_TYPE);
        registration.addRecipeCatalyst(ModItems.LV_CRUSHER.get(), CrusherRecipeCategory.RECIPE_TYPE);
    }

    private static String getSteelFluidBarrelSubtype(ItemStack stack) {
        FluidStack fluid = SteelFluidBarrelItem.getContainedFluid(stack);
        if (fluid.isEmpty()) {
            return "empty";
        }

        return BuiltInRegistries.FLUID.getKey(fluid.getFluid()) + ":" + fluid.getAmount();
    }
}
