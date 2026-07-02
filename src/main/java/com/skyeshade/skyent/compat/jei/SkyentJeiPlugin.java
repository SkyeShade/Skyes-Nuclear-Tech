package com.skyeshade.skyent.compat.jei;

import com.skyeshade.skyent.SkyesNuclearTech;
import com.skyeshade.skyent.content.item.SteelFluidBarrelItem;
import com.skyeshade.skyent.content.item.SteelFluidBarrelVariants;
import com.skyeshade.skyent.registry.ModItems;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.ingredients.subtypes.ISubtypeInterpreter;
import mezz.jei.api.ingredients.subtypes.UidContext;
import mezz.jei.api.registration.IExtraIngredientRegistration;
import mezz.jei.api.registration.ISubtypeRegistration;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;

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

    private static String getSteelFluidBarrelSubtype(ItemStack stack) {
        FluidStack fluid = SteelFluidBarrelItem.getContainedFluid(stack);
        if (fluid.isEmpty()) {
            return "empty";
        }

        return BuiltInRegistries.FLUID.getKey(fluid.getFluid()) + ":" + fluid.getAmount();
    }
}
