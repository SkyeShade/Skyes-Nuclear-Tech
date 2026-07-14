package com.skyeshade.skyent.mixin.client;

import com.skyeshade.skyent.client.effect.CameraShakeManager;
import net.minecraft.client.Camera;
import net.minecraft.world.phys.Vec2;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Camera.class)
public class CameraShakeMixin {
    @Inject(method = "setup", at = @At("TAIL"))
    private void skyent$applyShake(CallbackInfo callbackInfo) {
        Vec2 shake = CameraShakeManager.tickShake();
        if (shake == Vec2.ZERO) {
            return;
        }

        Camera camera = (Camera) (Object) this;
        ((CameraAccessor) camera).skyent$setRotation(camera.getYRot() + shake.x, camera.getXRot() + shake.y);
    }
}
