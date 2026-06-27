package com.skyeshade.skyent.event;

import com.skyeshade.skyent.event.systems.BootstrapSystem;
import com.skyeshade.skyent.registry.ModBlockEntities;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;

public final class CommonEvents {
    private CommonEvents() {
    }

    public static void register(IEventBus modEventBus) {
        modEventBus.addListener(CommonEvents::onCommonSetup);
        modEventBus.addListener(CommonEvents::onRegisterCapabilities);
    }

    public static void onCommonSetup(FMLCommonSetupEvent event) {
        BootstrapSystem.onCommonSetup(event);
    }

    public static void onRegisterCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(
                Capabilities.EnergyStorage.BLOCK,
                ModBlockEntities.COMBUSTION_GENERATOR.get(),
                (generator, side) -> generator.getEnergyStorage()
        );

        event.registerBlockEntity(
                Capabilities.ItemHandler.BLOCK,
                ModBlockEntities.COMBUSTION_GENERATOR.get(),
                (generator, side) -> generator.getAutomationItemHandler(side)
        );

        event.registerBlockEntity(
                Capabilities.FluidHandler.BLOCK,
                ModBlockEntities.COMBUSTION_GENERATOR.get(),
                (generator, side) -> generator.getAutomationFluidHandler(side)
        );
    }
}
