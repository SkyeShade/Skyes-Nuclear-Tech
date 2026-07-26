package com.skyeshade.skyent.registry;

import com.skyeshade.skyent.SkyesNuclearTech;
import com.skyeshade.skyent.content.fluid.MoltenCoriumFluid;
import com.skyeshade.skyent.content.fluid.SkyentChemicalFluid;
import com.skyeshade.skyent.content.fluid.SteamFluid;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.BaseFlowingFluid;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModFluids {
    private static final DeferredRegister<Fluid> FLUIDS = DeferredRegister.create(
            BuiltInRegistries.FLUID,
            SkyesNuclearTech.MOD_ID
    );

    public static final DeferredHolder<Fluid, MoltenCoriumFluid.Source> MOLTEN_CORIUM = FLUIDS.register(
            "molten_corium",
            MoltenCoriumFluid.Source::new
    );

    public static final DeferredHolder<Fluid, MoltenCoriumFluid.Flowing> FLOWING_MOLTEN_CORIUM = FLUIDS.register(
            "flowing_molten_corium",
            MoltenCoriumFluid.Flowing::new
    );

    public static final DeferredHolder<Fluid, SteamFluid.Source> STEAM = FLUIDS.register(
            "steam",
            SteamFluid.Source::new
    );

    public static final DeferredHolder<Fluid, SteamFluid.Flowing> FLOWING_STEAM = FLUIDS.register(
            "flowing_steam",
            SteamFluid.Flowing::new
    );

    public static final DeferredHolder<Fluid, SkyentChemicalFluid.Source> SULFURIC_ACID = FLUIDS.register(
            "sulfuric_acid",
            () -> new SkyentChemicalFluid.Source(chemicalProperties(ModFluidTypes.SULFURIC_ACID, "sulfuric_acid", "flowing_sulfuric_acid"))
    );

    public static final DeferredHolder<Fluid, SkyentChemicalFluid.Flowing> FLOWING_SULFURIC_ACID = FLUIDS.register(
            "flowing_sulfuric_acid",
            () -> new SkyentChemicalFluid.Flowing(chemicalProperties(ModFluidTypes.SULFURIC_ACID, "sulfuric_acid", "flowing_sulfuric_acid"))
    );

    public static final DeferredHolder<Fluid, SkyentChemicalFluid.Source> DILUTED_SULFURIC_ACID = FLUIDS.register(
            "diluted_sulfuric_acid",
            () -> new SkyentChemicalFluid.Source(chemicalProperties(ModFluidTypes.DILUTED_SULFURIC_ACID, "diluted_sulfuric_acid", "flowing_diluted_sulfuric_acid"))
    );

    public static final DeferredHolder<Fluid, SkyentChemicalFluid.Flowing> FLOWING_DILUTED_SULFURIC_ACID = FLUIDS.register(
            "flowing_diluted_sulfuric_acid",
            () -> new SkyentChemicalFluid.Flowing(chemicalProperties(ModFluidTypes.DILUTED_SULFURIC_ACID, "diluted_sulfuric_acid", "flowing_diluted_sulfuric_acid"))
    );

    public static final DeferredHolder<Fluid, SkyentChemicalFluid.Source> MINERAL_SLURRY = FLUIDS.register(
            "mineral_slurry",
            () -> new SkyentChemicalFluid.Source(chemicalProperties(ModFluidTypes.MINERAL_SLURRY, "mineral_slurry", "flowing_mineral_slurry")
                    .slopeFindDistance(3)
                    .tickRate(10))
    );

    public static final DeferredHolder<Fluid, SkyentChemicalFluid.Flowing> FLOWING_MINERAL_SLURRY = FLUIDS.register(
            "flowing_mineral_slurry",
            () -> new SkyentChemicalFluid.Flowing(chemicalProperties(ModFluidTypes.MINERAL_SLURRY, "mineral_slurry", "flowing_mineral_slurry")
                    .slopeFindDistance(3)
                    .tickRate(10))
    );

    public static final DeferredHolder<Fluid, SkyentChemicalFluid.Source> BRINE = FLUIDS.register(
            "brine",
            () -> new SkyentChemicalFluid.Source(chemicalProperties(ModFluidTypes.BRINE, "brine", "flowing_brine"))
    );

    public static final DeferredHolder<Fluid, SkyentChemicalFluid.Flowing> FLOWING_BRINE = FLUIDS.register(
            "flowing_brine",
            () -> new SkyentChemicalFluid.Flowing(chemicalProperties(ModFluidTypes.BRINE, "brine", "flowing_brine"))
    );

    public static final DeferredHolder<Fluid, SkyentChemicalFluid.Source> DEMINERALISED_WATER = FLUIDS.register(
            "demineralised_water",
            () -> new SkyentChemicalFluid.Source(chemicalProperties(ModFluidTypes.DEMINERALISED_WATER, "demineralised_water", "flowing_demineralised_water"))
    );

    public static final DeferredHolder<Fluid, SkyentChemicalFluid.Flowing> FLOWING_DEMINERALISED_WATER = FLUIDS.register(
            "flowing_demineralised_water",
            () -> new SkyentChemicalFluid.Flowing(chemicalProperties(ModFluidTypes.DEMINERALISED_WATER, "demineralised_water", "flowing_demineralised_water"))
    );

    private ModFluids() {
    }

    private static BaseFlowingFluid.Properties chemicalProperties(
            DeferredHolder<net.neoforged.neoforge.fluids.FluidType, net.neoforged.neoforge.fluids.FluidType> type,
            String sourceId,
            String flowingId
    ) {
        return new BaseFlowingFluid.Properties(
                type,
                () -> BuiltInRegistries.FLUID.get(ResourceLocation.fromNamespaceAndPath(SkyesNuclearTech.MOD_ID, sourceId)),
                () -> BuiltInRegistries.FLUID.get(ResourceLocation.fromNamespaceAndPath(SkyesNuclearTech.MOD_ID, flowingId))
        )
                .slopeFindDistance(4)
                .levelDecreasePerBlock(1)
                .tickRate(5)
                .explosionResistance(1.0F);
    }

    public static void register(IEventBus modEventBus) {
        FLUIDS.register(modEventBus);
    }
}
