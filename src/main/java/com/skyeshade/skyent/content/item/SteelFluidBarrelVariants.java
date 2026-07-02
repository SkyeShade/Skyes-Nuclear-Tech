package com.skyeshade.skyent.content.item;

import com.skyeshade.skyent.SkyesNuclearTech;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidType;

public final class SteelFluidBarrelVariants {
    private SteelFluidBarrelVariants() {
    }

    public static List<ItemStack> createFilledVariants() {
        Set<FluidType> seenFluidTypes = new HashSet<>();
        List<Fluid> fluids = new ArrayList<>();
        BuiltInRegistries.FLUID.forEach(fluid -> {
            if (shouldIncludeFluid(fluid, seenFluidTypes)) {
                fluids.add(fluid);
            }
        });
        fluids.sort(Comparator
                .comparingInt(SteelFluidBarrelVariants::creativeFluidPriority)
                .thenComparing(fluid -> BuiltInRegistries.FLUID.getKey(fluid).toString()));

        List<ItemStack> variants = new ArrayList<>();
        for (Fluid fluid : fluids) {
            ItemStack barrel = SteelFluidBarrelItem.createFilledBarrel(new FluidStack(fluid, SteelFluidBarrelItem.CAPACITY_MB));
            if (!barrel.isEmpty()) {
                variants.add(barrel);
            }
        }
        return variants;
    }

    private static boolean shouldIncludeFluid(Fluid fluid, Set<FluidType> seenFluidTypes) {
        if (fluid == Fluids.EMPTY) {
            return false;
        }

        ResourceLocation id = BuiltInRegistries.FLUID.getKey(fluid);
        String path = id.getPath();
        if (path.startsWith("flowing_") || path.endsWith("_flowing")) {
            return false;
        }

        return seenFluidTypes.add(fluid.getFluidType());
    }

    private static int creativeFluidPriority(Fluid fluid) {
        if (fluid == Fluids.WATER) {
            return 0;
        }
        if (fluid == Fluids.LAVA) {
            return 1;
        }

        ResourceLocation id = BuiltInRegistries.FLUID.getKey(fluid);
        return SkyesNuclearTech.MOD_ID.equals(id.getNamespace()) ? 2 : 3;
    }
}
