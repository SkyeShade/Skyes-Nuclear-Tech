package com.skyeshade.skyent.event;

import com.skyeshade.skyent.event.systems.BootstrapSystem;
import com.skyeshade.skyent.event.systems.RadiationDebugSystem;
import com.skyeshade.skyent.event.systems.RadiationExposureSystem;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;

public final class ServerEvents {
    private ServerEvents() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(ServerEvents::onServerStarting);
        NeoForge.EVENT_BUS.addListener(ServerEvents::onRegisterCommands);
        NeoForge.EVENT_BUS.addListener(ServerEvents::onPlayerLoggedOut);
        NeoForge.EVENT_BUS.addListener(RadiationExposureSystem::onPlayerTick);
    }

    public static void onServerStarting(ServerStartingEvent event) {
        BootstrapSystem.onServerStarting(event);
    }

    public static void onRegisterCommands(RegisterCommandsEvent event) {
        RadiationDebugSystem.registerCommands(event);
    }

    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        RadiationDebugSystem.onPlayerLoggedOut(event);
        RadiationExposureSystem.onPlayerLoggedOut(event);
    }
}
