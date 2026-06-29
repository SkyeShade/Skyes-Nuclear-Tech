package com.skyeshade.skyent.network;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public final class ClientPayloadHandlers {
    private ClientPayloadHandlers() {
    }

    public static void handleRadiationRaysDebug(RadiationRaysDebugPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> setClientRadiationRays(payload.enabled()));
    }

    public static void handleRadiationRayBatch(RadiationRayBatchPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> addClientRadiationRays(payload));
    }

    private static void setClientRadiationRays(boolean enabled) {
        if (FMLEnvironment.dist != Dist.CLIENT) {
            return;
        }

        try {
            Class<?> clientDebug = Class.forName("com.skyeshade.skyent.client.debug.RadiationRayDebugClient");
            clientDebug.getMethod("setEnabled", boolean.class).invoke(null, enabled);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Unable to update client radiation ray debug state", exception);
        }
    }

    private static void addClientRadiationRays(RadiationRayBatchPayload payload) {
        if (FMLEnvironment.dist != Dist.CLIENT) {
            return;
        }

        try {
            Class<?> clientDebug = Class.forName("com.skyeshade.skyent.client.debug.RadiationRayDebugClient");
            clientDebug.getMethod("addRays", RadiationRayBatchPayload.class).invoke(null, payload);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Unable to add client radiation ray debug records", exception);
        }
    }
}
