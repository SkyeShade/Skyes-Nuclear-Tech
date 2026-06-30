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

    public static void handleRadiationDebugOverlay(RadiationDebugOverlayPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> updateRadiationDebugOverlay(payload));
    }

    public static void handleGeigerExposure(GeigerExposurePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> setClientGeigerExposure(payload.exposureMillisievertsPerSecond(), payload.radiationSickness()));
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

    private static void updateRadiationDebugOverlay(RadiationDebugOverlayPayload payload) {
        if (FMLEnvironment.dist != Dist.CLIENT) {
            return;
        }

        try {
            Class<?> overlay = Class.forName("com.skyeshade.skyent.client.debug.RadiationDebugOverlayClient");
            overlay.getMethod("handlePayload", RadiationDebugOverlayPayload.class).invoke(null, payload);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Unable to update client radiation debug overlay", exception);
        }
    }

    private static void setClientGeigerExposure(double exposureMillisievertsPerSecond, double radiationSickness) {
        if (FMLEnvironment.dist != Dist.CLIENT) {
            return;
        }

        try {
            Class<?> geigerState = Class.forName("com.skyeshade.skyent.client.item.GeigerCounterClientState");
            geigerState.getMethod("setExposureMillisievertsPerSecond", double.class).invoke(null, exposureMillisievertsPerSecond);
            geigerState.getMethod("setRadiationSickness", double.class).invoke(null, radiationSickness);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Unable to update client Geiger exposure state", exception);
        }
    }
}
