package com.skyeshade.skyent.event;

import com.skyeshade.skyent.client.renderer.LVConnectorRenderer;
import com.skyeshade.skyent.client.screen.CombustionGeneratorScreen;
import com.skyeshade.skyent.client.screen.ElectricFurnaceScreen;
import com.skyeshade.skyent.event.systems.BootstrapSystem;
import com.skyeshade.skyent.registry.ModBlockEntities;
import com.skyeshade.skyent.registry.ModMenus;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

public final class ClientEvents {
    private ClientEvents() {
    }

    public static void register(IEventBus modEventBus) {
        modEventBus.addListener(ClientEvents::onClientSetup);
        modEventBus.addListener(ClientEvents::onRegisterMenuScreens);
        modEventBus.addListener(ClientEvents::onRegisterRenderers);
    }

    public static void onClientSetup(FMLClientSetupEvent event) {
        BootstrapSystem.onClientSetup(event);
    }

    public static void onRegisterMenuScreens(RegisterMenuScreensEvent event) {
        event.register(ModMenus.COMBUSTION_GENERATOR.get(), CombustionGeneratorScreen::new);
        event.register(ModMenus.ELECTRIC_FURNACE.get(), ElectricFurnaceScreen::new);
    }

    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ModBlockEntities.LV_CONNECTOR.get(), LVConnectorRenderer::new);
    }
}
