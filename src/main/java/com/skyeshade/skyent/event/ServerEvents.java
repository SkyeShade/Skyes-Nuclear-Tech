package com.skyeshade.skyent.event;

import com.skyeshade.skyent.event.systems.BootstrapSystem;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartingEvent;

public final class ServerEvents {
    private ServerEvents() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(ServerEvents::onServerStarting);
    }

    public static void onServerStarting(ServerStartingEvent event) {
        BootstrapSystem.onServerStarting(event);
    }
}
