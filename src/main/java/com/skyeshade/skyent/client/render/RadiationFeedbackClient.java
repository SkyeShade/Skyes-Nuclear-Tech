package com.skyeshade.skyent.client.render;

import com.skyeshade.skyent.SkyesNuclearTech;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

public final class RadiationFeedbackClient {
    private static final float START_MSV_PER_SECOND = 100.0F;
    private static final float MAX_MSV_PER_SECOND = 1000.0F;
    private static final ResourceLocation SHADER = ResourceLocation.fromNamespaceAndPath(
            SkyesNuclearTech.MOD_ID,
            "shaders/post/radiation_feedback.json"
    );

    private static boolean active;
    private static float targetIntensity;
    private static float displayedIntensity;
    private static float time;

    private RadiationFeedbackClient() {
    }

    public static void setDoseRate(float milliSievertsPerSecond) {
        float raw = (milliSievertsPerSecond - START_MSV_PER_SECOND) / (MAX_MSV_PER_SECOND - START_MSV_PER_SECOND);
        targetIntensity = smoothStep(Mth.clamp(raw, 0.0F, 1.0F));
    }

    public static void tick() {
        Minecraft minecraft = Minecraft.getInstance();
        displayedIntensity += (targetIntensity - displayedIntensity) * 0.12F;
        if (displayedIntensity < 0.001F) {
            displayedIntensity = 0.0F;
        }

        if (displayedIntensity > 0.0F && !active) {
            active = true;
            minecraft.gameRenderer.loadEffect(SHADER);
        }

        if (displayedIntensity <= 0.0F && active) {
            minecraft.gameRenderer.shutdownEffect();
            active = false;
            return;
        }

        if (!active) {
            return;
        }

        time += 1.0F;
        PostChain chain = minecraft.gameRenderer.currentEffect();
        if (chain != null) {
            chain.setUniform("Intensity", displayedIntensity);
            chain.setUniform("Time", time);
        }
    }

    public static void clear() {
        targetIntensity = 0.0F;
        displayedIntensity = 0.0F;
        time = 0.0F;
        if (active) {
            Minecraft.getInstance().gameRenderer.shutdownEffect();
            active = false;
        }
    }

    private static float smoothStep(float value) {
        float clamped = Mth.clamp(value, 0.0F, 1.0F);
        return clamped * clamped * (3.0F - 2.0F * clamped);
    }
}
