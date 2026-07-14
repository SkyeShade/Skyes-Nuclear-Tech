package com.skyeshade.skyent.client.effect;

import com.skyeshade.skyent.network.NukeDetonationEffectsPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public final class NukeDetonationEffectsClient {
    private static final int FLASH_DURATION_TICKS = 18;
    private static final List<Flash> FLASHES = new ArrayList<>();

    private NukeDetonationEffectsClient() {
    }

    public static void handlePayload(NukeDetonationEffectsPayload payload) {
        if (Minecraft.getInstance().level == null || !payload.flashSky()) {
            return;
        }

        FLASHES.add(new Flash());
    }

    public static void onClientTick(ClientTickEvent.Post event) {
        if (Minecraft.getInstance().level == null) {
            FLASHES.clear();
            return;
        }

        Iterator<Flash> iterator = FLASHES.iterator();
        while (iterator.hasNext()) {
            Flash flash = iterator.next();
            flash.age++;
            if (flash.age > FLASH_DURATION_TICKS) {
                iterator.remove();
            }
        }
    }

    public static void onRenderLevel(RenderLevelStageEvent event) {
        // Entity-owned nuke visuals are rendered by NuclearExplosionRenderer.
    }

    public static void onRenderGui(RenderGuiEvent.Post event) {
        float intensity = 0.0F;
        for (Flash flash : FLASHES) {
            float progress = flash.age / (float) FLASH_DURATION_TICKS;
            intensity = Math.max(intensity, (1.0F - progress) * (1.0F - progress));
        }

        if (intensity <= 0.0F) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        int alpha = Math.round(Mth.clamp(intensity, 0.0F, 1.0F) * 190.0F);
        int color = alpha << 24 | 0xFFFFE8;
        event.getGuiGraphics().fill(0, 0, minecraft.getWindow().getGuiScaledWidth(), minecraft.getWindow().getGuiScaledHeight(), color);
    }

    private static final class Flash {
        private int age;
    }
}
