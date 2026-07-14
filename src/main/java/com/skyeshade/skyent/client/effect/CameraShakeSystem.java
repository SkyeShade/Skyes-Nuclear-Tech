package com.skyeshade.skyent.client.effect;

import net.neoforged.neoforge.client.event.ClientTickEvent;

public final class CameraShakeSystem {
    private CameraShakeSystem() {
    }

    public static void onClientTick(ClientTickEvent.Post event) {
        CameraShakeManager.tick();
    }
}
