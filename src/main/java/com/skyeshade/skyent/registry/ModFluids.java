package com.skyeshade.skyent.registry;

import com.skyeshade.skyent.SkyesNuclearTech;
import com.skyeshade.skyent.content.fluid.MoltenCoriumFluid;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.material.Fluid;
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

    private ModFluids() {
    }

    public static void register(IEventBus modEventBus) {
        FLUIDS.register(modEventBus);
    }
}
