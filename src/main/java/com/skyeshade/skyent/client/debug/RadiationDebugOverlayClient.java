package com.skyeshade.skyent.client.debug;

import com.skyeshade.skyent.network.RadiationDebugOverlayPayload;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

public final class RadiationDebugOverlayClient {
    private static final int X = 8;
    private static final int Y = 8;
    private static final int LINE_HEIGHT = 10;
    private static final int LINE_1_COLOR = 0xFFFFFF;
    private static final int LINE_2_COLOR = 0xC8C8C8;

    private static boolean enabled;
    private static String line1 = "";
    private static String line2 = "";

    private RadiationDebugOverlayClient() {
    }

    public static void handlePayload(RadiationDebugOverlayPayload payload) {
        enabled = payload.enabled();
        line1 = payload.line1();
        line2 = payload.line2();
    }

    public static void onRenderGui(RenderGuiEvent.Post event) {
        if (!enabled || Minecraft.getInstance().options.hideGui) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }

        event.getGuiGraphics().drawString(minecraft.font, line1, X, Y, LINE_1_COLOR, true);
        event.getGuiGraphics().drawString(minecraft.font, line2, X, Y + LINE_HEIGHT, LINE_2_COLOR, true);
    }
}
