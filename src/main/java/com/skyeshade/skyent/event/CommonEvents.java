package com.skyeshade.skyent.event;

import com.skyeshade.skyent.event.systems.BootstrapSystem;
import com.skyeshade.skyent.network.ClientPayloadHandlers;
import com.skyeshade.skyent.network.GeigerExposurePayload;
import com.skyeshade.skyent.network.RadiationDebugOverlayPayload;
import com.skyeshade.skyent.network.RadiationRayBatchPayload;
import com.skyeshade.skyent.network.RadiationRaysDebugPayload;
import com.skyeshade.skyent.registry.ModBlockEntities;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

public final class CommonEvents {
    private CommonEvents() {
    }

    public static void register(IEventBus modEventBus) {
        modEventBus.addListener(CommonEvents::onCommonSetup);
        modEventBus.addListener(CommonEvents::onRegisterCapabilities);
        modEventBus.addListener(CommonEvents::onRegisterPayloads);
    }

    public static void onCommonSetup(FMLCommonSetupEvent event) {
        BootstrapSystem.onCommonSetup(event);
    }

    public static void onRegisterCapabilities(RegisterCapabilitiesEvent event) {
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

        event.registerBlockEntity(
                Capabilities.ItemHandler.BLOCK,
                ModBlockEntities.ELECTRIC_FURNACE.get(),
                (furnace, side) -> furnace.getAutomationItemHandler()
        );

        event.registerBlockEntity(
                Capabilities.ItemHandler.BLOCK,
                ModBlockEntities.BRICK_BLAST_FURNACE.get(),
                (furnace, side) -> furnace.getAutomationItemHandler(side)
        );

        event.registerBlockEntity(
                Capabilities.ItemHandler.BLOCK,
                ModBlockEntities.LV_ELECTRIC_PUMP.get(),
                (pump, side) -> pump.getAutomationItemHandler()
        );

        event.registerBlockEntity(
                Capabilities.FluidHandler.BLOCK,
                ModBlockEntities.LV_ELECTRIC_PUMP.get(),
                (pump, side) -> pump.getAutomationFluidHandler()
        );

        event.registerBlockEntity(
                Capabilities.FluidHandler.BLOCK,
                ModBlockEntities.BASIC_FLUID_DUCT.get(),
                (duct, side) -> duct.getFluidHandler(side)
        );

        event.registerBlockEntity(
                Capabilities.EnergyStorage.BLOCK,
                ModBlockEntities.LV_RJ_CONVERTER.get(),
                (converter, side) -> converter.getFEOutput()
        );

        event.registerBlockEntity(
                Capabilities.EnergyStorage.BLOCK,
                ModBlockEntities.LV_FE_CONVERTER.get(),
                (converter, side) -> converter.getFEInput()
        );
    }

    public static void onRegisterPayloads(RegisterPayloadHandlersEvent event) {
        event.registrar("1")
                .playToClient(
                        RadiationRaysDebugPayload.TYPE,
                        RadiationRaysDebugPayload.STREAM_CODEC,
                        ClientPayloadHandlers::handleRadiationRaysDebug
                )
                .playToClient(
                        RadiationRayBatchPayload.TYPE,
                        RadiationRayBatchPayload.STREAM_CODEC,
                        ClientPayloadHandlers::handleRadiationRayBatch
                )
                .playToClient(
                        GeigerExposurePayload.TYPE,
                        GeigerExposurePayload.STREAM_CODEC,
                        ClientPayloadHandlers::handleGeigerExposure
                )
                .playToClient(
                        RadiationDebugOverlayPayload.TYPE,
                        RadiationDebugOverlayPayload.STREAM_CODEC,
                        ClientPayloadHandlers::handleRadiationDebugOverlay
                );
    }
}
