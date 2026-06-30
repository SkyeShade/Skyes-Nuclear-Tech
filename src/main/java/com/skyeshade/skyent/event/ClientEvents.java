package com.skyeshade.skyent.event;

import com.skyeshade.skyent.client.debug.RadiationDebugOverlayClient;
import com.skyeshade.skyent.client.debug.RadiationRayDebugClient;
import com.skyeshade.skyent.client.item.GeigerCounterClientState;
import com.skyeshade.skyent.client.item.GeigerCounterSoundManager;
import com.skyeshade.skyent.client.item.PlacedGeigerCounterSoundManager;
import com.skyeshade.skyent.client.renderer.blockentity.GeigerCounterPlacedRenderer;
import com.skyeshade.skyent.client.renderer.LVConnectorRenderer;
import com.skyeshade.skyent.client.renderer.LVElectricPumpRenderer;
import com.skyeshade.skyent.client.screen.BrickBlastFurnaceScreen;
import com.skyeshade.skyent.client.screen.CombustionGeneratorScreen;
import com.skyeshade.skyent.client.screen.ElectricFurnaceScreen;
import com.skyeshade.skyent.client.screen.LVElectricPumpScreen;
import com.skyeshade.skyent.event.systems.BootstrapSystem;
import com.skyeshade.skyent.registry.ModBlockEntities;
import com.skyeshade.skyent.registry.ModMenus;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

public final class ClientEvents {
    private ClientEvents() {
    }

    public static void register(IEventBus modEventBus) {
        modEventBus.addListener(ClientEvents::onClientSetup);
        modEventBus.addListener(ClientEvents::onRegisterMenuScreens);
        modEventBus.addListener(ClientEvents::onRegisterRenderers);
        NeoForge.EVENT_BUS.addListener(ClientEvents::onClientTick);
        NeoForge.EVENT_BUS.addListener(ClientEvents::onRenderGui);
        NeoForge.EVENT_BUS.addListener(ClientEvents::onRenderLevel);
    }

    public static void onClientSetup(FMLClientSetupEvent event) {
        BootstrapSystem.onClientSetup(event);
    }

    public static void onRegisterMenuScreens(RegisterMenuScreensEvent event) {
        event.register(ModMenus.COMBUSTION_GENERATOR.get(), CombustionGeneratorScreen::new);
        event.register(ModMenus.ELECTRIC_FURNACE.get(), ElectricFurnaceScreen::new);
        event.register(ModMenus.BRICK_BLAST_FURNACE.get(), BrickBlastFurnaceScreen::new);
        event.register(ModMenus.LV_ELECTRIC_PUMP.get(), LVElectricPumpScreen::new);
    }

    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ModBlockEntities.LV_CONNECTOR.get(), LVConnectorRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.LV_ELECTRIC_PUMP.get(), LVElectricPumpRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.GEIGER_COUNTER_PLACED.get(), GeigerCounterPlacedRenderer::new);
    }

    public static void onClientTick(ClientTickEvent.Post event) {
        GeigerCounterClientState.clientTick();
        GeigerCounterSoundManager.clientTick();
        PlacedGeigerCounterSoundManager.clientTick();
        RadiationRayDebugClient.onClientTick(event);
    }

    public static void onRenderLevel(RenderLevelStageEvent event) {
        RadiationRayDebugClient.onRenderLevel(event);
    }

    public static void onRenderGui(RenderGuiEvent.Post event) {
        RadiationDebugOverlayClient.onRenderGui(event);
    }
}
