package com.skyeshade.skyent.mixin.client;

import com.skyeshade.skyent.client.render.HeatingChamberLightRefreshTracker;
import com.skyeshade.skyent.client.render.IndustrialPressLightRefreshTracker;
import com.skyeshade.skyent.client.render.RollingMillLightRefreshTracker;
import com.skyeshade.skyent.client.render.WireMillLightRefreshTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
public class MixinLevelRenderer {
    @Inject(method = "setSectionDirty(IIIZ)V", at = @At("HEAD"))
    private void skyent$refreshHeatingChamberLight(int sectionX, int sectionY, int sectionZ, boolean reRenderOnMainThread, CallbackInfo callbackInfo) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level != null) {
            HeatingChamberLightRefreshTracker.refreshForDirtySection(level, sectionX, sectionY, sectionZ);
            IndustrialPressLightRefreshTracker.refreshForDirtySection(level, sectionX, sectionY, sectionZ);
            RollingMillLightRefreshTracker.refreshForDirtySection(level, sectionX, sectionY, sectionZ);
            WireMillLightRefreshTracker.refreshForDirtySection(level, sectionX, sectionY, sectionZ);
        }
    }
}
